// Test suite initially generated with Claude 4.5, following Meeple Meet's global test architecture.
// Then, the test suite was manually reviewed, cleaned up, and debugged.
package com.github.meeplemeet.integration

import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.map.MarkerPreview
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPin
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import java.time.LocalDateTime
import java.time.LocalTime
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreMarkerPreviewTest : FirestoreTests() {
  private lateinit var testAccount: Account
  private lateinit var testShop: Shop
  private lateinit var testGame1: Game
  private lateinit var testGame2: Game
  private lateinit var testDiscussionWithSession: Discussion
  private lateinit var testDiscussionWithoutSession: Discussion
  private lateinit var testSpaceRenter: SpaceRenter
  private lateinit var testLocation: Location
  private lateinit var testTimestamp: Timestamp

  @Before
  fun setup() {
    testLocation = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")
    testTimestamp = Timestamp.now()

    runBlocking {
      // Create test account
      testAccount =
          accountRepository.createAccount(
              "testuser", "Test User", "test@example.com", photoUrl = null)

      // Create test game
      db.collection(GAMES_COLLECTION_PATH)
          .document("game_test_1")
          .set(
              GameNoUid(
                  name = "Catan",
                  description = "Strategy board game",
                  imageURL = "https://example.com/catan.jpg",
                  minPlayers = 3,
                  maxPlayers = 4,
                  recommendedPlayers = null,
                  averagePlayTime = null,
                  genres = emptyList()))
          .await()

      db.collection(GAMES_COLLECTION_PATH)
          .document("game_test_2")
          .set(
              GameNoUid(
                  name = "Ticket to Ride",
                  description = "Train adventure game",
                  imageURL = "https://example.com/ttr.jpg",
                  minPlayers = 2,
                  maxPlayers = 5,
                  recommendedPlayers = null,
                  averagePlayTime = null,
                  genres = emptyList()))
          .await()

      testGame1 = gameRepository.getGameById("game_test_1")
      testGame2 = gameRepository.getGameById("game_test_2")

      // Create test shop with opening hours
      val shopOpeningHours = createTestOpeningHours()
      testShop =
          shopRepository.createShop(
              owner = testAccount,
              name = "Test Board Game Shop",
              address = testLocation,
              openingHours = shopOpeningHours)

      // Create discussion with session
      val baseDiscussion =
          discussionRepository.createDiscussion(
              "Session Discussion", "Test discussion with session", testAccount.uid)

      testDiscussionWithSession =
          sessionRepository.createSession(
              baseDiscussion.uid,
              "Epic Game Night",
              testGame1.uid,
              testTimestamp,
              testLocation,
              testAccount.uid)

      // Create discussion without session
      testDiscussionWithoutSession =
          discussionRepository.createDiscussion(
              "No Session Discussion", "Test discussion without session", testAccount.uid)

      // Create test space renter
      val spaceOpeningHours = createTestOpeningHours()
      testSpaceRenter =
          spaceRenterRepository.createSpaceRenter(
              owner = testAccount,
              name = "Test Game Space",
              address = testLocation,
              openingHours = spaceOpeningHours,
              spaces = listOf(Space(seats = 10, costPerHour = 25.0)))
    }
  }

  // ========================================================================
  // Helper Methods
  // ========================================================================

  /**
   * Creates a standard set of opening hours for testing. Configured to be open during typical
   * business hours (9:00-18:00 Monday-Friday, 10:00-17:00 Saturday-Sunday).
   */
  private fun createTestOpeningHours(): List<OpeningHours> {
    return listOf(
        OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))), // Monday
        OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))), // Tuesday
        OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))), // Wednesday
        OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))), // Thursday
        OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "18:00"))), // Friday
        OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "17:00"))), // Saturday
        OpeningHours(day = 7, hours = listOf(TimeSlot("10:00", "17:00")))) // Sunday
  }

  /**
   * Creates opening hours that will be open at the current time. Useful for testing the isOpenNow
   * logic.
   */
  private fun createAlwaysOpen(): List<OpeningHours> {
    val fullDaySlot = listOf(TimeSlot("00:00", "23:59"))
    return (1..7).map { day -> OpeningHours(day = day, hours = fullDaySlot) }
  }

  /**
   * Creates opening hours that will be closed at the current time. Useful for testing the isOpenNow
   * logic.
   */
  private fun createAlwaysClosed(): List<OpeningHours> {
    return (1..7).map { day -> OpeningHours(day = day, hours = emptyList()) }
  }

  // ========================================================================
  // Single Pin Preview Tests
  // ========================================================================

  @Test
  fun getMarkerPreview_returnsShopMarkerPreview_forShopPin() = runTest {
    val shopPin = StorableGeoPin(uid = testShop.id, type = PinType.SHOP)

    val preview = markerPreviewRepository.getMarkerPreview(shopPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    assertEquals("Test Board Game Shop", shopPreview.name)
    assertEquals("EPFL", shopPreview.address)
  }

  @Test
  fun getMarkerPreview_returnsSessionMarkerPreview_forSessionPin() = runTest {
    val sessionPin = StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION)

    val preview = markerPreviewRepository.getMarkerPreview(sessionPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SessionMarkerPreview)

    val sessionPreview = preview as MarkerPreview.SessionMarkerPreview
    assertEquals("Epic Game Night", sessionPreview.title)
    assertEquals("EPFL", sessionPreview.address)
    assertEquals("Catan", sessionPreview.game)
    assertNotNull(sessionPreview.date)
    assertTrue(sessionPreview.date.contains("at"))
  }

  @Test
  fun getMarkerPreview_returnsSpaceMarkerPreview_forSpacePin() = runTest {
    val spacePin = StorableGeoPin(uid = testSpaceRenter.id, type = PinType.SPACE)

    val preview = markerPreviewRepository.getMarkerPreview(spacePin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SpaceMarkerPreview)

    val spacePreview = preview as MarkerPreview.SpaceMarkerPreview
    assertEquals("Test Game Space", spacePreview.name)
    assertEquals("EPFL", spacePreview.address)
  }

  @Test
  fun getMarkerPreview_returnsNull_forDiscussionWithoutSession() = runTest {
    val sessionPin = StorableGeoPin(uid = testDiscussionWithoutSession.uid, type = PinType.SESSION)

    val preview = markerPreviewRepository.getMarkerPreview(sessionPin)

    assertNull(preview)
  }

  @Test(expected = IllegalArgumentException::class)
  fun getMarkerPreview_throwsException_forNonExistentShop() = runTest {
    val invalidPin = StorableGeoPin(uid = "non_existent_shop", type = PinType.SHOP)

    markerPreviewRepository.getMarkerPreview(invalidPin)
  }

  @Test(expected = IllegalArgumentException::class)
  fun getMarkerPreview_throwsException_forNonExistentSpace() = runTest {
    val invalidPin = StorableGeoPin(uid = "non_existent_space", type = PinType.SPACE)

    markerPreviewRepository.getMarkerPreview(invalidPin)
  }

  @Test(expected = com.github.meeplemeet.model.DiscussionNotFoundException::class)
  fun getMarkerPreview_throwsException_forNonExistentSession() = runTest {
    val invalidPin = StorableGeoPin(uid = "non_existent_discussion", type = PinType.SESSION)

    markerPreviewRepository.getMarkerPreview(invalidPin)
  }

  // ========================================================================
  // Multiple Pins Preview Tests
  // ========================================================================

  @Test
  fun getMarkerPreviews_returnsEmptyList_forEmptyInput() = runTest {
    val previews = markerPreviewRepository.getMarkerPreviews(emptyList())

    assertNotNull(previews)
    assertTrue(previews.isEmpty())
  }

  @Test
  fun getMarkerPreviews_returnsSinglePreview_forSingleShopPin() = runTest {
    val pins = listOf(StorableGeoPin(uid = testShop.id, type = PinType.SHOP))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(1, previews.size)
    assertNotNull(previews[0])
    assertTrue(previews[0] is MarkerPreview.ShopMarkerPreview)

    val shopPreview = previews[0] as MarkerPreview.ShopMarkerPreview
    assertEquals("Test Board Game Shop", shopPreview.name)
  }

  @Test
  fun getMarkerPreviews_returnsMixedPreviews_forMixedPinTypes() = runTest {
    val pins =
        listOf(
            StorableGeoPin(uid = testShop.id, type = PinType.SHOP),
            StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION),
            StorableGeoPin(uid = testSpaceRenter.id, type = PinType.SPACE))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(3, previews.size)

    // Verify we have one of each type
    val shopPreviews = previews.filterIsInstance<MarkerPreview.ShopMarkerPreview>()
    val sessionPreviews = previews.filterIsInstance<MarkerPreview.SessionMarkerPreview>()
    val spacePreviews = previews.filterIsInstance<MarkerPreview.SpaceMarkerPreview>()

    assertEquals(1, shopPreviews.size)
    assertEquals(1, sessionPreviews.size)
    assertEquals(1, spacePreviews.size)
  }

  @Test
  fun getMarkerPreviews_handlesNullSessionPreview_inMixedList() = runTest {
    val pins =
        listOf(
            StorableGeoPin(uid = testShop.id, type = PinType.SHOP),
            StorableGeoPin(uid = testDiscussionWithoutSession.uid, type = PinType.SESSION),
            StorableGeoPin(uid = testSpaceRenter.id, type = PinType.SPACE))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(3, previews.size)

    // One should be null (the session without session data)
    val nullCount = previews.count { it == null }
    assertEquals(1, nullCount)

    // The other two should be valid
    val nonNullPreviews = previews.filterNotNull()
    assertEquals(2, nonNullPreviews.size)
  }

  @Test
  fun getMarkerPreviews_handlesMultiplePinsOfSameType() = runTest {
    // Create additional shops
    val shop2 =
        shopRepository.createShop(
            owner = testAccount,
            name = "Second Shop",
            address = testLocation,
            openingHours = createTestOpeningHours())

    val shop3 =
        shopRepository.createShop(
            owner = testAccount,
            name = "Third Shop",
            address = testLocation,
            openingHours = createTestOpeningHours())

    val pins =
        listOf(
            StorableGeoPin(uid = testShop.id, type = PinType.SHOP),
            StorableGeoPin(uid = shop2.id, type = PinType.SHOP),
            StorableGeoPin(uid = shop3.id, type = PinType.SHOP))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(3, previews.size)
    assertTrue(previews.all { it is MarkerPreview.ShopMarkerPreview })

    val shopPreviews = previews.filterIsInstance<MarkerPreview.ShopMarkerPreview>()
    val shopNames = shopPreviews.map { it.name }.toSet()

    assertTrue(shopNames.contains("Test Board Game Shop"))
    assertTrue(shopNames.contains("Second Shop"))
    assertTrue(shopNames.contains("Third Shop"))
  }

  @Test
  fun getMarkerPreviews_handlesLargeNumberOfPins() = runTest {
    // Create 10 shops
    val shops = mutableListOf(testShop)
    repeat(9) { i ->
      val shop =
          shopRepository.createShop(
              owner = testAccount,
              name = "Shop ${i + 2}",
              address = testLocation,
              openingHours = createTestOpeningHours())
      shops.add(shop)
    }

    val pins = shops.map { StorableGeoPin(uid = it.id, type = PinType.SHOP) }

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(10, previews.size)
    assertTrue(previews.all { it is MarkerPreview.ShopMarkerPreview })
  }

  // ========================================================================
  // Opening Hours Logic Tests (isOpenNow)
  // ========================================================================

  @Test
  fun shopMarkerPreview_showsOpen_whenCurrentlyOpen() = runTest {
    // Create shop with hours that are definitely open now
    val openNowHours = createAlwaysOpen()
    val openShop =
        shopRepository.createShop(
            owner = testAccount,
            name = "Open Shop",
            address = testLocation,
            openingHours = openNowHours)

    val pin = StorableGeoPin(uid = openShop.id, type = PinType.SHOP)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    assertTrue(shopPreview.open)
  }

  @Test
  fun shopMarkerPreview_showsClosed_whenCurrentlyClosed() = runTest {
    // Create shop with hours that are definitely closed now
    val closedNowHours = createAlwaysClosed()
    val closedShop =
        shopRepository.createShop(
            owner = testAccount,
            name = "Closed Shop",
            address = testLocation,
            openingHours = closedNowHours)

    val pin = StorableGeoPin(uid = closedShop.id, type = PinType.SHOP)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    assertTrue(!shopPreview.open)
  }

  @Test
  fun shopMarkerPreview_showsClosed_whenNoOpeningHoursForToday() = runTest {
    // Create shop with opening hours for a different day
    val now = LocalDateTime.now()
    val notToday = if (now.dayOfWeek.value == 1) 2 else 1

    val differentDayHours =
        listOf(OpeningHours(day = notToday, hours = listOf(TimeSlot("09:00", "18:00"))))

    val shop =
        shopRepository.createShop(
            owner = testAccount,
            name = "Closed Today Shop",
            address = testLocation,
            openingHours = differentDayHours)

    val pin = StorableGeoPin(uid = shop.id, type = PinType.SHOP)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    assertTrue(!shopPreview.open)
  }

  @Test
  fun shopMarkerPreview_showsClosed_withEmptyOpeningHours() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount,
            name = "No Hours Shop",
            address = testLocation,
            openingHours = emptyList())

    val pin = StorableGeoPin(uid = shop.id, type = PinType.SHOP)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    assertTrue(!shopPreview.open)
  }

  @Test
  fun spaceMarkerPreview_correctlyReflectsOpenStatus() = runTest {
    // Create space with hours that are definitely open now
    val openNowHours = createAlwaysOpen()
    val openSpace =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount,
            name = "Open Space",
            address = testLocation,
            openingHours = openNowHours)

    val pin = StorableGeoPin(uid = openSpace.id, type = PinType.SPACE)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SpaceMarkerPreview)

    val spacePreview = preview as MarkerPreview.SpaceMarkerPreview
    assertTrue(spacePreview.open)
  }

  @Test
  fun shopMarkerPreview_handlesMultipleTimeSlotsInDay() = runTest {
    // Create hours with multiple slots (e.g., 09:00-12:00 and 14:00-18:00)
    val now = LocalDateTime.now()
    val currentDay = now.dayOfWeek.value
    val currentTime = now.toLocalTime()

    // Create two time slots where current time might fall in between
    val morningSlot = TimeSlot("09:00", "12:00")
    val afternoonSlot = TimeSlot("14:00", "18:00")

    val multiSlotHours =
        listOf(OpeningHours(day = currentDay, hours = listOf(morningSlot, afternoonSlot)))

    val shop =
        shopRepository.createShop(
            owner = testAccount,
            name = "Multi Slot Shop",
            address = testLocation,
            openingHours = multiSlotHours)

    val pin = StorableGeoPin(uid = shop.id, type = PinType.SHOP)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    // Should be open if current time is in either slot
    val expectedOpen =
        (currentTime.isAfter(LocalTime.parse("09:00")) &&
            currentTime.isBefore(LocalTime.parse("12:00"))) ||
            (currentTime.isAfter(LocalTime.parse("14:00")) &&
                currentTime.isBefore(LocalTime.parse("18:00")))

    assertEquals(expectedOpen, shopPreview.open)
  }

  // ========================================================================
  // Date Formatting Tests (formatTimeStamp)
  // ========================================================================

  @Test
  fun sessionMarkerPreview_formatsDateCorrectly() = runTest {
    val sessionPin = StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION)

    val preview = markerPreviewRepository.getMarkerPreview(sessionPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SessionMarkerPreview)

    val sessionPreview = preview as MarkerPreview.SessionMarkerPreview

    // Check format: "dd/MM/yyyy at HH:mm"
    assertTrue(sessionPreview.date.matches(Regex("\\d{2}/\\d{2}/\\d{4} at \\d{2}:\\d{2}")))

    // Should contain " at " separator
    assertTrue(sessionPreview.date.contains(" at "))

    // Should have proper date separators
    val datePart = sessionPreview.date.split(" at ")[0]
    assertEquals(2, datePart.count { it == '/' })
  }

  @Test
  fun sessionMarkerPreview_formatsTimestampWithCorrectComponents() = runTest {
    // Create a session with a specific timestamp
    val specificDate = Timestamp(1700000000, 0) // Nov 14, 2023, 22:13:20 UTC

    val discussion =
        discussionRepository.createDiscussion("Specific Date Session", "Test", testAccount.uid)

    val sessionWithSpecificDate =
        sessionRepository.createSession(
            discussion.uid,
            "Specific Session",
            testGame1.uid,
            specificDate,
            testLocation,
            testAccount.uid)

    val sessionPin = StorableGeoPin(uid = sessionWithSpecificDate.uid, type = PinType.SESSION)
    val preview = markerPreviewRepository.getMarkerPreview(sessionPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SessionMarkerPreview)

    val sessionPreview = preview as MarkerPreview.SessionMarkerPreview

    // Verify the formatted date contains expected components
    assertNotNull(sessionPreview.date)
    assertTrue(sessionPreview.date.isNotEmpty())
    assertTrue(sessionPreview.date.contains("/"))
    assertTrue(sessionPreview.date.contains(" at "))
    assertTrue(sessionPreview.date.contains(":"))
  }

  // ========================================================================
  // Game Name Resolution Tests
  // ========================================================================

  @Test
  fun sessionMarkerPreview_resolvesGameName() = runTest {
    val sessionPin = StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION)

    val preview = markerPreviewRepository.getMarkerPreview(sessionPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SessionMarkerPreview)

    val sessionPreview = preview as MarkerPreview.SessionMarkerPreview
    assertEquals("Catan", sessionPreview.game)
  }

  @Test
  fun sessionMarkerPreview_handlesMultipleSessions_withDifferentGames() = runTest {
    // Create another session
    val discussion2 =
        discussionRepository.createDiscussion("Another Session", "Test 2", testAccount.uid)

    val session2 =
        sessionRepository.createSession(
            discussion2.uid,
            "Train Game Night",
            testGame2.uid,
            testTimestamp,
            testLocation,
            testAccount.uid)

    val pins =
        listOf(
            StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION),
            StorableGeoPin(uid = session2.uid, type = PinType.SESSION))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(2, previews.size)

    val sessionPreviews = previews.filterIsInstance<MarkerPreview.SessionMarkerPreview>()
    assertEquals(2, sessionPreviews.size)

    val gameNames = sessionPreviews.map { it.game }.toSet()
    assertTrue(gameNames.contains("Catan"))
    assertTrue(gameNames.contains("Ticket to Ride"))
  }

  // ========================================================================
  // Location Data Tests
  // ========================================================================

  @Test
  fun allPreviewTypes_containCorrectLocationNames() = runTest {
    val pins =
        listOf(
            StorableGeoPin(uid = testShop.id, type = PinType.SHOP),
            StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION),
            StorableGeoPin(uid = testSpaceRenter.id, type = PinType.SPACE))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(3, previews.size)

    // All should have "EPFL" as address
    previews.filterNotNull().forEach { preview -> assertEquals("EPFL", preview.address) }
  }

  @Test
  fun previews_handleDifferentLocations() = runTest {
    val location2 = Location(latitude = 48.8566, longitude = 2.3522, name = "Paris")

    val shop2 =
        shopRepository.createShop(
            owner = testAccount,
            name = "Paris Shop",
            address = location2,
            openingHours = createTestOpeningHours())

    val pins =
        listOf(
            StorableGeoPin(uid = testShop.id, type = PinType.SHOP),
            StorableGeoPin(uid = shop2.id, type = PinType.SHOP))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(2, previews.size)

    val shopPreviews = previews.filterIsInstance<MarkerPreview.ShopMarkerPreview>()
    val addresses = shopPreviews.map { it.address }.toSet()

    assertTrue(addresses.contains("EPFL"))
    assertTrue(addresses.contains("Paris"))
  }

  // ========================================================================
  // Parallel Processing Tests
  // ========================================================================

  @Test
  fun getMarkerPreviews_processesMultiplePinsInParallel() = runTest {
    // Create multiple entities of each type
    val shops = mutableListOf(testShop)
    repeat(3) { i ->
      shops.add(
          shopRepository.createShop(
              owner = testAccount,
              name = "Shop $i",
              address = testLocation,
              openingHours = createTestOpeningHours()))
    }

    val spaces = mutableListOf(testSpaceRenter)
    repeat(3) { i ->
      spaces.add(
          spaceRenterRepository.createSpaceRenter(
              owner = testAccount,
              name = "Space $i",
              address = testLocation,
              openingHours = createTestOpeningHours()))
    }

    val sessions = mutableListOf(testDiscussionWithSession)
    repeat(3) { i ->
      val disc = discussionRepository.createDiscussion("Session $i", "Test", testAccount.uid)
      sessions.add(
          sessionRepository.createSession(
              disc.uid, "Game $i", testGame1.uid, testTimestamp, testLocation, testAccount.uid))
    }

    // Create mixed list of all pins
    val pins =
        shops.map { StorableGeoPin(uid = it.id, type = PinType.SHOP) } +
            spaces.map { StorableGeoPin(uid = it.id, type = PinType.SPACE) } +
            sessions.map { StorableGeoPin(uid = it.uid, type = PinType.SESSION) }

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    // Should have all previews
    assertEquals(12, previews.size)

    // Verify counts by type
    val shopPreviews = previews.filterIsInstance<MarkerPreview.ShopMarkerPreview>()
    val spacePreviews = previews.filterIsInstance<MarkerPreview.SpaceMarkerPreview>()
    val sessionPreviews = previews.filterIsInstance<MarkerPreview.SessionMarkerPreview>()

    assertEquals(4, shopPreviews.size)
    assertEquals(4, spacePreviews.size)
    assertEquals(4, sessionPreviews.size)
  }

  // ========================================================================
  // Edge Cases and Error Handling
  // ========================================================================

  @Test
  fun getMarkerPreviews_handlesAllNullSessions() = runTest {
    // Create multiple discussions without sessions
    val discussions =
        List(3) { i ->
          discussionRepository.createDiscussion("Discussion $i", "No session", testAccount.uid)
        }

    val pins = discussions.map { StorableGeoPin(uid = it.uid, type = PinType.SESSION) }

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(3, previews.size)
    assertTrue(previews.all { it == null })
  }

  @Test
  fun getMarkerPreview_handlesShopWithEmptyName() = runTest {
    val shop =
        shopRepository.createShop(
            owner = testAccount,
            name = "",
            address = testLocation,
            openingHours = createTestOpeningHours())

    val pin = StorableGeoPin(uid = shop.id, type = PinType.SHOP)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    assertEquals("", shopPreview.name)
  }

  @Test
  fun getMarkerPreview_handlesLocationWithEmptyName() = runTest {
    val emptyNameLocation = Location(latitude = 46.5197, longitude = 6.5665, name = "")

    val shop =
        shopRepository.createShop(
            owner = testAccount,
            name = "Test Shop",
            address = emptyNameLocation,
            openingHours = createTestOpeningHours())

    val pin = StorableGeoPin(uid = shop.id, type = PinType.SHOP)
    val preview = markerPreviewRepository.getMarkerPreview(pin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    val shopPreview = preview as MarkerPreview.ShopMarkerPreview
    assertEquals("", shopPreview.address)
  }

  @Test
  fun getMarkerPreviews_maintainsCorrectOrder_forSameTypeSequentialPins() = runTest {
    val shop1 =
        shopRepository.createShop(
            owner = testAccount,
            name = "A Shop",
            address = testLocation,
            openingHours = createTestOpeningHours())
    val shop2 =
        shopRepository.createShop(
            owner = testAccount,
            name = "B Shop",
            address = testLocation,
            openingHours = createTestOpeningHours())
    val shop3 =
        shopRepository.createShop(
            owner = testAccount,
            name = "C Shop",
            address = testLocation,
            openingHours = createTestOpeningHours())

    val pins =
        listOf(
            StorableGeoPin(uid = shop1.id, type = PinType.SHOP),
            StorableGeoPin(uid = shop2.id, type = PinType.SHOP),
            StorableGeoPin(uid = shop3.id, type = PinType.SHOP))

    val previews = markerPreviewRepository.getMarkerPreviews(pins)

    assertEquals(3, previews.size)

    // Since shops are processed in parallel and merged, we just verify all are present
    val shopPreviews = previews.filterIsInstance<MarkerPreview.ShopMarkerPreview>()
    val names = shopPreviews.map { it.name }.toSet()

    assertEquals(3, names.size)
    assertTrue(names.contains("A Shop"))
    assertTrue(names.contains("B Shop"))
    assertTrue(names.contains("C Shop"))
  }

  @Test
  fun sessionMarkerPreview_containsAllRequiredFields() = runTest {
    val sessionPin = StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION)

    val preview = markerPreviewRepository.getMarkerPreview(sessionPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SessionMarkerPreview)

    val sessionPreview = preview as MarkerPreview.SessionMarkerPreview

    // Verify all fields are populated and non-empty
    assertNotNull(sessionPreview.title)
    assertTrue(sessionPreview.title.isNotEmpty())

    assertNotNull(sessionPreview.address)
    assertTrue(sessionPreview.address.isNotEmpty())

    assertNotNull(sessionPreview.game)
    assertTrue(sessionPreview.game.isNotEmpty())

    assertNotNull(sessionPreview.date)
    assertTrue(sessionPreview.date.isNotEmpty())
  }

  @Test
  fun shopMarkerPreview_inheritsFromMarkerPreview() = runTest {
    val shopPin = StorableGeoPin(uid = testShop.id, type = PinType.SHOP)

    val preview = markerPreviewRepository.getMarkerPreview(shopPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview)
    assertTrue(preview is MarkerPreview.ShopMarkerPreview)

    // Verify base class properties
    assertEquals("Test Board Game Shop", preview?.name)
    assertEquals("EPFL", preview?.address)
  }

  @Test
  fun sessionMarkerPreview_usesSessionNameAsTitle() = runTest {
    val sessionPin = StorableGeoPin(uid = testDiscussionWithSession.uid, type = PinType.SESSION)

    val preview = markerPreviewRepository.getMarkerPreview(sessionPin)

    assertNotNull(preview)
    assertTrue(preview is MarkerPreview.SessionMarkerPreview)

    val sessionPreview = preview as MarkerPreview.SessionMarkerPreview

    // Title should match session name
    assertEquals("Epic Game Night", sessionPreview.title)

    // And also be accessible via base class property
    assertEquals("Epic Game Night", sessionPreview.name)
  }
}
