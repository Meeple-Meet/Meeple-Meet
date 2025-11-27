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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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

/* ---------------------------------------------------------
   EXTENSIONS + HELPERS
--------------------------------------------------------- */

fun Notification.sentAtLocalDateTime(): LocalDateTime =
    sentAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

fun NotificationType.label(): String =
    when (this) {
      NotificationType.FRIEND_REQUEST -> "Incoming friend request"
      NotificationType.JOIN_DISCUSSION -> "Discussion invite"
      NotificationType.JOIN_SESSION -> "Session invite"
    }

data class NotificationSection(val header: String, val items: List<Notification>)

fun groupNotifications(list: List<Notification>): List<NotificationSection> {
  val today = LocalDate.now()
  val yesterday = today.minusDays(1)

  val todayItems = list.filter { it.sentAtLocalDateTime().toLocalDate() == today }
  val yesterdayItems = list.filter { it.sentAtLocalDateTime().toLocalDate() == yesterday }
  val earlierItems =
      list.filter {
        val d = it.sentAtLocalDateTime().toLocalDate()
        d != today && d != yesterday
      }

  val sections = mutableListOf<NotificationSection>()
  if (todayItems.isNotEmpty()) sections.add(NotificationSection("Today", todayItems))
  if (yesterdayItems.isNotEmpty()) sections.add(NotificationSection("Yesterday", yesterdayItems))
  if (earlierItems.isNotEmpty()) sections.add(NotificationSection("Earlier", earlierItems))

  return sections
}

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

data class NotificationSheetState(
    val notification: Notification,
    val popupData: NotificationPopupData
)

// fun Notification.toPopupData(): NotificationPopupData {
//  val dateLabel = sentAtLocalDateTime().format(DateTimeFormatter.ofPattern("MMM d"))
//  return when (type) {
//    NotificationType.FRIEND_REQUEST ->
//        NotificationPopupData.FriendRequest(
//            username = "User $senderOrDiscussionId",
//            handle = senderOrDiscussionId,
//            bio = "User $senderOrDiscussionId wants to add you as a friend.",
//            avatar = null)
//    NotificationType.JOIN_DISCUSSION ->
//        NotificationPopupData.Discussion(
//            title = "Discussion invitation",
//            participants = 0,
//            dateLabel = dateLabel,
//            description = "You've been invited to join discussion $senderOrDiscussionId.",
//            icon = null)
//    NotificationType.JOIN_SESSION ->
//        NotificationPopupData.Session(
//            title = "Session invitation",
//            participants = 0,
//            dateLabel = dateLabel,
//            description = "You've been invited to join session $senderOrDiscussionId.",
//            icon = null)
//  }
// }

/* ---------------------------------------------------------
   MAIN SCREEN
--------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTab(
    account: Account,
    viewModel: NotificationsViewModel = viewModel(),
    onBack: () -> Unit
) {
  val scope = rememberCoroutineScope()

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
          scope.launch {
            current.notification.execute()
            viewModel.readNotification(account, current.notification)
            sheetState = null
          }
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
                  text = "Notifications",
                  modifier = Modifier.testTag(NotificationsTabTestTags.HEADER_TITLE))
            },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
                    contentDescription = "Back")
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
                        .padding(top = 16.dp)
                        .testTag(NotificationsTabTestTags.NOTIFICATION_LIST)) {
                  grouped.forEachIndexed { index, section ->
                    if (index > 0) item { Spacer(Modifier.height(20.dp)) }

                    item {
                      Text(
                          text = section.header,
                          style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                          color = AppColors.textIcons,
                          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }

                    items(section.items, key = { it.uid }) { notif ->
                      val context = LocalContext.current
                      NotificationCard(
                          notif = notif,
                          viewModel = viewModel,
                          onClick = {
                            scope.launch {
                              viewModel.preparePopupData(notif, context) { popupData ->
                                sheetState = NotificationSheetState(notif, popupData)
                              }
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

enum class NotificationFilter(val label: String) {
  ALL("All"),
  UNREAD("Unread"),
  FRIEND_REQUESTS("Friend requests"),
  DISCUSSIONS("Discussions"),
  SESSIONS("Sessions")
}

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
    val padLimit = 24
    val padStart = minOf(scroll.value, padLimit)
    val padEnd = minOf(scroll.maxValue - scroll.value, padLimit)

    Box(
        modifier =
            Modifier.fillMaxWidth().height(40.dp).padding(start = padStart.dp, end = padEnd.dp)) {
          Row(modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 12.dp)) {
            filters.forEach { filter ->
              FilterChip(
                  text = filter.label,
                  selected = filter == selected,
                  modifier =
                      Modifier.testTag(NotificationsTabTestTags.FILTER_CHIP_PREFIX + filter.name),
                  onClick = { onSelected(filter) })

              Spacer(Modifier.width(16.dp))
            }
          }

          if (showLeft) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .width(32.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AppColors.primary,
                                AppColors.primary.copy(alpha = 0.7f),
                                Color.Transparent))))
          }

          if (showRight) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .width(32.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, AppColors.primary))))
          }
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
          if (showLeft) {
            Icon(Icons.Default.ChevronLeft, null, tint = AppColors.textIconsFade)
          } else Spacer(Modifier.size(24.dp))

          if (showRight) {
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.textIconsFade)
          } else Spacer(Modifier.size(24.dp))
        }
  }
}

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
              .clip(RoundedCornerShape(4.dp))
              .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
              .clickable { onClick() }
              .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = AppColors.textIcons)
      }
}

/* ---------------------------------------------------------
   NOTIFICATION CARD
--------------------------------------------------------- */

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
          DismissDirection.StartToEnd -> FractionalThreshold(0.10f)
          DismissDirection.EndToStart -> FractionalThreshold(0.40f)
        }
      })
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeBackground(state: DismissState) {
  val dir = state.dismissDirection ?: return
  val isTriggered = state.targetValue != DismissValue.Default
  val scale by animateFloatAsState(if (isTriggered) 1.3f else 1f)

  val color =
      when (dir) {
        DismissDirection.StartToEnd -> AppColors.neutral
        DismissDirection.EndToStart -> AppColors.negative
      }

  Box(
      modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
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
        viewModel.getDiscussion(notif.senderOrDiscussionId) { disc ->
          discussionName = disc.name
          viewModel.loadDiscussionImage(disc.uid, context) { bytes -> avatarBytes = bytes }
        }
      }
      NotificationType.JOIN_SESSION -> {
        viewModel.getDiscussion(notif.senderOrDiscussionId) { disc ->
          val session = disc.session
          sessionName = session?.name ?: disc.name
          viewModel.loadDiscussionImage(disc.uid, context) { bytes -> avatarBytes = bytes }
        }
      }
    }
  }

  val dateTime = notif.sentAtLocalDateTime()
  val date = dateTime.toLocalDate()
  val today = LocalDate.now()
  val timeText =
      if (date == today || date == today.minusDays(1))
          dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
      else dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  val displayName =
      when (notif.type) {
        NotificationType.FRIEND_REQUEST -> friend?.name ?: "User"
        NotificationType.JOIN_DISCUSSION -> discussionName ?: "Discussion"
        NotificationType.JOIN_SESSION -> sessionName ?: "Session"
      }

  val fallbackLetter = displayName.firstOrNull()?.uppercaseChar() ?: '•'

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .testTag(NotificationsTabTestTags.NOTIFICATION_ITEM_PREFIX + notif.uid)
              .background(AppColors.primary)
              .clickable { onClick() }
              .padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (!notif.read) {
          Box(
              modifier =
                  Modifier.padding(top = 12.dp)
                      .size(18.dp)
                      .clip(RoundedCornerShape(50))
                      .background(AppColors.negative)
                      .testTag(NotificationsTabTestTags.UNREAD_DOT_PREFIX + notif.uid))
        }

        Spacer(Modifier.width(12.dp))

        LetterAvatar(
            letter = fallbackLetter,
            size = 44.dp,
            modifier = Modifier.clip(RoundedCornerShape(22.dp)),
            imageBytes = avatarBytes)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = notif.type.label(),
              style = MaterialTheme.typography.bodySmall,
              fontSize = 17.sp,
              color = AppColors.textIcons)

          val subtitle =
              when (notif.type) {
                NotificationType.FRIEND_REQUEST ->
                    buildAnnotatedString {
                      append("User ")
                      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$displayName ") }
                      append("wants to add you as a friend.")
                    }
                NotificationType.JOIN_DISCUSSION ->
                    buildAnnotatedString {
                      append("Invited to join discussion ")
                      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(displayName) }
                    }
                NotificationType.JOIN_SESSION ->
                    buildAnnotatedString {
                      append("Invited to join session ")
                      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(displayName) }
                    }
              }

          Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = AppColors.textIconsFade,
              modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(Modifier.width(8.dp))

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
        viewModel.getOtherAccount(notification.senderOrDiscussionId) { acc ->
          viewModel.loadAccountImage(notification.senderOrDiscussionId, context) { bytes ->
            avatar = bytes
          }

          loadedData =
              NotificationPopupData.FriendRequest(
                  username = acc.name,
                  handle = acc.handle,
                  bio =
                      if (!acc.description.isNullOrBlank()) acc.description!!
                      else "No description provided",
                  avatar = avatar)
        }
      }
      NotificationType.JOIN_DISCUSSION -> {
        viewModel.getDiscussion(notification.senderOrDiscussionId) { disc ->
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
                                  "dd/MM/yyyy")), //   val dateLabel =
                                                  // sentAtLocalDateTime().format(DateTimeFormatter.ofPattern("MMM d"))
                  description = disc.description.ifBlank { "No description provided." },
                  icon = icon)
        }
      }
      NotificationType.JOIN_SESSION -> {
        viewModel.getDiscussion(notification.senderOrDiscussionId) { disc ->
          viewModel.loadDiscussionImage(disc.uid, context) { bytes -> avatar = bytes }

          val session = disc.session
          if (session != null) {
            viewModel.getGame(session.gameId) { game ->
              val dateTime =
                  session.date.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

              loadedData =
                  NotificationPopupData.Session(
                      title = session.name,
                      participants = session.participants.size,
                      dateLabel = dateTime.format(DateTimeFormatter.ofPattern("MMM d")),
                      description =
                          "Play ${game.name} at ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))} at ${session.location.name}.",
                      icon = avatar)
            }
          }
        }
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
                meta = "${d.participants} participants • ${d.dateLabel}",
                aboutLabel =
                    if (d.title.contains("discussion", ignoreCase = true)) "About this discussion"
                    else "About this session",
                description = d.description,
                previewCharLimit = 160,
                onAccept = onAccept,
                onDecline = onDecline,
                onClose = onDismiss)
          }
          is NotificationPopupData.Session -> {
            NotificationSheetContent(
                avatarBytes = d.icon,
                title = d.title,
                subtitle = null,
                meta = "${d.participants} participants • ${d.dateLabel}",
                aboutLabel =
                    if (d.title.contains("discussion", ignoreCase = true)) "About this discussion"
                    else "About this session",
                description = d.description,
                previewCharLimit = 160,
                onAccept = onAccept,
                onDecline = onDecline,
                onClose = onDismiss)
          }
          is NotificationPopupData.FriendRequest -> {
            NotificationSheetContent(
                avatarBytes = d.avatar,
                title = d.username,
                subtitle = "@${d.handle}",
                meta = null,
                aboutLabel = "About",
                description = d.bio,
                previewCharLimit = 150,
                onAccept = onAccept,
                onDecline = onDecline,
                onClose = onDismiss)
          }
        }
      }
}

@Composable
fun BottomSheetButtons(onDecline: () -> Unit, onAccept: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween) {
        Button(
            onClick = onDecline,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.negative, contentColor = AppColors.textIcons),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.medium),
            modifier = Modifier.testTag(NotificationsTabTestTags.SHEET_DECLINE_BUTTON),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, null)
                Text("Decline", modifier = Modifier.padding(4.dp))
              }
            }

        Button(
            onClick = onAccept,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.affirmative, contentColor = AppColors.textIcons),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.medium),
            modifier = Modifier.testTag(NotificationsTabTestTags.SHEET_ACCEPT_BUTTON),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null)
                Text("Accept", modifier = Modifier.padding(4.dp))
              }
            }
      }
}

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
  Column(modifier = Modifier.padding(20.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Icon(
          Icons.Default.Close,
          "Close",
          tint = AppColors.textIcons,
          modifier = Modifier.clickable { onClose() })
    }

    Spacer(Modifier.height(4.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      LetterAvatar(letter = title.firstOrNull() ?: '•', size = 52.dp, imageBytes = avatarBytes)

      Spacer(Modifier.width(12.dp))

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
      Spacer(Modifier.height(8.dp))
      Text(meta, style = MaterialTheme.typography.bodyMedium, color = AppColors.textIconsFade)
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = aboutLabel,
        style = MaterialTheme.typography.titleMedium,
        color = AppColors.textIcons,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp))

    ExpandableText(
        text = description,
        previewCharLimit = previewCharLimit,
        modifier = Modifier.testTag(NotificationsTabTestTags.SHEET_DESCRIPTION))

    Spacer(Modifier.height(20.dp))

    BottomSheetButtons(onDecline, onAccept)
  }
}

/* ---------------------------------------------------------
   EXPANDABLE TEXT / AVATAR / EMPTY STATE
--------------------------------------------------------- */

@Composable
fun ExpandableText(text: String, previewCharLimit: Int = 160, modifier: Modifier = Modifier) {
  var expanded by remember { mutableStateOf(false) }

  Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
    Text(
        if (expanded || text.length <= previewCharLimit) text
        else text.take(previewCharLimit) + "…",
        color = AppColors.textIcons)

    if (text.length > previewCharLimit) {
      Text(
          text = if (expanded) "Show less" else "Show more",
          color = AppColors.textIcons,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded })
    }
  }
}

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
              fontSize = (size.value * 0.45).sp,
              fontWeight = FontWeight.Bold)
        }
  }
}

@Composable
fun EmptyNotificationState(filter: NotificationFilter, modifier: Modifier = Modifier) {
  val message =
      when (filter) {
        NotificationFilter.ALL -> "You have no notifications yet."
        NotificationFilter.UNREAD -> "You're all caught up! No unread notifications."
        NotificationFilter.FRIEND_REQUESTS -> "No incoming friend requests."
        NotificationFilter.DISCUSSIONS -> "No discussion invitations."
        NotificationFilter.SESSIONS -> "No session invitations."
      }

  Column(
      modifier = modifier.fillMaxSize().padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            color = AppColors.textIconsFade,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(NotificationsTabTestTags.EMPTY_STATE_TEXT))
      }
}
