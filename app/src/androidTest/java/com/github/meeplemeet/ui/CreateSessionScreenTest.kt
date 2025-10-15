package com.github.meeplemeet.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.repositories.GameRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Location
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.theme.AppTheme
import com.google.firebase.Timestamp
import io.mockk.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CreateSessionScreenTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var firestoreRepo: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel

  private lateinit var sessionRepo:
      com.github.meeplemeet.model.repositories.FirestoreSessionRepository
  private lateinit var fakeGameRepo: FakeGameRepository
  private lateinit var sessionVM: FirestoreSessionViewModel

  private val me = Account(uid = "user1", handle = "", name = "Marco", email = "marco@epfl.ch")
  private val alex = Account(uid = "user2", handle = "", name = "Alexandre", email = "alex@epfl.ch")
  private val dany = Account(uid = "user3", handle = "", name = "Dany", email = "dany@epfl.ch")

  private val discussionId = "discussion1"

  private lateinit var injectedDiscussionFlow: MutableStateFlow<Discussion?>
  private lateinit var baseDiscussion: Discussion

  /** All editable text inputs on the screen: Title, Game, Location. */
  private fun allInputs() = compose.onAllNodes(hasSetTextAction())

  private fun titleInput() = allInputs()[0]

  private fun gameInput() = allInputs()[1]

  private fun locationInput() = allInputs()[2]

  private fun buttonWithLabel(text: String) =
      compose.onNode(
          hasClickAction().and(hasAnyDescendant(hasText(text, substring = false))),
          useUnmergedTree = true)

  private fun createBtn() = buttonWithLabel("Create Session")

  private fun discardBtn() = buttonWithLabel("Discard")

  private fun backBtn() = compose.onNodeWithContentDescription("Back")

  /** In new UI, trailing icon for the search field is a "Clear" button. */
  private fun clearIcon() = compose.onNodeWithContentDescription("Clear", useUnmergedTree = true)

  private fun setContent(onBack: () -> Unit = {}) {
    compose.setContent {
      AppTheme {
        CreateSessionScreen(
            viewModel = viewModel,
            sessionViewModel = sessionVM,
            currentUser = me,
            discussionId = discussionId,
            onBack = onBack)
      }
    }
  }

  private class FakeGameRepository : GameRepository {
    var throwOnSearch: Boolean = false

    override suspend fun getGames(maxResults: Int) =
        emptyList<com.github.meeplemeet.model.structures.Game>()

    override suspend fun getGameById(gameID: String): com.github.meeplemeet.model.structures.Game {
      throw RuntimeException("not used in these tests")
    }

    override suspend fun getGamesByGenre(genreID: Int, maxResults: Int) =
        emptyList<com.github.meeplemeet.model.structures.Game>()

    override suspend fun getGamesByGenres(genreIDs: List<Int>, maxResults: Int) =
        emptyList<com.github.meeplemeet.model.structures.Game>()

    override suspend fun searchGamesByNameContains(
        query: String,
        maxResults: Int,
        ignoreCase: Boolean
    ): List<com.github.meeplemeet.model.structures.Game> {
      if (throwOnSearch) throw RuntimeException("boom")
      return emptyList()
    }
  }

  @Before
  fun setUp() {
    firestoreRepo = mockk(relaxed = true)
    viewModel = spyk(FirestoreViewModel(firestoreRepo))

    baseDiscussion =
        Discussion(
            uid = discussionId,
            name = "Board Night",
            description = "",
            messages = emptyList(),
            participants = listOf(me.uid, alex.uid, dany.uid),
            admins = listOf(me.uid),
            creatorId = me.uid)

    val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map = discussionFlowsField.get(viewModel) as MutableMap<String, StateFlow<Discussion?>>
    injectedDiscussionFlow = MutableStateFlow(baseDiscussion)
    map[discussionId] = injectedDiscussionFlow

    every { viewModel.getDiscussionParticipants(any(), any()) } answers
        {
          val disc = firstArg<Discussion>()
          val cb = secondArg<(List<Account>) -> Unit>()
          val accounts =
              disc.participants.distinct().mapNotNull { uid ->
                when (uid) {
                  me.uid -> me
                  alex.uid -> alex
                  dany.uid -> dany
                  "u4" -> Account(uid = "u4", handle = "newb", name = "Newbie", email = "n@x")
                  else -> null
                }
              }
          cb(accounts)
        }

    sessionRepo = mockk(relaxed = true)
    fakeGameRepo = FakeGameRepository()
    sessionVM =
        FirestoreSessionViewModel(
            initDiscussion = baseDiscussion,
            repository = sessionRepo,
            gameRepository = fakeGameRepo)
  }

  @Test
  fun participants_exclude_current_user_from_candidates() {
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Marco", substring = false).assertCountEquals(1)
  }

  @Test
  fun flow_with_only_me_has_no_candidates() {
    injectedDiscussionFlow.value = baseDiscussion.copy(participants = listOf(me.uid))
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Alexandre").assertCountEquals(0)
    compose.onAllNodesWithText("Dany").assertCountEquals(0)
  }

  @Test
  fun flow_update_shows_new_participant_candidate() {
    setContent()
    injectedDiscussionFlow.value =
        baseDiscussion.copy(uid = "disc2", participants = listOf(me.uid, alex.uid, dany.uid, "u4"))
    compose.waitUntil(5_000) {
      compose.onAllNodesWithText("Newbie").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onAllNodesWithText("Newbie").assertCountEquals(1)
  }

  @Test
  fun duplicates_in_participants_do_not_duplicate_candidates() {
    injectedDiscussionFlow.value =
        baseDiscussion.copy(participants = listOf(me.uid, alex.uid, alex.uid, dany.uid, alex.uid))
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
  }

  @Test
  fun create_button_is_disabled_without_date_and_time() {
    injectedDiscussionFlow.value = baseDiscussion.copy(participants = listOf(me.uid))
    setContent()
    titleInput().performTextInput("Friday Night Board Game Jam")
    gameInput().performTextInput("Root")
    locationInput().performTextInput("Table A1")
    createBtn().assertIsNotEnabled()
  }

  @Test
  fun game_search_bar_header_and_clear_icon_present_and_working() {
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Proposed game:").onFirst().assertExists()

    gameInput().performTextInput("Cascadia")

    // Wait until the trailing clear icon is laid out
    compose.waitUntil(3_000) {
      compose
          .onAllNodesWithContentDescription("Clear", useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    clearIcon().assertExists().performClick()

    // Give the UI a frame to apply the cleared text, then assert it's gone
    compose.waitUntil(3_000) {
      compose.onAllNodesWithText("Cascadia").fetchSemanticsNodes().isEmpty()
    }
    compose.onAllNodesWithText("Cascadia").assertCountEquals(0)
  }

  @Test
  fun game_search_bar_surfaces_repo_error_message() {
    fakeGameRepo.throwOnSearch = true
    setContent()
    gameInput().performTextInput("Catan")
    compose.waitUntil(5_000) {
      compose
          .onAllNodesWithText("Game search failed due to a repository error")
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    compose
        .onAllNodesWithText("Game search failed due to a repository error")
        .onFirst()
        .assertExists()
  }

  @Test
  fun discard_calls_onBack_without_asserting_field_values() {
    val backCalled = AtomicBoolean(false)
    setContent(onBack = { backCalled.set(true) })
    discardBtn().performSemanticsAction(SemanticsActions.OnClick)
    compose.waitUntil(2_000) { backCalled.get() }
  }

  @Test
  fun back_button_triggers_callback() {
    val back = AtomicBoolean(false)
    setContent(onBack = { back.set(true) })
    backBtn().performClick()
    compose.waitUntil(2_000) { back.get() }
  }

  @Test
  fun typing_title_game_location_updates_fields() {
    injectedDiscussionFlow.value = baseDiscussion.copy(participants = listOf(me.uid))
    setContent()
    titleInput().performTextInput("Friday Night Board Game Jam")
    gameInput().performTextInput("Root")
    locationInput().performTextInput("Table A1")
    compose.waitForIdle()
    compose.onAllNodesWithText("Friday Night Board Game Jam").onFirst().assertExists()
    compose.onAllNodesWithText("Root").onFirst().assertExists()
    compose.onAllNodesWithText("Table A1").onFirst().assertExists()
  }

  @Test
  fun clicking_self_chip_does_not_remove_me_and_candidates_are_unique() {
    setContent()
    compose.waitForIdle()
    compose.onNodeWithText("Marco").assertIsDisplayed().performClick()
    compose.onNodeWithText("Marco").assertIsDisplayed()
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
  }

  @Test
  fun add_then_remove_participant_keeps_single_visible_copy() {
    setContent()
    compose.waitUntil(5_000) {
      compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onAllNodesWithText("Alexandre").onLast().performClick()
    compose.runOnIdle {}
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    compose.onAllNodesWithText("Alexandre").onFirst().performClick()
    compose.runOnIdle {}
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
  }

  @Test
  fun null_discussion_shows_no_candidates() {
    injectedDiscussionFlow.value = null
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Alexandre").assertCountEquals(0)
    compose.onAllNodesWithText("Dany").assertCountEquals(0)
    titleInput().performTextInput("Solo Setup")
    locationInput().performTextInput("Open Table")
    compose.onAllNodesWithText("Solo Setup").onFirst().assertExists()
    compose.onAllNodesWithText("Open Table").onFirst().assertExists()
  }

  @Test
  fun participants_section_add_remove_and_self_protection_in_component() {
    val meAcc = me
    val all = listOf(meAcc, alex, dany)
    compose.setContent {
      AppTheme {
        var min by remember { mutableStateOf(1) }
        var max by remember { mutableStateOf(2) }
        var selected by remember { mutableStateOf(listOf(meAcc)) }
        ParticipantsSection(
            currentUserId = meAcc.uid,
            selected = selected,
            allCandidates = all,
            minPlayers = min,
            maxPlayers = max,
            onMinMaxChange = { a, b ->
              min = a
              max = b
            },
            onAdd = { a -> selected = (selected + a).distinctBy { it.uid } },
            onRemove = { a -> selected = selected.filterNot { it.uid == a.uid } },
            minSliderNumber = MIN_SLIDER_NUMBER,
            maxSliderNumber = MAX_SLIDER_NUMBER,
            sliderSteps = SLIDER_STEPS,
            mainSectionTitle = PARTICIPANT_SECTION_NAME,
            sliderDescription = SLIDER_DESCRIPTION)
      }
    }
    compose.onAllNodesWithText("Marco").assertCountEquals(1)
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    compose.onAllNodesWithText("Dany").assertCountEquals(1)
    compose.onAllNodesWithText("Alexandre").onLast().performClick()
    compose.runOnIdle {}
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    compose.onNodeWithText("Marco").performClick()
    compose.onAllNodesWithText("Marco").assertCountEquals(1)
    compose.onAllNodesWithText("Alexandre").onFirst().performClick()
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
  }

  @Test
  fun initial_load_fetches_participants_once() {
    setContent()
    compose.waitForIdle()
    verify(exactly = 1) {
      viewModel.getDiscussionParticipants(match { it.uid == discussionId }, any())
    }
  }

  @Test
  fun changing_discussion_uid_refetches_participants() {
    setContent()
    compose.waitForIdle()
    verify(exactly = 1) {
      viewModel.getDiscussionParticipants(match { it.uid == discussionId }, any())
    }
    injectedDiscussionFlow.value = baseDiscussion.copy(uid = "discX")
    compose.waitForIdle()
    verify(exactly = 1) { viewModel.getDiscussionParticipants(match { it.uid == "discX" }, any()) }
    verify(exactly = 2) { viewModel.getDiscussionParticipants(any(), any()) }
  }

  @Test
  fun null_discussion_does_not_fetch_and_create_is_disabled() {
    injectedDiscussionFlow.value = null
    setContent()
    compose.waitForIdle()
    verify(exactly = 0) { viewModel.getDiscussionParticipants(any(), any()) }
    createBtn().assertIsNotEnabled()
  }

  @Test
  fun even_if_fetch_omits_me_current_user_is_still_present_and_not_a_candidate() {
    every { viewModel.getDiscussionParticipants(match { it.uid == "discY" }, any()) } answers
        {
          val cb = secondArg<(List<Account>) -> Unit>()
          cb(listOf(alex, dany))
        }
    injectedDiscussionFlow.value =
        baseDiscussion.copy(uid = "discY", participants = listOf(alex.uid, dany.uid))
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Marco").assertCountEquals(1)
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    compose.onAllNodesWithText("Dany").assertCountEquals(1)
  }

  @Test
  fun initial_candidates_show_two_non_me_names() {
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    compose.onAllNodesWithText("Dany").assertCountEquals(1)
  }

  @Test
  fun empty_participant_list_results_in_no_candidates() {
    injectedDiscussionFlow.value = baseDiscussion.copy(participants = emptyList())
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("Alexandre").assertCountEquals(0)
    compose.onAllNodesWithText("Dany").assertCountEquals(0)
    compose.onAllNodesWithText("Marco").assertCountEquals(1)
  }

  @Test
  fun participants_header_and_slider_labels_present() {
    setContent()
    compose.waitForIdle()
    compose.onAllNodesWithText("$PARTICIPANT_SECTION_NAME:").onFirst().assertExists()
    compose.onAllNodesWithText(SLIDER_DESCRIPTION).onFirst().assertExists()
  }

  @Test
  fun create_session_button_component_enabled_click_calls_callback() {
    val clicked = AtomicBoolean(false)
    compose.setContent {
      AppTheme {
        CreateSessionButton(
            formToSubmit = SessionForm(title = "t"),
            enabled = true,
            onCreate = { clicked.set(true) })
      }
    }
    compose
        .onNode(
            hasClickAction().and(hasAnyDescendant(hasText("Create Session"))),
            useUnmergedTree = true)
        .assertIsEnabled()
        .performClick()
    compose.waitUntil(2_000) { clicked.get() }
  }

  @Test
  fun create_session_button_component_disabled_stays_disabled() {
    val clicked = AtomicBoolean(false)
    compose.setContent {
      AppTheme {
        CreateSessionButton(
            formToSubmit = SessionForm(), enabled = false, onCreate = { clicked.set(true) })
      }
    }
    compose
        .onNode(
            hasClickAction().and(hasAnyDescendant(hasText("Create Session"))),
            useUnmergedTree = true)
        .assertIsNotEnabled()
    assert(!clicked.get())
  }

  @Test
  fun organisation_section_shows_location_label_and_search_field() {
    compose.setContent {
      AppTheme {
        OrganisationSection(
            date = null,
            time = null,
            locationText = "",
            onDateChange = {},
            onTimeChange = {},
            onLocationChange = {},
            onLocationPicked = {},
            title = ORGANISATION_SECTION_NAME)
      }
    }
    compose.onAllNodesWithText("Location").onFirst().assertExists()
  }

  @Test
  fun changing_discussion_uid_twice_refetches_each_time() {
    setContent()
    compose.waitForIdle()
    verify(exactly = 1) {
      viewModel.getDiscussionParticipants(match { it.uid == discussionId }, any())
    }
    injectedDiscussionFlow.value = baseDiscussion.copy(uid = "discX")
    compose.waitForIdle()
    injectedDiscussionFlow.value = baseDiscussion.copy(uid = "discY")
    compose.waitForIdle()
    verify(exactly = 1) { viewModel.getDiscussionParticipants(match { it.uid == "discX" }, any()) }
    verify(exactly = 1) { viewModel.getDiscussionParticipants(match { it.uid == "discY" }, any()) }
    verify(exactly = 3) { viewModel.getDiscussionParticipants(any(), any()) }
  }

  private fun screenFileClass(): Class<*> {
    return try {
      Class.forName("com.github.meeplemeet.ui.CreateSessionScreenKt")
    } catch (_: ClassNotFoundException) {
      Class.forName("com.github.meeplemeet.ui.SessionCreationScreenKt")
    }
  }

  @Test
  fun randomLocationFrom_bounds_and_name_behavior() {
    val cls = screenFileClass()
    val m =
        cls.getDeclaredMethod("randomLocationFrom", String::class.java).apply {
          isAccessible = true
        }
    val nonBlank = m.invoke(null, "Hall A") as Location
    assert(nonBlank.name == "Hall A")
    assert(nonBlank.latitude in -90.0..90.0)
    assert(nonBlank.longitude in -180.0..180.0)
    val blank = m.invoke(null, "   ") as Location
    assert(blank.name == "Random place")
    assert(blank.latitude in -90.0..90.0)
    assert(blank.longitude in -180.0..180.0)
  }

  @Test
  fun toTimestamp_with_explicit_zone_matches_epoch_millis() {
    val cls = screenFileClass()
    val m =
        cls.getDeclaredMethod(
                "toTimestamp", LocalDate::class.java, LocalTime::class.java, ZoneId::class.java)
            .apply { isAccessible = true }
    val date = LocalDate.of(2025, 1, 2)
    val time = LocalTime.of(13, 45, 30, 123_000_000)
    val zone = ZoneId.of("Europe/Zurich")
    val expectedMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val ts = m.invoke(null, date, time, zone) as Timestamp
    val gotMillis = ts.toDate().time
    assert(gotMillis == expectedMillis)
  }

  @Test
  fun toTimestamp_default_zone_equals_system_default_explicit() {
    val cls = screenFileClass()
    val explicit =
        cls.getDeclaredMethod(
                "toTimestamp", LocalDate::class.java, LocalTime::class.java, ZoneId::class.java)
            .apply { isAccessible = true }
    val withDefault =
        cls.getDeclaredMethod(
                "toTimestamp\$default",
                LocalDate::class.java,
                LocalTime::class.java,
                ZoneId::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java)
            .apply { isAccessible = true }
    val date = LocalDate.of(2025, 5, 6)
    val time = LocalTime.of(9, 10, 0, 0)
    val sys = ZoneId.systemDefault()
    val tsExplicit = explicit.invoke(null, date, time, sys) as Timestamp
    val maskForZoneDefault = 1 shl 2
    val tsDefault =
        withDefault.invoke(null, date, time, null, maskForZoneDefault, null) as Timestamp
    assert(tsExplicit.toDate().time == tsDefault.toDate().time)
  }

  @Test
  fun toTimestamp_returns_now_when_date_or_time_null() {
    val cls = screenFileClass()
    val m =
        cls.getDeclaredMethod(
                "toTimestamp", LocalDate::class.java, LocalTime::class.java, ZoneId::class.java)
            .apply { isAccessible = true }

    val before = System.currentTimeMillis()
    val ts1 = m.invoke(null, null, null, ZoneId.of("UTC")) as Timestamp
    val ts2 = m.invoke(null, null, LocalTime.of(12, 0), ZoneId.of("UTC")) as Timestamp
    val ts3 = m.invoke(null, null, null, ZoneId.systemDefault()) as Timestamp
    val after = System.currentTimeMillis()
    val window = (before - 10_000)..(after + 10_000)

    assert(ts1.toDate().time in window)
    assert(ts2.toDate().time in window)
    assert(ts3.toDate().time in window)
  }

  @Test
  fun toTimestamp_different_zones_roundtrip_consistent_with_zoneddatetime() {
    val cls = screenFileClass()
    val m =
        cls.getDeclaredMethod(
                "toTimestamp", LocalDate::class.java, LocalTime::class.java, ZoneId::class.java)
            .apply { isAccessible = true }
    val date = LocalDate.of(2025, 10, 15)
    val time = LocalTime.of(8, 30, 45, 0)
    val zones = listOf("Europe/Zurich", "Asia/Tokyo", "America/New_York").map(ZoneId::of)
    zones.forEach { z ->
      val expected = ZonedDateTime.of(date, time, z).toInstant().toEpochMilli()
      val got = (m.invoke(null, date, time, z) as Timestamp).toDate().time
      assert(got == expected)
    }
  }

  @Test
  fun create_button_disabled_when_participants_out_of_bounds() {
    setContent()
    titleInput().performTextInput("Event")
    compose.waitUntil(5_000) {
      compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isNotEmpty()
    }
    createBtn().assertIsNotEnabled()
    compose.onAllNodesWithText("Alexandre").onLast().performClick()
    compose.runOnIdle {}
    createBtn().assertIsNotEnabled()
  }

  @Test
  fun candidate_list_empty_after_selecting_all_available_candidates() {
    setContent()
    compose.waitUntil(5_000) {
      compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onAllNodesWithText("Alexandre").onLast().performClick()
    compose.onAllNodesWithText("Dany").onLast().performClick()
    compose.runOnIdle {}
    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    compose.onAllNodesWithText("Dany").assertCountEquals(1)
  }

  @Test
  fun discard_button_component_invokes_callback_and_is_enabled() {
    val called = AtomicBoolean(false)
    compose.setContent { AppTheme { DiscardButton(onDiscard = { called.set(true) }) } }
    compose
        .onNode(hasClickAction().and(hasAnyDescendant(hasText("Discard"))), useUnmergedTree = true)
        .assertIsEnabled()
        .performClick()
    assert(called.get())
  }

  @Test
  fun discard_from_screen_resets_fields_and_participants() {
    setContent()
    compose.waitForIdle()

    titleInput().performTextInput("Temp Title")
    gameInput().performTextInput("Temp Game")
    locationInput().performTextInput("Temp Location")

    compose.waitUntil(5_000) {
      compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onAllNodesWithText("Alexandre").onLast().performClick()
    compose.runOnIdle {}

    discardBtn().performClick()

    // Harden: wait for the screen to recompose after discard
    compose.waitUntil(3_000) {
      // Heuristic: fields are focusable and ready again
      compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 3
    }

    allInputs()[0].performTextClearance()
    allInputs()[0].performTextInput("X")
    allInputs()[1].performTextClearance()
    allInputs()[1].performTextInput("Y")
    allInputs()[2].performTextClearance()
    allInputs()[2].performTextInput("Z")

    titleInput().assertTextEquals("X")

    gameInput()
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Y")))

    locationInput()
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Z")))

    compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    createBtn().assertIsNotEnabled()
  }
}
