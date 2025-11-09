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
 * @property collectionName The name of the Firestore collection this repository manages
 * @property db The FirebaseFirestore instance to use. Defaults to the shared instance from
 *   FirebaseProvider
 */
open class FirestoreRepository(
    collectionName: String,
    val db: FirebaseFirestore = FirebaseProvider.db
) {
  companion object {
    private val used = mutableSetOf<String>()
  }

  init {
    // Make sure the collection name is not blank and not already in use
    require(collectionName.isNotBlank())
    require(used.add(collectionName))
  }

  /** The Firestore collection reference for this repository */
  val collection = db.collection(collectionName)

  /**
   * Generates a new unique identifier for a document in this collection.
   *
   * @return A unique document ID that can be used when creating new documents
   */
  fun newUUID() = collection.document().id
}
