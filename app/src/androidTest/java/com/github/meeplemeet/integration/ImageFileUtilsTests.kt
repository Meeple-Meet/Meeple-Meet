package com.github.meeplemeet.integration

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.images.ImageFileUtils
import java.io.File
import java.io.FileOutputStream
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Integration tests for ImageFileUtils methods. */
class ImageFileUtilsTests {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var testUri: Uri
  private var testUriFile: File? = null

  @Before
  fun setup() {
    // Clean up cache directory before each test
    context.cacheDir.listFiles()?.forEach { file ->
      if (file.isDirectory) {
        file.deleteRecursively()
      } else {
        file.delete()
      }
    }
  }

  @After
  fun tearDown() {
    // Clean up test URI file if it exists
    testUriFile?.let { file ->
      if (file.exists()) {
        file.delete()
      }
    }

    // Clean up cache directory
    context.cacheDir.listFiles()?.forEach { file ->
      if (file.isDirectory) {
        file.deleteRecursively()
      } else {
        file.delete()
      }
    }
  }

  private fun createTestBitmap(width: Int, height: Int, color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)
    return bitmap
  }

  private fun createTestImageUri(filename: String, width: Int, height: Int, color: Int): Uri {
    val bitmap = createTestBitmap(width, height, color)

    // Create a file in the test directory
    val file = File(context.filesDir, filename)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }
    bitmap.recycle()

    testUriFile = file
    return Uri.fromFile(file)
  }

  @Test
  fun cacheUriToFileCreatesFileInCache() = runBlocking {
    val uri = createTestImageUri("test_image.jpg", 800, 600, Color.RED)

    val cachedPath = ImageFileUtils.cacheUriToFile(context, uri)

    val cachedFile = File(cachedPath)
    assertTrue(cachedFile.exists())
    assertTrue(cachedFile.absolutePath.startsWith(context.cacheDir.absolutePath))
    assertTrue(cachedFile.name.startsWith("gallery_"))
    assertTrue(cachedFile.name.endsWith(".jpg"))
  }

  @Test
  fun cacheUriToFilePreservesImageContent() = runBlocking {
    val uri = createTestImageUri("test_image.jpg", 100, 100, Color.BLUE)

    val cachedPath = ImageFileUtils.cacheUriToFile(context, uri)

    // Verify the cached file contains valid image data
    val cachedBitmap = BitmapFactory.decodeFile(cachedPath)
    assertNotNull(cachedBitmap)
    assertEquals(100, cachedBitmap.width)
    assertEquals(100, cachedBitmap.height)
    cachedBitmap.recycle()
  }

  @Test
  fun saveBitmapToCacheCreatesFileInCache() = runBlocking {
    val bitmap = createTestBitmap(640, 480, Color.GREEN)

    val cachedPath = ImageFileUtils.saveBitmapToCache(context, bitmap)

    val cachedFile = File(cachedPath)
    assertTrue(cachedFile.exists())
    assertTrue(cachedFile.absolutePath.startsWith(context.cacheDir.absolutePath))
    assertTrue(cachedFile.name.startsWith("camera_"))
    assertTrue(cachedFile.name.endsWith(".jpg"))

    bitmap.recycle()
  }

  @Test
  fun saveBitmapToCachePreservesDimensions() = runBlocking {
    val originalBitmap = createTestBitmap(1024, 768, Color.YELLOW)

    val cachedPath = ImageFileUtils.saveBitmapToCache(context, originalBitmap)

    // Verify the cached file contains image with correct dimensions
    val cachedBitmap = BitmapFactory.decodeFile(cachedPath)
    assertNotNull(cachedBitmap)
    assertEquals(1024, cachedBitmap.width)
    assertEquals(768, cachedBitmap.height)

    originalBitmap.recycle()
    cachedBitmap.recycle()
  }

  @Test
  fun saveBitmapToCacheCompressesToJPEG() = runBlocking {
    val bitmap = createTestBitmap(800, 600, Color.MAGENTA)

    val cachedPath = ImageFileUtils.saveBitmapToCache(context, bitmap)

    val cachedFile = File(cachedPath)
    assertTrue(cachedFile.exists())

    // Verify file can be decoded as JPEG
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeFile(cachedPath, options)

    assertNotNull(options.outMimeType)
    assertTrue(options.outMimeType!!.contains("jpeg") || options.outMimeType!!.contains("jpg"))

    bitmap.recycle()
  }

  @Test
  fun multipleCacheUriToFileCallsCreateUniqueFiles() = runBlocking {
    val uri1 = createTestImageUri("test1.jpg", 100, 100, Color.RED)

    val path1 = ImageFileUtils.cacheUriToFile(context, uri1)

    // Clean up the first test file
    testUriFile?.delete()

    val uri2 = createTestImageUri("test2.jpg", 100, 100, Color.BLUE)
    val path2 = ImageFileUtils.cacheUriToFile(context, uri2)

    // Verify both files exist and have different paths
    assertTrue(File(path1).exists())
    assertTrue(File(path2).exists())
    assertTrue(path1 != path2)
  }

  @Test
  fun multipleSaveBitmapToCacheCallsCreateUniqueFiles() = runBlocking {
    val bitmap1 = createTestBitmap(100, 100, Color.RED)
    val bitmap2 = createTestBitmap(100, 100, Color.BLUE)

    val path1 = ImageFileUtils.saveBitmapToCache(context, bitmap1)
    val path2 = ImageFileUtils.saveBitmapToCache(context, bitmap2)

    // Verify both files exist and have different paths
    assertTrue(File(path1).exists())
    assertTrue(File(path2).exists())
    assertTrue(path1 != path2)

    bitmap1.recycle()
    bitmap2.recycle()
  }
}
