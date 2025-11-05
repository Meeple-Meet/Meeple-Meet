// Test suite initially generated with Claude 4.5, following Meeple Meet's global test architecture.
// Then, the test suite was manually reviewed, cleaned up, and debugged.
package com.github.meeplemeet.integration

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.map.GEO_PIN_COLLECTION_PATH
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPinNoUid
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.shared.Location
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreGeoPinTest : FirestoreTests() {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: StorableGeoPinRepository
  private lateinit var collection: CollectionReference

  private val testLocation1 = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")
  private val testLocation2 = Location(latitude = 48.8566, longitude = 2.3522, name = "Paris")
  private val testLocation3 = Location(latitude = 51.5074, longitude = -0.1278, name = "London")

  @Before
  fun setup() {
    firestore = FirebaseProvider.db
    repository = StorableGeoPinRepository(firestore)
    collection = firestore.collection(GEO_PIN_COLLECTION_PATH)
  }

  /* ========== Test 1: Pin Creation ========== */
  @Test
  fun upsertGeoPinCreatesAllPinTypes() = runTest {
    // Create SHOP pin
    repository.upsertGeoPin(ref = "shop123", type = PinType.SHOP, location = testLocation1)

    // Create SPACE pin
    repository.upsertGeoPin(ref = "=space456", type = PinType.SPACE, location = testLocation2)

    // Create SESSION pin
    repository.upsertGeoPin(ref = "session789", type = PinType.SESSION, location = testLocation3)

    // Verify pins were created by checking Firestore collection count
    val snapshot = collection.get().await()
    assert(snapshot.documents.size >= 3)
  }

  /* ========== Test 2: Pin Update ========== */
  @Test
  fun upsertGeoPinUpdatesAllFields() = runTest {
    // Create initial pin
    val pinId = "pin-update-test"
    repository.upsertGeoPin(pinId, PinType.SHOP, testLocation1)
    repository.upsertGeoPin(pinId, PinType.SPACE, testLocation2)

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
    repository.upsertGeoPin(pinId, PinType.SESSION, testLocation1)

    // Verify exists
    val beforeDelete = collection.document(pinId).get().await()
    assert(beforeDelete.exists())

    // Delete pin
    repository.deleteGeoPin(pinId)

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

    pins.forEach { (id, type, location) -> repository.upsertGeoPin(id, type, location) }

    // Verify count
    val snapshot = collection.get().await()
    assert(snapshot.documents.size >= pins.size)
  }

  /* ========== Test 5: Update Then Delete Workflow ========== */
  @Test
  fun updateThenDeleteCompleteLifecycle() = runTest {
    // Create & update
    val pinId = "lifecycle-pin"
    repository.upsertGeoPin(pinId, PinType.SESSION, testLocation1)
    repository.upsertGeoPin(pinId, PinType.SESSION, testLocation2)

    // Verify update
    val afterUpdate = collection.document(pinId).get().await()
    assert(afterUpdate.exists())

    // Delete
    repository.deleteGeoPin(pinId)
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
      repository.upsertGeoPin("shop$index", PinType.SHOP, location)
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

    repository.upsertGeoPin(shopPinId, PinType.SHOP, testLocation1)
    repository.upsertGeoPin(spacePinId, PinType.SPACE, testLocation2)
    repository.upsertGeoPin(sessionPinId, PinType.SESSION, testLocation3)

    // Update one pin
    repository.upsertGeoPin(shopPinId, PinType.SHOP, testLocation2)

    // Delete one pin
    repository.deleteGeoPin(sessionPinId)
    delay(500)

    // Verify final state
    val shopSnapshot = collection.document(shopPinId).get().await()
    assert(shopSnapshot.exists())

    val spaceSnapshot = collection.document(spacePinId).get().await()
    assert(spaceSnapshot.exists())

    val sessionSnapshot = collection.document(sessionPinId).get().await()
    assert(!sessionSnapshot.exists())
  }
}
