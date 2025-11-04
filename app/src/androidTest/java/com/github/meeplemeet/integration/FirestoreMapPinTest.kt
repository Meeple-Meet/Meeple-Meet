// Test suite initially generated with Claude 4.5, following Meeple Meet's global test architecture.
// Then, the test suite was manually reviewed, cleaned up, and debugged.
package com.github.meeplemeet.integration

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.map.MAP_PIN_COLLECTION_PATH
import com.github.meeplemeet.model.map.MapPinRepository
import com.github.meeplemeet.model.map.PinType
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

class FirestoreMapPinTest : FirestoreTests() {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: MapPinRepository
  private lateinit var collection: CollectionReference

  private val testLocation1 = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")
  private val testLocation2 = Location(latitude = 48.8566, longitude = 2.3522, name = "Paris")
  private val testLocation3 = Location(latitude = 51.5074, longitude = -0.1278, name = "London")

  @Before
  fun setup() {
    firestore = FirebaseProvider.db
    repository = MapPinRepository(firestore)
    collection = firestore.collection(MAP_PIN_COLLECTION_PATH)
  }

  /* ========== Test 1: Pin Creation ========== */
  @Test
  fun createMapPinCreatesAllPinTypes() = runTest {
    // Create SHOP pin
    repository.createMapPin(
        type = PinType.SHOP,
        location = testLocation1,
        label = "Board Game Shop",
        ref = "shops/shop123")

    // Create SPACE pin
    repository.createMapPin(
        type = PinType.SPACE,
        location = testLocation2,
        label = "Gaming Space Paris",
        ref = "spaces/space456")

    // Create SESSION pin
    repository.createMapPin(
        type = PinType.SESSION,
        location = testLocation3,
        label = "Friday Night Session",
        ref = "sessions/session789")

    // Verify pins were created by checking Firestore collection count
    val snapshot = collection.get().await()
    assert(snapshot.documents.size >= 3)
  }

  /* ========== Test 2: Pin Update ========== */
  @Test
  fun updateMapPinUpdatesAllFields() = runTest {
    // Create initial pin
    val pinId = collection.document().id
    repository.updateMapPin(
        uid = pinId,
        type = PinType.SHOP,
        location = testLocation1,
        label = "Original Shop",
        ref = "shops/original")

    // Update all fields
    repository.updateMapPin(
        uid = pinId,
        type = PinType.SPACE,
        location = testLocation2,
        label = "Updated Space",
        ref = "spaces/updated")

    // Verify update
    val snapshot = collection.document(pinId).get().await()
    val pin = snapshot.toObject(com.github.meeplemeet.model.map.MapPinNoUid::class.java)

    assertNotNull(pin)
    assertEquals(PinType.SPACE, pin!!.type)
    assertEquals(testLocation2, pin.location)
    assertEquals("Updated Space", pin.label)
    assertEquals("spaces/updated", pin.ref)
  }

  /* ========== Test 3: Pin Deletion ========== */
  @Test
  fun deleteMapPinRemovesFromFirestoreAndGeoFirestore() = runTest {
    // Create pin
    val created =
        repository.createMapPin(
            type = PinType.SESSION,
            location = testLocation1,
            label = "Temporary Session",
            ref = "sessions/temp")

    // Verify exists
    val beforeDelete = collection.document(created.uid).get().await()
    assert(beforeDelete.exists())

    // Delete pin
    repository.deleteMapPin(created.uid)

    // Verify deleted
    val afterDelete = collection.document(created.uid).get().await()
    assert(!afterDelete.exists())
  }

  /* ========== Test 4: Multiple Pins Management ========== */
  @Test
  fun createMultiplePinsAllStoredCorrectly() = runTest {
    val pins =
        listOf(
            Triple(PinType.SHOP, testLocation1, "Shop 1"),
            Triple(PinType.SHOP, testLocation2, "Shop 2"),
            Triple(PinType.SPACE, testLocation3, "Space 1"),
            Triple(PinType.SESSION, testLocation1, "Session 1"),
            Triple(PinType.SESSION, testLocation2, "Session 2"))

    pins.forEachIndexed { index, (type, location, label) ->
      repository.createMapPin(type = type, location = location, label = label, ref = "ref$index")
    }

    // Verify count
    val snapshot = collection.get().await()
    assert(snapshot.documents.size >= pins.size)
  }

  /* ========== Test 5: Location Precision ========== */
  @Test
  fun pinLocationPreservesPrecision() = runTest {
    val preciseLocation =
        Location(
            latitude = 46.51970123456789, longitude = 6.56650987654321, name = "Precise Location")

    val pinId = collection.document().id
    repository.updateMapPin(
        uid = pinId,
        type = PinType.SHOP,
        location = preciseLocation,
        label = "Precise Shop",
        ref = "shops/precise")

    val snapshot = collection.document(pinId).get().await()
    val pin = snapshot.toObject(com.github.meeplemeet.model.map.MapPinNoUid::class.java)

    assertNotNull(pin)
    assertEquals(preciseLocation.latitude, pin!!.location.latitude, 0.0000001)
    assertEquals(preciseLocation.longitude, pin.location.longitude, 0.0000001)
  }

  /* ========== Test 6: Edge Cases - Empty Strings ========== */
  @Test
  fun createPinWithEmptyLabel_succeeds() = runTest {
    repository.createMapPin(
        type = PinType.SHOP, location = testLocation1, label = "", ref = "shops/empty")

    // Should not throw - empty labels are allowed
    val snapshot = collection.get().await()
    assert(snapshot.documents.isNotEmpty())
  }

  /* ========== Test 7: Edge Cases - Special Characters ========== */
  @Test
  fun createPinWithSpecialCharacters_succeeds() = runTest {
    val specialLabel = "Café & Board Games™ (Paris) - #1 Shop!"
    val specialRef = "shops/café-&-games"

    repository.createMapPin(
        type = PinType.SHOP, location = testLocation2, label = specialLabel, ref = specialRef)

    val snapshot = collection.get().await()
    val pin = snapshot.documents.firstOrNull { it.getString("label") == specialLabel }

    assertNotNull(pin)
    assertEquals(specialLabel, pin!!.getString("label"))
    assertEquals(specialRef, pin.getString("ref"))
  }

  /* ========== Test 8: Update Then Delete Workflow ========== */
  @Test
  fun updateThenDeleteCompleteLifecycle() = runTest {
    // Create
    val pinId = collection.document().id
    repository.updateMapPin(
        uid = pinId,
        type = PinType.SESSION,
        location = testLocation1,
        label = "Initial Session",
        ref = "sessions/initial")

    // Update
    repository.updateMapPin(
        uid = pinId,
        type = PinType.SESSION,
        location = testLocation2,
        label = "Updated Session",
        ref = "sessions/updated")

    // Verify update
    val afterUpdate = collection.document(pinId).get().await()
    assert(afterUpdate.exists())
    assertEquals("Updated Session", afterUpdate.getString("label"))

    // Delete
    repository.deleteMapPin(pinId)
    delay(500)

    // Verify deletion
    val afterDelete = collection.document(pinId).get().await()
    assert(!afterDelete.exists())
  }

  /* ========== Test 9: Different Locations Same Type ========== */
  @Test
  fun multiplePinsSameTypeDifferentLocations() = runTest {
    val locations =
        listOf(
            Location(46.5197, 6.5665, "EPFL"),
            Location(46.5198, 6.5666, "Near EPFL"),
            Location(46.5199, 6.5667, "Very Near EPFL"))

    locations.forEachIndexed { index, location ->
      repository.createMapPin(
          type = PinType.SHOP,
          location = location,
          label = "Shop at ${location.name}",
          ref = "shops/shop$index")
    }

    val snapshot = collection.get().await()
    val shops = snapshot.documents.filter { it.getString("type") == "SHOP" }
    assert(shops.size >= locations.size)
  }

  /* ========== Test 10: Comprehensive Integration Test ========== */
  @Test
  fun integrationFullPinManagementWorkflow() = runTest {
    // Create multiple pins of different types
    val shopPinId = collection.document().id
    repository.updateMapPin(
        uid = shopPinId,
        type = PinType.SHOP,
        location = testLocation1,
        label = "Main Shop",
        ref = "shops/main")

    val spacePinId = collection.document().id
    repository.updateMapPin(
        uid = spacePinId,
        type = PinType.SPACE,
        location = testLocation2,
        label = "Gaming Space",
        ref = "spaces/gaming")

    val sessionPinId = collection.document().id
    repository.updateMapPin(
        uid = sessionPinId,
        type = PinType.SESSION,
        location = testLocation3,
        label = "Weekly Session",
        ref = "sessions/weekly")

    // Update one pin
    repository.updateMapPin(
        uid = shopPinId,
        type = PinType.SHOP,
        location = testLocation2, // Moved to Paris
        label = "Main Shop - Paris",
        ref = "shops/main-paris")

    // Delete one pin
    repository.deleteMapPin(sessionPinId)
    delay(500)

    // Verify final state
    val shopSnapshot = collection.document(shopPinId).get().await()
    assert(shopSnapshot.exists())
    assertEquals("Main Shop - Paris", shopSnapshot.getString("label"))

    val spaceSnapshot = collection.document(spacePinId).get().await()
    assert(spaceSnapshot.exists())

    val sessionSnapshot = collection.document(sessionPinId).get().await()
    assert(!sessionSnapshot.exists())
  }
}
