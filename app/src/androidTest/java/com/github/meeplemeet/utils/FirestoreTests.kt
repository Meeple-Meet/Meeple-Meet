package com.github.meeplemeet.utils

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.auth.HANDLES_COLLECTION_PATH
import com.github.meeplemeet.model.discussions.ACCOUNT_COLLECTION_PATH
import com.github.meeplemeet.model.discussions.DISCUSSIONS_COLLECTION_PATH
import com.github.meeplemeet.model.posts.POSTS_COLLECTION_PATH
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.BeforeClass

var firestoreEmulatorLaunched = false
var authEmulatorLaunched = false

@OptIn(ExperimentalCoroutinesApi::class)
open class FirestoreTests {
  lateinit var db: FirebaseFirestore
  lateinit var auth: FirebaseAuth

  companion object {
    @BeforeClass
    @JvmStatic
    fun globalSetUp() {
      if (!firestoreEmulatorLaunched) {
        firestoreEmulatorLaunched = true
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
      }
      if (!authEmulatorLaunched) {
        authEmulatorLaunched = true
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
      }
    }

    private suspend fun deleteAllCollectionsOnce(db: FirebaseFirestore) {
      val cleaner = FirestoreTests()
      cleaner.db = db
      cleaner.deleteCollection(HANDLES_COLLECTION_PATH)
      cleaner.deleteCollection(ACCOUNT_COLLECTION_PATH)
      cleaner.deleteCollection(DISCUSSIONS_COLLECTION_PATH)
      cleaner.deleteCollection(POSTS_COLLECTION_PATH)
    }
  }

  private suspend fun deleteCollection(path: String, batchSize: Long = 100) {
    val collection = db.collection(path)

    while (true) {
      val snapshot = collection.limit(batchSize).get().await()
      if (snapshot.isEmpty) break

      for (doc in snapshot.documents) {
        // known subcollections
        when (path) {
          POSTS_COLLECTION_PATH -> {
            deleteCollection("$path/${doc.id}/fields", batchSize)
          }
          ACCOUNT_COLLECTION_PATH -> {
            deleteCollection("$path/${doc.id}/previews", batchSize)
          }
        }
        doc.reference.delete().await()
      }
    }
  }

  @Before
  fun testsSetup() {
    db = FirebaseProvider.db
    auth = FirebaseProvider.auth

    runBlocking {
      val db = FirebaseProvider.db
      deleteAllCollectionsOnce(db)
    }
  }
}
