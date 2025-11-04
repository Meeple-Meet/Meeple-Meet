/** Documentation was written with the help of ChatGPT */
package com.github.meeplemeet.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.discussions.Poll
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.appShapes
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/** Test-tag constants for the Discussion screen and its poll sub-components. */
object DiscussionTestTags {
  const val INPUT_FIELD = "Input Field"
  const val SEND_BUTTON = "Send Button"
  const val ATTACHMENT_BUTTON = "attachment_button"
  const val ATTACHMENT_POLL_OPTION = "attachment_poll_option"

  const val DIALOG_ROOT = "poll_dialog_root"
  const val QUESTION_FIELD = "poll_question_field"
  const val OPTION_TEXT_FIELD = "poll_option_field"
  const val ADD_OPTION_BUTTON = "poll_add_option"
  const val REMOVE_OPTION_BUTTON = "poll_remove_option"
  const val ALLOW_MULTIPLE_CHECKBOX = "poll_allow_multiple"
  const val CREATE_POLL_CONFIRM = "poll_create_button"

  fun pollVoteButton(msgIndex: Int, optIndex: Int) = "poll_msg${msgIndex}_opt${optIndex}_vote"

  fun pollPercent(msgIndex: Int, optIndex: Int) = "poll_msg${msgIndex}_opt${optIndex}_percent"

  fun discussionInfo(name: String) = "DiscussionInfo/$name"
}

/**
 * Root screen for a single discussion.
 *
 * @param account Current logged-in user.
 * @param discussion Discussion to display.
 * @param viewModel discussion source of truth.
 * @param onBack Navigation – upward finish.
 * @param onOpenDiscussionInfo Opens details bottom-sheet.
 * @param onCreateSessionClick Opens / creates a game session when the user is admin or participant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(
    account: Account,
    discussion: Discussion,
    viewModel: DiscussionViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenDiscussionInfo: (Discussion) -> Unit = {},
    onCreateSessionClick: (Discussion) -> Unit = {},
) {
  val scope = rememberCoroutineScope()
  var messageText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  var isSending by remember { mutableStateOf(false) }
  var discussionName by remember { mutableStateOf("Loading...") }
  val userCache = remember { mutableStateMapOf<String, Account>() }

  val discussionState by viewModel.discussionFlow(discussion.uid).collectAsState()

  LaunchedEffect(discussion.uid) { viewModel.readDiscussionMessages(account, discussion) }

  LaunchedEffect(discussionState) {
    discussionState?.let { disc ->
      discussionName = disc.name
      disc.messages.forEach { msg ->
        if (!userCache.containsKey(msg.senderId) && msg.senderId != account.uid) {
          try {
            viewModel.getOtherAccount(msg.senderId) { acct -> userCache[msg.senderId] = acct }
          } catch (_: Exception) {}
        }
      }
    }
  }

  LaunchedEffect(discussionState?.messages?.size) {
    val size = discussionState?.messages?.size ?: 0
    if (size > 0) {
      try {
        listState.animateScrollToItem(maxOf(0, size - 1))
      } catch (_: Exception) {}
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(AppColors.primary)) {
    TopAppBar(
        title = {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                  Modifier.fillMaxSize()
                      .testTag(DiscussionTestTags.discussionInfo(discussion.name))
                      .clickable { onOpenDiscussionInfo(discussion) }) {
                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .background(color = AppColors.focus, shape = CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = discussionName,
                    style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.textIcons),
                    modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
              }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AppColors.textIconsFade,
                modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON))
          }
        },
        actions = {
          val icon =
              when {
                discussion.session != null &&
                    discussion.session.participants.contains(account.uid) -> Icons.Default.Games
                discussion.admins.contains(account.uid) -> Icons.Default.LibraryAdd
                else -> null
              }

          if (icon != null) {
            IconButton(onClick = { onCreateSessionClick(discussion) }) {
              Icon(icon, contentDescription = "Session action")
            }
          }
        })

    val messages: List<Message> = discussionState?.messages ?: emptyList()

    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
          itemsIndexed(
              items = messages, key = { _, msg -> "${msg.senderId}-${msg.createdAt.seconds}-${msg.createdAt.nanoseconds}" }) {
                  index,
                  message ->
                val isMine = message.senderId == account.uid
                val sender = if (!isMine) userCache[message.senderId]?.name ?: "Unknown" else "You"

                val showDateHeader =
                    shouldShowDateHeader(
                        current = message.createdAt.toDate(),
                        previous = messages.getOrNull(index - 1)?.createdAt?.toDate())
                if (showDateHeader) {
                  Spacer(Modifier.height(3.dp))
                  DateSeparator(date = message.createdAt.toDate())
                  Spacer(Modifier.height(3.dp))
                }

                if (message.poll != null) {
                  PollBubble(
                      msgIndex = index,
                      poll = message.poll,
                      authorName = sender,
                      currentUserId = account.uid,
                      onVote = { optionIndex, isRemoving ->
                        if (isRemoving) {
                          viewModel.removeVoteFromPoll(discussion, message, account, optionIndex)
                        } else {
                          viewModel.voteOnPoll(discussion, message, account, optionIndex)
                        }
                      },
                      createdAt = message.createdAt.toDate())
                } else {
                  ChatBubble(message, isMine, sender)
                }
              }
        }

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(8.dp)
                .background(AppColors.secondary, shape = CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Box {
            IconButton(
                modifier = Modifier.testTag(DiscussionTestTags.ATTACHMENT_BUTTON),
                onClick = { showAttachmentMenu = true }) {
                  Icon(
                      Icons.Default.AttachFile,
                      contentDescription = "Attach",
                      tint = AppColors.textIconsFade)
                }

            if (showAttachmentMenu) {
              val popupOffset = IntOffset(x = -30, y = -170) // ↓ shift down
              Popup(
                  onDismissRequest = { showAttachmentMenu = false },
                  offset = popupOffset,
                  properties = PopupProperties(focusable = true)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.secondary,
                        border = BorderStroke(1.dp, AppColors.textIconsFade),
                        modifier = Modifier.widthIn(min = 40.dp, max = 100.dp) // ← kill the 8 dp
                        ) {
                          Column {
                            DropdownMenuItem(
                                text = { Text("Create poll") },
                                onClick = {
                                  showAttachmentMenu = false
                                  showPollDialog = true
                                },
                                modifier =
                                    Modifier.testTag(DiscussionTestTags.ATTACHMENT_POLL_OPTION))
                          }
                        }
                  }
            }
          }

          if (showPollDialog) {
            CreatePollDialog(
                onDismiss = { showPollDialog = false },
                onCreate = { question, options, allowMultiple ->
                  viewModel.createPoll(
                      discussion = discussion,
                      creatorId = account.uid,
                      question = question,
                      options = options,
                      allowMultipleVotes = allowMultiple)
                  showPollDialog = false
                })
          }

          Spacer(Modifier.width(8.dp))

          BasicTextField(
              value = messageText,
              onValueChange = { messageText = it },
              modifier = Modifier.weight(1f).testTag(DiscussionTestTags.INPUT_FIELD),
              singleLine = true,
              decorationBox = { inner ->
                if (messageText.isEmpty())
                    Text("Type something...", color = AppColors.textIconsFade)
                inner()
              })

          Spacer(Modifier.width(8.dp))

          IconButton(
              modifier = Modifier.testTag(DiscussionTestTags.SEND_BUTTON),
              onClick = {
                if (messageText.isNotBlank() && !isSending) {
                  scope.launch {
                    isSending = true
                    try {
                      viewModel.sendMessageToDiscussion(discussion, account, messageText)
                      messageText = ""
                    } finally {
                      isSending = false
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
 * Visual bubble for a poll message.
 *
 * @param msgIndex Position inside the message list (used for test tags).
 * @param poll The poll data.
 * @param authorName Display name of the creator.
 * @param currentUserId Id of the viewer (to show personal vote).
 * @param createdAt Time-stamp shown under the card.
 * @param onVote Callback when an option is tapped (index, isRemoving).
 */
@Composable
fun PollBubble(
    msgIndex: Int,
    poll: Poll,
    authorName: String,
    currentUserId: String,
    createdAt: Date,
    onVote: (optionIndex: Int, isRemoving: Boolean) -> Unit
) {
  val isMine = authorName == "You"
  val userVotes = poll.getUserVotes(currentUserId) ?: emptyList()
  val counts = poll.getVoteCountsByOption()
  val total = poll.getTotalVotes()

  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {

          /*  card container  */
          Card(
              shape = appShapes.large,
              colors = CardDefaults.cardColors(containerColor = AppColors.secondary),
              elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
              modifier = Modifier.widthIn(min = 120.dp, max = 250.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {

                  /*  header  */
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).background(AppColors.focus, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isMine) "$authorName asked:" else "$authorName asks:",
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textIcons,
                        fontSize = 14.sp)
                  }
                  Spacer(Modifier.height(4.dp))
                  Text(poll.question, color = AppColors.textIcons, fontSize = 14.sp)
                  Spacer(Modifier.height(8.dp))
                  Text(
                      text = if (poll.allowMultipleVotes) "Select multiple" else "Select one",
                      color = AppColors.textIconsFade,
                      fontSize = 12.sp)
                  Spacer(Modifier.height(8.dp))

                  /*  vote rows  */
                  poll.options.forEachIndexed { index, label ->
                    val votesForOption = counts[index] ?: 0
                    val percent = if (total > 0) (votesForOption * 100f / total).toInt() else 0
                    val selected = index in userVotes
                    val background = if (selected) AppColors.divider else AppColors.primary

                    /*  SINGLE CLICKABLE CONTAINER  */
                    Box(
                        modifier =
                            Modifier.testTag(DiscussionTestTags.pollVoteButton(msgIndex, index))
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(background)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = AppColors.affirmative),
                                    onClick = {
                                      // single-choice: un-vote previous selection
                                      if (!poll.allowMultipleVotes &&
                                          !selected &&
                                          userVotes.isNotEmpty()) {
                                        userVotes.firstOrNull()?.let { prev -> onVote(prev, true) }
                                      }
                                      onVote(index, selected)
                                    })) {
                          Row(
                              modifier =
                                  Modifier.fillMaxWidth()
                                      .padding(horizontal = 12.dp, vertical = 10.dp),
                              horizontalArrangement = Arrangement.SpaceBetween,
                              verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                  val affirmative = AppColors.affirmative
                                  val primary = AppColors.primary
                                  val isCheckBox = poll.allowMultipleVotes
                                  val shapeSize = 18.dp
                                  val stroke = 1.dp

                                  Canvas(
                                      modifier =
                                          Modifier.size(shapeSize)
                                              .border(
                                                  stroke,
                                                  AppColors.textIcons,
                                                  if (isCheckBox) RoundedCornerShape(4.dp)
                                                  else CircleShape)
                                              .padding(3.dp)) {
                                        if (selected) {
                                          if (isCheckBox) {
                                            // draw check-box fill + check mark
                                            drawRoundRect(
                                                color = affirmative,
                                                size = size,
                                                cornerRadius =
                                                    CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                                            // simple check mark (two lines)
                                            val check =
                                                Path().apply {
                                                  moveTo(size.width * .25f, size.height * .5f)
                                                  lineTo(size.width * .45f, size.height * .7f)
                                                  lineTo(size.width * .75f, size.height * .3f)
                                                }
                                            drawPath(
                                                check,
                                                color = primary,
                                                style =
                                                    Stroke(
                                                        width = 2.dp.toPx(), cap = StrokeCap.Round))
                                          } else {
                                            // original radio dot
                                            drawCircle(
                                                color = affirmative, radius = size.minDimension / 2)
                                          }
                                        }
                                      }
                                  Spacer(Modifier.width(6.dp))
                                  Text(label, color = AppColors.textIcons, fontSize = 14.sp)
                                }
                                Text(
                                    "$percent%",
                                    color = AppColors.textIcons,
                                    fontSize = 14.sp,
                                    modifier =
                                        Modifier.testTag(
                                            DiscussionTestTags.pollPercent(msgIndex, index)))
                              }
                        }
                    Spacer(Modifier.height(6.dp))
                  }
                }
              }

          Text(
              text = DateFormat.format("HH:mm", createdAt).toString(),
              style = MaterialTheme.typography.labelSmall.copy(color = AppColors.textIconsFade),
              modifier = Modifier.padding(top = 2.dp))
        }
      }
}

/**
 * Ordinary chat message bubble (text only).
 *
 * @param message Content to render.
 * @param isMine Whether the message was sent by the current user (aligns right).
 * @param senderName Display name of the sender (null for own messages).
 */
@Composable
private fun ChatBubble(message: Message, isMine: Boolean, senderName: String?) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        if (!isMine) {
          Box(modifier = Modifier.size(32.dp).background(AppColors.focus, shape = CircleShape))
          Spacer(Modifier.width(6.dp))
        }

        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
          Box(
              modifier =
                  Modifier.shadow(elevation = 4.dp, shape = appShapes.large, clip = false)
                      .background(color = AppColors.secondary, shape = appShapes.large)
                      .padding(10.dp)
                      .widthIn(min = 100.dp, max = 250.dp)) {
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
                  Modifier.size(32.dp).background(color = AppColors.focus, shape = CircleShape))
        }
      }
}

/**
 * Sticky date header inside the message list.
 *
 * @param date Calendar to format.
 */
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
 * Dialog to create a new poll.
 *
 * @param onDismiss Close without creating.
 * @param onCreate Create poll with non-blank options.
 */
@Composable
fun CreatePollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean) -> Unit) {
  var question by remember { mutableStateOf("") }
  var options by remember { mutableStateOf(listOf("", "")) }
  var allowMultiple by remember { mutableStateOf(false) }

  AlertDialog(
      containerColor = AppColors.primary,
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
            modifier = Modifier.testTag(DiscussionTestTags.CREATE_POLL_CONFIRM),
            onClick = { onCreate(question, options.filter { it.isNotBlank() }, allowMultiple) },
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
            enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2) {
              Text("Create")
            }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.negative)) {
              Text("Cancel")
            }
      },
      title = { Text("Create Poll", color = AppColors.textIcons) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = question,
              onValueChange = { question = it },
              label = { Text("Question") },
              singleLine = true,
              colors =
                  TextFieldDefaults.colors()
                      .copy(
                          focusedTextColor = AppColors.textIcons,
                          unfocusedTextColor = AppColors.textIcons,
                          unfocusedIndicatorColor = AppColors.textIcons,
                          focusedIndicatorColor = AppColors.textIcons,
                          unfocusedLabelColor = AppColors.textIconsFade,
                          focusedLabelColor = AppColors.textIconsFade,
                          unfocusedContainerColor = Color.Transparent,
                          focusedContainerColor = Color.Transparent),
              modifier = Modifier.fillMaxWidth().testTag(DiscussionTestTags.QUESTION_FIELD))

          Text("Options", fontWeight = FontWeight.Bold)
          options.forEachIndexed { index, option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                  value = option,
                  colors =
                      TextFieldDefaults.colors()
                          .copy(
                              focusedTextColor = AppColors.textIcons,
                              unfocusedTextColor = AppColors.textIcons,
                              unfocusedIndicatorColor = AppColors.textIcons,
                              focusedIndicatorColor = AppColors.textIcons,
                              unfocusedLabelColor = AppColors.textIconsFade,
                              focusedLabelColor = AppColors.textIconsFade,
                              unfocusedContainerColor = Color.Transparent,
                              focusedContainerColor = Color.Transparent),
                  onValueChange = { new ->
                    options = options.toMutableList().apply { this[index] = new }
                  },
                  label = { Text("Option ${index + 1}") },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag(DiscussionTestTags.OPTION_TEXT_FIELD))
              Spacer(Modifier.width(8.dp))
              if (options.size > 2) {
                IconButton(
                    onClick = { options = options.toMutableList().apply { removeAt(index) } },
                    modifier = Modifier.testTag(DiscussionTestTags.REMOVE_OPTION_BUTTON)) {
                      Icon(Icons.Default.Remove, contentDescription = "Remove")
                    }
              }
              if (index == options.lastIndex && options.size < 7) {
                IconButton(
                    onClick = { options = options + "" },
                    modifier = Modifier.testTag(DiscussionTestTags.ADD_OPTION_BUTTON)) {
                      Icon(Icons.Default.Add, contentDescription = "Add")
                    }
              }
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allowMultiple,
                onCheckedChange = { allowMultiple = it },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = AppColors.affirmative, uncheckedColor = Color.Unspecified),
                modifier = Modifier.testTag(DiscussionTestTags.ALLOW_MULTIPLE_CHECKBOX))
            Text("Allow multiple votes")
          }
        }
      },
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.testTag(DiscussionTestTags.DIALOG_ROOT))
}

/** Formats a date as “Today”, “Yesterday” or “MMM dd, yyyy”. */
fun formatDateBubble(date: Date): String {
  val today = Calendar.getInstance()
  val cal = Calendar.getInstance().apply { time = date }

  return when {
    isSameDay(cal, today) -> "Today"
    isSameDay(cal, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
    else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
  }
}

/** Returns true if [current] and [previous] are on different calendar days. */
fun shouldShowDateHeader(current: Date, previous: Date?): Boolean {
  if (previous == null) return true
  val calCurrent = Calendar.getInstance().apply { time = current }
  val calPrev = Calendar.getInstance().apply { time = previous }
  return !(calCurrent.get(Calendar.YEAR) == calPrev.get(Calendar.YEAR) &&
      calCurrent.get(Calendar.DAY_OF_YEAR) == calPrev.get(Calendar.DAY_OF_YEAR))
}

/** Returns true if two [Calendar] instances represent the same day. */
fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
  return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
      cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
