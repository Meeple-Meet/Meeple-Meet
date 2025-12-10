/** Documentation was generated using ChatGPT. */
package com.github.meeplemeet.ui.sessions

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.sessions.SessionOverviewViewModel
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.MessagingColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

const val NO_SESSIONS_DEFAULT_TEXT = "No sessions yet"
const val NO_SESSIONS_HISTORY_TEXT = "No past sessions yet"
const val AMOUNT_OF_PICTURES_PER_ROW = 3
const val TITLE_MAX_LINES = 1

object SessionsOverviewScreenTestTags {
  const val TEST_TAG_ARCHIVE_BUTTON = "archiveButton"
  const val TEST_TAG_NEXT_SESSIONS = "nextSessionsToggle"
  const val TEST_TAG_HISTORY = "historyToggle"
}
/**
 * Main screen that lists gaming sessions for the logged-in user.
 * - Collects a real-time list of sessions via [SessionOverviewViewModel.sessionMapFlow].
 * - Offers a toggle between “Next sessions” (chronological list) and “History” (WIP placeholder).
 * - Emits [onSelectSession] when the user taps a card.
 *
 * @param viewModel Source of truth for sessions – injected by default.
 * @param navigation Global navigator supplied by the caller.
 * @param account Currently signed-in user; UID is used to load personal sessions.
 * @param onSelectSession Callback that receives the [Session] the user tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsOverviewScreen(
    viewModel: SessionOverviewViewModel = viewModel(),
    navigation: NavigationActions,
    account: Account?,
    onSelectSession: (String) -> Unit = {}
) {
  val context = LocalContext.current
  val sessionMap by
      viewModel.sessionMapFlow(account?.uid ?: "", context).collectAsState(initial = emptyMap())

  /* --------------  NEW: toggle state  -------------- */
  var showHistory by remember { mutableStateOf(false) }
  var popupSession by remember { mutableStateOf<Session?>(null) }
  var archivedSessions by remember { mutableStateOf<List<Session>>(emptyList()) }

  // Refresh counter to force re-fetching when switching to history tab
  var historyRefreshTrigger by remember { mutableStateOf(0) }

  // Fetch archived sessions when history tab is active
  // Refresh whenever showHistory becomes true (by using historyRefreshTrigger)
  LaunchedEffect(showHistory, account, historyRefreshTrigger) {
    if (showHistory && account != null) {
      viewModel.getArchivedSessions(account.uid) { sessions -> archivedSessions = sessions }
    }
  }

  // Increment refresh trigger when history tab is opened
  LaunchedEffect(showHistory) {
    if (showHistory) {
      historyRefreshTrigger++
    }
  }

  var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
  DisposableEffect(Unit) {
    val timer = Timer()
    val now = System.currentTimeMillis()
    val delay = 60000L - (now % 60000L)
    timer.schedule(
        object : TimerTask() {
          override fun run() {
            currentTime = System.currentTimeMillis()
          }
        },
        delay,
        60000L)
    onDispose { timer.cancel() }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
          Column(Modifier.fillMaxWidth()) {
            /* 1. original top-bar (kept) */
            CenterAlignedTopAppBar(
                title = {
                  Text(
                      text = MeepleMeetScreen.SessionsOverview.title,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onPrimary,
                      modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
                })

            SessionToggle(
                showHistory = showHistory,
                onNext = { showHistory = false },
                onHistory = { showHistory = true })
          }
        },
        bottomBar = {
          BottomNavigationMenu(
              currentScreen = MeepleMeetScreen.SessionsOverview,
              onTabSelected = { navigation.navigateTo(it) })
        }) { innerPadding ->
          Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
              showHistory -> {
                if (archivedSessions.isEmpty()) {
                  EmptySessionsListText(isHistory = true)
                } else {
                  HistoryGrid(sessions = archivedSessions, onSessionClick = { popupSession = it })
                }
              }
              sessionMap.isEmpty() -> EmptySessionsListText(isHistory = false)
              else -> {
                // Show all active sessions (future + past < 24h)
                val activeSessions =
                    sessionMap.toList().sortedBy { (_, session) -> session.date.toDate().time }

                if (activeSessions.isEmpty()) {
                  EmptySessionsListText(isHistory = false)
                } else {
                  /* ----------------  NEXT SESSIONS (existing list)  ---------------- */
                  LazyColumn(
                      modifier = Modifier.fillMaxSize(),
                      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.none)) {
                        items(activeSessions, key = { it.first }) { (id, session) ->
                          SessionCard(
                              id = id,
                              session = session,
                              viewModel = viewModel,
                              currentUserId = account?.uid ?: "",
                              currentTime = currentTime,
                              modifier = Modifier.fillMaxWidth().testTag("sessionCard_$id"),
                              onClick = { onSelectSession(id) })
                        }
                      }
                }
              }
            }
          }
        }
  }
  if (popupSession != null) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                .clickable(
                    enabled = true,
                    indication = null,
                    interactionSource =
                        remember {
                          androidx.compose.foundation.interaction.MutableInteractionSource()
                        }) {
                      popupSession = null
                    })
  }

  popupSession?.let { session ->
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      com.github.meeplemeet.ui.components.SessionDetailsCard(
          session = session,
          viewModel = viewModel,
          onClose = { popupSession = null },
          modifier = Modifier.wrapContentSize().padding(Dimensions.Padding.extraLarge))
    }
  }
}

/** Displays a centred label when the user has no upcoming sessions. */
@Composable
private fun EmptySessionsListText(isHistory: Boolean = true) {
  Box(
      modifier = Modifier.fillMaxSize().padding(Dimensions.Spacing.xxxLarge),
      contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
              Icon(
                  imageVector = Icons.AutoMirrored.Filled.Article,
                  contentDescription = null,
                  modifier = Modifier.size(Dimensions.IconSize.giant),
                  tint = MessagingColors.secondaryText)
              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
              Text(
                  text = if (isHistory) NO_SESSIONS_HISTORY_TEXT else NO_SESSIONS_DEFAULT_TEXT,
                  style = MaterialTheme.typography.bodyLarge,
                  fontSize = Dimensions.TextSize.title,
                  color = MessagingColors.secondaryText)
            }
      }
}

/**
 * Visual representation of a single session (Reddit-style card).
 *
 * Layout (top → bottom):
 * - Session title
 * - Game name (resolved asynchronously)
 * - Participants: “Alice, Bob and 3 more” (resolved asynchronously)
 * - Location • date
 *
 * @param session Domain object to render.
 * @param viewModel Used to resolve participant names and game title.
 * @param modifier Optional [Modifier].
 * @param onClick Invoked when the card is tapped.
 */
@Composable
fun SessionOverCard(
    modifier: Modifier = Modifier,
    session: Session,
    gameName: String,
    participantText: String,
    date: String,
    currentTime: Long = System.currentTimeMillis(),
    onClick: () -> Unit = {}
) {
  val timeToArchive =
      remember(session.date, currentTime) {
        val archiveTime = session.date.toDate().time + 24 * 60 * 60 * 1000L
        val diff = archiveTime - currentTime
        if (diff > 0) {
          if (diff <= 24 * 60 * 60 * 1000L) {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            "Automatically archives in " +
                if (hours > 0) "$hours h $minutes min" else "$minutes min"
          } else {
            ""
          }
        } else {
          "Archiving..."
        }
      }

  Column(modifier = modifier.clickable(onClick = onClick).background(AppColors.primary)) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = Dimensions.Spacing.large, vertical = Dimensions.Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large)) {
          Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = Dimensions.TextSize.largeHeading,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.textIcons,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = Dimensions.TextSize.subtitle,
                    color = AppColors.textIcons,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)

                Text(
                    text = participantText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = Dimensions.TextSize.body,
                    color = AppColors.textIconsFade,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
                      if (session.location.name.isNotBlank()) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null)
                        Text(
                            text = session.location.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = Dimensions.TextSize.small,
                            color = AppColors.textIconsFade,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Text(
                            text = "•",
                            fontSize = Dimensions.TextSize.small,
                            color = AppColors.textIconsFade)
                      }
                      Text(
                          text = date,
                          style = MaterialTheme.typography.bodySmall,
                          fontSize = Dimensions.TextSize.small,
                          color = AppColors.textIconsFade)
                    }
              }
        }
    // Time to archive text
    if (timeToArchive.isNotBlank()) {
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .padding(end = Dimensions.Spacing.large, bottom = Dimensions.Spacing.small),
          horizontalArrangement = Arrangement.End) {
            Text(
                text = timeToArchive,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textIconsFade)
          }
    }

    HorizontalDivider(color = AppColors.divider, thickness = Dimensions.DividerThickness.standard)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    modifier: Modifier = Modifier,
    id: String,
    session: Session,
    viewModel: SessionOverviewViewModel,
    currentUserId: String,
    currentTime: Long = System.currentTimeMillis(),
    onClick: () -> Unit = {}
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  // Check if user is admin
  val isAdmin by
      produceState(initialValue = false, key1 = id, key2 = currentUserId) {
        if (currentUserId.isNotBlank()) {
          value = viewModel.isAdmin(id, currentUserId)
        }
      }

  // Check if session is within 2 hours or has passed
  val canArchive =
      remember(session.date, currentTime) {
        val sessionTime = session.date.toDate().time
        val twoHoursBeforeSession = sessionTime - (2 * 60 * 60 * 1000L)
        currentTime >= twoHoursBeforeSession
      }

  // State for confirmation dialog
  var showArchiveConfirmation by remember { mutableStateOf(false) }

  val actionWidth = Dimensions.ComponentWidth.spaceLabelWidth
  val actionWidthPx = with(density) { actionWidth.toPx() }
  val offsetX = remember { Animatable(0f) }

  Box(modifier = modifier.height(IntrinsicSize.Min)) {
    // Background (Archive Button)
    if (isAdmin && canArchive) {
      Box(
          modifier =
              Modifier.align(Alignment.CenterEnd)
                  .width(actionWidth)
                  .fillMaxHeight()
                  .background(AppColors.neutral)
                  .clickable {
                    // Check if session has a photo
                    if (!session.photoUrl.isNullOrBlank()) {
                      viewModel.archiveSession(context, id)
                      scope.launch { offsetX.animateTo(0f) }
                    } else {
                      showArchiveConfirmation = true
                    }
                  }
                  .testTag(SessionsOverviewScreenTestTags.TEST_TAG_ARCHIVE_BUTTON),
          contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Archive,
                contentDescription = "Archive",
                tint = AppColors.primary,
                modifier = Modifier.size(Dimensions.IconSize.xxLarge))
          }
    }

    // Foreground (Card)
    Box(
        modifier =
            Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    enabled = isAdmin && canArchive,
                    orientation = Orientation.Horizontal,
                    state =
                        rememberDraggableState { delta ->
                          scope.launch {
                            val newOffset = (offsetX.value + delta).coerceIn(-actionWidthPx, 0f)
                            offsetX.snapTo(newOffset)
                          }
                        },
                    onDragStopped = {
                      val targetOffset =
                          if (offsetX.value < -actionWidthPx / 2) -actionWidthPx else 0f
                      scope.launch { offsetX.animateTo(targetOffset) }
                    })) {
          val date =
              remember(session.date) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(session.date.toDate())
              }

          /* ---------  resolve names via the callback  --------- */
          val names = remember { mutableStateListOf<String>() }

          LaunchedEffect(session.participants) {
            names.clear()
            viewModel.getAccounts(session.participants.toSet().toList(), context) {
              it.forEach { account -> names += account?.name ?: "Unknown" }
            }
          }
          /* ----------------------------------------------------- */

          val participantText =
              when {
                names.size < session.participants.size -> "Loading…"
                names.isEmpty() -> "No participants"
                names.size == 1 -> names.first()
                names.size == 2 -> names.joinToString(", ")
                else -> {
                  val firstTwo = names.take(2).joinToString(", ")
                  "$firstTwo and ${names.size - 2} more"
                }
              }

          val gameName = session.gameName.takeIf { it.isNotBlank() } ?: "No game selected"

          SessionOverCard(
              session = session,
              gameName = gameName,
              participantText = participantText,
              date = date,
              currentTime = currentTime,
              modifier = Modifier.fillMaxWidth(),
              onClick = onClick)
        }
  }

  // Confirmation dialog for archiving without image
  if (showArchiveConfirmation) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { showArchiveConfirmation = false },
        title = { Text("Archive Session") },
        text = { Text("This session doesn't have an image. Are you sure you want to archive it?") },
        confirmButton = {
          androidx.compose.material3.TextButton(
              onClick = {
                viewModel.archiveSession(context, id)
                scope.launch { offsetX.animateTo(0f) }
                showArchiveConfirmation = false
              }) {
                Text("Archive")
              }
        },
        dismissButton = {
          androidx.compose.material3.TextButton(onClick = { showArchiveConfirmation = false }) {
            Text("Cancel")
          }
        })
  }
}

/**
 * Two-state toggle bar placed directly under the top app bar.
 *
 * The bar is split exactly 50/50:
 * - Left half → “Next sessions”
 * - Right half → “History”
 *
 * The active half uses [AppColors.primary] background and contrasting text.
 *
 * @param showHistory Which half is currently active.
 * @param onNext Called when the left (Next) half is clicked.
 * @param onHistory Called when the right (History) half is clicked.
 */
@Composable
private fun SessionToggle(onNext: () -> Unit, onHistory: () -> Unit, showHistory: Boolean) {
  Row(modifier = Modifier.fillMaxWidth().height(Dimensions.Spacing.xxxLarge)) {
    /* ----  LEFT HALF – Next Sessions  ---- */
    Box(
        modifier =
            Modifier.weight(1f)
                .fillMaxHeight()
                .background(if (!showHistory) AppColors.primary else AppColors.divider)
                .clickable { onNext() }
                .testTag(SessionsOverviewScreenTestTags.TEST_TAG_NEXT_SESSIONS),
        contentAlignment = Alignment.Center) {
          Text(
              text = "Next sessions",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Normal,
              color = if (!showHistory) AppColors.textIcons else AppColors.textIconsFade)
        }

    /* ----  RIGHT HALF – History  ---- */
    Box(
        modifier =
            Modifier.weight(1f)
                .fillMaxHeight()
                .background(if (showHistory) AppColors.primary else AppColors.divider)
                .clickable { onHistory() }
                .testTag(SessionsOverviewScreenTestTags.TEST_TAG_HISTORY),
        contentAlignment = Alignment.Center) {
          Text(
              text = "History",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Normal,
              color = if (showHistory) AppColors.textIcons else AppColors.textIconsFade)
        }
  }
  HorizontalDivider(color = AppColors.divider, thickness = Dimensions.DividerThickness.standard)
}

/**
 * Displays a scrollable grid of past game sessions, formatted as a gallery.
 *
 * The grid arranges the provided [sessions] into rows where each row contains exactly
 * [AMOUNT_OF_PICTURES_PER_ROW] cards. If the last row has fewer sessions, empty spacers are added
 * to preserve alignment.
 *
 * Each session is rendered using [HistoryCard], and the whole layout is wrapped inside a vertically
 * scrollable [LazyColumn]. Horizontal spacing and padding are applied to maintain consistent visual
 * structure.
 *
 * @param sessions The list of past sessions to be displayed in the history gallery.
 */
@Composable
private fun HistoryGrid(sessions: List<Session>, onSessionClick: (Session) -> Unit) {
  LazyColumn(modifier = Modifier.fillMaxSize().padding(Dimensions.Spacing.medium)) {
    items(sessions.chunked(AMOUNT_OF_PICTURES_PER_ROW)) { row ->
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
            row.forEach { session ->
              HistoryCard(
                  session = session,
                  modifier =
                      Modifier.weight(Dimensions.Weight.full)
                          .clickable { onSessionClick(session) }
                          .testTag("historyCard_${session.gameId}"))
            }

            repeat(AMOUNT_OF_PICTURES_PER_ROW - row.size) {
              Spacer(modifier = Modifier.weight(Dimensions.Weight.full))
            }
          }

      Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
    }
  }
}

/**
 * Renders an individual history entry showing a session preview image and its title beneath it.
 *
 * The card includes:
 * - A subtle border around the entire component.
 * - A selfie-style portrait image using an aspect ratio defined by
 *   [Dimensions.Fractions.topBarDivider].
 * - Rounded corners and a unified background.
 * - Centered text for the session title, truncated when necessary.
 *
 * This card is intended to be used inside a grid layout such as [HistoryGrid].
 *
 * @param session The session data used to render the card's image and title.
 * @param modifier Optional [Modifier] to adjust layout behavior when placed in a grid.
 */
@Composable
private fun HistoryCard(session: Session, modifier: Modifier = Modifier) {
  val url = getSessionPicture(session)

  Column(
      modifier =
          modifier
              .border(
                  width = Dimensions.DividerThickness.standard,
                  color = AppColors.divider,
                  shape = MaterialTheme.shapes.medium)
              .clip(MaterialTheme.shapes.medium)
              .background(AppColors.secondary)
              .padding(Dimensions.Spacing.small),
      horizontalAlignment = Alignment.CenterHorizontally) {
        val selfieRatio = Dimensions.Fractions.topBarDivider

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(selfieRatio)
                    .clip(MaterialTheme.shapes.medium)) {
              if (url != null) {
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = session.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
              } else {
                Box(
                    modifier =
                        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = Alignment.Center) {
                      Icon(
                          imageVector = Icons.Default.SentimentDissatisfied,
                          contentDescription = null,
                          tint = androidx.compose.ui.graphics.Color.Gray,
                          modifier = Modifier.size(Dimensions.IconSize.large))
                    }
              }
            }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

        Text(
            text = session.name,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.textIcons,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterHorizontally))
      }
}

/**
 * Returns the URL of the image representing the given session.
 *
 * If the session has a photoUrl, it is returned. Otherwise, null is returned.
 *
 * @param session The session to get the picture for.
 * @return The URL of the session image or null.
 */
private fun getSessionPicture(session: Session): String? {
  return session.photoUrl?.takeIf { it.isNotBlank() }
}
