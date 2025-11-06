// This class was primerily done by hand and adjusted using ChatGPT-5 Extended Thinking
// LLM was used to spot errors, suggest improvements, and write some repetitive code sections.
// Copilot was also used to generated docstrings
@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.LocationUIState
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.components.*
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.theme.Elevation
import com.google.firebase.Timestamp
import java.time.*
import java.util.Date
import kotlin.random.Random
import kotlinx.coroutines.launch

/* =======================================================================
 * Test Tags  (no overlap with ComponentsTestTags which use "comp_*")
 * ======================================================================= */
object SessionCreationTestTags {
  // App bar
  const val SCAFFOLD = "add_session_scaffold"
  const val TOP_APP_BAR = "add_session_top_app_bar"
  const val TOP_APP_BAR_TITLE = "add_session_top_app_bar_title"
  const val NAV_BACK_BTN = "nav_back_btn"

  // Snackbar
  const val SNACKBAR_HOST = "add_session_snackbar_host"

  // Content
  const val CONTENT_COLUMN = "add_session_content_column"

  // Title field
  const val FORM_TITLE_FIELD = "add_session_title_field"

  // Sections
  const val GAME_SEARCH_SECTION = "add_session_game_search_section"
  const val GAME_SEARCH_ERROR = "add_session_game_search_error"
  const val LOCATION_SEARCH_ERROR = "add_session_location_search_error"
  const val PARTICIPANTS_SECTION = "add_session_participants_section"
  const val ORG_SECTION = "add_session_organisation_section"

  // Buttons row & actions
  const val BUTTON_ROW = "add_session_button_row"
  const val DISCARD_BUTTON = "add_session_discard_button"
  const val DISCARD_ICON = "add_session_discard_icon"
  const val CREATE_BUTTON = "add_session_create_button"
  const val CREATE_ICON = "add_session_create_icon"
}

/* =======================================================================
 * Setup
 * ======================================================================= */

data class SessionForm(
    val title: String = "",
    val proposedGameString: String = "",
    val participants: List<Account> = emptyList(),
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val locationText: String = ""
)

const val TITLE_PLACEHOLDER: String = "Title"
const val PARTICIPANT_SECTION_NAME: String = "Participants"
const val ORGANISATION_SECTION_NAME: String = "Organisation"
const val GAME_SEARCH_PLACEHOLDER: String = "Search games…"

/** TODO: change this to a truly location searcher later when coded */
/**
 * Returns a list of mock location suggestions based on a query string.
 *
 * @param query The input string to base the suggestions on.
 * @param max The maximum number of location suggestions to return.
 * @return A list of [Location] objects with randomized coordinates.
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
 * Converts a [LocalDate] and [LocalTime] to a Firebase [Timestamp].
 *
 * @param date The date of the session, or null.
 * @param time The time of the session, or null.
 * @param zoneId The time zone to use for conversion (defaults to system default).
 * @return A [Timestamp] corresponding to the combined date and time, or the current timestamp if
 *   either is null.
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

/**
 * Composable function representing the Add Session screen.
 *
 * This screen allows the user to create a new session, including setting a title, selecting a game,
 * specifying the date and time, choosing a location, and managing participants.
 *
 * @param account The current user's account.
 * @param discussion The discussion context for the session.
 * @param viewModel The FirestoreViewModel for data operations.
 * @param sessionViewModel The FirestoreSessionViewModel for session-specific operations.
 * @param onBack Callback function to be invoked when navigating back.
 */
@Composable
fun CreateSessionScreen(
    account: Account,
    discussion: Discussion,
    viewModel: DiscussionViewModel,
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit = {}
) {
  // Holds the form state for the session
  var form by remember(account.uid) { mutableStateOf(SessionForm(participants = listOf(account))) }
  // Holds the selected location (may be null)
  var selectedLocation by remember { mutableStateOf<Location?>(null) }
  val gameUi by sessionViewModel.gameUIState.collectAsState()
  val locationUi by sessionViewModel.locationUIState.collectAsState()

  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  // Helper to show error messages in a snackbar
  val showError: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

  // Fetch participants and possibly trigger game query on discussion change
  LaunchedEffect(discussion.uid) {
    viewModel.getAccounts(discussion.participants) { fetched ->
      form = form.copy(participants = (fetched + account).distinctBy { it.uid })
    }

    // If a game query was already entered, trigger search
    if (form.proposedGameString.isNotBlank()) {
      runCatching { sessionViewModel.setGameQuery(account, discussion, form.proposedGameString) }
          .onFailure { e -> showError(e.message ?: "Failed to run game search") }
    }
  }

  Scaffold(
      modifier = Modifier.testTag(SessionCreationTestTags.SCAFFOLD),
      topBar = {
        TopBarWithDivider(
            text = MeepleMeetScreen.CreateSession.title,
            onReturn = { onBack() },
        )
      },
      snackbarHost = {
        SnackbarHost(snackbar, modifier = Modifier.testTag(SessionCreationTestTags.SNACKBAR_HOST))
      },
      bottomBar = {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 25.dp)
                    .testTag(SessionCreationTestTags.BUTTON_ROW),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              // Whether form is ready for creation
              val canCreate = form.title.isNotBlank() && form.date != null && form.time != null

              // Reset form and go back on discard
              DiscardButton(
                  modifier = Modifier.weight(1f),
                  onDiscard = {
                    form = SessionForm(participants = listOf(account))
                    selectedLocation = null
                    onBack()
                  })

              // Create a new session if form is valid
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
                                    form.proposedGameString.ifBlank { "Unknown game" }
                                  },
                              date = toTimestamp(form.date, form.time),
                              location = selectedLocation ?: Location(),
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
                  modifier = Modifier.weight(1f),
              )
            }
      }) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag(SessionCreationTestTags.CONTENT_COLUMN),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

              // Organisation section (title, game, date, time, location)
              OrganisationSection(
                  gameUi = gameUi,
                  locationUi = locationUi,
                  sessionViewModel = sessionViewModel,
                  account = account,
                  discussion = discussion,
                  showError = showError,
                  onTitleChange = { form = form.copy(title = it) },
                  form = form,
                  onQueryFallbackChange = { form = form.copy(proposedGameString = it) },
                  date = form.date,
                  time = form.time,
                  locationText = form.locationText,
                  onDateChange = { form = form.copy(date = it) },
                  onTimeChange = { form = form.copy(time = it) },
                  onLocationChange = { form = form.copy(locationText = it) },
                  onLocationPicked = { selectedLocation = it },
                  modifier = Modifier.testTag(SessionCreationTestTags.ORG_SECTION))

              // Participants section (player selection and slider)
              ParticipantsSection(
                  account = account,
                  selected = form.participants,
                  allCandidates = form.participants,
                  minPlayers = gameUi.fetchedGame?.minPlayers ?: 0,
                  maxPlayers = gameUi.fetchedGame?.maxPlayers ?: 0,
                  onAdd = { toAdd ->
                    form =
                        form.copy(participants = (form.participants + toAdd).distinctBy { it.uid })
                  },
                  mainSectionTitle = PARTICIPANT_SECTION_NAME,
                  onRemove = { toRemove ->
                    form =
                        form.copy(
                            participants = form.participants.filterNot { it.uid == toRemove.uid })
                  },
                  modifier = Modifier.testTag(SessionCreationTestTags.PARTICIPANTS_SECTION))
            }
      }
}

/* =======================================================================
 * Components (only test tags added via modifiers; logic unchanged)
 * ======================================================================= */

/**
 * Composable function representing the game search bar section.
 *
 * @param sessionViewModel The FirestoreSessionViewModel for session-specific operations.
 * @param currentUser The current user's account.
 * @param discussion The discussion context for the session.
 * @param queryFallback The fallback query string for the game search.
 * @param onQueryFallbackChange Callback function to be invoked when the query changes.
 * @param modifier Modifier for styling the composable.
 * @param onError Callback function to handle errors.
 */
@Composable
fun GameSearchBar(
    sessionViewModel: SessionViewModel,
    gameUi: GameUIState,
    currentUser: Account,
    discussion: Discussion?,
    queryFallback: String,
    onQueryFallbackChange: (String) -> Unit,
    onError: (String) -> Unit = {}
) {
  val gameQuery = gameUi.gameQuery.ifBlank { queryFallback }
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
            modifier = Modifier.testTag(SessionCreationTestTags.GAME_SEARCH_ERROR),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error)
      }
}

@Composable
fun LocationSearchBar(
    sessionViewModel: SessionViewModel,
    locationUi: LocationUIState,
    currentUser: Account,
    discussion: Discussion?,
    onError: (String) -> Unit = {},
    onPick: ((Location) -> Unit)? = null
) {
  LocationSearchField(
      query = locationUi.locationQuery,
      onQueryChange = { q ->
        discussion?.let { disc ->
          runCatching { sessionViewModel.setLocationQuery(currentUser, disc, q) }
              .onFailure { e -> onError(e.message ?: "Location search failed") }
        }
      },
      results = locationUi.locationSuggestions,
      onPick = { loc ->
        discussion?.let { disc ->
          runCatching {
                sessionViewModel.setLocation(currentUser, disc, loc)
                onPick?.invoke(loc)
              }
              .onFailure { e -> onError(e.message ?: "Failed to select location") }
        }
      },
      isLoading = false,
      placeholder = "Search locations…",
      modifier = Modifier.fillMaxWidth())

  locationUi.locationSearchError
      ?.takeIf { it.isNotBlank() }
      ?.let { msg ->
        Spacer(Modifier.height(6.dp))
        Text(
            msg,
            modifier = Modifier.testTag(SessionCreationTestTags.LOCATION_SEARCH_ERROR),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error)
      }
}

/**
 * Composable function representing the Create Session button.
 *
 * @param formToSubmit The session form data to be submitted upon creation.
 * @param onCreate Callback function to be invoked when the create button is clicked.
 * @param modifier Modifier for styling the composable.
 * @param enabled Boolean flag indicating whether the button is enabled.
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
      modifier = modifier.testTag(SessionCreationTestTags.CREATE_BUTTON),
      shape = CircleShape,
      elevation = ButtonDefaults.buttonElevation(defaultElevation = Elevation.raised),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onBackground)) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.testTag(SessionCreationTestTags.CREATE_ICON))
        Spacer(Modifier.width(8.dp))
        Text("Create", style = MaterialTheme.typography.bodyMedium)
      }
}

/**
 * Composable function representing the Discard button.
 *
 * @param onDiscard Callback function to be invoked when the discard button is clicked.
 * @param modifier Modifier for styling the composable.
 */
@Composable
fun DiscardButton(modifier: Modifier = Modifier, onDiscard: () -> Unit) {
  OutlinedButton(
      onClick = onDiscard,
      modifier = modifier.testTag(SessionCreationTestTags.DISCARD_BUTTON),
      shape = CircleShape,
      border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
      colors =
          ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Icon(
            Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.testTag(SessionCreationTestTags.DISCARD_ICON))
        Spacer(Modifier.width(8.dp))
        Text("Discard", style = MaterialTheme.typography.bodyMedium)
      }
}

/**
 * Composable function representing the organisation section of the session creation form.
 *
 * This section allows the user to enter the session title, search and select a game, pick date and
 * time, and search for a location.
 *
 * @param form The current session form state.
 * @param sessionViewModel The FirestoreSessionViewModel for session-specific operations.
 * @param account The current user's account.
 * @param onQueryFallbackChange Callback when the game query fallback changes.
 * @param discussion The discussion context for the session.
 * @param showError Callback for error handling.
 * @param onTitleChange Callback when the session title changes.
 * @param date The selected date for the session.
 * @param time The selected time for the session.
 * @param locationText The text input for the location search.
 * @param onDateChange Callback when the date changes.
 * @param onTimeChange Callback when the time changes.
 * @param onLocationChange Callback when the location text changes.
 * @param title The title of the organisation section.
 * @param modifier Modifier for styling the composable.
 * @param onLocationPicked Optional callback when a location is picked.
 */
@Composable
fun OrganisationSection(
    gameUi: GameUIState,
    locationUi: LocationUIState,
    sessionViewModel: SessionViewModel,
    account: Account,
    discussion: Discussion,
    date: LocalDate?,
    time: LocalTime?,
    locationText: String,
    modifier: Modifier = Modifier,
    form: SessionForm = SessionForm(),
    onQueryFallbackChange: (String) -> Unit = {},
    showError: (String) -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onLocationChange: (String) -> Unit,
    onLocationPicked: ((Location) -> Unit)? = null,
) {
  SectionCard(
      modifier
          .testTag(SessionCreationTestTags.ORG_SECTION)
          .fillMaxWidth()
          // border is now background color to create no border effect
          .border(1.dp, MaterialTheme.colorScheme.background, MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.large)) {

        // Title field for session name
        IconTextField(
            value = form.title,
            onValueChange = { onTitleChange(it) },
            placeholder = TITLE_PLACEHOLDER,
            editable = true,
            leadingIcon = {
              Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Title")
            },
            modifier = Modifier.fillMaxWidth().testTag(SessionCreationTestTags.FORM_TITLE_FIELD))

        Spacer(Modifier.height(10.dp))

        // Game search section
        GameSearchBar(
            gameUi = gameUi,
            sessionViewModel = sessionViewModel,
            currentUser = account,
            discussion = discussion,
            queryFallback = form.proposedGameString,
            onQueryFallbackChange = { onQueryFallbackChange(it) },
            onError = showError)

        Spacer(Modifier.height(10.dp))

        // Date picker for session date
        DatePickerDockedField(
            value = date, onValueChange = onDateChange, label = "Date", editable = true)

        Spacer(Modifier.height(10.dp))

        // Time picker for session time
        TimePickerField(value = time, onValueChange = onTimeChange, label = "Time")

        Spacer(Modifier.height(10.dp))

        // Location search field with suggestions
        LocationSearchBar(
            sessionViewModel = sessionViewModel,
            locationUi = locationUi,
            currentUser = account,
            discussion = discussion,
            onError = showError,
            onPick = onLocationPicked)
      }
}

/**
 * Composable function representing the participants section of the session creation form.
 *
 * @param selected The list of currently selected participants.
 * @param allCandidates The list of all candidate participants.
 * @param minPlayers The minimum number of players.
 * @param maxPlayers The maximum number of players.
 * @param onAdd Callback function to be invoked when a participant is added.
 * @param onRemove Callback function to be invoked when a participant is removed.
 * @param mainSectionTitle The title of the participants section.
 * @param modifier Modifier for styling the composable.
 */
@Composable
fun ParticipantsSection(
    modifier: Modifier = Modifier,
    account: Account,
    selected: List<Account>,
    allCandidates: List<Account>,
    minPlayers: Int,
    maxPlayers: Int,
    onAdd: (Account) -> Unit,
    onRemove: (Account) -> Unit,
    mainSectionTitle: String,
) {
  SectionCard(
      modifier
          .testTag(SessionCreationTestTags.PARTICIPANTS_SECTION)
          .fillMaxWidth()
          // border is now background color to create no border effect
          .border(1.dp, MaterialTheme.colorScheme.background, MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.large)) {

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

        Spacer(Modifier.height(2.dp))

        // Slider row
        if (minPlayers > 0 && maxPlayers > 0) {
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
                    range = (minPlayers - 1f)..(maxPlayers + 1f),
                    values = minPlayers.toFloat()..maxPlayers.toFloat(),
                    steps = (maxPlayers - minPlayers + 1).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                    sliderModifier =
                        Modifier.background(MaterialTheme.colorScheme.background, CircleShape)
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
        }

        Spacer(Modifier.height(12.dp))

        // All candidate chips
        UserChipsGrid(
            participants = allCandidates,
            onRemove = onRemove,
            onAdd = onAdd,
            account = account,
            editable = true)
      }
}
