package com.github.meeplemeet.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Location
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.components.CountBubble
import com.github.meeplemeet.ui.components.DatePickerDockedField
import com.github.meeplemeet.ui.components.DiscretePillSlider
import com.github.meeplemeet.ui.components.GameSearchField
import com.github.meeplemeet.ui.components.IconTextField
import com.github.meeplemeet.ui.components.LocationSearchField
import com.github.meeplemeet.ui.components.SectionCard
import com.github.meeplemeet.ui.components.UnderlinedLabel
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.appShapes
import java.time.LocalTime
import java.util.Calendar
import kotlin.math.roundToInt

/* =======================================================================
 * Test tags for UI tests
 * ======================================================================= */

// Object holding test tags for UI testing purposes.
object SessionTestTags {
  const val TITLE = "session_title"
  const val PROPOSED_GAME = "proposed_game"
  const val MIN_PLAYERS = "min_players"
  const val MAX_PLAYERS = "max_players"
  const val PARTICIPANT_CHIPS = "participant_chips"
  const val DATE_FIELD = "date_field"
  const val TIME_FIELD = "time_field"
  const val LOCATION_FIELD = "location_field"
  const val QUIT_BUTTON = "quit_button"
  const val DATE_PICKER_OK_BUTTON = "date_picker_ok_button"
  const val DATE_PICK_BUTTON = "date_pick_button"
  const val TIME_PICK_BUTTON = "time_pick_button"
  const val TIME_PICKER_OK_BUTTON = "time_picker_ok_button"
  const val CHAT_BADGE = "chat_badge"
  const val NOTIFICATION_BADGE_COUNT = "notification_badge_count"
  const val EMPTY_BADGE = "empty_badge"
  const val DELETE_SESSION_BUTTON = "delete_session_button"
}

/* =======================================================================
 * Models
 * ======================================================================= */

val sliderMinRange = 2f
val sliderMaxRange = 10f

/* =======================================================================
 * Public entry point
 * ======================================================================= */

/**
 * Main composable for the Session View screen. Displays session details, participants, proposed
 * game, and organizational info.
 *
 * @param viewModel Global FirestoreViewModel for retrieving discussions
 * @param sessionViewModel ViewModel managing session-specific operations
 * @param currentUser Currently logged-in user
 * @param initial Initial session form state (optional)
 * @param discussionId ID of the discussion linked to the session
 * @param onBack Callback triggered when navigating back
 * @param onclickNotification Callback when clicking the notifications icon
 * @param onclickChat Callback when clicking the chat icon
 */
@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionViewScreen(
    viewModel: FirestoreViewModel,
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    initial: SessionForm = SessionForm(),
    discussionId: String,
    onBack: () -> Unit = {},
    onclickNotification: () -> Unit = {},
    onclickChat: () -> Unit = {}
) {
  // Local state for the session form data.
  var form by remember { mutableStateOf(initial) }

  // Observe discussion updates from the view model.
  val discussion by viewModel.discussionFlow(discussionId).collectAsState()
  val isCurrUserAdmin =
      currentUser.uid == discussion?.creatorId ||
          discussion?.admins?.contains(currentUser.uid) == true

  // Scaffold provides the top bar and main content area.
  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = "Session View",
            onReturn = onBack,
            { TopRightIcons(onclickChat = onclickChat, onclickNotification = onclickNotification) })
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(AppColors.primary)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {

          // Session title (centered, not editable for now).
          Title(
              text = form.title.ifEmpty { "New Session" },
              editable = isCurrUserAdmin,
              form,
              onValueChange = { newTitle -> form = form.copy(title = newTitle) },
              modifier =
                  Modifier.align(Alignment.CenterHorizontally)
                      .then(Modifier.testTag(SessionTestTags.TITLE)))

          // Proposed game section
          // background and border are primary for members since it blends with the screen bg
          // proposed game is a text for members, it's not in a editable box
          discussion?.let { disc ->
            ProposedGameSection(
                sessionViewModel = sessionViewModel,
                currentUser = currentUser,
                discussion = disc, // safe – non-null here
                editable = isCurrUserAdmin)
          } ?: Box(Modifier.fillMaxWidth()) {}

          // Participants section
          ParticipantsSection(
              form = form,
              editable = isCurrUserAdmin,
              onFormChange = { min, max ->
                form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
              },
              onRemoveParticipant = { p ->
                form = form.copy(participants = form.participants.filterNot { it.uid == p.uid })
              })

          // Organisation section
          // editable for admins and the session creator, read-only for members
          OrganizationSection(form, onFormChange = { form = it }, editable = isCurrUserAdmin)

          Spacer(Modifier.height(4.dp))

          // Quit session button
          OutlinedButton(
              onClick = {
                onBack()
                val updatedParticipants = form.participants.filterNot { it.uid == currentUser.uid }
                discussion?.let { disc ->
                  sessionViewModel.updateSession(
                      requester = currentUser,
                      discussion = disc,
                      newParticipantList = updatedParticipants)
                }
                onBack()
              },
              shape = CircleShape,
              border = BorderStroke(1.5.dp, AppColors.negative),
              modifier = Modifier.testTag(SessionTestTags.QUIT_BUTTON).fillMaxWidth(),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Quit Session", style = MaterialTheme.typography.bodyMedium)
              }

          discussion?.let { disc ->
            DeleteSessionBTN(
                sessionViewModel = sessionViewModel,
                currentUser = currentUser,
                discussion = disc,
                userIsAdmin = isCurrUserAdmin,
                onback = onBack)
          }
        }
  }
}

/* =======================================================================
 * Sub-components
 * ======================================================================= */

/**
 * Displays the participants section of the session. Shows participant count, editable range slider
 * for player limits, and participant chips.
 *
 * @param form Current session form data
 * @param onFormChange Callback triggered when player limits are adjusted
 * @param editable Whether the section is editable (admin-only)
 * @param onRemoveParticipant Callback to remove a participant
 */
@Composable
fun ParticipantsSection(
    form: SessionForm,
    onFormChange: (Float, Float) -> Unit,
    editable: Boolean = false,
    onRemoveParticipant: (Account) -> Unit
) {
  SectionCard(
      modifier =
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.primary)
              .border(1.dp, AppColors.secondary, shape = appShapes.extraLarge)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          UnderlinedLabel(
              text = "Participants:",
              textColor = AppColors.textIcons,
              textStyle = MaterialTheme.typography.titleLarge,
          )
          Spacer(Modifier.width(8.dp))
          CountBubble(
              count = form.participants.size,
              modifier =
                  Modifier.clip(CircleShape)
                      .background(AppColors.affirmative)
                      .border(1.dp, AppColors.affirmative, CircleShape)
                      .padding(horizontal = 10.dp, vertical = 6.dp))
        }

        Spacer(Modifier.height(12.dp))

        // Slider for selecting min and max number of players.
        PillSliderNoBackground(
            title = "Number of players",
            editable = editable,
            range = sliderMinRange..sliderMaxRange,
            values = form.minPlayers.toFloat()..form.maxPlayers.toFloat(),
            steps = 7,
            onValuesChange = { min, max -> onFormChange(min, max) })

        // Min/max bubbles of the slider
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          CountBubble(
              count = form.minPlayers,
              modifier =
                  Modifier.clip(CircleShape)
                      .background(AppColors.secondary)
                      .border(1.dp, AppColors.secondary, CircleShape)
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                      .testTag(SessionTestTags.MIN_PLAYERS))
          CountBubble(
              count = form.maxPlayers,
              modifier =
                  Modifier.clip(CircleShape)
                      .background(AppColors.secondary)
                      .border(1.dp, AppColors.secondary, CircleShape)
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                      .testTag(SessionTestTags.MAX_PLAYERS))
        }
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.height(12.dp))

        // Participant chips (grid of users, removable).
        UserChipsGrid(
            participants = form.participants,
            onRemove = { p -> onRemoveParticipant(p) },
            editable = editable)
      }
}

/**
 * Displays the proposed game section for the session. Admins can search and update the game, while
 * members see it as read-only.
 *
 * @param sessionViewModel ViewModel managing session operations
 * @param currentUser Currently logged-in user
 * @param discussion The discussion the session belongs to
 * @param editable Whether the current user can modify the proposed game
 */
@Composable
private fun ProposedGameSection(
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussion: Discussion,
    editable: Boolean
) {
  val gameUIState by sessionViewModel.gameUIState.collectAsState()

  SectionCard(
      modifier =
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.primary)
              .border(1.dp, AppColors.primary)) {
        Column(modifier = Modifier.fillMaxWidth()) {
          UnderlinedLabel(
              text = "Proposed game:",
              textColor = AppColors.textIcons,
              textStyle = MaterialTheme.typography.titleLarge)

          Spacer(Modifier.height(8.dp))

          if (editable) {
            GameSearchField(
                query = gameUIState.gameQuery,
                onQueryChange = { sessionViewModel.setGameQuery(currentUser, discussion, it) },
                results = gameUIState.gameSuggestions,
                onPick = { sessionViewModel.setGame(currentUser, discussion, it) },
                isLoading = false,
                modifier = Modifier.fillMaxWidth().testTag(SessionTestTags.PROPOSED_GAME))
          } else {
            Text(
                text = "Current Game",
                modifier = Modifier.testTag(SessionTestTags.PROPOSED_GAME).padding(top = 4.dp),
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
    form: SessionForm,
    onFormChange: (SessionForm) -> Unit,
    editable: Boolean = false
) {
  val mockResults =
      listOf(
          Location(46.5197, 6.6323, "Student Lounge"),
          Location(46.5191, 6.5668, "Rolex Learning Center"),
          Location(46.5221, 6.5674, "Satellite Café"))

  SectionCard(
      modifier =
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.primary)
              .border(1.dp, AppColors.secondary, shape = appShapes.extraLarge)
              .fillMaxWidth()) {
        UnderlinedLabel(
            text = "Organisation:",
            textColor = AppColors.textIcons,
            textStyle = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        DatePickerDockedField(
            value = form.date,
            editable = editable,
            onValueChange = { onFormChange(form.copy(date = it!!)) },
        )

        Spacer(Modifier.height(10.dp))

        // Time field using the new TimeField composable
        TimeField(
            value = form.time.toString(),
            onValueChange = { onFormChange(form.copy(time = it)) },
            editable = editable,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))

        if (editable) {
          // Admins and creators: interactive search field
          LocationSearchField(
              query = form.locationText,
              onQueryChange = { newQuery -> onFormChange(form.copy(locationText = newQuery)) },
              results = mockResults,
              onPick = { picked -> onFormChange(form.copy(locationText = picked.name)) },
              isLoading = false, // can hook into your VM later
              placeholder = "Search locations…")
        } else {
          // Members: plain read-only text field
          IconTextField(
              value = form.locationText,
              editable = false,
              onValueChange = { if (editable) onFormChange(form.copy(locationText = it)) },
              placeholder = "Location",
              leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") },
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
 * @param form Current session form data
 * @param onValueChange Callback triggered when the title changes
 * @param modifier Modifier applied to the composable
 */
@Composable
fun Title(
    text: String,
    editable: Boolean = false,
    form: SessionForm,
    onValueChange: (String) -> Unit = {},
    modifier: Modifier
) {
  if (editable) {
    OutlinedTextField(
        value = text,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.headlineLarge,
        singleLine = true,
        modifier = modifier.fillMaxWidth())
  } else {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = AppColors.textIcons,
        modifier = modifier)
  }
}

/**
 * Displays the notification and chat icons with optional badges.
 *
 * @param onclickNotification Callback for the notification icon
 * @param onclickChat Callback for the chat icon
 */
@Composable
fun TopRightIcons(onclickNotification: () -> Unit = {}, onclickChat: () -> Unit = {}) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    BadgedIconButton(
        icon = Icons.Default.Notifications,
        contentDescription = "Notifications",
        modifier = Modifier.testTag(SessionTestTags.NOTIFICATION_BADGE_COUNT),
        badgeCount = 3, // Example badge count for notifications
        onClick = { onclickNotification() })
    BadgedIconButton(
        icon = Icons.Default.ChatBubbleOutline,
        modifier = Modifier.testTag(SessionTestTags.CHAT_BADGE),
        contentDescription = "Messages",
        badgeCount = 0,
        onClick = { onclickChat() })
  }
}

/**
 * Composable used for the individual UserChip
 *
 * @param name User's name
 * @param onRemove Callback fn used to remove the user
 * @param modifier Modifiers to apply to this component
 * @param showRemoveBTN Should only be visible to admins/owners
 */
@Composable
fun UserChip(
    name: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    showRemoveBTN: Boolean = false
) {
  InputChip(
      selected = false,
      onClick = {},
      label = { Text(text = name, style = MaterialTheme.typography.bodySmall) },
      avatar = {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.LightGray),
            contentAlignment = Alignment.Center) {
              Text(
                  text = name.firstOrNull()?.toString() ?: "A",
                  color = AppColors.focus,
                  fontWeight = FontWeight.Bold)
            }
      },
      trailingIcon = {
        if (showRemoveBTN) {
          IconButton(
              onClick = onRemove, modifier = Modifier.size(18.dp).testTag("remove:${name}")) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove participant",
                    tint = AppColors.negative)
              }
        }
      },
      modifier = modifier,
      colors =
          InputChipDefaults.inputChipColors(
              labelColor = AppColors.textIcons,
          ),
      shape = appShapes.extraLarge)
}

/**
 * Component used to display the participants in a clean and flexible box Each chip shows a
 * participant name and optionally a remove button for admins.
 *
 * @param participants List of participants to display
 * @param onRemove Callback fn used when an Admin/Owner removes a participant
 * @param modifier Modifiers used for the component
 * @param editable Whether the current user can edit (remove) participants
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserChipsGrid(
    participants: List<Account>,
    onRemove: (Account) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false
) {
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = modifier.testTag(SessionTestTags.PARTICIPANT_CHIPS).fillMaxWidth()) {
        participants.forEach { p ->
          UserChip(
              name = p.name,
              modifier = Modifier.testTag("chip${p.uid}"),
              onRemove = { if (editable) onRemove(p) },
              showRemoveBTN = editable)
        }

        // Add button chip (to add new participants)
        // might be implemented later (users might joining the session themselves)
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
 * @param editable whether the current user can edit the slider (Admin/Owner only)
 * @param onValuesChange callback fn used when the slider is moved
 */
@Composable
fun PillSliderNoBackground(
    title: String,
    range: ClosedFloatingPointRange<Float>,
    values: ClosedFloatingPointRange<Float>,
    steps: Int,
    editable: Boolean = true,
    onValuesChange: (Float, Float) -> Unit
) {
  Column {
    Text(title, style = MaterialTheme.typography.labelSmall, color = AppColors.textIconsFade)
    Spacer(Modifier.height(6.dp))
    DiscretePillSlider(
        range = range,
        values = values,
        editable = editable,
        steps = steps,
        onValuesChange = onValuesChange,
        surroundModifier =
            Modifier.fillMaxWidth()
                .background(AppColors.primary, CircleShape)
                .border(1.dp, AppColors.primary, CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        sliderModifier = Modifier.testTag("discrete_pill_slider"),
        sliderColors =
            SliderDefaults.colors(
                activeTrackColor = AppColors.neutral,
                inactiveTrackColor = AppColors.divider,
                thumbColor = AppColors.neutral))
  }
}

/**
 * A composable that displays an icon with a small numeric badge close to it
 *
 * @param icon Icon to display
 * @param contentDescription Content description of the icon
 * @param badgeCount Number to display on the little badge
 * @param onClick Callback fn used on click of the button
 * @param modifier Modifiers to be added to the composable
 */
@Composable
fun BadgedIconButton(
    icon: ImageVector,
    contentDescription: String,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  BadgedBox(
      badge = {
        if (badgeCount > 0) {
          Badge(modifier = modifier) { Text(badgeCount.toString()) }
        } else {
          Text(text = "", modifier = Modifier.testTag(SessionTestTags.EMPTY_BADGE))
        } // Empty badge when count is 0 to avoid layout shift
      }) {
        IconButton(onClick = onClick) {
          Icon(icon, contentDescription = contentDescription, tint = AppColors.textIcons)
        }
      }
}

/**
 * * Dialog popup used to edit the time field
 *
 * @param onDismiss callback fn called when focusing out of the popup
 * @param onTimeSelected callback fn used to update the time field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(onDismiss: () -> Unit, onTimeSelected: (LocalTime) -> Unit) {
  // initialize state with current time
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
        TimePicker(
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
 * Composable used to display an interactive timefield
 *
 * @param value Time set
 * @param onValueChange callback fn when time is changed
 * @param modifier Modifiers that want to be passed to the composable
 * @param editable Whether the composable should be made editable depending on the current user's
 *   perms
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
      placeholder = "Time",
      leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = "Time") },
      trailingIcon = {
        if (editable) {
          TextButton(
              onClick = { showDialogTime = true },
              modifier = Modifier.testTag(SessionTestTags.TIME_PICK_BUTTON)) {
                Text("Pick")
              }
        }
      },
      modifier = modifier.testTag(SessionTestTags.TIME_FIELD))

  if (showDialogTime) {
    TimePickerDialog(
        onDismiss = { showDialogTime = false }, onTimeSelected = { sel -> onValueChange(sel) })
  }
}

/**
 * Deletes the currently viewed session - Only accessible with Admin/Owner rights
 *
 * @param sessionViewModel ViewModel used to delete the session
 * @param currentUser User performing the action
 * @param discussion Discussion the session is tied to
 * @param userIsAdmin Boolean to check whether the user can see this button
 * @param onback Callback function for navigation
 */
@Composable
fun DeleteSessionBTN(
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussion: Discussion,
    userIsAdmin: Boolean,
    onback: () -> Unit
) {
  if (userIsAdmin) {
    OutlinedButton(
        onClick = {
          sessionViewModel.deleteSession(currentUser, discussion)
          onback()
        },
        shape = CircleShape,
        border = BorderStroke(1.5.dp, AppColors.negative),
        modifier = Modifier.fillMaxWidth().testTag(SessionTestTags.DELETE_SESSION_BUTTON),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
          Icon(Icons.Default.Delete, contentDescription = "Delete Session")
          Spacer(Modifier.width(8.dp))
          Text("Delete Session", style = MaterialTheme.typography.bodyMedium)
        }
  }
}
