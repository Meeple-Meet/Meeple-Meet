package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.TimeSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class OfflineModeManagerShopTest {

  private val testAccount =
      Account(uid = "test_uid", handle = "@test", name = "Test User", email = "test@test.com")
  private val testLocation = Location(latitude = 1.0, longitude = 1.0, name = "Test Location")
  private val testOpeningHours =
      listOf(OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "17:00"))))
  private val testGame =
      Game(
          uid = "game_1",
          name = "Test Game",
          minPlayers = 2,
          maxPlayers = 4,
          averagePlayTime = 60,
          description = "Test Description",
          imageURL = "",
          recommendedPlayers = 2)
  private val testGameCollection = listOf(testGame to 1)

  private lateinit var testShop: Shop

  @Before
  fun setup() = runBlocking {
    OfflineModeManager.setNetworkStatusForTesting(false)
    testShop =
        Shop(
            id = "test_shop_coverage",
            owner = testAccount,
            name = "Original Name",
            phone = "123",
            email = "original@test.com",
            website = "original.com",
            address = testLocation,
            openingHours = emptyList(),
            gameCollection = emptyList(),
            photoCollectionUrl = emptyList())
    OfflineModeManager.addPendingShop(testShop)
    // Clear initial pending status to start fresh for change tracking
    OfflineModeManager.clearShopChanges(testShop.id)
    delay(50)
  }

  @After
  fun tearDown() {
    OfflineModeManager.removeShop(testShop.id)
    OfflineModeManager.setNetworkStatusForTesting(true)
  }

  @Test
  fun setShopChange_updatesAllSimpleFields() = runBlocking {
    OfflineModeManager.setShopChange(testShop, "name", "New Name")
    OfflineModeManager.setShopChange(testShop, "phone", "456")
    OfflineModeManager.setShopChange(testShop, "email", "new@test.com")
    OfflineModeManager.setShopChange(testShop, "website", "new.com")
    delay(50)

    // Verify the cached object is updated (applyShopChanges logic)
    var cached: Shop? = null
    OfflineModeManager.loadShop(testShop.id) { cached = it }
    delay(50)

    assertNotNull(cached)
    assertEquals("New Name", cached!!.name)
    assertEquals("456", cached!!.phone)
    assertEquals("new@test.com", cached!!.email)
    assertEquals("new.com", cached!!.website)
  }

  @Test
  fun setShopChange_updatesComplexFields() = runBlocking {
    val newLocation = Location(latitude = 2.0, longitude = 2.0, name = "New Location")
    val newOpeningHours = testOpeningHours
    val newGameCollection = testGameCollection
    val newPhotos = listOf("photo1.jpg", "photo2.jpg")

    OfflineModeManager.setShopChange(testShop, "address", newLocation)
    OfflineModeManager.setShopChange(testShop, "openingHours", newOpeningHours)
    OfflineModeManager.setShopChange(testShop, "gameCollection", newGameCollection)
    OfflineModeManager.setShopChange(testShop, "photoCollectionUrl", newPhotos)
    delay(50)

    var cached: Shop? = null
    OfflineModeManager.loadShop(testShop.id) { cached = it }
    delay(50)

    assertNotNull(cached)
    assertEquals(newLocation, cached!!.address)
    assertEquals(newOpeningHours, cached!!.openingHours)
    assertEquals(newGameCollection, cached!!.gameCollection)
    assertEquals(newPhotos, cached!!.photoCollectionUrl)
  }

  @Test
  fun setShopChange_ignoresTypeMismatches() = runBlocking {
    // Try to set String fields with Ints
    OfflineModeManager.setShopChange(testShop, "name", 123)
    OfflineModeManager.setShopChange(testShop, "phone", 123)

    // Try to set complex fields with Strings
    OfflineModeManager.setShopChange(testShop, "address", "Not a location")
    OfflineModeManager.setShopChange(testShop, "openingHours", "Not a list")
    OfflineModeManager.setShopChange(testShop, "gameCollection", 123)
    OfflineModeManager.setShopChange(testShop, "photoCollectionUrl", 123)

    delay(50)

    var cached: Shop? = null
    OfflineModeManager.loadShop(testShop.id) { cached = it }
    delay(50)

    assertNotNull(cached)
    // Should retain original values
    assertEquals("Original Name", cached!!.name)
    assertEquals("123", cached!!.phone)
    assertEquals(testLocation, cached!!.address)
    assertEquals(0, cached!!.openingHours.size)
    assertEquals(0, cached!!.gameCollection.size)
    assertEquals(0, cached!!.photoCollectionUrl.size)
  }

  @Test
  fun setShopChange_validatesListContent() = runBlocking {
    // Try to set openingHours with list of Strings
    val invalidList = listOf("Not", "Opening", "Hours")
    OfflineModeManager.setShopChange(testShop, "openingHours", invalidList)

    // Try to set gameCollection with list of Strings
    OfflineModeManager.setShopChange(testShop, "gameCollection", invalidList)

    // Try to set photoCollectionUrl with list of Ints
    val invalidPhotoList = listOf(1, 2, 3)
    OfflineModeManager.setShopChange(testShop, "photoCollectionUrl", invalidPhotoList)

    delay(50)

    var cached: Shop? = null
    OfflineModeManager.loadShop(testShop.id) { cached = it }
    delay(50)

    assertNotNull(cached)
    // Should retain original values
    assertEquals(0, cached!!.openingHours.size)
    assertEquals(0, cached!!.gameCollection.size)
    assertEquals(0, cached!!.photoCollectionUrl.size)
  }
}
