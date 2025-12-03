package com.github.meeplemeet.ui.sessions
// AI was used on this file

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.components.CountBubble
import com.github.meeplemeet.ui.components.DatePickerDockedField
import com.github.meeplemeet.ui.components.DiscretePillSlider
import com.github.meeplemeet.ui.components.IconTextField
import com.github.meeplemeet.ui.components.SectionCard
import com.github.meeplemeet.ui.components.SessionGameSearchBar
import com.github.meeplemeet.ui.components.SessionLocationSearchBar
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.components.UnderlinedLabel
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.appShapes
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

/* =======================================================================
 * Test tags for UI tests
 * ======================================================================= */

// Object holding test tags for UI testing purposes.
object SessionTestTags {
  const val LOCATION_PICKER_DIALOG = "location_picker_dialog"
  const val LOCATION_PICKER_BUTTON = "location_picker_button"
  const val TITLE = "session_title"
  const val PROPOSED_GAME = "proposed_game"
  const val MIN_PLAYERS = "min_players"
  const val MAX_PLAYERS = "max_players"
  const val PARTICIPANT_CHIPS = "participant_chips"
  const val ADD_PARTICIPANT_BUTTON = "add_participant_button"
  const val ADD_PARTICIPANT_SEARCH = "add_participant_search"
  const val DISCRETE_PILL_SLIDER = "discrete_pill_slider"
  const val DATE_FIELD = "date_field"
  const val TIME_FIELD = "time_field"
  const val LOCATION_FIELD = "location_field"
  const val LOCATION_FIELD_ITEM = "location_field_item"
  const val QUIT_BUTTON = "quit_button"
  const val DATE_PICKER_OK_BUTTON = "date_picker_ok_button"
  const val DATE_PICK_BUTTON = "date_pick_button"
  const val TIME_PICK_BUTTON = "time_pick_button"
  const val TIME_PICKER_OK_BUTTON = "time_picker_ok_button"
  const val DELETE_SESSION_BUTTON = "delete_session_button"

  fun chipsTag(uid: String) = "chip${uid}"

  fun addParticipantTag(uid: String) = "add_participant_item:${uid}"

  fun removeParticipantTag(uid: String) = "remove:${uid}"
}
/* =======================================================================
 * Magic String/Numbers
 * ======================================================================= */

private const val LABEL_PARTICIPANTS = "Participants:"
private const val LABEL_NUM_PLAYERS = "Number of players"
private const val LABEL_TITLE = "Title"
private const val PLACEHOLDER_TIME = "Time"
private const val BUTTON_PICK = "Pick"
private const val BUTTON_LEAVE = "Leave"
private const val BUTTON_DELETE = "Delete"
private const val BUTTON_ADD = "+"
private const val PLACEHOLDER_SEARCH = "Search"
private const val PLACEHOLDER_LOCATION = "Location"
private const val TEXT_LOADING = "Loading..."
private const val SESSION_DETAILS_TITLE = "Session Details"
const val MAX_TITLE_LENGTH: Int = 100

/* =======================================================================
 * Helpers
 * ======================================================================= */

/**
 * Helper function to convert a Firebase Timestamp to a LocalDate and LocalTime pair.
 *
 * @param timestamp The Firebase Timestamp to convert.
 * @return Pair containing the corresponding LocalDate and LocalTime.
 */
fun timestampToLocal(timestamp: Timestamp): Pair<LocalDate, LocalTime> {
  val instant = Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())
  val zone = ZoneId.systemDefault()
  val dateTime = LocalDateTime.ofInstant(instant, zone)
  return dateTime.toLocalDate() to dateTime.toLocalTime()
}

/**
 * Main composable for the Session View screen. Displays session details, participants, proposed
 * game, and organizational info. Handles data loading and user interactions for both admins and
 * regular members.
 *
 * @param viewModel Global FirestoreViewModel for retrieving discussions
 * @param viewModel ViewModel managing session-specific operations
 * @param account Currently logged-in user
 * @param initial Initial session form state (optional)
 * @param discussion The discussion linked to the session
 * @param onBack Callback triggered when navigating back
 */
@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsScreen(
    account: Account,
    discussion: Discussion,
    viewModel: SessionViewModel = viewModel(key = discussion.uid),
    initial: SessionForm = SessionForm(),
    onBack: () -> Unit = {},
) {
  var form by remember { mutableStateOf(initial) }
  val gameUIState by viewModel.gameUIState.collectAsState()
  val locationUi by viewModel.locationUIState.collectAsState()

  val snackbar = remember { SnackbarHostState() }
  val focusManager = LocalFocusManager.current
  var isInputFocused by remember { mutableStateOf(false) }

  // Fetch game as soon as we know the proposed game.
  // This LaunchedEffect triggers whenever the session's gameId changes,
  // ensuring the UI always has the latest game info.
  val sessionGameId = discussion.session?.gameId.orEmpty()
  LaunchedEffect(sessionGameId) {
    if (sessionGameId.isNotBlank()) {
      viewModel.getGameFromId(sessionGameId)
    }
  }

  val game = gameUIState.fetchedGame
  val session = discussion.session!!

  // This LaunchedEffect block updates the form state when the session changes.
  // It fetches participant accounts and updates the UI fields accordingly.
  // If the game info is missing, it triggers a fetch for the proposed game.
  LaunchedEffect(discussion.uid) {
    val (date, time) = timestampToLocal(session.date)

    val accounts = RepositoryProvider.accounts.getAccounts(session.participants)
    form =
        form.copy(
            title = session.name,
            date = date,
            time = time,
            proposedGameString = session.gameId,
            participants = accounts,
            locationText = session.location.name)

    if (form.proposedGameString.isNotBlank()) viewModel.getGameFromId(form.proposedGameString)

    if (session.gameId.isNotBlank() && game == null) viewModel.getGameFromId(session.gameId)
  }

  // Determine if the current user is an admin or the creator of the discussion.
  val isCurrUserAdmin =
      account.uid == discussion.creatorId || discussion.admins.contains(account.uid)

  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = SESSION_DETAILS_TITLE,
            onReturn = {
              // Only admins/owners can persist changes to the session on back navigation.
              // If validation fails, navigate back without saving changes.
              if (isCurrUserAdmin && !isDateTimeInPast(form.date, form.time)) {
                viewModel.updateSession(
                    requester = account,
                    discussion = discussion,
                    name = form.title,
                    gameId = gameUIState.fetchedGame?.uid ?: session.gameId,
                    date = toTimestamp(form.date, form.time),
                    location = locationUi.selectedLocation ?: session.location)
              }
              onBack()
            },
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
                      .background(AppColors.primary)
                      .padding(
                          horizontal = Dimensions.Spacing.xxxLarge,
                          vertical = Dimensions.Padding.extraMedium),
              horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {
                OutlinedButton(
                    onClick = {
                      viewModel.removeUserFromSession(discussion, account, account)
                      onBack()
                    },
                    shape = CircleShape,
                    border = BorderStroke(Dimensions.DividerThickness.medium, AppColors.negative),
                    modifier = Modifier.weight(1f).testTag(SessionTestTags.QUIT_BUTTON),
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                      Spacer(Modifier.width(Dimensions.Spacing.medium))
                      Text(BUTTON_LEAVE, style = MaterialTheme.typography.bodyMedium)
                    }

                // "Delete" button is only visible for admins/owners (see DeleteSessionBTN).
                DeleteSessionBTN(
                    viewModel = viewModel,
                    currentUser = account,
                    discussion = discussion,
                    userIsAdmin = isCurrUserAdmin,
                    modifier = Modifier.weight(1f))
              }
        }
      }) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .background(AppColors.primary)
                    .padding(innerPadding)
                    .padding(
                        horizontal = Dimensions.Spacing.extraLarge,
                        vertical = Dimensions.Spacing.medium)
                    .pointerInput(Unit) {
                      detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {

              // Organisation section (session info and controls)
              // Editable for admins and the session creator, read-only for members.
              OrganizationSection(
                  form = form,
                  onFormChange = { form = it },
                  editable = isCurrUserAdmin,
                  discussion = discussion,
                  account = account,
                  gameUIState = gameUIState,
                  onValueChangeTitle = {
                    if (it.length <= MAX_TITLE_LENGTH) form = form.copy(title = it)
                  },
                  isCurrUserAdmin = isCurrUserAdmin,
                  sessionViewModel = viewModel,
                  onFocusChanged = { isInputFocused = it })

              // Participants section (chips, add/remove)
              ParticipantsSection(
                  form = form,
                  editable = isCurrUserAdmin,
                  account = account,
                  game = game,
                  onRemoveParticipant = { p ->
                    form = form.copy(participants = form.participants.filterNot { it.uid == p.uid })
                    viewModel.removeUserFromSession(discussion, account, p)
                  },
                  onAddParticipant = { viewModel.addUserToSession(discussion, account, it) },
                  discussion = discussion,
                  viewModel = viewModel,
                  onFocusChanged = { isInputFocused = it })
            }
      }
}

/* =======================================================================
 * Sub-components
 * ======================================================================= */

@Composable
fun ParticipantsSection(
    form: SessionForm,
    account: Account,
    editable: Boolean = false,
    game: Game?,
    onRemoveParticipant: (Account) -> Unit,
    onAddParticipant: (Account) -> Unit,
    discussion: Discussion,
    viewModel: SessionViewModel,
    onFocusChanged: (Boolean) -> Unit = {}
) {
  val participants = form.participants
  val currentCount = participants.size

  // Fetch discussion members (UID -> Account) once and keep in state
  var candidateAccounts by remember { mutableStateOf<List<Account>>(emptyList()) }
  LaunchedEffect(discussion.participants) {
    viewModel.getAccounts(discussion.participants) { accounts -> candidateAccounts = accounts }
  }

  // UI shell
  SectionCard(modifier = Modifier.clip(appShapes.extraLarge).background(AppColors.primary)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      UnderlinedLabel(
          text = LABEL_PARTICIPANTS,
          textColor = AppColors.textIcons,
          textStyle = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.width(Dimensions.Spacing.medium))
      CountBubble(
          count = currentCount,
          modifier =
              Modifier.clip(CircleShape)
                  .background(AppColors.affirmative)
                  .border(Dimensions.DividerThickness.standard, AppColors.affirmative, CircleShape)
                  .padding(
                      horizontal = Dimensions.Spacing.large,
                      vertical = Dimensions.Padding.mediumSmall))
    }

    Spacer(Modifier.height(Dimensions.Spacing.large))

    // Slider (visual-only)
    if (game != null) {
      PillSliderNoBackground(
          title = LABEL_NUM_PLAYERS,
          range = (game.minPlayers.toFloat() - 1)..(game.maxPlayers.toFloat() + 1),
          values = game.minPlayers.toFloat()..game.maxPlayers.toFloat(),
          steps = (game.maxPlayers - game.minPlayers + 1).coerceAtLeast(0))

      // Min/Max bubbles (below)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CountBubble(
            count = game.minPlayers,
            modifier =
                Modifier.clip(CircleShape)
                    .background(AppColors.secondary)
                    .border(Dimensions.DividerThickness.standard, AppColors.secondary, CircleShape)
                    .padding(
                        horizontal = Dimensions.Spacing.large,
                        vertical = Dimensions.Padding.mediumSmall)
                    .testTag(SessionTestTags.MIN_PLAYERS))
        CountBubble(
            count = game.maxPlayers,
            modifier =
                Modifier.clip(CircleShape)
                    .background(AppColors.secondary)
                    .border(Dimensions.DividerThickness.standard, AppColors.secondary, CircleShape)
                    .padding(
                        horizontal = Dimensions.Spacing.large,
                        vertical = Dimensions.Padding.mediumSmall)
                    .testTag(SessionTestTags.MAX_PLAYERS))
      }
    }

    Spacer(Modifier.height(Dimensions.Spacing.extraMedium))

    // 👉 Delegate chips + add-button + dropdown to UserChipsGrid
    UserChipsGrid(
        participants = participants,
        onRemove = onRemoveParticipant,
        account = account,
        editable = editable,
        candidateMembers = candidateAccounts, // full discussion members as Accounts
        modifier = Modifier.testTag(SessionTestTags.PARTICIPANT_CHIPS))
  }
}

/**
 * Component used to display the participants in a scrollable vertical list. Each chip shows a
 * participant name and optionally a remove button for admins.
 *
 * @param participants List of participants to display
 * @param onRemove Callback fn used when an Admin/Owner removes a participant
 * @param modifier Modifiers used for the component
 * @param account The current user that's viewing the session details
 * @param editable Whether the current user can edit (remove) participants
 */
@Composable
fun UserChipsGrid(
    participants: List<Account>,
    onRemove: (Account) -> Unit,
    modifier: Modifier = Modifier,
    account: Account,
    editable: Boolean = false,
    candidateMembers: List<Account> = emptyList(),
    onAdd: ((Account) -> Unit)? = null,
    useCheckboxes: Boolean = false,
    selectedParticipants: List<Account> = emptyList()
) {
  var searchQuery by remember { mutableStateOf("") }

  // Set up vertical scroll with a maximum height
  val maxHeight = Dimensions.ContainerSize.mapHeight
  val scrollState = rememberScrollState()

  Column(
      modifier = modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(scrollState),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
        // Existing participant chips - now full width rectangles
        participants.forEach { p ->
          val isSelected = selectedParticipants.any { it.uid == p.uid }
          UserChip(
              user = p,
              modifier = Modifier.fillMaxWidth().testTag(SessionTestTags.chipsTag(p.uid)),
              onRemove = { if (editable) onRemove(p) },
              account = account,
              showRemoveBTN = !useCheckboxes && editable,
              showCheckbox = useCheckboxes,
              isChecked = isSelected,
              onCheckedChange =
                  if (useCheckboxes && onAdd != null) {
                    { checked ->
                      if (checked) {
                        onAdd(p)
                      } else {
                        onRemove(p)
                      }
                    }
                  } else null)
        }
      }
}

@Composable
private fun AvatarBubble(name: String) {
  Box(
      modifier =
          Modifier.size(Dimensions.AvatarSize.tiny).clip(CircleShape).background(Color.LightGray),
      contentAlignment = Alignment.Center) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = AppColors.focus,
            fontWeight = FontWeight.Bold)
      }
}

@Composable
private fun ProposedGameSection(
    viewModel: SessionViewModel,
    currentUser: Account,
    discussion: Discussion,
    editable: Boolean,
    gameUIState: GameUIState,
) {

  Column(modifier = Modifier.fillMaxWidth()) {
    if (editable) {
      SessionGameSearchBar(currentUser, discussion, viewModel, gameUIState.fetchedGame)
    } else {
      val displayedName = gameUIState.fetchedGame?.name ?: TEXT_LOADING
      Row {
        UnderlinedLabel(
            text = "Proposed Game:",
            textColor = AppColors.textIcons,
            textStyle = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.width(Dimensions.Spacing.large))
        Text(
            text = displayedName,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.testTag(SessionTestTags.PROPOSED_GAME)
                    .padding(top = Dimensions.Spacing.small),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.textIcons)
      }
    }
  }
}

/**
 * Displays and manages the organizational details of the session. Includes date, time, and location
 * fields, with editable options for admins.
 *
 * @param form Current session form data
 * @param onFormChange Callback triggered when form data changes
 * @param editable Whether the section is editable (admin-only)
 */
@Composable
fun OrganizationSection(
    sessionViewModel: SessionViewModel,
    account: Account,
    onValueChangeTitle: (String) -> Unit,
    isCurrUserAdmin: Boolean,
    gameUIState: GameUIState,
    discussion: Discussion,
    form: SessionForm,
    onFormChange: (SessionForm) -> Unit,
    editable: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {}
) {
  SectionCard(
      modifier = Modifier.clip(appShapes.extraLarge).background(AppColors.primary).fillMaxWidth()) {
        Title(
            text = form.title,
            editable = isCurrUserAdmin,
            onValueChange = { onValueChangeTitle(it) },
            onFocusChanged = onFocusChanged,
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .then(Modifier.testTag(SessionTestTags.TITLE)))

        Spacer(Modifier.height(Dimensions.Spacing.extraMedium))

        Box(Modifier.onFocusChanged { onFocusChanged(it.isFocused) }) {
          ProposedGameSection(
              viewModel = sessionViewModel,
              currentUser = account,
              discussion = discussion,
              editable = editable,
              gameUIState = gameUIState)
        }

        Spacer(Modifier.height(Dimensions.Spacing.extraMedium))

        Box(Modifier.onFocusChanged { onFocusChanged(it.isFocused) }) {
          DatePickerDockedField(
              value = form.date,
              editable = editable,
              onValueChange = { onFormChange(form.copy(date = it!!)) },
          )
        }

        Spacer(Modifier.height(Dimensions.Spacing.extraMedium))

        // Time field using the new TimeField composable
        Column {
          Box(Modifier.onFocusChanged { onFocusChanged(it.isFocused) }) {
            TimeField(
                value = form.time.toString(),
                onValueChange = { onFormChange(form.copy(time = it)) },
                editable = editable,
                modifier = Modifier.fillMaxWidth())
          }

          // Show error if date/time is in the past (only for admins who can edit)
          if (editable && isDateTimeInPast(form.date, form.time)) {
            Spacer(Modifier.height(Dimensions.Spacing.extraSmall))
            Text(
                text = "Cannot update session to a time in the past",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimensions.Padding.medium))
          }
        }
        Spacer(Modifier.height(Dimensions.Spacing.extraMedium))

        if (editable) {
          // Admins and creators: interactive search field
          Box(Modifier.onFocusChanged { onFocusChanged(it.isFocused) }) {
            SessionLocationSearchBar(
                account,
                discussion,
                sessionViewModel,
            )
          }
        } else {
          // Members: plain read-only text field
          IconTextField(
              value = form.locationText,
              editable = false,
              onValueChange = { if (editable) onFormChange(form.copy(locationText = it)) },
              placeholder = PLACEHOLDER_LOCATION,
              leadingIcon = {
                Icon(Icons.Default.LocationOn, contentDescription = PLACEHOLDER_LOCATION)
              },
              modifier = Modifier.testTag(SessionTestTags.LOCATION_FIELD).fillMaxWidth(),
              textStyle = MaterialTheme.typography.bodySmall,
          )
        }
      }
}

/**
 * Displays the session title, either as editable text input for admins or plain text for members.
 *
 * @param text Title text
 * @param editable Whether the field is editable
 * @param onValueChange Callback triggered when the title changes
 * @param modifier Modifier applied to the composable
 */
@Composable
fun Title(
    text: String,
    editable: Boolean = false,
    onValueChange: (String) -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier
) {
  if (editable) {
    FocusableInputField(
        value = text,
        label = { Text(LABEL_TITLE, color = AppColors.textIconsFade) },
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        modifier = modifier.fillMaxWidth().onFocusChanged { onFocusChanged(it.isFocused) })
  } else {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = AppColors.textIcons,
        modifier = modifier)
  }
}

/**
 * Composable used for the individual UserChip - now a full-width rectangular item
 *
 * @param user User's account (needed for his name and handle)
 * @param onRemove Callback fn used to remove the user
 * @param modifier Modifiers to apply to this component
 * @param showRemoveBTN Should only be visible to admins/owners
 */
@Composable
fun UserChip(
    user: Account,
    account: Account,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    showRemoveBTN: Boolean = false,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    showCheckbox: Boolean = false
) {
  Surface(
      modifier = modifier,
      shape = appShapes.medium,
      color = AppColors.primary,
      tonalElevation = Dimensions.Elevation.minimal) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
              // Avatar and name
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
                  modifier = Modifier.weight(1f)) {
                    // Avatar
                    Box(
                        modifier =
                            Modifier.size(Dimensions.AvatarSize.small)
                                .clip(CircleShape)
                                .background(Color.LightGray),
                        contentAlignment = Alignment.Center) {
                          Text(
                              text = user.name.firstOrNull()?.toString() ?: "A",
                              color = AppColors.focus,
                              fontWeight = FontWeight.Bold,
                              style = MaterialTheme.typography.bodyMedium)
                        }

                    // Name and handle
                    Column {
                      Text(
                          text = user.name,
                          style = MaterialTheme.typography.bodyMedium,
                          color = AppColors.textIcons,
                          fontWeight = FontWeight.Medium)
                      Text(
                          text = "@${user.handle}",
                          style = MaterialTheme.typography.bodySmall,
                          color = AppColors.textIconsFade)
                    }
                  }

              // Checkbox or Remove button
              if (showCheckbox && onCheckedChange != null) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag(SessionTestTags.removeParticipantTag(user.name)),
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = AppColors.neutral,
                            uncheckedColor = AppColors.textIconsFade))
              } else if (showRemoveBTN && account.handle != user.handle) {
                IconButton(
                    onClick = onRemove,
                    modifier =
                        Modifier.size(Dimensions.IconSize.large)
                            .testTag(SessionTestTags.removeParticipantTag(user.name))) {
                      Icon(
                          imageVector = Icons.Default.Close,
                          contentDescription = "Remove participant",
                          tint = AppColors.negative)
                    }
              }
            }
      }
}

/**
 * Compact, discrete "pill" styled range slider with subtle rounded track & dots. This mirrors the
 * blue/red dotted pills in the mock (generic visual).
 *
 * @param title Text to display with the pill slider
 * @param range Range of values that the slider can attain
 * @param values Values that be attained by the slider
 * @param steps Number of steps to display
 */
@Composable
fun PillSliderNoBackground(
    title: String,
    range: ClosedFloatingPointRange<Float>,
    values: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
  Column {
    Text(title, style = MaterialTheme.typography.labelSmall, color = AppColors.textIconsFade)
    Spacer(Modifier.height(Dimensions.Padding.mediumSmall))
    DiscretePillSlider(
        range = range,
        values = values,
        steps = steps,
        modifier =
            Modifier.fillMaxWidth()
                .background(AppColors.primary, CircleShape)
                .border(Dimensions.DividerThickness.standard, AppColors.primary, CircleShape)
                .padding(horizontal = Dimensions.Spacing.large, vertical = Dimensions.Padding.tiny),
        sliderModifier = Modifier.testTag(SessionTestTags.DISCRETE_PILL_SLIDER),
        sliderColors =
            SliderDefaults.colors(
                activeTrackColor = AppColors.neutral,
                inactiveTrackColor = AppColors.divider,
                thumbColor = AppColors.neutral))
  }
}

/**
 * Dialog composable for picking a time using the Material3 TimePicker.
 *
 * @param onDismiss Callback when the dialog is dismissed.
 * @param onTimeSelected Callback with the selected LocalTime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(onDismiss: () -> Unit, onTimeSelected: (LocalTime) -> Unit) {
  // Initialize state with current time
  val calendar = Calendar.getInstance()
  val initialHour = calendar.get(Calendar.HOUR_OF_DAY)
  val initialMinute = calendar.get(Calendar.MINUTE)

  val timePickerState =
      rememberTimePickerState(
          initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)

  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = AppColors.primary,
      confirmButton = {
        TextButton(
            onClick = {
              val h = timePickerState.hour
              val m = timePickerState.minute
              val selectedTime = LocalTime.of(h, m)
              onTimeSelected(selectedTime)
              onDismiss()
            },
            modifier = Modifier.testTag(SessionTestTags.TIME_PICKER_OK_BUTTON)) {
              Text("OK")
            }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
      text = {
        // Use the Material3 TimePicker (dial) inside the dialog
        TimeInput(
            state = timePickerState,
            colors =
                TimePickerDefaults.colors(
                    clockDialColor = AppColors.secondary,
                    clockDialSelectedContentColor = AppColors.primary,
                    clockDialUnselectedContentColor = AppColors.textIconsFade,
                    selectorColor = AppColors.neutral,
                    periodSelectorBorderColor = AppColors.textIconsFade,
                    periodSelectorSelectedContainerColor = AppColors.secondary,
                    periodSelectorSelectedContentColor = AppColors.negative,
                    timeSelectorSelectedContainerColor = AppColors.neutral,
                    timeSelectorUnselectedContainerColor = AppColors.secondary,
                ))
      })
}

/**
 * Composable used to display an interactive time field. Shows a time value and, if editable, allows
 * the user to pick a new time via a dialog.
 *
 * @param value Time set (as a string)
 * @param onValueChange Callback fn when time is changed
 * @param modifier Modifiers that want to be passed to the composable
 * @param editable Whether the composable should be made editable depending on the current user's
 *   permissions
 */
@Composable
fun TimeField(
    value: String,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false
) {
  var showDialogTime by remember { mutableStateOf(false) }

  IconTextField(
      value = value,
      onValueChange = {}, // controlled externally
      placeholder = PLACEHOLDER_TIME,
      editable = editable,
      leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = PLACEHOLDER_TIME) },
      trailingIcon = {
        if (editable) {
          TextButton(
              onClick = { showDialogTime = true },
              modifier = Modifier.testTag(SessionTestTags.TIME_PICK_BUTTON)) {
                Text(BUTTON_PICK)
              }
        }
      },
      modifier = modifier.testTag(SessionTestTags.TIME_FIELD))

  // Show the time picker dialog when requested.
  if (showDialogTime) {
    TimePickerDialog(
        onDismiss = { showDialogTime = false }, onTimeSelected = { sel -> onValueChange(sel) })
  }
}

/**
 * Deletes the currently viewed session - Only accessible with Admin/Owner rights
 *
 * @param viewModel ViewModel used to delete the session
 * @param currentUser User performing the action
 * @param discussion Discussion the session is tied to
 * @param userIsAdmin Boolean to check whether the user can see this button
 * @param modifier Modifier for the button layout
 */
@Composable
fun DeleteSessionBTN(
    viewModel: SessionViewModel,
    currentUser: Account,
    discussion: Discussion,
    userIsAdmin: Boolean,
    modifier: Modifier = Modifier
) {
  if (userIsAdmin) {
    OutlinedButton(
        onClick = { viewModel.deleteSession(currentUser, discussion) },
        shape = CircleShape,
        border = BorderStroke(Dimensions.DividerThickness.medium, AppColors.negative),
        modifier = modifier.testTag(SessionTestTags.DELETE_SESSION_BUTTON),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
          Icon(Icons.Default.Delete, contentDescription = "Delete Session")
          Spacer(Modifier.width(Dimensions.Spacing.medium))
          Text(BUTTON_DELETE, style = MaterialTheme.typography.bodyMedium)
        }
  }
}
