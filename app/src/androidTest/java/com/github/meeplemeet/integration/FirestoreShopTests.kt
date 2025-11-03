package com.github.meeplemeet.integration

import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.sessions.FirestoreGameRepository
import com.github.meeplemeet.model.sessions.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.sessions.GameNoUid
import com.github.meeplemeet.model.shared.Location
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.SHOP_COLLECTION_PATH
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class FirestoreShopTests : FirestoreTests() {
  private lateinit var shopRepository: ShopRepository
  private lateinit var discussionRepository: DiscussionRepository
  private lateinit var gameRepository: FirestoreGameRepository
  private lateinit var createShopViewModel: CreateShopViewModel

  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testGame1: Game
  private lateinit var testGame2: Game
  private lateinit var testLocation1: Location
  private lateinit var testLocation2: Location
  private lateinit var testOpeningHours: List<OpeningHours>

  @Before
  fun setup() {
    shopRepository = ShopRepository(db)
    discussionRepository = DiscussionRepository()
    gameRepository = FirestoreGameRepository(db)
    createShopViewModel = CreateShopViewModel(shopRepository)

    runBlocking {
      // Create test accounts
      testAccount1 =
          discussionRepository.createAccount(
              "alice", "Alice", email = "alice@shop.com", photoUrl = null)
      testAccount2 =
          discussionRepository.createAccount("bob", "Bob", email = "bob@shop.com", photoUrl = null)

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
                  genres = listOf(1, 2)))
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
                  genres = listOf(3)))
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
      val snapshot = db.collection(SHOP_COLLECTION_PATH).get().await()
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
    val gameCollection = listOf(testGame1 to 5, testGame2 to 3)

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
    assertEquals(5, shop.gameCollection.find { it.first.uid == testGame1.uid }?.second)
    assertEquals(3, shop.gameCollection.find { it.first.uid == testGame2.uid }?.second)
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
    val gameCollection = listOf(testGame1 to 10)

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
    assertEquals(testGame1.uid, fetched.gameCollection[0].first.uid)
    assertEquals(10, fetched.gameCollection[0].second)
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
  fun updateShopNameUpdatesName() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Old Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id, name = "New Name")

    val updated = shopRepository.getShop(shop.id)
    assertEquals("New Name", updated.name)
    assertEquals(shop.id, updated.id)
  }

  @Test
  fun updateShopPhoneUpdatesPhone() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            phone = "+41 11 111 1111",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id, phone = "+41 22 222 2222")

    val updated = shopRepository.getShop(shop.id)
    assertEquals("+41 22 222 2222", updated.phone)
  }

  @Test
  fun updateShopEmailUpdatesEmail() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            email = "old@shop.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id, email = "new@shop.com")

    val updated = shopRepository.getShop(shop.id)
    assertEquals("new@shop.com", updated.email)
  }

  @Test
  fun updateShopWebsiteUpdatesWebsite() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id, website = "https://new.com")

    val updated = shopRepository.getShop(shop.id)
    assertEquals("https://new.com", updated.website)
  }

  @Test
  fun updateShopAddressUpdatesAddress() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id, address = testLocation2)

    val updated = shopRepository.getShop(shop.id)
    assertEquals(testLocation2, updated.address)
  }

  @Test
  fun updateShopOpeningHoursUpdatesOpeningHours() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    val newOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("10:00", "19:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("10:00", "19:00"))))

    shopRepository.updateShop(shop.id, openingHours = newOpeningHours)

    val updated = shopRepository.getShop(shop.id)
    assertEquals(2, updated.openingHours.size)
    assertEquals("10:00", updated.openingHours[0].hours[0].open)
    assertEquals("19:00", updated.openingHours[0].hours[0].close)
  }

  @Test
  fun updateShopGameCollectionUpdatesGameCollection() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours,
            gameCollection = listOf(testGame1 to 5))

    val newGameCollection = listOf(testGame2 to 8, testGame1 to 3)
    shopRepository.updateShop(shop.id, gameCollection = newGameCollection)

    val updated = shopRepository.getShop(shop.id)
    assertEquals(2, updated.gameCollection.size)
    assertEquals(8, updated.gameCollection.find { it.first.uid == testGame2.uid }?.second)
    assertEquals(3, updated.gameCollection.find { it.first.uid == testGame1.uid }?.second)
  }

  @Test
  fun updateShopOwnerIdUpdatesOwnerId() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount1,
            name = "Test Shop",
            address = testLocation1,
            openingHours = testOpeningHours)

    shopRepository.updateShop(shop.id, ownerId = testAccount2.uid)

    val updated = shopRepository.getShop(shop.id)
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
            gameCollection = listOf(testGame1 to 5))

    val newOpeningHours = listOf(OpeningHours(day = 1, hours = listOf(TimeSlot("08:00", "22:00"))))
    val newGameCollection = listOf(testGame2 to 10)

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
    assertEquals(testGame2.uid, updated.gameCollection[0].first.uid)
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
            gameCollection = listOf(testGame1 to 5))

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
        listOf(testGame1 to 50, testGame2 to 30, testGame1 to 20) // Note: duplicate game IDs

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
            gameCollection = listOf(testGame1 to 5, testGame2 to 3))

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

  // ========================================================================
  // CreateShopViewModel Tests
  // ========================================================================

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenNameIsBlank() {
    createShopViewModel.createShop(
        owner = testAccount1, name = "", address = testLocation1, openingHours = testOpeningHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenNameIsOnlyWhitespace() {
    createShopViewModel.createShop(
        owner = testAccount1,
        name = "   ",
        address = testLocation1,
        openingHours = testOpeningHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenLessThan7OpeningHours() {
    val incompleteHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))))

    createShopViewModel.createShop(
        owner = testAccount1,
        name = "Test Shop",
        address = testLocation1,
        openingHours = incompleteHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenMoreThan7UniqueOpeningHourDays() {
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
        openingHours = tooManyHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenDuplicateDaysInOpeningHours() {
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
        openingHours = duplicateDays)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenAddressIsDefault() {
    createShopViewModel.createShop(
        owner = testAccount1,
        name = "Test Shop",
        address = Location(), // Default empty location
        openingHours = testOpeningHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createShopViewModelThrowsWhenEmptyOpeningHours() {
    createShopViewModel.createShop(
        owner = testAccount1,
        name = "Test Shop",
        address = testLocation1,
        openingHours = emptyList())
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
        gameCollection = listOf(testGame1 to 10))

    // Give it time to complete the async operation
    kotlinx.coroutines.delay(1000)

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
        openingHours = fullWeekHours)

    // Give it time to complete the async operation
    kotlinx.coroutines.delay(1000)

    // Verify shop was created
    val shops = shopRepository.getShops(10u)
    val createdShop = shops.find { it.name == "Minimal Shop" }

    assertNotNull(createdShop)
    assertEquals("Minimal Shop", createdShop!!.name)
    assertEquals("", createdShop.phone)
    assertEquals("", createdShop.email)
    assertEquals("", createdShop.website)
    assertTrue(createdShop.gameCollection.isEmpty())
  }
}
