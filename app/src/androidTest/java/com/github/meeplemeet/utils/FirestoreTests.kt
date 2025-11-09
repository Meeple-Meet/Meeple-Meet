package com.github.meeplemeet.utils

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.auth.AccountRepository
import com.github.meeplemeet.model.auth.AuthenticationRepository
import com.github.meeplemeet.model.auth.HandlesRepository
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.MarkerPreviewRepository
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.posts.PostRepository
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.shared.game.FirestoreGameRepository
import com.github.meeplemeet.model.shared.location.LocationRepository
import com.github.meeplemeet.model.shared.location.NominatimLocationRepository
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
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

    /** Lazily initialized repository for account/authentication operations. */
    val authenticationRepository: AuthenticationRepository by lazy { AuthenticationRepository() }

    /** Lazily initialized repository for user handle operations. */
    val handlesRepository: HandlesRepository by lazy { HandlesRepository() }

    /** Lazily initialized repository for account operations. */
    val accountRepository: AccountRepository by lazy { AccountRepository() }

    /** Lazily initialized repository for discussion operations. */
    val discussionRepository: DiscussionRepository by lazy { DiscussionRepository() }

    /** Lazily initialized repository for gaming session operations. */
    val sessionRepository: SessionRepository by lazy { SessionRepository() }

    /** Lazily initialized repository for board game data operations. */
    val gameRepository: FirestoreGameRepository by lazy { FirestoreGameRepository() }

    /** Lazily initialized repository for location operations. */
    val locationRepository: LocationRepository by lazy { NominatimLocationRepository() }

    /** Lazily initialized repository for geo pin operations. */
    val geoPinRepository: StorableGeoPinRepository by lazy { StorableGeoPinRepository() }

    /** Lazily initialized repository for marker preview operations. */
    val markerPreviewRepository: MarkerPreviewRepository by lazy { MarkerPreviewRepository() }

    /** Lazily initialized repository for post operations. */
    val postRepository: PostRepository by lazy { PostRepository() }

    /** Lazily initialized repository for shop operations. */
    val shopRepository: ShopRepository by lazy { ShopRepository() }

    /** Lazily initialized repository for space renter operations. */
    val spaceRenterRepository: SpaceRenterRepository by lazy { SpaceRenterRepository() }

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
      cleaner.deleteCollection(accountRepository.collection)
      cleaner.deleteCollection(handlesRepository.collection)
      cleaner.deleteCollection(discussionRepository.collection)
      cleaner.deleteCollection(postRepository.collection)
      cleaner.deleteCollection(geoPinRepository.collection)
    }
  }

  private suspend fun deleteCollection(
      collection: CollectionReference,
      isAccount: Boolean = false,
      isPost: Boolean = false,
      batchSize: Long = 100
  ) {
    while (true) {
      val snapshot = collection.limit(batchSize).get().await()
      if (snapshot.isEmpty) break

      for (doc in snapshot.documents) {
        // known subcollections
        if (isPost) {
          deleteCollection(
              collection.document(doc.id).collection("comments"), batchSize = batchSize)
        }
        if (isAccount) {
          deleteCollection(
              collection.document(doc.id).collection("previews"), batchSize = batchSize)
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
