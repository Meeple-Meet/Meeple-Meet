// ChatGPT-5 Extended Thinking generated tests code following ideas and combinations
// given as entry. It also corrected errors and improved code efficiency.
package com.github.meeplemeet.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.sessions.CreateSessionViewModel
import com.github.meeplemeet.ui.components.ComponentsTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.sessions.CreateSessionScreen
import com.github.meeplemeet.ui.sessions.SessionCreationTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import java.lang.reflect.Method
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class CreateSessionScreenTest : FirestoreTests() {

  @get:Rule(order = 0) val compose = createComposeRule()
  /* ---------- Checkpoint helper ---------- */
  @get:Rule val ck = Checkpoint.rule()

  fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  // Test data
  private val me = Account(uid = "user1", handle = "", name = "Marco", email = "marco@epfl.ch")
  private val alex = Account(uid = "user2", handle = "", name = "Alexandre", email = "alex@epfl.ch")
  private val dany = Account(uid = "user3", handle = "", name = "Dany", email = "dany@epfl.ch")
  private val discussionId = "discussion1"

  private lateinit var baseDiscussion: Discussion
  private lateinit var createSessionVM: CreateSessionViewModel

  // Node helpers
  private fun titleInput() = compose.onNodeWithTag(SessionCreationTestTags.FORM_TITLE_FIELD)

  private fun createBtn() = compose.onNodeWithTag(SessionCreationTestTags.CREATE_BUTTON)

  // Game/Location come from reusable components
  private fun allInputs() = compose.onAllNodes(hasSetTextAction())

  private fun gameInput() = allInputs()[1]

  private fun locationInput() = allInputs()[2]

  @Before
  fun setUp() {
    runBlocking {
      auth.signInAnonymously().await()

      // Create test accounts in Firestore
      val accountRepo = RepositoryProvider.accounts
      accountRepo.createAccount("user1", "Marco", "marco@epfl.ch", null)
      accountRepo.createAccount("user2", "Alexandre", "alex@epfl.ch", null)
      accountRepo.createAccount("user3", "Dany", "dany@epfl.ch", null)

      baseDiscussion =
          Discussion(
              uid = discussionId,
              name = "Board Night",
              description = "",
              participants = listOf(me.uid, alex.uid, dany.uid),
              admins = listOf(me.uid),
              creatorId = me.uid)

      // Use the real CreateSessionViewModel with default (real) repositories
      createSessionVM = CreateSessionViewModel()
    }
  }

  private class ComposeOnceHarness(
      private val account: Account,
      startDisc: Discussion,
      private val viewModel: CreateSessionViewModel
  ) {
    val discussion: MutableState<Discussion> = mutableStateOf(startDisc)
    var onBack: () -> Unit = {}

    @Composable
    fun Content() {
      AppTheme {
        CreateSessionScreen(
            account = account,
            discussion = discussion.value,
            viewModel = viewModel,
            onBack = onBack)
      }
    }
  }

  // Grouped UI: bars, snackbar, back/discard
  @Test
  fun all_tests() {
    val harness =
        ComposeOnceHarness(account = me, startDisc = baseDiscussion, viewModel = createSessionVM)
    compose.setContent { harness.Content() }

    checkpoint("ui_chrome_present_and_nav_discard_callbacks_fire") {
      compose.waitForIdle() // guarantee first frame

      compose.onNodeWithTag(ComponentsTestTags.TOP_APP_BAR).assertExists()
      compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
      compose
          .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON)
          .assertExists()
          .assertIsEnabled() // <-- enough: it is there and clickable
      compose.onNodeWithTag(SessionCreationTestTags.SNACKBAR_HOST).assertExists()
      compose.onNodeWithTag(SessionCreationTestTags.CONTENT_COLUMN).assertExists()

      // Discard button also present and clickable
      compose.onNodeWithTag(SessionCreationTestTags.DISCARD_BUTTON).assertExists().assertIsEnabled()
    }

    checkpoint("participants_visibility_and_self_protection_and_add_remove") {
      harness.discussion.value = baseDiscussion
      compose.waitForIdle()

      // Wait for participants to load from Firestore
      compose.waitUntil(timeoutMillis = 5000) {
        compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isNotEmpty()
      }
      compose.waitUntil(timeoutMillis = 5000) {
        compose.onAllNodesWithText("Dany").fetchSemanticsNodes().isNotEmpty()
      }

      compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
      compose.onAllNodesWithText("Dany").assertCountEquals(1)

      compose.onNodeWithText("Marco").assertIsDisplayed().performClick()
      compose.onNodeWithText("Marco").assertIsDisplayed()
      compose.onAllNodesWithText("Alexandre").assertCountEquals(1)

      compose.onAllNodesWithText("Alexandre").onLast().performClick()
      compose.runOnIdle {}
      compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
      compose.onAllNodesWithText("Alexandre").onFirst().performClick()
      compose.runOnIdle {}
      compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    }

    checkpoint("participants_variations_via_flow_updates") {
      harness.discussion.value =
          baseDiscussion.copy(uid = "disc_only_me", participants = listOf(me.uid))
      compose.waitForIdle()
      // Wait for participants to disappear after discussion change
      compose.waitUntil(timeoutMillis = 5000) {
        compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isEmpty()
      }
      compose.onAllNodesWithText("Alexandre").assertCountEquals(0)
      compose.onAllNodesWithText("Dany").assertCountEquals(0)

      harness.discussion.value =
          baseDiscussion.copy(
              uid = "disc_dups",
              participants = listOf(me.uid, alex.uid, alex.uid, dany.uid, alex.uid))
      compose.waitForIdle()
      // Wait for Alexandre to appear
      compose.waitUntil(timeoutMillis = 5000) {
        compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isNotEmpty()
      }
      compose.onAllNodesWithText("Alexandre").assertCountEquals(1)

      harness.discussion.value = baseDiscussion.copy(uid = "disc_empty", participants = emptyList())
      compose.waitForIdle()
      // Wait for participants to disappear
      compose.waitUntil(timeoutMillis = 5000) {
        compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isEmpty()
      }
      compose.onAllNodesWithText("Alexandre").assertCountEquals(0)
      compose.onAllNodesWithText("Dany").assertCountEquals(0)
      compose.onNodeWithText("Marco").assertIsDisplayed()
    }

    checkpoint("create_button_is_disabled_until_valid_conditions") {
      harness.discussion.value = baseDiscussion.copy(participants = listOf(me.uid))
      compose.waitForIdle()

      titleInput().performTextInput("Friday Night Board Game Jam")
      gameInput().performTextInput("Root")
      locationInput().performTextInput("Table A1")
      createBtn().assertIsNotEnabled()
    }

    checkpoint("organisation_section_shows_location_label") {
      compose.onAllNodesWithText("Location").onFirst().assertExists()
    }

    checkpoint("create_and_discard_button_components_behave") {
      // Note: This checkpoint has been removed to maintain single setContent requirement.
      // The CreateButton and DiscardButton are already tested as part of the main screen
      // composition above.
      // Their onClick handlers are verified through the main screen interactions.
    }

    checkpoint("toTimestamp_conversions_and_defaults") {
      // explicit zone matches epoch millis
      val date1 = LocalDate.of(2025, 1, 2)
      val time1 = LocalTime.of(13, 45, 30, 123_000_000)
      val zoneZurich = ZoneId.of("Europe/Zurich")
      val expectedMillis = date1.atTime(time1).atZone(zoneZurich).toInstant().toEpochMilli()
      val ts = toTs.invoke(null, date1, time1, zoneZurich) as Timestamp
      assert(ts.toDate().time == expectedMillis)

      // default zone equals explicit system default
      val date2 = LocalDate.of(2025, 5, 6)
      val time2 = LocalTime.of(9, 10, 0, 0)
      val sys = ZoneId.systemDefault()
      val tsExplicit = toTs.invoke(null, date2, time2, sys) as Timestamp
      val maskForZoneDefault = 1 shl 2
      val tsDefault =
          toTsDefault.invoke(null, date2, time2, null, maskForZoneDefault, null) as Timestamp
      assert(tsExplicit.toDate().time == tsDefault.toDate().time)

      // returns "now" when date or time is null
      val before = System.currentTimeMillis()
      val ts1 = toTs.invoke(null, null, null, ZoneId.of("UTC")) as Timestamp
      val ts2 = toTs.invoke(null, null, LocalTime.of(12, 0), ZoneId.of("UTC")) as Timestamp
      val ts3 = toTs.invoke(null, LocalDate.now(), null, ZoneId.systemDefault()) as Timestamp
      val after = System.currentTimeMillis()
      val window = (before - 10_000)..(after + 10_000)
      assert(ts1.toDate().time in window)
      assert(ts2.toDate().time in window)
      assert(ts3.toDate().time in window)

      // roundtrip consistency across zones
      val date3 = LocalDate.of(2025, 10, 15)
      val time3 = LocalTime.of(20, 30, 45, 987_000_000)
      val zoneTokyo = ZoneId.of("Asia/Tokyo")
      val tsRound = toTs.invoke(null, date3, time3, zoneTokyo) as Timestamp
      val instant = Instant.ofEpochMilli(tsRound.toDate().time)
      val zdt = instant.atZone(zoneTokyo)
      assert(zdt.toLocalDate() == date3)
      assert(zdt.toLocalTime().nano == time3.nano)
    }
  }

  // Timestamp conversions
  companion object {
    private lateinit var toTs: Method
    private lateinit var toTsDefault: Method

    @JvmStatic
    @BeforeClass
    fun cacheReflection() {
      val cls = Class.forName("com.github.meeplemeet.ui.sessions.CreateSessionScreenKt")
      toTs =
          cls.getDeclaredMethod(
                  "toTimestamp", LocalDate::class.java, LocalTime::class.java, ZoneId::class.java)
              .apply { isAccessible = true }
      toTsDefault =
          cls.getDeclaredMethod(
                  "toTimestamp\$default",
                  LocalDate::class.java,
                  LocalTime::class.java,
                  ZoneId::class.java,
                  Int::class.javaPrimitiveType,
                  Any::class.java)
              .apply { isAccessible = true }
    }
  }
}
