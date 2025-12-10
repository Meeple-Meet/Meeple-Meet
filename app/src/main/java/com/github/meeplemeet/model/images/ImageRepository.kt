package com.github.meeplemeet.model.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.storage.StorageManager
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.DiskStorageException
import com.github.meeplemeet.model.ImageProcessingException
import com.github.meeplemeet.model.RemoteStorageException
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for managing image storage and retrieval across the application.
 *
 * Handles all image operations including encoding to WebP format, local caching, and Firebase
 * Storage uploads/downloads. Supports images for:
 * - Account profile pictures
 * - Discussion profile pictures (NEW)
 * - Discussion message photo attachments (NEW)
 * - Shop photos
 * - Space renter photos
 *
 * ## Image Processing
 * - All images are automatically converted to WebP format for optimal compression
 * - Default settings: 800px max dimension, 40% quality (configurable per-image)
 * - Images are downscaled if they exceed the maximum dimension
 *
 * ## Caching Strategy
 * - Two-tier caching: local disk cache (app cache dir) + Firebase Storage
 * - Local cache is checked first; on miss, downloads from Firebase Storage
 * - Downloaded images are automatically cached locally for future use
 * - StorageManager API used on Android O+ to intelligently manage cache space
 *
 * ## Storage Paths
 * - Account profiles: `accounts/{accountId}/profile.webp`
 * - Discussion profiles: `discussions/{discussionId}/profile.webp`
 * - Discussion messages: `discussions/{discussionId}/messages/{UUID}.webp`
 * - Shop photos: `shops/{shopId}/{UUID}.webp`
 * - Space renter photos: `spaceRenters/{spaceRenterId}/{UUID}.webp`
 *
 * ## Thread Safety
 * All operations use coroutines with configurable dispatcher (defaults to Dispatchers.IO). Multiple
 * image operations (save/load/delete) are performed in parallel when possible.
 *
 * @property dispatcher Coroutine dispatcher for I/O operations (defaults to Dispatchers.IO)
 */
class ImageRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
  private val storage = FirebaseProvider.storage

  /**
   * Modern storage allocation using StorageManager (Android O+). Considers clearable cached data
   * and can trigger automatic cleanup.
   *
   * @param context Android context
   * @param bytesNeeded Number of bytes required
   * @throws DiskStorageException if insufficient space cannot be allocated
   */
  private fun ensureStorageSpace(context: Context, bytesNeeded: Long) {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val appSpecificDir = context.cacheDir
    val uuid = storageManager.getUuidForPath(appSpecificDir)

    try {
      val allocatableBytes = storageManager.getAllocatableBytes(uuid)

      if (allocatableBytes < bytesNeeded) {
        throw DiskStorageException(
            "Insufficient storage: need ${bytesNeeded / 1024}KB, " +
                "allocatable ${allocatableBytes / 1024}KB")
      }

      // Attempt to allocate the required space (may clear cached data automatically)
      storageManager.allocateBytes(uuid, bytesNeeded)
    } catch (e: IOException) {
      throw DiskStorageException("Failed to allocate storage space: ${e.message}", e)
    }
  }

  private fun accountPath(id: String) = "${RepositoryProvider.accounts.collectionName}/$id"

  private fun discussionBasePath(id: String) =
      "${RepositoryProvider.discussions.collectionName}/$id"

  private fun discussionProfilePath(id: String) = "${discussionBasePath(id)}/profile.webp"

  private fun sessionPhotoPath(id: String) = "${discussionBasePath(id)}/session.webp"

  private fun discussionMessagesDir(id: String) = "${discussionBasePath(id)}/messages"

  private fun discussionMessagePath(id: String) =
      "${discussionMessagesDir(id)}/${UUID.randomUUID()}.webp"

  private fun shopPath(id: String) = "shops/$id"

  private fun spaceRenterPath(id: String) = "space_renters/$id"

  private fun cachePath(context: Context, storagePath: String) = "${context.cacheDir}/$storagePath"

  private fun normalizePath(candidate: String, expectedPrefix: String): String {
    val path =
        if (candidate.contains("/o/")) {
          val encoded = candidate.substringAfter("/o/").substringBefore('?')
          URLDecoder.decode(encoded, "UTF-8")
        } else {
          candidate
        }

    return if (path.startsWith(expectedPrefix)) path else "$expectedPrefix/${File(path).name}"
  }

  private fun toHttps(url: String): String =
      if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url

  /**
   * Saves an account profile picture to Firebase Storage and local cache.
   *
   * @param accountId The unique identifier for the account
   * @param context Android context for accessing cache directory
   * @param inputPath Absolute path to the source image file
   * @return The public download URL of the saved picture
   * @throws ImageProcessingException if image encoding fails
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   */
  suspend fun saveAccountProfilePicture(
      accountId: String,
      context: Context,
      inputPath: String
  ): String = saveImage(context, inputPath, accountPath(accountId))

  /**
   * Deletes the account profile picture from both cache and Firebase Storage.
   *
   * @param accountId The unique identifier for the account
   * @param context Android context for accessing cache directory
   * @throws DiskStorageException if disk delete fails
   * @throws RemoteStorageException if Firebase Storage delete fails
   */
  suspend fun deleteAccountProfilePicture(accountId: String, context: Context) =
      deleteImages(context, accountPath(accountId))

  /**
   * Deletes the account profile picture from local cache only (does not delete from Firebase
   * Storage).
   *
   * This method is useful for cache eviction scenarios where the remote image should be preserved
   * but local storage needs to be freed. Used by [OfflineModeManager] when evicting old cached
   * accounts.
   *
   * @param accountId The unique identifier for the account
   * @param context Android context for accessing cache directory
   * @throws DiskStorageException if disk delete fails
   * @see deleteAccountProfilePicture to delete from both cache and Firebase Storage
   */
  suspend fun deleteLocalAccountProfilePicture(accountId: String, context: Context) =
      deleteLocalImages(context, accountPath(accountId))

  /**
   * Loads an account profile picture from cache or Firebase Storage.
   *
   * @param accountId The unique identifier for the account
   * @param context Android context for accessing cache directory
   * @return The image as a byte array
   * @throws DiskStorageException if disk read fails
   * @throws RemoteStorageException if Firebase Storage download fails
   */
  suspend fun loadAccountProfilePicture(accountId: String, context: Context): ByteArray {
    return loadImage(context, accountPath(accountId))
  }

  /**
   * Saves a discussion profile picture to Firebase Storage and local cache.
   *
   * Discussion profile pictures are stored at `discussions/{discussionId}/profile.webp` in Firebase
   * Storage. This operation requires admin privileges in the discussion (enforced by caller).
   *
   * ## Usage
   * Typically called by [DiscussionDetailsViewModel.setDiscussionProfilePicture], which handles
   * permission checks and updates the discussion document.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion
   * @param inputPath Absolute path to the source image file (from gallery or camera)
   * @return The public HTTPS download URL of the saved profile picture
   * @throws ImageProcessingException if image encoding fails
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   * @see DiscussionDetailsViewModel.setDiscussionProfilePicture for high-level API
   * @see loadDiscussionProfilePicture to retrieve the profile picture
   */
  suspend fun saveDiscussionProfilePicture(
      context: Context,
      discussionId: String,
      inputPath: String
  ): String = saveImage(context, inputPath, discussionProfilePath(discussionId))

  /**
   * Loads the discussion profile picture from cache or Firebase Storage.
   *
   * Checks local cache first; on cache miss, downloads from Firebase Storage and caches locally.
   * The image is returned as a byte array in WebP format.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion
   * @return The image as a byte array in WebP format
   * @throws DiskStorageException if disk read fails
   * @throws RemoteStorageException if Firebase Storage download fails or profile picture doesn't
   *   exist
   * @see saveDiscussionProfilePicture to upload a profile picture
   */
  suspend fun loadDiscussionProfilePicture(context: Context, discussionId: String): ByteArray =
      loadImage(context, discussionProfilePath(discussionId))

  suspend fun deleteLocalDiscussionProfilePicture(context: Context, discussionId: String) =
      deleteLocalImages(context, discussionProfilePath(discussionId))

  /**
   * Saves a session photo to Firebase Storage and local cache.
   *
   * Session photos are stored at a fixed path `discussions/{discussionId}/session/photo.webp`. This
   * operation requires admin privileges in the discussion (enforced by caller).
   *
   * ## Usage
   * Typically called by [SessionViewModel.saveSessionPhoto], which handles permission checks and
   * updates the session document with the returned photo URL.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion containing the session
   * @param inputPath Absolute path to the source image file (from gallery or camera)
   * @return The public HTTPS download URL of the saved session photo
   * @throws ImageProcessingException if image encoding fails
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   * @see SessionViewModel.saveSessionPhoto for high-level API
   * @see loadSessionPhoto to retrieve the session photo
   * @see deleteSessionPhoto to delete the session photo
   */
  suspend fun saveSessionPhoto(context: Context, discussionId: String, inputPath: String): String =
      saveImage(context, inputPath, sessionPhotoPath(discussionId))

  /**
   * Deletes the session photo from both cache and Firebase Storage.
   *
   * Removes the session photo stored at `discussions/{discussionId}/session/photo.webp`. This
   * operation requires admin privileges in the discussion (enforced by caller).
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion containing the session
   * @throws DiskStorageException if disk delete fails
   * @throws RemoteStorageException if Firebase Storage delete fails
   * @see SessionViewModel.deleteSessionPhoto for high-level API
   * @see saveSessionPhoto to upload a session photo
   */
  suspend fun deleteSessionPhoto(context: Context, discussionId: String) =
      deleteImages(context, sessionPhotoPath(discussionId))

  /**
   * Loads the session photo from cache or Firebase Storage.
   *
   * Checks local cache first; on cache miss, downloads from Firebase Storage and caches locally.
   * The image is returned as a byte array in WebP format.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion containing the session
   * @return The image as a byte array in WebP format
   * @throws DiskStorageException if disk read fails
   * @throws RemoteStorageException if Firebase Storage download fails or session photo doesn't
   *   exist
   * @see SessionViewModel.loadSessionPhoto for high-level API
   * @see saveSessionPhoto to upload a session photo
   */
  suspend fun loadSessionPhoto(context: Context, discussionId: String): ByteArray =
      loadImage(context, sessionPhotoPath(discussionId))

  /**
   * Moves a session photo to the archived sessions storage location.
   *
   * This operation:
   * 1. Downloads the current session photo
   * 2. Uploads it to the new location: `archived_sessions/{newUuid}.webp`
   * 3. Deletes the original photo
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The ID of the discussion containing the session
   * @param newUuid The new UUID for the archived session
   * @return The download URL of the moved photo
   */
  suspend fun moveSessionPhoto(context: Context, discussionId: String, newUuid: String): String {
    val oldPath = sessionPhotoPath(discussionId)
    val newPath = "archived_sessions/${newUuid}.webp"

    // 1. Download the current photo
    val bytes = loadImage(context, oldPath)

    // 2. Upload to new location (and cache locally)
    val newUrl = saveImage(context, bytes, newPath)

    // 3. Delete the old photo
    deleteSessionPhoto(context, discussionId)

    return newUrl
  }

  /**
   * Helper to save raw image bytes directly to both local cache and Firebase Storage.
   *
   * This is a lower-level variant of [saveImage] that accepts pre-processed bytes instead of a file
   * path. Used internally by [moveSessionPhoto] when relocating already-processed images.
   *
   * ## Storage Process
   * 1. Creates parent directory if needed
   * 2. Checks available storage space using StorageManager
   * 3. Writes bytes to local cache
   * 4. Uploads to Firebase Storage
   * 5. Returns HTTPS download URL (upgraded from HTTP if needed)
   *
   * ## Error Handling
   * - Throws [DiskStorageException] for file system errors
   * - Throws [RemoteStorageException] for Firebase Storage errors
   * - Both local and remote saves must succeed
   *
   * @param context Android context for accessing cache directory
   * @param bytes The image data as a byte array (already in WebP format)
   * @param storagePath Storage path (used for both local cache and Firebase Storage)
   * @return The public HTTPS download URL of the saved image
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   */
  private suspend fun saveImage(context: Context, bytes: ByteArray, storagePath: String): String {
    // Save to disk
    try {
      withContext(dispatcher) {
        val diskPath = cachePath(context, storagePath)
        val parentDir = File(diskPath).parentFile

        // Check if parent directory can be created
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
          throw DiskStorageException("Failed to create directory: ${parentDir.absolutePath}")
        }

        // Ensure sufficient storage space
        ensureStorageSpace(context, bytes.size.toLong())

        FileOutputStream(diskPath).use { fos -> fos.write(bytes) }
      }
    } catch (e: DiskStorageException) {
      throw e
    } catch (e: IOException) {
      throw DiskStorageException("Failed to write image to disk at $storagePath", e)
    } catch (e: SecurityException) {
      throw DiskStorageException("Permission denied writing to $storagePath", e)
    }

    // Upload to Firebase Storage
    try {
      val ref = storage.reference.child(storagePath)
      ref.putBytes(bytes).await()
      return toHttps(ref.downloadUrl.await().toString())
    } catch (e: StorageException) {
      throw RemoteStorageException(
          "Firebase Storage upload failed for $storagePath: ${e.errorCode}", e)
    } catch (e: Exception) {
      throw RemoteStorageException("Failed to upload image to Firebase Storage at $storagePath", e)
    }
  }

  /**
   * Saves discussion message photos to Firebase Storage under unique names and returns their URLs.
   *
   * Each photo is uploaded to `discussions/{discussionId}/messages/{UUID}.webp` with a randomly
   * generated UUID to prevent collisions. Multiple photos are processed in parallel for optimal
   * performance.
   *
   * ## Usage Flow
   * 1. User selects photo from gallery or captures with camera
   * 2. [ImageFileUtils] caches the image to app cache directory
   * 3. This method uploads the cached file to Firebase Storage
   * 4. Returns download URLs to be stored in Message.photoUrl field
   * 5. [DiscussionRepository.sendPhotoMessageToDiscussion] creates the message with photoUrl
   *
   * ## Storage Organization
   * Photos are organized under the discussion's messages directory, separate from the discussion
   * profile picture. Each photo gets a unique filename to support multiple message attachments.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion
   * @param inputPaths Variable number of absolute paths to source image files (from cache)
   * @return List of public HTTPS download URLs in the same order as inputPaths
   * @throws ImageProcessingException if image encoding fails for any photo
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   * @see DiscussionViewModel.sendMessageWithPhoto for high-level API
   * @see deleteDiscussionPhotoMessages to delete message photos
   * @see ImageFileUtils.cacheUriToFile for caching gallery selections
   */
  suspend fun saveDiscussionPhotoMessages(
      context: Context,
      discussionId: String,
      vararg inputPaths: String
  ): List<String> = saveImages(context, { discussionMessagePath(discussionId) }, *inputPaths)

  /**
   * Deletes discussion message photos from cache and Firebase Storage.
   *
   * Can delete specific photos by providing storage paths, or delete all message photos for a
   * discussion if no paths are specified. Paths can be either storage paths or full download URLs
   * (automatically normalized).
   *
   * ## Deletion Modes
   * - **Specific photos**: Pass one or more storagePaths to delete individual message photos
   * - **All photos**: Pass no storagePaths (empty vararg) to delete entire messages directory
   *
   * ## Path Normalization
   * Accepts both formats:
   * - Storage path: `discussions/{discussionId}/messages/{UUID}.webp`
   * - Download URL:
   *   `https://firebasestorage.googleapis.com/.../o/discussions%2F{id}%2Fmessages%2F{UUID}.webp?...`
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion
   * @param storagePaths Variable number of storage paths or download URLs to delete. If empty,
   *   deletes all message photos for the discussion.
   * @throws DiskStorageException if disk delete fails
   * @throws RemoteStorageException if Firebase Storage delete fails
   * @see saveDiscussionPhotoMessages to upload message photos
   */
  suspend fun deleteDiscussionPhotoMessages(
      context: Context,
      discussionId: String,
      vararg storagePaths: String
  ) {
    val base = discussionMessagesDir(discussionId)
    if (storagePaths.isEmpty()) {
      deleteDirectory(context, base)
    } else {
      val normalized = storagePaths.map { normalizePath(it, base) }.toTypedArray()
      deleteImages(context, *normalized)
    }
  }

  /**
   * Loads multiple discussion message photos in parallel from cache or Firebase Storage.
   *
   * Efficiently loads multiple photos concurrently. Checks local cache first for each photo; on
   * cache misses, downloads from Firebase Storage and caches locally. Photos are returned in sorted
   * order by filename.
   *
   * ## Note on Naming
   * This method assumes numbered filenames (0.webp, 1.webp, etc.), but discussion message photos
   * use UUID-based naming. This method may not work as expected for discussion message photos in
   * production. Consider loading photos by URL from Message.photoUrl field instead.
   *
   * @param context Android context for accessing cache directory
   * @param discussionId The unique identifier for the discussion
   * @param count Number of images to load from the messages directory
   * @return List of images as byte arrays in filename sort order (0.webp, 1.webp, ...)
   * @throws DiskStorageException if disk read fails
   * @throws RemoteStorageException if Firebase Storage operations fail
   * @see saveDiscussionPhotoMessages which uses UUID-based naming
   */
  suspend fun loadDiscussionPhotoMessages(
      context: Context,
      discussionId: String,
      count: Int
  ): List<ByteArray> {
    return loadImages(context, discussionMessagesDir(discussionId), count)
  }

  /**
   * Saves multiple shop photos in parallel to Firebase Storage and local cache.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the shop
   * @param inputPaths Variable number of absolute paths to source image files
   * @return List of public download URLs in the same order as inputPaths
   * @throws ImageProcessingException if image encoding fails
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   */
  suspend fun saveShopPhotos(
      context: Context,
      shopId: String,
      vararg inputPaths: String
  ): List<String> =
      saveImages(context, { "${shopPath(shopId)}/${UUID.randomUUID()}.webp" }, *inputPaths)

  /**
   * Deletes all shop photos from cache and Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the shop
   * @throws DiskStorageException if disk delete fails
   * @throws RemoteStorageException if Firebase Storage delete fails
   */
  suspend fun deleteShopPhotos(context: Context, shopId: String, vararg storagePaths: String) {
    val base = shopPath(shopId)
    if (storagePaths.isEmpty()) {
      deleteDirectory(context, base)
    } else {
      val normalized = storagePaths.map { normalizePath(it, base) }.toTypedArray()
      deleteImages(context, *normalized)
    }
  }

  /**
   * Loads multiple shop photos in parallel from cache or Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the shop
   * @param count Number of images to load (loads 0.webp, 1.webp, ..., (count-1).webp)
   * @return List of images as byte arrays in index order
   * @throws DiskStorageException if disk read fails
   * @throws RemoteStorageException if Firebase Storage operations fail
   */
  suspend fun loadShopPhotos(
      context: Context,
      shopId: String,
      urls: List<String>
  ): List<ByteArray> {
    return loadImagesByUrls(context, urls)
  }

  /**
   * Saves multiple space renter photos in parallel to Firebase Storage and local cache.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the space renter
   * @param inputPaths Variable number of absolute paths to source image files
   * @return List of public download URLs in the same order as inputPaths
   * @throws ImageProcessingException if image encoding fails
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   */
  suspend fun saveSpaceRenterPhotos(
      context: Context,
      shopId: String,
      vararg inputPaths: String
  ): List<String> =
      saveImages(context, { "${spaceRenterPath(shopId)}/${UUID.randomUUID()}.webp" }, *inputPaths)

  /**
   * Loads multiple space renter photos in parallel from cache or Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the space renter
   * @param count Number of images to load (loads 0.webp, 1.webp, ..., (count-1).webp)
   * @return List of images as byte arrays in index order
   * @throws DiskStorageException if disk read fails
   * @throws RemoteStorageException if Firebase Storage operations fail
   */
  suspend fun loadSpaceRenterPhotos(
      context: Context,
      shopId: String,
      urls: List<String>
  ): List<ByteArray> {
    return loadImagesByUrls(context, urls)
  }

  /**
   * Deletes all space renter photos from cache and Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param shopId The unique identifier for the space renter
   * @throws DiskStorageException if disk delete fails
   * @throws RemoteStorageException if Firebase Storage delete fails
   */
  suspend fun deleteSpaceRenterPhotos(
      context: Context,
      shopId: String,
      vararg storagePaths: String
  ) {
    val base = spaceRenterPath(shopId)
    if (storagePaths.isEmpty()) {
      deleteDirectory(context, base)
    } else {
      val normalized = storagePaths.map { normalizePath(it, base) }.toTypedArray()
      deleteImages(context, *normalized)
    }
  }

  /**
   * Encodes an image to WebP format and saves it to both local cache and Firebase Storage.
   *
   * @param context Android context for accessing cache directory
   * @param inputPath Absolute path to the source image file
   * @param storagePath Storage path (used for both local cache and Firebase Storage)
   * @throws ImageProcessingException if image encoding fails
   * @throws DiskStorageException if disk write fails
   * @throws RemoteStorageException if Firebase Storage upload fails
   */
  private suspend fun saveImage(context: Context, inputPath: String, storagePath: String): String {
    val bytes =
        try {
          encodeWebP(inputPath)
        } catch (e: Exception) {
          throw ImageProcessingException("Failed to encode image at $inputPath", e)
        }

    return saveImage(context, bytes, storagePath)
  }

  /**
   * Loads an image from local cache if available, otherwise downloads from Firebase Storage.
   *
   * ## Caching Strategy
   * 1. **Cache hit**: Reads directly from local cache directory
   * 2. **Cache miss**: Downloads from Firebase Storage using streaming for memory efficiency
   * 3. **Automatic caching**: Downloaded images are cached locally if sufficient disk space
   *
   * ## Memory Optimization
   * Uses streaming to avoid loading entire images into memory during downloads. When caching is
   * feasible (>10MB free space), streams directly to disk. Otherwise, streams to memory to return
   * the result.
   *
   * ## Download Behavior
   * - Checks disk space before caching (minimum 10MB required)
   * - Uses 8KB buffer for streaming operations
   * - Falls back to in-memory streaming if caching fails
   * - Cleans up partial files if caching fails mid-stream
   *
   * @param context Android context for accessing cache directory
   * @param path Storage path (used for both local cache and Firebase Storage)
   * @return The image as a byte array in WebP format
   * @throws DiskStorageException if disk read/write fails
   * @throws RemoteStorageException if Firebase Storage download fails
   */
  private suspend fun loadImage(context: Context, path: String): ByteArray {
    val diskPath = "${context.cacheDir}/$path"
    val file = File(diskPath)

    // Check if image exists in cache
    if (file.exists()) {
      return try {
        withContext(dispatcher) { file.readBytes() }
      } catch (e: IOException) {
        throw DiskStorageException("Failed to read cached image at $path", e)
      } catch (e: SecurityException) {
        throw DiskStorageException("Permission denied reading $path", e)
      }
    }

    // Download from Firebase Storage using streaming for better memory efficiency
    return try {
      withContext(dispatcher) {
        // Prepare cache directory
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
          parentDir.mkdirs()
        }

        // Fetch the stream from Firebase Storage
        val streamTask = storage.reference.child(path).stream
        val result = streamTask.await()

        // Determine if we can cache directly to disk
        val canCache =
            try {
              // Check if we have reasonable disk space available
              file.parentFile?.usableSpace?.let { it > 10 * 1024 * 1024 } ?: false // 10MB minimum
            } catch (_: Exception) {
              false
            }

        val buffer = ByteArray(8192) // 8KB buffer

        if (canCache) {
          // Stream directly to cache file to avoid loading entire file into memory
          try {
            result.stream.use { inputStream ->
              FileOutputStream(diskPath).use { outputStream ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                  outputStream.write(buffer, 0, bytesRead)
                }
              }
            }

            // Successfully cached, read and return
            file.readBytes()
          } catch (e: IOException) {
            // Failed to cache, clean up and throw
            if (file.exists()) file.delete()
            throw e
          }
        } else {
          // Stream to memory when caching is not feasible
          val outputStream = ByteArrayOutputStream()
          result.stream.use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
              outputStream.write(buffer, 0, bytesRead)
            }
          }
          outputStream.toByteArray()
        }
      }
    } catch (e: StorageException) {
      throw RemoteStorageException("Firebase Storage download failed for $path: ${e.errorCode}", e)
    } catch (e: IOException) {
      throw RemoteStorageException("Failed to stream image from Firebase Storage at $path", e)
    } catch (e: Exception) {
      throw RemoteStorageException("Failed to download image from Firebase Storage at $path", e)
    }
  }

  /**
   * Saves multiple images in parallel to Firebase Storage and local cache.
   *
   * @param context Android context for accessing cache directory
   * @param inputPaths Variable number of absolute paths to source image files
   */
  private suspend fun saveImages(
      context: Context,
      pathBuilder: () -> String,
      vararg inputPaths: String
  ): List<String> = coroutineScope {
    inputPaths
        .map { inputPath -> async { saveImage(context, inputPath, pathBuilder()) } }
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
   * @throws DiskStorageException if disk read fails
   * @throws RemoteStorageException if Firebase Storage operations fail
   */
  private suspend fun loadImages(
      context: Context,
      parentPath: String,
      count: Int
  ): List<ByteArray> = coroutineScope {
    val cachedFiles =
        try {
          withContext(dispatcher) {
            val dir = File("${context.cacheDir}/$parentPath")
            if (dir.exists()) dir.listFiles()?.sortedBy { it.name } ?: emptyList() else emptyList()
          }
        } catch (e: IOException) {
          throw DiskStorageException("Failed to list cached images at $parentPath", e)
        } catch (e: SecurityException) {
          throw DiskStorageException("Permission denied accessing $parentPath", e)
        }

    val cachedBytes =
        cachedFiles.take(count).map { file ->
          async {
            try {
              withContext(dispatcher) { file.readBytes() }
            } catch (e: IOException) {
              throw DiskStorageException("Failed to read cached file ${file.name}", e)
            }
          }
        }

    val remaining = count - cachedBytes.size
    val remoteBytes =
        if (remaining > 0) {
          try {
            val refs =
                storage.reference.child(parentPath).listAll().await().items.sortedBy { it.name }
            refs.take(remaining).map { ref ->
              async {
                val path = ref.path.trimStart('/')
                loadImage(context, path)
              }
            }
          } catch (e: StorageException) {
            throw RemoteStorageException(
                "Failed to list images in Firebase Storage at $parentPath: ${e.errorCode}", e)
          } catch (e: Exception) {
            throw RemoteStorageException("Failed to access Firebase Storage at $parentPath", e)
          }
        } else {
          emptyList()
        }

    (cachedBytes + remoteBytes).awaitAll()
  }
  /**
   * Loads multiple images in parallel from their URLs.
   *
   * @param context Android context for accessing cache directory
   * @param urls List of full HTTPS download URLs
   * @return List of images as byte arrays in the same order as urls
   */
  private suspend fun loadImagesByUrls(context: Context, urls: List<String>): List<ByteArray> =
      coroutineScope {
        urls
            .map { url ->
              async {
                val path =
                    if (url.contains("/o/")) {
                      val encoded = url.substringAfter("/o/").substringBefore('?')
                      URLDecoder.decode(encoded, "UTF-8")
                    } else {
                      url
                    }
                loadImage(context, path)
              }
            }
            .awaitAll()
      }

  /**
   * Deletes one or more images from both local cache and Firebase Storage.
   *
   * This method performs complete deletion from both storage tiers. For cache-only deletion (e.g.,
   * during cache eviction), use [deleteLocalImages] instead.
   *
   * ## Deletion Process
   * 1. Deletes from local cache directory
   * 2. Deletes from Firebase Storage in parallel (for multiple images)
   *
   * @param context Android context for accessing cache directory
   * @param storagePaths Storage paths or download URLs to delete
   * @throws DiskStorageException if disk delete fails
   * @throws RemoteStorageException if Firebase Storage delete fails
   * @see deleteLocalImages for cache-only deletion
   */
  private suspend fun deleteImages(context: Context, vararg storagePaths: String) {
    if (storagePaths.isEmpty()) return

    deleteLocalImages(context, *storagePaths)

    try {
      coroutineScope {
        storagePaths
            .map { path -> async { storage.reference.child(path).delete().await() } }
            .awaitAll()
      }
    } catch (e: StorageException) {
      throw RemoteStorageException("Firebase Storage delete failed for images: ${e.errorCode}", e)
    } catch (e: Exception) {
      throw RemoteStorageException("Failed to delete images", e)
    }
  }

  /**
   * Deletes one or more images from local cache only (does not delete from Firebase Storage).
   *
   * This method is useful for cache eviction scenarios where remote images should be preserved but
   * local storage needs to be freed. Used internally by cache management operations.
   *
   * ## Use Cases
   * - Cache eviction when storage is low
   * - Cleaning up old cached accounts ([deleteLocalAccountProfilePicture])
   * - Removing temporary cached data
   *
   * @param context Android context for accessing cache directory
   * @param storagePaths Storage paths to delete from local cache
   * @throws DiskStorageException if disk delete fails
   * @see deleteImages for complete deletion from both cache and Firebase Storage
   */
  private suspend fun deleteLocalImages(context: Context, vararg storagePaths: String) {
    if (storagePaths.isEmpty()) return

    try {
      withContext(dispatcher) {
        storagePaths.forEach { storagePath ->
          val diskPath = cachePath(context, storagePath)
          val file = File(diskPath)
          if (file.exists() && !file.delete()) {
            throw DiskStorageException("Failed to delete cached image at $storagePath")
          }
        }
      }
    } catch (e: DiskStorageException) {
      throw e
    } catch (e: SecurityException) {
      throw DiskStorageException("Permission denied deleting images", e)
    }
  }

  /**
   * Deletes an entire directory and all its contents from both cache and Firebase Storage.
   *
   * Recursively removes all files under the given path prefix from both local cache and Firebase
   * Storage. Used for bulk deletions like removing all shop photos or discussion message photos.
   *
   * ## Recursive Deletion
   * - Deletes all files in the directory
   * - Recursively deletes all subdirectories
   * - Removes the directory itself
   *
   * @param context Android context for accessing cache directory
   * @param parentPath Parent directory path to delete
   * @throws DiskStorageException if local directory deletion fails
   * @throws RemoteStorageException if Firebase Storage deletion fails
   */
  private suspend fun deleteDirectory(context: Context, parentPath: String) {
    try {
      withContext(dispatcher) {
        val dir = File("${context.cacheDir}/$parentPath")
        if (dir.exists() && !dir.deleteRecursively()) {
          throw DiskStorageException("Failed to delete cached directory at $parentPath")
        }
      }
    } catch (e: DiskStorageException) {
      throw e
    } catch (e: SecurityException) {
      throw DiskStorageException("Permission denied deleting $parentPath", e)
    }

    try {
      deleteRemoteDirectory(storage.reference.child(parentPath))
    } catch (e: StorageException) {
      throw RemoteStorageException(
          "Firebase Storage delete failed for $parentPath: ${e.errorCode}", e)
    } catch (e: Exception) {
      throw RemoteStorageException("Failed to delete images at $parentPath", e)
    }
  }

  /**
   * Recursively deletes a directory and all its contents from Firebase Storage.
   *
   * Uses parallel deletion for optimal performance when removing directories with many files.
   * Recursively traverses subdirectories and deletes all items.
   *
   * ## Performance
   * - Parallel deletion of all items at each directory level
   * - Concurrent recursive deletion of subdirectories
   *
   * @param root StorageReference to the directory to delete
   * @throws StorageException if Firebase Storage operations fail
   */
  private suspend fun deleteRemoteDirectory(root: StorageReference) {
    val listResult = root.listAll().await()
    coroutineScope {
      val prefixDeletes =
          listResult.prefixes.map { prefix -> async { deleteRemoteDirectory(prefix) } }
      val itemDeletes = listResult.items.map { item -> async { item.delete().await() } }
      (prefixDeletes + itemDeletes).awaitAll()
    }
  }

  /**
   * Encodes an image from the file system to WebP format with compression and downscaling.
   *
   * @param inputPath Absolute path to the input image file
   * @param targetMaxPx Maximum dimension (width or height) in pixels. Images larger than this are
   *   downscaled
   * @param quality WebP compression quality (0-100, lower means smaller file size)
   * @return The encoded WebP image as a byte array
   * @throws ImageProcessingException if image decoding or encoding fails
   */
  private fun encodeWebP(inputPath: String, targetMaxPx: Int = 1600, quality: Int = 60): ByteArray {
    // Check if file exists and is readable
    val inputFile = File(inputPath)
    if (!inputFile.exists()) {
      throw ImageProcessingException("Input file does not exist: $inputPath")
    }
    if (!inputFile.canRead()) {
      throw ImageProcessingException("Cannot read input file: $inputPath")
    }

    val opts =
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
          BitmapFactory.decodeFile(inputPath, this)

          // Check if image was successfully decoded
          if (outWidth <= 0 || outHeight <= 0) {
            throw ImageProcessingException("Invalid or corrupted image file: $inputPath")
          }

          val maxDim = maxOf(outWidth, outHeight)
          inSampleSize = (maxDim / targetMaxPx).coerceAtLeast(1)
          inJustDecodeBounds = false
        }

    val bmp =
        try {
          BitmapFactory.decodeFile(inputPath, opts)
              ?: throw ImageProcessingException("Failed to decode image: $inputPath (null bitmap)")
        } catch (e: OutOfMemoryError) {
          throw ImageProcessingException("Out of memory while decoding image: $inputPath", e)
        } catch (e: Exception) {
          throw ImageProcessingException("Failed to decode image: $inputPath", e)
        }

    return try {
      val out = ByteArrayOutputStream()
      val compressed = bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
      if (!compressed) {
        throw ImageProcessingException("Failed to compress image to WebP format: $inputPath")
      }
      out.toByteArray()
    } catch (e: OutOfMemoryError) {
      throw ImageProcessingException("Out of memory while compressing image: $inputPath", e)
    } catch (e: ImageProcessingException) {
      throw e
    } catch (e: Exception) {
      throw ImageProcessingException("Failed to encode image to WebP: $inputPath", e)
    } finally {
      bmp.recycle()
    }
  }
}
