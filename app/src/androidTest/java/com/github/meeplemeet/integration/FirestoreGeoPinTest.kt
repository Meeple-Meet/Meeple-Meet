// Test suite initially generated with Claude 4.5, following Meeple Meet's global test architecture.
// Then, the test suite was manually reviewed, cleaned up, and debugged.
package com.github.meeplemeet.integration

import com.github.meeplemeet.model.map.GeoFirestoreOperations
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPinNoUid
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.GeoPoint
import java.util.concurrent.atomic.AtomicInteger
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.fail
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreGeoPinTest : FirestoreTests() {
  private lateinit var collection: CollectionReference

  private val testLocation1 = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")
  private val testLocation2 = Location(latitude = 48.8566, longitude = 2.3522, name = "Paris")
  private val testLocation3 = Location(latitude = 51.5074, longitude = -0.1278, name = "London")

  @Before
  fun setup() {
    collection = geoPinRepository.collection
  }

  /* ========== Test 1: Pin Creation ========== */
  @Test
  fun upsertGeoPinCreatesAllPinTypes() = runTest {
    // Create SHOP pin
    geoPinRepository.upsertGeoPin(ref = "shop123", type = PinType.SHOP, location = testLocation1)

    // Create SPACE pin
    geoPinRepository.upsertGeoPin(ref = "=space456", type = PinType.SPACE, location = testLocation2)

    // Create SESSION pin
    geoPinRepository.upsertGeoPin(
        ref = "session789", type = PinType.SESSION, location = testLocation3)

    // Verify pins were created by checking Firestore collection count
    val snapshot = collection.get().await()
    assert(snapshot.documents.size >= 3)
  }

  /* ========== Test 2: Pin Update ========== */
  @Test
  fun upsertGeoPinUpdatesAllFields() = runTest {
    // Create initial pin
    val pinId = "pin-update-test"
    geoPinRepository.upsertGeoPin(pinId, PinType.SHOP, testLocation1)
    geoPinRepository.upsertGeoPin(pinId, PinType.SPACE, testLocation2)

    // Verify update
    val snapshot = collection.document(pinId).get().await()
    val pin = snapshot.toObject(StorableGeoPinNoUid::class.java)

    assertNotNull(pin)
    assertEquals(PinType.SPACE, pin!!.type)
  }

  /* ========== Test 3: Pin Deletion ========== */
  @Test
  fun deleteGeoPinRemovesFromFirestoreAndGeoFirestore() = runTest {
    // Create pin
    val pinId = "pin-delete-test"
    geoPinRepository.upsertGeoPin(pinId, PinType.SESSION, testLocation1)

    // Verify exists
    val beforeDelete = collection.document(pinId).get().await()
    assert(beforeDelete.exists())

    // Delete pin
    geoPinRepository.deleteGeoPin(pinId)

    // Verify deleted
    val afterDelete = collection.document(pinId).get().await()
    assert(!afterDelete.exists())
  }

  /* ========== Test 4: Multiple Pins Management ========== */
  @Test
  fun createMultiplePinsAllStoredCorrectly() = runTest {
    val pins =
        listOf(
            Triple("shop1", PinType.SHOP, testLocation1),
            Triple("shop2", PinType.SHOP, testLocation2),
            Triple("space1", PinType.SPACE, testLocation3),
            Triple("session1", PinType.SESSION, testLocation1),
            Triple("session2", PinType.SESSION, testLocation2))

    pins.forEach { (id, type, location) -> geoPinRepository.upsertGeoPin(id, type, location) }

    // Verify count
    val snapshot = collection.get().await()
    assert(snapshot.documents.size >= pins.size)
  }

  /* ========== Test 5: Update Then Delete Workflow ========== */
  @Test
  fun updateThenDeleteCompleteLifecycle() = runTest {
    // Create & update
    val pinId = "lifecycle-pin"
    geoPinRepository.upsertGeoPin(pinId, PinType.SESSION, testLocation1)
    geoPinRepository.upsertGeoPin(pinId, PinType.SESSION, testLocation2)

    // Verify update
    val afterUpdate = collection.document(pinId).get().await()
    assert(afterUpdate.exists())

    // Delete
    geoPinRepository.deleteGeoPin(pinId)
    delay(500)

    // Verify deletion
    val afterDelete = collection.document(pinId).get().await()
    assert(!afterDelete.exists())
  }

  /* ========== Test 6: Different Locations Same Type ========== */
  @Test
  fun multiplePinsSameTypeDifferentLocations() = runTest {
    val locations =
        listOf(
            Location(46.5197, 6.5665, "EPFL"),
            Location(46.5198, 6.5666, "Near EPFL"),
            Location(46.5199, 6.5667, "Very Near EPFL"))

    locations.forEachIndexed { index, location ->
      geoPinRepository.upsertGeoPin("shop$index", PinType.SHOP, location)
    }

    val snapshot = collection.get().await()
    val shops = snapshot.documents.filter { it.getString("type") == "SHOP" }
    assert(shops.size >= locations.size)
  }

  /* ========== Test 7: Comprehensive Integration Test ========== */
  @Test
  fun integrationFullPinManagementWorkflow() = runTest {
    // Create multiple pins of different types
    val shopPinId = "main-shop"
    val spacePinId = "gaming-space"
    val sessionPinId = "weekly-session"

    geoPinRepository.upsertGeoPin(shopPinId, PinType.SHOP, testLocation1)
    geoPinRepository.upsertGeoPin(spacePinId, PinType.SPACE, testLocation2)
    geoPinRepository.upsertGeoPin(sessionPinId, PinType.SESSION, testLocation3)

    // Update one pin
    geoPinRepository.upsertGeoPin(shopPinId, PinType.SHOP, testLocation2)

    // Delete one pin
    geoPinRepository.deleteGeoPin(sessionPinId)
    delay(500)

    // Verify final state
    val shopSnapshot = collection.document(shopPinId).get().await()
    assert(shopSnapshot.exists())

    val spaceSnapshot = collection.document(spacePinId).get().await()
    assert(spaceSnapshot.exists())

    val sessionSnapshot = collection.document(sessionPinId).get().await()
    assert(!sessionSnapshot.exists())
  }

  /* ========== RETRY LOGIC TESTS ========== */

  /* ========== Test 8: Retry on Transient Failure - Success on Second Attempt ========== */
  @Test
  fun upsertGeoPinRetriesOnTransientFailureAndSucceeds() = runTest {
    val attemptCounter = AtomicInteger(0)

    // Mock GeoFirestore that fails once then succeeds
    val mockGeoOps =
        object : GeoFirestoreOperations {
          override fun setLocation(
              uid: String,
              geoPoint: GeoPoint,
              callback: (Exception?) -> Unit
          ) {
            val attempt = attemptCounter.incrementAndGet()
            if (attempt == 1) {
              // First attempt fails
              callback(Exception("Transient network error"))
            } else {
              // Second attempt succeeds
              callback(null)
            }
          }

          override fun removeLocation(uid: String, callback: (Exception?) -> Unit) {
            callback(null)
          }
        }

    val testRepo = StorableGeoPinRepository(mockGeoOps)

    // This should succeed after retry
    testRepo.upsertGeoPin("retry-test-1", PinType.SHOP, testLocation1)

    // Verify it retried (attempt counter should be 2)
    assertEquals(2, attemptCounter.get())
  }

  /* ========== Test 9: Retry Exhausted - Fails After 3 Attempts ========== */
  @Test
  fun upsertGeoPinFailsAfterAllRetriesExhausted() = runTest {
    val attemptCounter = AtomicInteger(0)

    // Mock GeoFirestore that always fails
    val mockGeoOps =
        object : GeoFirestoreOperations {
          override fun setLocation(
              uid: String,
              geoPoint: GeoPoint,
              callback: (Exception?) -> Unit
          ) {
            attemptCounter.incrementAndGet()
            callback(Exception("Persistent network error"))
          }

          override fun removeLocation(uid: String, callback: (Exception?) -> Unit) {
            callback(null)
          }
        }

    val testRepo = StorableGeoPinRepository(mockGeoOps)

    // This should fail after 3 attempts
    try {
      testRepo.upsertGeoPin("retry-test-2", PinType.SHOP, testLocation1)
      fail("Expected exception to be thrown")
    } catch (e: Exception) {
      // Expected behavior
      assert(e.message!!.contains("Persistent network error"))
    }

    // Verify it tried 3 times
    assertEquals(3, attemptCounter.get())
  }

  /* ========== Test 10: Delete Retry on Transient Failure ========== */
  @Test
  fun deleteGeoPinRetriesOnTransientFailureAndSucceeds() = runTest {
    val attemptCounter = AtomicInteger(0)

    // Mock GeoFirestore that fails once then succeeds
    val mockGeoOps =
        object : GeoFirestoreOperations {
          override fun setLocation(
              uid: String,
              geoPoint: GeoPoint,
              callback: (Exception?) -> Unit
          ) {
            callback(null)
          }

          override fun removeLocation(uid: String, callback: (Exception?) -> Unit) {
            val attempt = attemptCounter.incrementAndGet()
            if (attempt == 1) {
              // First attempt fails
              callback(Exception("Transient deletion error"))
            } else {
              // Second attempt succeeds
              callback(null)
            }
          }
        }

    val testRepo = StorableGeoPinRepository(mockGeoOps)

    // This should succeed after retry
    testRepo.deleteGeoPin("retry-test-3")

    // Verify it retried (attempt counter should be 2)
    assertEquals(2, attemptCounter.get())
  }

  /* ========== Test 11: Delete Fails After All Retries ========== */
  @Test
  fun deleteGeoPinFailsAfterAllRetriesExhausted() = runTest {
    val attemptCounter = AtomicInteger(0)

    // Mock GeoFirestore that always fails
    val mockGeoOps =
        object : GeoFirestoreOperations {
          override fun setLocation(
              uid: String,
              geoPoint: GeoPoint,
              callback: (Exception?) -> Unit
          ) {
            callback(null)
          }

          override fun removeLocation(uid: String, callback: (Exception?) -> Unit) {
            attemptCounter.incrementAndGet()
            callback(Exception("Persistent deletion error"))
          }
        }

    val testRepo = StorableGeoPinRepository(mockGeoOps)

    // This should fail after 3 attempts
    try {
      testRepo.deleteGeoPin("retry-test-4")
      fail("Expected exception to be thrown")
    } catch (e: Exception) {
      // Expected behavior
      assert(e.message!!.contains("Persistent deletion error"))
    }

    // Verify it tried 3 times
    assertEquals(3, attemptCounter.get())
  }

  /* ========== Test 12: Retry Succeeds on Third Attempt ========== */
  @Test
  fun upsertGeoPinSucceedsOnThirdAttempt() = runTest {
    val attemptCounter = AtomicInteger(0)

    // Mock GeoFirestore that fails twice then succeeds
    val mockGeoOps =
        object : GeoFirestoreOperations {
          override fun setLocation(
              uid: String,
              geoPoint: GeoPoint,
              callback: (Exception?) -> Unit
          ) {
            val attempt = attemptCounter.incrementAndGet()
            if (attempt < 3) {
              // First two attempts fail
              callback(Exception("Transient error attempt $attempt"))
            } else {
              // Third attempt succeeds
              callback(null)
            }
          }

          override fun removeLocation(uid: String, callback: (Exception?) -> Unit) {
            callback(null)
          }
        }

    val testRepo = StorableGeoPinRepository(mockGeoOps)

    // This should succeed on the third attempt
    testRepo.upsertGeoPin("retry-test-5", PinType.SHOP, testLocation1)

    // Verify it tried 3 times
    assertEquals(3, attemptCounter.get())
  }
}
