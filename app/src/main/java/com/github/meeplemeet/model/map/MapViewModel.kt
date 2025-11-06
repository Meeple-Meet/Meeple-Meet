// Docs generated with Claude Code.

package com.github.meeplemeet.model.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.FirebaseProvider.db
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.imperiumlabs.geofirestore.GeoFirestore
import org.imperiumlabs.geofirestore.GeoQuery
import org.imperiumlabs.geofirestore.listeners.GeoQueryEventListener

data class MapUIState(
    val geoPins: List<GeoPinWithLocation> = emptyList(),
    val errorMsg: String? = null,
    val selectedMarkerPreview: MarkerPreview? = null,
    val selectedId: String? = null
)

data class GeoPinWithLocation(val geoPin: StorableGeoPin, val location: GeoPoint)

/**
 * ViewModel for displaying shops and space renters on a map.
 *
 * This ViewModel retrieves and exposes lists of shops and space renters through [StateFlow]s for UI
 * observation, typically for rendering markers on a map interface.
 */
class MapViewModel(
    private val markerPreviewRepo: MarkerPreviewRepository = RepositoryProvider.markerPreviews
) : ViewModel() {
  private val _uiState = MutableStateFlow(MapUIState())
  val uiState: StateFlow<MapUIState> = _uiState.asStateFlow()

  private val geoPinCollection = db.collection(GEO_PIN_COLLECTION_PATH)
  private var geoQuery: GeoQuery? = null

  fun startGeoQuery(
      center: Location,
      radiusKm: Double,
      includeTypes: Set<PinType> = setOf(PinType.SHOP, PinType.SPACE)
  ) {
    stopGeoQuery()

    val geoFirestore = GeoFirestore(geoPinCollection)
    val query = geoFirestore.queryAtLocation(GeoPoint(center.latitude, center.longitude), radiusKm)

    val listener =
        object : GeoQueryEventListener {

          override fun onKeyEntered(documentID: String, location: GeoPoint) {
            viewModelScope.launch {
              try {
                val doc = geoPinCollection.document(documentID).get().await()
                val noUid = doc.toObject(StorableGeoPinNoUid::class.java) ?: return@launch
                if (noUid.type !in includeTypes) return@launch

                val pin = GeoPinWithLocation(fromNoUid(documentID, noUid), location)

                _uiState.update { it.copy(geoPins = it.geoPins + pin) }
              } catch (t: Throwable) {
                _uiState.update {
                  it.copy(errorMsg = t.message ?: "Failed to read geo pin $documentID")
                }
                // TODO what's better
                // _uiState.update { it.copy(errorMsg = "Failed to read geo pin $documentID") }
              }
            }
          }

          override fun onKeyMoved(documentID: String, location: GeoPoint) {
            _uiState.update { state ->
              val updatedPins =
                  state.geoPins.map { pinWithLocation ->
                    if (pinWithLocation.geoPin.uid == documentID)
                        pinWithLocation.copy(location = location)
                    else pinWithLocation
                  }
              state.copy(geoPins = updatedPins)
            }
          }

          override fun onKeyExited(documentID: String) {
            _uiState.update { it ->
              it.copy(geoPins = it.geoPins.filterNot { it.geoPin.uid == documentID })
            }
          }

          override fun onGeoQueryReady() {}

          override fun onGeoQueryError(exception: Exception) {
            _uiState.update { it.copy(errorMsg = exception.message ?: "GeoQuery error") }
            // TODO what's better ?
            // _uiState.update { it.copy(errorMsg = "GeoQuery error") }
          }
        }

    query.addGeoQueryEventListener(listener)
    geoQuery = query
  }

  fun stopGeoQuery() {
    try {
      geoQuery?.removeAllListeners()
    } catch (_: Throwable) {} finally {
      geoQuery = null
    }

    _uiState.update { MapUIState() }
  }

  fun updateQueryCenter(newCenter: Location) {
    geoQuery?.setCenter(GeoPoint(newCenter.latitude, newCenter.longitude))
  }

  fun updateRadius(newRadiusKm: Double) {
    geoQuery?.setRadius(newRadiusKm)
  }

  fun updateCenterAndRadius(center: Location, radiusKm: Double) {
    geoQuery?.setLocation(GeoPoint(center.latitude, center.longitude), radiusKm)
  }

  fun selectPin(pin: GeoPinWithLocation) {
    viewModelScope.launch {
      try {
        val preview = markerPreviewRepo.getMarkerPreview(pin.geoPin)
        _uiState.update {
          it.copy(selectedMarkerPreview = preview, selectedId = pin.geoPin.uid, errorMsg = null)
        }
      } catch (t: Throwable) {
        _uiState.update {
          it.copy(
              selectedMarkerPreview = null,
              selectedId = null,
              errorMsg = t.message ?: "Failed to fetch preview for ${pin.geoPin.uid}")
        }
      }
    }
  }

  fun clearSelectedPin() {
    _uiState.update { it.copy(selectedMarkerPreview = null, selectedId = null) }
  }

  override fun onCleared() {
    stopGeoQuery()
    super.onCleared()
  }
}
