// This class was primerily done by hand and adjusted using ChatGPT-5 Extended Thinking
// LLM was used to spot errors, suggest improvements, and write some repetitive code sections.
// Copilot was also used to generated docstrings
@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.meeplemeet.ui.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.sessions.CreateSessionViewModel
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.components.*
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.Elevation
import com.google.firebase.Timestamp
import java.time.*
import java.util.Date
import kotlinx.coroutines.launch

/* =======================================================================
 * Test Tags  (no overlap with ComponentsTestTags which use "comp_*")
 * ======================================================================= */
object SessionCreationTestTags {
  // App bar
  const val SCAFFOLD = "add_session_scaffold"

  // Snackbar
  const val SNACKBAR_HOST = "add_session_snackbar_host"

  // Content
  const val CONTENT_COLUMN = "add_session_content_column"

  // Title field
  const val FORM_TITLE_FIELD = "add_session_title_field"

  // Sections
  const val GAME_SEARCH_ERROR = "add_session_game_search_error"
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
 * Magic numbers and strings extracted as constants
 * ======================================================================= */

private val CountBubbleVerticalPadding = Dimensions.Spacing.small

private const val LABEL_CREATE = "Create"
private const val LABEL_DISCARD = "Discard"
private const val LABEL_EDIT_TITLE = "Edit Title"
private const val LABEL_DATE = "Date"
private const val LABEL_TIME = "Time"
const val LABEL_UNKNOWN_GAME = "Unknown game"
private const val LABEL_PARTICIPANTS = "Participants"

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

/**
 * Checks if the given date and time combination is in the past.
 *
 * @param date The date to check, or null.
 * @param time The time to check, or null.
 * @param zoneId The time zone to use for comparison (defaults to system default).
 * @return true if the date/time is in the past, false otherwise (including if either is null).
 */
fun isDateTimeInPast(
    date: LocalDate?,
    time: LocalTime?,
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean {
  if (date == null || time == null) return false
  val selectedDateTime = date.atTime(time).atZone(zoneId)
  val now = LocalDateTime.now(zoneId).atZone(zoneId)
  return selectedDateTime.isBefore(now)
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
 * @param onBack Callback function to be invoked when navigating back.
 */
@Composable
fun CreateSessionScreen(
    account: Account,
    discussion: Discussion,
    viewModel: CreateSessionViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
  // Holds the form state for the session
  var form by remember(account.uid) { mutableStateOf(SessionForm(participants = listOf(account))) }
  // Store all discussion members separately (remains constant)
  var allDiscussionMembers by remember { mutableStateOf<List<Account>>(emptyList()) }
  // Holds the selected location (may be null)
  val gameUi by viewModel.gameUIState.collectAsState()
  val locationUi by viewModel.locationUIState.collectAsState()

  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val context = LocalContext.current
  var isInputFocused by remember { mutableStateOf(false) }
  // Helper to show error messages in a snackbar
  val showError: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

  // Fetch participants and possibly trigger game query on discussion change
  LaunchedEffect(discussion.uid) {
    viewModel.getAccounts(discussion.participants, context) { fetched ->
      allDiscussionMembers = (fetched.filterNotNull() + account).distinctBy { it.uid }
      form = form.copy(participants = allDiscussionMembers)
    }

    // If a game query was already entered, trigger search
    if (form.proposedGameString.isNotBlank()) {
      runCatching { viewModel.setGameQuery(account, discussion, form.proposedGameString) }
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
        val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
        if (!(shouldHide && isInputFocused)) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          horizontal = Dimensions.Padding.xxxLarge,
                          vertical = Dimensions.Padding.xxLarge)
                      .testTag(SessionCreationTestTags.BUTTON_ROW),
              horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {
                // Whether form is ready for creation
                val isDateTimeInPast = isDateTimeInPast(form.date, form.time)
                val canCreate =
                    form.title.isNotBlank() &&
                        form.date != null &&
                        form.time != null &&
                        !isDateTimeInPast

                // Reset form and go back on discard
                DiscardButton(
                    modifier = Modifier.weight(1f),
                    onDiscard = {
                      form = SessionForm(participants = listOf(account))
                      onBack()
                    })

                // Create a new session if form is valid
                CreateSessionButton(
                    formToSubmit = form,
                    enabled = canCreate,
                    onCreate = {
                      runCatching {
                            val selectedGameId = viewModel.gameUIState.value.selectedGameUid
                            viewModel.createSession(
                                requester = account,
                                discussion = discussion,
                                name = form.title,
                                gameId =
                                    selectedGameId.ifBlank {
                                      form.proposedGameString.ifBlank { LABEL_UNKNOWN_GAME }
                                    },
                                gameName = "Temp game name",
                                date = toTimestamp(form.date, form.time),
                                location = locationUi.selectedLocation ?: Location(),
                                *form.participants.toTypedArray())
                          }
                          .onFailure { e ->
                            showError(e.message ?: "Failed to create session")
                            return@CreateSessionButton
                          }

                      form = SessionForm(participants = listOf(account))
                      onBack()
                    },
                    modifier = Modifier.weight(1f),
                )
              }
        }
      }) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(
                        horizontal = Dimensions.Padding.extraLarge,
                        vertical = Dimensions.Padding.medium)
                    .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                    .verticalScroll(rememberScrollState())
                    .testTag(SessionCreationTestTags.CONTENT_COLUMN),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.none)) {

              // Organisation section (title, game, date, time, location)
              OrganisationSection(
                  gameUi = gameUi,
                  viewModel = viewModel,
                  account = account,
                  discussion = discussion,
                  onTitleChange = {
                    if (it.length <= MAX_TITLE_LENGTH) form = form.copy(title = it)
                  },
                  form = form,
                  date = form.date,
                  time = form.time,
                  onDateChange = { form = form.copy(date = it) },
                  onTimeChange = { form = form.copy(time = it) },
                  onFocusChanged = { isInputFocused = it },
                  modifier = Modifier.testTag(SessionCreationTestTags.ORG_SECTION))

              // Participants section (player selection and slider)
              ParticipantsSection(
                  account = account,
                  selected = form.participants,
                  allCandidates = allDiscussionMembers,
                  minPlayers = gameUi.fetchedGame?.minPlayers ?: 0,
                  maxPlayers = gameUi.fetchedGame?.maxPlayers ?: 0,
                  onAdd = { toAdd ->
                    form =
                        form.copy(participants = (form.participants + toAdd).distinctBy { it.uid })
                  },
                  mainSectionTitle = LABEL_PARTICIPANTS,
                  onRemove = { toRemove ->
                    form =
                        form.copy(
                            participants = form.participants.filterNot { it.uid == toRemove.uid })
                  })
            }
      }
}

/* =======================================================================
 * Components (only test tags added via modifiers; logic unchanged)
 * ======================================================================= */

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
        Spacer(Modifier.width(Dimensions.Spacing.medium))
        Text(LABEL_CREATE, style = MaterialTheme.typography.bodyMedium)
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
      border = BorderStroke(Dimensions.DividerThickness.medium, MaterialTheme.colorScheme.error),
      colors =
          ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Icon(
            Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.testTag(SessionCreationTestTags.DISCARD_ICON))
        Spacer(Modifier.width(Dimensions.Spacing.medium))
        Text(LABEL_DISCARD, style = MaterialTheme.typography.bodyMedium)
      }
}

/**
 * Composable function representing the organisation section of the session creation form.
 *
 * This section allows the user to enter the session title, search and select a game, pick date and
 * time, and search for a location.
 *
 * @param form The current session form state.
 * @param viewModel The FirestoreSessionViewModel for session-specific operations.
 * @param account The current user's account.
 * @param discussion The discussion context for the session.
 * @param onTitleChange Callback when the session title changes.
 * @param date The selected date for the session.
 * @param time The selected time for the session.
 * @param onDateChange Callback when the date changes.
 * @param onTimeChange Callback when the time changes.
 * @param modifier Modifier for styling the composable.
 */
@Composable
fun OrganisationSection(
    gameUi: GameUIState,
    viewModel: CreateSessionViewModel,
    account: Account,
    discussion: Discussion,
    date: LocalDate?,
    time: LocalTime?,
    modifier: Modifier = Modifier,
    form: SessionForm = SessionForm(),
    onTitleChange: (String) -> Unit = {},
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
  SectionCard(
      modifier
          .testTag(SessionCreationTestTags.ORG_SECTION)
          .fillMaxWidth()
          // border is now background color to create no border effect
          .border(
              Dimensions.DividerThickness.standard,
              MaterialTheme.colorScheme.background,
              MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.large)) {
        Text(text = "Basic Info", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(Dimensions.Spacing.small))

        // Title field for session name
        FocusableInputField(
            value = form.title,
            onValueChange = { onTitleChange(it) },
            label = { Text(text = TITLE_PLACEHOLDER) },
            leadingIcon = {
              Icon(
                  Icons.Default.Edit,
                  contentDescription = LABEL_EDIT_TITLE,
                  tint = MaterialTheme.colorScheme.onBackground)
            },
            modifier =
                Modifier.fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .testTag(SessionCreationTestTags.FORM_TITLE_FIELD))

        Spacer(Modifier.height(Dimensions.Spacing.extraMedium))

        // Game search section
        Box(Modifier.onFocusChanged { onFocusChanged(it.isFocused) }) {
          SessionGameSearchBar(account, discussion, viewModel, gameUi.fetchedGame)
        }

        Spacer(Modifier.height(Dimensions.Spacing.xLarge))

        Text(text = "Where & When", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(Dimensions.Spacing.small))

        DateAndTimePicker(date, time, onDateChange, onFocusChanged, onTimeChange)

        Spacer(Modifier.height(Dimensions.Spacing.extraMedium))

        // Location search field with suggestions
        Box(Modifier.onFocusChanged { onFocusChanged(it.isFocused) }) {
          SessionLocationSearchButton(account, discussion, viewModel)
        }
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
    mainSectionTitle: String
) {
  SectionCard(
      modifier
          .testTag(SessionCreationTestTags.PARTICIPANTS_SECTION)
          .fillMaxWidth()
          // border is now background color to create no border effect
          .border(
              Dimensions.DividerThickness.standard,
              MaterialTheme.colorScheme.background,
              MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.large)) {

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
          Column {
            Text(mainSectionTitle, style = MaterialTheme.typography.titleLarge)
            if (minPlayers > 0 && maxPlayers > 0)
                Text(
                    "Recommended: $minPlayers - $maxPlayers",
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textIconsFade)
          }
          Spacer(Modifier.width(Dimensions.Spacing.medium))
        }

        Spacer(Modifier.height(Dimensions.Spacing.medium))

        UserChipsGrid(
            participants = allCandidates,
            account = account,
            selectedParticipants = selected,
            editable = true,
            onToggle = { user, checked ->
              if (checked) {
                onAdd(user)
              } else {
                onRemove(user)
              }
            },
        )
      }
}
