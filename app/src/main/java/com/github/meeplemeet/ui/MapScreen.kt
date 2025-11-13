@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location as AndroidLocation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.R
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.MarkerPreview
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPin
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.min
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object MapScreenTestTags {
  const val GOOGLE_MAP_SCREEN = "mapScreen"
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

  fun getTestTagForPin(pinId: String) = "mapPin_$pinId"
}

private val DEFAULT_CENTER = Location(46.5183, 6.5662, "EPFL")
private const val DEFAULT_RADIUS_KM = 10.0
private const val DEFAULT_ZOOM_LEVEL = 14f
private const val CAMERA_CENTER_DEBOUNCE_MS = 1000L
private const val DEFAULT_MARKER_SCALE = 1.5f
private const val DEFAULT_MARKER_BACKGROUND_ALPHA = 1.0f
private const val RGB_MAX_ALPHA = 255

/**
 * MapScreen displays an interactive Google Map centered on the user's location (if granted) or
 * defaults to EPFL. It dynamically loads pins (shops, spaces, sessions) from Firestore through the
 * [MapViewModel].
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
    viewModel: MapViewModel = viewModel(),
    navigation: NavigationActions,
    account: Account,
    onFABCLick: (PinType) -> Unit,
    onRedirect: (StorableGeoPin) -> Unit
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
        if (granted) {
          permissionGranted = true
        } else {
          val coarseGranted =
              ContextCompat.checkSelfPermission(
                  context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                  PackageManager.PERMISSION_GRANTED
          permissionGranted = coarseGranted
        }
        permissionChecked = true
      }

  // Observe lifecycle to refresh location when returning to the app
  val lifecycleState by
      LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

  // --- Map & query state ---
  var userLocation by remember { mutableStateOf<Location?>(null) }
  var isLoadingLocation by remember { mutableStateOf(true) }
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
    val fine =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val coarse =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    when {
      fine -> permissionGranted = true
      coarse -> permissionGranted = true
      else -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    permissionChecked = true
  }

  /**
   * Retrieves user location whenever:
   * - permission state changes, or
   * - lifecycle returns to RESUMED (i.e., app resumes).
   *
   * If location retrieval fails or permission denied, falls back to EPFL.
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

    userLocation = loc ?: DEFAULT_CENTER
    isLoadingLocation = false
  }

  /**
   * Starts the Firestore geo query once a valid user location is obtained. Runs only once to avoid
   * restarting the query unnecessarily.
   */
  LaunchedEffect(userLocation) {
    if (userLocation == null) return@LaunchedEffect

    viewModel.startGeoQuery(
        center = userLocation!!, currentUserId = account.uid, radiusKm = DEFAULT_RADIUS_KM)
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
        BottomNavigationMenu(
            currentScreen = MeepleMeetScreen.Map,
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

        // --- Marker preview bottom sheet ---
        uiState.selectedGeoPin?.let { geoPin ->
          ModalBottomSheet(
              sheetState = sheetState, onDismissRequest = { viewModel.clearSelectedPin() }) {
                when {
                  uiState.isLoadingPreview -> {
                    MarkerPreviewLoadingSheet(geoPin = geoPin)
                  }
                  uiState.selectedMarkerPreview != null -> {
                    MarkerPreviewSheet(
                        preview = uiState.selectedMarkerPreview!!,
                        onClose = { viewModel.clearSelectedPin() },
                        geoPin = uiState.selectedGeoPin!!,
                        onRedirect = onRedirect)
                  }
                }
              }
        }

        // --- Map rendering ---
        val cameraPositionState = rememberCameraPositionState()

        /** Center the camera on user location when it changes (first load or refresh). */
        LaunchedEffect(userLocation) {
          userLocation?.let {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(it.latitude, it.longitude), DEFAULT_ZOOM_LEVEL))
          }
        }

        /**
         * Watches camera movement and updates Firestore query center after a short debounce.
         * Ensures live results around current map area.
         */
        LaunchedEffect(cameraPositionState) {
          snapshotFlow { cameraPositionState.position.target }
              .debounce(CAMERA_CENTER_DEBOUNCE_MS)
              .collect { latLng ->
                viewModel.updateQueryCenter(Location(latLng.latitude, latLng.longitude))
              }
        }

        // --- Google Map content ---
        Box(modifier = Modifier.fillMaxSize()) {
          val isDarkTheme = isSystemInDarkTheme()
          val mapStyleOptions =
              if (isDarkTheme) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
              } else {
                null
              }

          val shopIcon = rememberMarkerIcon(resId = R.drawable.ic_storefront)
          val spaceIcon = rememberMarkerIcon(resId = R.drawable.ic_table)
          val sessionIcon = rememberMarkerIcon(resId = R.drawable.ic_dice)

          GoogleMap(
              modifier =
                  Modifier.fillMaxSize()
                      .padding(innerPadding)
                      .testTag(MapScreenTestTags.GOOGLE_MAP_SCREEN),
              cameraPositionState = cameraPositionState,
              properties = MapProperties(mapStyleOptions = mapStyleOptions)) {
                val pinPriority = listOf(PinType.SESSION, PinType.SHOP, PinType.SPACE)

                val uniquePins =
                    uiState.geoPins
                        .groupBy { it.location }
                        .mapValues { (_, pins) ->
                          pins.minByOrNull { pinPriority.indexOf(it.geoPin.type) }!!
                        }
                        .values

                uniquePins.forEach { gp ->
                  val pos = LatLng(gp.location.latitude, gp.location.longitude)
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
                        viewModel.selectPin(gp)
                        true
                      },
                      icon = icon,
                      tag = MapScreenTestTags.getTestTagForPin(gp.geoPin.uid))
                }
              }

          // --- FILTER BUTTON + vertical list of filter chips (top-start) ---
          Box(
              modifier =
                  Modifier.align(Alignment.TopStart)
                      .padding(start = Dimensions.Padding.medium, top = Dimensions.Padding.medium)
                      .wrapContentSize()) {
                // Main filter FAB
                FloatingActionButton(
                    onClick = { showFilterButtons = !showFilterButtons },
                    containerColor = AppColors.neutral,
                    contentColor = AppColors.textIcons,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    modifier =
                        Modifier.testTag(MapScreenTestTags.FILTER_BUTTON)
                            .size(Dimensions.ButtonSize.standard)) {
                      Icon(Icons.Default.FilterList, contentDescription = "Filter pins")
                    }

                // Panel with FilterChips
                AnimatedVisibility(
                    visible = showFilterButtons,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart)) {
                      Surface(
                          modifier =
                              Modifier.padding(top = Dimensions.Padding.giant)
                                  .widthIn(max = 120.dp)
                                  .wrapContentHeight()
                                  .shadow(6.dp, RoundedCornerShape(Dimensions.CornerRadius.large)),
                          tonalElevation = Dimensions.Elevation.high,
                          shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
                            Column(
                                modifier =
                                    Modifier.padding(
                                        horizontal = Dimensions.Padding.medium,
                                        vertical = Dimensions.Padding.mediumSmall),
                                verticalArrangement =
                                    Arrangement.spacedBy(Dimensions.Spacing.small)) {
                                  PinType.entries.forEach { type ->
                                    val selected = includeTypes.contains(type)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                          includeTypes =
                                              if (selected) includeTypes - type
                                              else includeTypes + type
                                        },
                                        label = {
                                          Text(
                                              text =
                                                  type.name.lowercase().replaceFirstChar {
                                                    it.uppercaseChar()
                                                  },
                                              style = MaterialTheme.typography.labelLarge)
                                        },
                                        leadingIcon = {
                                          Checkbox(
                                              checked = selected,
                                              onCheckedChange = null,
                                              modifier = Modifier.size(Dimensions.IconSize.medium))
                                        },
                                        modifier =
                                            Modifier.testTag(pinTypeTestTag(type))
                                                .height(Dimensions.Padding.huge)
                                                .fillMaxWidth())
                                  }
                                }
                          }
                    }
              }

          // --- Add button for shop/space owners ---
          if (account.shopOwner || account.spaceRenter) {
            FloatingActionButton(
                onClick = {
                  when (account.shopOwner to account.spaceRenter) {
                    true to true -> showCreateDialog = true
                    true to false -> onFABCLick(PinType.SHOP)
                    false to true -> onFABCLick(PinType.SPACE)
                    else -> {}
                  }
                },
                contentColor = AppColors.textIcons,
                containerColor = AppColors.neutral,
                shape = CircleShape,
                modifier =
                    Modifier.testTag(MapScreenTestTags.ADD_FAB)
                        .align(Alignment.TopEnd)
                        .padding(
                            top = Dimensions.Padding.medium, end = Dimensions.Padding.medium)) {
                  Icon(Icons.Default.AddLocationAlt, contentDescription = "Create")
                }
          }

          if (showCreateDialog) {
            Dialog(
                onDismissRequest = {
                  showCreateDialog = false
                  selectedCreateType = null
                }) {
                  Surface(
                      shape = RoundedCornerShape(Dimensions.CornerRadius.extraLarge),
                      color = MaterialTheme.colorScheme.surface,
                      tonalElevation = Dimensions.Elevation.xxHigh,
                      modifier =
                          Modifier.testTag(MapScreenTestTags.ADD_CHOOSE_DIALOG)
                              .widthIn(min = 300.dp, max = 420.dp)
                              .wrapContentHeight()
                              .border(
                                  1.dp,
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
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
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
                                            activeContainerColor = AppColors.focus),
                                    border = BorderStroke(1.dp, AppColors.textIcons),
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
        }
      }

  /** Controls bottom sheet visibility based on whether a marker preview is available. */
  LaunchedEffect(uiState.selectedMarkerPreview) {
    if (uiState.selectedMarkerPreview != null) {
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
 * Displays a simple loading sheet while fetching marker preview data.
 *
 * Shows a centered text and a circular progress indicator. The text varies depending on the type of
 * the pin being loaded.
 *
 * @param geoPin the [StorableGeoPin] for which the preview is loading
 */
@Composable
private fun MarkerPreviewLoadingSheet(geoPin: StorableGeoPin) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(Dimensions.Padding.extraLarge)
              .testTag(MapScreenTestTags.MARKER_PREVIEW_SHEET),
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text =
                when (geoPin.type) {
                  PinType.SHOP -> "Loading shop..."
                  PinType.SPACE -> "Loading space..."
                  PinType.SESSION -> "Loading session..."
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
 * The sheet includes a close icon in the top-right corner.
 */
@Composable
private fun MarkerPreviewSheet(
    preview: MarkerPreview,
    onClose: () -> Unit,
    geoPin: StorableGeoPin,
    onRedirect: (StorableGeoPin) -> Unit
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(Dimensions.Padding.extraLarge)
              .testTag(MapScreenTestTags.MARKER_PREVIEW_SHEET)) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Text(
              text = preview.name,
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

        Button(
            onClick = { onRedirect(geoPin) },
            modifier =
                Modifier.align(Alignment.End)
                    .testTag(MapScreenTestTags.PREVIEW_VIEW_DETAILS_BUTTON)) {
              Text(text = "View details")
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
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
