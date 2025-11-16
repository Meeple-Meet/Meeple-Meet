/** Documentation was written with the help of ChatGPT */
package com.github.meeplemeet.ui.discussions

import android.text.format.DateFormat
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
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.MessagingColors
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
  val messages by viewModel.messagesFlow(discussion.uid).collectAsState()

  LaunchedEffect(discussion.uid) { viewModel.readDiscussionMessages(account, discussion) }

  LaunchedEffect(discussionState) { discussionState?.let { disc -> discussionName = disc.name } }

  LaunchedEffect(messages) {
    messages.forEach { msg ->
      if (!userCache.containsKey(msg.senderId) && msg.senderId != account.uid) {
        try {
          viewModel.getOtherAccount(msg.senderId) { acct -> userCache[msg.senderId] = acct }
        } catch (_: Exception) {}
      }
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      try {
        listState.animateScrollToItem(maxOf(0, messages.size - 1))
      } catch (_: Exception) {}
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(MessagingColors.messagingBackground)) {
    TopAppBar(
        colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        title = {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                  Modifier.fillMaxSize()
                      .testTag(DiscussionTestTags.discussionInfo(discussion.name))
                      .clickable { onOpenDiscussionInfo(discussion) }) {
                Box(
                    modifier =
                        Modifier.size(Dimensions.ButtonSize.medium)
                            .clip(CircleShape)
                            .background(AppColors.neutral, CircleShape))
                Spacer(Modifier.width(Dimensions.Spacing.large))
                Text(
                    text = discussionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = Dimensions.TextSize.heading,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
              }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON))
          }
        },
        actions = {
          val icon =
              when {
                discussion.session != null &&
                    discussion.session.participants.contains(account.uid) -> Icons.Default.Games
                discussion.admins.contains(account.uid) || discussion.creatorId == account.uid ->
                    Icons.Default.LibraryAdd
                else -> null
              }

          if (icon != null) {
            IconButton(onClick = { onCreateSessionClick(discussion) }) {
              Icon(
                  icon,
                  contentDescription = "Session action",
                  tint = MaterialTheme.colorScheme.onSurface)
            }
          }
        })

    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
      itemsIndexed(items = messages, key = { _, msg -> msg.uid }) { index, message ->
        val isMine = message.senderId == account.uid
        val sender = if (!isMine) userCache[message.senderId]?.name ?: "Unknown" else "You"

        val showDateHeader =
            shouldShowDateHeader(
                current = message.createdAt.toDate(),
                previous = messages.getOrNull(index - 1)?.createdAt?.toDate())
        if (showDateHeader) {
          Spacer(Modifier.height(Dimensions.Spacing.extraSmall))
          DateSeparator(date = message.createdAt.toDate())
          Spacer(Modifier.height(Dimensions.Spacing.extraSmall))
        }

        // Check if this is the last message from this sender
        val nextMessage = messages.getOrNull(index + 1)
        val isLastFromSender = nextMessage?.senderId != message.senderId

        // Check if the previous message is from the same sender
        val prevMessage = messages.getOrNull(index - 1)
        val isSameSenderAsPrevious = prevMessage?.senderId == message.senderId && !showDateHeader

        if (message.poll != null) {
          PollBubble(
              msgIndex = index,
              poll = message.poll,
              authorName = sender,
              currentUserId = account.uid,
              onVote = { optionIndex, isRemoving ->
                if (isRemoving) {
                  viewModel.removeVoteFromPoll(discussion.uid, message.uid, account, optionIndex)
                } else {
                  viewModel.voteOnPoll(discussion.uid, message.uid, account, optionIndex)
                }
              },
              createdAt = message.createdAt.toDate(),
              showProfilePicture = isLastFromSender)
        } else {
          ChatBubble(message, isMine, sender, isLastFromSender)
        }

        // Add spacing between messages
        if (!isLastFromSender) {
          Spacer(Modifier.height(Dimensions.Spacing.extraSmall))
        } else {
          Spacer(Modifier.height(Dimensions.Spacing.small))
        }
      }
    }

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Dimensions.Elevation.medium) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          horizontal = Dimensions.Spacing.medium,
                          vertical = Dimensions.Spacing.medium),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
                Row(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(Dimensions.CornerRadius.round))
                            .background(MessagingColors.inputBackground)
                            .padding(
                                horizontal = Dimensions.Spacing.large,
                                vertical = Dimensions.Spacing.medium),
                    verticalAlignment = Alignment.CenterVertically) {
                      Box {
                        IconButton(
                            modifier =
                                Modifier.size(Dimensions.ButtonSize.medium)
                                    .testTag(DiscussionTestTags.ATTACHMENT_BUTTON),
                            onClick = { showAttachmentMenu = true }) {
                              Icon(
                                  Icons.Default.AttachFile,
                                  contentDescription = "Attach",
                                  tint = MessagingColors.primaryText,
                                  modifier = Modifier.size(Dimensions.IconSize.standard))
                            }

                        if (showAttachmentMenu) {
                          val popupOffset = IntOffset(x = -30, y = -200)
                          Popup(
                              onDismissRequest = { showAttachmentMenu = false },
                              offset = popupOffset,
                              properties = PopupProperties(focusable = true)) {
                                Surface(
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                                    color = MessagingColors.messageBubbleOther,
                                    shadowElevation = Dimensions.Elevation.high,
                                    modifier =
                                        Modifier.widthIn(max = Dimensions.AvatarSize.large)) {
                                      Column {
                                        DropdownMenuItem(
                                            text = {
                                              Icon(
                                                  Icons.Default.Poll,
                                                  contentDescription = null,
                                                  tint = MessagingColors.primaryText,
                                                  modifier =
                                                      Modifier.size(Dimensions.AvatarSize.large))
                                            },
                                            onClick = {
                                              showAttachmentMenu = false
                                              showPollDialog = true
                                            },
                                            modifier =
                                                Modifier.testTag(
                                                    DiscussionTestTags.ATTACHMENT_POLL_OPTION))
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

                      BasicTextField(
                          value = messageText,
                          onValueChange = { messageText = it },
                          modifier = Modifier.weight(1f).testTag(DiscussionTestTags.INPUT_FIELD),
                          textStyle =
                              MaterialTheme.typography.bodyMedium.copy(
                                  fontSize = Dimensions.TextSize.body,
                                  color = MessagingColors.primaryText),
                          decorationBox = { inner ->
                            if (messageText.isEmpty())
                                Text(
                                    "Message",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = Dimensions.TextSize.body,
                                    color = MessagingColors.metadataText)
                            inner()
                          })
                    }

                FloatingActionButton(
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
                    modifier =
                        Modifier.size(Dimensions.ButtonSize.standard)
                            .testTag(DiscussionTestTags.SEND_BUTTON),
                    containerColor = MessagingColors.whatsappGreen,
                    contentColor = MessagingColors.messageBubbleOther,
                    shape = CircleShape) {
                      Icon(
                          Icons.AutoMirrored.Filled.Send,
                          contentDescription = "Send",
                          modifier = Modifier.size(Dimensions.IconSize.standard))
                    }
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
 * @param showProfilePicture Whether to show the profile picture for this message.
 */
@Composable
fun PollBubble(
    msgIndex: Int,
    poll: Poll,
    authorName: String,
    currentUserId: String,
    createdAt: Date,
    onVote: (optionIndex: Int, isRemoving: Boolean) -> Unit,
    showProfilePicture: Boolean = true
) {
  val isMine = authorName == "You"
  val userVotes = poll.getUserVotes(currentUserId) ?: emptyList()
  val counts = poll.getVoteCountsByOption()
  val total = poll.getTotalVotes()

  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.small),
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
      verticalAlignment = Alignment.Bottom) {
        // Profile picture for received messages (on the left)
        if (!isMine) {
          if (showProfilePicture) {
            Box(
                modifier =
                    Modifier.size(Dimensions.AvatarSize.small)
                        .clip(CircleShape)
                        .background(AppColors.neutral, CircleShape))
          } else {
            Spacer(Modifier.width(Dimensions.AvatarSize.small))
          }
          Spacer(Modifier.width(Dimensions.Spacing.small))
        }

        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {

          /*  card container  */
          Surface(
              shape =
                  RoundedCornerShape(
                      topStart =
                          if (isMine) Dimensions.CornerRadius.large
                          else Dimensions.Spacing.extraSmall,
                      topEnd =
                          if (isMine) Dimensions.Spacing.extraSmall
                          else Dimensions.CornerRadius.large,
                      bottomStart = Dimensions.CornerRadius.large,
                      bottomEnd = Dimensions.CornerRadius.large),
              color =
                  if (isMine) MessagingColors.messageBubbleOwn
                  else MessagingColors.messageBubbleOther,
              shadowElevation = Dimensions.Elevation.minimal,
              modifier =
                  Modifier.widthIn(
                      min = Dimensions.ContainerSize.bottomSpacer.times(2),
                      max =
                          Dimensions.ContainerSize.bottomSpacer
                              .times(2)
                              .plus(Dimensions.Padding.giant))) {
                Column(modifier = Modifier.padding(Dimensions.Spacing.large)) {

                  /*  header  */
                  if (!isMine) {
                    Text(
                        text = authorName,
                        fontWeight = FontWeight.SemiBold,
                        color = MessagingColors.whatsappGreen,
                        fontSize = Dimensions.TextSize.medium)
                    Spacer(Modifier.height(Dimensions.Spacing.small))
                  }
                  Text(
                      poll.question,
                      color = MessagingColors.primaryText,
                      fontSize = Dimensions.TextSize.body,
                      fontWeight = FontWeight.Medium)
                  Spacer(Modifier.height(Dimensions.Spacing.medium))
                  Text(
                      text = if (poll.allowMultipleVotes) "Select multiple" else "Select one",
                      color = MessagingColors.metadataText,
                      fontSize = Dimensions.TextSize.small)
                  Spacer(Modifier.height(Dimensions.Spacing.medium))

                  /*  vote rows  */
                  poll.options.forEachIndexed { index, label ->
                    val votesForOption = counts[index] ?: 0
                    val percent = if (total > 0) (votesForOption * 100f / total).toInt() else 0
                    val selected = index in userVotes
                    val background =
                        if (selected) MessagingColors.selectionBackground
                        else MessagingColors.neutralBackground

                    /*  SINGLE CLICKABLE CONTAINER  */
                    Box(
                        modifier =
                            Modifier.testTag(DiscussionTestTags.pollVoteButton(msgIndex, index))
                                .fillMaxWidth()
                                .defaultMinSize(
                                    minHeight =
                                        Dimensions.ButtonSize.medium.plus(
                                            Dimensions.Spacing.extraSmall))
                                .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                                .background(background)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = MessagingColors.whatsappGreen),
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
                                      .padding(
                                          horizontal = Dimensions.Spacing.large,
                                          vertical = Dimensions.Padding.extraMedium),
                              horizontalArrangement = Arrangement.SpaceBetween,
                              verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)) {
                                      val isCheckBox = poll.allowMultipleVotes
                                      val shapeSize =
                                          Dimensions.IconSize.small.plus(
                                              Dimensions.Spacing.extraSmall)
                                      val borderColor =
                                          if (selected) MessagingColors.whatsappGreen
                                          else MessagingColors.secondaryText
                                      val fillColor = MessagingColors.whatsappGreen

                                      Canvas(
                                          modifier =
                                              Modifier.size(shapeSize)
                                                  .border(
                                                      Dimensions.DividerThickness.medium,
                                                      borderColor,
                                                      if (isCheckBox)
                                                          RoundedCornerShape(
                                                              Dimensions.Spacing.extraSmall)
                                                      else CircleShape)
                                                  .padding(Dimensions.Spacing.extraSmall)) {
                                            if (selected) {
                                              if (isCheckBox) {
                                                // draw check-box fill + check mark
                                                drawRoundRect(
                                                    color = fillColor,
                                                    size = size,
                                                    cornerRadius =
                                                        CornerRadius(
                                                            Dimensions.Spacing.extraSmall.toPx(),
                                                            Dimensions.Spacing.extraSmall.toPx()))
                                                // simple check mark (two lines)
                                                val check =
                                                    Path().apply {
                                                      moveTo(size.width * .25f, size.height * .5f)
                                                      lineTo(size.width * .45f, size.height * .7f)
                                                      lineTo(size.width * .75f, size.height * .3f)
                                                    }
                                                drawPath(
                                                    check,
                                                    color = Color.White,
                                                    style =
                                                        Stroke(
                                                            width =
                                                                Dimensions.DividerThickness.thin
                                                                    .toPx(),
                                                            cap = StrokeCap.Round))
                                              } else {
                                                // original radio dot
                                                drawCircle(
                                                    color = fillColor,
                                                    radius = size.minDimension / 2)
                                              }
                                            }
                                          }
                                      Spacer(Modifier.width(Dimensions.Spacing.medium))
                                      Text(
                                          label,
                                          color = MessagingColors.primaryText,
                                          fontSize = Dimensions.TextSize.standard,
                                          modifier = Modifier.weight(1f))
                                    }
                                Text(
                                    "$percent%",
                                    color = MessagingColors.metadataText,
                                    fontSize = Dimensions.TextSize.medium,
                                    fontWeight = FontWeight.Medium,
                                    modifier =
                                        Modifier.testTag(
                                            DiscussionTestTags.pollPercent(msgIndex, index)))
                              }
                        }
                    Spacer(Modifier.height(Dimensions.Spacing.small))
                  }

                  // Timestamp inside poll bubble
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = DateFormat.format("HH:mm", createdAt).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = Dimensions.TextSize.tiny,
                        color = MessagingColors.metadataText)
                  }
                }
              }
        }

        // Profile picture for sent messages (on the right)
        if (isMine) {
          Spacer(Modifier.width(Dimensions.Spacing.small))
          if (showProfilePicture) {
            Box(
                modifier =
                    Modifier.size(Dimensions.AvatarSize.small)
                        .clip(CircleShape)
                        .background(AppColors.focus, CircleShape))
          } else {
            Spacer(Modifier.width(Dimensions.AvatarSize.small))
          }
        }
      }
}

/**
 * Ordinary chat message bubble (text only).
 *
 * @param message Content to render.
 * @param isMine Whether the message was sent by the current user (aligns right).
 * @param senderName Display name of the sender (null for own messages).
 * @param showProfilePicture Whether to show the profile picture for this message.
 */
@Composable
private fun ChatBubble(
    message: Message,
    isMine: Boolean,
    senderName: String?,
    showProfilePicture: Boolean = true
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.small),
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
      verticalAlignment = Alignment.Bottom) {
        // Profile picture for received messages (on the left)
        if (!isMine) {
          if (showProfilePicture) {
            Box(
                modifier =
                    Modifier.size(Dimensions.AvatarSize.small)
                        .clip(CircleShape)
                        .background(AppColors.neutral, CircleShape))
          } else {
            Spacer(Modifier.width(Dimensions.AvatarSize.small))
          }
          Spacer(Modifier.width(Dimensions.Spacing.small))
        }

        // Message bubble
        Box(
            modifier =
                Modifier.widthIn(
                        max =
                            Dimensions.ContainerSize.bottomSpacer
                                .times(2)
                                .plus(Dimensions.Padding.giant))
                    .clip(
                        RoundedCornerShape(
                            topStart =
                                if (isMine) Dimensions.CornerRadius.large
                                else Dimensions.Spacing.extraSmall,
                            topEnd =
                                if (isMine) Dimensions.Spacing.extraSmall
                                else Dimensions.CornerRadius.large,
                            bottomStart = Dimensions.CornerRadius.large,
                            bottomEnd = Dimensions.CornerRadius.large))
                    .background(
                        color =
                            if (isMine) MessagingColors.messageBubbleOwn
                            else MessagingColors.messageBubbleOther,
                    )
                    .padding(
                        horizontal = Dimensions.Spacing.large,
                        vertical = Dimensions.Spacing.medium)) {
              Column {
                if (senderName != null && !isMine) {
                  Text(
                      senderName,
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = Dimensions.TextSize.small,
                      fontWeight = FontWeight.SemiBold,
                      color = MessagingColors.whatsappGreen)
                  Spacer(Modifier.height(Dimensions.Spacing.small))
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
                      Text(
                          message.content,
                          style = MaterialTheme.typography.bodyMedium,
                          fontSize = Dimensions.TextSize.body,
                          color = MessagingColors.primaryText,
                          modifier = Modifier.weight(1f, fill = false))
                      Text(
                          text = DateFormat.format("HH:mm", message.createdAt.toDate()).toString(),
                          style = MaterialTheme.typography.labelSmall,
                          fontSize = Dimensions.TextSize.tiny,
                          color = MessagingColors.metadataText)
                    }
              }
            }

        // Profile picture for sent messages (on the right)
        if (isMine) {
          Spacer(Modifier.width(Dimensions.Spacing.small))
          if (showProfilePicture) {
            Box(
                modifier =
                    Modifier.size(Dimensions.AvatarSize.small)
                        .clip(CircleShape)
                        .background(AppColors.focus, CircleShape))
          } else {
            Spacer(Modifier.width(Dimensions.AvatarSize.small))
          }
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
  Box(
      modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.Spacing.medium),
      contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
            color = MessagingColors.whatsappLightGreen,
            shadowElevation = Dimensions.Elevation.low) {
              Text(
                  text = formatDateBubble(date),
                  color = MessagingColors.metadataText,
                  fontSize = Dimensions.TextSize.small,
                  fontWeight = FontWeight.Medium,
                  modifier =
                      Modifier.padding(
                          horizontal = Dimensions.Spacing.large,
                          vertical = Dimensions.Spacing.small),
                  style = MaterialTheme.typography.labelSmall)
            }
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
            modifier =
                Modifier.testTag(DiscussionTestTags.CREATE_POLL_CONFIRM)
                    .defaultMinSize(
                        minWidth =
                            Dimensions.ComponentWidth.spaceLabelWidth.plus(
                                Dimensions.Padding.xxxLarge))
                    .padding(
                        horizontal = Dimensions.Spacing.medium,
                        vertical = Dimensions.Spacing.small),
            onClick = { onCreate(question, options.filter { it.isNotBlank() }, allowMultiple) },
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
            enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2) {
              Text(
                  "Create",
                  color = AppColors.textIcons,
                  style = MaterialTheme.typography.labelLarge)
            }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier =
                Modifier.defaultMinSize(
                        minWidth =
                            Dimensions.ComponentWidth.spaceLabelWidth.plus(
                                Dimensions.Padding.xxxLarge))
                    .padding(
                        horizontal = Dimensions.Spacing.medium,
                        vertical = Dimensions.Spacing.small),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.negative)) {
              Text(
                  "Cancel",
                  color = AppColors.textIcons,
                  style = MaterialTheme.typography.labelLarge)
            }
      },
      title = {
        Text(
            "Create Poll",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AppColors.textIcons)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
          OutlinedTextField(
              value = question,
              onValueChange = { question = it },
              label = { Text("Poll Question") },
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

          Spacer(Modifier.height(Dimensions.Spacing.small))

          Text(
              "Options",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = AppColors.textIcons)

          options.forEachIndexed { index, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
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

                  if (options.size > 2) {
                    IconButton(
                        onClick = { options = options.toMutableList().apply { removeAt(index) } },
                        modifier = Modifier.testTag(DiscussionTestTags.REMOVE_OPTION_BUTTON)) {
                          Icon(
                              Icons.Default.Remove,
                              contentDescription = "Remove",
                              tint = AppColors.textIcons)
                        }
                  }
                  if (index == options.lastIndex && options.size < 7) {
                    IconButton(
                        onClick = { options = options + "" },
                        modifier = Modifier.testTag(DiscussionTestTags.ADD_OPTION_BUTTON)) {
                          Icon(
                              Icons.Default.Add,
                              contentDescription = "Add",
                              tint = AppColors.textIcons)
                        }
                  }
                }
          }

          Spacer(Modifier.height(Dimensions.Spacing.small))

          // Clickable row for checkbox
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                      .clickable { allowMultiple = !allowMultiple }
                      .padding(vertical = Dimensions.Spacing.small),
              verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allowMultiple,
                    onCheckedChange = {
                      allowMultiple = !allowMultiple
                    }, // Let the row handle the click
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = AppColors.affirmative,
                            uncheckedColor = AppColors.textIconsFade),
                    modifier = Modifier.testTag(DiscussionTestTags.ALLOW_MULTIPLE_CHECKBOX))
                Spacer(Modifier.width(Dimensions.Spacing.small))
                Text(
                    "Allow multiple votes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.textIcons)
              }
        }
      },
      shape = RoundedCornerShape(Dimensions.CornerRadius.large),
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
