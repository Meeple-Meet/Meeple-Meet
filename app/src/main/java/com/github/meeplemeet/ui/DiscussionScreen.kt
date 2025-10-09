package com.github.meeplemeet.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.appShapes
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Composable screen that displays a discussion (chat) and allows sending messages.
 *
 * Messages are collected from [FirestoreViewModel] via a [StateFlow] and displayed in a scrollable
 * list. Users are cached locally for display purposes.
 *
 * @param viewModel FirestoreViewModel for fetching discussion and sending messages
 * @param discussionId ID of the discussion to display
 * @param currentUser The currently logged-in user
 * @param onBack Callback when the back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(
    viewModel: FirestoreViewModel,
    discussionId: String,
    currentUser: Account,
    onBack: () -> Unit
) {
  val scope = rememberCoroutineScope()
  var messageText by remember { mutableStateOf("") }
  val messages = remember { mutableStateListOf<Message>() }
  val listState = rememberLazyListState()
  var isSending by remember { mutableStateOf(false) }
  var discussionName by remember { mutableStateOf("Loading...") }
  val userCache = remember { mutableStateMapOf<String, Account>() }

  /** Collect the discussion StateFlow as Compose state */
  val discussion by viewModel.discussionFlow(discussionId).collectAsState()

  /**
   * Update messages list and user cache whenever the discussion changes.
   *
   * Scrolls to the last message automatically.
   */
  LaunchedEffect(discussion) {
    discussion?.let { disc ->
      messages.clear()
      messages.addAll(disc.messages)
      scope.launch { listState.animateScrollToItem(messages.size) }

      disc.messages.forEach { msg ->
        if (!userCache.containsKey(msg.senderId) && msg.senderId != currentUser.uid) {
          try {
            viewModel.getOtherAccount(msg.senderId) { account -> userCache[msg.senderId] = account }
          } catch (_: Exception) {}
        }
      }

      discussionName = disc.name
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(AppColors.primary)) {
    /** Top bar showing discussion name and navigation back button */
    TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(40.dp).background(color = Color(0xFF800080), shape = CircleShape))
            /** TODO: Placeholder for avatar */
            Spacer(Modifier.width(8.dp))
            Text(
                text = discussionName,
                style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.textIcons))
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AppColors.textIconsFade)
          }
        },
        actions = {
          IconButton(onClick = {}) { Icon(Icons.Default.Search, contentDescription = "Search") }
        })

    /** LazyColumn showing all messages with optional date separators */
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
          itemsIndexed(messages) { index, message ->
            val isMine = message.senderId == currentUser.uid
            val sender = if (!isMine) userCache[message.senderId]?.name ?: "Unknown" else "You"

            val showDateHeader =
                shouldShowDateHeader(
                    current = message.createdAt.toDate(),
                    previous = messages.getOrNull(index - 1)?.createdAt?.toDate())
            if (showDateHeader) {
              DateSeparator(date = message.createdAt.toDate())
            }

            ChatBubble(message, isMine, sender)
          }
        }

    /** Input row at the bottom for typing messages */
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(8.dp)
                .background(AppColors.secondary, shape = CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = {}) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = "Attach",
                tint = AppColors.textIconsFade)
          }
          Spacer(Modifier.width(8.dp))

          /** Text input field */
          BasicTextField(
              value = messageText,
              onValueChange = { messageText = it },
              modifier = Modifier.weight(1f),
              singleLine = true,
              decorationBox = { innerTextField ->
                if (messageText.isEmpty()) {
                  Text("Type something...", color = AppColors.textIconsFade)
                }
                innerTextField()
              })

          Spacer(Modifier.width(8.dp))

          /** Send button */
          IconButton(
              onClick = {
                discussion?.let { disc ->
                  if (messageText.isNotBlank() && !isSending) {
                    scope.launch {
                      isSending = true
                      try {
                        viewModel.sendMessageToDiscussion(disc, currentUser, messageText)
                        messageText = ""
                      } finally {
                        isSending = false
                      }
                    }
                  }
                }
              },
              enabled = !isSending) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = AppColors.textIconsFade)
              }
        }
  }
}

/**
 * A single message bubble in the chat.
 *
 * @param message Message to display
 * @param isMine True if the message belongs to the current user
 * @param senderName Name of the sender
 */
@Composable
private fun ChatBubble(message: Message, isMine: Boolean, senderName: String?) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        if (!isMine) {
          Box(modifier = Modifier.size(32.dp).background(Color(0xFF800080), shape = CircleShape))
          /** TODO: Placeholder for avatar */
          Spacer(Modifier.width(6.dp))
        }

        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
          Box(
              modifier =
                  Modifier.shadow(elevation = 4.dp, shape = appShapes.large, clip = false)
                      .background(color = AppColors.secondary, shape = appShapes.large)
                      .padding(10.dp)
                      .widthIn(max = 250.dp)) {
                Column {
                  if (senderName != null) {
                    Text(
                        senderName,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                color = AppColors.textIconsFade))
                    Spacer(Modifier.height(2.dp))
                  }
                  Text(
                      message.content,
                      style =
                          MaterialTheme.typography.bodySmall.copy(color = AppColors.textIconsFade))
                }
              }
          Text(
              text = DateFormat.format("HH:mm", message.createdAt.toDate()).toString(),
              style = MaterialTheme.typography.labelSmall.copy(color = AppColors.textIconsFade),
              modifier =
                  Modifier.padding(top = 2.dp)
                      .align(if (isMine) Alignment.End else Alignment.Start))
        }

        if (isMine) {
          Spacer(Modifier.width(6.dp))
          Box(
              modifier =
                  Modifier.size(32.dp).background(color = Color(0xFF800080), shape = CircleShape))
          /** TODO: Placeholder for avatar */
        }
      }
}

/** Shows a date separator between messages. */
@Composable
private fun DateSeparator(date: Date) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Text(
        text = formatDateBubble(date),
        color = AppColors.textIconsFade,
        modifier =
            Modifier.background(AppColors.divider, shape = CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall)
  }
}

/**
 * Formats a date for display in a date separator. Returns "Today", "Yesterday", or formatted date
 * string.
 */
fun formatDateBubble(date: Date): String {
  val cal = Calendar.getInstance()
  val today = Calendar.getInstance()
  cal.time = date

  return when {
    isSameDay(cal, today) -> "Today"
    isSameDay(cal, today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
    else -> DateFormat.format("MMM dd, yyyy", date).toString()
  }
}

/** Determines whether a date header should be displayed between messages. */
fun shouldShowDateHeader(current: Date, previous: Date?): Boolean {
  if (previous == null) return true
  val calCurrent = Calendar.getInstance().apply { time = current }
  val calPrev = Calendar.getInstance().apply { time = previous }
  return !(calCurrent.get(Calendar.YEAR) == calPrev.get(Calendar.YEAR) &&
      calCurrent.get(Calendar.DAY_OF_YEAR) == calPrev.get(Calendar.DAY_OF_YEAR))
}

/** Checks if two Calendar instances represent the same day. */
fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
  return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
      cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
