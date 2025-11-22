package com.github.meeplemeet.model.map

import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.imperiumlabs.geofirestore.GeoFirestore
import org.imperiumlabs.geofirestore.extension.removeLocation
import org.imperiumlabs.geofirestore.extension.setLocation

/**
 * Repository responsible for managing map pins in Firestore. Each pin includes geolocation data
 * stored via the GeoFirestore library.
 *
 * @property db Firestore instance used to access the pins collection.
 */
class StorableGeoPinRepository : FirestoreRepository("geo_pins") {

  /** Retry parameters for GeoFirestore operations */
  private companion object {

    const val GEO_RETRY_COUNT = 3
    const val GEO_RETRY_DELAY = 200L
  }

  /**
   * Creates or replaces a geo-pin document in Firestore using the given ID.
   *
   * This method is idempotent: if a pin with the same ID already exists, it will be overwritten.
   * Metadata is stored with merge semantics to avoid overwriting geolocation fields. Geolocation is
   * then set via GeoFirestore with retry logic to handle transient failures.
   *
   * @param ref ID of the external object this pin is linked to (e.g. the shop or session it
   *   represents).
   * @param type Updated pin type. See [PinType]
   * @param location Updated location.
   * @return The created or updated [StorableGeoPin] instance.
   * @throws Exception if geolocation cannot be set after all retries.
   */
  suspend fun upsertGeoPin(ref: String, type: PinType, location: Location): StorableGeoPin {
    val pin = StorableGeoPin(uid = ref, type = type)

    collection.document(pin.uid).set(toNoUid(pin), SetOptions.merge()).await()
    retry("set geolocation for $ref") { setGeoLocation(pin.uid, location) }

    return pin
  }

  /**
   * Deletes a geo-pin document and removes its geolocation data.
   *
   * Geolocation removal is attempted with retry logic to handle transient failures. The Firestore
   * document is then deleted. Deletion is idempotent: if the document does not exist, no error is
   * thrown.
   *
   * @param ref ID of the external object this pin is linked to (e.g. the shop or session it
   *   represents).
   * @throws Exception if geolocation removal fails after all retries or if document deletion fails.
   */
  suspend fun deleteGeoPin(ref: String) {
    retry("remove geolocation for $ref") { removeGeoLocation(ref) }
    collection.document(ref).delete().await()
  }

  /**
   * Executes a suspendable block with retry logic.
   *
   * The block is executed up to [GEO_RETRY_COUNT] times. If it throws an exception, the error is
   * captured and the block retried after [GEO_RETRY_DELAY] milliseconds. If all attempts fail, the
   * last captured exception is rethrown. If no exception was captured (unexpected case), an
   * [IllegalStateException] is thrown.
   *
   * @param action A human-readable description of the action, used in error messages.
   * @param block The suspendable operation to execute.
   * @throws Exception if the block fails after all retries.
   */
  private suspend fun retry(action: String, block: suspend () -> Unit) {
    var lastError: Exception? = null
    repeat(GEO_RETRY_COUNT) { attempt ->
      try {
        block()
        lastError = null
        return
      } catch (e: Exception) {
        lastError = e
        if (attempt < GEO_RETRY_COUNT - 1) delay(GEO_RETRY_DELAY)
      }
    }
    throw lastError ?: IllegalStateException("Unknown error during $action")
  }

  /**
   * Sets the geolocation of a geo-pin using GeoFirestore.
   *
   * @param uid Document ID of the pin.
   * @param location Location to associate with the pin.
   */
  private suspend fun setGeoLocation(uid: String, location: Location) {
    val geoFirestore = GeoFirestore(collection)
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
    val geoFirestore = GeoFirestore(collection)
    suspendCoroutine { cont ->
      geoFirestore.removeLocation(uid) { exception ->
        if (exception != null) cont.resumeWithException(exception) else cont.resume(Unit)
      }
    }
  }
}
