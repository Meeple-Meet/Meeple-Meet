package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.MainActivityViewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.sessions.SessionOverviewViewModel
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.sessions.NO_SESSIONS_DEFAULT_TEXT
import com.github.meeplemeet.ui.sessions.SessionsOverviewScreen
import com.github.meeplemeet.ui.sessions.SessionsOverviewScreenTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import io.mockk.mockk
import java.util.UUID
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionsOverviewScreenTest : FirestoreTests() {

  @get:Rule(order = 0) val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.rule()

  fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var nav: NavigationActions
  private lateinit var navVM: MainActivityViewModel
  private lateinit var viewModel: SessionOverviewViewModel
  private lateinit var account: Account
  private lateinit var capturedDiscussionId: String

  private val testLocation = Location(46.5197, 6.5665, "EPFL")
  private val testGameId = "g_test_chess"

  /* ---------- helpers ---------- */
  private fun sessionCard(discussionId: String) = compose.onNodeWithTag("sessionCard_$discussionId")

  private fun emptyText() = compose.onNodeWithText(NO_SESSIONS_DEFAULT_TEXT)

  @Before
  fun setup() = runBlocking {
    nav = mockk(relaxed = true)
    navVM = MainActivityViewModel()
    viewModel = SessionOverviewViewModel()

    val uid = "uid_" + UUID.randomUUID().toString().take(8)
    account = accountRepository.createAccount(uid, "Tester", "test@x.com", null)

    db.collection(GAMES_COLLECTION_PATH).document(testGameId).set(GameNoUid(name = "Chess")).await()

    capturedDiscussionId = ""

    compose.setContent {
        AppTheme(ThemeMode.DARK) {
          SessionsOverviewScreen(
              viewModel = viewModel,
              navigation = nav,
              account = account,
              unreadCount = account.notifications.count {it -> !it.read},
              onSelectSession = { session -> capturedDiscussionId = session })
        }
    }
  }

  @Test
  fun full_smoke_all_cases() {
    runBlocking {

      /* 1. empty state ---------------------------------------------------- */
      checkpoint("Initial empty state") { emptyText().assertIsDisplayed() }

      /* 2. create session -> card appears --------------------------------- */
      checkpoint("Create session – card appears") {
        runBlocking {
          val discussion =
              discussionRepository.createDiscussion("Game Night", "Let's play", account.uid)
          val game = gameRepository.getGameById(testGameId)
          val futureDate = Timestamp(java.util.Date(System.currentTimeMillis() + 86400000))
          sessionRepository.createSession(
              discussion.uid, "Chess Night", game.uid, futureDate, testLocation, account.uid)
          compose.waitUntil { emptyText().isNotDisplayed() }
          sessionCard(discussion.uid).assertIsDisplayed()
        }
      }

      /* 3. tap card -> correct id passed to callback ----------------------- */
      checkpoint("Tap card – correct discussion id emitted") {
        runBlocking {
          val discussion =
              discussionRepository.createDiscussion("Navigate Me", "Tap test", account.uid)
          val game = gameRepository.getGameById(testGameId)
          val futureDate = Timestamp(java.util.Date(System.currentTimeMillis() + 86400000))
          sessionRepository.createSession(
              discussion.uid, "Tap Night", game.uid, futureDate, testLocation, account.uid)
          compose.waitUntil { sessionCard(discussion.uid).isDisplayed() }
          sessionCard(discussion.uid).performClick()
          assertEquals(discussion.uid, capturedDiscussionId)
        }
      }

      /* 4. delete session -> card disappears ------------------------------- */
      checkpoint("Delete session – card disappears") {
        runBlocking {
          val discussion =
              discussionRepository.createDiscussion("Delete Me", "Will vanish", account.uid)
          val game = gameRepository.getGameById(testGameId)
          val futureDate = Timestamp(java.util.Date(System.currentTimeMillis() + 10000000))
          sessionRepository.createSession(
              discussion.uid, "Vanish Night", game.uid, futureDate, testLocation, account.uid)
          compose.waitUntil { sessionCard(discussion.uid).isDisplayed() }

          sessionRepository.deleteSession(discussion.uid)
          compose.waitUntil { sessionCard(discussion.uid).isNotDisplayed() }
        }
      }

      /* 5. click history card -> popup appears ---------------------------- */
      checkpoint("Click history card – popup appears") {
        runBlocking {
          compose
              .onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_NEXT_SESSIONS)
              .performClick()

          val pastDate =
              Timestamp(java.util.Date(System.currentTimeMillis() - 25 * 60 * 60 * 1000L))
          val discussion =
              discussionRepository.createDiscussion("Past Session", "Old times", account.uid)
          val game = gameRepository.getGameById(testGameId)
          sessionRepository.createSession(
              discussion.uid, "Past Night", game.uid, pastDate, testLocation, account.uid)

          compose.onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_HISTORY).performClick()
          compose.waitUntil(4000) { compose.onNodeWithText("Past Night").isDisplayed() }

          compose.onNodeWithText("Past Night").performClick()

          compose.onAllNodesWithText("Past Night").assertCountEquals(2)
          compose.onNodeWithContentDescription("Close").assertIsDisplayed()

          compose.onNodeWithContentDescription("Close").performClick()
          compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        }
      }
    }
  }

  @Test
  fun archive_flow_comprehensive() {
    runBlocking {
      /* 1. Archive with confirmation (no photo) --------------------------------- */
      checkpoint("Archive without photo - shows confirmation dialog") {
        runBlocking {
          val discussion =
              discussionRepository.createDiscussion("Archive Me", "Will be archived", account.uid)
          val game = gameRepository.getGameById(testGameId)

          val pastDate = Timestamp(java.util.Date(System.currentTimeMillis() - (90 * 60 * 1000L)))
          sessionRepository.createSession(
              discussion.uid, "Archive Night", game.uid, pastDate, testLocation, account.uid)

          compose.waitUntil(2000) {
            compose.onNodeWithText("Automatically archives in", substring = true).isDisplayed()
          }

          sessionCard(discussion.uid).assertIsDisplayed()
          compose
              .onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_ARCHIVE_BUTTON)
              .assertExists()

          sessionCard(discussion.uid).performTouchInput { swipeLeft() }
          compose
              .onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_ARCHIVE_BUTTON)
              .performClick()

          compose.waitUntil(2000) { compose.onNodeWithText("Archive Session").isDisplayed() }
          compose
              .onNodeWithText(
                  "This session doesn't have an image. Are you sure you want to archive it?")
              .assertIsDisplayed()

          compose.onNodeWithText("Archive").performClick()

          compose.waitUntil(2000) { sessionCard(discussion.uid).isNotDisplayed() }
          compose.onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_HISTORY).performClick()
          compose.waitUntil(2000) { compose.onNodeWithText("Archive Night").isDisplayed() }

          compose
              .onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_NEXT_SESSIONS)
              .performClick()
          compose.waitUntil(2000) {
            compose
                .onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_NEXT_SESSIONS)
                .isDisplayed()
          }
        }
      }

      /* 2. Non-admin cannot archive -------------------------------------------- */
      checkpoint("Non-admin cannot see archive button") {
        runBlocking {
          val otherUid = "uid_" + UUID.randomUUID().toString().take(8)
          accountRepository.createAccount(otherUid, "Other User", "other@x.com", null)

          val discussion =
              discussionRepository.createDiscussion("Not Admin", "Cannot archive", otherUid)

          discussionRepository.addUserToDiscussion(discussion.uid, account.uid)

          val game = gameRepository.getGameById(testGameId)

          val withinTwoHours =
              Timestamp(java.util.Date(System.currentTimeMillis() - (60 * 60 * 1000L)))
          sessionRepository.createSession(
              discussion.uid,
              "User Night",
              game.uid,
              withinTwoHours,
              testLocation,
              otherUid,
              account.uid)

          compose.waitUntil(2000) { sessionCard(discussion.uid).isDisplayed() }

          compose
              .onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_ARCHIVE_BUTTON)
              .assertDoesNotExist()
        }
      }

      /* 3. Archive button hidden when more than 2 hours away ------------------- */
      checkpoint("Archive button hidden when more than 2 hours away") {
        runBlocking {
          val discussion =
              discussionRepository.createDiscussion("Future Session", "Too far away", account.uid)
          val game = gameRepository.getGameById(testGameId)

          val futureDate =
              Timestamp(java.util.Date(System.currentTimeMillis() + (3 * 60 * 60 * 1000L)))
          sessionRepository.createSession(
              discussion.uid, "Future Night", game.uid, futureDate, testLocation, account.uid)

          compose.waitUntil(2000) { sessionCard(discussion.uid).isDisplayed() }

          compose
              .onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_ARCHIVE_BUTTON)
              .assertDoesNotExist()
        }
      }
    }
  }

  @After
  fun tearDown(): Unit = runBlocking {
    db.collection(GAMES_COLLECTION_PATH).document(testGameId).delete().await()
  }
}
