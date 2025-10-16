// Chatgpt helped for documentation and some bug fixes

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
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
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.components.CountBubble
import com.github.meeplemeet.ui.components.DatePickerDockedField
import com.github.meeplemeet.ui.components.DiscretePillSlider
import com.github.meeplemeet.ui.components.IconTextField
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
}

/* =======================================================================
 * Models
 * ======================================================================= */

// Simple data class representing a session participant (not always used, see Account).
data class Participant(val id: String, val name: String)

// Constants for the minimum and maximum values of the player count slider.
val sliderMinRange = 2f
val sliderMaxRange = 10f

/* =======================================================================
 * Public entry point
 * ======================================================================= */

/**
 * Main screen composable for viewing (and possibly editing) a session. Displays the session title,
 * proposed game, participants, organizational info, and a quit button.
 *
 * @param viewModel The FirestoreViewModel for session/discussion data.
 * @param currentUser The current logged-in user/account.
 * @param initial The initial SessionForm state (default: empty).
 * @param discussionId The ID of the discussion/session.
 * @param onBack Callback when the user navigates back or quits.
 */
@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionViewScreen(
    viewModel: FirestoreViewModel,
    currentUser: Account,
    initial: SessionForm = SessionForm(),
    discussionId: String,
    onBack: () -> Unit = {}
) {
  // Local state for the session form data.
  var form by remember { mutableStateOf(initial) }

  // Observe discussion updates from the view model.
  val discussion by viewModel.discussionFlow(discussionId).collectAsState()

  // Scaffold provides the top bar and main content area.
  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = "Session View",
            onReturn = {
              onBack()
              /** save the data */
            },
            { TopRightIcons() })
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
              editable = false, // TODO: make editable for admins and the session creator
              form,
              modifier =
                  Modifier.align(Alignment.CenterHorizontally)
                      .then(Modifier.testTag(SessionTestTags.TITLE)))

          // Proposed game section (read-only).
          // Background and border are primary for members since it blends with the screen bg.
          ProposedGameSection()

          // Participants section with slider and chips.
          ParticipantsSection(
              form,
              onFormChange = { min, max ->
                form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
              },
              onRemoveParticipant = { p ->
                form = form.copy(participants = form.participants.filterNot { it.uid == p.uid })
              })

          // Organisation section (date, time, location).
          // Editable for admins and the session creator, read-only for members.
          OrganizationSection(form, onFormChange = { form = it })

          Spacer(Modifier.height(4.dp))

          // Quit session button (removes user from session, not yet implemented).
          OutlinedButton(
              onClick = onBack
              /** TODO: remove currentAccount from the session */
              ,
              modifier =
                  Modifier.fillMaxWidth().then(Modifier.testTag(SessionTestTags.QUIT_BUTTON)),
              shape = CircleShape,
              border = BorderStroke(1.5.dp, AppColors.negative),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Quit Session", style = MaterialTheme.typography.bodyMedium)
              }
        }
  }
}

/* =======================================================================
 * Sub-components
 * ======================================================================= */

/**
 * Section displaying the list of participants, a slider for player count, and chips for each user.
 *
 * @param form The current session form state.
 * @param onFormChange Callback when the slider values change.
 * @param onRemoveParticipant Callback when a participant is removed.
 */
@Composable
fun ParticipantsSection(
    form: SessionForm,
    onFormChange: (Float, Float) -> Unit,
    onRemoveParticipant: (Account) -> Unit
) {
  SectionCard(
      modifier =
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.primary)
              .border(1.dp, AppColors.secondary, shape = appShapes.extraLarge)) {
        // Header row: "Participants" label and participant count bubble.
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
            range = sliderMinRange..sliderMaxRange,
            values = form.minPlayers.toFloat()..form.maxPlayers.toFloat(),
            steps = 7,
            onValuesChange = { min, max -> onFormChange(min, max) })

        // Min/max value bubbles for the slider.
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
            participants = form.participants, onRemove = { acc -> onRemoveParticipant(acc) })
      }
}

/**
 * Section displaying the currently proposed game for the session. For now, shown as read-only text
 * for members.
 */
@Composable
private fun ProposedGameSection() {
  SectionCard(
      modifier =
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.primary)
              .border(1.dp, AppColors.primary)) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
              UnderlinedLabel(
                  text = "Proposed game:",
                  textColor = AppColors.textIcons,
                  textStyle = MaterialTheme.typography.titleLarge)
              Spacer(Modifier.width(8.dp))
              // Text for members (not editable).
              Text(
                  "Current Game",
                  modifier = Modifier.testTag(SessionTestTags.PROPOSED_GAME),
                  style = MaterialTheme.typography.bodyMedium,
                  color = AppColors.textIcons)
            }
        Spacer(Modifier.height(10.dp))
      }
}

/**
 * Section for organizing session details: date, time, and location. Editable for admins/session
 * creator, read-only for members.
 *
 * @param form The current session form state.
 * @param onFormChange Callback when any organization field changes.
 */
@Composable
fun OrganizationSection(form: SessionForm, onFormChange: (SessionForm) -> Unit) {
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

        /** TODO: check date format */
        // Date field with a docked date picker dialog.
        DatePickerDockedField(
            value = form.date,
            onValueChange = { onFormChange(form.copy(date = it!!)) },
        )

        Spacer(Modifier.height(10.dp))

        /** TODO: check time format */
        // Time field using the TimeField composable, opens a time picker dialog.
        TimeField(
            value = form.time.toString(),
            onValueChange = { onFormChange(form.copy(time = it)) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))

        // Location field (currently a simple text field).
        // Could be improved with suggestions and map integration.
        OutlinedTextField(
            value = form.locationText,
            onValueChange = { onFormChange(form.copy(locationText = it)) },
            placeholder = { Text("Location", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") },
            modifier = Modifier.testTag(SessionTestTags.LOCATION_FIELD).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions.Default,
        )
      }
}

/**
 * Displays the session title. (Currently not editable.)
 *
 * @param text The title text to display.
 * @param editable Whether the title is editable (not yet supported).
 * @param form The session form (unused for now).
 * @param onValueChange Callback for title changes (unused for now).
 * @param modifier Modifier for styling.
 */
@Composable
fun Title(
    text: String,
    editable: Boolean = false,
    form: SessionForm,
    onValueChange: (String) -> Unit = {},
    modifier: Modifier
) {
  Text(
      text = text,
      style = MaterialTheme.typography.headlineLarge,
      color = AppColors.textIcons,
      modifier = modifier)
}

/**
 * Row of icon buttons for notifications and chat, each with a badge.
 *
 * @param onclickNotification Callback when notification icon is clicked.
 * @param onclickChat Callback when chat icon is clicked.
 */
@Composable
fun TopRightIcons(onclickNotification: () -> Unit = {}, onclickChat: () -> Unit = {}) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    BadgedIconButton(
        icon = Icons.Default.Notifications,
        contentDescription = "Notifications",
        modifier = Modifier.testTag(SessionTestTags.NOTIFICATION_BADGE_COUNT),
        // badge count could be dynamic based on actual notifications
        // not implemented in this demo
        badgeCount = 3, // Example badge count for notifications
        onClick = {
          onclickNotification() /* TODO: go to list of users that wants to join the session */
        })
    BadgedIconButton(
        icon = Icons.Default.ChatBubbleOutline,
        modifier = Modifier.testTag(SessionTestTags.CHAT_BADGE),
        contentDescription = "Messages",
        badgeCount = 0,
        onClick = { onclickChat() /* TODO: navigates to the discussion*/ })
  }
}

/**
 * Displays a participant as a removable chip with their name and avatar.
 *
 * @param name The participant's display name.
 * @param onRemove Callback when the remove icon is clicked.
 * @param modifier Modifier for styling.
 */
@Composable
fun UserChip(name: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
  InputChip(
      selected = false,
      onClick = { /* optional: maybe show details */},
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
        IconButton(onClick = onRemove, modifier = Modifier.size(18.dp)) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Remove participant",
              tint = AppColors.negative)
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
 * Displays the list of participants as a grid of removable chips.
 *
 * @param participants List of participant accounts.
 * @param onRemove Callback when a participant is removed.
 * @param modifier Modifier for styling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserChipsGrid(
    participants: List<Account>,
    onRemove: (Account) -> Unit,
    modifier: Modifier = Modifier
) {
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = modifier.testTag(SessionTestTags.PARTICIPANT_CHIPS).fillMaxWidth()) {
        participants.forEach { p -> UserChip(name = p.name, onRemove = { onRemove(p) }) }
      }
}

/**
 * Compact, discrete "pill" styled range slider with subtle rounded track & dots. This mirrors the
 * blue/red dotted pills in the mock (generic visual).
 *
 * @param title The label above the slider.
 * @param range The minimum and maximum values for the slider.
 * @param values The current selected min/max values.
 * @param steps Number of steps between min and max.
 * @param onValuesChange Callback when slider values change.
 */
@Composable
fun PillSliderNoBackground(
    title: String,
    range: ClosedFloatingPointRange<Float>,
    values: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValuesChange: (Float, Float) -> Unit
) {
  Column {
    Text(title, style = MaterialTheme.typography.labelSmall, color = AppColors.textIconsFade)
    Spacer(Modifier.height(6.dp))
    DiscretePillSlider(
        range = range,
        values = values,
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
 * Icon button with a badge, for notifications or chat.
 *
 * @param icon The icon to display.
 * @param contentDescription Content description for accessibility.
 * @param badgeCount Number to display in the badge (shows empty if 0).
 * @param onClick Callback for button click.
 * @param modifier Modifier for styling.
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
        IconButton(onClick = { onClick() }) {
          Icon(icon, contentDescription = contentDescription, tint = AppColors.textIcons)
        }
      }
}

/**
 * Dialog for picking a time using the Material3 TimePicker.
 *
 * @param onDismiss Callback when the dialog is dismissed.
 * @param onTimeSelected Callback with the selected LocalTime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(onDismiss: () -> Unit, onTimeSelected: (LocalTime) -> Unit) {
  // Initialize state with current time.
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
            // Kotlin
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
        // Use the Material3 TimePicker (dial) inside the dialog.
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
 * Displays a time field with a button to open a time picker dialog.
 *
 * @param value The current time as a string.
 * @param onValueChange Callback with the new LocalTime when picked.
 * @param modifier Modifier for styling.
 */
@Composable
fun TimeField(value: String, onValueChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
  // Controls visibility of the time picker dialog.
  var showDialogTime by remember { mutableStateOf(false) }

  IconTextField(
      value = value,
      onValueChange = {}, // controlled externally
      placeholder = "Time",
      leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = "Time") },
      trailingIcon = {
        TextButton(
            onClick = { showDialogTime = true },
            modifier = Modifier.testTag(SessionTestTags.TIME_PICK_BUTTON)) {
              Text("Pick")
            }
      },
      modifier = modifier.testTag(SessionTestTags.TIME_FIELD))

  if (showDialogTime) {
    TimePickerDialog(
        onDismiss = { showDialogTime = false }, onTimeSelected = { sel -> onValueChange(sel) })
  }
}

/// * =======================================================================
// * Preview
// * ======================================================================= */
//
//
// @OptIn(ExperimentalMaterial3Api::class)
// @Preview(showBackground = true, name = "datePicker")
// @Composable
// private fun Preview_datePicker() {
//  AppTheme {
//    val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)
//
//    DatePicker(
//        title = { Text("Select date") },
//        state = datePickerState,
//        colors =
//            DatePickerDefaults.colors(
//                containerColor = AppColors.primary,
//                titleContentColor = AppColors.textIconsFade,
//                headlineContentColor = AppColors.textIcons,
//                selectedDayContentColor = AppColors.neutral,
//            ))
//  }
// }
//
// @Preview(showBackground = true, name = "SectionCard")
// @Composable
// private fun Preview_SectionCard() {
//  AppTheme {
//    Column {
//      SectionCard(
//          Modifier.clip(appShapes.extraLarge)
//              .background(AppColors.primary)
//              .border(1.dp, AppColors.primary, shape = appShapes.extraLarge)) {
//            UnderlinedLabel("Sample section")
//            Spacer(Modifier.height(8.dp))
//            Text(
//                "Any content goes in here; this container uses your theme shapes and borders.",
//                style = MaterialTheme.typography.bodySmall,
//                modifier = Modifier.padding(top = 4.dp))
//          }
//      Spacer(Modifier.height(12.dp))
//      SectionCard(
//          Modifier.clip(appShapes.extraLarge)
//              .background(AppColors.secondary)
//              .border(1.dp, AppColors.secondary, shape = appShapes.extraLarge)) {
//            UnderlinedLabel("Another section")
//            Spacer(Modifier.height(8.dp))
//            Text(
//                "This is a second SectionCard using the main composable.",
//                style = MaterialTheme.typography.bodySmall,
//                modifier = Modifier.padding(top = 4.dp))
//          }
//    }
//  }
// }
//
// @Preview(showBackground = true, name = "UnderlinedLabel")
// @Composable
// private fun Preview_UnderlinedLabel() {
//  AppTheme {
//    Column(Modifier.padding(16.dp)) {
//      UnderlinedLabel("Proposed game:")
//      Spacer(Modifier.height(8.dp))
//      UnderlinedLabel("Participants:")
//    }
//  }
// }
//
// @Preview(showBackground = true, name = "IconTextField")
// @Composable
// private fun Preview_IconTextField() {
//  AppTheme {
//    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
//      IconTextField(
//          value = "",
//          onValueChange = {},
//          placeholder = "Search games",
//          trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
//          textStyle = MaterialTheme.typography.bodySmall,
//          modifier = Modifier)
//      IconTextField(
//          value = "2025-10-15",
//          onValueChange = {},
//          placeholder = "Date",
//          leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
//          textStyle = MaterialTheme.typography.bodySmall,
//          modifier = Modifier)
//      IconTextField(
//          value = "Student Lounge",
//          onValueChange = {},
//          placeholder = "Location",
//          leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
//          textStyle = MaterialTheme.typography.bodySmall,
//          modifier = Modifier)
//    }
//  }
// }
//
// @Preview(showBackground = true, name = "CountBubble")
// @Composable
// private fun Preview_CountBubble() {
//  AppTheme {
//    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//      CountBubble(
//          0,
//          modifier =
//              Modifier.clip(CircleShape)
//                  .background(AppColors.primary)
//                  .border(1.dp, AppColors.secondary, CircleShape)
//                  .padding(horizontal = 10.dp, vertical = 6.dp))
//      CountBubble(
//          3,
//          modifier =
//              Modifier.clip(CircleShape)
//                  .background(AppColors.secondary)
//                  .border(1.dp, AppColors.secondary, CircleShape)
//                  .padding(horizontal = 10.dp, vertical = 6.dp))
//      CountBubble(
//          12,
//          modifier =
//              Modifier.clip(CircleShape)
//                  .background(AppColors.affirmative)
//                  .border(1.dp, AppColors.secondary, CircleShape)
//                  .padding(horizontal = 10.dp, vertical = 6.dp))
//    }
//  }
// }
//
// @Preview(showBackground = true, name = "ParticipantChip")
// @Composable
// private fun Preview_ParticipantChip() {
//  AppTheme {
//    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//      UserChip(name = "user1", onRemove = {})
//      UserChip(name = "Alice", onRemove = {})
//    }
//  }
// }
//
// @Preview(showBackground = true, name = "ParticipantChipsGrid")
// @Composable
// private fun Preview_ParticipantChipsGrid() {
//  AppTheme {
//    UserChipsGrid(
//        participants =
//            listOf(
//                Participant("1", "user1"),
//                Participant("2", "John Doe"),
//                Participant("3", "Alice"),
//                Participant("4", "Bob"),
//                Participant("5", "Robert")),
//        onRemove = {})
//  }
// }
//
// @Preview(showBackground = true, name = "DiscretePillSlider")
// @Composable
// private fun Preview_DiscretePillSlider() {
//  AppTheme {
//    var values by remember { mutableStateOf(3f..6f) }
//    Column(Modifier.padding(16.dp)) {
//      PillSliderNoBackground(
//          title = "Players",
//          range = 2f..10f,
//          values = values,
//          steps = 7,
//          onValuesChange = { start, end -> values = start..end })
//    }
//  }
// }
//
// @Preview(showBackground = true, name = "BadgedIconButton")
// @Composable
// private fun Preview_BadgedIconButton() {
//  AppTheme { TopRightIcons() }
// }
//
//// Full screen preview (kept separate from the sub-component previews)
// @Preview(showBackground = true, name = "Create Session – Full")
// @Composable
// private fun Preview_SessionView_Full() {
//  var form =
//      SessionForm(
//          title = "Friday Night Meetup",
//          proposedGameQuery = "",
//          minPlayers = 3,
//          maxPlayers = 6,
//          participants =
//              listOf(
//                  Participant("1", "user1"),
//                  Participant("2", "John Doe"),
//                  Participant("3", "Alice"),
//                  Participant("4", "Bob"),
//                  Participant("5", "Robert")),
//          dateText = LocalDate.now(),
//          timeText = "19:00",
//          locationText = "Student Lounge")
//  AppTheme {
//    Scaffold(
//        topBar = {
//          TopBarWithDivider(
//              text = "Session View",
//              onReturn = {
//                {}
//                /** save the data */
//              },
//              { TopRightIcons() })
//        },
//    ) { innerPadding ->
//      Column(
//          modifier =
//              Modifier.fillMaxSize()
//                  .verticalScroll(rememberScrollState())
//                  .background(AppColors.primary)
//                  .padding(innerPadding)
//                  .padding(horizontal = 16.dp, vertical = 8.dp),
//          verticalArrangement = Arrangement.spacedBy(16.dp)) {
//
//            // Title
//            Title(
//                text = form.title.ifEmpty { "New Session" },
//                editable = true,
//                form,
//                modifier = Modifier.align(Alignment.CenterHorizontally))
//
//            // Proposed game section
//            // background and border are primary for members since it blends with the screen bg
//            // proposed game is a text for members, it's not in a editable box
//            ProposedGameSection()
//
//            // Participants section
//            ParticipantsSection(
//                form,
//                onFormChange = { min, max ->
//                  form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
//                },
//                onRemoveParticipant = { p ->
//                  form = form.copy(participants = form.participants.filterNot { it.id == p.id })
//                })
//
//            // Organisation section
//            // editable for admins and the session creator, read-only for members
//            OrganizationSection(form, onFormChange = { form = it })
//
//            Spacer(Modifier.height(4.dp))
//
//            // Quit session button
//            OutlinedButton(
//                onClick = {},
//                modifier = Modifier.fillMaxWidth(),
//                shape = CircleShape,
//                border = BorderStroke(1.5.dp, AppColors.negative),
//                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
//                  Icon(Icons.Default.Delete, contentDescription = null)
//                  Spacer(Modifier.width(8.dp))
//                  Text("Quit Session", style = MaterialTheme.typography.bodyMedium)
//                }
//          }
//    }
//  }
// }
//
//// =============================
//// Organisation previews
//// =============================
//
// @Preview(showBackground = true, name = "Organisation – Single Rows")
// @Composable
// private fun Preview_Organisation_SingleRows() {
//  AppTheme {
//    var form by remember {
//      mutableStateOf(SessionForm(dateText = LocalDate.now(), timeText = "19:30", locationText =
// "EPFL"))
//    }
//    OrganizationSection(form, onFormChange = { form = it })
//  }
// }
//
// @Preview(showBackground = true, name = "Session View – Lower area")
// @Composable
// private fun Preview_Session_LowerArea() {
//  AppTheme {
//    var form by remember {
//      mutableStateOf(
//          SessionForm(dateText = LocalDate.now(), timeText = "19:30", locationText = "Satellite
// "))
//    }
//    Column(
//        modifier = Modifier.fillMaxWidth().background(AppColors.primary).padding(16.dp),
//        verticalArrangement = Arrangement.spacedBy(16.dp)) {
//          // Organisation section (reuse composable)
//          OrganizationSection(form, onFormChange = { form = it })
//
//          Spacer(Modifier.height(4.dp))
//
//          // Quit session button
//          OutlinedButton(
//              onClick = {},
//              modifier = Modifier.fillMaxWidth(),
//              shape = CircleShape,
//              border = BorderStroke(1.5.dp, AppColors.negative),
//              colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
//                Icon(Icons.Default.Delete, contentDescription = null)
//                Spacer(Modifier.width(8.dp))
//                Text("Quit Session", style = MaterialTheme.typography.bodyMedium)
//              }
//        }
//  }
// }
//
// @Preview(showBackground = true, name = "DateField and DatePickerDialog")
// @Composable
// private fun Preview_DateField_DatePickerDialog() {
//  AppTheme {
//    var date by remember { mutableStateOf(LocalDate.now()) }
//    Column(Modifier.padding(16.dp)) { DatePickerDockedField(value = date, onValueChange = { date =
// it }) }
//  }
// }
//
// @Preview(showBackground = true, name = "TimeField and TimePickerDialog")
// @Composable
// private fun Preview_TimeField_TimePickerDialog() {
//  AppTheme {
//    var time by remember { mutableStateOf("18:30") }
//    Column(Modifier.padding(16.dp)) { TimeField(value = time, onValueChange = { time = it }) }
//  }
// }
//
// @OptIn(ExperimentalMaterial3Api::class)
// @Preview(showBackground = true, name = "TimePicker")
// @Composable
// private fun Preview_TimePicker() {
//  AppTheme {
//    // Initialize with example time
//    val timePickerState = rememberTimePickerState(is24Hour = false)
//
//    // Place the TimePicker inside a Column to make it visible
//    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
//      TimePicker(
//          state = timePickerState,
//          colors =
//              TimePickerDefaults.colors(
//                  clockDialColor = AppColors.secondary,
//                  clockDialSelectedContentColor = AppColors.primary,
//                  clockDialUnselectedContentColor = AppColors.textIconsFade,
//                  selectorColor = AppColors.neutral,
//                  periodSelectorBorderColor = AppColors.textIconsFade,
//                  periodSelectorSelectedContainerColor = AppColors.secondary,
//                  periodSelectorSelectedContentColor = AppColors.negative,
//                  timeSelectorSelectedContainerColor = AppColors.neutral,
//                  timeSelectorUnselectedContainerColor = AppColors.secondary,
//              ))
//    }
//  }
// }
