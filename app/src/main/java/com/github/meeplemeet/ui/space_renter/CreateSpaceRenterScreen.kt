// This file was initially done by hand and
// then improved and refactored using ChatGPT-5 Extend Thinking
// Docstrings were generated using copilot from Android studio
package com.github.meeplemeet.ui.space_renter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.space_renter.CreateSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.ui.components.*
import kotlinx.coroutines.launch

/* ================================================================================================
 * Test tags
 * ================================================================================================ */
object CreateSpaceRenterScreenTestTags {
  const val SCAFFOLD = "create_space_renter_scaffold"
  const val TOPBAR = "create_space_renter_topbar"
  const val TITLE = "create_space_renter_title"
  const val NAV_BACK = "create_space_renter_nav_back"
  const val SNACKBAR_HOST = "create_space_renter_snackbar_host"
  const val LIST = "create_space_renter_list"

  const val SECTION_REQUIRED = "section_required"
  const val SECTION_AVAILABILITY = "section_availability"
  const val SECTION_SPACES = "section_spaces"

  const val SPACES_ADD_BUTTON = "spaces_add_button"
  const val SPACES_ADD_LABEL = "spaces_add_label"

  const val BOTTOM_SPACER = "bottom_spacer"
}

/* ================================================================================================
 * UI defaults
 * ================================================================================================ */
object AddSpaceRenterUi {
  object Dimensions {
    val contentHPadding = ShopFormUi.Dim.contentHPadding
    val contentVPadding = ShopFormUi.Dim.contentVPadding
    val between = ShopFormUi.Dim.betweenControls
    val bottomSpacer = ShopFormUi.Dim.bottomSpacer
  }

  object Strings {
    const val SCREEN_TITLE = "Create Space Renter"

    const val REQUIREMENTS_SECTION = "Required Info"
    const val SECTION_AVAILABILITY = "Availability"
    const val SECTION_SPACES = "Available Spaces"

    const val BTN_ADD_SPACE = "Add Space"
    const val ERROR_VALIDATION = "Validation error"
    const val ERROR_CREATE = "Failed to create space renter"
  }

  object Numbers {
    const val MIN_SEATS_PER_SPACE = 1
    const val MIN_COST_PER_HOUR = 0.0
  }
}

/* ================================================================================================
 * Screen
 * ================================================================================================ */
@Composable
fun CreateSpaceRenterScreen(
    owner: Account,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateSpaceRenterViewModel = viewModel()
) {
  val locationUi by viewModel.locationUIState.collectAsState()

  AddSpaceRenterContent(
      owner = owner,
      onBack = onBack,
      onCreated = onCreated,
      onCreate = { renter ->
        viewModel.createSpaceRenter(
            owner = owner,
            name = renter.name,
            phone = renter.phone,
            email = renter.email,
            website = renter.website,
            address = renter.address,
            openingHours = renter.openingHours,
            spaces = renter.spaces)
      },
      locationUi = locationUi,
      viewModel = viewModel)
}

/* ================================================================================================
 * Content
 * ================================================================================================ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSpaceRenterContent(
    owner: Account,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onCreate: suspend (SpaceRenter) -> Unit,
    locationUi: LocationUIState,
    viewModel: CreateSpaceRenterViewModel
) {
  val snackbarHost = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var name by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var phone by rememberSaveable { mutableStateOf("") }
  var link by rememberSaveable { mutableStateOf("") }

  var week by remember { mutableStateOf(emptyWeek()) }
  var editingDay by remember { mutableStateOf<Int?>(null) }
  var showHoursDialog by remember { mutableStateOf(false) }

  var spaces by remember { mutableStateOf(listOf<Space>()) }
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

  LaunchedEffect(locationUi.locationQuery) {
    val sel = locationUi.selectedLocation
    if (sel != null && locationUi.locationQuery != sel.name) {
      val typed = locationUi.locationQuery
      viewModel.clearLocationSearch()
      if (typed.isNotBlank()) viewModel.setLocationQuery(typed)
    }
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

  fun discardAndBack() {
    name = ""
    email = ""
    phone = ""
    link = ""
    viewModel.clearLocationSearch()
    week = emptyWeek()
    editingDay = null
    showHoursDialog = false
    spaces = emptyList()
    spacesExpanded = false
    onBack()
  }

  fun addSpace() {
    spaces =
        spaces +
            Space(
                seats = AddSpaceRenterUi.Numbers.MIN_SEATS_PER_SPACE,
                costPerHour = AddSpaceRenterUi.Numbers.MIN_COST_PER_HOUR)
    spacesExpanded = true
  }

  val draftRenter =
      SpaceRenter(
          id = "",
          owner = owner,
          name = name,
          phone = phone,
          email = email,
          website = link,
          address = locationUi.selectedLocation ?: Location(),
          openingHours = week,
          spaces = spaces)

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  AddSpaceRenterUi.Strings.SCREEN_TITLE,
                  modifier = Modifier.testTag(CreateSpaceRenterScreenTestTags.TITLE))
            },
            navigationIcon = {
              IconButton(
                  onClick = onBack,
                  modifier = Modifier.testTag(CreateSpaceRenterScreenTestTags.NAV_BACK)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                  }
            },
            modifier = Modifier.testTag(CreateSpaceRenterScreenTestTags.TOPBAR))
      },
      snackbarHost = {
        SnackbarHost(
            snackbarHost,
            modifier = Modifier.testTag(CreateSpaceRenterScreenTestTags.SNACKBAR_HOST))
      },
      bottomBar = {
        ActionBar(
            onDiscard = { discardAndBack() },
            onPrimary = {
              scope.launch {
                try {
                  onCreate(draftRenter)
                  onCreated()
                } catch (e: IllegalArgumentException) {
                  snackbarHost.showSnackbar(e.message ?: AddSpaceRenterUi.Strings.ERROR_VALIDATION)
                } catch (_: Exception) {
                  snackbarHost.showSnackbar(AddSpaceRenterUi.Strings.ERROR_CREATE)
                }
              }
            },
            enabled = isValid)
      },
      modifier = Modifier.testTag(CreateSpaceRenterScreenTestTags.SCAFFOLD)) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).testTag(CreateSpaceRenterScreenTestTags.LIST),
            contentPadding =
                PaddingValues(
                    horizontal = AddSpaceRenterUi.Dimensions.contentHPadding,
                    vertical = AddSpaceRenterUi.Dimensions.contentVPadding)) {
              // Required info
              item {
                CollapsibleSection(
                    title = AddSpaceRenterUi.Strings.REQUIREMENTS_SECTION,
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
                    testTag = CreateSpaceRenterScreenTestTags.SECTION_REQUIRED)
              }

              // Availability
              item {
                CollapsibleSection(
                    title = AddSpaceRenterUi.Strings.SECTION_AVAILABILITY,
                    initiallyExpanded = false,
                    content = {
                      AvailabilitySection(
                          week = week,
                          onEdit = { day ->
                            editingDay = day
                            showHoursDialog = true
                          })
                    },
                    testTag = CreateSpaceRenterScreenTestTags.SECTION_AVAILABILITY)
              }

              // Spaces
              item {
                CollapsibleSection(
                    title = AddSpaceRenterUi.Strings.SECTION_SPACES,
                    initiallyExpanded = false,
                    expanded = spacesExpanded,
                    onExpandedChange = { spacesExpanded = it },
                    header = {
                      TextButton(
                          onClick = { addSpace() },
                          modifier =
                              Modifier.testTag(CreateSpaceRenterScreenTestTags.SPACES_ADD_BUTTON)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(AddSpaceRenterUi.Dimensions.between))
                            Text(
                                AddSpaceRenterUi.Strings.BTN_ADD_SPACE,
                                modifier =
                                    Modifier.testTag(
                                        CreateSpaceRenterScreenTestTags.SPACES_ADD_LABEL))
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
                    testTag = CreateSpaceRenterScreenTestTags.SECTION_SPACES)
              }

              item {
                Spacer(
                    Modifier.height(AddSpaceRenterUi.Dimensions.bottomSpacer)
                        .testTag(CreateSpaceRenterScreenTestTags.BOTTOM_SPACER))
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
