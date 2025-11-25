package com.github.meeplemeet.model

import android.content.Context
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.sessions.SessionOverviewViewModel
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionOverviewViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var viewModel: SessionOverviewViewModel
  private lateinit var mockSessionRepository: SessionRepository
  private lateinit var mockGameRepository: GameRepository
  private lateinit var mockImageRepository: ImageRepository
  private lateinit var mockContext: Context

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockSessionRepository = mockk(relaxed = true)
    mockGameRepository = mockk(relaxed = true)
    mockImageRepository = mockk(relaxed = true)
    mockContext = mockk(relaxed = true)

    mockkStatic("com.google.firebase.FirebaseApp")
    mockkStatic("com.google.firebase.firestore.FirebaseFirestore")
    mockkStatic("com.google.firebase.storage.FirebaseStorage")

    every { com.google.firebase.FirebaseApp.initializeApp(any()) } returns mockk()
    every { com.google.firebase.FirebaseApp.getInstance() } returns mockk()

    every { com.google.firebase.firestore.FirebaseFirestore.getInstance() } returns
        mockk(relaxed = true)
    every { com.google.firebase.storage.FirebaseStorage.getInstance() } returns
        mockk(relaxed = true)

    mockkObject(RepositoryProvider)
    every { RepositoryProvider.sessions } returns mockSessionRepository
    every { RepositoryProvider.images } returns mockImageRepository
    every { RepositoryProvider.discussions } returns mockk(relaxed = true)
    every { RepositoryProvider.geoPins } returns mockk(relaxed = true)

    viewModel =
        SessionOverviewViewModel(mockSessionRepository, mockGameRepository, mockImageRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `loadAllPhotosForAUser loads photos from all past sessions`() =
      runTest(testDispatcher) {
        val userId = "user-123"
        val sessionIds = listOf("session-1", "session-2")
        val photo1 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                "uuid-1", "https://example.com/photo1.webp")
        val photo2 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                "uuid-2", "https://example.com/photo2.webp")
        val session1 =
            Session(
                name = "Game Night 1",
                gameId = "game-1",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 1"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo1))
        val session2 =
            Session(
                name = "Game Night 2",
                gameId = "game-2",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 2"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo2))
        var result: Result<List<com.github.meeplemeet.model.sessions.SessionPhoto>>? = null

        coEvery { mockSessionRepository.getPastSessionIdsForUser(userId) } returns sessionIds
        coEvery { mockSessionRepository.getSession("session-1") } returns session1
        coEvery { mockSessionRepository.getSession("session-2") } returns session2

        viewModel.loadAllPhotosForAUser(userId) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isSuccess)
        val photos = result?.getOrNull()
        assertEquals(2, photos?.size)
        assertEquals("uuid-1", photos?.get(0)?.uuid)
        assertEquals("uuid-2", photos?.get(1)?.uuid)
      }

  @Test
  fun `loadAllPhotosForAUser returns empty list when user has no sessions`() =
      runTest(testDispatcher) {
        val userId = "user-123"
        var result: Result<List<com.github.meeplemeet.model.sessions.SessionPhoto>>? = null

        coEvery { mockSessionRepository.getPastSessionIdsForUser(userId) } returns emptyList()

        viewModel.loadAllPhotosForAUser(userId) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isSuccess)
        assertEquals(emptyList(), result?.getOrNull())
      }

  @Test
  fun `loadAllPhotosForAUser handles sessions with no photos`() =
      runTest(testDispatcher) {
        val userId = "user-123"
        val sessionIds = listOf("session-1", "session-2")
        val session1 =
            Session(
                name = "Game Night 1",
                gameId = "game-1",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 1"),
                participants = listOf(userId),
                sessionPhotos = emptyList())
        val session2 =
            Session(
                name = "Game Night 2",
                gameId = "game-2",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 2"),
                participants = listOf(userId),
                sessionPhotos = emptyList())
        var result: Result<List<com.github.meeplemeet.model.sessions.SessionPhoto>>? = null

        coEvery { mockSessionRepository.getPastSessionIdsForUser(userId) } returns sessionIds
        coEvery { mockSessionRepository.getSession("session-1") } returns session1
        coEvery { mockSessionRepository.getSession("session-2") } returns session2

        viewModel.loadAllPhotosForAUser(userId) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isSuccess)
        assertEquals(emptyList(), result?.getOrNull())
      }

  @Test
  fun `getSessionFromPhoto finds session containing the photo`() =
      runTest(testDispatcher) {
        val userId = "user-123"
        val photoUuid = "photo-uuid-1"
        val sessionIds = listOf("session-1", "session-2")
        val photo1 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                photoUuid, "https://example.com/photo1.webp")
        val photo2 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                "other-uuid", "https://example.com/photo2.webp")
        val session1 =
            Session(
                name = "Game Night 1",
                gameId = "game-1",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 1"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo1))
        val session2 =
            Session(
                name = "Game Night 2",
                gameId = "game-2",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 2"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo2))
        var result: Result<Session?>? = null

        coEvery { mockSessionRepository.getPastSessionIdsForUser(userId) } returns sessionIds
        coEvery { mockSessionRepository.getSession("session-1") } returns session1
        coEvery { mockSessionRepository.getSession("session-2") } returns session2

        viewModel.getSessionFromPhoto(userId, photoUuid) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isSuccess)
        assertEquals(session1, result?.getOrNull())
        assertEquals("Game Night 1", result?.getOrNull()?.name)
      }

  @Test
  fun `getSessionFromPhoto returns null when photo not found`() =
      runTest(testDispatcher) {
        val userId = "user-123"
        val photoUuid = "non-existent-uuid"
        val sessionIds = listOf("session-1", "session-2")
        val photo1 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                "other-uuid-1", "https://example.com/photo1.webp")
        val photo2 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                "other-uuid-2", "https://example.com/photo2.webp")
        val session1 =
            Session(
                name = "Game Night 1",
                gameId = "game-1",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 1"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo1))
        val session2 =
            Session(
                name = "Game Night 2",
                gameId = "game-2",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 2"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo2))
        var result: Result<Session?>? = null

        coEvery { mockSessionRepository.getPastSessionIdsForUser(userId) } returns sessionIds
        coEvery { mockSessionRepository.getSession("session-1") } returns session1
        coEvery { mockSessionRepository.getSession("session-2") } returns session2

        viewModel.getSessionFromPhoto(userId, photoUuid) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isSuccess)
        assertNull(result?.getOrNull())
      }

  @Test
  fun `getSessionFromPhoto returns null when user has no sessions`() =
      runTest(testDispatcher) {
        val userId = "user-123"
        val photoUuid = "photo-uuid"
        var result: Result<Session?>? = null

        coEvery { mockSessionRepository.getPastSessionIdsForUser(userId) } returns emptyList()

        viewModel.getSessionFromPhoto(userId, photoUuid) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isSuccess)
        assertNull(result?.getOrNull())
      }

  @Test
  fun `getSessionFromPhoto searches all sessions in parallel`() =
      runTest(testDispatcher) {
        val userId = "user-123"
        val photoUuid = "photo-uuid"
        val sessionIds = listOf("session-1", "session-2", "session-3")
        val photo1 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                "other-uuid", "https://example.com/photo1.webp")
        val photo2 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                photoUuid, "https://example.com/photo2.webp")
        val photo3 =
            com.github.meeplemeet.model.sessions.SessionPhoto(
                "another-uuid", "https://example.com/photo3.webp")
        val session1 =
            Session(
                name = "Game Night 1",
                gameId = "game-1",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 1"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo1))
        val session2 =
            Session(
                name = "Game Night 2",
                gameId = "game-2",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 2"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo2))
        val session3 =
            Session(
                name = "Game Night 3",
                gameId = "game-3",
                date = Timestamp.now(),
                location = Location(0.0, 0.0, "Location 3"),
                participants = listOf(userId),
                sessionPhotos = listOf(photo3))
        var result: Result<Session?>? = null

        coEvery { mockSessionRepository.getPastSessionIdsForUser(userId) } returns sessionIds
        coEvery { mockSessionRepository.getSession("session-1") } returns session1
        coEvery { mockSessionRepository.getSession("session-2") } returns session2
        coEvery { mockSessionRepository.getSession("session-3") } returns session3

        viewModel.getSessionFromPhoto(userId, photoUuid) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isSuccess)
        assertEquals(session2, result?.getOrNull())
        assertEquals("Game Night 2", result?.getOrNull()?.name)

        // Verify all sessions were checked (parallel search)
        io.mockk.coVerify { mockSessionRepository.getSession("session-1") }
        io.mockk.coVerify { mockSessionRepository.getSession("session-2") }
        io.mockk.coVerify { mockSessionRepository.getSession("session-3") }
      }
}
