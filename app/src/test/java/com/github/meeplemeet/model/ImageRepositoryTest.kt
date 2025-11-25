package com.github.meeplemeet.model

import android.content.Context
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.sessions.SessionRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ListResult
import com.google.firebase.storage.StorageReference
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageRepositoryTest {

  private val testDispatcher = StandardTestDispatcher()

  private lateinit var imageRepository: ImageRepository
  private lateinit var sessionRepository: SessionRepository
  private lateinit var mockContext: Context
  private lateinit var mockStorage: FirebaseStorage
  private lateinit var rootReference: StorageReference

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockContext = mockk(relaxed = true)
    mockStorage = mockk(relaxed = true)
    rootReference = mockk(relaxed = true)

    mockkObject(FirebaseProvider)
    every { FirebaseProvider.storage } returns mockStorage
    every { mockStorage.reference } returns rootReference

    mockkObject(RepositoryProvider)
    sessionRepository = mockk(relaxed = true)
    every { RepositoryProvider.sessions } returns sessionRepository

    imageRepository = spyk(ImageRepository(testDispatcher), recordPrivateCalls = true)

    mockkStatic("kotlinx.coroutines.tasks.TasksKt")
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `saveSessionPhotos generates SessionPhoto objects with UUID and URL`() =
      runTest(testDispatcher) {
        val discussionId = "discussion-1"
        val inputs = arrayOf("/tmp/a.jpg", "/tmp/b.jpg")
        val mockUrl = "https://firebase.storage.com/image.webp"

        // Mock saveImage to succeed
        coEvery { 
          imageRepository["saveImage"](mockContext, any<String>(), any<String>()) 
        } returns mockUrl
        
        // Mock Firebase Storage downloadUrl
        val mockStorageRef = mockk<StorageReference>(relaxed = true)
        val mockDownloadUrlTask = mockk<com.google.android.gms.tasks.Task<android.net.Uri>>(relaxed = true)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        
        every { mockStorage.reference } returns rootReference
        every { rootReference.child(any()) } returns mockStorageRef
        every { mockStorageRef.downloadUrl } returns mockDownloadUrlTask
        coEvery { mockDownloadUrlTask.await() } returns mockUri
        every { mockUri.toString() } returns mockUrl

        val result = imageRepository.saveSessionPhotos(mockContext, discussionId, *inputs)

        // Result should be a list of 2 SessionPhoto objects
        assertEquals(2, result.size)
        
        // Verify each SessionPhoto has valid UUID and URL
        result.forEach { photo ->
          // Check UUID format (36 characters with dashes)
          assertEquals(36, photo.uuid.length)
          assert(photo.uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
          
          // Check URL is set
          assertEquals(mockUrl, photo.url)
        }
        
        // Verify saveImage was called twice (once per input)
        coVerify(exactly = 2) { 
          imageRepository["saveImage"](mockContext, any<String>(), any<String>()) 
        }
      }

  @Test
  fun `loadSessionPhotos loads images for given uuids`() =
      runTest(testDispatcher) {
        val discussionId = "discussion-2"
        val photoUuids = listOf("uuid-1", "uuid-2")

        val bytes1 = byteArrayOf(1)
        val bytes2 = byteArrayOf(2)
        coEvery {
          imageRepository["loadImage"](mockContext, "discussions/$discussionId/session/uuid-1.webp")
        } returns bytes1
        coEvery {
          imageRepository["loadImage"](mockContext, "discussions/$discussionId/session/uuid-2.webp")
        } returns bytes2

        val result = imageRepository.loadSessionPhotos(mockContext, discussionId, photoUuids)

        assertEquals(listOf("uuid-1" to bytes1, "uuid-2" to bytes2), result)
      }

  @Test(expected = RuntimeException::class)
  fun `saveSessionPhotos propagates exception when storage fails`() =
      runTest(testDispatcher) {
        val discussionId = "discussion-1"
        val inputs = arrayOf("/tmp/a.jpg")

        coEvery { imageRepository["saveImage"](mockContext, any<String>(), any<String>()) } throws
            RuntimeException("Storage failed")

        imageRepository.saveSessionPhotos(mockContext, discussionId, *inputs)
      }



  @Test(expected = RuntimeException::class)
  fun `deleteSessionPhoto propagates exception when storage fails`() =
      runTest(testDispatcher) {
        val discussionId = "discussion-1"
        val photoUuid = "uuid-1"
        val expectedPath = "discussions/$discussionId/session/$photoUuid.webp"

        coEvery { imageRepository["deleteImages"](mockContext, arrayOf(expectedPath)) } throws
            RuntimeException("Delete failed")

        imageRepository.deleteSessionPhoto(mockContext, discussionId, photoUuid)
      }

  @Test
  fun `deleteSessionPhoto deletes from storage and returns uuid`() =
      runTest(testDispatcher) {
        val discussionId = "discussion-1"
        val photoUuid = "uuid-1"
        val expectedPath = "discussions/$discussionId/session/$photoUuid.webp"

        coEvery { imageRepository["deleteImages"](mockContext, arrayOf(expectedPath)) } returns Unit

        val result = imageRepository.deleteSessionPhoto(mockContext, discussionId, photoUuid)

        assertEquals(photoUuid, result)
        coVerify {
          imageRepository["deleteImages"](mockContext, arrayOf(expectedPath))
        }
      }
}
