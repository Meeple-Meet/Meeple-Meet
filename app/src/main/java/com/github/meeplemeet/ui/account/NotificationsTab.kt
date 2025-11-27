package com.github.meeplemeet.ui.account

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.Notification
import com.github.meeplemeet.model.account.NotificationType
import com.github.meeplemeet.model.account.NotificationsViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationsTabTestTags {
  const val HEADER_TITLE = "header_title"
  const val FILTER_CHIP_PREFIX = "filter_chip_"
  const val EMPTY_STATE_TEXT = "empty_state_text"
  const val NOTIFICATION_LIST = "notification_list"
  const val NOTIFICATION_ITEM_PREFIX = "notification_item_"
  const val SHEET_TITLE = "sheet_title"
  const val SHEET_DESCRIPTION = "sheet_description"
  const val SHEET_ACCEPT_BUTTON = "sheet_accept_button"
  const val SHEET_DECLINE_BUTTON = "sheet_decline_button"
  const val UNREAD_DOT_PREFIX = "unread_dot_"
}

object NotificationsTabUi {
  object Header {
    const val TITLE = "Notifications"
    const val BACK_ICON_DESCRIPTION = "Back"
  }

  object NotificationLabels {
    const val FRIEND_REQUEST = "Incoming friend request"
    const val JOIN_DISCUSSION = "Discussion invite"
    const val JOIN_SESSION = "Session invite"
  }

  object DateSections {
    const val TODAY = "Today"
    const val YESTERDAY = "Yesterday"
    const val EARLIER = "Earlier"
    const val DAYS_BEFORE_YESTERDAY = 1L
  }

  object Filters {
    const val PADDING_LIMIT = 24
    const val GRADIENT_ALPHA = 0.7f
  }

  object NotificationCard {
    const val UNREAD_DOT_CORNER_RADIUS = 50
    val AVATAR_CORNER_RADIUS: Dp = 22.dp
    val UNREAD_DOT_SIZE: Dp = 18.dp
    val AVATAR_SIZE: Dp = 44.dp

    object TimeFormats {
      const val TIME_PATTERN = "h:mm a"
      const val DATE_PATTERN = "dd/MM/yyyy"
    }

    object Subtitles {
      const val FRIEND_REQUEST_PREFIX = "User "
      const val FRIEND_REQUEST_SUFFIX = "wants to add you as a friend."
      const val DISCUSSION_PREFIX = "Invited to join discussion "
      const val SESSION_PREFIX = "Invited to join session "
    }

    object Swipe {
      const val MARK_READ_THRESHOLD = 0.10f
      const val DELETE_THRESHOLD = 0.40f
      const val TRIGGERED_SCALE = 1.3f
      const val DEFAULT_SCALE = 1f
    }

    object Fallbacks {
      const val DEFAULT_USER = "User"
      const val DEFAULT_DISCUSSION = "Discussion"
      const val DEFAULT_SESSION = "Session"
      const val FALLBACK_LETTER = '•'
    }
  }

  object NotificationSheet {
    val AVATAR_SIZE: Dp = 52.dp

    object Content {
      const val CLOSE_ICON_DESCRIPTION = "Close"
      const val DISCUSSION_KEYWORD = "discussion"
      const val ABOUT_DISCUSSION = "About this discussion"
      const val ABOUT_SESSION = "About this session"
      const val ABOUT_FRIEND = "About"
      const val NO_DESCRIPTION = "No description provided"
      const val NO_DESCRIPTION_DISCUSSION = "No description provided."
      const val HANDLE_PREFIX = "@"

      const val DATE_PATTERN = "dd/MM/yyyy"
      const val SESSION_DATE_PATTERN = "MMM d"
      const val SESSION_TIME_PATTERN = "h:mm a"

      const val DISCUSSION_PREVIEW_CHAR_LIMIT = 160
      const val SESSION_PREVIEW_CHAR_LIMIT = 160
      const val FRIEND_PREVIEW_CHAR_LIMIT = 150

      fun sessionDescription(gameName: String, time: String, locationName: String) =
          "Play $gameName at $time at $locationName."

      fun participantsMeta(participants: Int, dateLabel: String) =
          "$participants participants • $dateLabel"
    }

    object Buttons {
      const val DECLINE_TEXT = "Decline"
      const val ACCEPT_TEXT = "Accept"
    }
  }

  object ExpandableText {
    const val DEFAULT_PREVIEW_CHAR_LIMIT = 160
    const val MORE_INDICATOR = "…"
    const val SHOW_MORE = "Show more"
    const val SHOW_LESS = "Show less"
  }

  object LetterAvatar {
    const val SIZE_MULTIPLIER = 0.45f
  }

  object EmptyState {
    object Messages {
      const val ALL = "You have no notifications yet."
      const val UNREAD = "You're all caught up! No unread notifications."
      const val FRIEND_REQUESTS = "No incoming friend requests."
      const val DISCUSSIONS = "No discussion invitations."
      const val SESSIONS = "No session invitations."
    }
  }
}

/* ---------------------------------------------------------
   EXTENSIONS + HELPERS
--------------------------------------------------------- */

/** Helper to manage dates and time */
fun Notification.sentAtLocalDateTime(): LocalDateTime =
    sentAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

/** Helper for modular composable */
fun NotificationType.label(): String =
    when (this) {
      NotificationType.FRIEND_REQUEST -> NotificationsTabUi.NotificationLabels.FRIEND_REQUEST
      NotificationType.JOIN_DISCUSSION -> NotificationsTabUi.NotificationLabels.JOIN_DISCUSSION
      NotificationType.JOIN_SESSION -> NotificationsTabUi.NotificationLabels.JOIN_SESSION
    }

/** Helper for organisation of the notifications by date */
data class NotificationSection(val header: String, val items: List<Notification>)

/** Groups notifications together with respect to the time they were sent at */
fun groupNotifications(list: List<Notification>): List<NotificationSection> {
  val today = LocalDate.now()
  val yesterday = today.minusDays(NotificationsTabUi.DateSections.DAYS_BEFORE_YESTERDAY)

  val todayItems = list.filter { it.sentAtLocalDateTime().toLocalDate() == today }
  val yesterdayItems = list.filter { it.sentAtLocalDateTime().toLocalDate() == yesterday }
  val earlierItems =
      list.filter {
        val d = it.sentAtLocalDateTime().toLocalDate()
        d != today && d != yesterday
      }

  val sections = mutableListOf<NotificationSection>()
  if (todayItems.isNotEmpty()) {
    sections.add(NotificationSection(NotificationsTabUi.DateSections.TODAY, todayItems))
  }
  if (yesterdayItems.isNotEmpty()) {
    sections.add(NotificationSection(NotificationsTabUi.DateSections.YESTERDAY, yesterdayItems))
  }
  if (earlierItems.isNotEmpty()) {
    sections.add(NotificationSection(NotificationsTabUi.DateSections.EARLIER, earlierItems))
  }

  return sections
}

/** Data classes for modular popup composables */
sealed class NotificationPopupData {
  data class Discussion(
      val title: String,
      val participants: Int,
      val dateLabel: String,
      val description: String,
      val icon: ByteArray?
  ) : NotificationPopupData()

  data class Session(
      val title: String,
      val participants: Int,
      val dateLabel: String,
      val description: String,
      val icon: ByteArray?
  ) : NotificationPopupData()

  data class FriendRequest(
      val username: String,
      val handle: String,
      val bio: String,
      val avatar: ByteArray?
  ) : NotificationPopupData()
}

/** Helper for modular composables */
data class NotificationSheetState(
    val notification: Notification,
    val popupData: NotificationPopupData
)

/* ---------------------------------------------------------
   MAIN SCREEN
--------------------------------------------------------- */

/**
 * Main screen/tab of this file. It shows the user his sorted notifications, filters and the
 * notifications are interactible
 *
 * @param account The current user
 * @param viewModel VM used by this screen
 * @param onBack callback upon click of the back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTab(
    account: Account,
    viewModel: NotificationsViewModel = viewModel(),
    onBack: () -> Unit
) {
  val filters = NotificationFilter.entries
  var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }

  val notifications: List<Notification> = account.notifications

  val filtered =
      when (selectedFilter) {
        NotificationFilter.ALL -> notifications
        NotificationFilter.UNREAD -> notifications.filter { !it.read }
        NotificationFilter.FRIEND_REQUESTS ->
            notifications.filter { it.type == NotificationType.FRIEND_REQUEST }
        NotificationFilter.DISCUSSIONS ->
            notifications.filter { it.type == NotificationType.JOIN_DISCUSSION }
        NotificationFilter.SESSIONS ->
            notifications.filter { it.type == NotificationType.JOIN_SESSION }
      }.sortedByDescending { n -> n.sentAt }

  val grouped = groupNotifications(filtered)

  var sheetState by remember { mutableStateOf<NotificationSheetState?>(null) }

  if (sheetState != null) {
    val current = sheetState!!
    NotificationSheet(
        notification = current.notification,
        data = current.popupData,
        viewModel = viewModel,
        onDismiss = { sheetState = null },
        onAccept = {
          viewModel.executeNotification(account, current.notification)
          viewModel.readNotification(account, current.notification)
          viewModel.deleteNotification(account, current.notification)
          sheetState = null
        },
        onDecline = {
          viewModel.deleteNotification(account, current.notification)
          sheetState = null
        })
  }

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            colors =
                TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = AppColors.primary,
                    titleContentColor = AppColors.textIcons,
                    navigationIconContentColor = AppColors.textIcons),
            title = {
              Text(
                  text = NotificationsTabUi.Header.TITLE,
                  modifier = Modifier.testTag(NotificationsTabTestTags.HEADER_TITLE))
            },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
                    contentDescription = NotificationsTabUi.Header.BACK_ICON_DESCRIPTION)
              }
            })
      }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
          FilterRow(
              filters = filters, selected = selectedFilter, onSelected = { selectedFilter = it })

          if (filtered.isEmpty()) {
            EmptyNotificationState(filter = selectedFilter)
          } else {
            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(top = Dimensions.Padding.extraLarge)
                        .testTag(NotificationsTabTestTags.NOTIFICATION_LIST)) {
                  grouped.forEachIndexed { index, section ->
                    if (index > 0) {
                      item { Spacer(Modifier.height(Dimensions.Padding.xLarge)) }
                    }

                    item {
                      Text(
                          text = section.header,
                          style =
                              MaterialTheme.typography.titleLarge.copy(
                                  fontSize = Dimensions.TextSize.largeHeading),
                          color = AppColors.textIcons,
                          modifier =
                              Modifier.padding(
                                  horizontal = Dimensions.Padding.extraLarge,
                                  vertical = Dimensions.Padding.medium))
                    }

                    items(section.items, key = { it.uid }) { notif ->
                      val context = LocalContext.current
                      NotificationCard(
                          notif = notif,
                          viewModel = viewModel,
                          onClick = {
                            viewModel.preparePopupData(
                                notif, account = account, context = context) { popupData ->
                                  sheetState = NotificationSheetState(notif, popupData)
                                }
                            viewModel.readNotification(account, notif)
                          },
                          onMarkRead = { viewModel.readNotification(account, notif) },
                          onDelete = { viewModel.deleteNotification(account, notif) })
                    }
                  }
                }
          }
        }
      }
}

/* ---------------------------------------------------------
   FILTERS
--------------------------------------------------------- */

/** Enum for all filters */
enum class NotificationFilter(val label: String) {
  ALL("All"),
  UNREAD("Unread"),
  FRIEND_REQUESTS("Friend requests"),
  DISCUSSIONS("Discussions"),
  SESSIONS("Sessions")
}

/**
 * Composable used to display the row of filters
 *
 * @param filters All available filters
 * @param selected The selected filter
 * @param onSelected Callback upon selection of a filter
 */
@Composable
fun FilterRow(
    filters: List<NotificationFilter>,
    selected: NotificationFilter,
    onSelected: (NotificationFilter) -> Unit,
) {
  val scroll = rememberScrollState()

  val showLeft by remember { derivedStateOf { scroll.value > 0 } }
  val showRight by remember { derivedStateOf { scroll.value < scroll.maxValue } }

  Column(modifier = Modifier.fillMaxWidth()) {
    val padLimit = NotificationsTabUi.Filters.PADDING_LIMIT
    val padStart = minOf(scroll.value, padLimit)
    val padEnd = minOf(scroll.maxValue - scroll.value, padLimit)

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(Dimensions.ContainerSize.iconButtonTouch)
                .padding(start = padStart.dp, end = padEnd.dp)) {
          Row(
              modifier =
                  Modifier.horizontalScroll(scroll)
                      .padding(horizontal = Dimensions.Padding.large)) {
                filters.forEach { filter ->
                  FilterChip(
                      text = filter.label,
                      selected = filter == selected,
                      modifier =
                          Modifier.testTag(
                              NotificationsTabTestTags.FILTER_CHIP_PREFIX + filter.name),
                      onClick = { onSelected(filter) })

                  Spacer(Modifier.width(Dimensions.Padding.extraLarge))
                }
              }

          if (showLeft) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .width(Dimensions.Padding.xxxLarge)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AppColors.primary,
                                AppColors.primary.copy(
                                    alpha = NotificationsTabUi.Filters.GRADIENT_ALPHA),
                                Color.Transparent))))
          }

          if (showRight) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .width(Dimensions.Padding.xxxLarge)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, AppColors.primary))))
          }
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    top = Dimensions.Padding.small,
                    start = Dimensions.Padding.large,
                    end = Dimensions.Padding.large),
        horizontalArrangement = Arrangement.SpaceBetween) {
          if (showLeft) {
            Icon(Icons.Default.ChevronLeft, null, tint = AppColors.textIconsFade)
          } else {
            Spacer(Modifier.size(Dimensions.IconSize.large))
          }

          if (showRight) {
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.textIconsFade)
          } else {
            Spacer(Modifier.size(Dimensions.IconSize.large))
          }
        }
  }
}

/**
 * Filter chips used for filtering
 *
 * @param text Text to display in the chip
 * @param selected Whether the chip is selected or not
 * @param modifier Modifiers to pass to the chip
 * @param onClick Callback upon click on the chip
 */
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
  val border = if (selected) AppColors.neutral else AppColors.textIconsFade

  Box(
      modifier =
          modifier
              .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
              .border(
                  if (selected) Dimensions.DividerThickness.medium
                  else Dimensions.DividerThickness.standard,
                  border,
                  RoundedCornerShape(Dimensions.CornerRadius.extraLarge))
              .clickable { onClick() }
              .padding(
                  horizontal = Dimensions.Padding.extraLarge,
                  vertical = Dimensions.Padding.medium)) {
        Text(text, color = AppColors.textIcons)
      }
}

/* ---------------------------------------------------------
   NOTIFICATION CARD
--------------------------------------------------------- */

/**
 * Notification card, handles everything in relation to the card
 *
 * @param notif Actual notification data
 * @param viewModel VM used by this screen
 * @param onClick Callback upon click on the notification
 * @param onMarkRead Callback upon swiping right
 * @param onDelete Callback upon swiping left
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun NotificationCard(
    notif: Notification,
    viewModel: NotificationsViewModel,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
  val dismissState =
      rememberDismissState(
          confirmStateChange = { state ->
            when (state) {
              DismissValue.DismissedToStart -> {
                onDelete()
                false
              }
              DismissValue.DismissedToEnd -> {
                onMarkRead()
                false
              }
              else -> false
            }
          })

  SwipeToDismiss(
      state = dismissState,
      background = { SwipeBackground(dismissState) },
      dismissContent = { NotificationRowContent(notif, viewModel, onClick) },
      directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
      dismissThresholds = {
        when (it) {
          DismissDirection.StartToEnd ->
              FractionalThreshold(NotificationsTabUi.NotificationCard.Swipe.MARK_READ_THRESHOLD)
          DismissDirection.EndToStart ->
              FractionalThreshold(NotificationsTabUi.NotificationCard.Swipe.DELETE_THRESHOLD)
        }
      })
}

/**
 * Function used to manage the swiping left/right
 *
 * @param state whether the card is currently swiped or not
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeBackground(state: DismissState) {
  val dir = state.dismissDirection ?: return
  val isTriggered = state.targetValue != DismissValue.Default
  val scale by
      animateFloatAsState(
          if (isTriggered) NotificationsTabUi.NotificationCard.Swipe.TRIGGERED_SCALE
          else NotificationsTabUi.NotificationCard.Swipe.DEFAULT_SCALE)

  val color =
      when (dir) {
        DismissDirection.StartToEnd -> AppColors.neutral
        DismissDirection.EndToStart -> AppColors.negative
      }

  Box(
      modifier =
          Modifier.fillMaxSize().background(color).padding(horizontal = Dimensions.Padding.xLarge),
      contentAlignment =
          when (dir) {
            DismissDirection.StartToEnd -> Alignment.CenterStart
            DismissDirection.EndToStart -> Alignment.CenterEnd
          }) {
        val icon =
            when (dir) {
              DismissDirection.StartToEnd -> Icons.Default.MarkChatRead
              DismissDirection.EndToStart -> Icons.Default.Delete
            }

        Icon(icon, null, tint = Color.White, modifier = Modifier.scale(scale))
      }
}

/**
 * Actual content of the notification
 *
 * @param notif Notification data
 * @param viewModel VM used by this screen
 * @param onClick Callback upon click
 */
@Composable
private fun NotificationRowContent(
    notif: Notification,
    viewModel: NotificationsViewModel,
    onClick: () -> Unit
) {
  val context = LocalContext.current

  var avatarBytes by remember(notif.uid) { mutableStateOf<ByteArray?>(null) }
  var friend by remember { mutableStateOf<Account?>(null) }
  var discussionName by remember { mutableStateOf<String?>(null) }
  var sessionName by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(notif.uid) {
    when (notif.type) {
      NotificationType.FRIEND_REQUEST -> {
        viewModel.getOtherAccount(notif.senderOrDiscussionId) { acc ->
          friend = acc
          viewModel.loadAccountImage(notif.senderOrDiscussionId, context) { avatarBytes = it }
        }
      }
      NotificationType.JOIN_DISCUSSION -> {
        viewModel.getDiscussion(
            notif.senderOrDiscussionId,
            onResult = { disc ->
              discussionName = disc.name
              viewModel.loadDiscussionImage(disc.uid, context) { bytes -> avatarBytes = bytes }
            })
      }
      NotificationType.JOIN_SESSION -> {
        viewModel.getDiscussion(
            notif.senderOrDiscussionId,
            onResult = { disc ->
              val session = disc.session
              sessionName = session?.name ?: disc.name
              viewModel.loadDiscussionImage(disc.uid, context) { bytes -> avatarBytes = bytes }
            })
      }
    }
  }

  val dateTime = notif.sentAtLocalDateTime()
  val date = dateTime.toLocalDate()
  val today = LocalDate.now()
  val timeText =
      if (date == today ||
          date == today.minusDays(NotificationsTabUi.DateSections.DAYS_BEFORE_YESTERDAY))
          dateTime.format(
              DateTimeFormatter.ofPattern(
                  NotificationsTabUi.NotificationCard.TimeFormats.TIME_PATTERN))
      else
          dateTime.format(
              DateTimeFormatter.ofPattern(
                  NotificationsTabUi.NotificationCard.TimeFormats.DATE_PATTERN))

  val displayName =
      when (notif.type) {
        NotificationType.FRIEND_REQUEST ->
            friend?.name ?: NotificationsTabUi.NotificationCard.Fallbacks.DEFAULT_USER
        NotificationType.JOIN_DISCUSSION ->
            discussionName ?: NotificationsTabUi.NotificationCard.Fallbacks.DEFAULT_DISCUSSION
        NotificationType.JOIN_SESSION ->
            sessionName ?: NotificationsTabUi.NotificationCard.Fallbacks.DEFAULT_SESSION
      }

  val fallbackLetter =
      displayName.firstOrNull() ?: NotificationsTabUi.NotificationCard.Fallbacks.FALLBACK_LETTER

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .testTag(NotificationsTabTestTags.NOTIFICATION_ITEM_PREFIX + notif.uid)
              .background(AppColors.primary)
              .clickable { onClick() }
              .padding(
                  horizontal = Dimensions.Padding.large,
                  vertical = Dimensions.Padding.extraMedium)) {
        if (!notif.read) {
          Box(
              modifier =
                  Modifier.padding(top = Dimensions.Padding.large)
                      .size(NotificationsTabUi.NotificationCard.UNREAD_DOT_SIZE)
                      .clip(
                          RoundedCornerShape(
                              NotificationsTabUi.NotificationCard.UNREAD_DOT_CORNER_RADIUS))
                      .background(AppColors.negative)
                      .testTag(NotificationsTabTestTags.UNREAD_DOT_PREFIX + notif.uid))
        }

        Spacer(Modifier.width(Dimensions.Spacing.large))

        LetterAvatar(
            letter = fallbackLetter,
            size = NotificationsTabUi.NotificationCard.AVATAR_SIZE,
            modifier =
                Modifier.clip(
                    RoundedCornerShape(NotificationsTabUi.NotificationCard.AVATAR_CORNER_RADIUS)),
            imageBytes = avatarBytes)

        Spacer(Modifier.width(Dimensions.Spacing.large))

        Column(modifier = Modifier.weight(Dimensions.Weight.full)) {
          Text(
              text = notif.type.label(),
              style = MaterialTheme.typography.bodySmall,
              fontSize = Dimensions.TextSize.title,
              color = AppColors.textIcons)

          val subtitle =
              when (notif.type) {
                NotificationType.FRIEND_REQUEST ->
                    buildAnnotatedString {
                      append(NotificationsTabUi.NotificationCard.Subtitles.FRIEND_REQUEST_PREFIX)
                      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$displayName ") }
                      append(NotificationsTabUi.NotificationCard.Subtitles.FRIEND_REQUEST_SUFFIX)
                    }
                NotificationType.JOIN_DISCUSSION ->
                    buildAnnotatedString {
                      append(NotificationsTabUi.NotificationCard.Subtitles.DISCUSSION_PREFIX)
                      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(displayName) }
                    }
                NotificationType.JOIN_SESSION ->
                    buildAnnotatedString {
                      append(NotificationsTabUi.NotificationCard.Subtitles.SESSION_PREFIX)
                      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(displayName) }
                    }
              }

          Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = AppColors.textIconsFade,
              modifier = Modifier.padding(top = Dimensions.Padding.mediumSmall))
        }

        Spacer(Modifier.width(Dimensions.Spacing.small))

        Column(horizontalAlignment = Alignment.End) {
          Text(
              text = timeText,
              color = AppColors.textIconsFade,
              style = MaterialTheme.typography.bodySmall)
        }
      }
}

/* ---------------------------------------------------------
   BOTTOM SHEET
--------------------------------------------------------- */

/**
 * Handles the notification popup
 *
 * @param notification Notification data
 * @param data Popup data
 * @param viewModel VM used by this screen
 * @param onDismiss Callback upon clicking away from the popup
 * @param onAccept Callback upon executing the notification
 * @param onDecline Callback upon declining the notification
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSheet(
    notification: Notification,
    data: NotificationPopupData,
    viewModel: NotificationsViewModel,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
  val context = LocalContext.current

  var avatar by remember { mutableStateOf<ByteArray?>(null) }
  var icon by remember { mutableStateOf<ByteArray?>(null) }
  var loadedData by remember { mutableStateOf(data) }

  LaunchedEffect(notification.uid) {
    when (notification.type) {
      NotificationType.FRIEND_REQUEST -> {
        viewModel.getOtherAccountData(
            notification.senderOrDiscussionId,
            onResult = { acc ->
              viewModel.loadAccountImage(notification.senderOrDiscussionId, context) { bytes ->
                avatar = bytes
              }

              loadedData =
                  NotificationPopupData.FriendRequest(
                      username = acc.name,
                      handle = acc.handle,
                      bio =
                          if (!acc.description.isNullOrBlank()) acc.description!!
                          else NotificationsTabUi.NotificationSheet.Content.NO_DESCRIPTION,
                      avatar = avatar)
            })
      }
      NotificationType.JOIN_DISCUSSION -> {
        viewModel.getDiscussion(
            notification.senderOrDiscussionId,
            onResult = { disc ->
              viewModel.loadDiscussionImage(disc.uid, context) { bytes -> icon = bytes }

              loadedData =
                  NotificationPopupData.Discussion(
                      title = disc.name,
                      participants = disc.participants.size,
                      dateLabel =
                          disc.createdAt
                              .toDate()
                              .toInstant()
                              .atZone(ZoneId.systemDefault())
                              .toLocalDate()
                              .format(
                                  DateTimeFormatter.ofPattern(
                                      NotificationsTabUi.NotificationSheet.Content.DATE_PATTERN)),
                      description =
                          disc.description.ifBlank {
                            NotificationsTabUi.NotificationSheet.Content.NO_DESCRIPTION_DISCUSSION
                          },
                      icon = icon)
            })
      }
      NotificationType.JOIN_SESSION -> {
        viewModel.getDiscussion(
            notification.senderOrDiscussionId,
            onResult = { disc ->
              viewModel.loadDiscussionImage(disc.uid, context) { bytes -> avatar = bytes }

              val session = disc.session
              if (session != null) {
                viewModel.getGame(session.gameId) { game ->
                  val dateTime =
                      session.date
                          .toDate()
                          .toInstant()
                          .atZone(ZoneId.systemDefault())
                          .toLocalDateTime()

                  val dateLabel =
                      dateTime.format(
                          DateTimeFormatter.ofPattern(
                              NotificationsTabUi.NotificationSheet.Content.SESSION_DATE_PATTERN))
                  val timeLabel =
                      dateTime.format(
                          DateTimeFormatter.ofPattern(
                              NotificationsTabUi.NotificationSheet.Content.SESSION_TIME_PATTERN))

                  loadedData =
                      NotificationPopupData.Session(
                          title = session.name,
                          participants = session.participants.size,
                          dateLabel = dateLabel,
                          description =
                              NotificationsTabUi.NotificationSheet.Content.sessionDescription(
                                  game.name, timeLabel, session.location.name),
                          icon = avatar)
                }
              }
            })
      }
    }
  }

  ModalBottomSheet(
      onDismissRequest = onDismiss,
      containerColor = AppColors.primary,
      contentColor = AppColors.textIcons,
      dragHandle = null) {
        when (val d = loadedData) {
          is NotificationPopupData.Discussion -> {
            NotificationSheetContent(
                avatarBytes = d.icon,
                title = d.title,
                subtitle = null,
                meta =
                    NotificationsTabUi.NotificationSheet.Content.participantsMeta(
                        d.participants, d.dateLabel),
                aboutLabel = NotificationsTabUi.NotificationSheet.Content.ABOUT_DISCUSSION,
                description = d.description,
                previewCharLimit =
                    NotificationsTabUi.NotificationSheet.Content.DISCUSSION_PREVIEW_CHAR_LIMIT,
                onAccept = onAccept,
                onDecline = onDecline,
                onClose = onDismiss)
          }
          is NotificationPopupData.Session -> {
            NotificationSheetContent(
                avatarBytes = d.icon,
                title = d.title,
                subtitle = null,
                meta =
                    NotificationsTabUi.NotificationSheet.Content.participantsMeta(
                        d.participants, d.dateLabel),
                aboutLabel = NotificationsTabUi.NotificationSheet.Content.ABOUT_SESSION,
                description = d.description,
                previewCharLimit =
                    NotificationsTabUi.NotificationSheet.Content.SESSION_PREVIEW_CHAR_LIMIT,
                onAccept = onAccept,
                onDecline = onDecline,
                onClose = onDismiss)
          }
          is NotificationPopupData.FriendRequest -> {
            NotificationSheetContent(
                avatarBytes = d.avatar,
                title = d.username,
                subtitle = NotificationsTabUi.NotificationSheet.Content.HANDLE_PREFIX + d.handle,
                meta = null,
                aboutLabel = NotificationsTabUi.NotificationSheet.Content.ABOUT_FRIEND,
                description = d.bio,
                previewCharLimit =
                    NotificationsTabUi.NotificationSheet.Content.FRIEND_PREVIEW_CHAR_LIMIT,
                onAccept = onAccept,
                onDecline = onDecline,
                onClose = onDismiss)
          }
        }
      }
}

/**
 * Buttons at the bottom of the popup sheet. Only reason they're inside their own composable is to
 * make the above composable easier to read
 *
 * @param onDecline Callback upon declining the notification
 * @param onAccept Callback upon executing the notification
 */
@Composable
fun BottomSheetButtons(onDecline: () -> Unit, onAccept: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(
                  horizontal = Dimensions.Padding.extraLarge, vertical = Dimensions.Padding.xLarge),
      horizontalArrangement = Arrangement.SpaceBetween) {
        Button(
            onClick = onDecline,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.negative, contentColor = AppColors.textIcons),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.medium),
            modifier = Modifier.testTag(NotificationsTabTestTags.SHEET_DECLINE_BUTTON),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.Padding.xLarge,
                    vertical = Dimensions.Padding.extraMedium)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, null)
                Text(
                    NotificationsTabUi.NotificationSheet.Buttons.DECLINE_TEXT,
                    modifier = Modifier.padding(Dimensions.Padding.small))
              }
            }

        Button(
            onClick = onAccept,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.affirmative, contentColor = AppColors.textIcons),
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.medium),
            modifier = Modifier.testTag(NotificationsTabTestTags.SHEET_ACCEPT_BUTTON),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.Padding.xLarge,
                    vertical = Dimensions.Padding.extraMedium)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null)
                Text(
                    NotificationsTabUi.NotificationSheet.Buttons.ACCEPT_TEXT,
                    modifier = Modifier.padding(Dimensions.Padding.small))
              }
            }
      }
}

/**
 * Creates the sheet's content and formats everything.
 *
 * @param avatarBytes Used to display profile pictures
 * @param title Title of the sheet
 * @param subtitle Smaller text under the title
 * @param meta Used for dates/handles
 * @param description User/discussion/session description
 * @param previewCharLimit Limit of how many chars to show
 * @param onAccept Callback upon accepting the request
 * @param onDecline Callback upon declining the request
 * @param onClose Callback upon closing the popup
 */
@Composable
fun NotificationSheetContent(
    avatarBytes: ByteArray?,
    title: String,
    subtitle: String?,
    meta: String?,
    aboutLabel: String,
    description: String,
    previewCharLimit: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClose: () -> Unit
) {
  Column(modifier = Modifier.padding(Dimensions.Padding.xLarge)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Icon(
          Icons.Default.Close,
          NotificationsTabUi.NotificationSheet.Content.CLOSE_ICON_DESCRIPTION,
          tint = AppColors.textIcons,
          modifier = Modifier.clickable { onClose() })
    }

    Spacer(Modifier.height(Dimensions.Spacing.small))

    Row(verticalAlignment = Alignment.CenterVertically) {
      LetterAvatar(
          letter =
              title.firstOrNull() ?: NotificationsTabUi.NotificationCard.Fallbacks.FALLBACK_LETTER,
          size = NotificationsTabUi.NotificationSheet.AVATAR_SIZE,
          imageBytes = avatarBytes)

      Spacer(Modifier.width(Dimensions.Spacing.large))

      Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.textIcons,
            modifier = Modifier.testTag(NotificationsTabTestTags.SHEET_TITLE))
        if (subtitle != null) {
          Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.textIcons)
        }
      }
    }

    if (meta != null) {
      Spacer(Modifier.height(Dimensions.Spacing.medium))
      Text(meta, style = MaterialTheme.typography.bodyMedium, color = AppColors.textIconsFade)
    }

    Spacer(Modifier.height(Dimensions.Spacing.extraLarge))

    Text(
        text = aboutLabel,
        style = MaterialTheme.typography.titleMedium,
        color = AppColors.textIcons,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = Dimensions.Padding.mediumSmall))

    ExpandableText(
        text = description,
        previewCharLimit = previewCharLimit,
        modifier = Modifier.testTag(NotificationsTabTestTags.SHEET_DESCRIPTION))

    Spacer(Modifier.height(Dimensions.Spacing.xLarge))

    BottomSheetButtons(onDecline, onAccept)
  }
}

/**
 * Composable to manage expandable text
 *
 * @param text Text that can be too long
 * @param previewCharLimit Limit to how many characters to display
 * @param modifier Modifier applied to this composable
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    previewCharLimit: Int = NotificationsTabUi.ExpandableText.DEFAULT_PREVIEW_CHAR_LIMIT
) {
  var expanded by remember { mutableStateOf(false) }

  Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
    Text(
        if (expanded || text.length <= previewCharLimit) text
        else text.take(previewCharLimit) + NotificationsTabUi.ExpandableText.MORE_INDICATOR,
        color = AppColors.textIcons)

    if (text.length > previewCharLimit) {
      Text(
          text =
              if (expanded) NotificationsTabUi.ExpandableText.SHOW_LESS
              else NotificationsTabUi.ExpandableText.SHOW_MORE,
          color = AppColors.textIcons,
          fontWeight = FontWeight.Bold,
          modifier =
              Modifier.padding(top = Dimensions.Padding.small).clickable { expanded = !expanded })
    }
  }
}

/**
 * Fallback when user/discussion do not have a picture
 *
 * @param letter Letter to use for the fake avatar
 * @param size Size of the avatar bubble
 * @param modifier Modifiers to apply to this screen
 * @param imageBytes Image to display if available
 */
@Composable
fun LetterAvatar(
    letter: Char,
    size: Dp,
    modifier: Modifier = Modifier,
    imageBytes: ByteArray? = null
) {
  if (imageBytes != null) {
    AsyncImage(
        model = imageBytes,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape))
  } else {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(AppColors.neutral),
        contentAlignment = Alignment.Center) {
          Text(
              text = letter.uppercase(),
              color = AppColors.primary,
              fontSize = (size.value * NotificationsTabUi.LetterAvatar.SIZE_MULTIPLIER).sp,
              fontWeight = FontWeight.Bold)
        }
  }
}

/**
 * Handles no notification being available for a filter
 *
 * @param filter Selected filter
 * @param modifier Modifiers to apply to this composable
 */
@Composable
fun EmptyNotificationState(filter: NotificationFilter, modifier: Modifier = Modifier) {
  val message =
      when (filter) {
        NotificationFilter.ALL -> NotificationsTabUi.EmptyState.Messages.ALL
        NotificationFilter.UNREAD -> NotificationsTabUi.EmptyState.Messages.UNREAD
        NotificationFilter.FRIEND_REQUESTS -> NotificationsTabUi.EmptyState.Messages.FRIEND_REQUESTS
        NotificationFilter.DISCUSSIONS -> NotificationsTabUi.EmptyState.Messages.DISCUSSIONS
        NotificationFilter.SESSIONS -> NotificationsTabUi.EmptyState.Messages.SESSIONS
      }

  Column(
      modifier = modifier.fillMaxSize().padding(Dimensions.Padding.xxxLarge),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            color = AppColors.textIconsFade,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(NotificationsTabTestTags.EMPTY_STATE_TEXT))
      }
}
