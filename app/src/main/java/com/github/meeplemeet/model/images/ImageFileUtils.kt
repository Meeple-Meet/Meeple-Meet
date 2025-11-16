package com.github.meeplemeet.model.images

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helpers to cache user-selected images (gallery or camera) into app cache storage so they can be
 * uploaded later.
 */
object ImageFileUtils {
  /** Copy a content Uri to a temporary JPEG file in cache and return its absolute path. */
  suspend fun cacheUriToFile(context: Context, uri: Uri): String =
      withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val file = File.createTempFile("gallery_", ".jpg", context.cacheDir)
        resolver.openInputStream(uri)?.use { input ->
          FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.absolutePath
      }

  /** Save an in-memory bitmap to a temporary JPEG file in cache and return its absolute path. */
  suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap): String =
      withContext(Dispatchers.IO) {
        val file = File.createTempFile("camera_", ".jpg", context.cacheDir)
        FileOutputStream(file).use { output ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        file.absolutePath
      }
}
