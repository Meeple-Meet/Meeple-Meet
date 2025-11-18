package com.github.meeplemeet.model

// Claude Code generated the documentation

import com.github.meeplemeet.FirebaseProvider
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Base repository class for Firestore collections.
 *
 * This class provides common functionality for repositories that interact with Firestore, including
 * access to a specific collection and UUID generation for new documents.
 *
 * @property collectionName Name of the Firestore collection this repository manages.
 * @property db FirebaseFirestore instance to use. Defaults to the shared instance from
 *   [FirebaseProvider].
 */
open class FirestoreRepository(
    val collectionName: String,
    val db: FirebaseFirestore = FirebaseProvider.db
) {
  init {
    require(collectionName.isNotBlank())
  }

  // The Firestore collection reference for this repository
  val collection = db.collection(collectionName)

  /**
   * Generates a new unique identifier for a document in this collection.
   *
   * @return A unique document ID that can be used when creating new documents
   */
  fun newUUID() = collection.document().id
}
