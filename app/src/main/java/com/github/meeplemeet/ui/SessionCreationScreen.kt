// All the components and the screen were done initially by hand. They were then given to a
// LLM (ChatGPT-5 Thinking Extend) to verify it's correctness and propose improvements.
// The suggestions were then manually reviewed and integrated where/when relevant.
// A final manual refactoring was done.
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.components.CountBubble
import com.github.meeplemeet.ui.components.DatePickerDockedField
import com.github.meeplemeet.ui.components.DiscretePillSlider
import com.github.meeplemeet.ui.components.IconTextField
import com.github.meeplemeet.ui.components.LabeledTextField
import com.github.meeplemeet.ui.components.ParticipantAction
import com.github.meeplemeet.ui.components.ParticipantChip
import com.github.meeplemeet.ui.components.SectionCard
import com.github.meeplemeet.ui.components.TimePickerField
import com.github.meeplemeet.ui.components.TwoPerRowGrid
import com.github.meeplemeet.ui.components.UnderlinedLabel
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.Elevation
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
const val GAME_SECTION_NAME: String = "Proposed game"
const val ORGANISATION_SECTION_NAME: String = "Organisation"
const val GAME_SEARCH_PLACEHOLDER: String = "Search games…"

const val MAX_SLIDER_NUMBER: Float = 9f
const val MIN_SLIDER_NUMBER: Float = 1f
const val SLIDER_STEPS: Int = (MAX_SLIDER_NUMBER - MIN_SLIDER_NUMBER - 1).toInt()

/* =======================================================================
 * Main screen
 * ======================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    viewModel: FirestoreViewModel,
    currentUser: Account,
    discussionId: String,
    onCreate: (SessionForm) -> Unit = {},
    onBack: () -> Unit = {},
) {
  var form by
      remember(currentUser.uid) { mutableStateOf(SessionForm(participants = listOf(currentUser))) }

  val discussion by viewModel.discussionFlow(discussionId).collectAsState()
  val availableParticipants = remember { mutableStateListOf<Account>() }

  LaunchedEffect(discussion?.participants) {
    availableParticipants.clear()
    val uids = discussion?.participants.orEmpty()
    uids.forEach { uid ->
      if (uid == currentUser.uid) {
        if (availableParticipants.none { it.uid == uid }) {
          availableParticipants.add(currentUser)
        }
      } else {
        viewModel.getOtherAccount(uid) { acc ->
          if (availableParticipants.none { it.uid == acc.uid }) {
            availableParticipants.add(acc)
          }
        }
      }
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

              // Proposed game section
              GameSection(
                  query = form.proposedGame,
                  onQueryChange = { form = form.copy(proposedGame = it) },
                  onSearchClick = { /* TODO: open game search */},
                  title = GAME_SECTION_NAME,
                  placeholder = GAME_SEARCH_PLACEHOLDER)

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

              // Organisation section
              OrganisationSection(
                  date = form.date,
                  time = form.time,
                  locationText = form.locationText,
                  onDateChange = { form = form.copy(date = it) },
                  onTimeChange = { form = form.copy(time = it) },
                  onLocationChange = { form = form.copy(locationText = it) },
                  onPickLocation = { /* TODO open location picker */},
                  title = ORGANISATION_SECTION_NAME)

              Spacer(Modifier.height(4.dp))

              // Creation and Discard buttons
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CreateSessionButton(
                    formToSubmit = form,
                    onCreate = { onCreate(form) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DiscardButton(
                    modifier = Modifier.fillMaxWidth(),
                    onDiscard = {
                      form = SessionForm(participants = listOf(currentUser))
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
 * A button to create the session.
 *
 * @param formToSubmit the form data to submit when creating the session
 * @param onCreate the callback to be invoked when the button is clicked
 * @param modifier the modifier to be applied to the button
 */
@Composable
private fun CreateSessionButton(
    formToSubmit: SessionForm,
    onCreate: (SessionForm) -> Unit,
    modifier: Modifier = Modifier
) {
  Button(
      onClick = { onCreate(formToSubmit) },
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
 * A button to discard the session creation.
 *
 * @param onDiscard the callback to be invoked when the button is clicked
 * @param modifier the modifier to be applied to the button
 */
@Composable
private fun DiscardButton(modifier: Modifier = Modifier, onDiscard: () -> Unit) {
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
 * The participants section allowing to select and manage participants.
 *
 * @param currentUserId the UID of the current user
 * @param selected the list of currently selected participants
 * @param allCandidates the list of all possible participant candidates
 * @param minPlayers the current minimum number of players
 * @param maxPlayers the current maximum number of players
 * @param onMinMaxChange the callback to be invoked when the min or max players change
 * @param onAdd the callback to be invoked when a participant is added
 * @param onRemove the callback to be invoked when a participant is removed
 * @param minSliderNumber the minimum value for the slider
 * @param maxSliderNumber the maximum value for the slider
 * @param sliderSteps the number of discrete steps for the slider
 * @param mainSectionTitle the title of the main section
 * @param sliderDescription the description text for the slider
 * @param elevationSelected the elevation for selected participant chips
 * @param elevationUnselected the elevation for unselected participant chips
 * @param modifier the modifier to be applied to the section
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
        val selectedIds by remember(selected) { mutableStateOf(selected.map { it.uid }.toSet()) }
        val candidates by
            remember(allCandidates, selectedIds, currentUserId) {
              mutableStateOf(
                  allCandidates.filter { it.uid != currentUserId && it.uid !in selectedIds })
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
 * The organisation section containing date, time, and location fields.
 *
 * @param date the currently selected date
 * @param time the currently selected time
 * @param locationText the current location text
 * @param onDateChange the callback to be invoked when the date changes
 * @param onTimeChange the callback to be invoked when the time changes
 * @param onLocationChange the callback to be invoked when the location text changes
 * @param onPickLocation the optional callback to be invoked when the "Pick" button is clicked
 * @param title the title of the section
 * @param modifier the modifier to be applied to the section
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
    onPickLocation: (() -> Unit)? = null,
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

        IconTextField(
            value = locationText,
            onValueChange = onLocationChange,
            placeholder = "Location",
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") },
            trailingIcon = {
              if (onPickLocation != null) {
                TextButton(onClick = onPickLocation) { Text("Pick") }
              }
            },
            modifier = Modifier.fillMaxWidth())
      }
}

/**
 * The game section containing a search field for the proposed game.
 *
 * @param query the current game query text
 * @param onQueryChange the callback to be invoked when the query text changes
 * @param onSearchClick the callback to be invoked when the search icon is clicked
 * @param title the title of the section
 * @param placeholder the placeholder text for the search field
 * @param modifier the modifier to be applied to the section
 */
@Composable
fun GameSection(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    title: String = GAME_SECTION_NAME,
) {
  SectionCard(
      modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          UnderlinedLabel("$title:")
          Spacer(Modifier.width(4.dp))
          Text(
              text = query,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(6.dp))

        IconTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            trailingIcon = {
              IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search")
              }
            },
            modifier = Modifier.fillMaxWidth())
      }
}

/* =======================================================================
 * Preview
 * ======================================================================= */

/**
 * @Preview(showBackground = true, name = "Create Session")
 * @Composable private fun CreateSessionPreview_Dark() { AppTheme { val all = listOf( Account("1",
 *   "Marco", email = "marco@example.com"), Account("2", "Alexandre", email =
 *   "alexandre@example.com"), Account("3", "Antoine", email = "antoine@example.com"), Account("4",
 *   "Dany", email = "dany@example.com"), Account("5", "Enes", email = "enes@example.com"),
 *   Account("6", "Nil", email = "nil@example.com"), Account("7", "Thomas", email =
 *   "thomas@example.com"), )
 *
 * CreateSessionScreen( currentUser = Account("1", "Marco", email = "marco@epfl.ch"), discussionId =
 * "", // If your CreateSessionScreen still accepts this param: availableParticipants = all ) } }
 */
@Composable
private fun CreateSessionScreenPreviewHost() {
  val me = Account(uid = "1", name = "Marco", email = "marco@epfl.ch", handle = "")
  val pool =
      listOf(
          me,
          Account("2", name = "Alexandre", email = "alexandre@example.com", handle = ""),
          Account("3", name = "Antoine", email = "antoine@example.com", handle = ""),
          Account("4", name = "Dany", email = "dany@example.com", handle = ""),
          Account("5", name = "Enes", email = "enes@example.com", handle = ""),
          Account("6", name = "Nil", email = "nil@example.com", handle = ""),
          Account("7", name = "Thomas", email = "thomas@example.com", handle = ""),
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
              IconButton(onClick = /* TODO */ {}) {
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

              // Game section
              GameSection(
                  query = form.proposedGame,
                  onQueryChange = { form = form.copy(proposedGame = it) },
                  onSearchClick = {},
                  title = GAME_SECTION_NAME,
                  placeholder = GAME_SEARCH_PLACEHOLDER)

              // Participants section (Accounts)
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

              // Organisation section
              OrganisationSection(
                  date = form.date,
                  time = form.time,
                  locationText = form.locationText,
                  onDateChange = { form = form.copy(date = it) },
                  onTimeChange = { form = form.copy(time = it) },
                  onLocationChange = { form = form.copy(locationText = it) },
                  onPickLocation = {},
                  title = ORGANISATION_SECTION_NAME)

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

@Preview(showBackground = true, name = "Create Session – Dark (safe preview)")
@Composable
private fun CreateSessionPreview_Dark_Safe() {
  AppTheme { CreateSessionScreenPreviewHost() }
}

@Preview(showBackground = true, name = "Create Session – Lower area")
@Composable
private fun Preview_CreateSession_LowerArea() {
  AppTheme {
    Column(
        modifier =
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
          var date by remember { mutableStateOf(LocalDate.of(2025, 10, 15)) }
          var time by remember { mutableStateOf(LocalTime.of(19, 0)) }
          var location by remember { mutableStateOf("Student Lounge") }

          OrganisationSection(
              date = date,
              time = time,
              locationText = location,
              onDateChange = { date = it },
              onTimeChange = { time = it },
              onLocationChange = { location = it },
              onPickLocation = {},
              title = ORGANISATION_SECTION_NAME)

          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CreateSessionButton(
                formToSubmit = SessionForm(), // preview stub
                onCreate = {},
                modifier = Modifier.fillMaxWidth(),
            )
            DiscardButton(modifier = Modifier.fillMaxWidth(), onDiscard = {})
          }
        }
  }
}

@Preview(showBackground = true, name = "Game Section")
@Composable
private fun Preview_GameSection() {
  AppTheme {
    var q by remember { mutableStateOf("Root") }
    GameSection(
        query = q,
        onQueryChange = { q = it },
        onSearchClick = {},
        modifier = Modifier.padding(16.dp),
        title = GAME_SECTION_NAME,
        placeholder = GAME_SEARCH_PLACEHOLDER)
  }
}

@Preview(showBackground = true, name = "Participants – Basic")
@Composable
private fun ParticipantsSectionPreview() {
  AppTheme {
    val me = Account("1", name = "Marco", email = "marco@example.com", handle = "")
    val pool =
        listOf(
            me,
            Account("2", name = "Alexandre", email = "alexandre@example.com", handle = ""),
            Account("3", name = "Antoine", email = "antoine@example.com", handle = ""),
            Account("4", name = "Dany", email = "dany@example.com", handle = ""),
            Account("5", name = "Enes", email = "enes@example.com", handle = ""),
            Account("6", name = "Nil", email = "nil@example.com", handle = ""),
            Account("7", name = "Thomas", email = "thomas@example.com", handle = ""),
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
        onPickLocation = {},
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
