package com.github.meeplemeet.ui.space_renter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.space_renter.EditSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.ui.components.*
import kotlinx.coroutines.launch

/* ================================================================================================
 * Test tags
 * ================================================================================================ */
object EditSpaceRenterScreenTestTags {
  const val SCAFFOLD = "edit_space_renter_scaffold"
  const val TOPBAR = "edit_space_renter_topbar"
  const val TITLE = "edit_space_renter_title"
  const val NAV_BACK = "edit_space_renter_nav_back"
  const val SNACKBAR_HOST = "edit_space_renter_snackbar_host"
  const val LIST = "edit_space_renter_list"

  const val SECTION_REQUIRED = "edit_section_required"
  const val SECTION_AVAILABILITY = "edit_section_availability"
  const val SECTION_SPACES = "edit_section_spaces"

  // Added for the Spaces header button and label used by tests
  const val SPACES_ADD_BUTTON = "edit_space_renter_spaces_add_button"
  const val SPACES_ADD_LABEL = "edit_space_renter_spaces_add_label"

  const val BOTTOM_SPACER = "edit_bottom_spacer"
}

/* ================================================================================================
 * UI defaults
 * ================================================================================================ */
object EditSpaceRenterUi {
  object Strings {
    const val SCREEN_TITLE = "Edit Space Renter"
    const val REQUIREMENTS_SECTION = "Required Info"
    const val SECTION_AVAILABILITY = "Availability"
    const val SECTION_SPACES = "Available Spaces"

    const val BTN_UPDATE = "Update"
    const val ERROR_VALIDATION = "Validation error"
    const val ERROR_UPDATE = "Failed to update space renter"
  }
}

/* ================================================================================================
 * Screen
 * ================================================================================================ */
@Composable
fun EditSpaceRenterScreen(
    spaceRenter: SpaceRenter,
    owner: Account,
    onBack: () -> Unit,
    onUpdated: () -> Unit,
    viewModel: EditSpaceRenterViewModel = viewModel()
) {
  val locationUi by viewModel.locationUIState.collectAsState()

  LaunchedEffect(spaceRenter.address) {
    if (locationUi.selectedLocation == null && spaceRenter.address != Location()) {
      viewModel.setLocation(spaceRenter.address)
    }
  }

  EditSpaceRenterContent(
      owner = owner,
      initialRenter = spaceRenter,
      onBack = onBack,
      onUpdated = onUpdated,
      onUpdateSpaceRenter = { updated ->
        viewModel.updateSpaceRenter(
            spaceRenter = updated,
            requester = owner,
            owner = updated.owner,
            name = updated.name,
            phone = updated.phone,
            email = updated.email,
            website = updated.website,
            address = updated.address,
            openingHours = updated.openingHours,
            spaces = updated.spaces)
      },
      locationUi = locationUi,
      viewModel = viewModel)
}

/* ================================================================================================
 * Content
 * ================================================================================================ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSpaceRenterContent(
    owner: Account,
    initialRenter: SpaceRenter,
    onBack: () -> Unit,
    onUpdated: () -> Unit,
    onUpdateSpaceRenter: suspend (SpaceRenter) -> Unit,
    locationUi: LocationUIState,
    viewModel: EditSpaceRenterViewModel
) {
  val snackbarHost = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var name by rememberSaveable { mutableStateOf(initialRenter.name) }
  var email by rememberSaveable { mutableStateOf(initialRenter.email) }
  var phone by rememberSaveable { mutableStateOf(initialRenter.phone) }
  var link by rememberSaveable { mutableStateOf(initialRenter.website) }

  var week by remember { mutableStateOf(initialRenter.openingHours) }
  var editingDay by remember { mutableStateOf<Int?>(null) }
  var showHoursDialog by remember { mutableStateOf(false) }

  var spaces by remember { mutableStateOf(initialRenter.spaces) }
  var spacesExpanded by rememberSaveable { mutableStateOf(false) }

  val hasOpeningHours by remember(week) { derivedStateOf { week.any { it.hours.isNotEmpty() } } }
  val hasAtLeastOneSpace by remember(spaces) { derivedStateOf { spaces.isNotEmpty() } }
  val allSpacesValid by
      remember(spaces) {
        derivedStateOf {
          spaces.all {
            it.seats >= AddSpaceRenterUi.Numbers.MIN_SEATS_PER_SPACE &&
                it.costPerHour >= AddSpaceRenterUi.Numbers.MIN_COST_PER_HOUR
          }
        }
      }

  val hasLocation by
      remember(locationUi.selectedLocation) {
        derivedStateOf { locationUi.selectedLocation != null }
      }

  val isValid by
      remember(name, email, hasLocation, hasOpeningHours, hasAtLeastOneSpace, allSpacesValid) {
        derivedStateOf {
          name.isNotBlank() &&
              isValidEmail(email) &&
              hasLocation &&
              hasOpeningHours &&
              hasAtLeastOneSpace &&
              allSpacesValid
        }
      }

  val draftRenter =
      initialRenter.copy(
          name = name,
          phone = phone,
          email = email,
          website = link,
          address = locationUi.selectedLocation ?: initialRenter.address,
          openingHours = week,
          spaces = spaces)

  fun addSpace() {
    spaces =
        spaces +
            Space(
                seats = AddSpaceRenterUi.Numbers.MIN_SEATS_PER_SPACE,
                costPerHour = AddSpaceRenterUi.Numbers.MIN_COST_PER_HOUR)
    spacesExpanded = true
  }

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  EditSpaceRenterUi.Strings.SCREEN_TITLE,
                  modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.TITLE))
            },
            navigationIcon = {
              IconButton(
                  onClick = onBack,
                  modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.NAV_BACK)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                  }
            },
            modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.TOPBAR))
      },
      snackbarHost = {
        SnackbarHost(
            snackbarHost, modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.SNACKBAR_HOST))
      },
      bottomBar = {
        ActionBar(
            onDiscard = onBack,
            onPrimary = {
              scope.launch {
                try {
                  onUpdateSpaceRenter(draftRenter)
                  onUpdated()
                } catch (e: IllegalArgumentException) {
                  snackbarHost.showSnackbar(e.message ?: EditSpaceRenterUi.Strings.ERROR_VALIDATION)
                } catch (e: Exception) {
                  snackbarHost.showSnackbar(EditSpaceRenterUi.Strings.ERROR_UPDATE)
                }
              }
            },
            enabled = isValid,
            primaryButtonText = ShopUiDefaults.StringsMagicNumbers.BTN_SAVE)
      },
      modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.SCAFFOLD)) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).testTag(EditSpaceRenterScreenTestTags.LIST),
            contentPadding =
                PaddingValues(
                    horizontal = AddSpaceRenterUi.Dimensions.contentHPadding,
                    vertical = AddSpaceRenterUi.Dimensions.contentVPadding)) {
              // Required Info
              item {
                CollapsibleSection(
                    title = EditSpaceRenterUi.Strings.REQUIREMENTS_SECTION,
                    initiallyExpanded = true,
                    content = {
                      SpaceRenterRequiredInfoSection(
                          spaceRenter = draftRenter,
                          onSpaceName = { name = it },
                          onEmail = { email = it },
                          onPhone = { phone = it },
                          onLink = { link = it },
                          onPickLocation = { loc -> viewModel.setLocation(loc) },
                          viewModel = viewModel,
                          owner = owner)
                    },
                    testTag = EditSpaceRenterScreenTestTags.SECTION_REQUIRED)
              }

              // Availability
              item {
                CollapsibleSection(
                    title = EditSpaceRenterUi.Strings.SECTION_AVAILABILITY,
                    initiallyExpanded = false,
                    content = {
                      AvailabilitySection(
                          week = week,
                          onEdit = { day ->
                            editingDay = day
                            showHoursDialog = true
                          })
                    },
                    testTag = EditSpaceRenterScreenTestTags.SECTION_AVAILABILITY)
              }

              // Spaces
              item {
                CollapsibleSection(
                    title = EditSpaceRenterUi.Strings.SECTION_SPACES,
                    initiallyExpanded = false,
                    expanded = spacesExpanded,
                    onExpandedChange = { spacesExpanded = it },
                    header = {
                      TextButton(
                          onClick = { addSpace() },
                          modifier =
                              Modifier.testTag(EditSpaceRenterScreenTestTags.SPACES_ADD_BUTTON)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(AddSpaceRenterUi.Dimensions.between))
                            Text(
                                AddSpaceRenterUi.Strings.BTN_ADD_SPACE,
                                modifier =
                                    Modifier.testTag(
                                        EditSpaceRenterScreenTestTags.SPACES_ADD_LABEL))
                          }
                    },
                    content = {
                      SpacesList(
                          spaces = spaces,
                          onChange = { idx, updated ->
                            spaces = spaces.mapIndexed { i, sp -> if (i == idx) updated else sp }
                          },
                          onDelete = { idx -> spaces = spaces.filterIndexed { i, _ -> i != idx } },
                      )
                    },
                    // Use the Edit\_... test tag so tests searching for "edit\_section\_spaces\_*"
                    // succeed
                    testTag = EditSpaceRenterScreenTestTags.SECTION_SPACES)
              }

              item {
                Spacer(
                    Modifier.height(AddSpaceRenterUi.Dimensions.bottomSpacer)
                        .testTag(EditSpaceRenterScreenTestTags.BOTTOM_SPACER))
              }
            }
      }

  OpeningHoursEditor(
      show = showHoursDialog,
      day = editingDay,
      week = week,
      onWeekChange = { week = it },
      onDismiss = { showHoursDialog = false })
}
