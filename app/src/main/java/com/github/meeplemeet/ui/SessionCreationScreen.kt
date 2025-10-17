@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui

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
import androidx.compose.ui.Modifier
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
fun CreateSessionScreen(
    viewModel: FirestoreViewModel,
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussionId: String,
    onBack: () -> Unit = {},
    onCreate: () -> Unit = {}
) {
  var form by
      remember(currentUser.uid) { mutableStateOf(SessionForm(participants = listOf(currentUser))) }
  var selectedLocation by remember { mutableStateOf<Location?>(null) }

  val discussion by viewModel.discussionFlow(discussionId).collectAsState()

  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val showError: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

  LaunchedEffect(discussion?.uid) {
    val disc = discussion ?: return@LaunchedEffect
    viewModel.getDiscussionParticipants(disc) { fetched ->
      form = form.copy(participants = (fetched + currentUser).distinctBy { it.uid })
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
                  currentUser = currentUser,
                  discussion = discussion,
                  queryFallback = form.proposedGame,
                  onQueryFallbackChange = { form = form.copy(proposedGame = it) },
                  onError = showError)

              // Participants section
              ParticipantsSection(
                  form = form,
                  editable = true,
                  onFormChange = { min, max ->
                    form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
                  },
                  onRemoveParticipant = { p ->
                    form = form.copy(participants = form.participants.filterNot { it.uid == p.uid })
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
