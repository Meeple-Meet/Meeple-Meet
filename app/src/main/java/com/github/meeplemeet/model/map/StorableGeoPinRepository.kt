package com.github.meeplemeet.model.map

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.shared.location.Location
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
const val GEO_PIN_COLLECTION_PATH = "geo_pins"

/**
 * Repository responsible for managing map pins in Firestore. Each pin includes geolocation data
 * stored via the GeoFirestore library.
 *
 * @property db Firestore instance used to access the pins collection.
 */
class StorableGeoPinRepository(private val db: FirebaseFirestore = FirebaseProvider.db) {
  /** Reference to the Firestore collection where pins are stored. */
  private val geoPinCollection = db.collection(GEO_PIN_COLLECTION_PATH)

  /**
   * Creates or replaces a geo-pin document in Firestore using the given ID.
   *
   * This method is idempotent: if a pin with the same ID already exists, it will be overwritten.
   * The pin is stored with metadata and geolocation, allowing it to be displayed on the map and
   * referenced later by its UID (which matches the linked entity's ID).
   *
   * @param ref ID of the external object this pin is linked to (e.g. the shop or session it
   *   represents).
   * @param type Updated pin type. See [PinType]
   * @param location Updated location.
   * @return The created or updated [StorableGeoPin] instance.
   */
  suspend fun upsertGeoPin(ref: String, type: PinType, location: Location): StorableGeoPin {
    val pin = StorableGeoPin(uid = ref, type = type)

    geoPinCollection.document(pin.uid).set(toNoUid(pin)).await()
    setGeoLocation(pin.uid, location)

    return pin
  }

  /**
   * Deletes a geo-pin document and removes its geolocation data.
   *
   * @param ref ID of the external object this pin is linked to (e.g. the shop or session it
   *   represents).
   */
  suspend fun deleteGeoPin(ref: String) {
    removeGeoLocation(ref)
    geoPinCollection.document(ref).delete().await()
  }

  /**
   * Sets the geolocation of a geo-pin using GeoFirestore.
   *
   * @param uid Document ID of the pin.
   * @param location Location to associate with the pin.
   */
  private suspend fun setGeoLocation(uid: String, location: Location) {
    val geoFirestore = GeoFirestore(geoPinCollection)
    suspendCoroutine { cont ->
      geoFirestore.setLocation(uid, GeoPoint(location.latitude, location.longitude)) { exception ->
        if (exception != null) cont.resumeWithException(exception) else cont.resume(Unit)
      }
    }
  }

  /**
   * Removes the geolocation data of a geo-pin from GeoFirestore.
   *
   * @param uid Document ID of the pin.
   */
  private suspend fun removeGeoLocation(uid: String) {
    val geoFirestore = GeoFirestore(geoPinCollection)
    suspendCoroutine { cont ->
      geoFirestore.removeLocation(uid) { exception ->
        if (exception != null) cont.resumeWithException(exception) else cont.resume(Unit)
      }
    }
  }
}
