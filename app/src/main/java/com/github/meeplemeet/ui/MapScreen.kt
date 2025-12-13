@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location as AndroidLocation
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.R
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.map.GeoPinWithLocation
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.MarkerPreview
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPin
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.navigation.BottomBarWithVerification
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object MapScreenTestTags {
  const val GOOGLE_MAP_SCREEN = "mapScreen"
  const val RECENTER_BUTTON = "recenterButton"
  const val SCALE_BAR = "scaleBar"
  const val SCALE_BAR_DISTANCE = "scaleBarDistance"
  const val BUTTON_MENU = "mapMenu"
  const val ADD_FAB = "addFab"
  const val ADD_CHOOSE_DIALOG = "chooseAddDialog"
  const val FILTER_BUTTON = "filterButton"
  const val FILTER_SHOP_CHIP = "filterShop"
  const val FILTER_SPACE_CHIP = "filterSpace"
  const val FILTER_SESSIONS_CHIP = "filterSession"
  const val MARKER_PREVIEW_SHEET = "markerPreviewSheet"
  const val PREVIEW_TITLE = "previewTitle"
  const val PREVIEW_ADDRESS = "previewAddress"
  const val PREVIEW_OPENING_HOURS = "previewOpeningHours"
  const val PREVIEW_GAME = "previewGame"
  const val PREVIEW_DATE = "previewDate"
  const val PREVIEW_CLOSE_BUTTON = "previewCloseButton"
  const val PREVIEW_VIEW_DETAILS_BUTTON = "previewViewDetailsButton"
  const val PREVIEW_NAVIGATE_BUTTON = "previewNavigateButton"
  const val CLUSTER_SHEET = "clusterSheet"

  fun getTestTagForPin(pinId: String) = "mapPin_$pinId"

  fun getTestTagForCluster(pos: LatLng) = "mapCluster_${pos.latitude}_${pos.longitude}"

  fun getTestTagForClusterItem(pinId: String) = "clusterItem_$pinId"
}

/**
 * Configuration object for cluster marker visuals on the map.
 *
 * Provides thresholds and colors for cluster sizes, allowing consistent coloring of cluster icons
 * depending on how many pins are grouped together.
 */
object ClusterConfig {
  private val startColor = Color(0xFF4CAF50) // Green
  private val midColor = Color(0xFFFFC107) // Amber
  private val endColor = Color(0xFFE53935) // Red

  private const val SMALL_CLUSTER_THRESHOLD = 5
  private const val MEDIUM_CLUSTER_THRESHOLD = 15

  private const val BACKGROUND_ALPHA = 0.85f

  /** Returns a gradient-based color depending on cluster size. */
  fun getColorForSize(size: Int): Color {
    val normalized =
        when {
          size <= SMALL_CLUSTER_THRESHOLD -> 0f
          size <= MEDIUM_CLUSTER_THRESHOLD ->
              (size - SMALL_CLUSTER_THRESHOLD).toFloat() /
                  (MEDIUM_CLUSTER_THRESHOLD - SMALL_CLUSTER_THRESHOLD)
          else -> 1f
        }

    // Interpolate:
    val color =
        if (normalized < 0.5f) {
          val t = normalized / 0.5f
          lerp(startColor, midColor, t)
        } else {
          val t = (normalized - 0.5f) / 0.5f
          lerp(midColor, endColor, t)
        }

    return color.copy(alpha = BACKGROUND_ALPHA)
  }
}

private object MapScaleBarDefaults {
  const val BAR_WIDTH: Int = 100
  val LINE_HEIGHT = 2.dp
  val TICK_HEIGHT = 8.dp
  val TICK_WIDTH = 2.dp
  val LABEL_SPACING = 2.dp
}

private const val DEFAULT_RADIUS_KM = 10.0
private const val DEFAULT_ZOOM_LEVEL = 14f
private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 5_000L
private const val CAMERA_CENTER_DEBOUNCE_MS = 1000L
private const val CAMERA_ZOOM_DEBOUNCE_MS = 500L
private const val SCALE_BAR_HIDE_MS = 3000L
private const val DEFAULT_MARKER_SCALE = 1.5f
private const val DEFAULT_MARKER_BACKGROUND_ALPHA = 1.0f
private const val RGB_MAX_ALPHA = 255

/**
 * MapScreen displays an interactive Google Map centered on the user's location (if granted) or
 * falls back to an approximate default location. It dynamically loads pins (shops, spaces,
 * sessions) from Firestore through the [MapViewModel].
 *
 * Main responsibilities:
 * - Request location permissions (fine or coarse)
 * - Center the map on the user's location or fallback
 * - Start a geo query around that location to load relevant markers
 * - Reactively update query center when user pans the map
 * - Display contextual previews in a bottom sheet when a pin is tapped
 * - Show error messages via snackbar
 *
 * Lifecycle behavior:
 * - When resuming the screen (e.g., returning to app), the location is refreshed once
 * - No continuous location tracking is performed for efficiency
 *
 * @param viewModel MapViewModel managing geo query and pin data
 * @param navigation navigation actions between screens
 * @param account current user account, used to filter sessions and permissions
 * @param onFABCLick action triggered when pressing the add button (for shop/space owners)
 * @param onRedirect action executed when clicking “View details” in a marker preview
 */
@OptIn(FlowPreview::class)
@Composable
fun MapScreen(
    account: Account,
    verified: Boolean,
    navigation: NavigationActions,
    viewModel: MapViewModel = viewModel(),
    onUserLocationChange: (Location?) -> Unit = {},
    onFABCLick: (PinType) -> Unit,
    onRedirect: (StorableGeoPin) -> Unit,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    unreadCount: Int,
    forceNoPermission: Boolean = false
) {
  // --- State & helpers ---
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

  // --- Permission management ---
  var permissionGranted by remember { mutableStateOf(false) }
  var permissionChecked by remember { mutableStateOf(false) }

  /**
   * Handles runtime permission requests for fine location. If fine is denied but coarse is granted,
   * coarse access is used as fallback.
   */
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted =
            granted ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        permissionChecked = true
      }

  // Observe lifecycle to refresh location when returning to the app
  val lifecycleState by
      LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

  // --- Map & query state ---
  var userLocation by remember { mutableStateOf<Location?>(null) }
  var isLoadingLocation by remember { mutableStateOf(true) }
  var isCameraCentered by remember { mutableStateOf(false) }
  var isQueryLaunched by remember { mutableStateOf(false) }
  var includeTypes by remember { mutableStateOf(PinType.entries.toSet()) }

  // --- UI controls (filters & creation) ---
  var showFilterButtons by remember { mutableStateOf(false) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var selectedCreateType by remember { mutableStateOf<PinType?>(null) }

  /**
   * Permission initialization. Checks for existing permission, requests it if missing. Runs only
   * once at screen start.
   */
  LaunchedEffect(Unit) {
    if (forceNoPermission) {
      // Bypass permission manager for testing purpose
      permissionGranted = false
      permissionChecked = true
    } else {
      val fine =
          ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
              PackageManager.PERMISSION_GRANTED
      val coarse =
          ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
              PackageManager.PERMISSION_GRANTED
      when {
        fine || coarse -> {
          permissionGranted = true
          permissionChecked = true
        }
        else -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
      }
    }
  }

  /**
   * Retrieves user location whenever:
   * - permission state changes, or
   * - lifecycle returns to RESUMED (i.e., app resumes).
   */
  LaunchedEffect(permissionGranted, permissionChecked, lifecycleState) {
    if (!permissionChecked) return@LaunchedEffect
    if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect

    isLoadingLocation = true
    val loc =
        if (permissionGranted) {
          try {
            getUserLocation(fusedClient)
          } catch (_: Exception) {
            null
          }
        } else null

    userLocation = loc
    onUserLocationChange(loc)
    isLoadingLocation = false
  }

  // --- Continuous location updates ---
  DisposableEffect(permissionGranted) {
    if (permissionGranted) {
      val locationRequest =
          LocationRequest.Builder(
                  Priority.PRIORITY_BALANCED_POWER_ACCURACY, DEFAULT_LOCATION_UPDATE_INTERVAL_MS)
              .build()

      val callback =
          object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
              result.lastLocation?.let { loc ->
                val location = Location(loc.latitude, loc.longitude, "User Location")
                userLocation = location
                onUserLocationChange(location)
              }
            }
          }

      fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

      onDispose { fusedClient.removeLocationUpdates(callback) }
    } else {
      userLocation = null
      onDispose {}
    }
  }

  /** Starts Firestore geo query once location is resolved (real or fallback). */
  LaunchedEffect(isLoadingLocation) {
    if (isLoadingLocation || isQueryLaunched) return@LaunchedEffect

    viewModel.startGeoQuery(
        center = userLocation ?: getApproximateLocationFromTimezone(),
        currentUserId = account.uid,
        radiusKm = DEFAULT_RADIUS_KM)

    isQueryLaunched = true
  }

  /** Updates the ViewModel filters whenever the set of included pin types changes. */
  LaunchedEffect(includeTypes) { viewModel.updateFilters(includeTypes) }

  /** Displays any error message emitted by the ViewModel in a snackbar. */
  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearErrorMsg()
    }
  }

  // --- UI scaffold ---
  Scaffold(
      bottomBar = {
        BottomBarWithVerification(
            currentScreen = MeepleMeetScreen.Map,
            unreadCount = unreadCount,
            verified = verified,
            onVerifyClick = { navigationActions.navigateTo(MeepleMeetScreen.Profile)},
            onTabSelected = { screen -> navigation.navigateTo(screen) })
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding ->

        // --- Loading state ---
        if (isLoadingLocation) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
          return@Scaffold
        }

        // --- Cluster bottom sheet OR Marker preview bottom sheet ---
        // If a marker preview is selected we show the marker preview sheet (priority).
        // Otherwise if selectedClusterPreviews is non-null we show cluster sheet.

        val markerPreview = uiState.selectedMarkerPreview
        val clusterPreviews = uiState.selectedClusterPreviews
        val isLoading = uiState.isLoadingPreview

        when {
          markerPreview != null -> {
            ModalBottomSheet(
                sheetState = sheetState, onDismissRequest = { viewModel.clearSelectedPin() }) {
                  if (isLoading) {
                    MarkerPreviewLoadingSheet(pin = uiState.selectedPin)
                  } else {
                    MarkerPreviewSheet(
                        preview = uiState.selectedMarkerPreview!!,
                        onClose = { viewModel.clearSelectedPin() },
                        pin = uiState.selectedPin!!,
                        userLocation = userLocation,
                        onRedirect = onRedirect)
                  }
                }
          }
          clusterPreviews != null -> {
            ModalBottomSheet(
                sheetState = sheetState, onDismissRequest = { viewModel.clearSelectedCluster() }) {
                  if (isLoading) {
                    MarkerPreviewLoadingSheet(null)
                  } else {
                    ClusterPreviewSheet(
                        clusterPreviews = clusterPreviews,
                        userLocation = userLocation,
                        onSelectPreview = { geoPin, preview ->
                          viewModel.selectPinFromCluster(geoPin, preview)
                        })
                  }
                }
          }
        }

        // --- Map rendering ---
        /** Centers the camera once location is resolved (real or fallback). */
        LaunchedEffect(isLoadingLocation) {
          if (isLoadingLocation || isCameraCentered) return@LaunchedEffect

          val target =
              userLocation?.let { LatLng(it.latitude, it.longitude) }
                  ?: LatLng(
                      getApproximateLocationFromTimezone().latitude,
                      getApproximateLocationFromTimezone().longitude)

          cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target, DEFAULT_ZOOM_LEVEL))

          isCameraCentered = true
        }

        /**
         * Watches camera updates and reacts to movement & zoom changes. When the map center
         * changes, updates the Firestore query center after a short debounce. When the zoom level
         * changes, updates the ViewModel so clustering can recompute with the right threshold. Both
         * flows are collected concurrently via `launch` to avoid blocking.
         */
        LaunchedEffect(cameraPositionState) {
          // Update query center when map moves
          launch {
            snapshotFlow { cameraPositionState.position.target }
                .debounce(CAMERA_CENTER_DEBOUNCE_MS)
                .collect { latLng ->
                  viewModel.updateQueryCenter(Location(latLng.latitude, latLng.longitude))
                }
          }

          // Update zoom level for clustering when zoom changes
          launch {
            snapshotFlow { cameraPositionState.position.zoom }
                .debounce(CAMERA_ZOOM_DEBOUNCE_MS)
                .collect { zoom -> viewModel.updateZoomLevel(zoom) }
          }
        }

        // --- Main map + buttons ---
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

          // --- Google Map content ---
          val isDarkTheme = isSystemInDarkTheme()
          val mapStyleOptions =
              if (isDarkTheme) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
              } else {
                null
              }

          // Prepare marker icons for different pin types
          val shopIcon = rememberMarkerIcon(resId = R.drawable.ic_storefront)
          val spaceIcon = rememberMarkerIcon(resId = R.drawable.ic_table)
          val sessionIcon = rememberMarkerIcon(resId = R.drawable.ic_dice)

          // State for scale bar visibility and recenter button
          var showScaleBar by remember { mutableStateOf(false) }
          var scaleBarJob: Job? by remember { mutableStateOf(null) }
          val currentZoom = cameraPositionState.position.zoom

          LaunchedEffect(currentZoom) {
            showScaleBar = true
            scaleBarJob?.cancel()
            scaleBarJob = launch {
              delay(SCALE_BAR_HIDE_MS)
              showScaleBar = false
            }
          }

          GoogleMap(
              modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.GOOGLE_MAP_SCREEN),
              cameraPositionState = cameraPositionState,
              properties =
                  MapProperties(
                      mapStyleOptions = mapStyleOptions, isMyLocationEnabled = permissionGranted),
              uiSettings =
                  MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)) {
                val clusters = viewModel.getClusters()

                clusters
                    .filter { it.items.isNotEmpty() }
                    .forEach { cluster ->
                      val size = cluster.items.size
                      val pos = LatLng(cluster.centerLat, cluster.centerLng)

                      if (size == 1) {
                        // Single pin, show the appropriate marker icon
                        val gp = cluster.items.first()
                        val icon =
                            when (gp.geoPin.type) {
                              PinType.SHOP -> shopIcon
                              PinType.SPACE -> spaceIcon
                              PinType.SESSION -> sessionIcon
                            }
                        Marker(
                            state = MarkerState(pos),
                            title = gp.geoPin.uid,
                            snippet = gp.geoPin.type.name,
                            onClick = {
                              viewModel.selectPin(gp) // Open preview for this pin
                              true
                            },
                            icon = icon,
                            tag = MapScreenTestTags.getTestTagForPin(gp.geoPin.uid))
                      } else {
                        // Cluster with multiple pins, create a cluster icon
                        val clusterIcon = rememberClusterIcon(size = size)
                        Marker(
                            state = MarkerState(pos),
                            title = "Cluster ($size)",
                            snippet = "$size items",
                            onClick = {
                              viewModel.selectCluster(cluster) // Open cluster sheet
                              true
                            },
                            icon = clusterIcon,
                            tag = MapScreenTestTags.getTestTagForCluster(pos))
                      }
                    }
              }

          StaticVerticalMapMenu(
              modifier =
                  Modifier.align(Alignment.TopStart)
                      .padding(start = Dimensions.Padding.medium, top = Dimensions.Padding.medium),
              showCreateButton = verified && (account.shopOwner || account.spaceRenter),
              onToggleFilters = { showFilterButtons = !showFilterButtons },
              onAddClick = {
                when (account.shopOwner to account.spaceRenter) {
                  true to true -> showCreateDialog = true
                  true to false -> onFABCLick(PinType.SHOP)
                  false to true -> onFABCLick(PinType.SPACE)
                  else -> {}
                }
              })

          // --- Panel with FilterChips ---
          AnimatedVisibility(
              visible = showFilterButtons,
              enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
              exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
              modifier =
                  Modifier.align(Alignment.TopStart)
                      .offset(
                          x =
                              Dimensions.Padding.medium +
                                  Dimensions.ButtonSize.standard +
                                  Dimensions.Spacing.medium,
                          y = Dimensions.Padding.medium)) {
                Surface(
                    modifier =
                        Modifier.widthIn(
                                max =
                                    Dimensions.ComponentWidth.spaceLabelWidth.plus(
                                        Dimensions.Padding.extraMedium))
                            .wrapContentHeight(),
                    tonalElevation = Dimensions.Elevation.high,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                    color = AppColors.primary.copy(alpha = 0.95f)) {
                      Column(
                          modifier =
                              Modifier.padding(
                                  horizontal = Dimensions.Padding.medium,
                                  vertical = Dimensions.Padding.mediumSmall),
                          verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
                            PinType.entries.forEach { type ->
                              val selected = includeTypes.contains(type)
                              FilterChip(
                                  selected = selected,
                                  onClick = {
                                    includeTypes =
                                        if (selected) includeTypes - type else includeTypes + type
                                  },
                                  label = {
                                    Text(
                                        text =
                                            type.name.lowercase().replaceFirstChar {
                                              it.uppercaseChar()
                                            },
                                        color = AppColors.textIcons,
                                        style = MaterialTheme.typography.labelLarge)
                                  },
                                  leadingIcon = {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(Dimensions.IconSize.medium))
                                  },
                                  colors =
                                      SelectableChipColors(
                                          containerColor = AppColors.primary,
                                          leadingIconColor = Color.Transparent,
                                          trailingIconColor = Color.Transparent,
                                          disabledContainerColor = Color.Transparent,
                                          disabledLabelColor = Color.Transparent,
                                          disabledLeadingIconColor = Color.Transparent,
                                          disabledTrailingIconColor = Color.Transparent,
                                          disabledSelectedContainerColor = Color.Transparent,
                                          selectedLabelColor = Color.Transparent,
                                          selectedLeadingIconColor = Color.Transparent,
                                          selectedTrailingIconColor = Color.Transparent,
                                          labelColor = AppColors.textIcons,
                                          selectedContainerColor = Color.Transparent),
                                  modifier =
                                      Modifier.testTag(pinTypeTestTag(type))
                                          .height(Dimensions.Padding.huge)
                                          .background(AppColors.primary)
                                          .fillMaxWidth())
                            }
                          }
                    }
              }

          // --- Dialog for business creation if ambiguous ---
          if (showCreateDialog) {
            Dialog(
                onDismissRequest = {
                  showCreateDialog = false
                  selectedCreateType = null
                }) {
                  Surface(
                      shape = RoundedCornerShape(Dimensions.CornerRadius.extraLarge),
                      color = AppColors.primary,
                      tonalElevation = Dimensions.Elevation.xxHigh,
                      modifier =
                          Modifier.testTag(MapScreenTestTags.ADD_CHOOSE_DIALOG)
                              .widthIn(
                                  min = Dimensions.ContainerSize.bottomSpacer.times(3),
                                  max =
                                      Dimensions.ContainerSize.bottomSpacer
                                          .times(4)
                                          .plus(Dimensions.Padding.xLarge))
                              .wrapContentHeight()
                              .border(
                                  Dimensions.DividerThickness.standard,
                                  AppColors.textIcons,
                                  RoundedCornerShape(Dimensions.CornerRadius.extraLarge))) {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(
                                        horizontal = Dimensions.Padding.xxLarge,
                                        vertical = Dimensions.Padding.xLarge),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                              // --- Title ---
                              Box(Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Select a place type",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.align(Alignment.Center))
                                IconButton(
                                    onClick = {
                                      showCreateDialog = false
                                      selectedCreateType = null
                                    },
                                    modifier =
                                        Modifier.size(Dimensions.ButtonSize.small)
                                            .align(Alignment.CenterEnd)) {
                                      Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                              }

                              Spacer(Modifier.height(Dimensions.Spacing.medium))
                              HorizontalDivider(
                                  modifier = Modifier.fillMaxWidth(0.8f),
                                  thickness = Dimensions.DividerThickness.standard,
                                  color =
                                      MaterialTheme.colorScheme.onSurface.copy(
                                          alpha = Dimensions.Alpha.readonlyBorder))
                              Spacer(Modifier.height(Dimensions.Spacing.xLarge))

                              // --- Two-option selector ---
                              @Composable
                              fun SingleChoiceSegmentedButtonRowScope.Option(
                                  label: String,
                                  type: PinType,
                                  index: Int
                              ) {
                                SegmentedButton(
                                    selected = selectedCreateType == type,
                                    onClick = { selectedCreateType = type },
                                    shape =
                                        SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                    colors =
                                        SegmentedButtonDefaults.colors(
                                            inactiveContainerColor = AppColors.primary,
                                            activeContainerColor = AppColors.focus),
                                    border =
                                        BorderStroke(
                                            Dimensions.DividerThickness.standard,
                                            AppColors.textIcons),
                                    label = {
                                      Row(
                                          modifier = Modifier.fillMaxWidth(),
                                          verticalAlignment = Alignment.CenterVertically,
                                          horizontalArrangement = Arrangement.Center) {
                                            if (selectedCreateType == type) {
                                              Icon(
                                                  Icons.Default.Check,
                                                  contentDescription = null,
                                                  modifier =
                                                      Modifier.size(Dimensions.IconSize.small))
                                            }
                                            Text(
                                                text = label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis)
                                          }
                                    },
                                    icon = {})
                              }

                              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                Option("Shop", PinType.SHOP, 0)
                                Option("Rental Space", PinType.SPACE, index = 1)
                              }

                              Spacer(Modifier.height(Dimensions.Spacing.xxLarge))
                              Button(
                                  onClick = {
                                    showCreateDialog = false
                                    onFABCLick(selectedCreateType!!)
                                  },
                                  modifier =
                                      Modifier.align(Alignment.CenterHorizontally)
                                          .fillMaxWidth(0.6f),
                                  enabled = selectedCreateType != null,
                                  shape = RoundedCornerShape(Dimensions.CornerRadius.round),
                                  colors =
                                      ButtonDefaults.buttonColors(
                                          containerColor = AppColors.affirmative)) {
                                    Text(
                                        text =
                                            when (selectedCreateType) {
                                              PinType.SHOP -> "Add Shop"
                                              PinType.SPACE -> "Add Rental Space"
                                              else -> "Add"
                                            })
                                  }
                              Spacer(Modifier.height(Dimensions.Spacing.medium))
                            }
                      }
                }
          }

          // --- Scale bar (bottom-right, left RECENTER button, appears on zoom) ---
          AnimatedVisibility(
              visible = showScaleBar,
              enter = fadeIn(),
              exit = fadeOut(),
              modifier =
                  Modifier.align(Alignment.BottomEnd)
                      .padding(
                          end =
                              if (permissionGranted && userLocation != null) {
                                // Shift scale bar to the left when recenter button is visible
                                Dimensions.Padding.medium
                                    .plus(Dimensions.ButtonSize.standard)
                                    .plus(Dimensions.Padding.small)
                              } else {
                                // No recenter button → align scale bar directly at bottom-right
                                Dimensions.Padding.medium
                              },
                          bottom = Dimensions.Padding.medium)) {
                MapScaleBar(
                    latitude = cameraPositionState.position.target.latitude,
                    zoomLevel = currentZoom)
              }

          // --- Recenter button (bottom-right) ---
          if (permissionGranted && userLocation != null) {
            FloatingActionButton(
                onClick = {
                  coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(userLocation!!.latitude, userLocation!!.longitude),
                            DEFAULT_ZOOM_LEVEL))
                  }
                },
                containerColor = AppColors.neutral,
                contentColor = AppColors.textIcons,
                shape = CircleShape,
                modifier =
                    Modifier.testTag(MapScreenTestTags.RECENTER_BUTTON)
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = Dimensions.Padding.medium, bottom = Dimensions.Padding.medium)
                        .size(Dimensions.ButtonSize.standard)) {
                  Icon(Icons.Default.MyLocation, contentDescription = "Recenter")
                }
          }
        }
      }

  /** Controls bottom sheet visibility based on any selection (marker or cluster). */
  LaunchedEffect(uiState.selectedMarkerPreview, uiState.selectedClusterPreviews) {
    val shouldShow =
        uiState.selectedMarkerPreview != null || uiState.selectedClusterPreviews != null
    if (shouldShow) {
      coroutineScope.launch { sheetState.show() }
    } else {
      if (sheetState.isVisible) {
        coroutineScope.launch { sheetState.hide() }
      }
    }
  }
}

/**
 * Maps each PinType to its corresponding test tag used in UI tests. Used to assign stable and
 * readable tags to dynamically generated FilterChips.
 *
 * @param type the PinType for which to get the test tag
 * @return the test tag string associated with the given PinType
 */
private fun pinTypeTestTag(type: PinType): String =
    when (type) {
      PinType.SHOP -> MapScreenTestTags.FILTER_SHOP_CHIP
      PinType.SPACE -> MapScreenTestTags.FILTER_SPACE_CHIP
      PinType.SESSION -> MapScreenTestTags.FILTER_SESSIONS_CHIP
    }

/**
 * Attempts to retrieve the user's last known location using the [FusedLocationProviderClient].
 *
 * This function does not request permission by itself, so it should only be called after ensuring
 * that location permissions are granted.
 *
 * @param fusedClient the FusedLocationProviderClient used to fetch the last location
 * @return a [Location] representing the user's coordinates, or null if unavailable
 */
@SuppressLint("MissingPermission")
private suspend fun getUserLocation(fusedClient: FusedLocationProviderClient): Location? {
  return try {
    val loc: AndroidLocation? = fusedClient.lastLocation.await()
    loc?.let { Location(it.latitude, it.longitude, "User Location") }
  } catch (_: SecurityException) {
    null
  } catch (_: Exception) {
    null
  }
}

/**
 * Opens Google Maps in navigation mode toward a given latitude/longitude.
 *
 * This uses the `google.navigation:` URI scheme to directly launch turn-by-turn navigation if
 * Google Maps is installed. If the Maps app is not available, the call is safely ignored.
 *
 * @param lat The destination latitude.
 * @param lng The destination longitude.
 */
private fun Context.openGoogleMapsDirections(lat: Double, lng: Double) {
  val uri = "google.navigation:q=$lat,$lng".toUri()
  val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
  if (intent.resolveActivity(packageManager) != null) {
    startActivity(intent)
  }
}

/**
 * Calculates the distance between two geographic coordinates using the Haversine formula.
 *
 * @param lat1 Latitude of the first point in degrees.
 * @param lng1 Longitude of the first point in degrees.
 * @param lat2 Latitude of the second point in degrees.
 * @param lng2 Longitude of the second point in degrees.
 * @return Distance between the two points in kilometers.
 */
private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
  val r = 6371.0 // Earth radius
  val dLat = Math.toRadians(lat2 - lat1)
  val dLng = Math.toRadians(lng2 - lng1)
  val lat1 = Math.toRadians(lat1)
  val lat2 = Math.toRadians(lat2)

  val haversine = sin(dLat / 2).pow(2.0) + sin(dLng / 2).pow(2.0) * cos(lat1) * cos(lat2)
  return 2 * r * asin(sqrt(haversine))
}

/**
 * Extension function for GeoPinWithLocation to compute the distance to a given location.
 *
 * @param location Target location.
 * @return Distance to the target location in kilometers.
 */
private fun GeoPinWithLocation.distanceTo(location: Location): Double {
  return distanceKm(
      this.location.latitude, this.location.longitude, location.latitude, location.longitude)
}

/**
 * Returns a user-friendly string representation of the distance from this GeoPinWithLocation to the
 * specified location.
 *
 * Distances below 1 km are shown in meters, otherwise in kilometers with 1 decimal.
 *
 * @param location Target location.
 * @return Distance as a formatted string, e.g., "500 m" or "2.3 km".
 */
private fun GeoPinWithLocation.distanceToString(location: Location): String {
  val km = distanceTo(location)
  return if (km < 1.0) "${(km * 1000).toInt()} m"
  else String.format(Locale.getDefault(), "%.1f km", km)
}

/**
 * Displays a simple loading sheet while fetching marker preview data.
 *
 * Shows a centered text and a circular progress indicator. The text varies depending on the type of
 * the pin being loaded, or shows "Loading cluster..." if loading a cluster (geoPin is null).
 *
 * @param pin the [GeoPinWithLocation] for which the preview is loading
 */
@Composable
private fun MarkerPreviewLoadingSheet(pin: GeoPinWithLocation?) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(Dimensions.Padding.extraLarge)
              .testTag(MapScreenTestTags.MARKER_PREVIEW_SHEET),
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text =
                when (pin?.geoPin?.type) {
                  PinType.SHOP -> "Loading shop..."
                  PinType.SPACE -> "Loading space..."
                  PinType.SESSION -> "Loading session..."
                  null -> "Loading cluster..."
                },
            style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(Dimensions.Spacing.extraLarge))

        CircularProgressIndicator()
      }
}

/**
 * MarkerPreviewSheet displays contextual details about a selected map pin.
 *
 * Content varies by pin type:
 * - Shop / Space:
 *     - Address with location icon
 *     - Opening status with clock icon (colored green/red)
 * - Session:
 *     - Game name with controller icon
 *     - Address with location icon
 *     - Date with calendar icon
 *
 * The sheet includes:
 * - A close button (top-right)
 * - A "View details" button
 * - A "Navigate" button opening Google Maps directions
 */
@Composable
private fun MarkerPreviewSheet(
    preview: MarkerPreview,
    onClose: () -> Unit,
    pin: GeoPinWithLocation,
    userLocation: Location?,
    onRedirect: (StorableGeoPin) -> Unit
) {
  val context = LocalContext.current

  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(Dimensions.Padding.extraLarge)
              .testTag(MapScreenTestTags.MARKER_PREVIEW_SHEET)) {
        Box(modifier = Modifier.fillMaxWidth()) {
          val title =
              if (userLocation != null) "${preview.name} — ${pin.distanceToString(userLocation)}"
              else preview.name
          Text(
              text = title,
              style = MaterialTheme.typography.titleLarge,
              modifier =
                  Modifier.align(Alignment.CenterStart).testTag(MapScreenTestTags.PREVIEW_TITLE))

          IconButton(
              onClick = onClose,
              modifier =
                  Modifier.align(Alignment.TopEnd)
                      .testTag(MapScreenTestTags.PREVIEW_CLOSE_BUTTON)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close preview")
              }
        }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

        when (preview) {
          is MarkerPreview.ShopMarkerPreview,
          is MarkerPreview.SpaceMarkerPreview -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location")
              Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
              Text(
                  text = preview.address,
                  modifier = Modifier.testTag(MapScreenTestTags.PREVIEW_ADDRESS))
            }

            Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Opening hours")
              Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
              val isOpen =
                  when (preview) {
                    is MarkerPreview.ShopMarkerPreview -> preview.open
                    is MarkerPreview.SpaceMarkerPreview -> preview.open
                    else -> false
                  }
              Text(
                  text = if (isOpen) "Open" else "Closed",
                  color =
                      if (isOpen) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.error,
                  modifier = Modifier.testTag(MapScreenTestTags.PREVIEW_OPENING_HOURS))
            }
          }
          is MarkerPreview.SessionMarkerPreview -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.SportsEsports, contentDescription = "Game")
              Spacer(modifier = Modifier.width(Dimensions.Spacing.small))
              Text(
                  text = "Playing: ${preview.game}",
                  modifier = Modifier.alignByBaseline().testTag(MapScreenTestTags.PREVIEW_GAME))
            }

            Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location")
              Spacer(modifier = Modifier.width(Dimensions.Spacing.small))
              Text(
                  text = preview.address,
                  modifier = Modifier.testTag(MapScreenTestTags.PREVIEW_ADDRESS))
            }

            Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Date")
              Spacer(modifier = Modifier.width(Dimensions.Spacing.small))
              Text(text = preview.date, modifier = Modifier.testTag(MapScreenTestTags.PREVIEW_DATE))
            }
          }
        }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.large))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          Button(
              onClick = { onRedirect(pin.geoPin) },
              modifier = Modifier.testTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON)) {
                Text(text = "View details")
              }

          Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))

          Button(
              onClick = {
                context.openGoogleMapsDirections(pin.location.latitude, pin.location.longitude)
              },
              modifier = Modifier.testTag(MapScreenTestTags.PREVIEW_NAVIGATE_BUTTON)) {
                Text(text = "Go To")
              }
        }
      }
}

/**
 * Displays a list of multiple marker previews as a cluster sheet.
 *
 * @param clusterPreviews list of geo pins with their preview data
 * @param onSelectPreview callback when a cluster item is selected
 */
@Composable
private fun ClusterPreviewSheet(
    clusterPreviews: List<Pair<GeoPinWithLocation, MarkerPreview>>,
    userLocation: Location?,
    onSelectPreview: (GeoPinWithLocation, MarkerPreview) -> Unit
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(Dimensions.Padding.large)
              .testTag(MapScreenTestTags.CLUSTER_SHEET)) {
        Text(text = "Multiple locations", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
          items(clusterPreviews) { (pin, preview) ->
            ClusterPreviewItem(
                preview = preview,
                pin = pin,
                userLocation = userLocation,
                onClick = { onSelectPreview(pin, preview) },
                testTag = MapScreenTestTags.getTestTagForClusterItem(pin.geoPin.uid))
          }
        }
      }
}

/**
 * Single item in a cluster preview list.
 *
 * Displays icon based on pin type, name, and game if session.
 *
 * @param preview marker preview data
 * @param onClick callback when the item is clicked
 * @param testTag UI testing tag
 */
@Composable
private fun ClusterPreviewItem(
    preview: MarkerPreview,
    pin: GeoPinWithLocation,
    userLocation: Location?,
    onClick: () -> Unit,
    testTag: String
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable { onClick() }
              .padding(Dimensions.Padding.medium)
              .testTag(testTag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          val icon =
              when (preview) {
                is MarkerPreview.ShopMarkerPreview -> Icons.Default.Storefront
                is MarkerPreview.SpaceMarkerPreview -> Icons.Default.TableRestaurant
                is MarkerPreview.SessionMarkerPreview -> Icons.Default.SportsEsports
              }

          Icon(
              imageVector = icon,
              contentDescription = null,
              modifier = Modifier.size(Dimensions.IconSize.large))

          Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))

          Column {
            Text(text = preview.name, style = MaterialTheme.typography.bodyLarge)

            if (preview is MarkerPreview.SessionMarkerPreview) {
              Text(
                  text = preview.game,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        // Distance added to the end
        userLocation?.let {
          Text(
              text = pin.distanceToString(it),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
}

/**
 * Creates a custom Google Maps marker icon from a drawable resource.
 *
 * The icon is drawn on a colored circle background with configurable scale, tint, and alpha. This
 * allows easy distinction between different pin types while maintaining legibility on the map.
 *
 * @param resId drawable resource ID of the marker icon
 * @param scale scaling factor applied to the drawable (default 1.5x)
 * @param tint color applied to the drawable icon (default [AppColors.neutral])
 * @param backgroundTint color of the circle background behind the icon (default
 *   [AppColors.primary])
 * @param backgroundAlpha alpha transparency of the background circle (0f = fully transparent, 1f =
 *   opaque)
 * @return a [BitmapDescriptor] usable as a Google Maps marker icon
 */
@Composable
private fun rememberMarkerIcon(
    @DrawableRes resId: Int,
    scale: Float = DEFAULT_MARKER_SCALE,
    tint: Color = AppColors.neutral,
    backgroundTint: Color = AppColors.primary,
    backgroundAlpha: Float = DEFAULT_MARKER_BACKGROUND_ALPHA
): BitmapDescriptor {
  val context = LocalContext.current

  return remember(resId, scale) {
    val drawable = AppCompatResources.getDrawable(context, resId) ?: error("Drawable not found")

    val width = (drawable.intrinsicWidth * scale).toInt()
    val height = (drawable.intrinsicHeight * scale).toInt()

    // Main bitmap
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    // Circle background
    val paint =
        Paint().apply {
          color = backgroundTint.toArgb()
          alpha = (RGB_MAX_ALPHA * backgroundAlpha).toInt()
          isAntiAlias = true
        }
    val radius = min(width, height) / 2f
    canvas.drawCircle(width / 2f, height / 2f, radius, paint)

    // Draw icon on background
    drawable.setTint(tint.toArgb())
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)

    BitmapDescriptorFactory.fromBitmap(bitmap)
  }
}

/**
 * Creates a custom Google Maps cluster icon with the cluster size displayed.
 *
 * The icon is a circle whose color depends on the cluster size (small/medium/large), and the size
 * number is drawn centered in white text.
 *
 * @param size number of items in the cluster
 * @param diameterDp diameter of the cluster icon in dp (default is 32dp)
 * @return a [BitmapDescriptor] usable as a Google Maps marker icon
 */
@Composable
private fun rememberClusterIcon(size: Int, diameterDp: Int = 32): BitmapDescriptor {
  val density = LocalDensity.current

  return remember(size, diameterDp) {
    val diameterPx = with(density) { diameterDp.dp.toPx() }.toInt()
    val radius = diameterPx / 2f

    // Create a bitmap
    val bitmap = createBitmap(diameterPx, diameterPx)
    val canvas = Canvas(bitmap)

    // Background circle
    val paint =
        Paint().apply {
          isAntiAlias = true
          color = ClusterConfig.getColorForSize(size).toArgb()
          style = Paint.Style.FILL
        }
    canvas.drawCircle(radius, radius, radius, paint)

    // Draw auto-sized text (size number)
    val digits = size.toString().length
    val textFactor =
        when (digits) {
          1 -> 0.50f
          2 -> 0.40f
          3 -> 0.32f
          else -> 0.28f
        }

    val textPaint =
        Paint().apply {
          color = Color.White.toArgb()
          textSize = diameterPx * textFactor
          textAlign = Paint.Align.CENTER
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
          isAntiAlias = true
        }

    val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(size.toString(), radius, textY, textPaint)

    BitmapDescriptorFactory.fromBitmap(bitmap)
  }
}

/**
 * Displays a map scale bar (metric only).
 * - Metric label (m/km) shown below the bar
 * - Values rounded to human-friendly steps (1, 2, 5 × 10^n)
 * - Design: horizontal line ending with a single downward tick at the left end (no upward tick).
 * - Transparent background so the map remains fully visible.
 */
@Composable
private fun MapScaleBar(latitude: Double, zoomLevel: Float) {
  val metersPerPixel = 156543.03392 * cos(latitude * Math.PI / 180) / (1 shl zoomLevel.toInt())
  val rawDistanceMeters = metersPerPixel * MapScaleBarDefaults.BAR_WIDTH
  val distanceMeters = roundDistance(rawDistanceMeters)

  val metricLabel =
      if (distanceMeters >= 1000) "${(distanceMeters / 1000).toInt()} km"
      else "${distanceMeters.toInt()} m"

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.testTag(MapScreenTestTags.SCALE_BAR)) {
        // Horizontal bar with downward tick at the left end
        Box {
          // Main horizontal line
          Box(
              Modifier.width(MapScaleBarDefaults.BAR_WIDTH.dp)
                  .height(MapScaleBarDefaults.LINE_HEIGHT)
                  .background(AppColors.textIcons))
          // Downward tick aligned to the left end of the line
          Box(
              Modifier.align(Alignment.BottomStart)
                  .height(MapScaleBarDefaults.TICK_HEIGHT)
                  .width(MapScaleBarDefaults.TICK_WIDTH)
                  .background(AppColors.textIcons))
        }

        Spacer(Modifier.height(MapScaleBarDefaults.LABEL_SPACING))
        Text(
            metricLabel,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.textIcons,
            modifier = Modifier.testTag(MapScreenTestTags.SCALE_BAR_DISTANCE))
      }
}

/**
 * Rounds a raw distance in meters to a human-friendly value.
 *
 * Uses steps 1, 2, or 5 multiplied by powers of 10 (e.g., 3200 m → 5 km; 180 m → 200 m).
 *
 * @param meters Raw distance in meters
 * @return Rounded distance in meters
 */
private fun roundDistance(meters: Double): Double {
  val pow10 = 10.0.pow(floor(log10(meters)))
  val normalized = meters / pow10
  val rounded =
      when {
        normalized < 2 -> 1.0
        normalized < 5 -> 2.0
        else -> 5.0
      }
  return rounded * pow10
}

/**
 * Static vertical menu for map controls.
 *
 * Displays filter and add buttons stacked vertically.
 *
 * @param modifier Modifier for the menu container
 * @param showCreateButton Whether to show the create/add button
 * @param onToggleFilters Callback when filter button is clicked
 * @param onAddClick Callback when add button is clicked
 */
@Composable
private fun StaticVerticalMapMenu(
    modifier: Modifier = Modifier,
    showCreateButton: Boolean,
    onToggleFilters: () -> Unit,
    onAddClick: () -> Unit
) {
  Column(
      modifier = modifier.testTag(MapScreenTestTags.BUTTON_MENU),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
        // Filter button - circular
        FloatingActionButton(
            onClick = onToggleFilters,
            containerColor = AppColors.neutral,
            contentColor = AppColors.textIcons,
            shape = CircleShape,
            modifier =
                Modifier.testTag(MapScreenTestTags.FILTER_BUTTON)
                    .size(Dimensions.ButtonSize.standard)) {
              Icon(Icons.Default.FilterList, contentDescription = "Filter pins")
            }

        // Add button (visible only for owners/renters)
        if (showCreateButton) {
          FloatingActionButton(
              onClick = onAddClick,
              containerColor = AppColors.neutral,
              contentColor = AppColors.textIcons,
              shape = CircleShape,
              modifier =
                  Modifier.testTag(MapScreenTestTags.ADD_FAB)
                      .size(Dimensions.ButtonSize.standard)) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = "Create")
              }
        }
      }
}

/**
 * Estimates a fallback geographic location based on the device timezone.
 *
 * Provides broad continental centers (e.g., Europe, Asia) or regional defaults for America (East
 * Coast, West Coast, Central). Used when user location permission is denied, so the map can still
 * be initialized.
 *
 * @return Approximate [Location] for the current timezone
 */
internal fun getApproximateLocationFromTimezone(): Location {
  val timeZone = TimeZone.getDefault().id

  return when {
    timeZone.startsWith("Europe/") -> {
      Location(50.0, 10.0, "Europe")
    }
    timeZone.startsWith("America/") -> {
      when {
        timeZone.contains("New_York") || timeZone.contains("Toronto") ->
            Location(40.0, -75.0, "East Coast")
        timeZone.contains("Los_Angeles") || timeZone.contains("Vancouver") ->
            Location(37.0, -120.0, "West Coast")
        timeZone.contains("Chicago") || timeZone.contains("Mexico") ->
            Location(35.0, -95.0, "Central")
        else -> Location(40.0, -100.0, "Americas")
      }
    }
    timeZone.startsWith("Asia/") -> Location(35.0, 105.0, "Asia")
    timeZone.startsWith("Africa/") -> Location(0.0, 20.0, "Africa")
    timeZone.startsWith("Australia/") -> Location(-25.0, 135.0, "Australia")
    else -> Location(0.0, 0.0, "World")
  }
}
