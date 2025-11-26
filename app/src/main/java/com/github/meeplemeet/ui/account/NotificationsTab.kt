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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.R
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.Notification
import com.github.meeplemeet.model.account.NotificationType
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/* ---------------------------------------------------------
   UI Test Tags
--------------------------------------------------------- */

object NotificationsTabTestTags {
  const val HEADER_TITLE = "header_title"
}

/* ---------------------------------------------------------
   MESSAGE BUILDING (prefix + variable + suffix)
--------------------------------------------------------- */

data class NotificationParts(val prefix: String, val bold: String, val suffix: String)

/**
 * Very simple text parts based on backend NotificationType and IDs. Can be enriched later once we
 * have display names from backend.
 */
fun Notification.parts(): NotificationParts {
  return when (type) {
    NotificationType.FRIEND_REQUEST ->
        NotificationParts(
            prefix = "User ", bold = senderOrDiscussionId, suffix = " sent you a friend request.")
    NotificationType.JOIN_DISCUSSION ->
        NotificationParts(
            prefix = "You've been invited to join discussion ",
            bold = senderOrDiscussionId,
            suffix = ".")
    NotificationType.JOIN_SESSION ->
        NotificationParts(
            prefix = "You've been invited to join session ",
            bold = senderOrDiscussionId,
            suffix = ".")
  }
}

/* ---------------------------------------------------------
   GROUPING: Today / Yesterday / Earlier
--------------------------------------------------------- */

data class NotificationSection(val header: String, val items: List<Notification>)

private fun Notification.sentAtLocalDateTime(): LocalDateTime =
    sentAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

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

/* ---------------------------------------------------------
   POPUP DATA
--------------------------------------------------------- */

sealed class NotificationPopupData {
  data class Discussion(
      val title: String,
      val participants: Int,
      val dateLabel: String,
      val description: String,
      val icon: Painter
  ) : NotificationPopupData()

  data class Session(
      val title: String,
      val participants: Int,
      val dateLabel: String,
      val description: String,
      val icon: Painter
  ) : NotificationPopupData()

  data class FriendRequest(
      val username: String,
      val handle: String,
      val bio: String,
      val avatar: Painter
  ) : NotificationPopupData()
}

/** Wrapper to keep popup UI data + backing backend notification together. */
data class NotificationSheetState(
    val notification: Notification,
    val popupData: NotificationPopupData
)

/* ---------------------------------------------------------
   MAIN SCREEN
--------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTab(
    account: Account,
    viewModel: ProfileScreenViewModel = viewModel(),
    onBack: () -> Unit
) {
  val filters = NotificationFilter.entries
  var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }

  // Backend notifications
  val notifications: List<Notification> =
      account.notifications.ifEmpty { stubBackendNotifications(account.uid) }

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
      }

  val grouped = groupNotifications(filtered)

  var sheetState by remember { mutableStateOf<NotificationSheetState?>(null) }

  if (sheetState != null) {
    val current = sheetState!!
    NotificationSheet(
        data = current.popupData,
        onDismiss = { sheetState = null },
        onAccept = {
          when (current.notification.type) {
            NotificationType.FRIEND_REQUEST -> {
              viewModel.acceptFriendRequest(account, current.notification)
            }
            NotificationType.JOIN_DISCUSSION,
            NotificationType.JOIN_SESSION -> {
              // TODO: expose a public executeNotification(account, notification) in the ViewModel
              // and call it here instead of just marking as read.
              viewModel.readNotification(account, current.notification)
            }
          }
          sheetState = null
        },
        onDecline = {
          // Decline == delete notification for now
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

          LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            grouped.forEachIndexed { index, section ->
              if (index > 0) {
                item { Spacer(Modifier.height(20.dp)) }
              }

              item {
                Text(
                    text = section.header,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = AppColors.textIcons,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
              }

              items(section.items, key = { it.uid }) { notif ->
                val icon = painterResource(R.drawable.google_logo)
                NotificationListItem(
                    notif = notif,
                    onClick = {
                      sheetState =
                          NotificationSheetState(
                              notification = notif, popupData = notif.toPopupData(icon))
                    },
                    onMarkRead = { viewModel.readNotification(account, notif) },
                    onDelete = { viewModel.deleteNotification(account, notif) })
              }
            }
          }
        }
      }
}

/* ---------------------------------------------------------
   FILTER ROW + CHIP
--------------------------------------------------------- */

@Composable
fun FilterRow(
    filters: List<NotificationFilter>,
    selected: NotificationFilter,
    onSelected: (NotificationFilter) -> Unit,
) {
  val scrollState = rememberScrollState()

  val showLeftFade by remember { derivedStateOf { scrollState.value > 0 } }
  val showRightFade by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

  Column(modifier = Modifier.fillMaxWidth()) {
    val padLimit = 24
    val dynamicPaddingStart = if (scrollState.value <= padLimit) scrollState.value else padLimit
    val dynamicPaddingEnd =
        if (scrollState.maxValue - scrollState.value <= padLimit)
            (scrollState.maxValue - scrollState.value)
        else padLimit

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(40.dp)
                .padding(start = dynamicPaddingStart.dp, end = dynamicPaddingEnd.dp)) {

          // === SCROLLABLE CHIP ROW ===
          Row(modifier = Modifier.horizontalScroll(scrollState).padding(horizontal = 12.dp)) {
            filters.forEach { filter ->
              val isSelected = filter == selected

              FilterChip(
                  text = filter.label, selected = isSelected, onClick = { onSelected(filter) })

              Spacer(Modifier.width(16.dp))
            }
          }

          // === LEFT FADE ===
          if (showLeftFade) {
            Box(
                modifier =
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

          // === RIGHT FADE ===
          if (showRightFade) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .width(32.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(Color.Transparent, AppColors.primary))))
          }
        }

    // === ARROWS BELOW CHIPS (visual cue only) ===
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
          if (showLeftFade) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = AppColors.textIconsFade)
          } else {
            Spacer(Modifier.size(24.dp))
          }

          if (showRightFade) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColors.textIconsFade)
          } else {
            Spacer(Modifier.size(24.dp))
          }
        }
  }
}

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
  val border = if (selected) AppColors.neutral else AppColors.textIconsFade

  Box(
      modifier =
          Modifier.clip(RoundedCornerShape(4.dp))
              .background(Color.Transparent)
              .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
              .clickable { onClick() }
              .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = text, color = AppColors.textIcons)
      }
}

enum class NotificationFilter(val label: String) {
  ALL("All"),
  UNREAD("Unread"),
  FRIEND_REQUESTS("Friend requests"),
  DISCUSSIONS("Discussions"),
  SESSIONS("Sessions")
}

/* ---------------------------------------------------------
   LIST ITEM (CARD + DIVIDER)
--------------------------------------------------------- */

@Composable
fun NotificationListItem(
    notif: Notification,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
    NotificationCard(notif = notif, onDelete = onDelete, onMarkRead = onMarkRead)
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp),
        color = AppColors.textIcons.copy(alpha = 0.4f))
  }
}

/* ---------------------------------------------------------
   SWIPE CARD
--------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun NotificationCard(notif: Notification, onDelete: () -> Unit, onMarkRead: () -> Unit) {
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
      dismissContent = { NotificationContent(notif = notif) },
      directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
      dismissThresholds = { direction ->
        when (direction) {
          DismissDirection.StartToEnd -> FractionalThreshold(0.10f) // mark read
          DismissDirection.EndToStart -> FractionalThreshold(0.40f) // delete
        }
      })
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeBackground(state: DismissState) {
  val direction = state.dismissDirection ?: return

  val isTriggered = state.targetValue != DismissValue.Default
  val scale by animateFloatAsState(if (isTriggered) 1.3f else 1f)

  val color =
      when (direction) {
        DismissDirection.StartToEnd -> AppColors.neutral
        DismissDirection.EndToStart -> AppColors.negative
      }

  Box(
      modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
      contentAlignment =
          when (direction) {
            DismissDirection.StartToEnd -> Alignment.CenterStart
            DismissDirection.EndToStart -> Alignment.CenterEnd
          }) {
        val icon =
            when (direction) {
              DismissDirection.StartToEnd -> Icons.Default.MarkChatRead
              DismissDirection.EndToStart -> Icons.Default.Delete
            }

        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.scale(scale))
      }
}

/* ---------------------------------------------------------
   CARD CONTENT
--------------------------------------------------------- */

@Composable
private fun NotificationContent(notif: Notification) {

  val dateTime = notif.sentAtLocalDateTime()
  val date = dateTime.toLocalDate()
  val today = LocalDate.now()
  val yesterday = today.minusDays(1)

  val timeText =
      when (date) {
        today,
        yesterday -> dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        else -> dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
      }

  val parts = notif.parts()
  val iconPainter = painterResource(R.drawable.google_logo) // Placeholder for all notifications

  Row(
      modifier =
          Modifier.background(AppColors.primary)
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp)) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            modifier = Modifier.width(44.dp).clip(RoundedCornerShape(22.dp)))

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = notif.type.label(),
              style = MaterialTheme.typography.bodySmall,
              fontSize = 17.sp,
              color = AppColors.textIcons)

          Text(
              text =
                  buildAnnotatedString {
                    append(parts.prefix)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(parts.bold) }
                    append(parts.suffix)
                  },
              style = MaterialTheme.typography.bodySmall,
              color = AppColors.textIconsFade,
              modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
          Text(
              text = timeText,
              style = MaterialTheme.typography.bodySmall,
              color = AppColors.textIconsFade)

          if (!notif.read) {
            Box(
                modifier =
                    Modifier.padding(top = 6.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.negative))
          }
        }
      }
}

/* ---------------------------------------------------------
   EXTENSION FOR DISPLAY NAME
--------------------------------------------------------- */

fun NotificationType.label(): String {
  return when (this) {
    NotificationType.FRIEND_REQUEST -> "Incoming friend request"
    NotificationType.JOIN_DISCUSSION -> "Discussion invite"
    NotificationType.JOIN_SESSION -> "Session invite"
  }
}

@Composable
fun ExpandableText(text: String, previewCharLimit: Int = 160, modifier: Modifier = Modifier) {
  var expanded by remember { mutableStateOf(false) }

  Column(modifier = modifier) {
    Text(
        text =
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
fun ExpandableAnnotatedBio(username: String, fullBio: String, previewCharLimit: Int = 150) {
  var expanded by remember { mutableStateOf(false) }

  fun annotate(text: String): AnnotatedString {
    return buildAnnotatedString {
      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(username) }
      append(text.removePrefix(username))
    }
  }

  if (fullBio.length <= previewCharLimit) {
    Text(annotate(fullBio))
  } else {
    Column {
      val preview = fullBio.take(previewCharLimit) + "…"
      Text(annotate(if (expanded) fullBio else preview))

      Text(
          text = if (expanded) "Show less" else "Show more",
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded })
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSheet(
    data: NotificationPopupData,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      containerColor = AppColors.primary,
      contentColor = AppColors.textIcons,
      dragHandle = null) {
        when (data) {
          is NotificationPopupData.Discussion ->
              DiscussionSessionSheetContent(
                  title = data.title,
                  participants = data.participants,
                  dateLabel = data.dateLabel,
                  description = data.description,
                  icon = data.icon,
                  onAccept = onAccept,
                  onDecline = onDecline,
                  onClose = onDismiss)
          is NotificationPopupData.Session ->
              DiscussionSessionSheetContent(
                  title = data.title,
                  participants = data.participants,
                  dateLabel = data.dateLabel,
                  description = data.description,
                  icon = data.icon,
                  onAccept = onAccept,
                  onDecline = onDecline,
                  onClose = onDismiss)
          is NotificationPopupData.FriendRequest ->
              FriendRequestSheetContent(
                  username = data.username,
                  handle = data.handle,
                  bio = data.bio,
                  avatar = data.avatar,
                  onAccept = onAccept,
                  onDecline = onDecline,
                  onClose = onDismiss)
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                Text(text = "Decline", modifier = Modifier.padding(4.dp))
              }
            }

        Button(
            onClick = onAccept,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.affirmative, contentColor = AppColors.textIcons),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.medium),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Text(text = "Accept", modifier = Modifier.padding(4.dp))
              }
            }
      }
}

@Composable
fun DiscussionSessionSheetContent(
    title: String,
    participants: Int,
    dateLabel: String,
    description: String,
    icon: Painter,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClose: () -> Unit
) {
  Column(modifier = Modifier.padding(20.dp)) {

    // Close button
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = AppColors.textIcons,
          modifier = Modifier.clickable { onClose() })
    }

    Spacer(Modifier.height(4.dp))

    // Title area
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
          painter = icon,
          contentDescription = null,
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
      Spacer(Modifier.width(12.dp))
      Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = AppColors.textIconsFade)
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = "$participants participants • $dateLabel",
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.textIconsFade)

    Spacer(Modifier.height(16.dp))

    // Header for description
    Text(
        text =
            if (title.contains("discussion", ignoreCase = true)) "About this discussion"
            else "About this session",
        style = MaterialTheme.typography.titleMedium,
        color = AppColors.textIcons,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp))

    // Expandable long description
    ExpandableText(text = description, previewCharLimit = 160)

    Spacer(Modifier.height(20.dp))

    BottomSheetButtons(onDecline = onDecline, onAccept = onAccept)
  }
}

@Composable
fun FriendRequestSheetContent(
    username: String,
    handle: String,
    bio: String,
    avatar: Painter,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClose: () -> Unit
) {
  Column(modifier = Modifier.padding(20.dp)) {

    // Close button
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = AppColors.textIcons,
          modifier = Modifier.clickable { onClose() })
    }

    Spacer(Modifier.height(4.dp))

    // Title area
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
          painter = avatar,
          contentDescription = null,
          modifier = Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)))

      Spacer(Modifier.width(12.dp))

      Column {
        Text(
            text = username,
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.textIcons)
        Text(
            text = "@$handle",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.textIcons)
      }
    }

    Spacer(Modifier.height(16.dp))

    // BIO header
    Text(
        text = "About",
        style = MaterialTheme.typography.titleMedium,
        color = AppColors.textIcons,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp))

    // Bold-username bio with expandable text
    ExpandableAnnotatedBio(username = username, fullBio = bio, previewCharLimit = 150)

    Spacer(Modifier.height(20.dp))

    BottomSheetButtons(onDecline = onDecline, onAccept = onAccept)
  }
}

/* ---------------------------------------------------------
   BACKEND-BASED POPUP MAPPING
--------------------------------------------------------- */

fun Notification.toPopupData(icon: Painter): NotificationPopupData {
  val dateLabel = sentAtLocalDateTime().format(DateTimeFormatter.ofPattern("MMM d"))
  return when (type) {
    NotificationType.FRIEND_REQUEST ->
        NotificationPopupData.FriendRequest(
            username = "User $senderOrDiscussionId",
            handle = senderOrDiscussionId,
            bio = "User $senderOrDiscussionId wants to add you as a friend.",
            avatar = icon)
    NotificationType.JOIN_DISCUSSION ->
        NotificationPopupData.Discussion(
            title = "Discussion invitation",
            participants = 0,
            dateLabel = dateLabel,
            description = "You've been invited to join discussion $senderOrDiscussionId.",
            icon = icon)
    NotificationType.JOIN_SESSION ->
        NotificationPopupData.Session(
            title = "Session invitation",
            participants = 0,
            dateLabel = dateLabel,
            description = "You've been invited to join session $senderOrDiscussionId.",
            icon = icon)
  }
}

fun stubBackendNotifications(receiverId: String): List<Notification> {
  return listOf(
      Notification(
          uid = "n1",
          senderOrDiscussionId = "user_abc",
          receiverId = receiverId,
          read = false,
          type = NotificationType.FRIEND_REQUEST,
          sentAt = Timestamp(Date(System.currentTimeMillis() - 60 * 60 * 1000)) // 1h ago
          ),
      Notification(
          uid = "n2",
          senderOrDiscussionId = "disc_jan_game",
          receiverId = receiverId,
          read = true,
          type = NotificationType.JOIN_DISCUSSION,
          sentAt = Timestamp(Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000)) // 2h ago
          ),
      Notification(
          uid = "n3",
          senderOrDiscussionId = "session_fri_night",
          receiverId = receiverId,
          read = false,
          type = NotificationType.JOIN_SESSION,
          sentAt = Timestamp(Date(System.currentTimeMillis() - 5 * 60 * 60 * 1000)) // 5h ago
          ),
      Notification(
          uid = "n4",
          senderOrDiscussionId = "user_xyz",
          receiverId = receiverId,
          read = true,
          type = NotificationType.FRIEND_REQUEST,
          sentAt = Timestamp(Date(System.currentTimeMillis() - 26 * 60 * 60 * 1000)) // 26h ago
          ),
      Notification(
          uid = "n5",
          senderOrDiscussionId = "disc_strategy",
          receiverId = receiverId,
          read = false,
          type = NotificationType.JOIN_DISCUSSION,
          sentAt = Timestamp(Date(System.currentTimeMillis() - 48 * 60 * 60 * 1000)) // 2 days ago
          ))
}
