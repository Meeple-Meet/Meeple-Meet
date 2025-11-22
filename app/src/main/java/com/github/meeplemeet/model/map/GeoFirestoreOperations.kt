package com.github.meeplemeet.model.map

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.GeoPoint
import org.imperiumlabs.geofirestore.GeoFirestore
import org.imperiumlabs.geofirestore.extension.removeLocation
import org.imperiumlabs.geofirestore.extension.setLocation

/** Wrapper interface around GeoFirestore operations to allow testing. */
interface GeoFirestoreOperations {
  /**
   * Sets the location for a document.
   *
   * @param uid Document ID
   * @param geoPoint Location to set
   * @param callback Completion callback
   */
  fun setLocation(uid: String, geoPoint: GeoPoint, callback: (Exception?) -> Unit)

  /**
   * Removes the location for a document.
   *
   * @param uid Document ID
   * @param callback Completion callback
   */
  fun removeLocation(uid: String, callback: (Exception?) -> Unit)
}

/** Default implementation that delegates to the real GeoFirestore library. */
class DefaultGeoFirestoreOperations(private val collectionReference: CollectionReference) :
    GeoFirestoreOperations {

  override fun setLocation(uid: String, geoPoint: GeoPoint, callback: (Exception?) -> Unit) {
    val geoFirestore = GeoFirestore(collectionReference)
    geoFirestore.setLocation(uid, geoPoint, callback)
  }

  override fun removeLocation(uid: String, callback: (Exception?) -> Unit) {
    val geoFirestore = GeoFirestore(collectionReference)
    geoFirestore.removeLocation(uid, callback)
  }
}
