package com.github.meeplemeet.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.sessions.SessionScreen
import com.github.meeplemeet.ui.sessions.SessionViewerTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionScreenArchiveTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var sessionVM: SessionViewModel

  private lateinit var admin: Account
  private lateinit var member: Account
  private lateinit var sessionGame: Game

  @Before
  fun setUp() = runBlocking {
    val suffix = System.currentTimeMillis()

    admin =
        accountRepository.createAccount(
            userHandle = "archive_admin_$suffix",
            name = "Archive Admin",
            email = "archive_admin_$suffix@meeple.test",
            photoUrl = null)

    member =
        accountRepository.createAccount(
            userHandle = "archive_member_$suffix",
            name = "Regular Member",
            email = "archive_member_$suffix@meeple.test",
            photoUrl = null)

    val gameId = "archive_test_game_$suffix"
    db.collection(GAMES_COLLECTION_PATH)
        .document(gameId)
        .set(
            GameNoUid(
                name = "Archive Test Game",
                description = "Used in SessionScreenArchive tests",
                imageURL = "https://example.com/game.jpg",
                minPlayers = 2,
                maxPlayers = 4,
                recommendedPlayers = 3,
                averagePlayTime = 60,
                genres = listOf("strategy")))
        .await()

    sessionGame = gameRepository.getGameById(gameId)
    sessionVM = SessionViewModel()
  }

  private suspend fun createDiscussionWithSession(
      startTimeOffsetMs: Long,
      hasPhoto: Boolean = false
  ): Discussion {
    val suffix = System.currentTimeMillis()
    val discussion =
        discussionRepository.createDiscussion(
            name = "Archive Discussion $suffix",
            description = "Discussion for archive tests",
            creatorId = admin.uid,
            participants = listOf(admin.uid, member.uid))

    discussionRepository.addAdminToDiscussion(discussion, admin.uid)

    val sessionDate = Date(System.currentTimeMillis() + startTimeOffsetMs)

    sessionRepository.createSession(
        discussionId = discussion.uid,
        name = "Archive Session",
        gameId = sessionGame.uid,
        date = Timestamp(sessionDate),
        location = Location(name = "Test Location"),
        admin.uid,
        member.uid)

    if (hasPhoto) {
      // Create auth user to allow upload
      val authEmail = "admin_${discussion.uid}@test.com"
      val authPassword = "password123"
      try {
        auth.createUserWithEmailAndPassword(authEmail, authPassword).await()
      } catch (_: Exception) {
        // Ignore if already exists
      }
      auth.signInWithEmailAndPassword(authEmail, authPassword).await()
      val authUid = auth.currentUser!!.uid

      // Add authUid to discussion admins temporarily for upload permission
      discussionRepository.addAdminToDiscussion(discussion, authUid)

      // Upload a dummy photo to storage so loadSessionPhoto doesn't fail
      val dummyPhoto = byteArrayOf(0x00, 0x01, 0x02, 0x03)
      val photoPath = "discussions/${discussion.uid}/session/photo.webp"
      storage.reference.child(photoPath).putBytes(dummyPhoto).await()

      sessionRepository.updateSession(discussion.uid, photoUrl = "https://example.com/photo.webp")
    }

    return discussionRepository.getDiscussion(discussion.uid, context = null)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun session_archive_scenarios() = runBlocking {
    // Define mutable state for the UI to observe
    var currentAccount by androidx.compose.runtime.mutableStateOf(admin)
    var currentDiscussion by
        androidx.compose.runtime.mutableStateOf(
            createDiscussionWithSession(startTimeOffsetMs = 60 * 60 * 1000L) // Default: 1 hour away
            )

    compose.setContent {
      AppTheme {
        SessionScreen(
            account = currentAccount,
            discussion = currentDiscussion,
            onBack = {},
            onEditClick = {},
            viewModel = sessionVM)
      }
    }

    compose.waitUntilAtLeastOneExists(hasTestTag(SessionViewerTestTags.SCREEN_ROOT))

    // 1. Admin - Within Threshold (1 hour away)
    // Expect: Archive button visible, Edit button hidden, Photo box visible (add mode)
    checkpoint("Admin within threshold: Archive visible, Edit hidden") {
      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_ARCHIVE, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_EDIT, useUnmergedTree = true)
          .assertDoesNotExist()

      compose
          .onNodeWithTag(SessionViewerTestTags.SESSION_PHOTO_BOX, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    // 2. Admin - Outside Threshold (3 hours away)
    // Expect: Archive button hidden, Edit button visible, Photo box hidden (no photo)
    currentDiscussion = createDiscussionWithSession(startTimeOffsetMs = 3 * 60 * 60 * 1000L)
    compose.waitForIdle()

    checkpoint("Admin outside threshold: Archive hidden, Edit visible") {
      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_ARCHIVE, useUnmergedTree = true)
          .assertDoesNotExist()

      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_EDIT, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(SessionViewerTestTags.SESSION_PHOTO_BOX, useUnmergedTree = true)
          .assertDoesNotExist()
    }

    // 3. Member - Within Threshold (1 hour away)
    // Expect: Neither button visible, Photo box hidden (no photo)
    currentAccount = member
    currentDiscussion = createDiscussionWithSession(startTimeOffsetMs = 60 * 60 * 1000L)
    compose.waitForIdle()

    checkpoint("Member within threshold: No admin buttons") {
      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_ARCHIVE, useUnmergedTree = true)
          .assertDoesNotExist()

      compose
          .onNodeWithTag(SessionViewerTestTags.TOP_BAR_EDIT, useUnmergedTree = true)
          .assertDoesNotExist()

      compose
          .onNodeWithTag(SessionViewerTestTags.SESSION_PHOTO_BOX, useUnmergedTree = true)
          .assertDoesNotExist()
    }

    // 4. Member - With Photo
    // Expect: Photo box visible (view mode), No add button
    currentDiscussion =
        createDiscussionWithSession(startTimeOffsetMs = 60 * 60 * 1000L, hasPhoto = true)
    compose.waitForIdle()

    checkpoint("Member with photo: Photo box visible") {
      compose
          .onNodeWithTag(SessionViewerTestTags.SESSION_PHOTO_BOX, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(SessionViewerTestTags.SESSION_PHOTO_ADD_BUTTON, useUnmergedTree = true)
          .assertDoesNotExist()
    }
  }
}
