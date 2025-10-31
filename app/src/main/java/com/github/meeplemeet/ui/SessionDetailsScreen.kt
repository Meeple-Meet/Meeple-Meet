package com.github.meeplemeet.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Game
import com.github.meeplemeet.model.structures.Location
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.model.viewmodels.GameUIState
import com.github.meeplemeet.ui.components.CountBubble
import com.github.meeplemeet.ui.components.DatePickerDockedField
import com.github.meeplemeet.ui.components.DiscretePillSlider
import com.github.meeplemeet.ui.components.GameSearchField
import com.github.meeplemeet.ui.components.IconTextField
import com.github.meeplemeet.ui.components.LocationSearchField
import com.github.meeplemeet.ui.components.SectionCard
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.components.UnderlinedLabel
import com.github.meeplemeet.ui.theme.AppColors
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
 * @param sessionViewModel ViewModel managing session-specific operations
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
    viewModel: FirestoreViewModel,
    sessionViewModel: FirestoreSessionViewModel,
    initial: SessionForm = SessionForm(),
    onBack: () -> Unit = {},
) {
  var form by remember { mutableStateOf(initial) }
  val gameUIState by sessionViewModel.gameUIState.collectAsState()

  // Fetch game as soon as we know the proposed game.
  // This LaunchedEffect triggers whenever the session's gameId changes,
  // ensuring the UI always has the latest game info.
  val sessionGameId = discussion.session?.gameId.orEmpty()
  LaunchedEffect(sessionGameId) {
    if (sessionGameId.isNotBlank()) {
      sessionViewModel.getGameFromId(sessionGameId)
    }
  }

  val game = gameUIState.fetchedGame
  val session = discussion.session!!

  // This LaunchedEffect block updates the form state when the session or game changes.
  // It fetches participant accounts and updates the UI fields accordingly.
  // If the game info is missing, it triggers a fetch for the proposed game.
  LaunchedEffect(session.gameId, game) {
    val (date, time) = timestampToLocal(session.date)

    viewModel.getAccounts(session.participants) { accounts ->
      form =
          form.copy(
              title = session.name,
              date = date,
              time = time,
              proposedGameString = session.gameId,
              minPlayers = session.minParticipants,
              maxPlayers = session.maxParticipants,
              participants = accounts,
              locationText = session.location.name)

      if (form.proposedGameString.isNotBlank())
          sessionViewModel.getGameFromId(form.proposedGameString)
    }

    if (session.gameId.isNotBlank() && game == null) {
      sessionViewModel.getGameFromId(session.gameId)
    }
  }

  // Determine if the current user is an admin or the creator of the discussion.
  val isCurrUserAdmin =
      account.uid == discussion.creatorId || discussion.admins.contains(account.uid)

  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = "Session Details",
            onReturn = {
              // Only admins/owners can persist changes to the session on back navigation.
              if (isCurrUserAdmin) {
                sessionViewModel.updateSession(
                    requester = account,
                    discussion = discussion,
                    name = form.title,
                    gameId = form.proposedGameString,
                    date = toTimestamp(form.date, form.time),
                    location = Location(0.0, 0.0, form.locationText),
                    minParticipants = form.minPlayers,
                    maxParticipants = form.maxPlayers,
                    newParticipantList = form.participants.ifEmpty { emptyList() })
              }
              onBack()
            },
        )
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

          // Organisation section (session info and controls)
          // Editable for admins and the session creator, read-only for members.
          OrganizationSection(
              form = form,
              onFormChange = { form = it },
              editable = isCurrUserAdmin,
              discussion = discussion,
              account = account,
              gameUIState = gameUIState,
              onValueChangeTitle = { form = form.copy(title = it) },
              isCurrUserAdmin = isCurrUserAdmin,
              sessionViewModel = sessionViewModel)

          // Participants section (chips, add/remove)
          ParticipantsSection(
              form = form,
              editable = isCurrUserAdmin,
              account = account,
              game = game,
              onRemoveParticipant = { p ->
                form = form.copy(participants = form.participants.filterNot { it.uid == p.uid })
              },
              onAddParticipant = { p -> form = form.copy(participants = form.participants + p) },
              discussion = discussion,
              viewModel = viewModel)

          Spacer(Modifier.height(4.dp))
          // Row with Leave and Delete buttons.
          // - "Leave" is available to all users and removes the current user from participants.
          //   If the user is the last participant, the session is deleted.
          // - "Delete" is shown only to admins/owners (see DeleteSessionBTN for logic).
          Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                      val updatedParticipants =
                          form.participants.filterNot { it.uid == account.uid }
                      discussion.let { disc ->
                        if (updatedParticipants.isNotEmpty())
                            sessionViewModel.updateSession(
                                requester = account,
                                discussion = disc,
                                newParticipantList = updatedParticipants)
                        else sessionViewModel.deleteSession(account, disc)
                      }
                      onBack()
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, AppColors.negative),
                    modifier = Modifier.weight(1f).testTag(SessionTestTags.QUIT_BUTTON),
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                      Spacer(Modifier.width(8.dp))
                      Text("Leave", style = MaterialTheme.typography.bodyMedium)
                    }

                // "Delete" button is only visible for admins/owners (see DeleteSessionBTN).
                DeleteSessionBTN(
                    sessionViewModel = sessionViewModel,
                    currentUser = account,
                    discussion = discussion,
                    userIsAdmin = isCurrUserAdmin,
                    onback = onBack,
                    modifier = Modifier.weight(1f))
              }
        }
  }
}

/* =======================================================================
 * Sub-components
 * ======================================================================= */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParticipantsSection(
    form: SessionForm,
    account: Account,
    editable: Boolean = false,
    game: Game?,
    onRemoveParticipant: (Account) -> Unit,
    onAddParticipant: (Account) -> Unit,
    discussion: Discussion,
    viewModel: FirestoreViewModel
) {
  val participants = form.participants
  val currentCount = participants.size
  val max = form.maxPlayers

  // Fetch discussion members (UID -> Account) once and keep in state
  var candidateAccounts by remember { mutableStateOf<List<Account>>(emptyList()) }
  LaunchedEffect(discussion.participants) {
    viewModel.getAccounts(discussion.participants) { accounts -> candidateAccounts = accounts }
  }

  // UI shell
  SectionCard(modifier = Modifier.clip(appShapes.extraLarge).background(AppColors.primary)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      UnderlinedLabel(
          text = "Participants:",
          textColor = AppColors.textIcons,
          textStyle = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.width(8.dp))
      CountBubble(
          count = currentCount,
          modifier =
              Modifier.clip(CircleShape)
                  .background(AppColors.affirmative)
                  .border(1.dp, AppColors.affirmative, CircleShape)
                  .padding(horizontal = 10.dp, vertical = 6.dp))
    }

    Spacer(Modifier.height(12.dp))

    // Slider (visual-only)
    if (game != null) {
      PillSliderNoBackground(
          title = "Number of players",
          range = game.minPlayers.toFloat()..game.maxPlayers.toFloat(),
          values = game.minPlayers.toFloat()..game.maxPlayers.toFloat(),
          steps = (game.maxPlayers - game.minPlayers - 1).coerceAtLeast(0))

      // Min/Max bubbles (below)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CountBubble(
            count = game.minPlayers,
            modifier =
                Modifier.clip(CircleShape)
                    .background(AppColors.secondary)
                    .border(1.dp, AppColors.secondary, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag(SessionTestTags.MIN_PLAYERS))
        CountBubble(
            count = game.maxPlayers,
            modifier =
                Modifier.clip(CircleShape)
                    .background(AppColors.secondary)
                    .border(1.dp, AppColors.secondary, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag(SessionTestTags.MAX_PLAYERS))
      }
    } else {
      Text("Loading slider...", color = AppColors.textIconsFade)
    }

    Spacer(Modifier.height(12.dp))

    // 👉 Delegate chips + add-button + dropdown to UserChipsGrid
    UserChipsGrid(
        participants = participants,
        onRemove = onRemoveParticipant,
        onAdd = onAddParticipant,
        account = account,
        editable = editable,
        candidateMembers = candidateAccounts, // full discussion members as Accounts
        maxPlayers = game?.maxPlayers ?: form.maxPlayers,
        modifier = Modifier.testTag(SessionTestTags.PARTICIPANT_CHIPS))
  }
}

/**
 * Component used to display the participants in a clean and flexible box Each chip shows a
 * participant name and optionally a remove button for admins.
 *
 * @param participants List of participants to display
 * @param onRemove Callback fn used when an Admin/Owner removes a participant
 * @param modifier Modifiers used for the component
 * @param account The current user that's viewing the session details
 * @param editable Whether the current user can edit (remove) participants
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserChipsGrid(
    participants: List<Account>,
    onRemove: (Account) -> Unit,
    onAdd: (Account) -> Unit,
    modifier: Modifier = Modifier,
    account: Account,
    editable: Boolean = false,
    candidateMembers: List<Account> = emptyList(),
    maxPlayers: Int = Int.MAX_VALUE
) {
  var showAddMenu by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }

  // Filter: not already a participant AND matches handle (case-insensitive)
  val filteredCandidates =
      candidateMembers
          .filter { m -> participants.none { it.uid == m.uid } }
          .filter { m -> m.handle.contains(searchQuery, ignoreCase = true) }

  // Set up vertical scroll with a maximum height of 3 rows of chips.
  val chipHeight = 40.dp
  val chipSpacing = 8.dp
  val maxRows = 3
  val maxHeight = (chipHeight * maxRows) + (chipSpacing * (maxRows - 1))
  val scrollState = rememberScrollState()

  Box(modifier = modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(scrollState)) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Remove scrollable from here; vertical scroll applied to parent Box
        modifier = Modifier.fillMaxWidth()) {
          // Existing participant chips
          participants.forEach { p ->
            UserChip(
                user = p,
                modifier = Modifier.testTag(SessionTestTags.chipsTag(p.uid)),
                onRemove = { if (editable) onRemove(p) },
                account = account,
                showRemoveBTN = editable)
          }

          // "+" button: admins only, disappears when full
          val canAdd = editable && filteredCandidates.isNotEmpty() && participants.size < maxPlayers
          if (canAdd) {
            Box(
                modifier =
                    Modifier.background(AppColors.primary)
                        .padding(
                            horizontal = 12.dp,
                            vertical = 7.dp) // mimic chip padding to vertically align elements
                ) {
                  IconButton(
                      onClick = { showAddMenu = true },
                      modifier =
                          Modifier.size(32.dp)
                              .border(1.dp, AppColors.divider, CircleShape)
                              .clip(CircleShape)
                              .background(AppColors.primary)
                              .testTag(SessionTestTags.ADD_PARTICIPANT_BUTTON)) {
                        Text("+", color = AppColors.textIcons, fontWeight = FontWeight.Bold)
                      }

                  DropdownMenu(
                      expanded = showAddMenu,
                      onDismissRequest = { showAddMenu = false },
                      modifier = Modifier.background(AppColors.primary),
                  ) {
                    // Search (by handle only)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search", color = AppColors.textIconsFade) },
                        singleLine = true,
                        modifier =
                            Modifier.padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .testTag(SessionTestTags.ADD_PARTICIPANT_SEARCH))

                    Spacer(Modifier.height(4.dp))

                    // Candidates list
                    filteredCandidates.forEach { member ->
                      DropdownMenuItem(
                          onClick = {
                            showAddMenu = false
                            onAdd(member)
                          },
                          modifier =
                              Modifier.testTag(SessionTestTags.addParticipantTag(member.uid)),
                          text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                              AvatarBubble(member.name)
                              Spacer(Modifier.width(10.dp))
                              Text(member.handle, color = AppColors.textIcons)
                            }
                          })
                    }
                  }
                }
          }
        }
  }
}

@Composable
private fun AvatarBubble(name: String) {
  Box(
      modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.LightGray),
      contentAlignment = Alignment.Center) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = AppColors.focus,
            fontWeight = FontWeight.Bold)
      }
}

@Composable
private fun ProposedGameSection(
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussion: Discussion,
    editable: Boolean,
    gameUIState: GameUIState,
    onChooseGame: (String) -> Unit
) {

  Column(modifier = Modifier.fillMaxWidth()) {
    if (editable) {
      GameSearchField(
          query = gameUIState.gameQuery,
          onQueryChange = { sessionViewModel.setGameQuery(currentUser, discussion, it) },
          results = gameUIState.gameSuggestions,
          onPick = {
            onChooseGame(it.uid)
            sessionViewModel.setGame(currentUser, discussion, it)
          },
          isLoading = false,
          modifier = Modifier.fillMaxWidth().testTag(SessionTestTags.PROPOSED_GAME))
    } else {
      val displayedName = gameUIState.fetchedGame?.name ?: "Loading..."
      Row {
        UnderlinedLabel(
            text = "Proposed Game:",
            textColor = AppColors.textIcons,
            textStyle = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = displayedName,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    sessionViewModel: FirestoreSessionViewModel,
    account: Account,
    onValueChangeTitle: (String) -> Unit,
    isCurrUserAdmin: Boolean,
    gameUIState: GameUIState,
    discussion: Discussion,
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
      modifier = Modifier.clip(appShapes.extraLarge).background(AppColors.primary).fillMaxWidth()) {
        Title(
            text = form.title.ifEmpty { "New Session" },
            editable = isCurrUserAdmin,
            onValueChange = { onValueChangeTitle(it) },
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .then(Modifier.testTag(SessionTestTags.TITLE)))

        Spacer(Modifier.height(10.dp))

        ProposedGameSection(
            sessionViewModel = sessionViewModel,
            currentUser = account,
            discussion = discussion,
            editable = editable,
            gameUIState = gameUIState) {}

        Spacer(Modifier.height(10.dp))

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
              isLoading = false,
              modifier = Modifier.testTag(SessionTestTags.LOCATION_FIELD),
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
    onValueChange: (String) -> Unit = {},
    modifier: Modifier
) {
  if (editable) {
    OutlinedTextField(
        value = text,
        label = { Text("Title", color = AppColors.textIconsFade) },
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium,
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
 * Composable used for the individual UserChip
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
    showRemoveBTN: Boolean = false
) {
  InputChip(
      selected = false,
      onClick = {},
      label = { Text(text = user.name, style = MaterialTheme.typography.bodySmall) },
      avatar = {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.LightGray),
            contentAlignment = Alignment.Center) {
              Text(
                  text = user.name.firstOrNull()?.toString() ?: "A",
                  color = AppColors.focus,
                  fontWeight = FontWeight.Bold)
            }
      },
      trailingIcon = {
        if (showRemoveBTN && account.handle != user.handle) {
          IconButton(
              onClick = onRemove,
              modifier =
                  Modifier.size(18.dp).testTag(SessionTestTags.removeParticipantTag(user.name))) {
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
 * Compact, discrete "pill" styled range slider with subtle rounded track & dots. This mirrors the
 * blue/red dotted pills in the mock (generic visual).
 *
 * @param title Text to display with the pill slider
 * @param range Range of values that the slider can attain
 * @param values Values that be attained by the slider
 * @param steps Number of steps to display
 * @param editable whether the current user can edit the slider (Admin/Owner only)
 */
@Composable
fun PillSliderNoBackground(
    title: String,
    range: ClosedFloatingPointRange<Float>,
    values: ClosedFloatingPointRange<Float>,
    steps: Int,
    editable: Boolean = true,
) {
  Column {
    Text(title, style = MaterialTheme.typography.labelSmall, color = AppColors.textIconsFade)
    Spacer(Modifier.height(6.dp))
    DiscretePillSlider(
        range = range,
        values = values,
        steps = steps,
        modifier =
            Modifier.fillMaxWidth()
                .background(AppColors.primary, CircleShape)
                .border(1.dp, AppColors.primary, CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
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

  // Show the time picker dialog when requested.
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
 * @param modifier Modifier for the button layout
 */
@Composable
fun DeleteSessionBTN(
    sessionViewModel: FirestoreSessionViewModel,
    currentUser: Account,
    discussion: Discussion,
    userIsAdmin: Boolean,
    onback: () -> Unit,
    modifier: Modifier = Modifier
) {
  if (userIsAdmin) {
    OutlinedButton(
        onClick = {
          sessionViewModel.deleteSession(currentUser, discussion)
          onback()
        },
        shape = CircleShape,
        border = BorderStroke(1.5.dp, AppColors.negative),
        modifier = modifier.testTag(SessionTestTags.DELETE_SESSION_BUTTON),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
          Icon(Icons.Default.Delete, contentDescription = "Delete Session")
          Spacer(Modifier.width(8.dp))
          Text("Delete", style = MaterialTheme.typography.bodyMedium)
        }
  }
}
