@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.map.MapUIState
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.model.map.MarkerPreview
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

object MapScreenTestTags {
  const val GOOGLE_MAP_SCREEN = "mapScreen"

  fun getTestTagForPin(pinId: String) = "mapPin_$pinId"
}

private val START_CENTER = Location(46.5183, 6.5662, "EPFL")
private const val START_RADIUS_KM = 1.0

/**
 * Simple MapScreen:
 * - shows markers for each GeoPinWithLocation (coming from viewModel.uiState.geoPins)
 * - on marker click: requests preview via viewModel.selectPin(pin) and displays bottom sheet with
 *   preview
 * - shows snackbar on errors
 */
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel(), navigationActions: NavigationActions) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiStateFlow =
      remember(viewModel.uiState, lifecycleOwner) {
        viewModel.uiState.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
      }
  val uiState by uiStateFlow.collectAsState(initial = MapUIState())

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
            onTabSelected = { screen -> navigationActions.navigateTo(screen) })
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

        LaunchedEffect(cameraPositionState.isMoving) {
          snapshotFlow { cameraPositionState.position.target }
              .collect { latLng ->
                viewModel.updateQueryCenter(Location(latLng.latitude, latLng.longitude))
              }
        }

        GoogleMap(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .testTag(MapScreenTestTags.GOOGLE_MAP_SCREEN),
            cameraPositionState = cameraPositionState) {
              uiState.geoPins.forEach { gp ->
                val pos = LatLng(gp.location.latitude, gp.location.longitude)
                // TODO add customization
                Marker(
                    state = MarkerState(pos),
                    title = gp.geoPin.uid,
                    snippet = gp.geoPin.type.name,
                    onClick = {
                      viewModel.selectPin(gp)
                      coroutineScope.launch {
                        if (viewModel.uiState.value.selectedMarkerPreview != null) {
                          sheetState.show()
                        }
                      }
                      true
                    },
                    tag = MapScreenTestTags.getTestTagForPin(gp.geoPin.uid))
              }
            }
      }

  // Close sheet when preview cleared
  LaunchedEffect(uiState.selectedMarkerPreview) {
    if (uiState.selectedMarkerPreview == null && sheetState.isVisible) {
      coroutineScope.launch { sheetState.hide() }
    }
  }
}

// TODO add customization
@Composable
private fun MarkerPreviewSheet(preview: MarkerPreview, onClose: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Text(
          text =
              when (preview) {
                is MarkerPreview.ShopMarkerPreview -> preview.name
                is MarkerPreview.SpaceMarkerPreview -> preview.name
                is MarkerPreview.SessionMarkerPreview -> preview.title
              },
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.align(Alignment.CenterStart))

      IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Close preview")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (preview) {
      is MarkerPreview.ShopMarkerPreview -> {
        Text(text = preview.address)
        Text(text = if (preview.open) "Open" else "Closed")
      }
      is MarkerPreview.SpaceMarkerPreview -> {
        Text(text = preview.address)
        Text(text = if (preview.open) "Available" else "Closed")
      }
      is MarkerPreview.SessionMarkerPreview -> {
        Text(text = preview.game)
        Text(text = preview.address)
        Text(text = preview.date)
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
      TextButton(onClick = onClose) { Text("Close") }
    }
  }
}
