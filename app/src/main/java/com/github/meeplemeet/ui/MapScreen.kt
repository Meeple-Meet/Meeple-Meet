@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.MarkerPreview
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

object MapScreenTestTags {
  const val GOOGLE_MAP_SCREEN = "mapScreen"
  const val ADD_FAB = "addFab"

  fun getTestTagForPin(pinId: String) = "mapPin_$pinId"
}

private val START_CENTER = Location(46.5183, 6.5662, "EPFL")
private const val START_RADIUS_KM = 10.0

/**
 * Simple MapScreen:
 * - shows markers for each GeoPinWithLocation (coming from viewModel.uiState.geoPins)
 * - on marker click: requests preview via viewModel.selectPin(pin) and displays bottom sheet with
 *   preview
 * - shows snackbar on errors
 */
@OptIn(FlowPreview::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    navigation: NavigationActions,
    account: Account,
    onFABCLick: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Launch initial query
  LaunchedEffect(Unit) {
    viewModel.startGeoQuery(center = START_CENTER, radiusKm = START_RADIUS_KM)
  }

  // Show errors in snackbar
  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = MeepleMeetScreen.Map.title,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
            })
      },
      bottomBar = {
        BottomNavigationMenu(
            currentScreen = MeepleMeetScreen.Map,
            onTabSelected = { screen -> navigation.navigateTo(screen) })
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding ->
        if (uiState.selectedMarkerPreview != null) {
          ModalBottomSheet(
              sheetState = sheetState, onDismissRequest = { viewModel.clearSelectedPin() }) {
                MarkerPreviewSheet(
                    preview = uiState.selectedMarkerPreview!!,
                    onClose = { viewModel.clearSelectedPin() })
              }
        }

        // Map
        val cameraPositionState = rememberCameraPositionState()
        LaunchedEffect(Unit) {
          cameraPositionState.move(
              CameraUpdateFactory.newLatLngZoom(
                  LatLng(START_CENTER.latitude, START_CENTER.longitude), 14f))
        }

        LaunchedEffect(cameraPositionState) {
          snapshotFlow { cameraPositionState.position.target }
              .debounce(1000)
              .collect { latLng ->
                viewModel.updateQueryCenter(Location(latLng.latitude, latLng.longitude))
              }
        }

        Box(modifier = Modifier.fillMaxSize()) {
          GoogleMap(
              modifier =
                  Modifier.fillMaxSize()
                      .padding(innerPadding)
                      .testTag(MapScreenTestTags.GOOGLE_MAP_SCREEN),
              cameraPositionState = cameraPositionState) {
                uiState.geoPins.forEach { gp ->
                  val pos = LatLng(gp.location.latitude, gp.location.longitude)
                  // TODO add better customization icon
                  val hue =
                      when (gp.geoPin.type) {
                        PinType.SHOP -> BitmapDescriptorFactory.HUE_RED
                        PinType.SPACE -> BitmapDescriptorFactory.HUE_BLUE
                        PinType.SESSION -> BitmapDescriptorFactory.HUE_GREEN
                      }
                  Marker(
                      state = MarkerState(pos),
                      title = gp.geoPin.uid,
                      snippet = gp.geoPin.type.name,
                      onClick = {
                        viewModel.selectPin(gp)
                        true
                      },
                      icon = BitmapDescriptorFactory.defaultMarker(hue),
                      tag = MapScreenTestTags.getTestTagForPin(gp.geoPin.uid))
                }
              }
          if (account.shopOwner || account.spaceRenter) {
            FloatingActionButton(
                onClick = onFABCLick,
                contentColor = AppColors.textIcons,
                containerColor = AppColors.neutral,
                modifier =
                    Modifier.testTag(MapScreenTestTags.ADD_FAB)
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp)) {
                  Icon(Icons.Default.Add, contentDescription = "Create")
                }
          }
        }
      }

  // Close sheet when preview cleared
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

@Composable
private fun MarkerPreviewSheet(preview: MarkerPreview, onClose: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Text(
          text = preview.name,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.align(Alignment.CenterStart))

      IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Close preview")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (preview) {
      is MarkerPreview.ShopMarkerPreview,
      is MarkerPreview.SpaceMarkerPreview -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location")
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = preview.address)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Opening hours")
          Spacer(modifier = Modifier.width(8.dp))
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
                  else MaterialTheme.colorScheme.error)
        }
      }
      is MarkerPreview.SessionMarkerPreview -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(imageVector = Icons.Default.SportsEsports, contentDescription = "Game")
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Playing: ${preview.game}", modifier = Modifier.alignByBaseline())
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location")
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = preview.address)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Date")
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = preview.date)
        }
      }
    }
  }
}
