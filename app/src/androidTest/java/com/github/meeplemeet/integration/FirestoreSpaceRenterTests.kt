package com.github.meeplemeet.integration

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.GEO_PIN_COLLECTION_PATH
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.CreateSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.EditSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.SPACE_RENTER_COLLECTION_PATH
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.github.meeplemeet.model.space_renter.SpaceRenterViewModel
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class FirestoreSpaceRenterTests : FirestoreTests() {
  private lateinit var spaceRenterRepository: SpaceRenterRepository
  private lateinit var discussionRepository: DiscussionRepository
  private lateinit var createSpaceRenterViewModel: CreateSpaceRenterViewModel
  private lateinit var spaceRenterViewModel: SpaceRenterViewModel
  private lateinit var editSpaceRenterViewModel: EditSpaceRenterViewModel
  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testLocation1: Location
  private lateinit var testLocation2: Location
  private lateinit var testOpeningHours: List<OpeningHours>
  private lateinit var testSpace1: Space
  private lateinit var testSpace2: Space

  @Before
  fun setup() {
    spaceRenterRepository = SpaceRenterRepository(db)
    discussionRepository = DiscussionRepository()
    createSpaceRenterViewModel = CreateSpaceRenterViewModel(spaceRenterRepository)
    spaceRenterViewModel = SpaceRenterViewModel(spaceRenterRepository)
    editSpaceRenterViewModel = EditSpaceRenterViewModel(spaceRenterRepository)

    runBlocking {
      // Create test accounts
      testAccount1 =
          discussionRepository.createAccount(
              "alice", "Alice", email = "alice@spacerenter.com", photoUrl = null)
      testAccount2 =
          discussionRepository.createAccount(
              "bob", "Bob", email = "bob@spacerenter.com", photoUrl = null)
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

    testSpace1 = Space(seats = 10, costPerHour = 25.0)
    testSpace2 = Space(seats = 20, costPerHour = 50.0)
  }

  @After
  fun tearDown() {
    runBlocking {
      // Clean up space renters collection
      val snapshot = db.collection(SPACE_RENTER_COLLECTION_PATH).get().await()
      val batch = db.batch()
      snapshot.documents.forEach { batch.delete(it.reference) }
      batch.commit().await()
    }
  }

  @Test
  fun createSpaceRenterCreatesNewSpaceRenter() = runTest {
    val spaces = listOf(testSpace1, testSpace2)

    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Game Space Rental",
            phone = "+41 21 123 4567",
            email = "contact@gamespace.com",
            website = "https://gamespace.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = spaces)

    assertNotNull(spaceRenter.id)
    assertEquals("Game Space Rental", spaceRenter.name)
    assertEquals(testAccount1.uid, spaceRenter.owner.uid)
    assertEquals("+41 21 123 4567", spaceRenter.phone)
    assertEquals("contact@gamespace.com", spaceRenter.email)
    assertEquals("https://gamespace.com", spaceRenter.website)
    assertEquals(testLocation1, spaceRenter.address)
    assertEquals(testOpeningHours.size, spaceRenter.openingHours.size)
    assertEquals(2, spaceRenter.spaces.size)
    assertEquals(10, spaceRenter.spaces[0].seats)
    assertEquals(25.0, spaceRenter.spaces[0].costPerHour)
    assertEquals(20, spaceRenter.spaces[1].seats)
    assertEquals(50.0, spaceRenter.spaces[1].costPerHour)
  }

  @Test
  fun createSpaceRenterWithEmptySpacesWorks() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "New Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    assertNotNull(spaceRenter.id)
    assertEquals("New Space Renter", spaceRenter.name)
    assertTrue(spaceRenter.spaces.isEmpty())
    assertEquals("", spaceRenter.phone)
    assertEquals("", spaceRenter.email)
    assertEquals("", spaceRenter.website)
  }

  @Test
  fun getSpaceRenterRetrievesExistingSpaceRenter() = runTest {
    val spaces = listOf(testSpace1)

    val created =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            phone = "+41 22 987 6543",
            email = "test@spacerenter.com",
            website = "https://testspacerenter.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = spaces)

    val fetched = spaceRenterRepository.getSpaceRenter(created.id)

    assertEquals(created.id, fetched.id)
    assertEquals("Test Space Renter", fetched.name)
    assertEquals(testAccount1.uid, fetched.owner.uid)
    assertEquals("+41 22 987 6543", fetched.phone)
    assertEquals("test@spacerenter.com", fetched.email)
    assertEquals("https://testspacerenter.com", fetched.website)
    assertEquals(testLocation1, fetched.address)
    assertEquals(testOpeningHours.size, fetched.openingHours.size)
    assertEquals(1, fetched.spaces.size)
    assertEquals(10, fetched.spaces[0].seats)
    assertEquals(25.0, fetched.spaces[0].costPerHour)
  }

  @Test(expected = IllegalArgumentException::class)
  fun getSpaceRenterThrowsForNonExistentSpaceRenter() = runTest {
    spaceRenterRepository.getSpaceRenter("non-existent-space-renter-id")
  }

  @Test
  fun getSpaceRentersReturnsMultipleSpaceRenters() = runTest {
    spaceRenterRepository.createSpaceRenter(
        owner = testAccount1,
        name = "Space Renter 1",
        address = testLocation1,
        openingHours = testOpeningHours)

    spaceRenterRepository.createSpaceRenter(
        owner = testAccount2,
        name = "Space Renter 2",
        address = testLocation2,
        openingHours = testOpeningHours)

    spaceRenterRepository.createSpaceRenter(
        owner = testAccount1,
        name = "Space Renter 3",
        address = testLocation1,
        openingHours = testOpeningHours)

    val spaceRenters = spaceRenterRepository.getSpaceRenters(10u)

    assertTrue(spaceRenters.size >= 3)
    assertTrue(spaceRenters.any { it.name == "Space Renter 1" })
    assertTrue(spaceRenters.any { it.name == "Space Renter 2" })
    assertTrue(spaceRenters.any { it.name == "Space Renter 3" })
  }

  @Test
  fun getSpaceRentersRespectsMaxResults() = runTest {
    // Create 5 space renters
    for (i in 1..5) {
      spaceRenterRepository.createSpaceRenter(
          owner = testAccount1,
          name = "Space Renter $i",
          address = testLocation1,
          openingHours = testOpeningHours)
    }

    val spaceRenters = spaceRenterRepository.getSpaceRenters(2u)

    assertEquals(2, spaceRenters.size)
  }

  @Test
  fun updateSpaceRenterNameUpdatesName() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Old Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, name = "New Name")

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("New Name", updated.name)
    assertEquals(spaceRenter.id, updated.id)
  }

  @Test
  fun updateSpaceRenterPhoneUpdatesPhone() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            phone = "+41 11 111 1111",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, phone = "+41 22 222 2222")

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("+41 22 222 2222", updated.phone)
  }

  @Test
  fun updateSpaceRenterEmailUpdatesEmail() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            email = "old@spacerenter.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, email = "new@spacerenter.com")

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("new@spacerenter.com", updated.email)
  }

  @Test
  fun updateSpaceRenterWebsiteUpdatesWebsite() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, website = "https://new.com")

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("https://new.com", updated.website)
  }

  @Test
  fun updateSpaceRenterAddressUpdatesAddress() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, address = testLocation2)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals(testLocation2, updated.address)
  }

  @Test
  fun updateSpaceRenterOpeningHoursUpdatesOpeningHours() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    val newOpeningHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("10:00", "19:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("10:00", "19:00"))))

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, openingHours = newOpeningHours)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals(2, updated.openingHours.size)
    assertEquals("10:00", updated.openingHours[0].hours[0].open)
    assertEquals("19:00", updated.openingHours[0].hours[0].close)
  }

  @Test
  fun updateSpaceRenterSpacesUpdatesSpaces() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1))

    val newSpaces = listOf(testSpace2, Space(seats = 5, costPerHour = 15.0))
    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, spaces = newSpaces)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals(2, updated.spaces.size)
    assertEquals(20, updated.spaces[0].seats)
    assertEquals(50.0, updated.spaces[0].costPerHour)
    assertEquals(5, updated.spaces[1].seats)
    assertEquals(15.0, updated.spaces[1].costPerHour)
  }

  @Test
  fun updateSpaceRenterOwnerIdUpdatesOwnerId() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, ownerId = testAccount2.uid)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals(testAccount2.uid, updated.owner.uid)
  }

  @Test
  fun updateSpaceRenterMultipleFieldsUpdatesAll() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Old Space Renter",
            phone = "+41 11 111 1111",
            email = "old@spacerenter.com",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1))

    val newOpeningHours = listOf(OpeningHours(day = 1, hours = listOf(TimeSlot("08:00", "22:00"))))
    val newSpaces = listOf(testSpace2)

    spaceRenterRepository.updateSpaceRenter(
        spaceRenter.id,
        name = "New Space Renter",
        phone = "+41 22 222 2222",
        email = "new@spacerenter.com",
        website = "https://new.com",
        address = testLocation2,
        openingHours = newOpeningHours,
        spaces = newSpaces)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("New Space Renter", updated.name)
    assertEquals("+41 22 222 2222", updated.phone)
    assertEquals("new@spacerenter.com", updated.email)
    assertEquals("https://new.com", updated.website)
    assertEquals(testLocation2, updated.address)
    assertEquals(1, updated.openingHours.size)
    assertEquals("08:00", updated.openingHours[0].hours[0].open)
    assertEquals(1, updated.spaces.size)
    assertEquals(20, updated.spaces[0].seats)
  }

  @Test(expected = IllegalArgumentException::class)
  fun updateSpaceRenterThrowsWhenNoFieldsProvided() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id)
  }

  @Test(expected = IllegalArgumentException::class)
  fun deleteSpaceRenterRemovesSpaceRenter() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "To Delete",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)

    // This should throw IllegalArgumentException
    spaceRenterRepository.getSpaceRenter(spaceRenter.id)
  }

  @Test
  fun updateSpaceRenterPreservesUnchangedFields() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Original Name",
            phone = "+41 11 111 1111",
            email = "original@spacerenter.com",
            website = "https://original.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1))

    // Update only the phone
    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, phone = "+41 99 999 9999")

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    // Phone should be updated
    assertEquals("+41 99 999 9999", updated.phone)
    // Other fields should remain the same
    assertEquals("Original Name", updated.name)
    assertEquals("original@spacerenter.com", updated.email)
    assertEquals("https://original.com", updated.website)
    assertEquals(testLocation1, updated.address)
    assertEquals(testOpeningHours.size, updated.openingHours.size)
    assertEquals(1, updated.spaces.size)
  }

  @Test
  fun getSpaceRentersReturnsSpaceRentersWithCorrectOwners() = runTest {
    spaceRenterRepository.createSpaceRenter(
        owner = testAccount1,
        name = "Alice's Space Renter",
        address = testLocation1,
        openingHours = testOpeningHours)

    spaceRenterRepository.createSpaceRenter(
        owner = testAccount2,
        name = "Bob's Space Renter",
        address = testLocation2,
        openingHours = testOpeningHours)

    val spaceRenters = spaceRenterRepository.getSpaceRenters(10u)

    val aliceSpaceRenter = spaceRenters.find { it.name == "Alice's Space Renter" }
    val bobSpaceRenter = spaceRenters.find { it.name == "Bob's Space Renter" }

    assertNotNull(aliceSpaceRenter)
    assertNotNull(bobSpaceRenter)
    assertEquals(testAccount1.uid, aliceSpaceRenter!!.owner.uid)
    assertEquals("Alice", aliceSpaceRenter.owner.name)
    assertEquals(testAccount2.uid, bobSpaceRenter!!.owner.uid)
    assertEquals("Bob", bobSpaceRenter.owner.name)
  }

  @Test
  fun updateSpaceRenterWithEmptySpacesClearsSpaces() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1, testSpace2))

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, spaces = emptyList())

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertTrue(updated.spaces.isEmpty())
  }

  @Test
  fun createSpaceRenterWithEmptyOpeningHoursWorks() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "24/7 Space Renter",
            address = testLocation1,
            openingHours = emptyList())

    val fetched = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertTrue(fetched.openingHours.isEmpty())
  }

  @Test
  fun updateSpaceRenterWithEmptyOpeningHoursClearsOpeningHours() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, openingHours = emptyList())

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertTrue(updated.openingHours.isEmpty())
  }

  @Test
  fun getSpaceRentersReturnsEmptyListWhenNoSpaceRentersExist() = runTest {
    val spaceRenters = spaceRenterRepository.getSpaceRenters(10u)

    assertNotNull(spaceRenters)
    assertTrue(spaceRenters.isEmpty())
  }

  @Test
  fun createSpaceRenterAlsoCreatesGeoPin() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "GeoPin Space",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1))

    val geoPinSnapshot =
        db.collection(GEO_PIN_COLLECTION_PATH).document(spaceRenter.id).get().await()

    assert(geoPinSnapshot.exists())
    assertEquals("SPACE", geoPinSnapshot.getString("type"))
  }

  @Test
  fun deleteSpaceRenterAlsoDeletesGeoPin() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "To Delete Space",
            address = testLocation1,
            openingHours = testOpeningHours)

    val beforeDelete = db.collection(GEO_PIN_COLLECTION_PATH).document(spaceRenter.id).get().await()
    assert(beforeDelete.exists())

    spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)

    val afterDelete = db.collection(GEO_PIN_COLLECTION_PATH).document(spaceRenter.id).get().await()
    assert(!afterDelete.exists())
  }

  @Test
  fun updateSpaceRenterOnlyUpdatesGeoPinIfAddressProvided() = runTest {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "GeoPin Update Space",
            address = testLocation1,
            openingHours = testOpeningHours)

    val geoPinRef = db.collection(GEO_PIN_COLLECTION_PATH).document(spaceRenter.id)

    // Location unchanged
    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, name = "Updated Name")

    val pinAfterNameUpdate = geoPinRef.get().await()
    assert(pinAfterNameUpdate.exists())
    assertEquals("SPACE", pinAfterNameUpdate.getString("type"))

    // Location changed
    spaceRenterRepository.updateSpaceRenter(spaceRenter.id, address = testLocation2)

    val pinAfterLocationUpdate = geoPinRef.get().await()
    assert(pinAfterLocationUpdate.exists())
    assertEquals("SPACE", pinAfterLocationUpdate.getString("type"))
  }

  // ========================================================================
  // CreateSpaceRenterViewModel Tests
  // ========================================================================

  @Test(expected = IllegalArgumentException::class)
  fun createSpaceRenterViewModelThrowsWhenNameIsBlank() {
    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1, name = "", address = testLocation1, openingHours = testOpeningHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createSpaceRenterViewModelThrowsWhenNameIsOnlyWhitespace() {
    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "   ",
        address = testLocation1,
        openingHours = testOpeningHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createSpaceRenterViewModelThrowsWhenLessThan7OpeningHours() {
    val incompleteHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))))

    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "Test Space Renter",
        address = testLocation1,
        openingHours = incompleteHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createSpaceRenterViewModelThrowsWhenMoreThan7UniqueOpeningHourDays() {
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

    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "Test Space Renter",
        address = testLocation1,
        openingHours = tooManyHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createSpaceRenterViewModelThrowsWhenDuplicateDaysInOpeningHours() {
    val duplicateDays =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("09:00", "18:00"))))

    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "Test Space Renter",
        address = testLocation1,
        openingHours = duplicateDays)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createSpaceRenterViewModelThrowsWhenAddressIsDefault() {
    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "Test Space Renter",
        address = Location(), // Default empty location
        openingHours = testOpeningHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createSpaceRenterViewModelThrowsWhenEmptyOpeningHours() {
    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "Test Space Renter",
        address = testLocation1,
        openingHours = emptyList())
  }

  @Test
  fun createSpaceRenterViewModelSucceedsWithValidData() = runTest {
    val fullWeekHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "17:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("10:00", "17:00"))))

    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "Valid Space Renter",
        phone = "+41 21 123 4567",
        email = "spacerenter@example.com",
        website = "https://spacerenter.example.com",
        address = testLocation1,
        openingHours = fullWeekHours,
        spaces = listOf(testSpace1))

    // Give it time to complete the async operation
    kotlinx.coroutines.delay(1000)

    // Verify space renter was created by fetching it
    val spaceRenters = spaceRenterRepository.getSpaceRenters(10u)
    val createdSpaceRenter = spaceRenters.find { it.name == "Valid Space Renter" }

    assertNotNull(createdSpaceRenter)
    assertEquals("Valid Space Renter", createdSpaceRenter!!.name)
    assertEquals(testAccount1.uid, createdSpaceRenter.owner.uid)
    assertEquals("+41 21 123 4567", createdSpaceRenter.phone)
    assertEquals("spacerenter@example.com", createdSpaceRenter.email)
    assertEquals("https://spacerenter.example.com", createdSpaceRenter.website)
    assertEquals(testLocation1, createdSpaceRenter.address)
    assertEquals(7, createdSpaceRenter.openingHours.size)
    assertEquals(1, createdSpaceRenter.spaces.size)
  }

  @Test
  fun createSpaceRenterViewModelSucceedsWithOptionalFieldsEmpty() = runTest {
    val fullWeekHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "17:00"))),
            OpeningHours(day = 7, hours = listOf(TimeSlot("10:00", "17:00"))))

    createSpaceRenterViewModel.createSpaceRenter(
        owner = testAccount1,
        name = "Minimal Space Renter",
        address = testLocation1,
        openingHours = fullWeekHours)

    // Give it time to complete the async operation
    kotlinx.coroutines.delay(1000)

    // Verify space renter was created
    val spaceRenters = spaceRenterRepository.getSpaceRenters(10u)
    val createdSpaceRenter = spaceRenters.find { it.name == "Minimal Space Renter" }

    assertNotNull(createdSpaceRenter)
    assertEquals("Minimal Space Renter", createdSpaceRenter!!.name)
    assertEquals("", createdSpaceRenter.phone)
    assertEquals("", createdSpaceRenter.email)
    assertEquals("", createdSpaceRenter.website)
    assertTrue(createdSpaceRenter.spaces.isEmpty())
  }

  // ========================================================================
  // SpaceRenterViewModel Tests
  // ========================================================================

  @Test
  fun spaceRenterViewModelInitialStateIsNull() {
    assertNull(spaceRenterViewModel.spaceRenter.value)
  }

  @Test(expected = IllegalArgumentException::class)
  fun spaceRenterViewModelThrowsWhenSpaceRenterIdIsBlank() {
    spaceRenterViewModel.getSpaceRenter("")
  }

  @Test(expected = IllegalArgumentException::class)
  fun spaceRenterViewModelThrowsWhenSpaceRenterIdIsOnlyWhitespace() {
    spaceRenterViewModel.getSpaceRenter("   ")
  }

  @Test
  fun spaceRenterViewModelLoadsSpaceRenterSuccessfully() = runBlocking {
    // Create a space renter first
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter for ViewModel",
            phone = "+41 21 555 0100",
            email = "vm@test.com",
            website = "https://vmtest.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1, testSpace2))

    // Load the space renter through ViewModel
    spaceRenterViewModel.getSpaceRenter(spaceRenter.id)

    // Give it time to complete the async operation
    kotlinx.coroutines.delay(1000)

    // Verify the StateFlow was updated
    val loadedSpaceRenter = spaceRenterViewModel.spaceRenter.value
    assertNotNull(loadedSpaceRenter)
    assertEquals(spaceRenter.id, loadedSpaceRenter!!.id)
    assertEquals("Test Space Renter for ViewModel", loadedSpaceRenter.name)
    assertEquals(testAccount1.uid, loadedSpaceRenter.owner.uid)
    assertEquals("+41 21 555 0100", loadedSpaceRenter.phone)
    assertEquals("vm@test.com", loadedSpaceRenter.email)
    assertEquals("https://vmtest.com", loadedSpaceRenter.website)
    assertEquals(testLocation1, loadedSpaceRenter.address)
    assertEquals(testOpeningHours.size, loadedSpaceRenter.openingHours.size)
    assertEquals(2, loadedSpaceRenter.spaces.size)
  }

  @Test
  fun spaceRenterViewModelLoadsSpaceRenterWithMinimalData() = runBlocking {
    // Create a space renter with minimal data
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount2,
            name = "Minimal VM Space Renter",
            address = testLocation2,
            openingHours = testOpeningHours)

    // Load the space renter through ViewModel
    spaceRenterViewModel.getSpaceRenter(spaceRenter.id)

    // Give it time to complete the async operation
    kotlinx.coroutines.delay(1000)

    // Verify the StateFlow was updated
    val loadedSpaceRenter = spaceRenterViewModel.spaceRenter.value
    assertNotNull(loadedSpaceRenter)
    assertEquals(spaceRenter.id, loadedSpaceRenter!!.id)
    assertEquals("Minimal VM Space Renter", loadedSpaceRenter.name)
    assertEquals(testAccount2.uid, loadedSpaceRenter.owner.uid)
    assertEquals("", loadedSpaceRenter.phone)
    assertEquals("", loadedSpaceRenter.email)
    assertEquals("", loadedSpaceRenter.website)
    assertTrue(loadedSpaceRenter.spaces.isEmpty())
  }

  @Test
  fun spaceRenterViewModelUpdatesStateFlowOnMultipleCalls() = runBlocking {
    // Create two different space renters
    val spaceRenter1 =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "First Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    val spaceRenter2 =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount2,
            name = "Second Space Renter",
            address = testLocation2,
            openingHours = testOpeningHours)

    // Load first space renter
    spaceRenterViewModel.getSpaceRenter(spaceRenter1.id)
    kotlinx.coroutines.delay(1000)

    assertEquals("First Space Renter", spaceRenterViewModel.spaceRenter.value?.name)

    // Load second space renter
    spaceRenterViewModel.getSpaceRenter(spaceRenter2.id)
    kotlinx.coroutines.delay(1000)

    assertEquals("Second Space Renter", spaceRenterViewModel.spaceRenter.value?.name)
  }

  @Test
  fun spaceRenterViewModelLoadsSpaceRenterWithSpaces() = runBlocking {
    // Create a space renter with spaces
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Space Renter with Spaces",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1, testSpace2))

    // Load the space renter through ViewModel
    spaceRenterViewModel.getSpaceRenter(spaceRenter.id)
    kotlinx.coroutines.delay(1000)

    // Verify spaces are loaded correctly
    val loadedSpaceRenter = spaceRenterViewModel.spaceRenter.value
    assertNotNull(loadedSpaceRenter)
    assertEquals(2, loadedSpaceRenter!!.spaces.size)

    assertEquals(10, loadedSpaceRenter.spaces[0].seats)
    assertEquals(25.0, loadedSpaceRenter.spaces[0].costPerHour)
    assertEquals(20, loadedSpaceRenter.spaces[1].seats)
    assertEquals(50.0, loadedSpaceRenter.spaces[1].costPerHour)
  }

  @Test
  fun spaceRenterViewModelLoadsSpaceRenterWithCorrectOwnerData() = runBlocking {
    // Create a space renter
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Owner Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    // Load the space renter through ViewModel
    spaceRenterViewModel.getSpaceRenter(spaceRenter.id)
    kotlinx.coroutines.delay(1000)

    // Verify owner data is loaded correctly
    val loadedSpaceRenter = spaceRenterViewModel.spaceRenter.value
    assertNotNull(loadedSpaceRenter)
    assertEquals(testAccount1.uid, loadedSpaceRenter!!.owner.uid)
    assertEquals("Alice", loadedSpaceRenter.owner.name)
    assertEquals("alice@spacerenter.com", loadedSpaceRenter.owner.email)
  }

  // ========================================================================
  // EditSpaceRenterViewModel Tests
  // ========================================================================

  @Test(expected = PermissionDeniedException::class)
  fun editSpaceRenterViewModelThrowsWhenNonOwnerTriesToUpdate() = runBlocking {
    // Create a space renter owned by testAccount1
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Alice's Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    // Try to update as testAccount2 (non-owner)
    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount2, name = "Hacked Space")
  }

  @Test(expected = PermissionDeniedException::class)
  fun editSpaceRenterViewModelThrowsWhenNonOwnerTriesToDelete() = runBlocking {
    // Create a space renter owned by testAccount1
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Alice's Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    // Try to delete as testAccount2 (non-owner)
    editSpaceRenterViewModel.deleteSpaceRenter(spaceRenter, testAccount2)
  }

  @Test(expected = IllegalArgumentException::class)
  fun editSpaceRenterViewModelThrowsWhenUpdatingToBlankName() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Original Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, name = "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun editSpaceRenterViewModelThrowsWhenUpdatingToWhitespaceName() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Original Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, name = "   ")
  }

  @Test(expected = IllegalArgumentException::class)
  fun editSpaceRenterViewModelThrowsWhenUpdatingWithLessThan7OpeningHours() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    val incompleteHours =
        listOf(
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))))

    editSpaceRenterViewModel.updateSpaceRenter(
        spaceRenter, testAccount1, openingHours = incompleteHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun editSpaceRenterViewModelThrowsWhenUpdatingWithMoreThan7UniqueOpeningHourDays() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
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

    editSpaceRenterViewModel.updateSpaceRenter(
        spaceRenter, testAccount1, openingHours = tooManyHours)
  }

  @Test(expected = IllegalArgumentException::class)
  fun editSpaceRenterViewModelThrowsWhenUpdatingToDefaultAddress() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, address = Location())
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesName() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Old Name",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, name = "New Name")
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("New Name", updated.name)
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesPhone() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            phone = "+41 11 111 1111",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, phone = "+41 99 999 9999")
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("+41 99 999 9999", updated.phone)
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesEmail() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            email = "old@spacerenter.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(
        spaceRenter, testAccount1, email = "new@spacerenter.com")
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("new@spacerenter.com", updated.email)
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesWebsite() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            website = "https://old.com",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(
        spaceRenter, testAccount1, website = "https://new.com")
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("https://new.com", updated.website)
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesAddress() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, address = testLocation2)
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals(testLocation2, updated.address)
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesOpeningHours() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
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

    editSpaceRenterViewModel.updateSpaceRenter(
        spaceRenter, testAccount1, openingHours = newOpeningHours)
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals(7, updated.openingHours.size)
    assertEquals("10:00", updated.openingHours[0].hours[0].open)
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesSpaces() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Test Space Renter",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1))

    val newSpaces = listOf(testSpace2, Space(seats = 15, costPerHour = 30.0))
    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, spaces = newSpaces)
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals(2, updated.spaces.size)
    assertEquals(20, updated.spaces[0].seats)
    assertEquals(50.0, updated.spaces[0].costPerHour)
    assertEquals(15, updated.spaces[1].seats)
    assertEquals(30.0, updated.spaces[1].costPerHour)
  }

  @Test
  fun editSpaceRenterViewModelSuccessfullyUpdatesMultipleFields() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Old Space Renter",
            phone = "+41 11 111 1111",
            email = "old@spacerenter.com",
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

    editSpaceRenterViewModel.updateSpaceRenter(
        spaceRenter,
        testAccount1,
        name = "New Space Renter",
        phone = "+41 22 222 2222",
        email = "new@spacerenter.com",
        website = "https://new.com",
        address = testLocation2,
        openingHours = newOpeningHours)
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    assertEquals("New Space Renter", updated.name)
    assertEquals("+41 22 222 2222", updated.phone)
    assertEquals("new@spacerenter.com", updated.email)
    assertEquals("https://new.com", updated.website)
    assertEquals(testLocation2, updated.address)
    assertEquals(7, updated.openingHours.size)
  }

  @Test
  fun editSpaceRenterViewModelOwnerCanDeleteOwnSpaceRenter() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "To Delete",
            address = testLocation1,
            openingHours = testOpeningHours)

    editSpaceRenterViewModel.deleteSpaceRenter(spaceRenter, testAccount1)
    kotlinx.coroutines.delay(1000)

    // Verify space renter is deleted
    try {
      spaceRenterRepository.getSpaceRenter(spaceRenter.id)
      throw AssertionError("SpaceRenter should have been deleted")
    } catch (_: IllegalArgumentException) {
      // Expected - space renter doesn't exist anymore
    }
  }

  @Test
  fun editSpaceRenterViewModelUpdatesPreserveUnchangedFields() = runBlocking {
    val spaceRenter =
        spaceRenterRepository.createSpaceRenter(
            owner = testAccount1,
            name = "Original Space Renter",
            phone = "+41 11 111 1111",
            email = "original@spacerenter.com",
            website = "https://original.com",
            address = testLocation1,
            openingHours = testOpeningHours,
            spaces = listOf(testSpace1))

    // Update only the phone
    editSpaceRenterViewModel.updateSpaceRenter(spaceRenter, testAccount1, phone = "+41 99 999 9999")
    kotlinx.coroutines.delay(1000)

    val updated = spaceRenterRepository.getSpaceRenter(spaceRenter.id)
    // Phone should be updated
    assertEquals("+41 99 999 9999", updated.phone)
    // Other fields should remain unchanged
    assertEquals("Original Space Renter", updated.name)
    assertEquals("original@spacerenter.com", updated.email)
    assertEquals("https://original.com", updated.website)
    assertEquals(testLocation1, updated.address)
    assertEquals(testOpeningHours.size, updated.openingHours.size)
    assertEquals(1, updated.spaces.size)
  }
}
