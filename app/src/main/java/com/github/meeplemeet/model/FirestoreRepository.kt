package com.github.meeplemeet.model

// Claude Code generated the documentation

import com.github.meeplemeet.FirebaseProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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
    val subCollections: List<String> = emptyList()
) {
  init {
    require(collectionName.isNotBlank())
  }

  val db: FirebaseFirestore = FirebaseProvider.db
  // The Firestore collection reference for this repository
  val collection = db.collection(collectionName)

  /**
   * Generates a new unique identifier for a document in this collection.
   *
   * @return A unique document ID that can be used when creating new documents
   */
  fun newUUID() = collection.document().id

  /**
   * Fully deletes a document and all its subcollections from Firestore.
   *
   * This function performs a complete deletion of a document including all documents in its
   * subcollections as defined by [subCollections]. It uses batched writes to efficiently handle
   * large amounts of data while respecting Firestore's transaction limits.
   *
   * The deletion process:
   * 1. Iterates through all subcollections specified in [subCollections]
   * 2. Retrieves all documents in each subcollection
   * 3. Batches delete operations (up to 450 per batch to stay under Firestore's 500 operation
   *    limit)
   * 4. Commits batches as they fill up
   * 5. Finally deletes the parent document itself
   *
   * Note: This operation is not atomic across multiple batches. If the operation fails partway
   * through, some documents may be deleted while others remain.
   *
   * @param parent The DocumentReference of the parent document to delete along with its
   *   subcollections
   * @throws Exception if any Firestore operation fails during the deletion process
   */
  suspend fun fullyDeleteDocument(parent: DocumentReference) {
    val db = parent.firestore
    val batch = db.batch()
    var count = 0

    for (name in subCollections) {
      val col = parent.collection(name)
      val snap = col.get().await()

      for (doc in snap.documents) {
        batch.delete(doc.reference)
        count += 1
        if (count == 450) {
          batch.commit().await()
          count = 0
        }
      }
    }

    batch.delete(parent)
    batch.commit().await()
  }
}
