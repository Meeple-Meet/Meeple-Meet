// Docs generated with Claude Code.

package com.github.meeplemeet.model.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.map.cluster.Cluster
import com.github.meeplemeet.model.map.cluster.ClusterItem
import com.github.meeplemeet.model.map.cluster.ClusterManager
import com.github.meeplemeet.model.map.cluster.DistanceBasedClusterStrategy
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * Cached cluster data with a version number to detect invalidation.
 *
 * @param clusters The list of clusters in this cache.
 * @param version Version number of the cache; incremented on any data change.
 */
data class ClusterCache(val clusters: List<Cluster<GeoPinWithLocation>>, val version: Int)

/** Pending operations on the pin list, accumulated during the debounce window. */
private sealed interface PinOperation {
  data class Add(val pin: GeoPinWithLocation, val preview: MarkerPreview?) : PinOperation

  data class Move(val documentId: String, val newLocation: GeoPoint) : PinOperation

  data class Remove(val documentId: String) : PinOperation
}

/**
 * Represents the UI state of the map, including currently visible geo-pins, cluster cache, and
 * selection state.
 *
 * @property allGeoPins The complete list of geo-pins from the query.
 * @property activeFilters The set of active pin type filters.
 * @property currentZoomLevel The current zoom level of the map.
 * @property clusterCache Cached cluster computation to avoid redundant calculations.
 * @property cacheVersion Incremented whenever allGeoPins, activeFilters, or zoom changes.
 */
data class MapUIState(
    internal val allGeoPins: List<GeoPinWithLocation> = emptyList(),
    val previews: Map<String, MarkerPreview> = emptyMap(),
    val activeFilters: Set<PinType> = PinType.entries.toSet(),
    val currentZoomLevel: Float = 14f,
    val errorMsg: String? = null,
    val selectedMarkerPreview: MarkerPreview? = null,
    val selectedClusterPreviews: List<Pair<GeoPinWithLocation, MarkerPreview>>? = null,
    val selectedPin: GeoPinWithLocation? = null,
    val isLoadingPreview: Boolean = false,

    // Internal cluster cache
    internal val clusterCache: ClusterCache? = null,
    internal val cacheVersion: Int = 0
)

/**
 * ViewModel managing geographically indexed map data (geo-pins) and clustering.
 *
 * Connects the UI layer (e.g., map composable) to Firestore through [GeoFirestore], enabling
 * real-time spatial queries with automatic updates.
 *
 * Clusters are cached to reduce recomputation, and updates are debounced to avoid multiple
 * recompositions.
 */
class MapViewModel(
    private val markerPreviewRepo: MarkerPreviewRepository = RepositoryProvider.markerPreviews,
    geoPinRepository: StorableGeoPinRepository = RepositoryProvider.geoPins,
    private val sessionRepo: SessionRepository = RepositoryProvider.sessions,
    private val clusterManager: ClusterManager = ClusterManager(DistanceBasedClusterStrategy())
) : ViewModel() {
  private val _uiState = MutableStateFlow(MapUIState())
  /** Public observable state of the map, containing geo-pins, errors, and selection info. */
  val uiState: StateFlow<MapUIState> = _uiState.asStateFlow()

  private val geoPinCollection = geoPinRepository.collection
  private var geoQuery: GeoQuery? = null

  // Debounce for pin updates
  private val pendingOperations = mutableListOf<PinOperation>()
  private var debounceJob: Job? = null
  private val PIN_UPDATE_DEBOUNCE_MS = 500L

  // Cached user session IDs for filtering
  private var userSessionIds = setOf<String>()

  /**
   * Returns the current clusters for the filtered pins at the current zoom level.
   *
   * Uses cached result if available and valid. Otherwise, computes new clusters and updates the
   * cache.
   */
  fun getClusters(): List<Cluster<GeoPinWithLocation>> {
    val state = _uiState.value

    // Check if cache is valid
    if (state.clusterCache != null && state.clusterCache.version == state.cacheVersion) {
      return state.clusterCache.clusters
    }

    // Cache is invalid or missing - recompute
    val filteredPins = state.allGeoPins.filter { it.geoPin.type in state.activeFilters }
    val clusters =
        clusterManager.cluster(
            items = filteredPins,
            zoomLevel = state.currentZoomLevel,
            mapper = { pin -> ClusterItem(pin.location.latitude, pin.location.longitude) })

    // Update cache in state
    _uiState.update { it.copy(clusterCache = ClusterCache(clusters, state.cacheVersion)) }

    return clusters
  }

  /**
   * Updates the current zoom level. Invalidates cluster cache.
   *
   * @param zoomLevel The new zoom level to apply for clustering.
   */
  fun updateZoomLevel(zoomLevel: Float) {
    _uiState.update { it.copy(currentZoomLevel = zoomLevel, cacheVersion = it.cacheVersion + 1) }
  }

  /**
   * Updates the set of active pin type filters applied to the map. Invalidates cluster cache.
   *
   * @param newFilters The new set of [PinType] to display.
   */
  fun updateFilters(newFilters: Set<PinType>) {
    _uiState.update { it.copy(activeFilters = newFilters, cacheVersion = it.cacheVersion + 1) }
  }

  /**
   * Schedules a pending pin operation to be applied after a short debounce period.
   *
   * Multiple operations added within [PIN_UPDATE_DEBOUNCE_MS] milliseconds are batched together to
   * minimize recompositions and cluster recalculations.
   *
   * @param operation The pin operation to schedule (Add, Move, or Remove).
   */
  private fun scheduleOperation(operation: PinOperation) {
    synchronized(pendingOperations) { pendingOperations.add(operation) }

    debounceJob?.cancel()
    debounceJob =
        viewModelScope.launch {
          delay(PIN_UPDATE_DEBOUNCE_MS)
          flushPendingOperations()
        }
  }

  /** Applies all pending pin operations in a single state update to minimize recompositions. */
  private fun flushPendingOperations() {
    val operations =
        synchronized(pendingOperations) {
          val copy = pendingOperations.toList()
          pendingOperations.clear()
          copy
        }

    if (operations.isEmpty()) return

    _uiState.update { state ->
      // Use a mutable map for O(1) lookups by documentId
      val pinMap = state.allGeoPins.associateBy { it.geoPin.uid }.toMutableMap()
      val previewMap = state.previews.toMutableMap()

      // Apply sequentially all pending operations
      operations.forEach { op ->
        when (op) {
          is PinOperation.Add -> {
            pinMap[op.pin.geoPin.uid] = op.pin
            if (op.preview != null) {
              previewMap[op.pin.geoPin.uid] = op.preview
            }
          }
          is PinOperation.Move ->
              pinMap[op.documentId]?.let { old ->
                pinMap[op.documentId] = old.copy(location = op.newLocation)
              }
          is PinOperation.Remove -> {
            pinMap.remove(op.documentId)
            previewMap.remove(op.documentId)
          }
        }
      }

      // Only one state update, thus only one cache invalidation.
      state.copy(
          allGeoPins = pinMap.values.toList(),
          previews = previewMap,
          cacheVersion = state.cacheVersion + 1)
    }
  }

  /** Immediately flushes all pending operations. */
  private fun flushImmediately() {
    debounceJob?.cancel()
    flushPendingOperations()
  }

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

    viewModelScope.launch {
      try {
        // Load user sessions for filtering
        val ids = sessionRepo.getSessionIdsForUser(currentUserId)
        userSessionIds = ids.toSet()
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
             * document, converts it into a [GeoPinWithLocation], and adds it to [uiState]. Also
             * loads the preview for the pin immediately.
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

                  // Load preview immediately
                  val preview =
                      try {
                        markerPreviewRepo.getMarkerPreview(pin.geoPin)
                      } catch (_: Exception) {
                        null // Preview loading failed, but still add the pin
                      }

                  scheduleOperation(PinOperation.Add(pin, preview))
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
              scheduleOperation(PinOperation.Move(documentID, location))
            }

            /**
             * Called when a document leaves the query radius (e.g., moved or deleted). Removes the
             * pin from [uiState].
             *
             * @param documentID The Firestore document ID.
             */
            override fun onKeyExited(documentID: String) {
              scheduleOperation(PinOperation.Remove(documentID))
            }

            /**
             * Called once all initial documents in range have been loaded. Can be used to signal
             * that the query is ready to display results.
             */
            override fun onGeoQueryReady() {
              // Immediately flush pending ops after the initial query burst
              flushImmediately()
            }

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

    // Cleanup debouncing
    debounceJob?.cancel()
    debounceJob = null
    pendingOperations.clear()

    _uiState.update { MapUIState() }
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
   * Selects a specific geo-pin and displays its [MarkerPreview]. Uses cached preview if available,
   * otherwise loads it on-demand. If fetching fails, updates the MapUIState errorMsg.
   *
   * @param pin The selected geo-pin.
   */
  fun selectPin(pin: GeoPinWithLocation) {
    val cachedPreview = _uiState.value.previews[pin.geoPin.uid]

    if (cachedPreview != null) {
      // Use cached preview immediately
      _uiState.update {
        it.copy(selectedMarkerPreview = cachedPreview, selectedPin = pin, errorMsg = null)
      }
    } else {
      // Load preview on-demand
      viewModelScope.launch {
        _uiState.update { it.copy(isLoadingPreview = true, selectedPin = pin) }
        try {
          val preview = markerPreviewRepo.getMarkerPreview(pin.geoPin)
          _uiState.update {
            val updatedPreviews =
                if (preview != null) {
                  it.previews + (pin.geoPin.uid to preview)
                } else {
                  it.previews
                }
            it.copy(
                selectedMarkerPreview = preview,
                selectedPin = pin,
                previews = updatedPreviews,
                errorMsg = null,
                isLoadingPreview = false)
          }
        } catch (e: Exception) {
          _uiState.update {
            it.copy(
                selectedMarkerPreview = null,
                selectedPin = null,
                errorMsg = "Failed to fetch preview for ${pin.geoPin.uid}: ${e.message}",
                isLoadingPreview = false)
          }
        }
      }
    }
  }

  /** Clears the current selection (both marker preview and geo-pin). */
  fun clearSelectedPin() {
    _uiState.update { it.copy(selectedMarkerPreview = null, selectedPin = null) }
  }

  /**
   * Selects a cluster and displays previews for all its pins. Uses cached previews if all are
   * available, otherwise loads missing ones using the optimized parallel loading.
   *
   * @param cluster The cluster whose pins should be previewed.
   */
  fun selectCluster(cluster: Cluster<GeoPinWithLocation>) {
    viewModelScope.launch {
      val state = _uiState.value
      val pins = cluster.items

      // Check which pins have cached previews
      val cachedPreviews =
          pins.mapNotNull { pin ->
            state.previews[pin.geoPin.uid]?.let { preview -> pin to preview }
          }

      if (cachedPreviews.size == pins.size) {
        // All cached, use immediately
        _uiState.update { it.copy(selectedClusterPreviews = cachedPreviews, errorMsg = null) }
      } else {
        // Some missing, reload all (to use parallel loading optimization)
        _uiState.update { it.copy(isLoadingPreview = true) }
        try {
          val previews = markerPreviewRepo.getMarkerPreviews(pins.map { it.geoPin })
          val paired = pins.zip(previews.filterNotNull())

          // Update cache with newly loaded previews
          val newPreviews = paired.associate { (pin, preview) -> pin.geoPin.uid to preview }

          _uiState.update {
            it.copy(
                selectedClusterPreviews = paired,
                previews = it.previews + newPreviews,
                errorMsg = null,
                isLoadingPreview = false)
          }
        } catch (e: Exception) {
          _uiState.update {
            it.copy(
                selectedClusterPreviews = null,
                errorMsg = "Failed to load previews for cluster: ${e.message}",
                isLoadingPreview = false)
          }
        }
      }
    }
  }

  /**
   * Selects a single pin from an already selected cluster.
   *
   * This clears the cluster selection.
   *
   * @param pin The pin selected inside the cluster.
   * @param preview The preview previously loaded for that pin.
   */
  fun selectPinFromCluster(pin: GeoPinWithLocation, preview: MarkerPreview) {
    _uiState.update {
      it.copy(
          selectedClusterPreviews = null,
          selectedPin = pin,
          selectedMarkerPreview = preview,
          errorMsg = null)
    }
  }

  /** Clears the currently selected cluster. */
  fun clearSelectedCluster() {
    _uiState.update { it.copy(selectedClusterPreviews = null) }
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
