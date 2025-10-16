package com.github.meeplemeet.ui

import android.annotation.SuppressLint
import android.util.Log
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
import androidx.compose.material.icons.filled.ArrowBackIosNew
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
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.appShapes
import java.time.LocalTime
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
  const val CHAT_BADGE = "chat_badge"
  const val NOTIFICATION_BADGE_COUNT = "notification_badge_count"
  const val EMPTY_BADGE = "empty_badge"
}

/* =======================================================================
 * Models
 * ======================================================================= */

data class Participant(val id: String, val name: String)

val sliderMinRange = 2f
val sliderMaxRange = 10f

/* =======================================================================
 * Public entry point
 * ======================================================================= */

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
  // Local state for the form
  var form by remember { mutableStateOf(initial) }

  val discussion by viewModel.discussionFlow(discussionId).collectAsState()
  val isCurrUserAdmin =
      currentUser.uid == discussion?.creatorId ||
          discussion?.admins?.contains(currentUser.uid) == true

  // Scaffold with top bar and content
  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = "Session View",
            onReturn = {
              onBack()
              /** save the data */
            },
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

          // Title
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
          } ?: Box(Modifier.fillMaxWidth()) { /* loading placeholder */}
          //          ProposedGameSection(
          //              sessionViewModel = sessionViewModel,
          //              currentUser = currentUser,
          //              discussion = discussion!!,
          //              editable = isCurrUserAdmin)

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
              modifier =
                  Modifier.fillMaxWidth().then(Modifier.testTag(SessionTestTags.QUIT_BUTTON)),
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

        // Chips
        UserChipsGrid(
            participants = form.participants,
            onRemove = { p -> onRemoveParticipant(p) },
            editable = editable)
      }
}

@Composable
private fun ProposedGameSection(
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussion: Discussion,
    editable: Boolean
) {
  val gameUIState by sessionViewModel.gameUIState.collectAsState()
  //  Log.d("Session debug", "ProposedGameSection editable=$editable,
  // currentUser=${currentUser.uid}")
  Log.d("DEBUG setGameQuery", "currentUser=${currentUser.uid}, admins=${discussion.admins}")

  SectionCard(
      modifier =
          Modifier.clip(appShapes.extraLarge)
              .background(AppColors.primary)
              .border(1.dp, AppColors.primary)) {
        Column(modifier = Modifier.fillMaxWidth().testTag(SessionTestTags.PROPOSED_GAME)) {
          UnderlinedLabel(
              text = "Proposed game:",
              textColor = AppColors.textIcons,
              textStyle = MaterialTheme.typography.titleLarge)

          Spacer(Modifier.height(8.dp))

          if (editable) {
            // 🔸 Replace everything with a single call to GameSearchField
            GameSearchField(
                query = gameUIState.gameQuery,
                onQueryChange = { sessionViewModel.setGameQuery(currentUser, discussion, it) },
                results = gameUIState.gameSuggestions,
                onPick = { sessionViewModel.setGame(currentUser, discussion, it) },
                isLoading = false,
                modifier = Modifier.fillMaxWidth().testTag(SessionTestTags.PROPOSED_GAME))
          } else {
            // Member view — just show the current game text
            Text(
                text = "Current Game",
                modifier = Modifier.testTag(SessionTestTags.PROPOSED_GAME).padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.textIcons)
          }
        }
      }
}

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
              modifier = Modifier.fillMaxWidth(),
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
        onClick = { onclickNotification() })
    BadgedIconButton(
        icon = Icons.Default.ChatBubbleOutline,
        modifier = Modifier.testTag(SessionTestTags.CHAT_BADGE),
        contentDescription = "Messages",
        badgeCount = 0,
        onClick = { onclickChat() })
  }
}

@Composable
fun UserChip(
    name: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    showRemoveBTN: Boolean = false
) {
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
        if (showRemoveBTN) {
          IconButton(onClick = onRemove, modifier = Modifier.size(18.dp)) {
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
              name = p.name, onRemove = { if (editable) onRemove(p) }, showRemoveBTN = editable)
        }

        // Add button chip (to add new participants)
        // might be implemented later (users might joining the session themselves)
      }
}

/**
 * Compact, discrete "pill" styled range slider with subtle rounded track & dots. This mirrors the
 * blue/red dotted pills in the mock (generic visual).
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
        modifier = Modifier.fillMaxWidth().testTag("delete_session_button"),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
          Icon(Icons.Default.Delete, contentDescription = "Delete Session")
          Spacer(Modifier.width(8.dp))
          Text("Delete Session", style = MaterialTheme.typography.bodyMedium)
        }
  }
}

/* =======================================================================
 * Preview
 * ======================================================================= */
// @OptIn(ExperimentalMaterial3Api::class)
// @Preview(showBackground = true, name = "datePicker")
// @Composable
// private fun Preview_datePicker() {
//    AppTheme {
//        val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)
//
//        DatePicker(
//            title = { Text("Select date") },
//            state = datePickerState,
//            colors =
//                DatePickerDefaults.colors(
//                    containerColor = AppColors.primary,
//                    titleContentColor = AppColors.textIconsFade,
//                    headlineContentColor = AppColors.textIcons,
//                    selectedDayContentColor = AppColors.neutral,
//                ))
//    }
// }
//
// @Preview(showBackground = true, name = "SectionCard")
// @Composable
// private fun Preview_SectionCard() {
//    AppTheme {
//        Column {
//            SectionCard(
//                Modifier.clip(appShapes.extraLarge)
//                    .background(AppColors.primary)
//                    .border(1.dp, AppColors.primary, shape = appShapes.extraLarge)) {
//                UnderlinedLabel("Sample section")
//                Spacer(Modifier.height(8.dp))
//                Text(
//                    "Any content goes in here; this container uses your theme shapes and
// borders.",
//                    style = MaterialTheme.typography.bodySmall,
//                    modifier = Modifier.padding(top = 4.dp))
//            }
//            Spacer(Modifier.height(12.dp))
//            SectionCard(
//                Modifier.clip(appShapes.extraLarge)
//                    .background(AppColors.secondary)
//                    .border(1.dp, AppColors.secondary, shape = appShapes.extraLarge)) {
//                UnderlinedLabel("Another section")
//                Spacer(Modifier.height(8.dp))
//                Text(
//                    "This is a second SectionCard using the main composable.",
//                    style = MaterialTheme.typography.bodySmall,
//                    modifier = Modifier.padding(top = 4.dp))
//            }
//        }
//    }
// }
//
// @Preview(showBackground = true, name = "UnderlinedLabel")
// @Composable
// private fun Preview_UnderlinedLabel() {
//    AppTheme {
//        Column(Modifier.padding(16.dp)) {
//            UnderlinedLabel("Proposed game:")
//            Spacer(Modifier.height(8.dp))
//            UnderlinedLabel("Participants:")
//        }
//    }
// }
//
// @Preview(showBackground = true, name = "IconTextField")
// @Composable
// private fun Preview_IconTextField() {
//    AppTheme {
//        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
//            IconTextField(
//                value = "",
//                onValueChange = {},
//                placeholder = "Search games",
//                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
//                textStyle = MaterialTheme.typography.bodySmall,
//                modifier = Modifier)
//            IconTextField(
//                value = "2025-10-15",
//                onValueChange = {},
//                placeholder = "Date",
//                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
//                textStyle = MaterialTheme.typography.bodySmall,
//                modifier = Modifier)
//            IconTextField(
//                value = "Student Lounge",
//                onValueChange = {},
//                placeholder = "Location",
//                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
//                textStyle = MaterialTheme.typography.bodySmall,
//                modifier = Modifier)
//        }
//    }
// }
//
// @Preview(showBackground = true, name = "CountBubble")
// @Composable
// private fun Preview_CountBubble() {
//    AppTheme {
//        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//            CountBubble(
//                0,
//                modifier =
//                    Modifier.clip(CircleShape)
//                        .background(AppColors.primary)
//                        .border(1.dp, AppColors.secondary, CircleShape)
//                        .padding(horizontal = 10.dp, vertical = 6.dp))
//            CountBubble(
//                3,
//                modifier =
//                    Modifier.clip(CircleShape)
//                        .background(AppColors.secondary)
//                        .border(1.dp, AppColors.secondary, CircleShape)
//                        .padding(horizontal = 10.dp, vertical = 6.dp))
//            CountBubble(
//                12,
//                modifier =
//                    Modifier.clip(CircleShape)
//                        .background(AppColors.affirmative)
//                        .border(1.dp, AppColors.secondary, CircleShape)
//                        .padding(horizontal = 10.dp, vertical = 6.dp))
//        }
//    }
// }
//
// @Preview(showBackground = true, name = "ParticipantChip")
// @Composable
// private fun Preview_ParticipantChip() {
//    AppTheme {
//        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//            UserChip(name = "user1", onRemove = {})
//            UserChip(name = "Alice", onRemove = {})
//        }
//    }
// }
//
// @Preview(showBackground = true, name = "ParticipantChipsGrid")
// @Composable
// private fun Preview_ParticipantChipsGrid() {
//    AppTheme {
//        UserChipsGrid(
//            participants =
//                listOf(
//                    Participant("1", "user1"),
//                    Participant("2", "John Doe"),
//                    Participant("3", "Alice"),
//                    Participant("4", "Bob"),
//                    Participant("5", "Robert")),
//            onRemove = {})
//    }
// }
//
// @Preview(showBackground = true, name = "DiscretePillSlider")
// @Composable
// private fun Preview_DiscretePillSlider() {
//    AppTheme {
//        var values by remember { mutableStateOf(3f..6f) }
//        Column(Modifier.padding(16.dp)) {
//            PillSliderNoBackground(
//                title = "Players",
//                range = 2f..10f,
//                values = values,
//                steps = 7,
//                onValuesChange = { start, end -> values = start..end })
//        }
//    }
// }
//
// @Preview(showBackground = true, name = "BadgedIconButton")
// @Composable
// private fun Preview_BadgedIconButton() {
//    AppTheme { TopRightIcons() }
// }
//

// Full screen preview (kept separate from the sub-component previews)
// @Preview(showBackground = true, name = "Create Session – Full")
// @Composable
// private fun Preview_SessionView_Full() {
//  val isAdmin = false
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
//                editable = isAdmin,
//                form,
//                modifier = Modifier.align(Alignment.CenterHorizontally))
//
//            // Proposed game section
//            // background and border are primary for members since it blends with the screen bg
//            // proposed game is a text for members, it's not in a editable box
//            val mockDiscussion =
//                Discussion(
//                    uid = "disc1",
//                    creatorId = "00001",
//                    name = "Friday Night Meetup",
//                    description = "something",
//                    messages = emptyList(),
//                    participants = listOf("00001", "00002", "00003", "00004", "00005"),
//                    admins = listOf("00001", "00002"),
//                    session = null)
//            ProposedGameSection(
//                sessionViewModel = FirestoreSessionViewModel(mockDiscussion),
//                currentUser =
//                    Account(
//                        uid = "00001", name = "user1", email = "hehe@example.com", handle = "u1"),
//                discussion = mockDiscussion,
//                editable = isAdmin)
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
//
//            DeleteSessionBTN(
//                sessionViewModel = FirestoreSessionViewModel(mockDiscussion),
//                currentUser =
//                    Account(uid = "00001", name = "user1", email = "01@example.com", handle =
// "01"),
//                discussion = mockDiscussion,
//                userIsAdmin = isAdmin,
//                onback = {})
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
//    AppTheme {
//        var form by remember {
//            mutableStateOf(SessionForm(dateText = LocalDate.now().toString(), timeText = "19:30",
// locationText =
//                "EPFL"))
//        }
//        OrganizationSection(form, onFormChange = { form = it })
//    }
// }
//
// @Preview(showBackground = true, name = "Session View – Lower area")
// @Composable
// private fun Preview_Session_LowerArea() {
//    AppTheme {
//        var form by remember {
//            mutableStateOf(
//                SessionForm(dateText = LocalDate.now().toString(), timeText = "19:30",
// locationText = "Satellite "))
//        }
//        Column(
//            modifier = Modifier.fillMaxWidth().background(AppColors.primary).padding(16.dp),
//            verticalArrangement = Arrangement.spacedBy(16.dp)) {
//            // Organisation section (reuse composable)
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
//                Icon(Icons.Default.ArrowBack, contentDescription = null)
//                Spacer(Modifier.width(8.dp))
//                Text("Quit Session", style = MaterialTheme.typography.bodyMedium)
//            }
//        }
//    }
// }
//
// @Preview(showBackground = true, name = "DateField and DatePickerDialog")
// @Composable
// private fun Preview_DateField_DatePickerDialog() {
//    AppTheme {
//        var date by remember { mutableStateOf(LocalDate.now()) }
//        Column(Modifier.padding(16.dp)) { DatePickerDockedField(value = date, onValueChange = {
// date = it }) }
//    }
// }
//
// @Preview(showBackground = true, name = "TimeField and TimePickerDialog")
// @Composable
// private fun Preview_TimeField_TimePickerDialog() {
//    AppTheme {
//        var time by remember { mutableStateOf("18:30") }
//        Column(Modifier.padding(16.dp)) { TimeField(value = time, onValueChange = { time = it }) }
//    }
// }
//
// @OptIn(ExperimentalMaterial3Api::class)
// @Preview(showBackground = true, name = "TimePicker")
// @Composable
// private fun Preview_TimePicker() {
//    AppTheme {
//        // Initialize with example time
//        val timePickerState = rememberTimePickerState(is24Hour = false)
//
//        // Place the TimePicker inside a Column to make it visible
//        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
//            TimePicker(
//                state = timePickerState,
//                colors =
//                    TimePickerDefaults.colors(
//                        clockDialColor = AppColors.secondary,
//                        clockDialSelectedContentColor = AppColors.primary,
//                        clockDialUnselectedContentColor = AppColors.textIconsFade,
//                        selectorColor = AppColors.neutral,
//                        periodSelectorBorderColor = AppColors.textIconsFade,
//                        periodSelectorSelectedContainerColor = AppColors.secondary,
//                        periodSelectorSelectedContentColor = AppColors.negative,
//                        timeSelectorSelectedContainerColor = AppColors.neutral,
//                        timeSelectorUnselectedContainerColor = AppColors.secondary,
//                    ))
//        }
//    }
// }

@Composable
fun FakeSessionViewScreen() {
  // 🔸 Toggle this to see how UI reacts for admin vs member
  val isAdmin = remember { mutableStateOf(true) }

  // 🔸 Fake data
  var form by remember {
    mutableStateOf(
        SessionForm(
            title = "Friday Night Meetup",
            minPlayers = 3,
            maxPlayers = 6,
            participants =
                listOf(
                    Account("1", "Marco", "Marco", "e@"),
                    Account("2", "John Doe", "john", "e@"),
                    Account("3", "Alice", "alice", "e@"),
                    Account("4", "Bob", "bob", "e@"),
                    Account("5", "Robert", "robert", "e@")),
            time = LocalTime.of(19, 0),
            locationText = "Student Lounge"))
  }

  val mockDiscussion =
      Discussion(
          uid = "discussion123",
          creatorId = "marcoUID",
          name = "Friday Night Meetup",
          description = "Weekly board game hangout",
          messages = emptyList(),
          participants = listOf("marcoUID") + form.participants.map { it.uid },
          admins = listOf("marcoUID"),
          session = null)

  Log.d(
      "DEBUG FAKE",
      "isContained=${form.participants.map{it.uid}.contains("marcoUID")}, admins=${mockDiscussion.admins}")

  val currentUser =
      if (isAdmin.value) {
        Account("marcoUID", "Marco", "marco", "marco@epfl.ch")
      } else {
        Account("user2", "John Doe", "john", "john@example.com")
      }

  val fakeSessionVM = FirestoreSessionViewModel(mockDiscussion)
  val onBack = {}
  val onclickChat = {}
  val onclickNotification = {}

  AppTheme {
    Scaffold(
        topBar = {
          TopBarWithDivider(
              text = "Session View",
              onReturn = {
                onBack()
                /** save the data */
              },
              {
                TopRightIcons(onclickChat = onclickChat, onclickNotification = onclickNotification)
              })
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

            // --- Title ---
            Title(
                text = form.title.ifEmpty { "New Session" },
                editable = isAdmin.value,
                form = form,
                onValueChange = { newTitle -> form = form.copy(title = newTitle) },
                modifier = Modifier.align(Alignment.CenterHorizontally))

            // --- Proposed game section ---
            ProposedGameSection(
                sessionViewModel = fakeSessionVM,
                currentUser = currentUser,
                discussion = mockDiscussion,
                editable = isAdmin.value)

            // --- Participants section ---
            ParticipantsSection(
                editable = isAdmin.value,
                form = form,
                onFormChange = { min, max ->
                  form = form.copy(minPlayers = min.roundToInt(), maxPlayers = max.roundToInt())
                },
                onRemoveParticipant = { participant ->
                  form =
                      form.copy(
                          participants = form.participants.filterNot { it.uid == participant.uid })
                })

            // --- Organisation section ---
            OrganizationSection(form = form, onFormChange = { form = it }, editable = isAdmin.value)

            Spacer(Modifier.height(4.dp))

            // --- Quit session button ---
            OutlinedButton(
                onClick = { println("Quit session clicked for user: ${currentUser.name}") },
                shape = CircleShape,
                border = BorderStroke(1.5.dp, AppColors.negative),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                  Icon(Icons.Default.ArrowBackIosNew, contentDescription = null)
                  Spacer(Modifier.width(8.dp))
                  Text("Quit Session", style = MaterialTheme.typography.bodyMedium)
                }

            // --- Delete session button (admin only) ---
            if (isAdmin.value) {
              OutlinedButton(
                  onClick = { println("Delete session clicked") },
                  shape = CircleShape,
                  border = BorderStroke(1.5.dp, AppColors.negative),
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Session", style = MaterialTheme.typography.bodyMedium)
                  }
            }

            // --- Permission toggle ---
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { isAdmin.value = !isAdmin.value },
                border = BorderStroke(1.dp, AppColors.focus),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.focus)) {
                  val roleText = if (isAdmin.value) "Switch to Member" else "Switch to Admin"
                  Text(roleText, style = MaterialTheme.typography.bodyMedium)
                }
          }
    }
  }
}
