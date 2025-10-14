package com.github.meeplemeet.ui

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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.components.CountBubble
import com.github.meeplemeet.ui.components.IconTextField
import com.github.meeplemeet.ui.components.SectionCard
import com.github.meeplemeet.ui.components.UnderlinedLabel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.appShapes
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.roundToInt

/* =======================================================================
 * Test tags for UI tests
 * ======================================================================= */

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
}

/* =======================================================================
 * Models
 * ======================================================================= */

data class Participant(val id: String, val name: String)

data class SessionForm(
    val title: String = "",
    val proposedGameQuery: String = "",
    val minPlayers: Int = 2,
    val maxPlayers: Int = 5,
    val participants: List<Participant> = emptyList(),
    val dateText: String = "",
    val timeText: String = "",
    val locationText: String = ""
)

/* =======================================================================
 * Public entry point
 * ======================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionViewScreen(
    viewModel: FirestoreViewModel,
    navigation: NavigationActions? = null,
    currentUser: Account,
    initial: SessionForm = SessionForm(),
    discussionId: String,
    onCreate: (SessionForm) -> Unit = {},
    onBack: () -> Unit = {}
) {
  var form by remember { mutableStateOf(initial) }

  val discussion by viewModel.discussionFlow(discussionId).collectAsState()

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

          // Title
          Title(
              text = form.title.ifEmpty { "New Session" },
              form,
              modifier =
                  Modifier.align(Alignment.CenterHorizontally)
                      .then(Modifier.testTag(SessionTestTags.TITLE)))

          // Proposed game section
          // background and border are primary for members since it blends with the screen bg
          // proposed game is a text for members, it's not in a editable box
          ProposedGameSection()

          // Participants section
          ParticipantsSection(
              form,
              onFormChange = { min, max ->
                form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
              },
              onRemoveParticipant = { p ->
                form = form.copy(participants = form.participants.filterNot { it.id == p.id })
              })

          // Organisation section
          // editable for admins and the session creator, read-only for members
          OrganizationSection(form, onFormChange = { form = it })

          Spacer(Modifier.height(4.dp))

          // Quit session button
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

@Composable
private fun ParticipantsSection(
    form: SessionForm,
    onFormChange: (Float, Float) -> Unit,
    onRemoveParticipant: (Participant) -> Unit
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

        DiscretePillSlider(
            title = "Number of players",
            range = 2f..10f,
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

        // Chips
        UserChipsGrid(
            participants = form.participants,
            onRemove = { p -> onRemoveParticipant(p) },
            modifier = Modifier.testTag(SessionTestTags.PARTICIPANT_CHIPS))
      }
}

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
              // Text for members
              Text(
                  "Current Game",
                  modifier = Modifier.testTag(SessionTestTags.PROPOSED_GAME),
                  style = MaterialTheme.typography.bodyMedium,
                  color = AppColors.textIcons)
            }
        Spacer(Modifier.height(10.dp))
        /** TODO: Search field or something for admins and the session creator to propose a game */
      }
}

@Composable
private fun OrganizationSection(form: SessionForm, onFormChange: (SessionForm) -> Unit) {
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
        DateField(
            value = form.dateText,
            onValueChange = { onFormChange(form.copy(dateText = it)) },
            modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(10.dp))

        /** TODO: check time format */
        // Time field using the new TimeField composable
        TimeField(
            value = form.timeText,
            onValueChange = { onFormChange(form.copy(timeText = it)) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))

        // Location field
        // could be redone like the bootcamp
        // using a search field with suggestions and map integration
        // for now it's just a text field
        IconTextField(
            value = form.locationText,
            onValueChange = { onFormChange(form.copy(locationText = it)) },
            placeholder = "Location",
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") },
            modifier = Modifier.testTag(SessionTestTags.LOCATION_FIELD).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
        )
      }
}

@Composable
private fun Title(
    text: String,
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

@Composable
private fun TopRightIcons() {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    BadgedIconButton(
        icon = Icons.Default.Notifications,
        contentDescription = "Notifications",
        // badge count could be dynamic based on actual notifications
        // not implemented in this demo
        badgeCount = 3, // Example badge count for notifications
        onClick = { /* TODO: go to list of users that wants to join the session */})
    BadgedIconButton(
        icon = Icons.Default.ChatBubbleOutline,
        contentDescription = "Messages",
        badgeCount = 0,
        onClick = { /* TODO: navigates to the discussion*/})
  }
}

@Composable
private fun UserChip(name: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserChipsGrid(
    participants: List<Participant>,
    onRemove: (Participant) -> Unit,
    modifier: Modifier = Modifier
) {
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = modifier.then(Modifier.fillMaxWidth())) {
        participants.forEach { p -> UserChip(name = p.name, onRemove = { onRemove(p) }) }

        // Add button chip (to add new participants)
        // might be implemented later (users might joining the session themselves)
        // AddUserChip(onClick = onAddPlaceholder)
      }
}

/**
 * Compact, discrete "pill" styled range slider with subtle rounded track & dots. This mirrors the
 * blue/red dotted pills in the mock (generic visual).
 */
@Composable
private fun DiscretePillSlider(
    title: String,
    range: ClosedFloatingPointRange<Float>,
    values: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValuesChange: (Float, Float) -> Unit
) {
  Column {
    Text(title, style = MaterialTheme.typography.labelSmall, color = AppColors.textIconsFade)
    Spacer(Modifier.height(6.dp))
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(AppColors.primary, CircleShape)
                .border(1.dp, AppColors.primary, CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp)) {
          RangeSlider(
              value = values,
              onValueChange = { onValuesChange(it.start, it.endInclusive) },
              valueRange = range,
              steps = steps,
              colors =
                  SliderDefaults.colors(
                      activeTrackColor = AppColors.neutral,
                      inactiveTrackColor = AppColors.divider,
                      thumbColor = AppColors.neutral))
        }
  }
}

@Composable
private fun BadgedIconButton(
    icon: ImageVector,
    contentDescription: String,
    badgeCount: Int,
    onClick: () -> Unit
) {
  BadgedBox(
      badge = {
        if (badgeCount > 0) {
          Badge { Text(badgeCount.toString()) }
        }
      }) {
        IconButton(onClick = { onClick() }) {
          Icon(icon, contentDescription = contentDescription, tint = AppColors.textIcons)
        }
      }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
  val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)

  AlertDialog(
      containerColor = AppColors.primary,
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
            onClick = {
              val millis = datePickerState.selectedDateMillis
              if (millis != null) {
                val date =
                    Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString() // e.g. "2025-10-13"
                onDateSelected(date)
              }
              onDismiss()
            },
            modifier = Modifier.testTag(SessionTestTags.DATE_PICKER_OK_BUTTON)) {
              Text("OK")
            }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
      text = {
        // Wrap DatePicker in a Box with fillMaxWidth and padding to avoid cropping
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
          DatePicker(
              state = datePickerState,
              modifier = Modifier.fillMaxWidth(),
              colors =
                  DatePickerDefaults.colors(
                      containerColor = AppColors.primary,
                      titleContentColor = AppColors.textIconsFade,
                      headlineContentColor = AppColors.textIcons,
                      selectedDayContentColor = AppColors.primary,
                      selectedDayContainerColor = AppColors.neutral))
        }
      })
}

@Composable
fun DateField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
  var showDialogDate by remember { mutableStateOf(false) }

  // The text field
  IconTextField(
      value = value,
      onValueChange = {}, // we control it externally
      placeholder = "Date",
      leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = "Date") },
      trailingIcon = {
        TextButton(
            onClick = { showDialogDate = true },
            modifier = Modifier.testTag(SessionTestTags.DATE_PICK_BUTTON)) {
              Text("Pick")
            }
      },
      modifier = modifier.testTag(SessionTestTags.DATE_FIELD))

  // The popup
  if (showDialogDate) {
    DatePickerDialog(
        onDismiss = { showDialogDate = false },
        onDateSelected = { selectedDate -> onValueChange(selectedDate) })
  }
}

// ---- Add after your DatePickerDialog / DateField ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(onDismiss: () -> Unit, onTimeSelected: (String) -> Unit) {
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
              /** Todo: fix this if buggy */
              val formatted = String.format("%02d:%02d", h, m)
              onTimeSelected(formatted)
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

@Composable
fun TimeField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
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

/* =======================================================================
 * Preview
 * ======================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "datePicker")
@Composable
private fun Preview_datePicker() {
  AppTheme {
    val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)

    DatePicker(
        title = { Text("Select date") },
        state = datePickerState,
        colors =
            DatePickerDefaults.colors(
                containerColor = AppColors.primary,
                titleContentColor = AppColors.textIconsFade,
                headlineContentColor = AppColors.textIcons,
                selectedDayContentColor = AppColors.neutral,
            ))
  }
}

@Preview(showBackground = true, name = "SectionCard")
@Composable
private fun Preview_SectionCard() {
  AppTheme {
    Column {
      SectionCard(
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.primary)
              .border(1.dp, AppColors.primary, shape = appShapes.extraLarge)) {
            UnderlinedLabel("Sample section")
            Spacer(Modifier.height(8.dp))
            Text(
                "Any content goes in here; this container uses your theme shapes and borders.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
          }
      Spacer(Modifier.height(12.dp))
      SectionCard(
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.secondary)
              .border(1.dp, AppColors.secondary, shape = appShapes.extraLarge)) {
            UnderlinedLabel("Another section")
            Spacer(Modifier.height(8.dp))
            Text(
                "This is a second SectionCard using the main composable.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
          }
    }
  }
}

@Preview(showBackground = true, name = "UnderlinedLabel")
@Composable
private fun Preview_UnderlinedLabel() {
  AppTheme {
    Column(Modifier.padding(16.dp)) {
      UnderlinedLabel("Proposed game:")
      Spacer(Modifier.height(8.dp))
      UnderlinedLabel("Participants:")
    }
  }
}

@Preview(showBackground = true, name = "IconTextField")
@Composable
private fun Preview_IconTextField() {
  AppTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      IconTextField(
          value = "",
          onValueChange = {},
          placeholder = "Search games",
          trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          textStyle = MaterialTheme.typography.bodySmall,
          modifier = Modifier)
      IconTextField(
          value = "2025-10-15",
          onValueChange = {},
          placeholder = "Date",
          leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
          textStyle = MaterialTheme.typography.bodySmall,
          modifier = Modifier)
      IconTextField(
          value = "Student Lounge",
          onValueChange = {},
          placeholder = "Location",
          leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
          textStyle = MaterialTheme.typography.bodySmall,
          modifier = Modifier)
    }
  }
}

@Preview(showBackground = true, name = "CountBubble")
@Composable
private fun Preview_CountBubble() {
  AppTheme {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      CountBubble(
          0,
          modifier =
              Modifier.clip(CircleShape)
                  .background(AppColors.primary)
                  .border(1.dp, AppColors.secondary, CircleShape)
                  .padding(horizontal = 10.dp, vertical = 6.dp))
      CountBubble(
          3,
          modifier =
              Modifier.clip(CircleShape)
                  .background(AppColors.secondary)
                  .border(1.dp, AppColors.secondary, CircleShape)
                  .padding(horizontal = 10.dp, vertical = 6.dp))
      CountBubble(
          12,
          modifier =
              Modifier.clip(CircleShape)
                  .background(AppColors.affirmative)
                  .border(1.dp, AppColors.secondary, CircleShape)
                  .padding(horizontal = 10.dp, vertical = 6.dp))
    }
  }
}

@Preview(showBackground = true, name = "ParticipantChip")
@Composable
private fun Preview_ParticipantChip() {
  AppTheme {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      UserChip(name = "user1", onRemove = {})
      UserChip(name = "Alice", onRemove = {})
    }
  }
}

@Preview(showBackground = true, name = "ParticipantChipsGrid")
@Composable
private fun Preview_ParticipantChipsGrid() {
  AppTheme {
    UserChipsGrid(
        participants =
            listOf(
                Participant("1", "user1"),
                Participant("2", "John Doe"),
                Participant("3", "Alice"),
                Participant("4", "Bob"),
                Participant("5", "Robert")),
        onRemove = {})
  }
}

@Preview(showBackground = true, name = "DiscretePillSlider")
@Composable
private fun Preview_DiscretePillSlider() {
  AppTheme {
    var values by remember { mutableStateOf(3f..6f) }
    Column(Modifier.padding(16.dp)) {
      DiscretePillSlider(
          title = "Players",
          range = 2f..10f,
          values = values,
          steps = 7,
          onValuesChange = { start, end -> values = start..end })
    }
  }
}

@Preview(showBackground = true, name = "BadgedIconButton")
@Composable
private fun Preview_BadgedIconButton() {
  AppTheme { TopRightIcons() }
}

// Full screen preview (kept separate from the sub-component previews)
@Preview(showBackground = true, name = "Create Session – Full")
@Composable
private fun Preview_SessionView_Full() {
  var form =
      SessionForm(
          title = "Friday Night Meetup",
          proposedGameQuery = "",
          minPlayers = 3,
          maxPlayers = 6,
          participants =
              listOf(
                  Participant("1", "user1"),
                  Participant("2", "John Doe"),
                  Participant("3", "Alice"),
                  Participant("4", "Bob"),
                  Participant("5", "Robert")),
          dateText = "2025-10-15",
          timeText = "19:00",
          locationText = "Student Lounge")
  AppTheme {
    Scaffold(
        topBar = {
          TopBarWithDivider(
              text = "Session View",
              onReturn = {
                {}
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

            // Title
            Title(
                text = form.title.ifEmpty { "New Session" },
                form,
                modifier = Modifier.align(Alignment.CenterHorizontally))

            // Proposed game section
            // background and border are primary for members since it blends with the screen bg
            // proposed game is a text for members, it's not in a editable box
            ProposedGameSection()

            // Participants section
            ParticipantsSection(
                form,
                onFormChange = { min, max ->
                  form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
                },
                onRemoveParticipant = { p ->
                  form = form.copy(participants = form.participants.filterNot { it.id == p.id })
                })

            // Organisation section
            // editable for admins and the session creator, read-only for members
            OrganizationSection(form, onFormChange = { form = it })

            Spacer(Modifier.height(4.dp))

            // Quit session button
            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
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
}

// =============================
// Organisation previews
// =============================

@Preview(showBackground = true, name = "Organisation – Single Rows")
@Composable
private fun Preview_Organisation_SingleRows() {
  AppTheme {
    var form by remember {
      mutableStateOf(SessionForm(dateText = "2025-1-16", timeText = "19:30", locationText = "EPFL"))
    }
    OrganizationSection(form, onFormChange = { form = it })
  }
}

@Preview(showBackground = true, name = "Session View – Lower area")
@Composable
private fun Preview_Session_LowerArea() {
  AppTheme {
    var form by remember {
      mutableStateOf(
          SessionForm(dateText = "2025-1-16", timeText = "19:30", locationText = "Satellite "))
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(AppColors.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
          // Organisation section (reuse composable)
          OrganizationSection(form, onFormChange = { form = it })

          Spacer(Modifier.height(4.dp))

          // Quit session button
          OutlinedButton(
              onClick = {},
              modifier = Modifier.fillMaxWidth(),
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

@Preview(showBackground = true, name = "DateField and DatePickerDialog")
@Composable
private fun Preview_DateField_DatePickerDialog() {
  AppTheme {
    var date by remember { mutableStateOf("2025-1-16") }
    Column(Modifier.padding(16.dp)) { DateField(value = date, onValueChange = { date = it }) }
  }
}

@Preview(showBackground = true, name = "TimeField and TimePickerDialog")
@Composable
private fun Preview_TimeField_TimePickerDialog() {
  AppTheme {
    var time by remember { mutableStateOf("18:30") }
    Column(Modifier.padding(16.dp)) { TimeField(value = time, onValueChange = { time = it }) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "TimePicker")
@Composable
private fun Preview_TimePicker() {
  AppTheme {
    // Initialize with example time
    val timePickerState = rememberTimePickerState(is24Hour = false)

    // Place the TimePicker inside a Column to make it visible
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
    }
  }
}
