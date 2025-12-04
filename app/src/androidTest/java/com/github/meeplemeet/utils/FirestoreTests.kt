package com.github.meeplemeet.utils

import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.account.HandlesRepository
import com.github.meeplemeet.model.auth.AuthenticationRepository
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.map.MarkerPreviewRepository
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.posts.PostRepository
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.shared.game.FirestoreGameRepository
import com.github.meeplemeet.model.shared.location.LocationRepository
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.BeforeClass

@OptIn(ExperimentalCoroutinesApi::class)
open class FirestoreTests {
  lateinit var db: FirebaseFirestore
  lateinit var auth: FirebaseAuth
  lateinit var storage: FirebaseStorage

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
  lateinit var imageRepository: ImageRepository

  companion object {
    var firestoreEmulatorLaunched = false
    var authEmulatorLaunched = false
    var storageEmulatorLaunched = false

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
      if (!storageEmulatorLaunched) {
        storageEmulatorLaunched = true
        FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199)
      }
    }
  }

  private suspend fun deleteAllCollectionsOnce(db: FirebaseFirestore) {
    val cleaner = FirestoreTests()
    cleaner.db = db
    cleaner.deleteCollection(accountRepository.collection, isAccount = true)
    cleaner.deleteCollection(handlesRepository.collection)
    cleaner.deleteCollection(discussionRepository.collection)
    cleaner.deleteCollection(postRepository.collection, isPost = true)
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
          deleteCollection(
              collection.document(doc.id).collection("businesses"), batchSize = batchSize)
          deleteCollection(
              collection.document(doc.id).collection("relationships"), batchSize = batchSize)
          deleteCollection(
              collection.document(doc.id).collection("notifications"), batchSize = batchSize)
        }
        doc.reference.delete().await()
      }
    }
  }

  private suspend fun deleteAllStorageFiles(storage: FirebaseStorage) {
    try {
      val listResult = storage.reference.listAll().await()

      // Delete all files in the root
      for (item in listResult.items) item.delete().await()

      // Recursively delete all files in subdirectories
      for (prefix in listResult.prefixes) deleteStoragePrefix(prefix)
    } catch (_: Exception) {}
  }

  private suspend fun deleteStoragePrefix(ref: StorageReference) {
    try {
      val listResult = ref.listAll().await()

      for (item in listResult.items) item.delete().await()

      for (prefix in listResult.prefixes) deleteStoragePrefix(prefix)
    } catch (_: Exception) {}
  }

  @Before
  fun testsSetup() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      UiBehaviorConfig.hideBottomBarWhenInputFocused = false
      UiBehaviorConfig.clearFocusOnKeyboardHide = false
    }

    // Set online mode for tests that expect Firestore interaction
    OfflineModeManager.setInternetConnection(true)

    db = FirebaseProvider.db
    auth = FirebaseProvider.auth
    storage = FirebaseProvider.storage

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
    imageRepository = RepositoryProvider.images

    runBlocking {
      val db = FirebaseProvider.db
      deleteAllCollectionsOnce(db)
      deleteAllStorageFiles(storage)
    }

    OfflineModeManager.forceInternet()
  }
}
