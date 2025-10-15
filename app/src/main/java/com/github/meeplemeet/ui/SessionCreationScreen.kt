@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

/** TODO: implement real geocoding later */
/**
 * Generate a random location for a given text
 *
 * @param text The text to base the location name on
 */
private fun randomLocationFrom(text: String): Location =
    Location(
        latitude = Random.nextDouble(-90.0, 90.0),
        longitude = Random.nextDouble(-180.0, 180.0),
        name = text.ifBlank { "Random place" })

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
private fun toTimestamp(
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
fun CreateSessionScreen(
    viewModel: FirestoreViewModel,
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussionId: String,
    onBack: () -> Unit = {},
) {
  var form by
      remember(currentUser.uid) { mutableStateOf(SessionForm(participants = listOf(currentUser))) }
  var selectedLocation by remember { mutableStateOf<Location?>(null) }

  val discussion by viewModel.discussionFlow(discussionId).collectAsState()
  val availableParticipants = remember { mutableStateListOf<Account>() }

  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val showError: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

  LaunchedEffect(discussion?.uid) {
    availableParticipants.clear()
    val disc = discussion ?: return@LaunchedEffect
    viewModel.getDiscussionParticipants(disc) { fetched ->
      val withMe = (fetched + currentUser).distinctBy { it.uid }
      availableParticipants.addAll(withMe)
    }

    if (form.proposedGame.isNotBlank()) {
      runCatching { sessionViewModel.setGameQuery(currentUser, disc, form.proposedGame) }
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
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                  currentUser = currentUser,
                  discussion = discussion,
                  queryFallback = form.proposedGame,
                  onQueryFallbackChange = { form = form.copy(proposedGame = it) },
                  onError = showError)

              // Participants section
              ParticipantsSection(
                  currentUserId = currentUser.uid,
                  selected = form.participants,
                  allCandidates = availableParticipants,
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
                val haveDiscussion = discussion != null
                val haveDateTime = form.date != null && form.time != null
                val withinBounds =
                    form.participants.size >= form.minPlayers &&
                        form.participants.size <= form.maxPlayers &&
                        form.minPlayers <= form.maxPlayers

                val canCreate =
                    (haveDiscussion && haveDateTime && withinBounds && form.title.isNotBlank())

                CreateSessionButton(
                    formToSubmit = form,
                    enabled = canCreate,
                    onCreate = {
                      val disc = discussion ?: return@CreateSessionButton

                      runCatching {
                            val selectedGameId = sessionViewModel.gameUIState.value.selectedGameUid
                            sessionViewModel.createSession(
                                requester = currentUser,
                                discussion = disc,
                                name = form.title,
                                gameId =
                                    selectedGameId.ifBlank {
                                      form.proposedGame.ifBlank { "Unknown game" }
                                    },
                                date = toTimestamp(form.date, form.time),
                                location =
                                    selectedLocation ?: randomLocationFrom(form.locationText),
                                minParticipants = form.minPlayers,
                                maxParticipants = form.maxPlayers,
                                *form.participants.toTypedArray())
                          }
                          .onFailure { e ->
                            showError(e.message ?: "Failed to create session")
                            return@CreateSessionButton
                          }

                      form = SessionForm(participants = listOf(currentUser))
                      selectedLocation = null
                      onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                DiscardButton(
                    modifier = Modifier.fillMaxWidth(),
                    onDiscard = {
                      form = SessionForm(participants = listOf(currentUser))
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

        DatePickerDockedField(value = date, onValueChange = onDateChange, label = "Date")

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

/* =========================
 * Previews
 * ========================= */

/*
@Preview(showBackground = true, name = "Create Session – Light")
@Composable
private fun Preview_CreateSession_Approx_Light() {
  AppTheme { CreateSessionApproxPreviewHost() }
}

@Composable
private fun CreateSessionApproxPreviewHost() {
  val me = Account(uid = "1", name = "Marco", handle = " ", email = "marco@epfl.ch")
  val pool =
      listOf(
          me,
          Account(uid = "2", name = "Alexandre", handle = " ", email = "alexandre@example.com"),
          Account(uid = "3", name = "Antoine", handle = " ", email = "antoine@example.com"),
          Account(uid = "4", name = "Dany", handle = " ", email = "dany@example.com"),
          Account(uid = "5", name = "Enes", handle = " ", email = "enes@example.com"),
          Account(uid = "6", name = "Nil", handle = " ", email = "nil@example.com"),
          Account(uid = "7", name = "Thomas", handle = " ", email = "thomas@example.com"),
      )

  var form by remember {
    mutableStateOf(
        SessionForm(
            title = "Friday Night Meetup",
            proposedGame = "Root",
            minPlayers = 2,
            maxPlayers = 4,
            participants = listOf(me, pool[1])))
  }

  val allGames = remember { previewSampleGames() }
  val allLocations = remember { previewSampleLocations() }

  // Game search
  var gameQuery by remember { mutableStateOf(form.proposedGame) }
  val filteredGames by
      remember(gameQuery, allGames) {
        mutableStateOf(
            if (gameQuery.isBlank()) emptyList()
            else allGames.filter { it.name.contains(gameQuery, ignoreCase = true) }.take(5))
      }

  // Location search
  var locationQuery by remember { mutableStateOf(form.locationText) }
  val filteredLocations by
      remember(locationQuery, allLocations) {
        mutableStateOf(
            if (locationQuery.isBlank()) emptyList()
            else allLocations.filter { it.name.contains(locationQuery, ignoreCase = true) }.take(5))
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
              IconButton(onClick = { /* preview only */}) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary)
              }
            },
            colors =
                TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface))
      }) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

              // Title
              LabeledTextField(
                  label = "Title",
                  value = form.title,
                  onValueChange = { form = form.copy(title = it) },
                  placeholder = TITLE_PLACEHOLDER,
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth())

              // Game search
              SectionCard(
                  Modifier.fillMaxWidth()
                      .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                      .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)) {
                    UnderlinedLabel("Proposed game:")
                    Spacer(Modifier.height(12.dp))
                    GameSearchField(
                        query = gameQuery,
                        onQueryChange = { q -> gameQuery = q },
                        results = filteredGames,
                        onPick = { picked ->
                          form = form.copy(proposedGame = picked.uid)
                          gameQuery = picked.name
                        },
                        modifier = Modifier.fillMaxWidth())
                  }

              // Participants
              ParticipantsSection(
                  currentUserId = me.uid,
                  selected = form.participants,
                  allCandidates = pool,
                  minPlayers = form.minPlayers,
                  maxPlayers = form.maxPlayers,
                  onMinMaxChange = { min, max ->
                    form = form.copy(minPlayers = min, maxPlayers = max)
                  },
                  onAdd = { acc ->
                    form = form.copy(participants = (form.participants + acc).distinctBy { it.uid })
                  },
                  onRemove = { acc ->
                    form =
                        form.copy(participants = form.participants.filterNot { it.uid == acc.uid })
                  },
                  minSliderNumber = MIN_SLIDER_NUMBER,
                  maxSliderNumber = MAX_SLIDER_NUMBER,
                  sliderSteps = SLIDER_STEPS,
                  mainSectionTitle = PARTICIPANT_SECTION_NAME,
                  sliderDescription = SLIDER_DESCRIPTION)

              // Organisation
              SectionCard(
                  Modifier.fillMaxWidth()
                      .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                      .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)) {
                    UnderlinedLabel("$ORGANISATION_SECTION_NAME:")
                    Spacer(Modifier.height(12.dp))

                    DatePickerDockedField(
                        value = form.date,
                        onValueChange = { form = form.copy(date = it) },
                        label = "Date")
                    Spacer(Modifier.height(10.dp))
                    TimePickerField(
                        value = form.time,
                        onValueChange = { form = form.copy(time = it) },
                        label = "Time")
                    Spacer(Modifier.height(10.dp))

                    LocationSearchField(
                        query = locationQuery,
                        onQueryChange = { q ->
                          locationQuery = q
                          form = form.copy(locationText = q)
                        },
                        results = filteredLocations,
                        onPick = { loc ->
                          locationQuery = loc.name
                          form = form.copy(locationText = loc.name)
                        },
                        modifier = Modifier.fillMaxWidth())
                  }

              Spacer(Modifier.height(4.dp))

              // Buttons
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CreateSessionButton(
                    formToSubmit = form,
                    onCreate = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                DiscardButton(modifier = Modifier.fillMaxWidth(), onDiscard = {})
              }
            }
      }
}

@Preview(showBackground = true, name = "Game Search Field")
@Composable
private fun Preview_GameSearchField() {
  AppTheme {
    val all = remember { previewSampleGames() }
    var q by remember { mutableStateOf("Root") }
    val results by
        remember(q) {
          mutableStateOf(all.filter { it.name.contains(q, ignoreCase = true) }.take(5))
        }
    GameSearchField(
        query = q,
        onQueryChange = { q = it },
        results = results,
        onPick = { /* no-op */},
        modifier = Modifier.padding(16.dp))
  }
}

@Preview(showBackground = true, name = "Organisation – With Location Search")
@Composable
private fun Preview_Organisation_WithLocation() {
  AppTheme {
    var date by remember { mutableStateOf(LocalDate.of(2025, 10, 15)) }
    var time by remember { mutableStateOf(LocalTime.of(19, 0)) }
    var locationText by remember { mutableStateOf("EPFL") }
    val allLocations = remember { previewSampleLocations() }
    val filtered by
        remember(locationText, allLocations) {
          mutableStateOf(
              if (locationText.isBlank()) emptyList()
              else
                  allLocations.filter { it.name.contains(locationText, ignoreCase = true) }.take(5))
        }

    SectionCard(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(16.dp)) {
          UnderlinedLabel(ORGANISATION_SECTION_NAME)
          Spacer(Modifier.height(12.dp))
          DatePickerDockedField(value = date, onValueChange = { date = it }, label = "Date")
          Spacer(Modifier.height(10.dp))
          TimePickerField(value = time, onValueChange = { time = it }, label = "Time")
          Spacer(Modifier.height(10.dp))
          LocationSearchField(
              query = locationText,
              onQueryChange = { locationText = it },
              results = filtered,
              onPick = { locationText = it.name },
              modifier = Modifier.fillMaxWidth())
        }
  }
}

@Preview(showBackground = true, name = "Participants – Basic")
@Composable
private fun Preview_ParticipantsSection_Basic() {
  AppTheme {
    val me = Account(uid = "1", name = "Marco", handle = " ", email = "marco@example.com")
    val pool =
        listOf(
            me,
            Account(uid = "2", name = "Alexandre", handle = " ", email = "alexandre@example.com"),
            Account(uid = "3", name = "Antoine", handle = " ", email = "antoine@example.com"),
            Account(uid = "4", name = "Dany", handle = " ", email = "dany@example.com"),
            Account(uid = "5", name = "Enes", handle = " ", email = "enes@example.com"),
            Account(uid = "6", name = "Nil", handle = " ", email = "nil@example.com"),
            Account(uid = "7", name = "Thomas", handle = " ", email = "thomas@example.com"),
        )
    var min by remember { mutableIntStateOf(2) }
    var max by remember { mutableIntStateOf(4) }
    var selected by remember { mutableStateOf(listOf(me, pool[1], pool[2])) }

    ParticipantsSection(
        currentUserId = me.uid,
        selected = selected,
        allCandidates = pool,
        minPlayers = min,
        maxPlayers = max,
        onMinMaxChange = { a, b ->
          min = a
          max = b
        },
        onAdd = { a -> selected = (selected + a).distinctBy { it.uid } },
        onRemove = { a -> selected = selected.filterNot { it.uid == a.uid } },
        minSliderNumber = MIN_SLIDER_NUMBER,
        maxSliderNumber = MAX_SLIDER_NUMBER,
        sliderSteps = SLIDER_STEPS,
        mainSectionTitle = PARTICIPANT_SECTION_NAME,
        sliderDescription = SLIDER_DESCRIPTION,
        modifier = Modifier.padding(16.dp))
  }
}

@Preview(showBackground = true, name = "Organisation – Empty")
@Composable
private fun Preview_Organisation_Empty() {
  AppTheme {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var location by remember { mutableStateOf("") }

    OrganisationSection(
        date = date,
        time = time,
        locationText = location,
        onDateChange = { date = it },
        onTimeChange = { time = it },
        onLocationChange = { location = it },
        title = ORGANISATION_SECTION_NAME)
  }
}

@Preview(showBackground = true, name = "Docked Date Picker")
@Composable
private fun Preview_DockedDatePicker() {
  AppTheme {
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.of(2025, 8, 17)) }
    Column(Modifier.padding(16.dp)) {
      DatePickerDockedField(value = date, onValueChange = { date = it })
      Spacer(Modifier.height(8.dp))
      Text(
          "Selected: ${date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: ""}",
          style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Preview(showBackground = true, name = "Time Picker Field")
@Composable
private fun Preview_TimePickerField() {
  AppTheme {
    var t by remember { mutableStateOf<LocalTime?>(LocalTime.of(19, 0)) }
    Column(Modifier.padding(16.dp)) {
      TimePickerField(value = t, onValueChange = { t = it })
      Spacer(Modifier.height(8.dp))
      Text("Selected: ${t?.toString() ?: ""}", style = MaterialTheme.typography.labelSmall)
    }
  }
}

private fun previewSampleGames(): List<Game> =
    listOf(
        Game(
            uid = "g1",
            name = "Catan",
            description = "Trade, build, settle.",
            imageURL = "",
            minPlayers = 3,
            maxPlayers = 4,
            recommendedPlayers = 4,
            averagePlayTime = 60,
            genres = listOf(1, 2)),
        Game(
            uid = "g2",
            name = "Carcassonne",
            description = "Tile-laying in medieval France.",
            imageURL = "",
            minPlayers = 2,
            maxPlayers = 5,
            recommendedPlayers = 4,
            averagePlayTime = 45,
            genres = listOf(2)),
        Game(
            uid = "g3",
            name = "Camel Up",
            description = "Chaotic camel betting fun.",
            imageURL = "",
            minPlayers = 2,
            maxPlayers = 8,
            recommendedPlayers = 5,
            averagePlayTime = 30,
            genres = listOf(3)),
        Game(
            uid = "root",
            name = "Root",
            description = "Woodland warfare.",
            imageURL = "",
            minPlayers = 2,
            maxPlayers = 6,
            recommendedPlayers = 4,
            averagePlayTime = 90,
            genres = listOf(4)))

private fun previewSampleLocations(): List<Location> =
    listOf(
        Location(name = "Lausanne Flon", latitude = 46.5215, longitude = 6.6328),
        Location(name = "EPFL Esplanade", latitude = 46.5191, longitude = 6.5668),
        Location(name = "Geneva Cornavin", latitude = 46.2102, longitude = 6.1424),
        Location(name = "Zürich HB", latitude = 47.3782, longitude = 8.5402),
        Location(name = "Bern Station", latitude = 46.9480, longitude = 7.4391),
    )
*/
