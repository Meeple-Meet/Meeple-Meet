package com.github.meeplemeet.model.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.meeplemeet.FirebaseProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for managing image storage and retrieval. Handles image encoding to WebP format,
 * caching, and Firebase Storage operations.
 */
class ImageRepository {
  private val storage = FirebaseProvider.storage

  private fun accountPath(id: String) = "accounts/$id"

  private fun discussionPath(id: String) = "discussions/$id"

  private fun shopPath(id: String) = "shops/$id"

  private fun spaceRenterPath(id: String) = "space_renters/$id"

  /**
   * Saves an account profile picture to Firebase Storage and local cache.
   *
   * @param accountId The unique identifier for the account
   * @param context Android context for accessing cache directory
   * @param inputPath Absolute path to the source image file
   */
  suspend fun saveAccountProfilePicture(accountId: String, context: Context, inputPath: String) {
    saveImage(context, inputPath, accountPath(accountId))
  }

  /**
   * Loads an account profile picture from cache or Firebase Storage.
   *
   * @param accountId The unique identifier for the account
   * @param context Android context for accessing cache directory
   * @return The image as a byte array
   */
  suspend fun loadAccountProfilePicture(accountId: String, context: Context): ByteArray {
    return loadImage(context, accountPath(accountId))
  }

  /**
   * Saves multiple discussion photos in parallel to Firebase Storage and local cache.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion
   * @param inputPaths Variable number of absolute paths to source image files
   */
  suspend fun saveDiscussionPhotos(
      context: Context,
      discussionId: String,
      vararg inputPaths: String
  ) {
    saveImages(context, discussionPath(discussionId), *inputPaths)
  }

  /**
   * Loads multiple discussion photos in parallel from cache or Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion
   * @param count Number of images to load (loads 0.webp, 1.webp, ..., (count-1).webp)
   * @return List of images as byte arrays in index order
   */
  suspend fun loadDiscussionPhotos(
      context: Context,
      discussionId: String,
      count: Int
  ): List<ByteArray> {
    return loadImages(context, discussionPath(discussionId), count)
  }

  /**
   * Saves multiple shop photos in parallel to Firebase Storage and local cache.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the shop
   * @param inputPaths Variable number of absolute paths to source image files
   */
  suspend fun saveShopPhotos(context: Context, shopId: String, vararg inputPaths: String) {
    saveImages(context, shopPath(shopId), *inputPaths)
  }

  /**
   * Loads multiple shop photos in parallel from cache or Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the shop
   * @param count Number of images to load (loads 0.webp, 1.webp, ..., (count-1).webp)
   * @return List of images as byte arrays in index order
   */
  suspend fun loadShopPhotos(context: Context, shopId: String, count: Int): List<ByteArray> {
    return loadImages(context, shopPath(shopId), count)
  }

  /**
   * Saves multiple space renter photos in parallel to Firebase Storage and local cache.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the space renter
   * @param inputPaths Variable number of absolute paths to source image files
   */
  suspend fun saveSpaceRenterPhotos(context: Context, shopId: String, vararg inputPaths: String) {
    saveImages(context, spaceRenterPath(shopId), *inputPaths)
  }

  /**
   * Loads multiple space renter photos in parallel from cache or Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the space renter
   * @param count Number of images to load (loads 0.webp, 1.webp, ..., (count-1).webp)
   * @return List of images as byte arrays in index order
   */
  suspend fun loadSpaceRenterPhotos(context: Context, shopId: String, count: Int): List<ByteArray> {
    return loadImages(context, spaceRenterPath(shopId), count)
  }

  /**
   * Encodes an image to WebP format and saves it to both local cache and Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param inputPath Absolute path to the source image file
   * @param storagePath Storage path (used for both local cache and Firebase Storage)
   */
  private suspend fun saveImage(context: Context, inputPath: String, storagePath: String) {
    val bytes = encodeWebP(inputPath)

    // Save to disk
    withContext(Dispatchers.IO) {
      val diskPath = "${context.cacheDir}/$storagePath"
      File(diskPath).parentFile?.mkdirs()
      FileOutputStream(diskPath).use { fos -> fos.write(bytes) }
    }

    storage.reference.child(storagePath).putBytes(bytes).await()
  }

  /**
   * Loads an image from local cache if available, otherwise downloads from Firebase Storage.
   * Downloaded images are automatically cached for future use.
   *
   * @param context Android context for accessing cache directory
   * @param path Storage path (used for both local cache and Firebase Storage)
   * @return The image as a byte array
   */
  private suspend fun loadImage(context: Context, path: String): ByteArray {
    val diskPath = "${context.cacheDir}/$path"
    val file = File(diskPath)

    // Check if image exists in cache
    if (file.exists()) {
      return withContext(Dispatchers.IO) { file.readBytes() }
    }

    // Download from Firebase Storage
    val bytes = storage.reference.child(path).getBytes(Long.MAX_VALUE).await()

    // Save to cache for future use
    withContext(Dispatchers.IO) {
      file.parentFile?.mkdirs()
      FileOutputStream(diskPath).use { fos -> fos.write(bytes) }
    }

    return bytes
  }

  /**
   * Saves multiple images in parallel to Firebase Storage and local cache.
   *
   * @param context Android context for accessing cache directory
   * @param parentPath Parent directory path in storage
   * @param inputPaths Variable number of absolute paths to source image files
   */
  private suspend fun saveImages(context: Context, parentPath: String, vararg inputPaths: String) =
      coroutineScope {
        inputPaths
            .mapIndexed { index, inputPath ->
              async {
                val storagePath = "$parentPath/$index.webp"
                saveImage(context, inputPath, storagePath)
              }
            }
            .awaitAll()
      }

  /**
   * Loads multiple images in parallel from cache or Firebase Storage. All images are loaded
   * concurrently for optimal performance.
   *
   * @param context Android context for accessing cache directory
   * @param parentPath Parent directory path in storage
   * @param count Number of images to load (0.webp through (count-1).webp)
   * @return List of images as byte arrays in index order
   */
  private suspend fun loadImages(
      context: Context,
      parentPath: String,
      count: Int
  ): List<ByteArray> = coroutineScope {
    (0 until count)
        .map { index ->
          async {
            val storagePath = "$parentPath/$index.webp"
            loadImage(context, storagePath)
          }
        }
        .awaitAll()
  }

  /**
   * Encodes an image from the file system to WebP format with compression and downscaling.
   *
   * @param inputPath Absolute path to the input image file
   * @param targetMaxPx Maximum dimension (width or height) in pixels. Images larger than this are
   *   downscaled
   * @param quality WebP compression quality (0-100, lower means smaller file size)
   * @return The encoded WebP image as a byte array
   */
  private fun encodeWebP(inputPath: String, targetMaxPx: Int = 800, quality: Int = 40): ByteArray {
    val opts =
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeFile(inputPath, this)

          val maxDim = maxOf(outWidth, outHeight)
          inSampleSize = (maxDim / targetMaxPx).coerceAtLeast(1)
          inJustDecodeBounds = false
        }

    val bmp = BitmapFactory.decodeFile(inputPath, opts)

    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
    bmp.recycle()

    return out.toByteArray()
  }
}
