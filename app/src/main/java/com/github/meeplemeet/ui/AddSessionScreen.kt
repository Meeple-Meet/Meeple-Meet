@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Location
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.components.*
import com.github.meeplemeet.ui.theme.Elevation
import com.google.firebase.Timestamp
import java.time.*
import java.util.Date
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

/* =======================================================================
 * Setup
 * ======================================================================= */

data class SessionForm(
    val title: String = "",
    val proposedGame: String = "",
    val minPlayers: Int = 1,
    val maxPlayers: Int = 1,
    val participants: List<Account> = emptyList(),
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val locationText: String = ""
)

const val TITLE_PLACEHOLDER: String = "Title"
const val PARTICIPANT_SECTION_NAME: String = "Participants"
const val SLIDER_DESCRIPTION: String = "Number of players"
const val ORGANISATION_SECTION_NAME: String = "Organisation"
const val GAME_SEARCH_PLACEHOLDER: String = "Search games…"
const val MAX_SLIDER_NUMBER: Float = 9f
const val MIN_SLIDER_NUMBER: Float = 1f
const val SLIDER_STEPS: Int = (MAX_SLIDER_NUMBER - MIN_SLIDER_NUMBER - 1).toInt()

/** TODO: change this to a truly location searcher later when coded */
/**
 * Mock location suggestions from a query string
 *
 * @param query The query string to base suggestions on
 * @param max Maximum number of suggestions to return; defaults to 5
 */
private fun mockLocationSuggestionsFrom(query: String, max: Int = 5): List<Location> {
  if (query.isBlank()) return emptyList()
  val rng = Random(query.hashCode())
  return List(max) { i ->
    val lat = rng.nextDouble(-90.0, 90.0)
    val lon = rng.nextDouble(-180.0, 180.0)
    Location(latitude = lat, longitude = lon, name = "$query #${i + 1}")
  }
}

/**
 * Convert LocalDate and LocalTime to Firebase Timestamp
 *
 * @param date The date to convert
 * @param time The time to convert
 * @param zoneId The time zone to use; defaults to system default
 */
fun toTimestamp(
    date: LocalDate?,
    time: LocalTime?,
    zoneId: ZoneId = ZoneId.systemDefault()
): Timestamp {
  return if (date != null && time != null) {
    val millis = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    Timestamp(Date(millis))
  } else {
    Timestamp.now()
  }
}

/* =======================================================================
 * Main screen
 * ======================================================================= */

@Composable
fun AddSessionScreen(
    account: Account,
    discussion: Discussion,
    viewModel: FirestoreViewModel,
    sessionViewModel: FirestoreSessionViewModel,
    onBack: () -> Unit = {}
) {
  var form by remember(account.uid) { mutableStateOf(SessionForm(participants = listOf(account))) }
  var selectedLocation by remember { mutableStateOf<Location?>(null) }

  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val showError: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

  LaunchedEffect(discussion.uid) {
    viewModel.getDiscussionParticipants(discussion) { fetched ->
      form = form.copy(participants = (fetched + account).distinctBy { it.uid })
    }

    if (form.proposedGame.isNotBlank()) {
      runCatching { sessionViewModel.setGameQuery(account, discussion, form.proposedGame) }
          .onFailure { e -> showError(e.message ?: "Failed to run game search") }
    }
  }

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  "Create Session",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimary)
            },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary)
              }
            },
            colors =
                TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface))
      },
      snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

              // Title
              LabeledTextField(
                  label = "Title",
                  value = form.title,
                  onValueChange = { form = form.copy(title = it) },
                  placeholder = TITLE_PLACEHOLDER,
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth())

              // Game search (below title) — VM-backed, with safe error handling
              GameSearchBar(
                  sessionViewModel = sessionViewModel,
                  currentUser = account,
                  discussion = discussion,
                  queryFallback = form.proposedGame,
                  onQueryFallbackChange = { form = form.copy(proposedGame = it) },
                  onError = showError)

              // Participants section
              ParticipantsSection(
                  currentUserId = account.uid,
                  selected = form.participants,
                  allCandidates = form.participants,
                  minPlayers = form.minPlayers,
                  maxPlayers = form.maxPlayers,
                  onMinMaxChange = { min, max ->
                    form = form.copy(minPlayers = min, maxPlayers = max)
                  },
                  minSliderNumber = MIN_SLIDER_NUMBER,
                  maxSliderNumber = MAX_SLIDER_NUMBER,
                  sliderSteps = SLIDER_STEPS,
                  onAdd = { toAdd ->
                    form =
                        form.copy(participants = (form.participants + toAdd).distinctBy { it.uid })
                  },
                  mainSectionTitle = PARTICIPANT_SECTION_NAME,
                  sliderDescription = SLIDER_DESCRIPTION,
                  onRemove = { toRemove ->
                    form =
                        form.copy(
                            participants = form.participants.filterNot { it.uid == toRemove.uid })
                  })

              // Organisation section (date/time + location search INSIDE)
              OrganisationSection(
                  date = form.date,
                  time = form.time,
                  locationText = form.locationText,
                  onDateChange = { form = form.copy(date = it) },
                  onTimeChange = { form = form.copy(time = it) },
                  onLocationChange = { form = form.copy(locationText = it) },
                  onLocationPicked = { selectedLocation = it },
                  title = ORGANISATION_SECTION_NAME)

              Spacer(Modifier.height(4.dp))

              // Creation and Discard buttons
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val haveDateTime = form.date != null && form.time != null
                val withinBounds =
                    form.participants.size >= form.minPlayers &&
                        form.participants.size <= form.maxPlayers

                val canCreate = haveDateTime && withinBounds && form.title.isNotBlank()

                CreateSessionButton(
                    formToSubmit = form,
                    enabled = canCreate,
                    onCreate = {
                      runCatching {
                            val selectedGameId = sessionViewModel.gameUIState.value.selectedGameUid
                            sessionViewModel.createSession(
                                requester = account,
                                discussion = discussion,
                                name = form.title,
                                gameId =
                                    selectedGameId.ifBlank {
                                      form.proposedGame.ifBlank { "Unknown game" }
                                    },
                                date = toTimestamp(form.date, form.time),
                                location = selectedLocation ?: Location(),
                                minParticipants = form.minPlayers,
                                maxParticipants = form.maxPlayers,
                                *form.participants.toTypedArray())
                          }
                          .onFailure { e ->
                            showError(e.message ?: "Failed to create session")
                            return@CreateSessionButton
                          }

                      form = SessionForm(participants = listOf(account))
                      selectedLocation = null
                      onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                DiscardButton(
                    modifier = Modifier.fillMaxWidth(),
                    onDiscard = {
                      form = SessionForm(participants = listOf(account))
                      selectedLocation = null
                      onBack()
                    })
              }
            }
      }
}

/* =======================================================================
 * Components
 * ======================================================================= */

/**
 * Game search bar
 *
 * @param sessionViewModel ViewModel holding the game search state and methods
 * @param currentUser The user performing the search
 * @param discussion The discussion to which the session will belong; if null, search is
 * @param queryFallback Fallback query string if VM has none
 * @param onQueryFallbackChange Callback when the query changes, to update the form state
 * @param onError Callback to surface errors
 * @param modifier Optional modifier for the outer card
 */
@Composable
fun GameSearchBar(
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussion: Discussion?,
    queryFallback: String,
    onQueryFallbackChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {}
) {
  val gameUi by sessionViewModel.gameUIState.collectAsState()
  val gameQuery = gameUi.gameQuery.ifBlank { queryFallback }

  SectionCard(
      modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)) {
        UnderlinedLabel("Proposed game:")
        Spacer(Modifier.height(12.dp))

        GameSearchField(
            query = gameQuery,
            onQueryChange = { q ->
              onQueryFallbackChange(q)
              discussion?.let { disc ->
                runCatching { sessionViewModel.setGameQuery(currentUser, disc, q) }
                    .onFailure { e -> onError(e.message ?: "Game search failed") }
              }
            },
            results = gameUi.gameSuggestions,
            onPick = { game ->
              discussion?.let { disc ->
                runCatching {
                      sessionViewModel.setGame(currentUser, disc, game)
                      sessionViewModel.getGameFromId(game.uid)
                    }
                    .onFailure { e -> onError(e.message ?: "Failed to select game") }
              }
            },
            isLoading = false,
            placeholder = GAME_SEARCH_PLACEHOLDER,
            modifier = Modifier.fillMaxWidth())

        gameUi.gameSearchError
            ?.takeIf { it.isNotBlank() }
            ?.let { msg ->
              Spacer(Modifier.height(6.dp))
              Text(
                  msg,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.error)
            }
      }
}

/**
 * Create Session button
 *
 * @param formToSubmit The form data to submit when creating
 * @param onCreate Callback when the button is clicked, with the form data
 * @param modifier Optional modifier for the button
 * @param enabled Whether the button is enabled
 */
@Composable
fun CreateSessionButton(
    formToSubmit: SessionForm,
    onCreate: (SessionForm) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
  Button(
      onClick = { onCreate(formToSubmit) },
      enabled = enabled,
      modifier = modifier,
      shape = CircleShape,
      elevation = ButtonDefaults.buttonElevation(defaultElevation = Elevation.raised),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onBackground)) {
        Icon(Icons.Default.Check, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Create Session", style = MaterialTheme.typography.bodyMedium)
      }
}

/**
 * Discard button
 *
 * @param onDiscard Callback when the button is clicked
 * @param modifier Optional modifier for the button
 */
@Composable
fun DiscardButton(modifier: Modifier = Modifier, onDiscard: () -> Unit) {
  OutlinedButton(
      onClick = onDiscard,
      modifier = modifier,
      shape = CircleShape,
      border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
      colors =
          ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Icon(Icons.Default.Delete, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Discard", style = MaterialTheme.typography.bodyMedium)
      }
}

/**
 * Organisation section
 *
 * @param date Currently selected date
 * @param time Currently selected time
 * @param locationText Current location text query
 * @param onDateChange Callback when the date changes
 * @param onTimeChange Callback when the time changes
 * @param onLocationChange Callback when the location text changes
 * @param onLocationPicked Optional callback when a location is picked from suggestions
 * @param title Title for the section
 * @param modifier Optional modifier for the outer card
 * @param onLocationPicked Optional callback when a location is picked from suggestions
 */
@Composable
fun OrganisationSection(
    date: LocalDate?,
    time: LocalTime?,
    locationText: String,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onLocationChange: (String) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    onLocationPicked: ((Location) -> Unit)? = null,
) {
  SectionCard(
      modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)) {
        UnderlinedLabel("$title:")
        Spacer(Modifier.height(12.dp))

        DatePickerDockedField(
            value = date, onValueChange = onDateChange, label = "Date", editable = true)

        Spacer(Modifier.height(10.dp))

        TimePickerField(value = time, onValueChange = onTimeChange, label = "Time")

        Spacer(Modifier.height(10.dp))

        val locationResults = remember(locationText) { mockLocationSuggestionsFrom(locationText) }
        LocationSearchField(
            query = locationText,
            onQueryChange = onLocationChange,
            results = locationResults,
            onPick = { loc ->
              onLocationChange(loc.name)
              onLocationPicked?.invoke(loc)
            },
            isLoading = false,
            placeholder = "Search locations…",
            modifier = Modifier.fillMaxWidth())
      }
}

/**
 * Participants section
 *
 * @param currentUserId The current user's ID, to prevent self-removal
 * @param selected The currently selected participants
 * @param allCandidates All possible candidates to add
 * @param minPlayers Current minimum players
 * @param maxPlayers Current maximum players
 * @param onMinMaxChange Callback when min/max change
 * @param onAdd Callback when a participant is added
 * @param onRemove Callback when a participant is removed
 * @param minSliderNumber Minimum number for the slider
 * @param maxSliderNumber Maximum number for the slider
 * @param sliderSteps Number of steps for the slider
 * @param mainSectionTitle Title for the section
 * @param sliderDescription Description text for the slider
 * @param elevationSelected Elevation for selected chips
 * @param elevationUnselected Elevation for unselected chips
 * @param modifier Optional modifier for the outer card
 */
@Composable
fun ParticipantsSection(
    currentUserId: String,
    selected: List<Account>,
    allCandidates: List<Account>,
    minPlayers: Int,
    maxPlayers: Int,
    onMinMaxChange: (Int, Int) -> Unit,
    onAdd: (Account) -> Unit,
    onRemove: (Account) -> Unit,
    minSliderNumber: Float,
    maxSliderNumber: Float,
    sliderSteps: Int,
    mainSectionTitle: String,
    sliderDescription: String,
    elevationSelected: Dp = Elevation.floating,
    elevationUnselected: Dp = Elevation.raised,
    modifier: Modifier = Modifier
) {
  SectionCard(
      modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)) {

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
          UnderlinedLabel("$mainSectionTitle:")
          Spacer(Modifier.width(8.dp))
          CountBubble(
              count = selected.size,
              modifier =
                  Modifier.shadow(Elevation.subtle, CircleShape, clip = false)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surface)
                      .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                      .padding(horizontal = 10.dp, vertical = 6.dp))
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = sliderDescription,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))

        // Slider row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()) {
              CountBubble(
                  count = minPlayers,
                  modifier =
                      Modifier.shadow(Elevation.subtle, CircleShape, clip = false)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.surface)
                          .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                          .padding(horizontal = 10.dp, vertical = 6.dp))

              DiscretePillSlider(
                  range = minSliderNumber..maxSliderNumber,
                  values = minPlayers.toFloat()..maxPlayers.toFloat(),
                  steps = sliderSteps,
                  editable = true,
                  onValuesChange = { min, max ->
                    val newMin =
                        min.roundToInt().coerceIn(minSliderNumber.toInt(), maxSliderNumber.toInt())
                    val newMax = max.roundToInt().coerceIn(newMin, maxSliderNumber.toInt())
                    if (newMin != minPlayers || newMax != maxPlayers) {
                      onMinMaxChange(newMin, newMax)
                    }
                  },
                  surroundModifier = Modifier.weight(1f),
                  sliderModifier =
                      Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                          .padding(horizontal = 10.dp, vertical = 6.dp))

              CountBubble(
                  count = maxPlayers,
                  modifier =
                      Modifier.shadow(Elevation.subtle, CircleShape, clip = false)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.surface)
                          .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                          .padding(horizontal = 10.dp, vertical = 6.dp))
            }

        Spacer(Modifier.height(12.dp))

        // Selected chips
        val animElev by animateDpAsState(targetValue = elevationSelected, label = "chipElevation")
        TwoPerRowGrid(
            items = selected,
            key = { it.uid },
            modifier = Modifier.fillMaxWidth(),
            rowsModifier = Modifier.fillMaxWidth(),
        ) { acc, itemModifier ->
          ParticipantChip(
              account = acc,
              action = ParticipantAction.Remove,
              onClick = { toRemove -> if (toRemove.uid != currentUserId) onRemove(toRemove) },
              modifier =
                  itemModifier
                      .shadow(animElev, MaterialTheme.shapes.large, clip = false)
                      .background(MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                      .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                      .padding(horizontal = 8.dp, vertical = 4.dp),
              textModifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))

        // Candidates
        val selectedIds by remember(selected) { derivedStateOf { selected.map { it.uid }.toSet() } }
        val candidates by
            remember(allCandidates, selectedIds, currentUserId) {
              derivedStateOf {
                allCandidates.filter { it.uid != currentUserId && it.uid !in selectedIds }
              }
            }

        TwoPerRowGrid(items = candidates, key = { it.uid }) { acc, itemModifier ->
          ParticipantChip(
              account = acc,
              action = ParticipantAction.Add,
              onClick = onAdd,
              modifier =
                  itemModifier
                      .shadow(elevationUnselected, MaterialTheme.shapes.large, clip = false)
                      .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                      .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                      .padding(horizontal = 8.dp, vertical = 4.dp),
              textModifier = Modifier.fillMaxWidth())
        }
      }
}
