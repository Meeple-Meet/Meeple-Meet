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

/**
 * Repository responsible for managing map pins in Firestore. Each pin includes geolocation data
 * stored via the GeoFirestore library.
 *
 * @property geoOps Operations wrapper for GeoFirestore (injectable for testing).
 */
class StorableGeoPinRepository(private val geoOps: GeoFirestoreOperations? = null) :
    FirestoreRepository("geo_pins") {

  /** Retry parameters for GeoFirestore operations */
  private companion object {

    const val GEO_RETRY_COUNT = 3
    const val GEO_RETRY_DELAY = 200L
  }

  // Lazy initialization of the default GeoFirestore operations
  private val geoOperations: GeoFirestoreOperations by lazy {
    geoOps ?: DefaultGeoFirestoreOperations(collection)
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
   * @param ownerId Owner identifier for business pins, or null for session pins.
   * @return The created or updated [StorableGeoPin] instance.
   * @throws Exception if geolocation cannot be set after all retries.
   */
  private suspend fun upsertGeoPin(
      ref: String,
      type: PinType,
      location: Location,
      ownerId: String?
  ): StorableGeoPin {
    val pin = StorableGeoPin(uid = ref, type = type, ownerId = ownerId)

    collection.document(pin.uid).set(toNoUid(pin), SetOptions.merge()).await()
    retry("set geolocation for $ref") { setGeoLocation(pin.uid, location) }

    return pin
  }

  /**
   * Creates or updates a business geo-pin (SHOP or SPACE).
   *
   * This method enforces the business invariant that only business pins (SHOP or SPACE) may have an
   * owner. The provided [ownerId] must be non-null and will be stored with the pin.
   *
   * @param ref ID of the external object this pin represents (shop or space).
   * @param type Pin type. Must be either [PinType.SHOP] or [PinType.SPACE].
   * @param location Geographic location of the pin.
   * @param ownerId Identifier of the user owning the business.
   * @return The created or updated [StorableGeoPin] instance.
   * @throws IllegalArgumentException if [type] is not SHOP or SPACE.
   * @throws Exception if geolocation cannot be set after all retries.
   */
  suspend fun upsertBusinessGeoPin(
      ref: String,
      type: PinType,
      location: Location,
      ownerId: String
  ): StorableGeoPin {
    require(type == PinType.SPACE || type == PinType.SHOP) {
      "Business geo-pin must be of type SHOP or SPACE"
    }

    return upsertGeoPin(ref = ref, type = type, location = location, ownerId = ownerId)
  }

  /**
   * Creates or updates a session geo-pin.
   *
   * Session pins do not have an owner and are visible to all session participants. Ownership
   * information is therefore intentionally omitted.
   *
   * @param ref ID of the session this pin represents.
   * @param location Geographic location of the session.
   * @return The created or updated [StorableGeoPin] instance.
   * @throws Exception if geolocation cannot be set after all retries.
   */
  suspend fun upsertSessionGeoPin(ref: String, location: Location): StorableGeoPin {
    return upsertGeoPin(ref = ref, type = PinType.SESSION, location = location, ownerId = null)
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
    suspendCoroutine { cont ->
      geoOperations.setLocation(uid, GeoPoint(location.latitude, location.longitude)) { exception ->
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
    suspendCoroutine { cont ->
      geoOperations.removeLocation(uid) { exception ->
        if (exception != null) cont.resumeWithException(exception) else cont.resume(Unit)
      }
    }
  }
}
