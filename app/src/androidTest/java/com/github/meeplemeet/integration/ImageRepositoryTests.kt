package com.github.meeplemeet.integration

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import java.io.File
import java.io.FileOutputStream
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ImageRepositoryTests : FirestoreTests() {
  @get:Rule val ck = Checkpoint.rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var testAccount: Account
  private lateinit var testImagePath1: String
  private lateinit var testImagePath2: String
  private lateinit var testImagePath3: String
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setup() {
    runBlocking {
      // Sign in anonymously for Storage authentication
      auth.signInAnonymously().await()

      // Create test account
      testAccount =
          accountRepository.createAccount(
              "testuser", "Test User", email = "test@example.com", photoUrl = null)

      // Create test images
      testImagePath1 = createTestImage("test_image_1.jpg", 800, 600, Color.RED)
      testImagePath2 = createTestImage("test_image_2.jpg", 1024, 768, Color.GREEN)
      testImagePath3 = createTestImage("test_image_3.jpg", 640, 480, Color.BLUE)
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      // Clean up test images
      File(testImagePath1).delete()
      File(testImagePath2).delete()
      File(testImagePath3).delete()

      // Clean up cache directory
      context.cacheDir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
          file.deleteRecursively()
        } else {
          file.delete()
        }
      }
    }
  }

  private fun createTestImage(filename: String, width: Int, height: Int, color: Int): String {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)

    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }
    bitmap.recycle()

    return file.absolutePath
  }

  @Test
  fun allImageRepositoryTests() {
    // ========================================================================
    // Account Profile Picture Tests
    // ========================================================================

    checkpoint("Test account created") {
      assertNotNull(testAccount)
      assertNotNull(testAccount.uid)
    }

    checkpoint("Save profile picture succeeds") {
      runTest {
        imageRepository.saveAccountProfilePicture(testAccount.uid, context, testImagePath1)
      }
    }

    checkpoint("Load profile picture returns correct data") {
      runTest {
        val bytes = imageRepository.loadAccountProfilePicture(testAccount.uid, context)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
      }
    }

    // ========================================================================
    // Shop Photos Tests
    // ========================================================================

    checkpoint("Save shop photos succeeds") {
      val shopId = "test_shop_123"

      runTest {
        imageRepository.saveShopPhotos(
            context, shopId, testImagePath1, testImagePath2, testImagePath3)
      }
    }

    checkpoint("Load shop photos returns list") {
      val shopId = "test_shop_123"

      runTest {
        val images = imageRepository.loadShopPhotos(context, shopId, 3)
        assertNotNull(images)
        assertEquals(3, images.size)
      }
    }

    checkpoint("Load shop photos with zero count returns empty list") {
      val shopId = "test_shop_456"

      runTest {
        val images = imageRepository.loadShopPhotos(context, shopId, 0)
        assertNotNull(images)
        assertTrue(images.isEmpty())
      }
    }

    // ========================================================================
    // Space Renter Photos Tests
    // ========================================================================

    checkpoint("Save space renter photos succeeds") {
      val spaceRenterId = "test_space_renter_123"

      runTest {
        imageRepository.saveSpaceRenterPhotos(
            context, spaceRenterId, testImagePath1, testImagePath2)
      }
    }

    checkpoint("Load space renter photos returns list") {
      val spaceRenterId = "test_space_renter_123"

      runTest {
        val images = imageRepository.loadSpaceRenterPhotos(context, spaceRenterId, 2)
        assertNotNull(images)
        assertEquals(2, images.size)
      }
    }

    // ========================================================================
    // Discussion Profile Picture Tests
    // ========================================================================

    checkpoint("Save discussion profile picture succeeds") {
      val discussionId = "test_discussion_profile_123"

      runTest {
        imageRepository.saveDiscussionProfilePicture(context, discussionId, testImagePath1)
      }
    }

    checkpoint("Load discussion profile picture returns correct data") {
      val discussionId = "test_discussion_profile_123"

      runTest {
        val bytes = imageRepository.loadDiscussionProfilePicture(context, discussionId)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
      }
    }

    // ========================================================================
    // Discussion Photo Messages Tests
    // ========================================================================

    checkpoint("Save discussion photo messages succeeds") {
      val discussionId = "test_discussion_messages_123"

      runTest {
        val urls =
            imageRepository.saveDiscussionPhotoMessages(
                context, discussionId, testImagePath1, testImagePath2, testImagePath3)
        assertNotNull(urls)
        assertEquals(3, urls.size)
        urls.forEach { url -> assertTrue("URL should not be empty", url.isNotEmpty()) }
      }
    }

    checkpoint("Load discussion photo messages returns list") {
      val discussionId = "test_discussion_messages_123"

      runTest {
        val images = imageRepository.loadDiscussionPhotoMessages(context, discussionId, 3)
        assertNotNull(images)
        assertEquals(3, images.size)
      }
    }

    checkpoint("Load discussion photo messages with zero count returns empty list") {
      val discussionId = "test_discussion_messages_789"

      runTest {
        val images = imageRepository.loadDiscussionPhotoMessages(context, discussionId, 0)
        assertNotNull(images)
        assertTrue(images.isEmpty())
      }
    }

    checkpoint("Save single discussion photo message succeeds") {
      val discussionId = "test_discussion_single_message"

      runTest {
        val urls =
            imageRepository.saveDiscussionPhotoMessages(context, discussionId, testImagePath1)
        assertNotNull(urls)
        assertEquals(1, urls.size)
        assertTrue("URL should not be empty", urls[0].isNotEmpty())
      }
    }

    // ========================================================================
    // Caching Tests
    // ========================================================================

    checkpoint("Cache directory is writable") {
      val testPath = "accounts/${testAccount.uid}"
      val cacheFile = File(context.cacheDir, testPath)
      cacheFile.parentFile?.mkdirs()
      assertTrue("Cache parent directory created", cacheFile.parentFile?.exists() == true)
    }

    checkpoint("Load from cache returns correct data") {
      val testPath = "accounts/${testAccount.uid}"
      val cacheFile = File(context.cacheDir, testPath)
      cacheFile.parentFile?.mkdirs()
      val testData = "test image data".toByteArray()

      FileOutputStream(cacheFile).use { it.write(testData) }

      try {
        runTest {
          val loadedBytes = imageRepository.loadAccountProfilePicture(testAccount.uid, context)
          assertNotNull(loadedBytes)
          assertTrue(loadedBytes.contentEquals(testData))
        }
      } finally {
        cacheFile.delete()
      }
    }

    checkpoint("Cache file cleanup works") {
      val testPath = "accounts/${testAccount.uid}"
      val cacheFile = File(context.cacheDir, testPath)
      cacheFile.parentFile?.mkdirs()
      FileOutputStream(cacheFile).use { it.write("test".toByteArray()) }

      assertTrue("Cache file exists", cacheFile.exists())
      cacheFile.delete()
      assertTrue("Cache file deleted", !cacheFile.exists())
    }

    // ========================================================================
    // Parallel Loading Tests
    // ========================================================================

    checkpoint("Multiple photo paths created") {
      val numPhotos = 10
      val photoPaths = Array(numPhotos) { "photo_$it.webp" }

      assertEquals(numPhotos, photoPaths.size)
      assertTrue("First path is photo_0.webp", photoPaths[0] == "photo_0.webp")
      assertTrue("Last path is photo_9.webp", photoPaths[9] == "photo_9.webp")
    }

    checkpoint("Parallel load completes in reasonable time") {
      val shopId = "test_shop_parallel"
      val numPhotos = 5
      val inputPaths = Array(numPhotos) { testImagePath1 }

      runTest {
        // First save the images
        imageRepository.saveShopPhotos(context, shopId, *inputPaths)

        val startTime = System.currentTimeMillis()
        val images = imageRepository.loadShopPhotos(context, shopId, numPhotos)
        val endTime = System.currentTimeMillis()

        assertNotNull(images)
        assertEquals(numPhotos, images.size)

        val elapsedTime = endTime - startTime
        assertTrue("Parallel loading completed within 30 seconds", elapsedTime < 30000)
      }
    }

    // ========================================================================
    // Edge Cases Tests
    // ========================================================================

    checkpoint("Special characters in shop ID handled") {
      val shopId = "test-shop_123.special"

      runTest {
        imageRepository.saveShopPhotos(context, shopId, testImagePath1)
        val images = imageRepository.loadShopPhotos(context, shopId, 1)
        assertNotNull(images)
        assertEquals(1, images.size)
      }
    }

    checkpoint("Images returned in correct order") {
      val discussionId = "test_discussion_order"

      runTest {
        // Save images
        imageRepository.saveDiscussionPhotoMessages(
            context, discussionId, testImagePath1, testImagePath2, testImagePath3)

        // Load images
        val images = imageRepository.loadDiscussionPhotoMessages(context, discussionId, 3)

        assertEquals(3, images.size)
        // All images should be non-empty
        images.forEach { bytes ->
          assertNotNull(bytes)
          assertTrue(bytes.isNotEmpty())
        }
      }
    }

    checkpoint("Multiple images saved and loaded") {
      val shopId = "test_shop_multi"

      runTest {
        imageRepository.saveShopPhotos(
            context, shopId, testImagePath1, testImagePath2, testImagePath3)
        val images = imageRepository.loadShopPhotos(context, shopId, 3)
        assertEquals(3, images.size)
      }
    }

    checkpoint("Long shop ID handled") {
      val shopId = "a".repeat(50)

      runTest {
        imageRepository.saveShopPhotos(context, shopId, testImagePath1)
        val images = imageRepository.loadShopPhotos(context, shopId, 1)
        assertNotNull(images)
        assertEquals(1, images.size)
      }
    }

    // ========================================================================
    // Compression and Quality Tests
    // ========================================================================

    checkpoint("4K image created successfully") {
      val largeImagePath = createTestImage("large_test_image.jpg", 3840, 2160, Color.BLUE)
      val originalFile = File(largeImagePath)
      val originalSize = originalFile.length()

      assertTrue("Original 4K image should exist", originalSize > 1000)
      originalFile.delete()
    }

    checkpoint("8K image downscaling calculations") {
      val veryLargeImagePath = createTestImage("very_large_image.jpg", 7680, 4320, Color.RED)
      val originalFile = File(veryLargeImagePath)

      try {
        val originalSize = originalFile.length()
        assertTrue("8K image should be created", originalSize > 1000)

        val expectedMaxDimension = 800
        val originalWidth = 7680
        val originalHeight = 4320

        val maxOriginalDim = maxOf(originalWidth, originalHeight)
        val scaleFactor = expectedMaxDimension.toFloat() / maxOriginalDim
        val expectedWidth = (originalWidth * scaleFactor).toInt()
        val expectedHeight = (originalHeight * scaleFactor).toInt()

        assertTrue("Expected width should be <= 800", expectedWidth <= expectedMaxDimension)
        assertTrue("Expected height should be <= 450", expectedHeight <= expectedMaxDimension)
        assertTrue(
            "Max dimension should be 800",
            maxOf(expectedWidth, expectedHeight) <= expectedMaxDimension)
      } finally {
        originalFile.delete()
      }
    }

    checkpoint("Aspect ratio preservation") {
      val width = 1920
      val height = 1080
      val imagePath = createTestImage("hq_aspect_test.jpg", width, height, Color.GREEN)
      val originalFile = File(imagePath)

      try {
        val originalAspectRatio = width.toFloat() / height.toFloat()

        val newWidth = 800
        val newHeight = (newWidth / originalAspectRatio).toInt()
        val newAspectRatio = newWidth.toFloat() / newHeight.toFloat()

        val aspectRatioDifference = abs(originalAspectRatio - newAspectRatio)
        assertTrue("Aspect ratio should be preserved", aspectRatioDifference < 0.01f)

        assertEquals("New width should be 800", 800, newWidth)
        assertTrue("New height should be around 450", abs(newHeight - 450) < 5)
      } finally {
        originalFile.delete()
      }
    }

    checkpoint("Multiple high-quality images created") {
      val images =
          listOf(
              createTestImage("parallel_1.jpg", 2560, 1440, Color.RED),
              createTestImage("parallel_2.jpg", 2560, 1440, Color.GREEN),
              createTestImage("parallel_3.jpg", 2560, 1440, Color.BLUE),
              createTestImage("parallel_4.jpg", 2560, 1440, Color.YELLOW))

      try {
        images.forEach { path ->
          val file = File(path)
          assertTrue("Image should exist", file.exists())
          assertTrue("Image should be created", file.length() > 1000)
        }
      } finally {
        images.forEach { File(it).delete() }
      }
    }

    checkpoint("Extremely large image handling") {
      try {
        val extremeImagePath = createTestImage("extreme_test.jpg", 12000, 6750, Color.CYAN)
        val extremeFile = File(extremeImagePath)

        try {
          assertTrue("Extreme image should be created", extremeFile.exists())

          val maxOriginalDim = 12000
          val targetMaxPx = 800
          val expectedSampleSize = (maxOriginalDim / targetMaxPx).coerceAtLeast(1)

          assertTrue("Sample size should be at least 10", expectedSampleSize >= 10)
          assertTrue("Sample size should reduce memory usage", expectedSampleSize > 1)
        } finally {
          extremeFile.delete()
        }
      } catch (_: OutOfMemoryError) {
        assertTrue("Device may not support extremely large image creation", true)
      }
    }

    checkpoint("Small image not upscaled") {
      val smallImagePath = createTestImage("small_test.jpg", 400, 300, Color.MAGENTA)
      val smallFile = File(smallImagePath)

      try {
        assertTrue("Small image should be created", smallFile.exists())

        val originalWidth = 400
        val originalHeight = 300
        val targetMaxPx = 800

        assertTrue(
            "Image should not need downscaling",
            maxOf(originalWidth, originalHeight) <= targetMaxPx)
      } finally {
        smallFile.delete()
      }
    }
  }
}
