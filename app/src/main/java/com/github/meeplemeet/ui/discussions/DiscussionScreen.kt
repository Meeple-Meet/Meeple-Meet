// Documentation was written with the help of ChatGPT
package com.github.meeplemeet.ui.discussions

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.discussions.Poll
import com.github.meeplemeet.model.images.ImageFileUtils
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.MessagingColors
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

const val MAX_MESSAGE_LENGTH: Int = 4096
const val CHAR_COUNTER_THRESHOLD: Int = 100
const val CHAR_COUNTER_WARNING_THRESHOLD: Int = 20

/** Test-tag constants for the Discussion screen and its poll sub-components. */
object DiscussionTestTags {
  const val INPUT_FIELD = "Input Field"
  const val SEND_BUTTON = "Send Button"
  const val ATTACHMENT_BUTTON = "attachment_button"
  const val ATTACHMENT_POLL_OPTION = "attachment_poll_option"
  const val ATTACHMENT_CAMERA_OPTION = "attachment_camera_option"
  const val ATTACHMENT_GALLERY_OPTION = "attachment_gallery_option"
  const val CHAR_COUNTER = "discussion_char_counter"

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
 * Character counter that appears when approaching the character limit.
 *
 * @param currentLength Current text length.
 * @param maxLength Maximum allowed length.
 * @param testTag Test tag for the counter.
 */
@Composable
fun CharacterCounter(currentLength: Int, maxLength: Int, testTag: String) {
  val remainingChars = maxLength - currentLength
  val showCounter = currentLength > maxLength - CHAR_COUNTER_THRESHOLD

  if (showCounter) {
    Text(
        text = remainingChars.toString(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = Dimensions.TextSize.small,
        fontWeight = FontWeight.Medium,
        color =
            when {
              remainingChars < 0 -> MaterialTheme.colorScheme.error
              remainingChars < CHAR_COUNTER_WARNING_THRESHOLD -> AppColors.warning
              else -> MessagingColors.metadataText
            },
        modifier = Modifier.padding(start = Dimensions.Spacing.small).testTag(testTag))
  }
}

/**
 * Root screen for a single discussion with messaging, photos, and polls.
 *
 * Displays a full-featured discussion interface with:
 * - **Message list**: Scrollable LazyColumn showing all messages (text, photo, poll)
 * - **Text input**: Bottom text field with send button
 * - **Photo attachments**: Camera and gallery integration via attachment menu (NEW)
 * - **Polls**: Create and vote on polls inline
 * - **Real-time updates**: Messages update automatically via StateFlow
 * - **Unread tracking**: Automatically marks messages as read when viewing
 *
 * ## Photo Features (NEW)
 * - Attachment button in bottom bar opens popup menu
 * - Camera option: Captures photo with device camera (requires CAMERA permission)
 * - Gallery option: Picks existing photo from device storage
 * - Photos are cached, uploaded to Firebase Storage, and sent as messages
 * - Photo messages display with inline image preview and fullscreen viewer on click
 * - Optional text caption can accompany photo
 *
 * ## Message Types
 * 1. **Text**: Standard chat bubbles with timestamp
 * 2. **Photo**: Image preview + optional caption with fullscreen zoom on tap (NEW)
 * 3. **Poll**: Interactive voting UI with progress bars and vote counts
 *
 * ## Permissions
 * - **CAMERA**: Required for camera capture (requested at runtime)
 * - **Storage**: Gallery access uses scoped storage (no permission needed on Android 10+)
 *
 * @param account Current logged-in user account.
 * @param discussion The discussion to display and interact with.
 * @param viewModel ViewModel managing discussion state and operations (provides messagesFlow).
 * @param onBack Navigation callback to exit the screen.
 * @param onOpenDiscussionInfo Callback to open discussion details/info bottom sheet.
 * @param onCreateSessionClick Callback to create/join gaming session for this discussion.
 * @see DiscussionViewModel.sendMessageWithPhoto for photo upload logic
 * @see DiscussionViewModel.messagesFlow for real-time message updates
 * @see ImageFileUtils for photo caching utilities
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
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  val sendPhoto: suspend (String) -> Unit = { path ->
    isSending = true
    try {
      viewModel.sendMessageWithPhoto(discussion, account, messageText, context, path)
      messageText = ""
    } catch (e: Exception) {
      snackbarHostState.showSnackbar(
          message = "Failed to send image: ${e.message ?: "Unknown error"}",
          duration = SnackbarDuration.Long)
    } finally {
      isSending = false
    }
  }

  val cameraLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null && !isSending) {
          scope.launch {
            val path = ImageFileUtils.saveBitmapToCache(context, bitmap)
            sendPhoto(path)
          }
        }
      }

  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
      }

  val galleryLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !isSending) {
          scope.launch {
            val path = ImageFileUtils.cacheUriToFile(context, uri)
            sendPhoto(path)
          }
        }
      }

  val discussionState by viewModel.discussionFlow(discussion.uid).collectAsState()
  val messages by viewModel.messagesFlow(discussion.uid).collectAsState()

  LaunchedEffect(discussionState) { discussionState?.let { disc -> discussionName = disc.name } }

  LaunchedEffect(messages) {
    messages
        .map { it.senderId }
        .toSet()
        .toList()
        .let {
          viewModel.getAccounts(it, context) { accounts ->
            accounts.forEach { account -> if (account != null) userCache[account.uid] = account }
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

  Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      containerColor = MessagingColors.messagingBackground) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MessagingColors.messagingBackground)
                    .padding(paddingValues)) {
              TopAppBar(
                  colors =
                      TopAppBarDefaults.topAppBarColors(
                          containerColor = MaterialTheme.colorScheme.surface),
                  title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxSize()
                                .testTag(DiscussionTestTags.discussionInfo(discussion.name))
                                .clickable { onOpenDiscussionInfo(discussion) }) {
                          ProfilePicture(
                              profilePictureUrl = discussion.profilePictureUrl,
                              size = Dimensions.ButtonSize.medium,
                              backgroundColor = AppColors.neutral)
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
                    IconButton(
                        onClick = {
                          viewModel.readDiscussionMessages(account, discussion)
                          onBack()
                        }) {
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
                              discussion.session.participants.contains(account.uid) ->
                              Icons.Default.Games
                          discussion.participants.size > 1 &&
                              (discussion.admins.contains(account.uid) ||
                                  discussion.creatorId == account.uid) -> Icons.Default.LibraryAdd
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
                  val sender =
                      if (!isMine) userCache[message.senderId]?.name ?: "Unknown"
                      else DiscussionCommons.YOU_SENDER_NAME

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
                  val isFirstFromSender =
                      prevMessage?.senderId != message.senderId || showDateHeader

                  when {
                    message.poll != null ->
                        PollBubble(
                            msgIndex = index,
                            poll = message.poll,
                            authorName = sender,
                            currentUserId = account.uid,
                            onVote = { optionIndex, isRemoving ->
                              if (isRemoving) {
                                viewModel.removeVoteFromPollAsync(
                                    discussion.uid, message.uid, account, optionIndex)
                              } else {
                                viewModel.voteOnPollAsync(
                                    discussion.uid, message.uid, account, optionIndex)
                              }
                            },
                            createdAt = message.createdAt.toDate(),
                            showProfilePicture = isLastFromSender)
                    message.photoUrl != null ->
                        PhotoBubble(
                            message,
                            isMine,
                            sender,
                            isLastFromSender,
                            isFirstFromSender,
                            messages,
                            userCache,
                            account.uid)
                    else -> ChatBubble(message, isMine, sender, isLastFromSender, isFirstFromSender)
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
                                      .wrapContentHeight()
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
                                              shape =
                                                  RoundedCornerShape(Dimensions.CornerRadius.large),
                                              color = MessagingColors.messageBubbleOther,
                                              shadowElevation = Dimensions.Elevation.high,
                                              modifier =
                                                  Modifier.wrapContentWidth()
                                                      .padding(
                                                          horizontal = Dimensions.Spacing.small)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement =
                                                        Arrangement.spacedBy(
                                                            Dimensions.Spacing.small),
                                                    modifier =
                                                        Modifier.padding(
                                                            Dimensions.Spacing.small)) {
                                                      IconButton(
                                                          onClick = {
                                                            showAttachmentMenu = false
                                                            val cameraPermissionGranted =
                                                                ContextCompat.checkSelfPermission(
                                                                    context,
                                                                    Manifest.permission.CAMERA) ==
                                                                    PackageManager
                                                                        .PERMISSION_GRANTED
                                                            if (cameraPermissionGranted) {
                                                              cameraLauncher.launch(null)
                                                            } else {
                                                              cameraPermissionLauncher.launch(
                                                                  Manifest.permission.CAMERA)
                                                            }
                                                          },
                                                          modifier =
                                                              Modifier.testTag(
                                                                  DiscussionTestTags
                                                                      .ATTACHMENT_CAMERA_OPTION)) {
                                                            Icon(
                                                                Icons.Default.PhotoCamera,
                                                                contentDescription = "Camera",
                                                                tint = MessagingColors.primaryText,
                                                                modifier =
                                                                    Modifier.size(
                                                                        Dimensions.AvatarSize
                                                                            .medium))
                                                          }
                                                      IconButton(
                                                          onClick = {
                                                            showAttachmentMenu = false
                                                            galleryLauncher.launch("image/*")
                                                          },
                                                          modifier =
                                                              Modifier.testTag(
                                                                  DiscussionTestTags
                                                                      .ATTACHMENT_GALLERY_OPTION)) {
                                                            Icon(
                                                                Icons.Default.Image,
                                                                contentDescription = "Gallery",
                                                                tint = MessagingColors.primaryText,
                                                                modifier =
                                                                    Modifier.size(
                                                                        Dimensions.AvatarSize
                                                                            .medium))
                                                          }
                                                      IconButton(
                                                          onClick = {
                                                            showAttachmentMenu = false
                                                            showPollDialog = true
                                                          },
                                                          modifier =
                                                              Modifier.testTag(
                                                                  DiscussionTestTags
                                                                      .ATTACHMENT_POLL_OPTION)) {
                                                            Icon(
                                                                Icons.Default.Poll,
                                                                contentDescription = "Poll",
                                                                tint = MessagingColors.primaryText,
                                                                modifier =
                                                                    Modifier.size(
                                                                        Dimensions.AvatarSize
                                                                            .medium))
                                                          }
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
                                    onValueChange = {
                                      if (it.length <= MAX_MESSAGE_LENGTH) messageText = it
                                    },
                                    modifier =
                                        Modifier.weight(1f)
                                            .wrapContentHeight()
                                            .testTag(DiscussionTestTags.INPUT_FIELD),
                                    textStyle =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = Dimensions.TextSize.body,
                                            color = MessagingColors.primaryText),
                                    minLines = 1,
                                    maxLines = 5,
                                    decorationBox = { inner ->
                                      if (messageText.isEmpty())
                                          Text(
                                              "Message",
                                              style = MaterialTheme.typography.bodyMedium,
                                              fontSize = Dimensions.TextSize.body,
                                              color = MessagingColors.metadataText)
                                      inner()
                                    })
                                CharacterCounter(
                                    currentLength = messageText.length,
                                    maxLength = MAX_MESSAGE_LENGTH,
                                    testTag = DiscussionTestTags.CHAR_COUNTER)
                              }

                          FloatingActionButton(
                              onClick = {
                                if (messageText.isNotBlank() && !isSending) {
                                  scope.launch {
                                    isSending = true
                                    try {
                                      viewModel.sendMessageToDiscussion(
                                          discussion, account, messageText)
                                      messageText = ""
                                    } catch (e: Exception) {
                                      snackbarHostState.showSnackbar(
                                          message =
                                              "Failed to send message: ${e.message ?: "Unknown error"}",
                                          duration = SnackbarDuration.Long)
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
  val isMine = authorName == DiscussionCommons.YOU_SENDER_NAME
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
            ProfilePicture(
                profilePictureUrl = null,
                size = Dimensions.AvatarSize.small,
                backgroundColor = AppColors.neutral)
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
                        text =
                            DateFormat.format(DiscussionCommons.TIME_FORMAT, createdAt).toString(),
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
            ProfilePicture(
                profilePictureUrl = null,
                size = Dimensions.AvatarSize.small,
                backgroundColor = AppColors.focus)
          } else {
            Spacer(Modifier.width(Dimensions.AvatarSize.small))
          }
        }
      }
}

/** Photo message bubble showing an image with optional caption. */
@Composable
private fun PhotoBubble(
    message: Message,
    isMine: Boolean,
    senderName: String,
    showProfilePicture: Boolean = true,
    showSenderName: Boolean = true,
    allMessages: List<Message> = emptyList(),
    userCache: Map<String, Account> = emptyMap(),
    currentUserId: String = ""
) {
  var showFullImage by remember { mutableStateOf(false) }
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.small),
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
      verticalAlignment = Alignment.Bottom) {
        if (!isMine) {
          if (showProfilePicture) {
            ProfilePicture(
                profilePictureUrl = null,
                size = Dimensions.AvatarSize.small,
                backgroundColor = AppColors.neutral)
          } else {
            Spacer(Modifier.width(Dimensions.AvatarSize.small))
          }
          Spacer(Modifier.width(Dimensions.Spacing.small))
        }

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
                            else MessagingColors.messageBubbleOther)
                    .padding(
                        horizontal = Dimensions.Spacing.small,
                        vertical = Dimensions.Spacing.small)) {
              Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
                if (!isMine && showSenderName) {
                  Text(
                      senderName,
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = Dimensions.TextSize.small,
                      fontWeight = FontWeight.SemiBold,
                      color = MessagingColors.whatsappGreen)
                }

                AsyncImage(
                    model = message.photoUrl,
                    contentDescription = "Photo message",
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                            .background(AppColors.neutral)
                            .clickable { showFullImage = true })

                if (message.content.isNotBlank()) {
                  Text(
                      message.content,
                      style = MaterialTheme.typography.bodyMedium,
                      fontSize = Dimensions.TextSize.body,
                      color = MessagingColors.primaryText)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                  Text(
                      text =
                          DateFormat.format(
                                  DiscussionCommons.TIME_FORMAT, message.createdAt.toDate())
                              .toString(),
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = Dimensions.TextSize.tiny,
                      color = MessagingColors.metadataText)
                }
              }
            }

        if (isMine) {
          Spacer(Modifier.width(Dimensions.Spacing.small))
          if (showProfilePicture) {
            ProfilePicture(
                profilePictureUrl = null,
                size = Dimensions.AvatarSize.small,
                backgroundColor = AppColors.focus)
          } else {
            Spacer(Modifier.width(Dimensions.AvatarSize.small))
          }
        }
      }

  if (showFullImage) {
    FullscreenImageDialog(
        photoUrl = message.photoUrl,
        senderName = senderName,
        sentAt = message.createdAt,
        show = true,
        onDismiss = { showFullImage = false },
        allPhotoMessages = allMessages,
        currentMessage = message,
        userCache = userCache,
        currentUserId = currentUserId)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageDialog(
    photoUrl: String?,
    senderName: String,
    sentAt: Timestamp,
    show: Boolean,
    onDismiss: () -> Unit,
    allPhotoMessages: List<Message> = emptyList(),
    currentMessage: Message? = null,
    userCache: Map<String, Account> = emptyMap(),
    currentUserId: String = ""
) {
  if (!show || photoUrl == null) return

  var currentPhotoMessage by remember(currentMessage) { mutableStateOf(currentMessage) }
  val photoMessages = remember(allPhotoMessages) { allPhotoMessages.filter { it.photoUrl != null } }

  val dateText =
      remember(currentPhotoMessage?.createdAt ?: sentAt) {
        val fmt = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.getDefault())
        fmt.format((currentPhotoMessage?.createdAt ?: sentAt).toDate())
      }

  val currentSenderName =
      remember(currentPhotoMessage, userCache) {
        currentPhotoMessage?.let { msg ->
          if (msg.senderId == currentUserId) DiscussionCommons.YOU_SENDER_NAME
          else userCache[msg.senderId]?.name ?: "Unknown"
        } ?: senderName
      }

  Dialog(
      onDismissRequest = onDismiss,
      properties =
          DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) {
          // Image
          AsyncImage(
              model = currentPhotoMessage?.photoUrl ?: photoUrl,
              contentDescription = null,
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize().clickable { onDismiss() })

          // Top bar overlay
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .statusBarsPadding()
                      .background(
                          Brush.verticalGradient(
                              listOf(
                                  Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.0f))))
                      .align(Alignment.TopCenter)) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                horizontal = Dimensions.Padding.extraLarge,
                                vertical = Dimensions.Spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                      IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White)
                      }

                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSenderName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = dateText,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall)
                          }
                    }
              }

          // Bottom thumbnail bar (like WhatsApp)
          if (photoMessages.isNotEmpty()) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.0f),
                                    Color.Black.copy(alpha = 0.9f))))
                        .align(Alignment.BottomCenter)) {
                  androidx.compose.foundation.lazy.LazyRow(
                      modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.extraLarge),
                      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
                        items(photoMessages.size) { index ->
                          val msg = photoMessages[index]
                          val isSelected =
                              msg.uid == (currentPhotoMessage?.uid ?: currentMessage?.uid)

                          Box(
                              modifier =
                                  Modifier.size(Dimensions.Spacing.xxxxLarge)
                                      .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                                      .border(
                                          width =
                                              if (isSelected) Dimensions.DividerThickness.medium
                                              else Dimensions.CornerRadius.none,
                                          color =
                                              if (isSelected) Color.White else Color.Transparent,
                                          shape =
                                              RoundedCornerShape(Dimensions.CornerRadius.medium))
                                      .clickable { currentPhotoMessage = msg }) {
                                AsyncImage(
                                    model = msg.photoUrl,
                                    contentDescription = "Photo thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize())
                              }
                        }
                      }
                }
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
 * @param showSenderName Whether to show the sender name for this message.
 */
@Composable
fun ChatBubble(
    message: Message,
    isMine: Boolean,
    senderName: String?,
    showProfilePicture: Boolean = true,
    showSenderName: Boolean = true
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.small),
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
      verticalAlignment = Alignment.Bottom) {
        // Profile picture for received messages (on the left)
        if (!isMine) {
          if (showProfilePicture) {
            ProfilePicture(
                profilePictureUrl = null,
                size = Dimensions.AvatarSize.small,
                backgroundColor = AppColors.neutral)
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
                if (senderName != null && !isMine && showSenderName) {
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
                          text =
                              DateFormat.format(
                                      DiscussionCommons.TIME_FORMAT, message.createdAt.toDate())
                                  .toString(),
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
            ProfilePicture(
                profilePictureUrl = null,
                size = Dimensions.AvatarSize.small,
                backgroundColor = AppColors.focus)
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
          FocusableInputField(
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
                  FocusableInputField(
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
