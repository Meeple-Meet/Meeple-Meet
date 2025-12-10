package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.utils.FirestoreTests
import io.mockk.mockk
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
  private lateinit var repository: ShopRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = mockk(relaxed = true)

    // Clear offline mode state
    OfflineModeManager.clearOfflineMode()
    OfflineModeManager.setNetworkStatusForTesting(true)

    viewModel = EditShopViewModel(repository)
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

    viewModel.updateShop(shop = shop, requester = owner, name = newName, phone = newPhone)

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
}
