package com.github.meeplemeet.utils

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
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

@OptIn(ExperimentalCoroutinesApi::class)
open class  FirestoreTests {
  lateinit var db: FirebaseFirestore
  lateinit var auth: FirebaseAuth

  lateinit var authenticationRepository: AuthenticationRepository
  lateinit var handlesRepository: HandlesRepository
  lateinit var accountRepository: AccountRepository
  lateinit var discussionRepository: DiscussionRepository
  lateinit var sessionRepository: SessionRepository
  lateinit var gameRepository: FirestoreGameRepository
  lateinit var locationRepository: LocationRepository
  lateinit var geoPinRepository: StorableGeoPinRepository
  lateinit var markerPreviewRepository: MarkerPreviewRepository
  lateinit var postRepository: PostRepository
  lateinit var shopRepository: ShopRepository
  lateinit var spaceRenterRepository: SpaceRenterRepository

  companion object {
    var firestoreEmulatorLaunched = false
    var authEmulatorLaunched = false

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

  private suspend fun deleteAllCollectionsOnce(db: FirebaseFirestore) {
    val cleaner = FirestoreTests()
    cleaner.db = db
    cleaner.deleteCollection(accountRepository.collection)
    cleaner.deleteCollection(handlesRepository.collection)
    cleaner.deleteCollection(discussionRepository.collection)
    cleaner.deleteCollection(postRepository.collection)
    cleaner.deleteCollection(geoPinRepository.collection)
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

    authenticationRepository = RepositoryProvider.authentication
    handlesRepository = RepositoryProvider.handles
    accountRepository = RepositoryProvider.accounts
    discussionRepository = RepositoryProvider.discussions
    sessionRepository = RepositoryProvider.sessions
    gameRepository = RepositoryProvider.games
    locationRepository = RepositoryProvider.locations
    geoPinRepository = RepositoryProvider.geoPins
    markerPreviewRepository = RepositoryProvider.markerPreviews
    postRepository = RepositoryProvider.posts
    shopRepository = RepositoryProvider.shops
    spaceRenterRepository = RepositoryProvider.spaceRenters

    runBlocking {
      val db = FirebaseProvider.db
      deleteAllCollectionsOnce(db)
    }
  }
}
