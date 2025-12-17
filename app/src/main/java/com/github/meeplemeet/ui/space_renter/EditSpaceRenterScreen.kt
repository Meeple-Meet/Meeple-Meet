package com.github.meeplemeet.ui.space_renter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.space_renter.EditSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.ui.LocalFocusableFieldObserver
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.components.*
import kotlinx.coroutines.launch

/* ================================================================================================
 * Test tags
 * ================================================================================================ */
/**
 * Contains test tag constants used for identifying UI components in tests for the Edit Space Renter
 * screen.
 */
object EditSpaceRenterScreenTestTags {
  const val SCAFFOLD = "edit_space_renter_scaffold"
  const val TOPBAR = "edit_space_renter_topbar"
  const val TITLE = "edit_space_renter_title"
  const val NAV_BACK = "edit_space_renter_nav_back"
  const val DELETE_BUTTON = "edit_space_renter_delete_button"
  const val DELETE_DIALOG = "edit_space_renter_delete_dialog"
  const val DELETE_CONFIRM = "edit_space_renter_delete_confirm"
  const val DELETE_CANCEL = "edit_space_renter_delete_cancel"
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
/** Holds UI-related constants such as string resources used in the Edit Space Renter screen. */
object EditSpaceRenterUi {
  object Strings {
    const val SCREEN_TITLE = "Edit Space Renter"
    const val REQUIREMENTS_SECTION = "Required Info"
    const val SECTION_AVAILABILITY = "Availability"
    const val SECTION_SPACES = "Available Spaces"
    const val ERROR_VALIDATION = "Validation error"
    const val ERROR_UPDATE = "Failed to update space renter"

    const val DELETE_DIALOG_TITLE = "Delete Space Renter"
    const val DELETE_DIALOG_MESSAGE =
        "Are you sure you want to delete this space renter? This action cannot be undone."
    const val DELETE_CONFIRM = "Delete"
    const val DELETE_CANCEL = "Cancel"
  }
}

data class SpaceRenterValidationState(
    val hasOpeningHours: Boolean,
    val hasAtLeastOneSpace: Boolean,
    val allSpacesValid: Boolean,
    val hasLocation: Boolean
)

@Composable
fun rememberSpaceRenterValidationState(
    week: List<OpeningHours>,
    spaces: List<Space>,
    locationUi: LocationUIState
): SpaceRenterValidationState {
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
  return SpaceRenterValidationState(
      hasOpeningHours, hasAtLeastOneSpace, allSpacesValid, hasLocation)
}

/* ================================================================================================
 * Screen
 * ================================================================================================ */
/**
 * Main entry point for the Edit Space Renter screen.
 *
 * Displays a screen allowing a space renter to edit their information, including required info,
 * availability, and available spaces.
 *
 * @param spaceRenter The initial [SpaceRenter] data being edited.
 * @param owner The [Account] of the current owner editing the space renter.
 * @param onBack Callback invoked when the user navigates back.
 * @param onUpdated Callback invoked when the renter update completes successfully.
 * @param viewModel The [EditSpaceRenterViewModel] handling logic and state for this screen.
 */
@Composable
fun EditSpaceRenterScreen(
    spaceRenter: SpaceRenter,
    online: Boolean,
    owner: Account,
    onBack: () -> Unit,
    onUpdated: () -> Unit,
    viewModel: EditSpaceRenterViewModel = viewModel()
) {
  val locationUi by viewModel.locationUIState.collectAsState()
  val context = LocalContext.current
  val currentSpaceRenter by viewModel.currentSpaceRenter.collectAsStateWithLifecycle()

  // Initialize ViewModel with the space renter
  LaunchedEffect(spaceRenter.id) { viewModel.initialize(spaceRenter) }

  // Use currentSpaceRenter if available, otherwise fall back to parameter
  val activeRenter = currentSpaceRenter ?: spaceRenter

  LaunchedEffect(activeRenter.address) {
    if (locationUi.selectedLocation == null && activeRenter.address != Location()) {
      viewModel.setLocation(activeRenter.address)
    }
  }
  LaunchedEffect(spaceRenter.address) {
    if (locationUi.selectedLocation == null && spaceRenter.address != Location()) {
      viewModel.setLocation(spaceRenter.address)
    }
  }

  EditSpaceRenterContent(
      owner = owner,
      online = online,
      initialRenter = activeRenter,
      onBack = onBack,
      onUpdated = onUpdated,
      onUpdateSpaceRenter = { updated ->
        viewModel.updateSpaceRenter(
            context = context,
            spaceRenter = updated,
            requester = owner,
            owner = updated.owner,
            name = updated.name,
            phone = updated.phone,
            email = updated.email,
            website = updated.website,
            address = updated.address,
            openingHours = updated.openingHours,
            spaces = updated.spaces,
            photoCollectionUrl = updated.photoCollectionUrl)
      },
      locationUi = locationUi,
      viewModel = viewModel)
}

/* ================================================================================================
 * Content
 * ================================================================================================ */
/**
 * Core UI content for the Edit Space Renter screen.
 *
 * Manages UI state (form fields, dialogs, lists) and validation logic, and coordinates saving
 * updates to the renter profile.
 *
 * @param owner The current [Account] editing the renter.
 * @param initialRenter The initial state of the [SpaceRenter].
 * @param onBack Callback for navigation back.
 * @param onUpdated Callback after successful update.
 * @param onUpdateSpaceRenter Suspend function performing the update operation.
 * @param locationUi The [LocationUIState] representing current location selection.
 * @param viewModel The [EditSpaceRenterViewModel] managing state and events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSpaceRenterContent(
    owner: Account,
    online: Boolean,
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
  var photoCollectionUrl by remember { mutableStateOf(initialRenter.photoCollectionUrl) }
  var email by rememberSaveable { mutableStateOf(initialRenter.email) }
  var phone by rememberSaveable { mutableStateOf(initialRenter.phone) }
  var link by rememberSaveable { mutableStateOf(initialRenter.website) }

  var week by remember { mutableStateOf(initialRenter.openingHours) }
  var editingDay by remember { mutableStateOf<Int?>(null) }
  var showHoursDialog by remember { mutableStateOf(false) }

  var spaces by remember { mutableStateOf(initialRenter.spaces) }
  var spacesExpanded by rememberSaveable { mutableStateOf(false) }
  val validation = rememberSpaceRenterValidationState(week, spaces, locationUi)

  var showDeleteDialog by remember { mutableStateOf(false) }

  // Determines whether all required fields are filled and valid.
  val isValid by
      remember(name, email, validation) {
        derivedStateOf {
          name.isNotBlank() &&
              isValidEmail(email) &&
              validation.hasLocation &&
              validation.hasOpeningHours &&
              validation.hasAtLeastOneSpace &&
              validation.allSpacesValid
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
          spaces = spaces,
          photoCollectionUrl = photoCollectionUrl)

  // Adds a new default space to the list and expands the section.
  fun addSpace() {
    spaces =
        spaces +
            Space(
                seats = AddSpaceRenterUi.Numbers.MIN_SEATS_PER_SPACE,
                costPerHour = AddSpaceRenterUi.Numbers.MIN_COST_PER_HOUR)
    spacesExpanded = true
  }

  // Scaffold structure for the screen including top bar, snackbar, and main content.
  var isInputFocused by remember { mutableStateOf(false) }
  var focusedFieldTokens by remember { mutableStateOf(emptySet<Any>()) }
  var isSaving by remember { mutableStateOf(false) }

  CompositionLocalProvider(
      LocalFocusableFieldObserver provides
          { token, focused ->
            focusedFieldTokens =
                if (focused) focusedFieldTokens + token else focusedFieldTokens - token
            isInputFocused = focusedFieldTokens.isNotEmpty()
          }) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                          enabled = !isSaving,
                          modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.NAV_BACK)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                          }
                    },
                    actions = {
                      IconButton(
                          onClick = { showDeleteDialog = true },
                          enabled = !isSaving,
                          modifier =
                              Modifier.testTag(EditSpaceRenterScreenTestTags.DELETE_BUTTON)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete space renter")
                          }
                    },
                    modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.TOPBAR))
              },
              snackbarHost = {
                SnackbarHost(
                    snackbarHost,
                    modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.SNACKBAR_HOST))
              },
              bottomBar = {
                val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
                if (!(shouldHide && isInputFocused))
                    ActionBar(
                        onDiscard = onBack,
                        onPrimary = {
                          isSaving = true
                          scope.launch {
                            try {
                              onUpdateSpaceRenter(draftRenter)
                              onUpdated()
                            } catch (e: IllegalArgumentException) {
                              isSaving = false
                              snackbarHost.showSnackbar(
                                  e.message ?: EditSpaceRenterUi.Strings.ERROR_VALIDATION)
                            } catch (e: Exception) {
                              isSaving = false
                              snackbarHost.showSnackbar(EditSpaceRenterUi.Strings.ERROR_UPDATE)
                            }
                          }
                        },
                        enabled = isValid && !isSaving,
                        primaryButtonText = ShopUiDefaults.StringsMagicNumbers.BTN_SAVE)
              },
              modifier = Modifier.testTag(EditSpaceRenterScreenTestTags.SCAFFOLD)) { padding ->
                LazyColumn(
                    modifier =
                        Modifier.padding(padding).testTag(EditSpaceRenterScreenTestTags.LIST),
                    contentPadding =
                        PaddingValues(
                            horizontal = AddSpaceRenterUi.Dimensions.contentHPadding,
                            vertical = AddSpaceRenterUi.Dimensions.contentVPadding)) {
                      item {
                        if (online) {
                          EditableImageCarousel(
                              photoCollectionUrl = photoCollectionUrl,
                              spacesCount = spaces.size,
                              setPhotoCollectionUrl = { photoCollectionUrl = it })
                        } else
                            ImageCarousel(
                                photoCollectionUrl = photoCollectionUrl,
                                maxNumberOfImages = spaces.size,
                                editable = false)
                      }
                      // Required Info
                      item {
                        CollapsibleSection(
                            title = EditSpaceRenterUi.Strings.REQUIREMENTS_SECTION,
                            initiallyExpanded = true,
                            content = {
                              SpaceRenterRequiredInfoSection(
                                  spaceRenter = draftRenter,
                                  online = online,
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
                            content = {
                              SpacesList(
                                  spaces = spaces,
                                  onChange = { idx, updated ->
                                    spaces =
                                        spaces.mapIndexed { i, sp -> if (i == idx) updated else sp }
                                  },
                                  onDelete = { idx ->
                                    spaces = spaces.filterIndexed { i, _ -> i != idx }
                                  },
                              )
                              AddButton(
                                  onClick = { addSpace() },
                                  buttonText = AddSpaceRenterUi.Strings.BTN_ADD_SPACE,
                                  buttonTestTag = EditSpaceRenterScreenTestTags.SPACES_ADD_BUTTON,
                                  labelTestTag = EditSpaceRenterScreenTestTags.SPACES_ADD_LABEL)
                            },
                            testTag = EditSpaceRenterScreenTestTags.SECTION_SPACES)
                      }

                      item {
                        Spacer(
                            Modifier.height(AddSpaceRenterUi.Dimensions.bottomSpacer)
                                .testTag(EditSpaceRenterScreenTestTags.BOTTOM_SPACER))
                      }
                    }
              }
        }
        if (isSaving) {
          Box(
              modifier =
                  Modifier.fillMaxSize()
                      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                      .clickable(enabled = true, onClick = {}),
              contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
        }
      }

  OpeningHoursEditor(
      show = showHoursDialog,
      day = editingDay,
      week = week,
      onWeekChange = { week = it },
      onDismiss = { showHoursDialog = false })

  ConfirmationDialog(
      show = showDeleteDialog,
      title = EditSpaceRenterUi.Strings.DELETE_DIALOG_TITLE,
      message = EditSpaceRenterUi.Strings.DELETE_DIALOG_MESSAGE,
      confirmText = EditSpaceRenterUi.Strings.DELETE_CONFIRM,
      cancelText = EditSpaceRenterUi.Strings.DELETE_CANCEL,
      onConfirm = {
        showDeleteDialog = false
        viewModel.deleteSpaceRenter(initialRenter, owner)
        onBack()
        onBack()
      },
      onDismiss = { showDeleteDialog = false },
      dialogTestTag = EditSpaceRenterScreenTestTags.DELETE_DIALOG,
      confirmTestTag = EditSpaceRenterScreenTestTags.DELETE_CONFIRM,
      cancelTestTag = EditSpaceRenterScreenTestTags.DELETE_CANCEL)
}
