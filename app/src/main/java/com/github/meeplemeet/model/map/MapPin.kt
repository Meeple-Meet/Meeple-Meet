package com.github.meeplemeet.model.map

import com.github.meeplemeet.model.shared.Location
import kotlinx.serialization.Serializable

/**
 * Represents a map pin displayed on the map and linked to a Firestore entity.
 *
 * @property uid Globally unique identifier of the pin (Firestore document ID).
 * @property type Type of the pin (e.g. SHOP, SESSION, SPACE).
 * @property location Geographical coordinates of the pin.
 * @property label Human-readable label displayed on the map.
 * @property ref Firestore path to the linked entity (e.g. "/shops/{id}").
 */
data class MapPin(
    val uid: String,
    val type: PinType,
    val location: Location,
    val label: String,
    val ref: String
)

/** Extension method to only fetch the document ID, without the collections paths */
val MapPin.refId: String
  get() = ref.substringAfterLast("/")

/**
 * Minimal serializable form of [MapPin] without the UID, used for Firestore storage.
 *
 * Firestore stores the UID as the document ID, so it is omitted from the stored object.
 */
@Serializable
data class MapPinNoUid(
    val type: PinType = PinType.SHOP,
    val location: Location = Location(),
    val label: String = "",
    val ref: String = "",
)

/**
 * Converts a full [MapPin] into its Firestore-storable form [MapPinNoUid].
 *
 * @param pin The map pin instance to convert.
 * @return The stripped-down form without UID for storage.
 */
fun toNoUid(pin: MapPin): MapPinNoUid = MapPinNoUid(pin.type, pin.location, pin.label, pin.ref)

/**
 * Reconstructs a full [MapPin] object from its Firestore representation.
 *
 * @param id The Firestore document ID (used as pin UID).
 * @param noUid The deserialized [MapPinNoUid] data from Firestore.
 * @return A fully constructed [MapPin] instance.
 */
fun fromNoUid(id: String, noUid: MapPinNoUid): MapPin =
    MapPin(id, noUid.type, noUid.location, noUid.label, noUid.ref)

/** Enum representing the type of entity linked to a map pin. */
enum class PinType {
  SHOP,
  SPACE,
  SESSION
}
