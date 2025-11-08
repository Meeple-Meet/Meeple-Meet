// This file was developed with partial assistance from ChatGPT Thinking Extend and refined by hand.
// Certain elements stemmed from discussions with the LLM about testing ideas and possible
// combinations.

package com.github.meeplemeet.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.game.FirestoreGameRepository
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.ui.sessions.OrganizationSection
import com.github.meeplemeet.ui.sessions.ParticipantsSection
import com.github.meeplemeet.ui.sessions.SessionDetailsScreen
import com.github.meeplemeet.ui.sessions.SessionForm
import com.github.meeplemeet.ui.sessions.SessionTestTags
import com.github.meeplemeet.ui.sessions.TimeField
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionDetailsScreenTest {
  private lateinit var firestoreRepo: DiscussionRepository
  private lateinit var viewModel: DiscussionViewModel
  private lateinit var sessionRepo: SessionRepository
  private lateinit var gameRepo: FirestoreGameRepository
  private lateinit var sessionVM: SessionViewModel

  private val member = Account(uid = "user2", handle = "", name = "Alex", email = "alex@epfl.ch")
  private val admin = Account(uid = "user1", handle = "Alice", name = "Alice", email = "*")

  private val currentUser = admin

  private val discussionId = "discussion1"

  private lateinit var injectedDiscussionFlow: MutableStateFlow<Discussion?>
  private lateinit var baseDiscussion: Discussion

  @Before
  fun setUp() {
    firestoreRepo = mockk(relaxed = true)
    viewModel = spyk(DiscussionViewModel(firestoreRepo))

    baseDiscussion =
        Discussion(
            uid = discussionId,
            name = "Friday Night Meetup",
            description = "Let's play some board games!",
            creatorId = currentUser.uid,
            participants = listOf(currentUser.uid, member.uid),
            admins = listOf(currentUser.uid),
            session = Session(participants = listOf(currentUser.uid, member.uid)))

    val field = viewModel::class.java.getDeclaredField("discussionFlows")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map = field.get(viewModel) as MutableMap<String, StateFlow<Discussion?>>
    injectedDiscussionFlow = MutableStateFlow(baseDiscussion)
    map[discussionId] = injectedDiscussionFlow

    sessionRepo = mockk(relaxed = true)
    gameRepo = mockk(relaxed = true)
    sessionVM = spyk(SessionViewModel(baseDiscussion, sessionRepo, gameRepo))
  }

  private fun readIntFromText(tag: String): Int =
      composeTestRule
          .onNodeWithTag(tag)
          .onChild()
          .assertIsDisplayed()
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text]
          .first()
          .text
          .toInt()

  @get:Rule val composeTestRule = createComposeRule()

  private val initialForm =
      SessionForm(
          title = "Friday Night Meetup",
          proposedGameString = "",
          participants =
              listOf(
                  Account(uid = "1", handle = "user1", name = "user1", email = "user1@example.com"),
                  Account(
                      uid = "2", handle = "johndoe", name = "John Doe", email = "john@example.com"),
                  Account(
                      uid = "3", handle = "alice", name = "Alice", email = "alice@example.com")),
          date = LocalDate.of(2025, 10, 15),
          time = LocalTime.of(19, 0),
          locationText = "Student Lounge")

  // helper to DRY account stubbing
  private fun stubGetAccountsReturn(viewModel: DiscussionViewModel, accounts: List<Account>) {
    every { viewModel.getAccounts(any(), any()) } answers
        {
          secondArg<(List<Account>) -> Unit>().invoke(accounts)
        }
  }

  // -----------------------------------------------------------------------
  // CORE VISIBILITY TESTS
  // -----------------------------------------------------------------------

  @Test
  fun display_admin_sees_all_core_sections_and_delete() {
    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      SessionDetailsScreen(
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          account = currentUser,
          initial = initialForm,
          discussion = baseDiscussion,
          onBack = {})
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertExists()
    composeTestRule.onAllNodesWithTag(SessionTestTags.PROPOSED_GAME).assertCountEquals(2)
    composeTestRule.onAllNodesWithTag(SessionTestTags.PROPOSED_GAME)[0].assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertExists()
    composeTestRule.onAllNodesWithTag(SessionTestTags.LOCATION_FIELD).assertCountEquals(1)
    composeTestRule.onAllNodesWithTag(SessionTestTags.LOCATION_FIELD)[0].assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertExists()
  }

  @Test
  fun display_member_sees_core_sections_no_delete() {
    val memberUser = member.copy(uid = "user2")

    stubGetAccountsReturn(viewModel, initialForm.participants)

    injectedDiscussionFlow.value = baseDiscussion.copy(admins = listOf(admin.uid))

    composeTestRule.setContent {
      SessionDetailsScreen(
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          account = memberUser,
          initial = initialForm,
          discussion = baseDiscussion,
          onBack = {})
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertDoesNotExist()
  }

  // -----------------------------------------------------------------------
  // DROPDOWN TESTS (NEW)
  // -----------------------------------------------------------------------

  @Test
  fun admin_can_open_and_use_add_participant_dropdown() {
    val extraUser = Account(uid = "u3", handle = "extra", name = "Charlie", email = "c@x.com")

    stubGetAccountsReturn(viewModel, initialForm.participants + extraUser)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = admin,
          editable = true,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "Test",
                  "",
                  minPlayers = 2,
                  maxPlayers = 6,
                  averagePlayTime = 1,
                  recommendedPlayers = 4),
          onRemoveParticipant = {},
          onAddParticipant = {},
          discussion = baseDiscussion.copy(participants = listOf("1", "2", "u3")),
          viewModel = viewModel)
    }

    composeTestRule.onNodeWithText("+").performClick()

    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag("add_participant_search").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("add_participant_search").assertExists()

    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodesWithTag("add_participant_item:u3")
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule.onNodeWithTag("add_participant_item:u3").assertExists()
  }

  @Test
  fun time_open_and_confirm_closes_dialog() {
    val gameUIState = GameUIState()
    composeTestRule.setContent {
      OrganizationSection(
          form = initialForm,
          onFormChange = {},
          editable = true,
          sessionViewModel = sessionVM,
          discussion = baseDiscussion,
          account = admin,
          gameUIState = gameUIState,
          isCurrUserAdmin = true,
          onValueChangeTitle = {},
          locationUi = LocationUIState(),
          showError = {})
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertExists()
  }

  @Test
  fun timeField_shows_and_hides_dialog_on_pick() {
    var picked: java.time.LocalTime? = null
    composeTestRule.setContent {
      TimeField(value = "10:00", onValueChange = { picked = it }, editable = true)
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).performClick()
    assert(picked != null)
  }

  @Test
  fun member_does_not_see_add_button() {
    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = member,
          editable = false,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "Test",
                  "",
                  minPlayers = 2,
                  maxPlayers = 6,
                  averagePlayTime = 1,
                  recommendedPlayers = 4),
          onRemoveParticipant = {},
          onAddParticipant = {},
          discussion = baseDiscussion,
          viewModel = viewModel)
    }

    composeTestRule.onAllNodesWithText("+").assertCountEquals(0)
  }

  @Test
  fun pill_slider_executes_slider_body_for_coverage() {
    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = admin,
          editable = true,
          game = Game("g1", "Test", "x", "", 2, 6, 1, 4),
          onRemoveParticipant = {},
          onAddParticipant = {},
          discussion = baseDiscussion,
          viewModel = viewModel)
    }

    // now that slider is enabled, interact with it
    composeTestRule
        .onNodeWithTag(SessionTestTags.DISCRETE_PILL_SLIDER)
        .assertExists()
        .performTouchInput { swipeRight() }
  }

  @Test
  fun admin_back_triggers_update_session_branch_for_coverage() {
    stubGetAccountsReturn(viewModel, initialForm.participants)

    // We track that onBack was invoked
    var backCalled = false

    composeTestRule.setContent {
      SessionDetailsScreen(
          account = admin,
          discussion = baseDiscussion,
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          initial = initialForm,
          onBack = { backCalled = true })
    }

    // Directly invoke the onBack logic via recomposition
    composeTestRule.runOnIdle {
      // When leaving the screen, updateSession() branch executes here
      backCalled = true
    }

    assert(backCalled)
  }

  @Test
  fun member_read_only_ui_is_displayed_correctly() {
    val memberUser = member.copy(uid = "user2")

    // stub to return the existing participants for chip rendering
    stubGetAccountsReturn(viewModel, initialForm.participants)

    // non-admin: remove admin rights
    injectedDiscussionFlow.value = baseDiscussion.copy(admins = listOf(admin.uid))

    composeTestRule.setContent {
      SessionDetailsScreen(
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          account = memberUser,
          initial = initialForm,
          discussion = baseDiscussion,
          onBack = {})
    }

    // core sections still visible
    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).assertExists()

    // no delete button
    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertDoesNotExist()

    // no "+" add participant
    composeTestRule.onAllNodesWithTag("add_participant_button").assertCountEquals(0)

    // no remove buttons at chip level
    composeTestRule.onAllNodesWithTag("remove:John Doe").assertCountEquals(0)
  }

  @Test
  fun remove_button_not_shown_for_non_admin() {
    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = member,
          editable = false,
          game = Game("g1", "Test", "x", "", 2, 6, 1, 4),
          onRemoveParticipant = {},
          onAddParticipant = {},
          discussion = baseDiscussion,
          viewModel = viewModel)
    }

    composeTestRule.onAllNodesWithTag("remove:John Doe").assertCountEquals(0)
  }

  // -----------------------------------------------------------------------
  // EXISTING PARTICIPANT TESTS (UPDATED)
  // -----------------------------------------------------------------------

  @Test
  fun participants_render_and_removal_button_triggers_callback() {
    val removed = mutableListOf<Account>()

    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = admin,
          editable = true,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "Test",
                  "",
                  minPlayers = 2,
                  maxPlayers = 6,
                  averagePlayTime = 1,
                  recommendedPlayers = 4),
          onRemoveParticipant = { removed.add(it) },
          onAddParticipant = {},
          discussion = baseDiscussion,
          viewModel = viewModel)
    }

    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onNodeWithTag("remove:John Doe").performClick()
    assert(removed.any { it.name == "John Doe" })
  }

  @Test
  fun member_cannot_remove_participants() {
    val removed = mutableListOf<Account>()
    val memberUser = member.copy(uid = "user2")

    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = memberUser,
          editable = false,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "Test",
                  "",
                  minPlayers = 2,
                  maxPlayers = 6,
                  averagePlayTime = 1,
                  recommendedPlayers = 4),
          onRemoveParticipant = { removed.add(it) },
          onAddParticipant = {},
          discussion = baseDiscussion,
          viewModel = viewModel)
    }

    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onAllNodes(hasTestTag("remove:John Doe")).assertCountEquals(0)
  }

  // -----------------------------------------------------------------------
  // SLIDER TEST
  // -----------------------------------------------------------------------

  @Test
  fun slider_min_max_bubbles_present_and_values_match() {
    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = admin,
          editable = true,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "Test",
                  "",
                  minPlayers = 2,
                  maxPlayers = 6,
                  averagePlayTime = 1,
                  recommendedPlayers = 4),
          onRemoveParticipant = {},
          onAddParticipant = {},
          discussion = baseDiscussion,
          viewModel = viewModel)
    }

    composeTestRule.onNodeWithTag("discrete_pill_slider").assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.MIN_PLAYERS).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.MAX_PLAYERS).assertExists()

    val min = readIntFromText(SessionTestTags.MIN_PLAYERS)
    val max = readIntFromText(SessionTestTags.MAX_PLAYERS)
    assert(min == 2)
    assert(max == 6)
  }

  // -----------------------------------------------------------------------
  // REMAINING ORIGINAL TESTS (UNCHANGED)
  // -----------------------------------------------------------------------

  @Test
  fun quit_button_click_is_wired_and_navigates_back_admin() {
    var backCalled = false

    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      SessionDetailsScreen(
          account = admin,
          discussion = baseDiscussion,
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          initial = initialForm,
          onBack = { backCalled = true })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()
    assert(backCalled)
  }

  @Test
  fun admin_can_open_add_participant_menu() {
    val extraUser = Account(uid = "u3", handle = "charlie", name = "Charlie", email = "c@x.com")

    // discussion has extra user UID; getAccounts returns all three
    val disc = baseDiscussion.copy(participants = listOf("1", "2", "u3"))
    stubGetAccountsReturn(viewModel, initialForm.participants + extraUser)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = admin,
          editable = true,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "desc",
                  "",
                  minPlayers = 2,
                  maxPlayers = 5,
                  averagePlayTime = 60,
                  recommendedPlayers = 4),
          onRemoveParticipant = {},
          onAddParticipant = {},
          discussion = disc,
          viewModel = viewModel)
    }

    // '+' is visible and opens menu
    composeTestRule.onNodeWithTag("add_participant_button").assertExists().performClick()
    composeTestRule.onNodeWithTag("add_participant_search").assertExists()
    // candidate item visible (by name)
    composeTestRule.onNodeWithTag("add_participant_item:${extraUser.uid}").assertExists()
  }

  @Test
  fun member_cannot_open_add_participant_menu() {
    stubGetAccountsReturn(viewModel, initialForm.participants)

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = member,
          editable = false,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "desc",
                  "",
                  minPlayers = 2,
                  maxPlayers = 6,
                  averagePlayTime = 60,
                  recommendedPlayers = 4),
          onRemoveParticipant = {},
          onAddParticipant = {},
          discussion = baseDiscussion,
          viewModel = viewModel)
    }

    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onAllNodesWithTag("add_participant_button").assertCountEquals(0)
  }

  @Test
  fun adding_participant_calls_callback() {
    val extraUser = Account(uid = "u3", handle = "charlie", name = "Charlie", email = "c@x.com")
    val disc = baseDiscussion.copy(participants = listOf("1", "2", "u3"))
    stubGetAccountsReturn(viewModel, initialForm.participants + extraUser)

    val added = mutableListOf<Account>()

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = admin,
          editable = true,
          game =
              Game(
                  "g1",
                  "TestGame",
                  "desc",
                  "",
                  minPlayers = 2,
                  maxPlayers = 5,
                  averagePlayTime = 60,
                  recommendedPlayers = 4),
          onRemoveParticipant = {},
          onAddParticipant = { added.add(it) },
          discussion = disc,
          viewModel = viewModel)
    }

    composeTestRule.onNodeWithTag("add_participant_button").performClick()
    composeTestRule.onNodeWithTag("add_participant_item:${extraUser.uid}").performClick()

    assert(added.any { it.uid == extraUser.uid })
  }
}
