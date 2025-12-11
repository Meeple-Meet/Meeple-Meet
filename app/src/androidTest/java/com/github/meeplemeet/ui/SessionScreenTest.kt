package com.github.meeplemeet.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.components.GameDetailsCard
import com.github.meeplemeet.ui.sessions.SessionScreen
import com.github.meeplemeet.ui.sessions.SessionViewerTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionViewerScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var sessionVM: SessionViewModel

  private lateinit var admin: Account
  private lateinit var member: Account
  private lateinit var extraParticipants: List<Account>

  private lateinit var baseDiscussion: Discussion
  private lateinit var sessionGame: Game

  @Before
  fun setUp() = runBlocking {
    val suffix = System.currentTimeMillis()

    admin =
        accountRepository.createAccount(
            userHandle = "session_admin_$suffix",
            name = "Session Admin",
            email = "session_admin_$suffix@meeple.test",
            photoUrl = null)

    member =
        accountRepository.createAccount(
            userHandle = "session_member_$suffix",
            name = "Regular Member",
            email = "session_member_$suffix@meeple.test",
            photoUrl = null)

    // Create enough extra participants to trigger the custom scrollbar
    extraParticipants =
        (1..8).map { idx ->
          accountRepository.createAccount(
              userHandle = "session_extra_${idx}_$suffix",
              name = "Extra Player $idx",
              email = "session_extra_${idx}_$suffix@meeple.test",
              photoUrl = null)
        }

    val participantIds = listOf(admin.uid, member.uid) + extraParticipants.map { it.uid }

    baseDiscussion =
        discussionRepository.createDiscussion(
            name = "Session Viewer Discussion",
            description = "Discussion for session viewer tests",
            creatorId = admin.uid,
            participants = participantIds)

    discussionRepository.addAdminToDiscussion(baseDiscussion, admin.uid)

    val gameId = "session_viewer_test_game_$suffix"
    db.collection(GAMES_COLLECTION_PATH)
        .document(gameId)
        .set(
            GameNoUid(
                name = "Session Viewer Game",
                description = "Used in SessionScreen UI tests",
                imageURL = "https://example.com/session_viewer_game.jpg",
                minPlayers = 2,
                maxPlayers = 4,
                recommendedPlayers = 3,
                averagePlayTime = 60,
                genres = listOf("strategy")))
        .await()

    sessionGame = gameRepository.getGameById(gameId)
    val futureDate = Timestamp(java.util.Date(System.currentTimeMillis() + (3 * 60 * 60 * 1000L)))

    sessionRepository.createSession(
        discussionId = baseDiscussion.uid,
        name = "Friday Night Session",
        gameId = sessionGame.uid,
        date = futureDate,
        location = Location(name = "Meeple Café"),
        *participantIds.toTypedArray())

    baseDiscussion = discussionRepository.getDiscussion(baseDiscussion.uid)

    sessionVM = SessionViewModel()
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 1 – Admin smoke test: top bar, image, basic info, participants, scrollbar
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun sessionViewer_adminSmoke_showsCoreSectionsAndScrollbar() {
    var backCalled = false
    var editCalled = false

    compose.setContent {
      AppTheme {
        SessionScreen(
            account = admin,
            discussion = baseDiscussion,
            onBack = { backCalled = true },
            onEditClick = { editCalled = true },
            viewModel = sessionVM)
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(SessionViewerTestTags.SCREEN_ROOT), timeoutMillis = 5_000)

    checkpoint("Top bar and admin edit button are visible") {
      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_BACK, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
          .assertHasClickAction()

      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_EDIT, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
          .assertHasClickAction()
    }

    checkpoint("Game header image is eventually displayed") {
      compose.waitUntilAtLeastOneExists(
          hasTestTag(SessionViewerTestTags.GAME_IMAGE), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(SessionViewerTestTags.GAME_IMAGE, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    checkpoint("Basic info and participants sections are visible") {
      compose
          .onNodeWithTag(SessionViewerTestTags.BASIC_INFO_SECTION, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(SessionViewerTestTags.PARTICIPANTS_SECTION, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    checkpoint("Participants list and custom scrollbar are visible with many participants") {
      compose.waitUntilAtLeastOneExists(
          hasTestTag(SessionViewerTestTags.PARTICIPANTS_LIST), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(SessionViewerTestTags.PARTICIPANTS_LIST, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose.waitUntilAtLeastOneExists(
          hasTestTag(SessionViewerTestTags.SCROLLBAR_TRACK), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(SessionViewerTestTags.SCROLLBAR_TRACK, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(SessionViewerTestTags.SCROLLBAR_THUMB, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    checkpoint("Edit button is wired") {
      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_EDIT, useUnmergedTree = true)
          .performClick()
      assert(editCalled)
    }

    checkpoint("Back button is wired") {
      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_BACK, useUnmergedTree = true)
          .performClick()
      assert(backCalled)
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 2 – Member view: no edit button, but leave button is visible
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun sessionViewer_memberView_hidesEditButton_showsLeaveButton() {
    var backCalled = false

    compose.setContent {
      AppTheme {
        SessionScreen(
            account = member,
            discussion = baseDiscussion,
            onBack = { backCalled = true },
            onEditClick = {},
            viewModel = sessionVM)
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(SessionViewerTestTags.SCREEN_ROOT), timeoutMillis = 5_000)

    checkpoint("Top bar is visible in member view") {
      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    checkpoint("Edit button is not shown for non-admin member") {
      compose
          .onAllNodesWithTag(SessionViewerTestTags.TOP_BAR_EDIT, useUnmergedTree = true)
          .assertCountEquals(0)
    }

    checkpoint("Leave button is visible and clickable") {
      compose.waitUntilAtLeastOneExists(
          hasTestTag(SessionViewerTestTags.LEAVE_BUTTON), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(SessionViewerTestTags.LEAVE_BUTTON, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
          .assertHasClickAction()

      compose
          .onNodeWithTag(SessionViewerTestTags.LEAVE_BUTTON, useUnmergedTree = true)
          .performClick()

      assert(backCalled)
    }
  }
  // ────────────────────────────────────────────────────────────────────────────
  // TEST 3 – GameDetailsCard: shows title + overview section
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun gameDetailsCard_showsTitleAndOverviewSection() {
    compose.setContent {
      AppTheme {
        // Use the real game we created in setUp so the object is valid
        GameDetailsCard(
            game = sessionGame,
            onClose = {},
        )
      }
    }

    checkpoint("GameDetailsCard shows title and Overview section") {
      // Game title is shown as headline
      compose
          .onNodeWithText(sessionGame.name, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      // "Overview:" header text from GameDescription()
      compose.onNodeWithText("Overview:", useUnmergedTree = true).assertExists().assertIsDisplayed()
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 4 – Member leave: removes member from participants in repository
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun sessionViewer_leaveAsMember_removesMemberFromSessionParticipants() {
    var backCalled = false

    compose.setContent {
      AppTheme {
        SessionScreen(
            account = member,
            discussion = baseDiscussion,
            onBack = { backCalled = true },
            onEditClick = {},
            viewModel = sessionVM)
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(SessionViewerTestTags.LEAVE_BUTTON), timeoutMillis = 5_000)

    checkpoint("Member taps leave button") {
      compose
          .onNodeWithTag(SessionViewerTestTags.LEAVE_BUTTON, useUnmergedTree = true)
          .assertExists()
          .assertHasClickAction()
          .performClick()
    }

    checkpoint("Repository no longer lists member as session participant") {
      runBlocking {
        // Give SessionViewModel coroutine a moment to perform update
        val updatedSession = sessionRepository.getSession(baseDiscussion.uid)
        compose.waitUntil { updatedSession != null }
        assert(member.uid !in updatedSession!!.participants) {
          "Expected member to be removed from session participants"
        }
      }
      assert(backCalled)
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 5 – Admin leave when sole admin & participant deletes the session
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun sessionViewer_leaveAsOnlyAdmin_deletesSessionWhenNoAdminsRemain() {
    lateinit var soloAdmin: Account
    lateinit var soloDiscussion: Discussion

    runBlocking {
      val suffix = System.currentTimeMillis()

      soloAdmin =
          accountRepository.createAccount(
              userHandle = "solo_admin_$suffix",
              name = "Solo Admin",
              email = "solo_admin_$suffix@meeple.test",
              photoUrl = null)

      soloDiscussion =
          discussionRepository.createDiscussion(
              name = "Solo Admin Discussion",
              description = "Admin alone in session",
              creatorId = soloAdmin.uid,
              participants = listOf(soloAdmin.uid))

      discussionRepository.addAdminToDiscussion(soloDiscussion, soloAdmin.uid)
      val futureDate = Timestamp(java.util.Date(System.currentTimeMillis() + (3 * 60 * 60 * 1000L)))

      sessionRepository.createSession(
          discussionId = soloDiscussion.uid,
          name = "Solo Admin Session",
          gameId = sessionGame.uid,
          date = futureDate,
          location = Location(name = "Solo Place"),
          soloAdmin.uid)

      soloDiscussion = discussionRepository.getDiscussion(soloDiscussion.uid)
    }

    var backCalled = false

    compose.setContent {
      AppTheme {
        SessionScreen(
            account = soloAdmin,
            discussion = soloDiscussion,
            onBack = { backCalled = true },
            onEditClick = {},
            viewModel = sessionVM)
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(SessionViewerTestTags.LEAVE_BUTTON), timeoutMillis = 5_000)

    checkpoint("Only admin taps leave button") {
      compose
          .onNodeWithTag(SessionViewerTestTags.LEAVE_BUTTON, useUnmergedTree = true)
          .assertExists()
          .assertHasClickAction()
          .performClick()
    }

    checkpoint("Session is deleted from repository when no admins remain") {
      runBlocking {
        val updatedSession = sessionRepository.getSession(soloDiscussion.uid)
        compose.waitUntil { updatedSession == null }
      }
      assert(backCalled)
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 6 – No session on discussion: shows empty state and no screen root tag
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  fun sessionViewer_noSession_showsEmptyStateInsteadOfMainLayout() {
    lateinit var discussionWithoutSession: Discussion

    runBlocking {
      val suffix = System.currentTimeMillis()

      val owner =
          accountRepository.createAccount(
              userHandle = "no_session_owner_$suffix",
              name = "No Session Owner",
              email = "no_session_owner_$suffix@meeple.test",
              photoUrl = null)

      discussionWithoutSession =
          discussionRepository.createDiscussion(
              name = "No Session Discussion",
              description = "Discussion without session",
              creatorId = owner.uid,
              participants = listOf(owner.uid))

      discussionRepository.addAdminToDiscussion(discussionWithoutSession, owner.uid)
    }

    compose.setContent {
      AppTheme {
        SessionScreen(
            account = admin, // arbitrary account, session is null anyway
            discussion = discussionWithoutSession,
            onBack = {},
            onEditClick = {},
            viewModel = sessionVM)
      }
    }

    checkpoint("Empty state text is shown when discussion has no session") {
      compose
          .onNodeWithText("No session available", useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    checkpoint("Main session viewer layout is not rendered without session") {
      compose
          .onAllNodesWithTag(SessionViewerTestTags.SCREEN_ROOT, useUnmergedTree = true)
          .assertCountEquals(0)
    }
  }
}
