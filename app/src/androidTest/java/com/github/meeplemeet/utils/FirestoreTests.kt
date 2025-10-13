package com.github.meeplemeet.utils

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.repositories.ACCOUNT_COLLECTION_PATH
import com.github.meeplemeet.model.repositories.DISCUSSIONS_COLLECTION_PATH
import com.github.meeplemeet.model.repositories.HANDLES_COLLECTION_PATH
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
  }

  private suspend fun deleteCollection(path: String, batchSize: Long = 100) {
    val collection = db.collection(path)

    while (true) {
      val snapshot = collection.limit(batchSize).get().await()
      if (snapshot.isEmpty) break

      val batch = db.batch()
      for (doc in snapshot.documents) {
        batch.delete(doc.reference)
      }
      batch.commit().await()
    }
  }

  @Before
  fun testsSetup() {
    db = FirebaseProvider.db
    auth = FirebaseProvider.auth

    runBlocking {
      deleteCollection(HANDLES_COLLECTION_PATH)
      deleteCollection(ACCOUNT_COLLECTION_PATH)
      deleteCollection(DISCUSSIONS_COLLECTION_PATH)
    }
  }
}
