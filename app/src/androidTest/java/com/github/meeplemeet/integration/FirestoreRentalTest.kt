package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.rental.RentalRepository
import com.github.meeplemeet.model.rental.RentalStatus
import com.github.meeplemeet.model.rental.RentalType
import com.github.meeplemeet.model.rental.RentalViewModel
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreRentalTest : FirestoreTests() {
  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account
  private lateinit var testStartDate: Timestamp
  private lateinit var testEndDate: Timestamp

  private val rentalRepository = RentalRepository()
  private lateinit var rentalViewModel: RentalViewModel

  @Before
  fun setup() {
    runBlocking {
      account1 =
          accountRepository.createAccount(
              "Antoine", "Antoine", email = "Antoine@example.com", photoUrl = null)
      account2 =
          accountRepository.createAccount(
              "Marco", "Marco", email = "Marco@example.com", photoUrl = null)
      account3 =
          accountRepository.createAccount(
              "Thomas", "Thomas", email = "Thomas@example.com", photoUrl = null)

      val now = System.currentTimeMillis() / 1000
      testStartDate = Timestamp(now + 3600, 0) // +1 hour
      testEndDate = Timestamp(now + 7200, 0) // +2 hours

      // Clean up all existing rentals before each test
      cleanupAllRentals()
    }

    // Initialize ViewModel fresh for each test
    rentalViewModel = RentalViewModel()

    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    runBlocking { cleanupAllRentals() }
    Dispatchers.resetMain()
  }

  private suspend fun cleanupAllRentals() {
    val snapshot = rentalRepository.collection.get().await()
    val batch = rentalRepository.db.batch()
    snapshot.documents.forEach { batch.delete(it.reference) }
    batch.commit().await()
  }

  // ========================================================================
  // RentalRepository Tests
  // ========================================================================

  @Test
  fun canCreateSpaceRental() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceRenter123",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0,
            notes = "Need projector")

    assertNotNull(rental.uid)
    assertEquals(account1.uid, rental.renterId)
    assertEquals(RentalType.SPACE, rental.type)
    assertEquals("spaceRenter123", rental.resourceId)
    assertEquals("0", rental.resourceDetailId)
    assertEquals(testStartDate, rental.startDate)
    assertEquals(testEndDate, rental.endDate)
    assertEquals(RentalStatus.CONFIRMED, rental.status)
    assertEquals(50.0, rental.totalCost)
    assertEquals("Need projector", rental.notes)
    assertNull(rental.associatedSessionId)
  }

  @Test
  fun canGetRentalById() = runBlocking {
    val created =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceRenter123",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    val fetched = rentalRepository.getRental(created.uid)

    assertNotNull(fetched)
    assertEquals(created.uid, fetched?.uid)
    assertEquals(account1.uid, fetched?.renterId)
    assertEquals(RentalType.SPACE, fetched?.type)
  }

  @Test
  fun getRentalReturnsNullForNonExistentId() = runBlocking {
    val fetched = rentalRepository.getRental("nonexistent-rental-id")

    assertNull(fetched)
  }

  @Test
  fun canGetRentalsByUser() = runBlocking {
    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceRenterA",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceRenterB",
        resourceDetailId = "1",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 75.0)

    rentalRepository.createRental(
        renterId = account2.uid,
        type = RentalType.SPACE,
        resourceId = "spaceRenterC",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 100.0)

    val account1Rentals = rentalRepository.getRentalsByUser(account1.uid)

    assertEquals(2, account1Rentals.size)
    assertTrue(account1Rentals.all { it.renterId == account1.uid })
  }

  @Test
  fun getRentalsByUserOrdersByStartDateDescending() = runBlocking {
    val earlier = Timestamp(testStartDate.seconds - 3600, 0)
    val later = Timestamp(testStartDate.seconds + 3600, 0)

    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceX",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceY",
        resourceDetailId = "0",
        startDate = later,
        endDate = Timestamp(later.seconds + 3600, 0),
        totalCost = 60.0)

    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceZ",
        resourceDetailId = "0",
        startDate = earlier,
        endDate = Timestamp(earlier.seconds + 3600, 0),
        totalCost = 40.0)

    val rentals = rentalRepository.getRentalsByUser(account1.uid)

    assertEquals(3, rentals.size)
    assertEquals(later, rentals[0].startDate)
    assertEquals(testStartDate, rentals[1].startDate)
    assertEquals(earlier, rentals[2].startDate)
  }

  @Test
  fun getRentalsByUserExcludesCompletedByDefault() = runBlocking {
    val rental1 =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    val rental2 =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space2",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 60.0)

    rentalRepository.updateRentalStatus(rental2.uid, RentalStatus.COMPLETED)

    val rentals = rentalRepository.getRentalsByUser(account1.uid, includeCompleted = false)

    assertEquals(1, rentals.size)
    assertEquals(rental1.uid, rentals[0].uid)
  }

  @Test
  fun getRentalsByUserIncludesCompletedWhenRequested() = runBlocking {
    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "space1",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    val rental2 =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space2",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 60.0)

    rentalRepository.updateRentalStatus(rental2.uid, RentalStatus.COMPLETED)

    val rentals = rentalRepository.getRentalsByUser(account1.uid, includeCompleted = true)

    assertEquals(2, rentals.size)
  }

  @Test
  fun canGetActiveRentalsByType() = runBlocking {
    val now = Timestamp.now()
    val future = Timestamp(now.seconds + 7200, 0)
    val past = Timestamp(now.seconds - 3600, 0)

    // Active rental (CONFIRMED, end date in future)
    val activeRental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceActive",
            resourceDetailId = "0",
            startDate = now,
            endDate = future,
            totalCost = 50.0)

    // Expired rental (end date in past)
    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceExpired",
        resourceDetailId = "0",
        startDate = past,
        endDate = now,
        totalCost = 60.0)

    // Cancelled rental
    val cancelledRental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceCancelled",
            resourceDetailId = "0",
            startDate = now,
            endDate = future,
            totalCost = 70.0)
    rentalRepository.updateRentalStatus(cancelledRental.uid, RentalStatus.CANCELLED)

    val activeRentals = rentalRepository.getActiveRentalsByType(account1.uid, RentalType.SPACE)

    assertEquals(1, activeRentals.size)
    assertEquals(activeRental.uid, activeRentals[0].uid)
    assertEquals(RentalStatus.CONFIRMED, activeRentals[0].status)
  }

  @Test
  fun canUpdateRentalStatus() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    assertEquals(RentalStatus.CONFIRMED, rental.status)

    rentalRepository.updateRentalStatus(rental.uid, RentalStatus.COMPLETED)

    val updated = rentalRepository.getRental(rental.uid)
    assertEquals(RentalStatus.COMPLETED, updated?.status)
  }

  @Test
  fun canAssociateRentalWithSession() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    assertNull(rental.associatedSessionId)

    val sessionId = "session123"
    rentalRepository.associateWithSession(rental.uid, sessionId)

    val updated = rentalRepository.getRental(rental.uid)
    assertEquals(sessionId, updated?.associatedSessionId)
  }

  @Test
  fun canDissociateRentalFromSession() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    rentalRepository.associateWithSession(rental.uid, "session123")

    var updated = rentalRepository.getRental(rental.uid)
    assertEquals("session123", updated?.associatedSessionId)

    rentalRepository.dissociateFromSession(rental.uid)

    updated = rentalRepository.getRental(rental.uid)
    assertNull(updated?.associatedSessionId)
  }

  @Test
  fun canCancelRental() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    rentalRepository.cancelRental(rental.uid)

    val updated = rentalRepository.getRental(rental.uid)
    assertEquals(RentalStatus.CANCELLED, updated?.status)
  }

  @Test
  fun canCompleteRental() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    rentalRepository.completeRental(rental.uid)

    val updated = rentalRepository.getRental(rental.uid)
    assertEquals(RentalStatus.COMPLETED, updated?.status)
  }

  @Test
  fun canDeleteRental() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "space1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    assertNotNull(rentalRepository.getRental(rental.uid))

    rentalRepository.deleteRental(rental.uid)

    assertNull(rentalRepository.getRental(rental.uid))
  }

  @Test
  fun isResourceAvailable_returnsTrue_whenNoConflicts() = runBlocking {
    val available =
        rentalRepository.isResourceAvailable(
            resourceId = "uniqueSpace1",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate)

    assertTrue(available)
  }

  @Test
  fun isResourceAvailable_returnsFalse_whenOverlappingRental() = runBlocking {
    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceOverlap",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    val available =
        rentalRepository.isResourceAvailable(
            resourceId = "spaceOverlap",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate)

    assertFalse(available)
  }

  @Test
  fun isResourceAvailable_returnsFalse_whenPartialOverlap() = runBlocking {
    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spacePartial",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    // Overlapping period (starts during existing rental)
    val overlapStart = Timestamp(testStartDate.seconds + 1800, 0)
    val overlapEnd = Timestamp(testEndDate.seconds + 1800, 0)

    val available =
        rentalRepository.isResourceAvailable(
            resourceId = "spacePartial",
            resourceDetailId = "0",
            startDate = overlapStart,
            endDate = overlapEnd)

    assertFalse(available)
  }

  @Test
  fun isResourceAvailable_returnsTrue_whenNonOverlappingRental() = runBlocking {
    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceNonOverlap",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    // Later period (no overlap)
    val laterStart = Timestamp(testEndDate.seconds + 1800, 0)
    val laterEnd = Timestamp(testEndDate.seconds + 5400, 0)

    val available =
        rentalRepository.isResourceAvailable(
            resourceId = "spaceNonOverlap",
            resourceDetailId = "0",
            startDate = laterStart,
            endDate = laterEnd)

    assertTrue(available)
  }

  @Test
  fun isResourceAvailable_ignoresCancelledRentals() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceCancelledCheck",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    rentalRepository.updateRentalStatus(rental.uid, RentalStatus.CANCELLED)

    val available =
        rentalRepository.isResourceAvailable(
            resourceId = "spaceCancelledCheck",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate)

    assertTrue(available)
  }

  @Test
  fun isResourceAvailable_returnsTrueForDifferentResources() = runBlocking {
    rentalRepository.createRental(
        renterId = account1.uid,
        type = RentalType.SPACE,
        resourceId = "spaceDifferent",
        resourceDetailId = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    // Different resourceDetailId
    val available1 =
        rentalRepository.isResourceAvailable(
            resourceId = "spaceDifferent",
            resourceDetailId = "1",
            startDate = testStartDate,
            endDate = testEndDate)

    assertTrue(available1)

    // Different resourceId
    val available2 =
        rentalRepository.isResourceAvailable(
            resourceId = "spaceOther",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate)

    assertTrue(available2)
  }

  // ========================================================================
  // RentalViewModel Tests
  // ========================================================================

  @Test
  fun viewModel_canCreateSpaceRental() = runTest {
    rentalViewModel.createSpaceRental(
        renterId = account1.uid,
        spaceRenterId = "spaceRenterVM1",
        spaceIndex = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0,
        notes = "Need projector")

    advanceUntilIdle()
    Thread.sleep(500) // Wait for Firestore write

    val rentals = rentalRepository.getRentalsByUser(account1.uid)

    assertTrue(rentals.isNotEmpty())
    val rental = rentals.first { it.resourceId == "spaceRenterVM1" }

    assertNotNull(rental)
    assertEquals(account1.uid, rental.renterId)
    assertEquals(RentalType.SPACE, rental.type)
    assertEquals("spaceRenterVM1", rental.resourceId)
    assertEquals("0", rental.resourceDetailId)
  }

  @Test(expected = IllegalStateException::class)
  fun viewModel_createSpaceRental_throwsWhenNotAvailable() = runTest {
    // Create first rental
    rentalViewModel.createSpaceRental(
        renterId = account1.uid,
        spaceRenterId = "spaceRenterVM2",
        spaceIndex = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    advanceUntilIdle()
    Thread.sleep(500)

    // Try to create overlapping rental - should throw
    rentalViewModel.createSpaceRental(
        renterId = account2.uid,
        spaceRenterId = "spaceRenterVM2",
        spaceIndex = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    advanceUntilIdle()
  }

  @Test
  fun viewModel_loadUserRentals_populatesStateFlow() = runTest {
    rentalViewModel.createSpaceRental(
        renterId = account1.uid,
        spaceRenterId = "spaceRenterVM3",
        spaceIndex = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    advanceUntilIdle()
    Thread.sleep(500)

    rentalViewModel.loadUserRentals(account1.uid)
    advanceUntilIdle()

    val rentals = rentalViewModel.userRentals.value
    assertNotNull(rentals)
  }

  @Test
  fun viewModel_loadActiveSpaceRentals_populatesStateFlow() = runTest {
    val now = Timestamp.now()
    val future = Timestamp(now.seconds + 7200, 0)

    rentalViewModel.createSpaceRental(
        renterId = account1.uid,
        spaceRenterId = "spaceRenterVM4",
        spaceIndex = "0",
        startDate = now,
        endDate = future,
        totalCost = 50.0)

    advanceUntilIdle()
    Thread.sleep(500)

    rentalViewModel.loadActiveSpaceRentals(account1.uid)
    advanceUntilIdle()

    val rentals = rentalViewModel.activeSpaceRentals.value
    assertNotNull(rentals)
  }

  @Test
  fun viewModel_cancelRental_updatesStatus() = runTest {
    rentalViewModel.createSpaceRental(
        renterId = account1.uid,
        spaceRenterId = "spaceRenterVM5",
        spaceIndex = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    advanceUntilIdle()
    Thread.sleep(500)

    // Get the created rental
    val rentals = rentalRepository.getRentalsByUser(account1.uid)
    assertTrue(rentals.isNotEmpty())
    val rental = rentals.first()

    rentalViewModel.cancelRental(rental.uid)
    advanceUntilIdle()
    Thread.sleep(500)

    val updated = rentalRepository.getRental(rental.uid)
    assertEquals(RentalStatus.CANCELLED, updated?.status)
  }

  @Test
  fun viewModel_associateRentalWithSession_updatesRental() = runTest {
    rentalViewModel.createSpaceRental(
        renterId = account1.uid,
        spaceRenterId = "spaceRenterVM6",
        spaceIndex = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    advanceUntilIdle()
    Thread.sleep(500)

    // Get the created rental
    val rentals = rentalRepository.getRentalsByUser(account1.uid)
    assertTrue(rentals.isNotEmpty())
    val rental = rentals.first()

    val sessionId = "session123"
    rentalViewModel.associateRentalWithSession(rental.uid, sessionId)
    advanceUntilIdle()
    Thread.sleep(500)

    val updated = rentalRepository.getRental(rental.uid)
    assertEquals(sessionId, updated?.associatedSessionId)
  }

  @Test
  fun viewModel_dissociateRentalFromSession_removesAssociation() = runTest {
    rentalViewModel.createSpaceRental(
        renterId = account1.uid,
        spaceRenterId = "spaceRenterVM7",
        spaceIndex = "0",
        startDate = testStartDate,
        endDate = testEndDate,
        totalCost = 50.0)

    advanceUntilIdle()
    Thread.sleep(500)

    // Get the created rental
    val rentals = rentalRepository.getRentalsByUser(account1.uid)
    assertTrue(rentals.isNotEmpty())
    val rental = rentals.first()

    rentalViewModel.associateRentalWithSession(rental.uid, "session123")
    advanceUntilIdle()
    Thread.sleep(500)

    var updated = rentalRepository.getRental(rental.uid)
    assertEquals("session123", updated?.associatedSessionId)

    rentalViewModel.dissociateRentalFromSession(rental.uid)
    advanceUntilIdle()
    Thread.sleep(500)

    updated = rentalRepository.getRental(rental.uid)
    assertNull(updated?.associatedSessionId)
  }

  @Test
  fun viewModel_isLoading_togglesCorrectly() = runTest {
    assertEquals(false, rentalViewModel.isLoading.value)

    rentalViewModel.loadUserRentals(account1.uid)
    assertEquals(true, rentalViewModel.isLoading.value)
  }

  @Test
  fun multipleRentalsForSameResource_differentTimes() = runBlocking {
    // First rental
    val rental1 =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceMulti",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    // Second rental (different time, same resource)
    val laterStart = Timestamp(testEndDate.seconds + 3600, 0)
    val laterEnd = Timestamp(testEndDate.seconds + 7200, 0)

    val rental2 =
        rentalRepository.createRental(
            renterId = account2.uid,
            type = RentalType.SPACE,
            resourceId = "spaceMulti",
            resourceDetailId = "0",
            startDate = laterStart,
            endDate = laterEnd,
            totalCost = 60.0)

    assertNotNull(rental1)
    assertNotNull(rental2)

    val fetched1 = rentalRepository.getRental(rental1.uid)
    val fetched2 = rentalRepository.getRental(rental2.uid)

    assertNotNull(fetched1)
    assertNotNull(fetched2)
  }

  @Test
  fun rentalCreation_setsCreatedAtTimestamp() = runBlocking {
    val beforeCreation = Timestamp.now()
    Thread.sleep(100)

    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceTimestamp",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    Thread.sleep(100)
    val afterCreation = Timestamp.now()

    assertTrue(rental.createdAt.seconds >= beforeCreation.seconds)
    assertTrue(rental.createdAt.seconds <= afterCreation.seconds)
  }

  @Test
  fun rentalCreation_defaultsToConfirmedStatus() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceConfirmed",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0)

    assertEquals(RentalStatus.CONFIRMED, rental.status)
  }

  @Test
  fun rentalCreation_handlesEmptyNotes() = runBlocking {
    val rental =
        rentalRepository.createRental(
            renterId = account1.uid,
            type = RentalType.SPACE,
            resourceId = "spaceEmptyNotes",
            resourceDetailId = "0",
            startDate = testStartDate,
            endDate = testEndDate,
            totalCost = 50.0,
            notes = "")

    assertEquals("", rental.notes)
  }
}
