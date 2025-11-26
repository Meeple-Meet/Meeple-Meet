/** Documentation was generated using ChatGPT. */
package com.github.meeplemeet.ui.sessions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

const val NO_SESSIONS_DEFAULT_TEXT = "No sessions yet"
const val NO_SESSIONS_HISTORY_TEXT = "No past sessions yet"
const val AMOUNT_OF_PICTURES_PER_ROW = 3
const val TITLE_MAX_LINES = 1
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
  val sessionMap by
      viewModel.sessionMapFlow(account?.uid ?: "").collectAsState(initial = emptyMap())

  /* --------------  NEW: toggle state  -------------- */
  var showHistory by remember { mutableStateOf(false) }

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

          SessionToggle( // <-- use the same variable
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
              val now = System.currentTimeMillis()

              // Separate past sessions
              val pastSessions =
                  sessionMap
                      .filter { (_, session) -> session.date.toDate().time < now }
                      .values
                      .sortedByDescending { it.date.toDate().time }

              if (pastSessions.isEmpty()) {
                EmptySessionsListText(isHistory = true)
              } else {
                HistoryGrid(sessions = pastSessions)
              }
            }
            sessionMap.isEmpty() -> EmptySessionsListText(isHistory = false)
            else -> {
              /* ----------------  NEXT SESSIONS (existing list)  ---------------- */
              LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.none)) {
                    items(sessionMap.entries.toList(), key = { it.key }) { (id, session) ->
                      SessionCard(
                          session = session,
                          viewModel = viewModel,
                          modifier = Modifier.fillMaxWidth().testTag("sessionCard_$id"),
                          onClick = { onSelectSession(id) })
                    }
                  }
            }
          }
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
public fun SessionOverCard(
    session: Session,
    gameName: String,
    participantText: String,
    date: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
  Column(modifier = modifier) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .background(AppColors.primary)
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
                      Text(
                          text = date,
                          style = MaterialTheme.typography.bodySmall,
                          fontSize = Dimensions.TextSize.small,
                          color = AppColors.textIconsFade)
                    }
              }
        }

    HorizontalDivider(color = AppColors.divider, thickness = Dimensions.DividerThickness.standard)
  }
}

@Composable
public fun SessionCard(
    session: Session,
    viewModel: SessionOverviewViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
  val date =
      remember(session.date) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(session.date.toDate())
      }

  /* ---------  resolve names via the callback  --------- */
  val names = remember { mutableStateListOf<String>() }

  LaunchedEffect(session.participants) {
    names.clear()
    session.participants.forEach { id ->
      if (id.isBlank()) {
        names += "Unknown"
      } else {
        viewModel.getOtherAccount(id) { acc ->
          names += acc.name // re-composition happens on each addition
        }
      }
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

  val gameName by
      produceState(
          key1 = session.gameId, initialValue = session.gameId // fallback: show id while loading
          ) {
            val name =
                if (session.gameId == LABEL_UNKNOWN_GAME) null
                else viewModel.getGameNameByGameId(session.gameId)
            value = name ?: "No game selected" // suspend call
      }

  SessionOverCard(
      session = session,
      gameName = gameName,
      participantText = participantText,
      date = date,
      modifier = modifier,
      onClick = onClick)
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
                .clickable { onNext() },
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
                .clickable { onHistory() },
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
private fun HistoryGrid(sessions: List<Session>) {
  LazyColumn(modifier = Modifier.fillMaxSize().padding(Dimensions.Spacing.medium)) {
    items(sessions.chunked(AMOUNT_OF_PICTURES_PER_ROW)) { row ->
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
            row.forEach { session ->
              HistoryCard(session = session, modifier = Modifier.weight(Dimensions.Weight.full))
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
      horizontalAlignment = Alignment.CenterHorizontally // CENTER TEXT
      ) {
        // Selfie-style portrait ratio
        val selfieRatio = Dimensions.Fractions.topBarDivider

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(selfieRatio)
                    .clip(MaterialTheme.shapes.medium)) {
              Image(
                  painter = rememberAsyncImagePainter(url),
                  contentDescription = session.name,
                  modifier = Modifier.fillMaxSize(),
                  contentScale = ContentScale.Crop)
            }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

        Text(
            text = session.name,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.textIcons,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterHorizontally) // ENSURE CENTERING
            )
      }
}

/**
 * Returns the URL of the image representing the given [session].
 *
 * This function acts as a placeholder until the real session or game image retrieval logic is
 * implemented. All history cards currently use this placeholder image.
 *
 * @param session The session for which an image should be resolved.
 * @return A placeholder URL pointing to a temporary session image.
 */
private fun getSessionPicture(session: Session): String {
  // TODO: Replace with real game/session image
  return "https://npr.brightspotcdn.com/dims4/default/389845d/2147483647/strip/true/crop/4373x3279+0+0/resize/880x660!/quality/90/?url=http%3A%2F%2Fnpr-brightspot.s3.amazonaws.com%2Flegacy%2Fimages%2Fnews%2Fnpr%2F2020%2F07%2F887305543_2064699070.jpg"
}
