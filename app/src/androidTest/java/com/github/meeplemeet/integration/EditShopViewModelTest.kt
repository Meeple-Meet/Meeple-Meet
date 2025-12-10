package com.github.meeplemeet.integration

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.location.Location
import io.mockk.coEvery
import io.mockk.coVerify
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.utils.FirestoreTests
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
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
class EditShopViewModelTest : FirestoreTests() {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var viewModel: EditShopViewModel
  lateinit var imageRepository: ImageRepository
  private lateinit var repository: ShopRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = mockk(relaxed = true)
    imageRepository = mockk(relaxed = true)

    // Mock RepositoryProvider to avoid Firebase initialization
    mockkObject(RepositoryProvider)
    every { RepositoryProvider.shops } returns repository
    every { RepositoryProvider.games } returns mockk(relaxed = true)
    every { RepositoryProvider.locations } returns mockk(relaxed = true)
    every { RepositoryProvider.accounts } returns mockk(relaxed = true)
    every { RepositoryProvider.images } returns imageRepository

    // Clear offline mode state
    OfflineModeManager.clearOfflineMode()
    OfflineModeManager.setNetworkStatusForTesting(true)

    viewModel = EditShopViewModel(repository, imageRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    OfflineModeManager.clearOfflineMode()
    unmockkAll()
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

    // Mock saveShopPhotos to return a fake URL
    val localPath = "/storage/emulated/0/DCIM/Camera/IMG_2023.jpg"
    val uploadedUrl =
        "https://firebasestorage.googleapis.com/v0/b/meeple-meet/o/shops%2Fshop1%2F123.webp"

    // Allow any arguments for saveShopPhotos
    coEvery { imageRepository.saveShopPhotos(any(), any(), *anyVararg()) } returns
        listOf(uploadedUrl)

    // Act
    // We pass a list containing the local path.
    viewModel.updateShop(
        context = mockk(),
        shop = initialShop,
        requester = owner,
        photoCollectionUrl = listOf(localPath))

    // Advance coroutines
    testDispatcher.scheduler.advanceUntilIdle()

    // Assert
    // Verify saveShopPhotos was called with the local path
    coVerify { imageRepository.saveShopPhotos(any(), eq("shop1"), eq(localPath)) }

    // Verify updateShop was called with the uploaded URL
    coVerify {
      repository.updateShop(
          id = eq("shop1"),
          photoCollectionUrl = eq(listOf(uploadedUrl)),
          // Verify other parameters are null or default as expected
          ownerId = any(),
          name = any(),
          phone = any(),
          email = any(),
          website = any(),
          address = any(),
          openingHours = any(),
          gameCollection = any())
    }
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

    // Mock saveShopPhotos to THROW
    coEvery { imageRepository.saveShopPhotos(any(), any(), *anyVararg()) } throws
        Exception("Upload failed")

    // Act
    viewModel.updateShop(
        context = mockk(),
        shop = initialShop,
        requester = owner,
        photoCollectionUrl = listOf("/local/path"))

    // Advance coroutines
    testDispatcher.scheduler.advanceUntilIdle() // This should throw exception

    // Assert (if exception not thrown, test fails)
    // Verify repository update was NOT called
    coVerify(exactly = 0) {
      repository.updateShop(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }
  }
}
