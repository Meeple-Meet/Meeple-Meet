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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.appShapes
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

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
    viewModel: FirestoreViewModel? = null,
    navigation: NavigationActions? = null,
    currentUser: Account,
    initial: SessionForm = SessionForm(),
    onCreate: (SessionForm) -> Unit = {},
    onBack: () -> Unit = {}
) {
  var form by remember { mutableStateOf(initial) }

  Scaffold(
      topBar = {
        TopBarWithDivider(text = "Session View", onReturn = { onBack() }, { TopRightIcons() })
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
          // proposed game is a text for members, it's not in a editable box; admins/session creator
          // get secondary bg
          SectionCard(backgroundColor = AppColors.primary, borderColor = AppColors.primary) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                  UnderlinedLabel("Proposed game:")
                  Spacer(Modifier.width(8.dp))
                  // Text for members
                  Text(
                      "Current Game",
                      modifier = Modifier,
                      style = MaterialTheme.typography.bodyMedium,
                      color = AppColors.textIcons)
                }
            Spacer(Modifier.height(10.dp))

            /**
             * TODO: Search field or something for admins and the session creator to propose a game
             */
          }

          // Participants section
          SectionCard(backgroundColor = AppColors.primary, borderColor = AppColors.divider) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                  UnderlinedLabel("Participants:")
                  CountBubble(
                      count = form.participants.size,
                      backgroundColor = AppColors.affirmative,
                      borderColor = AppColors.secondary)
                }

            Spacer(Modifier.height(12.dp))

            DiscretePillSlider(
                title = "Number of players",
                range = 2f..10f,
                values = form.minPlayers.toFloat()..form.maxPlayers.toFloat(),
                steps = 7,
                onValuesChange = { min, max ->
                  form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
                })

            // Min/max bubbles of the slider
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              CountBubble(
                  count = form.minPlayers,
                  backgroundColor = AppColors.primary,
                  borderColor = AppColors.secondary)
              CountBubble(
                  count = form.maxPlayers,
                  backgroundColor = AppColors.primary,
                  borderColor = AppColors.secondary)
            }
            Spacer(Modifier.height(10.dp))

            Spacer(Modifier.height(12.dp))

            // Chips
            UserChipsGrid(
                participants = form.participants,
                onRemove = { p ->
                  form = form.copy(participants = form.participants.filterNot { it.id == p.id })
                })
          }

          // Organisation section
          // editable for admins and the session creator, read-only for members
          SectionCard(backgroundColor = AppColors.primary, borderColor = AppColors.divider) {
            UnderlinedLabel("Organisation:")
            Spacer(Modifier.height(12.dp))

            DateField(value = form.dateText, onValueChange = { form = form.copy(dateText = it) })

            Spacer(Modifier.height(10.dp))

            // Time field
            IconTextField(
                value = form.timeText,
                onValueChange = { form = form.copy(timeText = it) },
                placeholder = "Time",
                leadingIcon = {
                  // Using CalendarToday to keep icon set light; replace with alarm icon if you
                  // prefer.
                  // opens a date picker for admins and the session creator
                  Icon(Icons.Default.AccessTime, contentDescription = "Time")
                },
                trailingIcon = { TextButton(onClick = { /* open time picker */}) { Text("Pick") } })

            Spacer(Modifier.height(10.dp))

            // Location field
            // could be redone like the bootcamp
            // using a search field with suggestions and map integration
            // for now it's just a text field
            IconTextField(
                value = form.locationText,
                onValueChange = { form = form.copy(locationText = it) },
                placeholder = "Location",
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") })
          }

          Spacer(Modifier.height(4.dp))

          // Quit session button
          OutlinedButton(
              onClick = onBack,
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

/* =======================================================================
 * Sub-components
 * ======================================================================= */

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.secondary,
    borderColor: Color = AppColors.divider,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .border(1.dp, borderColor, appShapes.large)
              .background(backgroundColor, appShapes.large)
              .padding(contentPadding),
      content = content)
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
private fun UnderlinedLabel(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.titleLarge,
      color = AppColors.textIcons,
      textDecoration = TextDecoration.Underline)
}

@Composable
private fun IconTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth(),
      leadingIcon = leadingIcon,
      trailingIcon = trailingIcon,
      placeholder = { Text(placeholder, color = AppColors.textIconsFade) },
      textStyle = MaterialTheme.typography.bodySmall.copy(color = AppColors.textIcons))
}

@Composable
private fun CountBubble(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.secondary,
    borderColor: Color = AppColors.textIconsFade
) {
  Box(
      modifier =
          modifier
              .clip(CircleShape)
              .background(backgroundColor)
              .border(1.dp, borderColor, CircleShape)
              .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("$count", style = MaterialTheme.typography.bodySmall, color = AppColors.textIcons)
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
) {
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()) {
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
        IconButton(onClick = { onClick }) {
          Icon(icon, contentDescription = contentDescription, tint = AppColors.textIcons)
        }
      }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
  val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)

  AlertDialog(
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
            }) {
              Text("OK")
            }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
      text = {

        // The date picker itself
        DatePicker(
            state = datePickerState,
            colors =
                DatePickerDefaults.colors(
                    containerColor = AppColors.primary,
                    titleContentColor = AppColors.textIconsFade,
                    headlineContentColor = AppColors.textIcons,
                    selectedDayContentColor = AppColors.neutral,
                ))
      })
}

@Composable
fun DateField(value: String, onValueChange: (String) -> Unit) {
  var showDialog by remember { mutableStateOf(false) }

  // The text field
  IconTextField(
      value = value,
      onValueChange = {}, // we control it externally
      placeholder = "Date",
      leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = "Date") },
      trailingIcon = { TextButton(onClick = { showDialog = true }) { Text("Pick") } })

  // The popup
  if (showDialog) {
    DatePickerDialog(
        onDismiss = { showDialog = false },
        onDateSelected = { selectedDate -> onValueChange(selectedDate) })
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

@Preview(showBackground = true, name = "Create Session – Dark")
@Composable
private fun CreateSessionPreview() {
  AppTheme {
    SessionViewScreen(
        currentUser = Account("marcoUID", "marco", email = "marco@epfl.ch"),
        initial =
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
                locationText = "Student Lounge"),
        viewModel = null,
        navigation = null,
    )
  }
}

@Preview(showBackground = true, name = "SectionCard")
@Composable
private fun Preview_SectionCard() {
  AppTheme {
    Column {
      SectionCard(backgroundColor = AppColors.primary, borderColor = AppColors.primary) {
        Row {
          UnderlinedLabel("Sample section")
          Spacer(Modifier.height(8.dp))
          Text(
              "Any content goes in here; this container uses your theme shapes and borders.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp))
        }
      }
      SectionCard {
        Row {
          UnderlinedLabel("Sample section")
          Spacer(Modifier.height(8.dp))
          Text(
              "Any content goes in here; this container uses your theme shapes and borders.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp))
        }
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
          trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
      IconTextField(
          value = "2025-10-15",
          onValueChange = {},
          placeholder = "Date",
          leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) })
      IconTextField(
          value = "Student Lounge",
          onValueChange = {},
          placeholder = "Location",
          leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) })
    }
  }
}

@Preview(showBackground = true, name = "CountBubble")
@Composable
private fun Preview_CountBubble() {
  AppTheme {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      CountBubble(0)
      CountBubble(3, backgroundColor = AppColors.primary, borderColor = AppColors.secondary)
      CountBubble(12, backgroundColor = AppColors.affirmative, borderColor = AppColors.secondary)
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
private fun Preview_CreateSession_Full() {
  AppTheme {
    SessionViewScreen(
        currentUser = Account("marcoUID", "marco", email = "marco@epfl.ch"),
        initial =
            SessionForm(
                title = "Friday Night Meetup",
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
                locationText = "Student Lounge"))
  }
}

// =============================
// Organisation previews
// =============================

@Preview(showBackground = true, name = "Organisation – Single Rows")
@Composable
private fun Preview_Organisation_SingleRows() {
  AppTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      var date by remember { mutableStateOf("2025-10-15") }
      var time by remember { mutableStateOf("") }
      var location by remember { mutableStateOf("EPFL – BC Building") }
      Spacer(Modifier.height(10.dp))

      DateField(value = date, onValueChange = { date = it })

      Spacer(Modifier.height(10.dp))

      // Time field
      IconTextField(
          value = time,
          onValueChange = { time = it },
          placeholder = "Time",
          leadingIcon = {
            // Using CalendarToday to keep icon set light; replace with alarm icon if you prefer.
            // opens a date picker for admins and the session creator
            Icon(Icons.Default.AccessTime, contentDescription = "Time")
          },
          trailingIcon = { TextButton(onClick = { /* open time picker */}) { Text("Pick") } })
      Spacer(Modifier.height(10.dp))

      // Location field
      // could be redone like the bootcamp
      // using a search field with suggestions and map integration
      // for now it's just a text field
      IconTextField(
          value = location,
          onValueChange = { location = it },
          placeholder = "Location",
          leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") })
    }
  }
}

@Preview(showBackground = true, name = "Create Session – Lower area")
@Composable
private fun Preview_CreateSession_LowerArea() {
  AppTheme {
    Column(
        modifier = Modifier.fillMaxWidth().background(AppColors.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
          var date by remember { mutableStateOf("2025-10-15") }
          var time by remember { mutableStateOf("19:00") }
          var location by remember { mutableStateOf("Student Lounge") }

          // Organisation section
          // editable for admins and the session creator, read-only for members
          SectionCard(backgroundColor = AppColors.primary, borderColor = AppColors.divider) {
            UnderlinedLabel("Organisation:")
            Spacer(Modifier.height(12.dp))

            DateField(value = date, onValueChange = { date = it })

            Spacer(Modifier.height(10.dp))

            // Time field
            IconTextField(
                value = time,
                onValueChange = { time = it },
                placeholder = "Time",
                leadingIcon = {
                  // Using CalendarToday to keep icon set light; replace with alarm icon if you
                  // prefer.
                  // opens a date picker for admins and the session creator
                  Icon(Icons.Default.AccessTime, contentDescription = "Time")
                },
                trailingIcon = { TextButton(onClick = { /* open time picker */}) { Text("Pick") } })

            Spacer(Modifier.height(10.dp))

            // Location field
            // could be redone like the bootcamp
            // using a search field with suggestions and map integration
            // for now it's just a text field
            IconTextField(
                value = location,
                onValueChange = { location = it },
                placeholder = "Location",
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") })
          }

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
    var date by remember { mutableStateOf("2025-10-15") }
    Column(Modifier.padding(16.dp)) { DateField(value = date, onValueChange = { date = it }) }
  }
}
