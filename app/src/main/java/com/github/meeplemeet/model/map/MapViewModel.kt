// Docs generated with Claude Code.

package com.github.meeplemeet.model.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.sessions.SessionRepository
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

/**
 * Represents the UI state of the map, including currently visible geo-pins, any selected
 * pin/preview, loading state, and error messages.
 *
 * @property geoPins The filtered list of geo-pins, based on [activeFilters].
 */
data class MapUIState(
    val allGeoPins: List<GeoPinWithLocation> = emptyList(),
    val activeFilters: Set<PinType> = PinType.entries.toSet(),
    val errorMsg: String? = null,
    val selectedMarkerPreview: MarkerPreview? = null,
    val selectedGeoPin: StorableGeoPin? = null,
    val isLoadingPreview: Boolean = false
) {
  val geoPins: List<GeoPinWithLocation>
    get() = allGeoPins.filter { it.geoPin.type in activeFilters }
}

/**
 * ViewModel responsible for managing and observing geographically indexed map data (geo-pins).
 *
 * This class connects the UI layer (e.g., a map composable) to Firestore through [GeoFirestore],
 * enabling real-time spatial queries based on a geographic center and radius.
 *
 * The ViewModel maintains a [MapUIState] representing the current visible markers, selection state,
 * and potential errors, all exposed as a [StateFlow].
 *
 * The lifecycle of the geographic query is controlled by [startGeoQuery] and [stopGeoQuery].
 *
 * ### Usage Notes
 * - [startGeoQuery] should typically be called **once** when the screen or map component is
 *   launched.
 * - After initialization, the query is kept up to date automatically by [GeoFirestore].
 * - To change the query parameters (center or radius), simply call [updateQueryCenter],
 *   [updateRadius], or [updateCenterAndRadius]; there is **no need** to restart the query.
 */
class MapViewModel(
    private val markerPreviewRepo: MarkerPreviewRepository = RepositoryProvider.markerPreviews,
    geoPinRepository: StorableGeoPinRepository = RepositoryProvider.geoPins,
    private val sessionRepo: SessionRepository = RepositoryProvider.sessions
) : ViewModel() {
  private val _uiState = MutableStateFlow(MapUIState())
  /** Public observable state of the map, containing geo-pins, errors, and selection info. */
  val uiState: StateFlow<MapUIState> = _uiState.asStateFlow()

  private val geoPinCollection = geoPinRepository.collection
  private var geoQuery: GeoQuery? = null

  /**
   * Starts a new geographic Firestore query centered around the given [center] and within the given
   * [radiusKm]. This method automatically listens for live updates from Firestore: whenever a
   * geo-pin is added, moved, or removed within the queried area, the [uiState] is updated
   * accordingly.
   *
   * The previous query (if any) is automatically stopped before starting a new one.
   *
   * @param center The geographic center of the query (latitude and longitude).
   * @param radiusKm The query radius, in kilometers.
   * @param currentUserId The ID of the current user. Used to fetch only sessions in which the user
   *   is a participant.
   */
  fun startGeoQuery(center: Location, radiusKm: Double, currentUserId: String) {
    // Always reset previous query before creating a new one.
    stopGeoQuery()

    // Pre-fetch session IDs where user is participating
    viewModelScope.launch {
      val userSessionIds = mutableSetOf<String>()
      try {
        val ids = sessionRepo.getSessionIdsForUser(currentUserId)
        userSessionIds.addAll(ids)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(errorMsg = "Failed to load user sessions: ${e.message ?: "unknown"}")
        }
        // continue: listener will still be attached but sessions will be filtered out
      }

      val geoFirestore = GeoFirestore(geoPinCollection)
      val query =
          geoFirestore.queryAtLocation(GeoPoint(center.latitude, center.longitude), radiusKm)

      // --- Core GeoQuery listener handling all Firestore-driven updates in real-time ---
      val listener =
          object : GeoQueryEventListener {

            /**
             * Called when a new document enters the queried radius. Fetches the full Firestore
             * document, converts it into a [GeoPinWithLocation], and adds it to [uiState].
             *
             * @param documentID The Firestore document ID.
             * @param location The geographic coordinates of the pin.
             */
            override fun onKeyEntered(documentID: String, location: GeoPoint) {
              viewModelScope.launch {
                try {
                  val doc = geoPinCollection.document(documentID).get().await()
                  val noUid = doc.toObject(StorableGeoPinNoUid::class.java) ?: return@launch

                  // Ignore session where user is not participating
                  if (noUid.type == PinType.SESSION && !userSessionIds.contains(documentID))
                      return@launch

                  val pin = GeoPinWithLocation(fromNoUid(documentID, noUid), location)

                  _uiState.update { it.copy(allGeoPins = it.allGeoPins + pin) }
                } catch (e: Exception) {
                  _uiState.update {
                    it.copy(
                        errorMsg =
                            "Failed to read geo pin $documentID: ${e.message ?: "unknown error"}")
                  }
                }
              }
            }

            /**
             * Called when a document already within the query radius changes location. Updates the
             * corresponding [GeoPinWithLocation] in [uiState].
             *
             * @param documentID The Firestore document ID.
             * @param location The new geographic position of the pin.
             */
            override fun onKeyMoved(documentID: String, location: GeoPoint) {
              _uiState.update { state ->
                val updatedPins =
                    state.allGeoPins.map { pinWithLocation ->
                      if (pinWithLocation.geoPin.uid == documentID)
                          pinWithLocation.copy(location = location)
                      else pinWithLocation
                    }
                state.copy(allGeoPins = updatedPins)
              }
            }

            /**
             * Called when a document leaves the query radius (e.g., moved or deleted). Removes the
             * pin from [uiState].
             *
             * @param documentID The Firestore document ID.
             */
            override fun onKeyExited(documentID: String) {
              _uiState.update { it ->
                it.copy(allGeoPins = it.allGeoPins.filterNot { it.geoPin.uid == documentID })
              }
            }

            /**
             * Called once all initial documents in range have been loaded. Can be used to signal
             * that the query is ready to display results.
             */
            override fun onGeoQueryReady() {}

            /**
             * Called when an error occurs while listening to Firestore geo updates. Updates the
             * MapUIState errorMsg with the error message.
             *
             * @param exception The exception describing the failure.
             */
            override fun onGeoQueryError(exception: Exception) {
              _uiState.update {
                it.copy(errorMsg = "GeoQuery error: ${exception.message ?: "unknown error"}")
              }
            }
          }

      query.addGeoQueryEventListener(listener)
      geoQuery = query
    }
  }

  /**
   * Stops any active geo query and clears the map state. This function is automatically called when
   * the ViewModel is cleared.
   */
  fun stopGeoQuery() {
    try {
      geoQuery?.removeAllListeners()
    } catch (_: Exception) {} finally {
      geoQuery = null
    }

    _uiState.update { MapUIState() }
  }

  /**
   * Updates the set of active pin type filters applied to the map. This affects the
   * [MapUIState.geoPins] property, which is the filtered view used by the UI.
   *
   * @param newFilters The new set of [PinType] to display.
   */
  fun updateFilters(newFilters: Set<PinType>) {
    _uiState.update { it.copy(activeFilters = newFilters) }
  }

  /**
   * Updates the geographic center of the currently active query. Has no effect if no query is
   * running.
   *
   * @param newCenter The new query center.
   */
  fun updateQueryCenter(newCenter: Location) {
    geoQuery?.setCenter(GeoPoint(newCenter.latitude, newCenter.longitude))
  }

  /**
   * Updates the radius (in kilometers) of the current query dynamically. Has no effect if no query
   * is running.
   *
   * @param newRadiusKm The new radius in kilometers.
   */
  fun updateRadius(newRadiusKm: Double) {
    geoQuery?.setRadius(newRadiusKm)
  }

  /**
   * Updates both the center and radius of the geo query in a single operation.
   *
   * @param center The new query center.
   * @param radiusKm The new radius in kilometers.
   */
  fun updateCenterAndRadius(center: Location, radiusKm: Double) {
    geoQuery?.setLocation(GeoPoint(center.latitude, center.longitude), radiusKm)
  }

  /**
   * Selects a specific geo-pin and fetches its corresponding [MarkerPreview] for display in the UI.
   * If fetching fails, updates the MapUIState errorMsg.
   *
   * @param pin The selected geo-pin.
   */
  fun selectPin(pin: GeoPinWithLocation) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingPreview = true, selectedGeoPin = pin.geoPin) }
      try {
        val preview = markerPreviewRepo.getMarkerPreview(pin.geoPin)
        _uiState.update {
          it.copy(
              selectedMarkerPreview = preview,
              selectedGeoPin = pin.geoPin,
              errorMsg = null,
              isLoadingPreview = false)
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
              selectedMarkerPreview = null,
              selectedGeoPin = null,
              errorMsg = "Failed to fetch preview for ${pin.geoPin.uid}: ${e.message}",
              isLoadingPreview = false)
        }
      }
    }
  }

  /** Clears the current selection (both marker preview and geo-pin). */
  fun clearSelectedPin() {
    _uiState.update { it.copy(selectedMarkerPreview = null, selectedGeoPin = null) }
  }

  /** Clears the current error message from [uiState]. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  /** Called automatically when the ViewModel is cleared; stops the geo query listener. */
  override fun onCleared() {
    stopGeoQuery()
    super.onCleared()
  }
}
