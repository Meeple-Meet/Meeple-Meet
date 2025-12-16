package com.github.meeplemeet.integration

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.GameItem
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class FirestoreShopTests : FirestoreTests() {
  private var createShopViewModel = CreateShopViewModel()
  private var shopViewModel = ShopViewModel()
  private var editShopViewModel = EditShopViewModel()

  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testGame1: Game
  private lateinit var testGame2: Game
  private lateinit var testLocation1: Location
  private lateinit var testLocation2: Location
  private lateinit var testOpeningHours: List<OpeningHours>

  @Before
  fun setup() {
    runBlocking {
      // Create test accounts
      testAccount1 =
          accountRepository.createAccount(
              "alice", "Alice", email = "alice@shop.com", photoUrl = null)
      testAccount2 =
          accountRepository.createAccount("bob", "Bob", email = "bob@shop.com", photoUrl = null)

      // Create test games
      db.collection(GAMES_COLLECTION_PATH)
          .document("g_catan")
          .set(
              GameNoUid(
                  name = "Catan",
                  description = "Settlers game",
                  imageURL = "https://example.com/catan.jpg",
                  minPlayers = 3,
                  maxPlayers = 4,
                  genres = listOf("1", "2")))
          .await()

      db.collection(GAMES_COLLECTION_PATH)
          .document("g_chess")
          .set(
              GameNoUid(
                  name = "Chess",
                  description = "Classic strategy",
                  imageURL = "https://example.com/chess.jpg",
                  minPlayers = 2,
                  maxPlayers = 2,
                  genres = listOf("3")))
          .await()

      testGame1 = gameRepository.getGameById("g_catan")
      testGame2 = gameRepository.getGameById("g_chess")
    }

    testLocation1 = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")
    testLocation2 = Location(latitude = 46.2044, longitude = 6.1432, name = "Geneva")

    testOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))))
  }

  @After
  fun tearDown() {
    runBlocking {
      // Clean up shops collection
      val snapshot = shopRepository.collection.get().await()
      val batch = db.batch()
      snapshot.documents.forEach { batch.delete(it.reference) }
      batch.commit().await()

      // Clean up games collection
      val gamesSnapshot = db.collection(GAMES_COLLECTION_PATH).get().await()
      val gamesBatch = db.batch()
      gamesSnapshot.documents.forEach { gamesBatch.delete(it.reference) }
      gamesBatch.commit().await()
    }
  }

  @Test
  fun createShopCreatesNewShop() = runTest {
    val gameCollection =
        listOf(
            GameItem(testGame1.uid, testGame1.name, 5), GameItem(testGame2.uid, testGame2.name, 3))

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Board Game Cafe",
            phone = "+41 21 123 4567",
            email = "contact@bgcafe.com",
            website = "https://bgcafe.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = gameCollection)

    assertNotNull(shop.id)
    assertEquals("Board Game Cafe", shop.name)
    assertEquals(testAccount1.uid, shop.owner.uid)
    assertEquals("+41 21 123 4567", shop.phone)
    assertEquals("contact@bgcafe.com", shop.email)
    assertEquals("https://bgcafe.com", shop.website)
    assertEquals(testLocation1, shop.address)
    assertEquals(testOpeningHours.size, shop.openingHours.size)
    assertEquals(2, shop.gameCollection.size)
    assertEquals(5, shop.gameCollection.find { it.gameId == testGame1.uid }?.quantity)
    assertEquals(3, shop.gameCollection.find { it.gameId == testGame2.uid }?.quantity)
  }

  @Test
  fun createShopWithEmptyGameCollectionWorks() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "New Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    assertNotNull(shop.id)
    assertEquals("New Shop", shop.name)
    assertTrue(shop.gameCollection.isEmpty())
    assertEquals("", shop.phone)
    assertEquals("", shop.email)
    assertEquals("", shop.website)
  }

  @Test
  fun getShopRetrievesExistingShop() = runTest {
    val gameCollection = listOf(GameItem(testGame1.uid, testGame1.name, 10))

    val created =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            phone = "+41 22 987 6543",
            email = "test@shop.com",
            website = "https://testshop.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = gameCollection)

    val fetched = shopRepository.getShop(created.id)

    assertEquals(created.id, fetched.id)
    assertEquals("Test Shop", fetched.name)
    assertEquals(testAccount1.uid, fetched.owner.uid)
    assertEquals("+41 22 987 6543", fetched.phone)
    assertEquals("test@shop.com", fetched.email)
    assertEquals("https://testshop.com", fetched.website)
    assertEquals(testLocation1, fetched.address)
    assertEquals(testOpeningHours.size, fetched.openingHours.size)
    assertEquals(1, fetched.gameCollection.size)
    assertEquals(testGame1.uid, fetched.gameCollection[0].gameId)
    assertEquals(10, fetched.gameCollection[0].quantity)
  }

  @Test(expected = IllegalArgumentException::class)
  fun getShopThrowsForNonExistentShop() = runTest { shopRepository.getShop("non-existent-shop-id") }

  @Test
  fun getShopsReturnsMultipleShops() = runTest {
    shopRepository.createShop(
        owner = testAccount1,
        name = "Shop 1",
        address = testLocation1,
        openingHours = testOpeningHours)

    shopRepository.createShop(
        owner = testAccount2,
        name = "Shop 2",
        address = testLocation2,
        openingHours = testOpeningHours)

    shopRepository.createShop(
        owner = testAccount1,
        name = "Shop 3",
        address = testLocation1,
        openingHours = testOpeningHours)

    val shops = shopRepository.getShops(10u)

    assertTrue(shops.size >= 3)
    assertTrue(shops.any { it.name == "Shop 1" })
    assertTrue(shops.any { it.name == "Shop 2" })
    assertTrue(shops.any { it.name == "Shop 3" })
  }

  @Test
  fun getShopsRespectsMaxResults() = runTest {
    // Create 5 shops
    for (i in 1..5) {
      shopRepository.createShop(
          owner = testAccount1,
          name = "Shop $i",
          address = testLocation1,
          openingHours = testOpeningHours)
    }

    val shops = shopRepository.getShops(2u)

    assertEquals(2, shops.size)
  }

  @Test
  fun updateShopIndividualFieldsWorkCorrectly() = runTest {
    // Test updating name
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Original Shop",
            phone = "+41 11 111 1111",
            email = "old@shop.com",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = listOf(GameItem(testGame1.uid, testGame1.name, 5)))

    // Update name
    shopRepository.updateShop(shop.id, name = "New Name")
    var updated = shopRepository.getShop(shop.id)
    assertEquals("New Name", updated.name)

    // Update phone
    shopRepository.updateShop(shop.id, phone = "+41 22 222 2222")
    updated = shopRepository.getShop(shop.id)
    assertEquals("+41 22 222 2222", updated.phone)

    // Update email
    shopRepository.updateShop(shop.id, email = "new@shop.com")
    updated = shopRepository.getShop(shop.id)
    assertEquals("new@shop.com", updated.email)

    // Update website
    shopRepository.updateShop(shop.id, website = "https://new.com")
    updated = shopRepository.getShop(shop.id)
    assertEquals("https://new.com", updated.website)

    // Update address
    shopRepository.updateShop(shop.id, address = testLocation2)
    updated = shopRepository.getShop(shop.id)
    assertEquals(testLocation2, updated.address)

    // Update opening hours
    val newOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("10:00", "19:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("10:00", "19:00"))))
    shopRepository.updateShop(shop.id, openingHours = newOpeningHours)
    updated = shopRepository.getShop(shop.id)
    assertEquals(2, updated.openingHours.size)
    assertEquals("10:00", updated.openingHours[0].hours[0].open)

    // Update game collection
    val newGameCollection =
        listOf(
            GameItem(testGame2.uid, testGame2.name, 8), GameItem(testGame1.uid, testGame1.name, 3))
    shopRepository.updateShop(shop.id, gameCollection = newGameCollection)
    updated = shopRepository.getShop(shop.id)
    assertEquals(2, updated.gameCollection.size)
    assertEquals(8, updated.gameCollection.find { it.gameId == testGame2.uid }?.quantity)

    // Update owner
    shopRepository.updateShop(shop.id, ownerId = testAccount2.uid)
    updated = shopRepository.getShop(shop.id)
    assertEquals(testAccount2.uid, updated.owner.uid)
  }

  @Test
  fun updateShopMultipleFieldsUpdatesAll() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Old Shop",
            phone = "+41 11 111 1111",
            email = "old@shop.com",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = listOf(GameItem(testGame1.uid, testGame1.name, 5)))

    val newOpeningHours = listOf(OpeningHours(day = 1, hours = listOf(TimeSlot("08:00", "22:00"))))
    val newGameCollection = listOf(GameItem(testGame2.uid, testGame2.name, 10))

    shopRepository.updateShop(
        shop.id,
        name = "New Shop",
        phone = "+41 22 222 2222",
        email = "new@shop.com",
        website = "https://new.com",
        address = testLocation2,
        openingHours = newOpeningHours,
        gameCollection = newGameCollection)

    val updated = shopRepository.getShop(shop.id)
    assertEquals("New Shop", updated.name)
    assertEquals("+41 22 222 2222", updated.phone)
    assertEquals("new@shop.com", updated.email)
    assertEquals("https://new.com", updated.website)
    assertEquals(testLocation2, updated.address)
    assertEquals(1, updated.openingHours.size)
    assertEquals("08:00", updated.openingHours[0].hours[0].open)
    assertEquals(1, updated.gameCollection.size)
    assertEquals(testGame2.uid, updated.gameCollection[0].gameId)
  }

  @Test(expected = IllegalArgumentException::class)
  fun updateShopThrowsWhenNoFieldsProvided() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id)
  }

  @Test(expected = IllegalArgumentException::class)
  fun deleteShopRemovesShop() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "To Delete",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.deleteShop(shop.id)

    // This should throw IllegalArgumentException
    shopRepository.getShop(shop.id)
  }

  @Test
  fun deleteShopsRemovesMultipleShopsInParallel() = runTest {
    // Create multiple shops
    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop to Delete 1",
            address = testLocation1,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount2,
            name = "Shop to Delete 2",
            address = testLocation2,
            openingHours = testOpeningHours)

    val shop3 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Shop to Delete 3",
            address = testLocation1,
            openingHours = testOpeningHours)

    val idsToDelete = listOf(shop1.id, shop2.id, shop3.id)

    // Delete all shops in parallel
    shopRepository.deleteShops(idsToDelete)

    // Verify all shops are deleted
    idsToDelete.forEach { id ->
      try {
        shopRepository.getShop(id)
        throw AssertionError("Shop $id should have been deleted")
      } catch (_: IllegalArgumentException) {
        // Expected - shop doesn't exist anymore
      }
    }
  }

  @Test
  fun deleteShopsWithEmptyListDoesNothing() = runTest {
    // Create a shop to ensure the repository is not empty
    shopRepository.createShop(
        owner = testAccount1,
        name = "Test Shop",
        address = testLocation1,
        openingHours = testOpeningHours)

    // Delete empty list should not throw
    shopRepository.deleteShops(emptyList())

    // Verify shops still exist
    val shops = shopRepository.getShops(10u)
    assertTrue(shops.isNotEmpty())
  }

  @Test
  fun updateShopPreservesUnchangedFields() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Original Name",
            phone = "+41 11 111 1111",
            email = "original@shop.com",
            website = "https://original.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = listOf(GameItem(testGame1.uid, testGame1.name, 5)))

    // Update only the phone
    shopRepository.updateShop(shop.id, phone = "+41 99 999 9999")

    val updated = shopRepository.getShop(shop.id)
    // Phone should be updated
    assertEquals("+41 99 999 9999", updated.phone)
    // Other fields should remain the same
    assertEquals("Original Name", updated.name)
    assertEquals("original@shop.com", updated.email)
    assertEquals("https://original.com", updated.website)
    assertEquals(testLocation1, updated.address)
    assertEquals(testOpeningHours.size, updated.openingHours.size)
    assertEquals(1, updated.gameCollection.size)
  }

  @Test
  fun createShopWithLargeGameCollectionWorks() = runTest {
    // Create a shop with multiple games
    val largeCollection =
        listOf(
            GameItem(testGame1.uid, testGame1.name, 50),
            GameItem(testGame2.uid, testGame2.name, 30),
            GameItem(testGame1.uid, testGame1.name, 20)) // Note: duplicate game IDs

    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Large Collection Shop",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = largeCollection)

    val fetched = shopRepository.getShop(shop.id)
    assertEquals(3, fetched.gameCollection.size)
  }

  @Test
  fun getShopsReturnsShopsWithCorrectOwners() = runTest {
    shopRepository.createShop(
        owner = testAccount1,
        name = "Alice's Shop",
        address = testLocation1,
        openingHours = testOpeningHours)

    shopRepository.createShop(
        owner = testAccount2,
        name = "Bob's Shop",
        address = testLocation2,
        openingHours = testOpeningHours)

    val shops = shopRepository.getShops(10u)

    val aliceShop = shops.find { it.name == "Alice's Shop" }
    val bobShop = shops.find { it.name == "Bob's Shop" }

    assertNotNull(aliceShop)
    assertNotNull(bobShop)
    assertEquals(testAccount1.uid, aliceShop!!.owner.uid)
    assertEquals("Alice", aliceShop.owner.name)
    assertEquals(testAccount2.uid, bobShop!!.owner.uid)
    assertEquals("Bob", bobShop.owner.name)
  }

  @Test
  fun updateShopWithEmptyGameCollectionClearsCollection() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection =
                listOf(
                    GameItem(testGame1.uid, testGame1.name, 5),
                    GameItem(testGame2.uid, testGame2.name, 3)))

    shopRepository.updateShop(shop.id, gameCollection = emptyList())

    val updated = shopRepository.getShop(shop.id)
    assertTrue(updated.gameCollection.isEmpty())
  }

  @Test
  fun createShopWithEmptyOpeningHoursWorks() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "24/7 Shop",
            address = testLocation1,
            openingHours = emptyList())

    val fetched = shopRepository.getShop(shop.id)
    assertTrue(fetched.openingHours.isEmpty())
  }

  @Test
  fun updateShopWithEmptyOpeningHoursClearsOpeningHours() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id, openingHours = emptyList())

    val updated = shopRepository.getShop(shop.id)
    assertTrue(updated.openingHours.isEmpty())
  }

  @Test
  fun getShopsReturnsEmptyListWhenNoShopsExist() = runTest {
    val shops = shopRepository.getShops(10u)

    assertNotNull(shops)
    assertTrue(shops.isEmpty())
  }

  @Test
  fun createShopAlsoCreatesGeoPin() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "GeoPin Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    val geoPinSnapshot = geoPinRepository.collection.document(shop.id).get().await()

    assert(geoPinSnapshot.exists())
    assertEquals("SHOP", geoPinSnapshot.getString("type"))
  }

  @Test
  fun deleteShopAlsoDeletesGeoPin() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "To Delete Pin",
            address = testLocation1,
            openingHours = testOpeningHours)

    val beforeDelete = geoPinRepository.collection.document(shop.id).get().await()
    assert(beforeDelete.exists())

    shopRepository.deleteShop(shop.id)

    val afterDelete = geoPinRepository.collection.document(shop.id).get().await()
    assert(!afterDelete.exists())
  }

  @Test
  fun updateShopOnlyUpdatesGeoPinIfAddressProvided() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "GeoPin Update Test",
            address = testLocation1,
            openingHours = testOpeningHours)

    val geoPinRef = geoPinRepository.collection.document(shop.id)

    // Location unchanged
    shopRepository.updateShop(shop.id, name = "Updated Name")

    val pinAfterNameUpdate = geoPinRef.get().await()
    assert(pinAfterNameUpdate.exists())
    assertEquals("SHOP", pinAfterNameUpdate.getString("type"))

    // Location changed
    shopRepository.updateShop(shop.id, address = testLocation2)

    val pinAfterLocationUpdate = geoPinRef.get().await()
    assert(pinAfterLocationUpdate.exists())
    assertEquals("SHOP", pinAfterLocationUpdate.getString("type"))
  }

  // ========================================================================
  // CreateShopViewModel Tests
  // ========================================================================

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenNameIsBlank() {
    runBlocking {
      createShopViewModel.createShop(
          owner = testAccount1,
          name = "",
          email = "test@test.com",
          address = testLocation1,
          openingHours = testOpeningHours)
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenNameIsOnlyWhitespace() {
    runBlocking {
      createShopViewModel.createShop(
          owner = testAccount1,
          name = "   ",
          address = testLocation1,
          openingHours = testOpeningHours,
          email = "test@test.com")
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenLessThan7OpeningHours() {
    runBlocking {
      val incompleteHours =
          listOf(
              OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))))

      createShopViewModel.createShop(
          owner = testAccount1,
          name = "Test Shop",
          address = testLocation1,
          openingHours = incompleteHours,
          email = "test@test.com")
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenMoreThan7UniqueOpeningHourDays() {
    runBlocking {
      // Use 8 unique days (0-7) to trigger validation error
      val tooManyHours =
          listOf(
              OpeningHours(day = 0, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 6, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 7, hours = listOf(TimeSlot("09:00", "18:00"))))

      createShopViewModel.createShop(
          owner = testAccount1,
          name = "Test Shop",
          address = testLocation1,
          openingHours = tooManyHours,
          email = "test@test.com")
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenDuplicateDaysInOpeningHours() {
    runBlocking {
      val duplicateDays =
          listOf(
              OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "18:00"))),
              OpeningHours(day = 6, hours = listOf(TimeSlot("09:00", "18:00"))))

      createShopViewModel.createShop(
          owner = testAccount1,
          name = "Test Shop",
          address = testLocation1,
          openingHours = duplicateDays,
          email = "test@test.com")
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenAddressIsDefault() {
    runBlocking {
      createShopViewModel.createShop(
          owner = testAccount1,
          name = "Test Shop",
          address = Location(), // Default empty location
          openingHours = testOpeningHours,
          email = "test@test.com")
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenEmptyOpeningHours() {
    runBlocking {
      createShopViewModel.createShop(
          owner = testAccount1,
          name = "Test Shop",
          address = testLocation1,
          openingHours = emptyList(),
          email = "test@test.com")
    }
  }

  @Test
  fun createShopViewModelSucceedsWithValidData() = runTest {
    val fullWeekHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "17:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("10:00", "17:00"))))

    createShopViewModel.createShop(
        owner = testAccount1,
        name = "Valid Shop",
        phone = "+41 21 123 4567",
        email = "shop@example.com",
        website = "https://shop.example.com",
        address = testLocation1,
        openingHours = fullWeekHours,
        gameCollection = listOf(GameItem(testGame1.uid, testGame1.name, 10)))

    // Give it time to complete the async operation
    delay(300)

    // Verify shop was created by fetching it
    val shops = shopRepository.getShops(10u)
    val createdShop = shops.find { it.name == "Valid Shop" }

    assertNotNull(createdShop)
    assertEquals("Valid Shop", createdShop!!.name)
    assertEquals(testAccount1.uid, createdShop.owner.uid)
    assertEquals("+41 21 123 4567", createdShop.phone)
    assertEquals("shop@example.com", createdShop.email)
    assertEquals("https://shop.example.com", createdShop.website)
    assertEquals(testLocation1, createdShop.address)
    assertEquals(7, createdShop.openingHours.size)
    assertEquals(1, createdShop.gameCollection.size)
  }

  @Test
  fun createShopViewModelSucceedsWithOptionalFieldsEmpty() = runTest {
    val fullWeekHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "17:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("10:00", "17:00"))))

    createShopViewModel.createShop(
        owner = testAccount1,
        name = "Minimal Shop",
        address = testLocation1,
        openingHours = fullWeekHours,
        email = "test@test.com")

    // Give it time to complete the async operation
    delay(300)

    // Verify shop was created
    val shops = shopRepository.getShops(10u)
    val createdShop = shops.find { it.name == "Minimal Shop" }

    assertNotNull(createdShop)
    assertEquals("Minimal Shop", createdShop!!.name)
    assertEquals("", createdShop.phone)
    assertEquals("test@test.com", createdShop.email)
    assertEquals("", createdShop.website)
    assertTrue(createdShop.gameCollection.isEmpty())
  }

  // ========================================================================
  // ShopViewModel Tests
  // ========================================================================

  @Test
  fun shopViewModelInitialStateIsNull() {
    assertNull(shopViewModel.shop.value)
  }

  @Test(expected = IllegalArgumentException::class)
  fun shopViewModelThrowsWhenShopIdIsBlank() {
    shopViewModel.getShop("")
  }

  @Test(expected = IllegalArgumentException::class)
  fun shopViewModelThrowsWhenShopIdIsOnlyWhitespace() {
    shopViewModel.getShop("   ")
  }

  @Test
  fun shopViewModelLoadsShopSuccessfully() = runBlocking {
    // Create a shop first
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop for ViewModel",
            phone = "+41 21 555 0100",
            email = "vm@test.com",
            website = "https://vmtest.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection =
                listOf(
                    GameItem(testGame1.uid, testGame1.name, 5),
                    GameItem(testGame2.uid, testGame2.name, 3)))

    // Load the shop through ViewModel
    shopViewModel.getShop(shop.id)

    val loadedShop =
        kotlinx.coroutines.withTimeout(5000) {
          while (shopViewModel.shop.value == null) {
            delay(100)
          }
          shopViewModel.shop.value!!
        }
    assertNotNull(loadedShop)
    assertEquals(shop.id, loadedShop.id)
    assertEquals("Test Shop for ViewModel", loadedShop.name)
    assertEquals(testAccount1.uid, loadedShop.owner.uid)
    assertEquals("+41 21 555 0100", loadedShop.phone)
    assertEquals("vm@test.com", loadedShop.email)
    assertEquals("https://vmtest.com", loadedShop.website)
    assertEquals(testLocation1, loadedShop.address)
    assertEquals(testOpeningHours.size, loadedShop.openingHours.size)
    assertEquals(2, loadedShop.gameCollection.size)
  }

  @Test
  fun shopViewModelLoadsShopWithMinimalData() = runBlocking {
    // Create a shop with minimal data
    val shop =
        shopRepository.createShop(
            owner = testAccount2,
            name = "Minimal VM Shop",
            address = testLocation2,
            openingHours = testOpeningHours)

    // Load the shop through ViewModel
    shopViewModel.getShop(shop.id)

    // Give it time to complete the async operation
    delay(200)

    // Verify the StateFlow was updated
    val loadedShop = shopViewModel.shop.value
    assertNotNull(loadedShop)
    assertEquals(shop.id, loadedShop!!.id)
    assertEquals("Minimal VM Shop", loadedShop.name)
    assertEquals(testAccount2.uid, loadedShop.owner.uid)
    assertEquals("", loadedShop.phone)
    assertEquals("", loadedShop.email)
    assertEquals("", loadedShop.website)
    assertTrue(loadedShop.gameCollection.isEmpty())
  }

  @Test
  fun shopViewModelUpdatesStateFlowOnMultipleCalls() = runBlocking {
    // Create two different shops
    val shop1 =
        shopRepository.createShop(
            owner = testAccount1,
            name = "First Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    val shop2 =
        shopRepository.createShop(
            owner = testAccount2,
            name = "Second Shop",
            address = testLocation2,
            openingHours = testOpeningHours)

    // Load first shop
    shopViewModel.getShop(shop1.id)
    delay(100)

    assertEquals("First Shop", shopViewModel.shop.value?.name)

    // Load second shop
    shopViewModel.getShop(shop2.id)
    delay(100)

    assertEquals("Second Shop", shopViewModel.shop.value?.name)
  }

  @Test
  fun shopViewModelLoadsShopWithGameCollection() = runBlocking {
    // Create a shop with games
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Game Shop",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection =
                listOf(
                    GameItem(testGame1.uid, testGame1.name, 10),
                    GameItem(testGame2.uid, testGame2.name, 5)))

    // Load the shop through ViewModel
    shopViewModel.getShop(shop.id)
    delay(100)

    // Verify game collection is loaded correctly
    val loadedShop = shopViewModel.shop.value
    assertNotNull(loadedShop)
    assertEquals(2, loadedShop!!.gameCollection.size)

    val game1Entry = loadedShop.gameCollection.find { it.gameId == testGame1.uid }
    val game2Entry = loadedShop.gameCollection.find { it.gameId == testGame2.uid }

    assertNotNull(game1Entry)
    assertNotNull(game2Entry)
    assertEquals(10, game1Entry!!.quantity)
    assertEquals(5, game2Entry!!.quantity)
    assertEquals("Catan", game1Entry.gameName)
    assertEquals("Chess", game2Entry.gameName)
  }

  @Test
  fun shopViewModelLoadsShopWithCorrectOwnerData() = runBlocking {
    // Create a shop
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Owner Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    // Load the shop through ViewModel
    shopViewModel.getShop(shop.id)
    delay(100)

    // Verify owner data is loaded correctly
    val loadedShop = shopViewModel.shop.value
    assertNotNull(loadedShop)
    assertEquals(testAccount1.uid, loadedShop!!.owner.uid)
    assertEquals("Alice", loadedShop.owner.name)
    assertEquals("alice@shop.com", loadedShop.owner.email)
  }

  // ========================================================================
  // EditShopViewModel Tests
  // ========================================================================

  @Test(expected = PermissionDeniedException::class)
  fun editShopViewModelThrowsWhenNonOwnerTriesToUpdate() = runBlocking {
    // Create a shop owned by testAccount1
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Alice's Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    // Try to update as testAccount2 (non-owner)
    editShopViewModel.updateShop(shop, testAccount2, name = "Hacked Shop")
  }

  @Test(expected = PermissionDeniedException::class)
  fun editShopViewModelThrowsWhenNonOwnerTriesToDelete() = runBlocking {
    // Create a shop owned by testAccount1
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Alice's Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    // Try to delete as testAccount2 (non-owner)
    editShopViewModel.deleteShop(shop, testAccount2)
  }

  @Test(expected = IllegalArgumentException::class)
  fun editShopViewModelThrowsWhenUpdatingToBlankName() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Original Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, name = "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun editShopViewModelThrowsWhenUpdatingToWhitespaceName() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Original Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, name = "   ")
  }

  @Test(expected = IllegalArgumentException::class)
  fun editShopViewModelThrowsWhenUpdatingWithLessThan7OpeningHours() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    val incompleteHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))))

    editShopViewModel.updateShop(shop, testAccount1, openingHours = incompleteHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun editShopViewModelThrowsWhenUpdatingWithMoreThan7UniqueOpeningHourDays() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    val tooManyHours =
        listOf(
            OpeningHours(day = 0, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("09:00", "18:00"))))

    editShopViewModel.updateShop(shop, testAccount1, openingHours = tooManyHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun editShopViewModelThrowsWhenUpdatingToDefaultAddress() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, address = Location())
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesName() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Old Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, name = "New Name")
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals("New Name", updated.name)
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesPhone() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            phone = "+41 11 111 1111",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, phone = "+41 99 999 9999")
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals("+41 99 999 9999", updated.phone)
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesEmail() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            email = "old@shop.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, email = "new@shop.com")
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals("new@shop.com", updated.email)
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesWebsite() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, website = "https://new.com")
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals("https://new.com", updated.website)
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesAddress() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.updateShop(shop, testAccount1, address = testLocation2)
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals(testLocation2, updated.address)
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesOpeningHours() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    val newOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("10:00", "19:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("10:00", "19:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("10:00", "19:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("10:00", "19:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("10:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("11:00", "18:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("11:00", "18:00"))))

    editShopViewModel.updateShop(shop, testAccount1, openingHours = newOpeningHours)
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals(7, updated.openingHours.size)
    assertEquals("10:00", updated.openingHours[0].hours[0].open)
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesGameCollection() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = listOf(GameItem(testGame1.uid, testGame1.name, 5)))

    val newGameCollection =
        listOf(
            GameItem(testGame2.uid, testGame2.name, 10), GameItem(testGame1.uid, testGame1.name, 3))
    editShopViewModel.updateShop(shop, testAccount1, gameCollection = newGameCollection)
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals(2, updated.gameCollection.size)
    assertEquals(10, updated.gameCollection.find { it.gameId == testGame2.uid }?.quantity)
    assertEquals(3, updated.gameCollection.find { it.gameId == testGame1.uid }?.quantity)
  }

  @Test
  fun editShopViewModelSuccessfullyUpdatesMultipleFields() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Old Shop",
            phone = "+41 11 111 1111",
            email = "old@shop.com",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    val newOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("08:00", "20:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("08:00", "20:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("08:00", "20:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("08:00", "20:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("08:00", "22:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("09:00", "22:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("09:00", "22:00"))))

    editShopViewModel.updateShop(
        shop,
        testAccount1,
        name = "New Shop",
        phone = "+41 22 222 2222",
        email = "new@shop.com",
        website = "https://new.com",
        address = testLocation2,
        openingHours = newOpeningHours)
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    assertEquals("New Shop", updated.name)
    assertEquals("+41 22 222 2222", updated.phone)
    assertEquals("new@shop.com", updated.email)
    assertEquals("https://new.com", updated.website)
    assertEquals(testLocation2, updated.address)
    assertEquals(7, updated.openingHours.size)
  }

  @Test
  fun editShopViewModelOwnerCanDeleteOwnShop() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "To Delete",
            address = testLocation1,
            openingHours = testOpeningHours)

    editShopViewModel.deleteShop(shop, testAccount1)
    delay(500)

    // Verify shop is deleted
    try {
      shopRepository.getShop(shop.id)
      throw AssertionError("Shop should have been deleted")
    } catch (_: IllegalArgumentException) {
      // Expected - shop doesn't exist anymore
    }
  }

  @Test
  fun editShopViewModelUpdatesPreserveUnchangedFields() = runBlocking {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Original Shop",
            phone = "+41 11 111 1111",
            email = "original@shop.com",
            website = "https://original.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = listOf(GameItem(testGame1.uid, testGame1.name, 5)))

    // Update only the phone
    editShopViewModel.updateShop(shop, testAccount1, phone = "+41 99 999 9999")
    delay(100)

    val updated = shopRepository.getShop(shop.id)
    // Phone should be updated
    assertEquals("+41 99 999 9999", updated.phone)
    // Other fields should remain unchanged
    assertEquals("Original Shop", updated.name)
    assertEquals("original@shop.com", updated.email)
    assertEquals("https://original.com", updated.website)
    assertEquals(testLocation1, updated.address)
    assertEquals(testOpeningHours.size, updated.openingHours.size)
    assertEquals(1, updated.gameCollection.size)
  }
}
