package com.github.meeplemeet.integration

import android.content.Context
import com.github.meeplemeet.model.images.ImageRepository

class FakeImageRepository : ImageRepository() {
  override suspend fun saveAccountProfilePicture(
      accountId: String,
      context: Context,
      inputPath: String
  ): String {
    return "https://example.com/profiles/$accountId.webp"
  }

  override suspend fun deleteAccountProfilePicture(accountId: String, context: Context) {
    // No-op
  }

  override suspend fun saveSpaceRenterPhotos(
      context: Context,
      shopId: String,
      vararg inputPaths: String
  ): List<String> {
    return inputPaths.mapIndexed { index, _ ->
        "https://example.com/spacerenter/$shopId/photo_$index.webp"
    }
  }

  override suspend fun deleteSpaceRenterPhotos(
      context: Context,
      shopId: String,
      vararg storagePaths: String
  ) {
    // No-op
  }
}
