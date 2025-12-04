package com.github.meeplemeet.integration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.sessions.SessionOverviewViewModel
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileOutputStream
import junit.framework.TestCase.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test

/**
 * Integration test for SessionOverviewViewModel.
 *
 * Uses real Firestore repositories (no mocks) and verifies that sessionMapFlow emits the correct
 * live map of discussion-id → Session when sessions are created / updated / deleted.
 */
class SessionOverviewViewModelTest : FirestoreTests() {

  private lateinit var viewModel: SessionOverviewViewModel
  private lateinit var account: Account
  private lateinit var existingGameId: String
  private val testLocation = Location(46.5197, 6.5665, "EPFL")

  @Before
  fun setup() = runBlocking {
    // Sign in anonymously for Firebase access
    auth.signInAnonymously().await()

    viewModel = SessionOverviewViewModel()
    account =
        accountRepository.createAccount(
            "sessionTestUser", "Session Tester", "session@test.com", photoUrl = null)
    addGameDoc("g_chess", "Chess", genres = listOf("1", "2"))
    existingGameId = "g_chess"
  }

  @Test
  fun sessionMapFlow_emitsEmptyMap_whenNoSessions() = runBlocking {
    val map = viewModel.sessionMapFlow(account.uid).first()
    assertTrue(map.isEmpty())
  }

  @Test
  fun sessionMapFlow_emitsMapWithNewSession() = runBlocking {
    // create a discussion + session
    val discussion = discussionRepository.createDiscussion("Game Night", "Let's play", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    sessionRepository.createSession(
        discussion.uid, "Chess Night", game.uid, Timestamp.now(), testLocation, account.uid)

    delay(100) // wait for Firestore snapshot

    val map = viewModel.sessionMapFlow(account.uid).first()
    assertEquals(1, map.size)
    assertEquals("Chess Night", map[discussion.uid]?.name)
  }

  @Test
  fun sessionMapFlow_removesEntry_whenSessionDeleted() = runBlocking {
    // create
    val discussion =
        discussionRepository.createDiscussion("To Be Deleted", "Will disappear", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    sessionRepository.createSession(
        discussion.uid, "Delete Me", game.uid, Timestamp.now(), testLocation, account.uid)
    delay(100)
    assertEquals(1, viewModel.sessionMapFlow(account.uid).first().size)

    // delete
    sessionRepository.deleteSession(discussion.uid)
    delay(100)

    val map = viewModel.sessionMapFlow(account.uid).first()
    assertTrue(map.isEmpty())
  }

  @Test
  fun getGameNameByGameId_returnsName_whenGameExists() = runBlocking {
    val name = viewModel.getGameNameByGameId(existingGameId)
    assertEquals("Chess", name)
  }

  @Test
  fun getGameNameByGameId_returnsNull_whenGameMissing() = runBlocking {
    val name = viewModel.getGameNameByGameId("nonexistent")
    assertNull(name)
  }

  @Test
  fun getArchivedSessionPhotoUrls_returnsPhotoUrls() = runBlocking {
    // Archive a session with a photo URL
    val discussion = discussionRepository.createDiscussion("Archive Photo", "Test", account.uid)
    val game = gameRepository.getGameById(existingGameId)
    val session =
        sessionRepository.createSession(
            discussion.uid,
            "Archive Photo Session",
            game.uid,
            Timestamp.now(),
            testLocation,
            account.uid)
    val photoUrl = "https://example.com/archived_photo.webp"
    val archivedId = java.util.UUID.randomUUID().toString()
    sessionRepository.archiveSession(discussion.uid, archivedId, photoUrl)

    delay(200) // Wait for archiving to complete

    // Retrieve archived photo URLs directly from repository
    val urls = sessionRepository.getArchivedSessionPhotoUrls(account.uid)
    assertTrue(urls.contains(photoUrl))
  }

  @Test
  fun getArchivedSessionByPhotoUrl_returnsSession() = runBlocking {
    // Archive a session with a photo URL
    val discussion = discussionRepository.createDiscussion("Find By Photo", "Test", account.uid)
    val game = gameRepository.getGameById(existingGameId)
    val session =
        sessionRepository.createSession(
            discussion.uid,
            "Find By Photo Session",
            game.uid,
            Timestamp.now(),
            testLocation,
            account.uid)
    val photoUrl = "https://example.com/find_by_photo.webp"
    val archivedId = java.util.UUID.randomUUID().toString()
    sessionRepository.archiveSession(discussion.uid, archivedId, photoUrl)

    delay(200) // Wait for archiving to complete

    // Find session by photo URL directly from repository
    val foundSession = sessionRepository.getArchivedSessionByPhotoUrl(photoUrl)
    assertNotNull(foundSession)
    assertEquals(photoUrl, foundSession?.photoUrl)
    assertEquals("Find By Photo Session", foundSession?.name)
  }

  @Test
  fun getArchivedSessionPhotoUrls_returnsEmpty_whenNoArchivedSessions() = runBlocking {
    // User has no archived sessions
    val urls = sessionRepository.getArchivedSessionPhotoUrls(account.uid)
    assertTrue(urls.isEmpty())
  }

  @Test
  fun getArchivedSessionPhotoUrls_returnsEmpty_whenPageOutOfBounds() = runBlocking {
    // Archive one session
    val discussion = discussionRepository.createDiscussion("Single Archive", "Test", account.uid)
    val game = gameRepository.getGameById(existingGameId)
    sessionRepository.createSession(
        discussion.uid, "Single Session", game.uid, Timestamp.now(), testLocation, account.uid)
    val photoUrl = "https://example.com/single_photo.webp"
    val archivedId = java.util.UUID.randomUUID().toString()
    sessionRepository.archiveSession(discussion.uid, archivedId, photoUrl)

    delay(200)

    // Request page 1 (but only 1 item exists in page 0)
    val urls = sessionRepository.getArchivedSessionPhotoUrls(account.uid, page = 1)
    assertTrue(urls.isEmpty())
  }

  @Test
  fun getArchivedSessionByPhotoUrl_returnsNull_whenPhotoUrlNotFound() = runBlocking {
    val foundSession =
        sessionRepository.getArchivedSessionByPhotoUrl("https://nonexistent.com/photo.webp")
    assertNull(foundSession)
  }

  @Test
  fun viewModel_getArchivedSessionByPhotoUrl_returnsSession() = runBlocking {
    // Archive a session with a photo URL
    val discussion =
        discussionRepository.createDiscussion("ViewModel Find By Photo", "Test", account.uid)
    val game = gameRepository.getGameById(existingGameId)
    sessionRepository.createSession(
        discussion.uid,
        "ViewModel Find By Photo Session",
        game.uid,
        Timestamp.now(),
        testLocation,
        account.uid)
    val photoUrl = "https://example.com/viewmodel_find_by_photo.webp"
    val archivedId = java.util.UUID.randomUUID().toString()
    sessionRepository.archiveSession(discussion.uid, archivedId, photoUrl)

    delay(200) // Wait for archiving to complete

    // Use ViewModel method with callback
    var foundSession: com.github.meeplemeet.model.sessions.Session? = null
    viewModel.getArchivedSessionByPhotoUrl(photoUrl) { foundSession = it }

    delay(100) // Wait for callback to execute

    assertNotNull(foundSession)
    assertEquals(photoUrl, foundSession?.photoUrl)
    assertEquals("ViewModel Find By Photo Session", foundSession?.name)
  }

  @Test
  fun viewModel_getArchivedSessionByPhotoUrl_returnsNull_whenPhotoUrlNotFound() = runBlocking {
    // Use ViewModel method with callback
    var foundSession: com.github.meeplemeet.model.sessions.Session? = null
    viewModel.getArchivedSessionByPhotoUrl("https://nonexistent.com/viewmodel_photo.webp") {
      foundSession = it
    }

    delay(100) // Wait for callback to execute

    assertNull(foundSession)
  }

  @Test
  fun updateSession_archivesPassedSession_withoutPhoto() = runBlocking {
    // Create a discussion and session that has passed (3 hours ago)
    val discussion =
        discussionRepository.createDiscussion("Past Session", "Happened earlier", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    val twentyFiveHoursAgo = Timestamp(java.util.Date(System.currentTimeMillis() - 25 * 60 * 60 * 1000L))

    sessionRepository.createSession(
        discussion.uid, "Past Chess Night", game.uid, twentyFiveHoursAgo, testLocation, account.uid)

    delay(100)

    // Verify session exists before archiving
    val sessionBefore = viewModel.sessionMapFlow(account.uid).first()
    assertEquals(1, sessionBefore.size)

    // Verify session is considered passed
    assertTrue(sessionRepository.isSessionPassed(discussion.uid))

    // Call updateSession
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    viewModel.updateSession(context, discussion.uid)

    delay(2000) // wait for archiving to complete

    // Verify session is no longer in active sessions
    val sessionAfter = viewModel.sessionMapFlow(account.uid).first()
    assertTrue(sessionAfter.isEmpty())

    // Verify session appears in archived sessions (photos list will be empty since no photo)
    val archivedUrls = sessionRepository.getArchivedSessionPhotoUrls(account.uid)
    assertTrue(archivedUrls.isEmpty())
  }

  @Test
  fun updateSession_archivesPassedSession_withPhoto() = runBlocking {
    // Create a discussion and session that has passed with a photo
    val discussion =
        discussionRepository.createDiscussion("Past Session With Photo", "Had a photo", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    val twentyFiveHoursAgo = Timestamp(java.util.Date(System.currentTimeMillis() - 25 * 60 * 60 * 1000L))

    sessionRepository.createSession(
        discussion.uid, "Photo Chess Night", game.uid, twentyFiveHoursAgo, testLocation, account.uid)

    // Add a photo to the session - create a valid image file
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val testImagePath = createTestImage(context, "test_session_photo.jpg")

    val photoUrl = imageRepository.saveSessionPhoto(context, discussion.uid, testImagePath)
    sessionRepository.updateSessionPhoto(discussion.uid, photoUrl)
    delay(100)

    // Clean up test image
    File(testImagePath).delete()

    // Verify session has photo before archiving
    val sessionWithPhoto = sessionRepository.getSession(discussion.uid)
    assertNotNull(sessionWithPhoto?.photoUrl)

    // Call updateSession
    viewModel.updateSession(context, discussion.uid)

    delay(2000) // Wait for archiving and photo moving to complete

    // Verify session is archived
    val sessionAfter = viewModel.sessionMapFlow(account.uid).first()
    assertTrue(sessionAfter.isEmpty())

    // Verify archived session has the moved photo URL
    val archivedUrls = sessionRepository.getArchivedSessionPhotoUrls(account.uid)
    assertEquals(1, archivedUrls.size)
    assertTrue(
        archivedUrls[0].startsWith("https://") && archivedUrls[0].contains("archived_sessions"))
  }

  @Test
  fun updateSession_doesNotArchive_nonPassedSession() = runBlocking {
    // Create a discussion and session with a future timestamp
    val discussion =
        discussionRepository.createDiscussion("Future Session", "Will happen later", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    // Create a timestamp for 1 hour from now
    val oneHourFromNow = Timestamp(System.currentTimeMillis() / 1000 + (1 * 60 * 60), 0)

    sessionRepository.createSession(
        discussion.uid, "Future Chess Night", game.uid, oneHourFromNow, testLocation, account.uid)

    delay(100)

    // Verify session exists
    val sessionBefore = viewModel.sessionMapFlow(account.uid).first()
    assertEquals(1, sessionBefore.size)

    // Call updateSession
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    viewModel.updateSession(context, discussion.uid)

    delay(200)

    // Verify session still exists (not archived)
    val sessionAfter = viewModel.sessionMapFlow(account.uid).first()
    assertEquals(1, sessionAfter.size)
    assertEquals("Future Chess Night", sessionAfter[discussion.uid]?.name)
  }

  @Test
  fun archiveSession_manually_archives_session() = runBlocking {
    // Create a discussion and session
    val discussion =
        discussionRepository.createDiscussion("Manual Archive", "Will be manually archived", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    // Create a session (time doesn't matter for manual archiving in ViewModel)
    val sessionDate = Timestamp(System.currentTimeMillis() / 1000 - (1 * 60 * 60), 0)

    sessionRepository.createSession(
        discussion.uid, "Manual Archive Night", game.uid, sessionDate, testLocation, account.uid)

    delay(100)

    // Verify session exists before archiving
    val sessionBefore = viewModel.sessionMapFlow(account.uid).first()
    assertEquals(1, sessionBefore.size)

    // Call archiveSession manually
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    viewModel.archiveSession(context, discussion.uid)

    delay(2000) // wait for archiving to complete

    // Verify session is no longer in active sessions
    val sessionAfter = viewModel.sessionMapFlow(account.uid).first()
    assertTrue(sessionAfter.isEmpty())

    // Verify session appears in archived sessions
    val archivedSessions = sessionRepository.getArchivedSessions(account.uid)
    assertEquals(1, archivedSessions.size)
    assertEquals("Manual Archive Night", archivedSessions[0].name)
  }

  private fun createTestImage(context: Context, filename: String): String {
    val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.RED)

    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }
    bitmap.recycle()

    return file.absolutePath
  }

  private fun addGameDoc(id: String, name: String, genres: List<String> = emptyList()) =
      runBlocking {
        db.collection(GAMES_COLLECTION_PATH)
            .document(id)
            .set(GameNoUid(name = name, genres = genres))
            .await()
      }
}
