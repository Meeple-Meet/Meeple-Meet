package com.github.meeplemeet.model.images

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val IMAGE_EXTENSION = ".jpg"
/**
 * Utility functions for caching user-selected images to local storage.
 *
 * This object provides helpers to save images from various sources (gallery URIs, camera bitmaps)
 * into the app's cache directory as JPEG files. These cached files can then be uploaded to Firebase
 * Storage.
 *
 * ## Use Cases
 * - **Gallery images**: User selects photo from gallery, returns content:// URI
 * - **Camera images**: User takes photo with camera, returns Bitmap
 *
 * ## Cache Management
 * - Files are stored in [Context.getCacheDir] which can be cleared by the system when storage is
 *   low
 * - Files use temporary naming with prefixes ("gallery_" or "camera_")
 * - Callers should clean up cached files after successful upload
 *
 * ## Thread Safety
 * All functions use `withContext(Dispatchers.IO)` for safe I/O operations off the main thread.
 */
object ImageFileUtils {
  /**
   * Copies a content URI to a temporary JPEG file in the app's cache directory.
   *
   * This function is typically used when the user selects an image from the gallery. The content
   * URI (e.g., `content://media/external/images/media/123`) is copied to a local file that can be
   * uploaded to Firebase Storage.
   *
   * ## Example Usage
   *
   * ```kotlin
   * val uri = ... // From photo picker
   * val cachedPath = ImageFileUtils.cacheUriToFile(context, uri)
   * // Upload cachedPath to Firebase Storage
   * File(cachedPath).delete() // Clean up after upload
   * ```
   *
   * @param context Application context for accessing content resolver and cache directory.
   * @param uri Content URI of the image to cache (typically from MediaStore or photo picker).
   * @return Absolute file path to the cached JPEG file in cache directory.
   * @throws IOException if reading from URI or writing to cache fails.
   */
  suspend fun cacheUriToFile(context: Context, uri: Uri): String =
      withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val file = File.createTempFile("gallery_", IMAGE_EXTENSION, context.cacheDir)
        resolver.openInputStream(uri)?.use { input ->
          FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.absolutePath
      }

  /**
   * Saves an in-memory bitmap to a temporary JPEG file in the app's cache directory.
   *
   * This function is typically used when the user captures a photo with the camera. The Bitmap is
   * compressed to JPEG format (95% quality) and saved to a local file for upload.
   *
   * ## Compression Settings
   * - Format: JPEG
   * - Quality: 95 (high quality, balances size and fidelity)
   *
   * ## Example Usage
   *
   * ```kotlin
   * val bitmap = ... // From camera capture
   * val cachedPath = ImageFileUtils.saveBitmapToCache(context, bitmap)
   * bitmap.recycle() // Free bitmap memory
   * // Upload cachedPath to Firebase Storage
   * File(cachedPath).delete() // Clean up after upload
   * ```
   *
   * @param context Application context for accessing cache directory.
   * @param bitmap In-memory bitmap to save (typically from camera or image processing).
   * @return Absolute file path to the cached JPEG file in cache directory.
   * @throws IOException if writing to cache fails.
   */
  suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap): String =
      withContext(Dispatchers.IO) {
        val file = File.createTempFile("camera_", IMAGE_EXTENSION, context.cacheDir)
        FileOutputStream(file).use { output ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        file.absolutePath
      }
  /**
   * Saves a byte array to a temporary JPEG file in the app's cache directory.
   *
   * This function is useful when you have raw image bytes (e.g., received from network or
   * processing) and want to cache them as a JPEG file.
   *
   * @param context Application context for accessing cache directory.
   * @param bytes Byte array to save.
   * @return Absolute file path to the cached JPEG file in cache directory.
   * @throws IOException if writing to cache fails.
   */
  suspend fun saveByteArrayToCache(context: Context, bytes: ByteArray): String =
      withContext(Dispatchers.IO) {
        val file = File.createTempFile("bytes_", IMAGE_EXTENSION, context.cacheDir)
        FileOutputStream(file).use { output -> output.write(bytes) }
        file.absolutePath
      }
}
