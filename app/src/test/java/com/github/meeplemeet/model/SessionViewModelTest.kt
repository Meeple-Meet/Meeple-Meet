package com.github.meeplemeet.model

import android.content.Context
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.sessions.SessionViewModel
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
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
class SessionViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var sessionViewModel: SessionViewModel
  private lateinit var mockSessionRepository: SessionRepository
  private lateinit var mockImageRepository: ImageRepository
  private lateinit var mockContext: Context

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockkObject(RepositoryProvider)
    mockSessionRepository = mockk(relaxed = true)
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

    every { RepositoryProvider.sessions } returns mockSessionRepository
    every { RepositoryProvider.images } returns mockImageRepository
    every { RepositoryProvider.discussions } returns mockk(relaxed = true)
    every { RepositoryProvider.geoPins } returns mockk(relaxed = true)

    sessionViewModel =
        SessionViewModel(
            sessionRepository = mockSessionRepository, imageRepository = mockImageRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `addSessionPhotos successfully adds photo and clears error`() =
      runTest(testDispatcher) {
        val discussionId = "disc-123"
        val photoPath = "/path/to/photo.jpg"
        val expectedPhotos =
            listOf(
                com.github.meeplemeet.model.sessions.SessionPhoto(
                    "uuid-1", "https://example.com/photo.webp"))

        coEvery {
          mockImageRepository.saveSessionPhotos(mockContext, discussionId, photoPath)
        } returns expectedPhotos

        sessionViewModel.addSessionPhotos(mockContext, discussionId, photoPath)
        advanceUntilIdle()

        assertNull(sessionViewModel.errorMessage.value)
        coVerify { mockImageRepository.saveSessionPhotos(mockContext, discussionId, photoPath) }
        coVerify { mockSessionRepository.addSessionPhotos(discussionId, expectedPhotos) }
      }

  @Test
  fun `addSessionPhotos sets error message on failure`() =
      runTest(testDispatcher) {
        val discussionId = "disc-123"
        val photoPath = "/path/to/photo.jpg"
        val errorMessage = "Upload failed"

        coEvery {
          mockImageRepository.saveSessionPhotos(mockContext, discussionId, photoPath)
        } throws RuntimeException(errorMessage)

        sessionViewModel.addSessionPhotos(mockContext, discussionId, photoPath)
        advanceUntilIdle()

        assertEquals("Failed to add photo: $errorMessage", sessionViewModel.errorMessage.value)
      }

  @Test
  fun `removeSessionPhoto successfully removes photo and clears error`() =
      runTest(testDispatcher) {
        val discussionId = "disc-123"
        val photoUuid = "uuid-1"

        coEvery {
          mockImageRepository.deleteSessionPhoto(mockContext, discussionId, photoUuid)
        } just Awaits

        sessionViewModel.removeSessionPhoto(mockContext, discussionId, photoUuid)
        advanceUntilIdle()

        assertNull(sessionViewModel.errorMessage.value)
        coVerify { mockImageRepository.deleteSessionPhoto(mockContext, discussionId, photoUuid) }
      }

  @Test
  fun `removeSessionPhoto sets error message on failure`() =
      runTest(testDispatcher) {
        val discussionId = "disc-123"
        val photoUuid = "uuid-1"
        val errorMessage = "Delete failed"

        coEvery {
          mockImageRepository.deleteSessionPhoto(mockContext, discussionId, photoUuid)
        } throws RuntimeException(errorMessage)

        sessionViewModel.removeSessionPhoto(mockContext, discussionId, photoUuid)
        advanceUntilIdle()

        assertEquals("Failed to delete photo: $errorMessage", sessionViewModel.errorMessage.value)
      }

  @Test
  fun `loadSessionPhotos returns failure result on error`() =
      runTest(testDispatcher) {
        val discussionId = "disc-123"
        val errorMessage = "Load failed"
        var result: Result<List<Pair<String, ByteArray>>>? = null

        coEvery { mockSessionRepository.getSession(discussionId) } throws
            RuntimeException(errorMessage)

        sessionViewModel.loadSessionPhotos(mockContext, discussionId) { result = it }
        advanceUntilIdle()

        assertEquals(true, result?.isFailure)
        assertEquals(errorMessage, result?.exceptionOrNull()?.message)
      }

  @Test
  fun `clearError resets error message to null`() =
      runTest(testDispatcher) {
        val discussionId = "disc-123"
        val photoPath = "/path/to/photo.jpg"

        coEvery {
          mockImageRepository.saveSessionPhotos(mockContext, discussionId, photoPath)
        } throws RuntimeException("Test error")

        sessionViewModel.addSessionPhotos(mockContext, discussionId, photoPath)
        advanceUntilIdle()

        // Verify error is set
        assertEquals("Failed to add photo: Test error", sessionViewModel.errorMessage.value)

        // Clear error
        sessionViewModel.clearError()

        // Verify error is cleared
        assertNull(sessionViewModel.errorMessage.value)
      }
}
