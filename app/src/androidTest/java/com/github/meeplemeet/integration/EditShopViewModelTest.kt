package com.github.meeplemeet.integration

import android.content.Context
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.game.GameSearchResult
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shared.location.LocationRepository
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.GameItem
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditShopViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var viewModel: EditShopViewModel

  // Manual Fakes
  private lateinit var fakeShopRepository: FakeShopRepository
  private lateinit var fakeImageRepository: FakeImageRepository
  private lateinit var fakeGameRepo: GameRepository
  private lateinit var locationRepo: LocationRepository

  // Helper classes for Fakes
  open class FakeShopRepository : ShopRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var updateShopCalled = false
    var updateShopParams: Map<String, Any?> = emptyMap()

    var deleteShopCalled = false

    override suspend fun updateShop(
        id: String,
        ownerId: String?,
        name: String?,
        phone: String?,
        email: String?,
        website: String?,
        address: Location?,
        openingHours: List<OpeningHours>?,
        gameCollection: List<GameItem>?,
        photoCollectionUrl: List<String>?
    ) {
      updateShopCalled = true
      updateShopParams =
          mapOf(
              "id" to id,
              "ownerId" to ownerId,
              "name" to name,
              "phone" to phone,
              "email" to email,
              "website" to website,
              "address" to address,
              "openingHours" to openingHours,
              "gameCollection" to gameCollection,
              "photoCollectionUrl" to photoCollectionUrl)
    }

    override suspend fun deleteShop(id: String) {
      deleteShopCalled = true
    }
  }

  open class FakeImageRepository : ImageRepository() {
    var saveShopPhotosCalled = false
    var saveShopPhotosParams: List<String>? = null
    var shouldThrowOnSave = false
    var savedUrls: List<String> = emptyList()

    override suspend fun saveShopPhotos(
        context: Context,
        shopId: String,
        vararg inputPaths: String
    ): List<String> {
      if (shouldThrowOnSave) throw Exception("Upload failed")
      saveShopPhotosCalled = true
      saveShopPhotosParams = inputPaths.toList()
      return savedUrls
    }

    override suspend fun deleteShopPhotos(
        context: Context,
        shopId: String,
        vararg storagePaths: String
    ) {
      // No-op
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    // Mock FirebaseProvider BEFORE creating any Repository that might use it (like
    // FakeShopRepository -> FirestoreRepository)
    // This prevents "IllegalStateException: Cannot call useEmulator() after instance has already
    // been initialized" in other tests
    mockkObject(FirebaseProvider)
    every { FirebaseProvider.db } returns mockk(relaxed = true)
    every { FirebaseProvider.storage } returns mockk(relaxed = true)

    fakeShopRepository = FakeShopRepository()
    fakeImageRepository = FakeImageRepository()

    // Use a manual fake that returns real data class instances, not mocks
    class FakeGameRepository : GameRepository {
      override suspend fun getGameById(gameID: String): Game =
          Game(
              uid = gameID,
              name = "Test Game",
              description = "",
              minPlayers = 1,
              maxPlayers = 4,
              imageURL = "",
              recommendedPlayers = 2,
              averagePlayTime = 60)

      override suspend fun getGamesById(vararg gameIDs: String): List<Game> =
          gameIDs.map { getGameById(it) }

      override suspend fun searchGamesByName(
          query: String,
          maxResults: Int
      ): List<GameSearchResult> = emptyList()
    }
    fakeGameRepo = FakeGameRepository()

    class FakeLocationRepository : LocationRepository {
      override suspend fun search(query: String): List<Location> = emptyList()
    }
    locationRepo = FakeLocationRepository()

    // Clear offline mode state
    OfflineModeManager.clearOfflineMode()
    OfflineModeManager.setNetworkStatusForTesting(true)

    viewModel =
        EditShopViewModel(
            shopRepository = fakeShopRepository,
            imageRepository = fakeImageRepository,
            gameRepository = fakeGameRepo,
            locationRepository = locationRepo)

    // Mock RepositoryProvider to prevent OfflineModeManager from initializing real Firestore
    mockkObject(RepositoryProvider)
    every { RepositoryProvider.shops } returns fakeShopRepository
    every { RepositoryProvider.images } returns fakeImageRepository
    // Games and Locations are mocked via dependency injection into ViewModel
    // RepositoryProvider.games and RepositoryProvider.locations are NOT used by OfflineModeManager
    // so we skip mocking them to avoid IncompatibleClassChangeError with interfaces
    // every { RepositoryProvider.games } returns fakeGameRepo
    // every { RepositoryProvider.locations } returns locationRepo
    every { RepositoryProvider.accounts } returns mockk(relaxed = true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    OfflineModeManager.clearOfflineMode()
    unmockkObject(RepositoryProvider)
    unmockkObject(FirebaseProvider)
  }

  @Test
  fun handleOfflineUpdate_updatesCacheAndRecordsChanges() = runTest {
    // Arrange
    val owner =
        Account(uid = "owner1", handle = "owner", name = "Owner", email = "owner@example.com")
    val shop =
        Shop(
            id = "shop1",
            owner = owner,
            name = "Original Shop",
            phone = "1234567890",
            email = "shop@example.com",
            website = "https://shop.com",
            address = Location(0.0, 0.0, "Original Address"),
            openingHours = emptyList(),
            gameCollection = emptyList())

    // Pre-populate cache to avoid RepositoryProvider usage in initialize
    OfflineModeManager.updateShopCache(shop)

    viewModel.initialize(shop)
    testDispatcher.scheduler.advanceUntilIdle() // Let initialize finish

    // Simulate offline
    OfflineModeManager.setNetworkStatusForTesting(false)

    // Act
    val newName = "Updated Shop Name"
    val newPhone = "0987654321"

    viewModel.updateShop(
        context = mockk(), shop = shop, requester = owner, name = newName, phone = newPhone)

    // Advance coroutines
    testDispatcher.scheduler.advanceUntilIdle()

    // Assert
    // 1. Verify ViewModel state updated
    val currentShop = viewModel.currentShop.value
    Assert.assertNotNull(currentShop)
    Assert.assertEquals(newName, currentShop?.name)
    Assert.assertEquals(newPhone, currentShop?.phone)

    // 2. Verify OfflineModeManager cache updated
    val cachedShop = OfflineModeManager.offlineModeFlow.value.shops["shop1"]?.first
    Assert.assertNotNull(cachedShop)
    Assert.assertEquals(newName, cachedShop?.name)
    Assert.assertEquals(newPhone, cachedShop?.phone)

    // 3. Verify changes recorded
    val changes = OfflineModeManager.offlineModeFlow.value.shops["shop1"]?.second
    Assert.assertNotNull(changes)
    Assert.assertEquals(newName, changes?.get("name"))
    Assert.assertEquals(newPhone, changes?.get("phone"))

    // Verify buildChangeMap logic (only changed fields are present)
    Assert.assertEquals(null, changes?.get("email"))
    Assert.assertEquals(null, changes?.get("website"))
  }

  @Test
  fun handleOnlineUpdate_uploadsImagesAndUpdatesRepository() = runTest {
    // Arrange
    val owner =
        Account(uid = "owner1", handle = "owner", name = "Owner", email = "owner@example.com")
    val initialShop =
        Shop(
            id = "shop1",
            owner = owner,
            name = "Original Shop",
            phone = "1234567890",
            email = "shop@example.com",
            website = "https://shop.com",
            address = Location(0.0, 0.0, "Original Address"),
            openingHours = emptyList(),
            gameCollection = emptyList(),
            photoCollectionUrl = emptyList())

    // Mock online status
    OfflineModeManager.setNetworkStatusForTesting(true)

    // Configure fake
    val localPath = "/storage/emulated/0/DCIM/Camera/IMG_2023.jpg"
    val uploadedUrl =
        "https://firebasestorage.googleapis.com/v0/b/meeple-meet/o/shops%2Fshop1%2F123.webp"

    fakeImageRepository.savedUrls = listOf(uploadedUrl)

    // Act
    viewModel.updateShop(
        context = mockk(),
        shop = initialShop,
        requester = owner,
        photoCollectionUrl = listOf(localPath))

    // Advance coroutines
    testDispatcher.scheduler.advanceUntilIdle()

    // Assert
    // Verify saveShopPhotos was called with the local path
    Assert.assertTrue(fakeImageRepository.saveShopPhotosCalled)
    Assert.assertEquals(listOf(localPath), fakeImageRepository.saveShopPhotosParams)

    // Verify updateShop was called with the uploaded URL
    Assert.assertTrue(fakeShopRepository.updateShopCalled)
    val params = fakeShopRepository.updateShopParams
    Assert.assertEquals("shop1", params["id"])
    Assert.assertEquals(listOf(uploadedUrl), params["photoCollectionUrl"])
    Assert.assertNull(params["name"]) // Should be null as not updated
  }

  @Test(expected = Exception::class)
  fun handleOnlineUpdate_abortsWhenUploadFails() = runTest {
    // Arrange
    val owner =
        Account(uid = "owner1", handle = "owner", name = "Owner", email = "owner@example.com")
    val initialShop =
        Shop(
            id = "shop1",
            owner = owner,
            name = "Original Shop",
            phone = "123",
            email = "email@email.com",
            website = "ref.com",
            address = Location(0.0, 0.0, "Addr"),
            openingHours = emptyList(),
            gameCollection = emptyList(),
            photoCollectionUrl = emptyList())

    OfflineModeManager.setNetworkStatusForTesting(true)

    // Configure fake to throw
    fakeImageRepository.shouldThrowOnSave = true

    // Act
    viewModel.updateShop(
        context = mockk(),
        shop = initialShop,
        requester = owner,
        photoCollectionUrl = listOf("/local/path"))

    // Advance coroutines
    testDispatcher.scheduler.advanceUntilIdle()

    // Assert done by expected exception.
    // If we want to check side effects if exception is swallowed (it's not), we can:
    Assert.assertFalse(fakeShopRepository.updateShopCalled)
  }
}
