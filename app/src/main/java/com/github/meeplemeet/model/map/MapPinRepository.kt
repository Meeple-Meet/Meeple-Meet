package com.github.meeplemeet.model.map

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.shared.Location
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.tasks.await
import org.imperiumlabs.geofirestore.GeoFirestore
import org.imperiumlabs.geofirestore.extension.removeLocation
import org.imperiumlabs.geofirestore.extension.setLocation

/** Firestore collection path for storing and retrieving map pins documents. */
const val MAP_PIN_COLLECTION_PATH = "map_pins"

/**
 * Repository responsible for managing map pins in Firestore. Each pin includes geolocation data
 * stored via the GeoFirestore library.
 *
 * @property db Firestore instance used to access the map pin collection.
 */
class MapPinRepository(private val db: FirebaseFirestore = FirebaseProvider.db) {
  /** Reference to the Firestore collection where map pins are stored. */
  private val mapPinCollection = db.collection(MAP_PIN_COLLECTION_PATH)

  /** Generates a new unique document ID for a map pin. */
  private fun newUUID() = mapPinCollection.document().id

  /**
   * Creates a new map pin document in Firestore and links it to a domain object (e.g. shop,
   * session). The pin is stored with metadata and geolocation, allowing it to be displayed on the
   * map and referenced later.
   *
   * @param type Type of the pin (e.g. shop, session). See [PinType]
   * @param location Geographical location of the pin.
   * @param label Display label for the pin.
   * @param ref ID of the external object this pin is linked to (e.g. the shop or session it
   *   represents).
   */
  suspend fun createMapPin(type: PinType, location: Location, label: String, ref: String) {
    val pin = MapPin(uid = newUUID(), type = type, location = location, label = label, ref = ref)

    mapPinCollection.document(pin.uid).set(toNoUid(pin)).await()
    setGeoLocation(pin.uid, pin.location)
  }

  /**
   * Updates an existing map pin document and its geolocation data.
   *
   * @param uid Unique ID of the pin to update.
   * @param type Updated pin type.
   * @param location Updated location.
   * @param label Updated label.
   * @param ref Updated reference ID.
   */
  suspend fun updateMapPin(
      uid: String,
      type: PinType,
      location: Location,
      label: String,
      ref: String
  ) {
    val pin = MapPin(uid = uid, type = type, location = location, label = label, ref = ref)

    mapPinCollection.document(pin.uid).set(toNoUid(pin)).await()
    setGeoLocation(pin.uid, pin.location)
  }

  /**
   * Deletes a map pin document and removes its geolocation data.
   *
   * @param uid Unique ID of the pin to delete.
   */
  suspend fun deleteMapPin(uid: String) {
    mapPinCollection.document(uid).delete().await()
    removeGeoLocation(uid)
  }

  /**
   * Sets the geolocation of a pin using GeoFirestore.
   *
   * @param uid Document ID of the pin.
   * @param location Location to associate with the pin.
   */
  private suspend fun setGeoLocation(uid: String, location: Location) {
    val geoFirestore = GeoFirestore(mapPinCollection)
    suspendCoroutine { cont ->
      geoFirestore.setLocation(uid, GeoPoint(location.latitude, location.longitude)) { exception ->
        if (exception != null) cont.resumeWithException(exception) else cont.resume(Unit)
      }
    }
  }

  /**
   * Removes the geolocation data of a pin from GeoFirestore.
   *
   * @param uid Document ID of the pin.
   */
  private suspend fun removeGeoLocation(uid: String) {
    val geoFirestore = GeoFirestore(mapPinCollection)
    suspendCoroutine { cont ->
      geoFirestore.removeLocation(uid) { exception ->
        if (exception != null) cont.resumeWithException(exception) else cont.resume(Unit)
      }
    }
  }
}
