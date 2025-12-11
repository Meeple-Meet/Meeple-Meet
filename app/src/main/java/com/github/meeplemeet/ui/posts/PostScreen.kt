// This file was firstly done by hand and improved bit by bit using ChatGPT-5 Thinking Extend
// Modifications by hand were then done and LLM was used to add test tags
// Copilot was used to generate docstrings
package com.github.meeplemeet.ui.posts

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountViewModel
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.model.discussions.EDIT_MAX_THRESHOLD
import com.github.meeplemeet.model.posts.Comment
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.posts.PostViewModel
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.discussions.CharacterCounter
import com.github.meeplemeet.ui.discussions.ProfilePicture
import com.github.meeplemeet.ui.navigation.EmailVerificationBanner
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.MessagingColors
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

/* ================================================================
 * Magic Numbers
 * ================================================================ */
const val ERROR_NOT_SENT_COMMENT: String = "Couldn't send comment. Please try again."
const val ERROR_NOT_DELETED_POST: String = "Couldn't delete post. Please try again."
const val ERROR_SEND_REPLY: String = "Couldn't send reply. Please try again."
const val ERROR_NOT_DELETED_COMMENT: String = "Couldn't delete comment. Please try again."
const val ERROR_NOT_EDITED_POST: String = "Couldn't edit post. Please try again."

const val BLOCKED_USER_STRING = "Comment from blocked user"

const val TOPBAR_TITLE: String = "Post"
const val COMMENT_TEXT_ZONE_PLACEHOLDER: String = "Share your thoughts..."
const val REPLY_TEXT_ZONE_PLACEHOLDER: String = "Write a reply…"
const val UNKNOWN_USER_PLACEHOLDER: String = "<Unknown User>"
const val TIMESTAMP_COMMENT_FORMAT: String = "MMM d, yyyy · HH:mm"
const val SEE_REPLIES_TEXT: String = "See replies"
const val HIDE_REPLIES_TEXT: String = "Hide replies"
const val MAX_COMMENT_LENGTH: Int = 2048

/* ================================================================
 * Setups and Helpers
 * ================================================================ */

private typealias ResolveUser = (String) -> Account?

private object ThreadStyle {
  val Step: Dp = Dimensions.Spacing.medium
  val GapAfter: Dp = Dimensions.Spacing.small
  val Stroke: Dp = Dimensions.Elevation.medium
  val VerticalInset: Dp = Dimensions.Spacing.small
}

private enum class PostEditTarget {
  TITLE,
  BODY
}

object PostTags {
  const val SCREEN = "post_screen"
  const val TOP_BAR = "post_top_bar"
  const val TOP_BAR_DIVIDER = "post_top_divider"
  const val NAV_BACK_BTN = "post_nav_back_btn"
  const val TOP_TITLE = "post_top_title"
  const val LOADING_BOX = "post_loading_box"
  const val LOADING_SPINNER = "post_loading_spinner"
  const val LIST = "post_list"
  const val COMPOSER_BAR = "post_composer_bar"
  const val COMPOSER_ATTACH = "post_composer_attach_btn"
  const val COMPOSER_INPUT = "post_composer_input"
  const val COMPOSER_SEND = "post_composer_send_btn"
  const val COMPOSER_CHAR_COUNTER = "post_composer_char_counter"

  fun postCard(id: String) = "post_card:$id"

  const val POST_HEADER = "post_header"
  const val POST_AVATAR = "post_avatar"
  const val POST_AUTHOR = "post_author"
  const val POST_DATE = "post_date"
  const val POST_TITLE = "post_title_text"
  const val POST_BODY = "post_body_text"
  const val POST_TAGS_ROW = "post_tags_row"

  fun tagChip(tag: String) = "post_tag_chip:$tag"

  const val POST_DELETE_BTN = "post_delete_btn"
  const val POST_EDIT_BTN = "post_edit_btn"
  const val POST_BODY_EDIT_BTN = "post_body_edit_btn"

  fun threadCard(id: String) = "post_thread_card:$id"

  fun treeDepth(depth: Int) = "post_tree_depth:$depth"

  fun gutterDepth(depth: Int) = "post_gutter_depth:$depth"

  fun commentCard(id: String) = "post_comment_card:$id"

  fun commentAuthor(id: String) = "post_comment_author:$id"

  fun commentDate(id: String) = "post_comment_date:$id"

  fun commentText(id: String) = "post_comment_text:$id"

  fun commentDeleteBtn(id: String) = "post_comment_delete_btn:$id"

  fun commentReplyToggle(id: String) = "post_comment_reply_toggle:$id"

  fun commentReplyField(id: String) = "post_comment_reply_field:$id"

  fun commentReplySend(id: String) = "post_comment_reply_send:$id"
}

/**
 * Composable function to display a row of tags associated with a post.
 *
 * @param tags List of tags to display.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostTagsRow(tags: List<String>) {
  if (tags.isEmpty()) return

  FlowRow(
      modifier = Modifier.testTag(PostTags.POST_TAGS_ROW),
      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraSmall),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraSmall)) {
        tags.forEach { tag ->
          Surface(
              shape = RoundedCornerShape(Dimensions.CornerRadius.small),
              color = MessagingColors.redditBlueBg,
              modifier = Modifier.testTag(PostTags.tagChip(tag))) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = Dimensions.TextSize.tiny,
                    fontWeight = FontWeight.Bold,
                    color = MessagingColors.redditBlue,
                    modifier =
                        Modifier.padding(
                            horizontal = Dimensions.Spacing.medium,
                            vertical = Dimensions.Spacing.small))
              }
        }
      }
  Spacer(Modifier.height(Dimensions.Spacing.medium))
}

/* ================================================================
 * Main screen
 * ================================================================ */

/**
 * Composable function representing the Post screen, displaying a post and its comments.
 *
 * @param account The current user's account information.
 * @param postId The ID of the post to display.
 * @param postViewModel ViewModel for managing post data.
 * @param accountViewModel ViewModel for managing user data.
 * @param onBack Lambda to invoke when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    account: Account,
    postId: String,
    verified: Boolean,
    postViewModel: PostViewModel = viewModel(),
    accountViewModel: AccountViewModel = postViewModel,
    onBack: () -> Unit = {},
    onVerifyClick: () -> Unit = {}
) {
  val post: Post? by postViewModel.postFlow(postId).collectAsState()

  val userCache = remember { mutableStateMapOf<String, Account>() }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  var topComment by rememberSaveable { mutableStateOf("") }
  var isSending by remember { mutableStateOf(false) }
  var isReplyingToComment by remember { mutableStateOf(false) }
  var editTarget by remember { mutableStateOf<PostEditTarget?>(null) }

  // Track if post was ever loaded to distinguish between loading and deleted states
  var postEverLoaded by remember { mutableStateOf(false) }
  var deleted by remember { mutableStateOf(false) }

  // Auto-navigate back if post is deleted after being loaded
  LaunchedEffect(post, deleted) {
    if (post != null) {
      postEverLoaded = true
    } else if (postEverLoaded && !deleted) {
      // Post was loaded but is now null (deleted)
      onBack()
    }
  }

  // Ensure current user is in cache
  LaunchedEffect(account.uid) { userCache[account.uid] = account }

  // Build a stable fingerprint of author IDs
  val authorsKey =
      remember(post) {
        post?.let { p ->
          buildSet {
                add(p.authorId)
                fun walk(list: List<Comment>) {
                  list.forEach { c ->
                    add(c.authorId)
                    if (c.children.isNotEmpty()) walk(c.children)
                  }
                }
                walk(p.comments)
              }
              .sorted()
              .joinToString("|")
        }
      }

  // Prefetch all distinct authors for the current post/comments in one go
  LaunchedEffect(post?.id, authorsKey) {
    val p = post ?: return@LaunchedEffect
    val toFetch: List<String> =
        buildSet {
              add(p.authorId)
              fun walk(list: List<Comment>) {
                list.forEach { c ->
                  add(c.authorId)
                  if (c.children.isNotEmpty()) walk(c.children)
                }
              }
              walk(p.comments)
            }
            .filterNot { it.isBlank() || it in userCache }
    accountViewModel.getAccounts(toFetch, context) {
      it.forEach { acc -> if (acc != null) userCache[acc.uid] = acc }
    }
  }

  Scaffold(
      modifier = Modifier.testTag(PostTags.SCREEN),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        Column(Modifier.testTag(PostTags.TOP_BAR)) {
          PostTopBar(onBack = onBack)
          HorizontalDivider(
              color = MaterialTheme.colorScheme.outline,
              modifier = Modifier.testTag(PostTags.TOP_BAR_DIVIDER))
        }
      },
      bottomBar = {
        if (!isReplyingToComment) {
          if (!verified) {
            EmailVerificationBanner(onVerifyClick = onVerifyClick)
          } else {
            ComposerBar(
                value = topComment,
                onValueChange = { if (it.length <= MAX_COMMENT_LENGTH) topComment = it },
                onAttach = { /* TODO attachments */},
                sendEnabled = !isSending && topComment.isNotBlank() && post != null,
                onSend = {
                  val p = post ?: return@ComposerBar

                  scope.launch {
                    isSending = true
                    val target = editTarget

                    try {
                      if (target != null) {
                        val value = topComment.trim()
                        when (target) {
                          PostEditTarget.TITLE ->
                              postViewModel.editPost(author = account, post = p, newTitle = value)
                          PostEditTarget.BODY ->
                              postViewModel.editPost(author = account, post = p, newBody = value)
                        }
                        editTarget = null
                      } else {
                        postViewModel.addComment(
                            author = account, post = p, parentId = p.id, text = topComment.trim())
                      }

                      topComment = ""
                      focusManager.clearFocus(force = true)
                    } catch (_: Throwable) {
                      snackbarHostState.showSnackbar(
                          if (target != null) ERROR_NOT_EDITED_POST else ERROR_NOT_SENT_COMMENT)
                    } finally {
                      isSending = false
                    }
                  }
                })
          }
        }
      }) { padding ->
        if (post == null) {
          Box(
              Modifier.fillMaxSize().padding(padding).testTag(PostTags.LOADING_BOX),
              contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag(PostTags.LOADING_SPINNER))
              }
        } else {
          val currentPost = post!!
          PostContent(
              post = currentPost,
              currentUser = account,
              verified = verified,
              onDeletePost = {
                scope.launch {
                  deleted = true
                  runCatching { postViewModel.deletePost(account, currentPost) }
                      .onFailure { snackbarHostState.showSnackbar(ERROR_NOT_DELETED_POST) }
                  onBack()
                }
              },
              onEditPostBody = {
                topComment = currentPost.body
                editTarget = PostEditTarget.BODY
              },
              onEditPostTitle = {
                topComment = currentPost.title
                editTarget = PostEditTarget.TITLE
              },
              onReply = { parentId, text ->
                scope.launch {
                  runCatching { postViewModel.addComment(account, currentPost, parentId, text) }
                      .onFailure { snackbarHostState.showSnackbar(ERROR_SEND_REPLY) }
                }
              },
              onDeleteComment = { comment ->
                scope.launch {
                  runCatching { postViewModel.removeComment(account, currentPost, comment) }
                      .onFailure { snackbarHostState.showSnackbar(ERROR_NOT_DELETED_COMMENT) }
                }
              },
              onReplyingStateChanged = { _, isReplying -> isReplyingToComment = isReplying },
              resolveUser = { uid -> userCache[uid] },
              modifier = Modifier.padding(padding))
        }
      }
}

/* ================================================================
 * Composables
 * ================================================================ */

/**
 * Top app bar for the Post screen, with a back button and title.
 *
 * @param onBack Lambda to invoke when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostTopBar(onBack: () -> Unit) {
  CenterAlignedTopAppBar(
      colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.testTag(PostTags.NAV_BACK_BTN)) {
          Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onSurface)
        }
      },
      title = {
        Text(
            text = TOPBAR_TITLE,
            style = MaterialTheme.typography.titleLarge,
            fontSize = Dimensions.TextSize.largeHeading,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(PostTags.TOP_TITLE))
      })
}

/**
 * Comment composer bar at the bottom of the Post screen.
 *
 * @param value The current text input value.
 * @param onValueChange Lambda to invoke when the text input changes.
 * @param onAttach Lambda to invoke when the attach button is pressed.
 * @param sendEnabled Whether the send button is enabled.
 * @param onSend Lambda to invoke when the send button is pressed.
 */
@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit
) {
  Surface(
      modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = Dimensions.Elevation.medium) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.Spacing.medium,
                        vertical = Dimensions.Spacing.medium)
                    .testTag(PostTags.COMPOSER_BAR),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
              Row(
                  modifier =
                      Modifier.weight(1f)
                          .wrapContentHeight()
                          .clip(RoundedCornerShape(Dimensions.AvatarSize.tiny))
                          .background(MessagingColors.inputBackground)
                          .padding(
                              horizontal = Dimensions.Padding.large,
                              vertical = Dimensions.Spacing.medium),
                  verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onAttach,
                        modifier =
                            Modifier.size(Dimensions.ButtonSize.medium)
                                .testTag(PostTags.COMPOSER_ATTACH)) {
                          Icon(
                              Icons.Default.AttachFile,
                              contentDescription = "Attach",
                              tint = MessagingColors.metadataText,
                              modifier = Modifier.size(Dimensions.IconSize.standard))
                        }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier =
                            Modifier.weight(1f)
                                .wrapContentHeight()
                                .semantics { contentDescription = "Comment input" }
                                .testTag(PostTags.COMPOSER_INPUT),
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = Dimensions.TextSize.body,
                                color = MessagingColors.primaryText),
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
                        decorationBox = { inner ->
                          if (value.isEmpty())
                              Text(
                                  COMMENT_TEXT_ZONE_PLACEHOLDER,
                                  style = MaterialTheme.typography.bodyMedium,
                                  fontSize = Dimensions.TextSize.body,
                                  color = MessagingColors.metadataText)
                          inner()
                        })
                    CharacterCounter(
                        currentLength = value.length,
                        maxLength = MAX_COMMENT_LENGTH,
                        testTag = PostTags.COMPOSER_CHAR_COUNTER)
                  }

              FloatingActionButton(
                  onClick = { if (sendEnabled) onSend() },
                  modifier =
                      Modifier.size(Dimensions.ButtonSize.standard).testTag(PostTags.COMPOSER_SEND),
                  containerColor = MessagingColors.redditOrange,
                  contentColor = MessagingColors.messagingSurface,
                  shape = CircleShape) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(Dimensions.IconSize.standard))
                  }
            }
      }
}

/**
 * Composable displaying the content of a post along with its comments.
 *
 * @param post The post to display.
 * @param currentUser The current user's account information.
 * @param onDeletePost Lambda to invoke when deleting the post.
 * @param onReply Lambda to invoke when replying to a comment.
 * @param onDeleteComment Lambda to invoke when deleting a comment.
 * @param onReplyingStateChanged Lambda to notify when reply field focus changes.
 * @param resolveUser Lambda to resolve a user ID to an Account.
 * @param modifier Modifier to be applied to the content.
 * @param onEditPostBody Lambda to invoke when editing the post body.
 * @param onEditPostTitle Lambda to invoke when editing the post title.
 */
@Composable
private fun PostContent(
    post: Post,
    currentUser: Account,
    verified: Boolean,
    onDeletePost: () -> Unit,
    onEditPostBody: () -> Unit,
    onEditPostTitle: () -> Unit,
    onReply: (parentId: String, text: String) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onReplyingStateChanged: (commentId: String, isReplying: Boolean) -> Unit,
    resolveUser: ResolveUser,
    modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()
  val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
  val focusManager = LocalFocusManager.current

  val canEditPost =
      post.authorId == currentUser.uid &&
          System.currentTimeMillis() <= post.timestamp.toDate().time + EDIT_MAX_THRESHOLD

  LazyColumn(
      state = listState,
      modifier =
          modifier
              .fillMaxSize()
              .background(MessagingColors.messagingBackground)
              .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
              .testTag(PostTags.LIST),
      contentPadding = PaddingValues(vertical = Dimensions.Spacing.none),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.none)) {
        item {
          PostCard(
              post = post,
              author = resolveUser(post.authorId),
              currentUser = currentUser,
              canEdit = canEditPost,
              onDelete = onDeletePost,
              onEditBody = onEditPostBody,
              onEditTitle = onEditPostTitle)
        }

        items(items = post.comments, key = { it.id }, contentType = { "root_comment" }) { root ->
          ThreadCard(
              root = root,
              currentUser = currentUser,
              verified = verified,
              resolveUser = resolveUser,
              onReply = onReply,
              onDelete = onDeleteComment,
              onReplyingStateChanged = onReplyingStateChanged,
              expandedStates = expandedStates)
        }
      }
}

/**
 * Composable displaying a card for a post, including title, body, tags, and author info.
 *
 * @param post The post to display.
 * @param author The author of the post.
 * @param currentUser The current user's account information.
 * @param onDelete Lambda to invoke when deleting the post.
 * @param onEditBody Lambda to invoke when editing the post body.
 * @param onEditTitle Lambda to invoke when editing the post title.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostCard(
    post: Post,
    author: Account?,
    currentUser: Account,
    canEdit: Boolean,
    onDelete: () -> Unit,
    onEditBody: () -> Unit,
    onEditTitle: () -> Unit
) {
  val isOwner = post.authorId == currentUser.uid
  val showEdit = canEdit && isOwner

  Column(modifier = Modifier.fillMaxWidth()) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MessagingColors.messagingSurface,
        shadowElevation = Dimensions.Elevation.minimal) {
          Box(modifier = Modifier.fillMaxWidth().testTag(PostTags.postCard(post.id))) {
            Column(modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.large)) {
              // Header
              PostHeader(post = post, author = author)

              Spacer(Modifier.height(Dimensions.Padding.large))

              // Tags row
              PostTagsRow(post.tags)

              // Title row
              PostEditableRow(
                  editVisible = showEdit,
                  editTestTag = PostTags.POST_EDIT_BTN,
                  editContentDescription = "Edit title",
                  verticalAlignment = Alignment.CenterVertically,
                  onEdit = onEditTitle) {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = Dimensions.TextSize.heading,
                        fontWeight = FontWeight.Bold,
                        color = MessagingColors.primaryText,
                        modifier = Modifier.weight(1f).testTag(PostTags.POST_TITLE))
                  }

              Spacer(Modifier.height(Dimensions.Padding.large))

              // Body row
              PostEditableRow(
                  editVisible = showEdit,
                  editTestTag = PostTags.POST_BODY_EDIT_BTN,
                  editContentDescription = "Edit body",
                  verticalAlignment = Alignment.Top,
                  editYOffset = -Dimensions.Spacing.extraLarge,
                  onEdit = onEditBody) {
                    Text(
                        text = post.body,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = Dimensions.TextSize.body,
                        color = MessagingColors.primaryText,
                        modifier = Modifier.weight(1f).testTag(PostTags.POST_BODY))
                  }

              Spacer(Modifier.height(Dimensions.Padding.large))

              // Bottom action row
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(Dimensions.Padding.extraLarge),
                  verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small)) {
                          Icon(
                              imageVector = Icons.Outlined.ChatBubbleOutline,
                              contentDescription = "Comments",
                              modifier = Modifier.size(Dimensions.IconSize.medium),
                              tint = MessagingColors.secondaryText)
                          Text(
                              text =
                                  "${post.commentCount} ${if (post.commentCount == 1) "comment" else "comments"}",
                              style = MaterialTheme.typography.bodySmall,
                              fontSize = Dimensions.TextSize.medium,
                              fontWeight = FontWeight.Medium,
                              color = MessagingColors.secondaryText)
                        }
                  }
            }

            // Top-right delete button
            if (isOwner) {
              IconButton(
                  onClick = onDelete,
                  modifier =
                      Modifier.align(Alignment.TopEnd)
                          .padding(top = Dimensions.Spacing.small, end = Dimensions.Padding.large)
                          .testTag(PostTags.POST_DELETE_BTN)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete post",
                        tint = MessagingColors.redditOrange,
                        modifier = Modifier.size(Dimensions.IconSize.standard))
                  }
            }
          }
        }

    HorizontalDivider(
        color = MessagingColors.divider, thickness = Dimensions.DividerThickness.thick)
  }
}

/**
 * Composable displaying a row with optional edit button.
 *
 * @param editVisible Whether the edit button should be visible.
 * @param editTestTag Test tag for the edit button.
 * @param editContentDescription Content description for the edit button.
 * @param verticalAlignment Vertical alignment for the row content.
 * @param editYOffset Vertical offset for the edit button.
 * @param onEdit Lambda to invoke when the edit button is pressed.
 * @param content Composable content to display in the row.
 */
@Composable
private fun PostEditableRow(
    editVisible: Boolean,
    editTestTag: String,
    editContentDescription: String,
    verticalAlignment: Alignment.Vertical,
    editYOffset: Dp = Dp.Hairline,
    onEdit: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = verticalAlignment) {
    content()

    if (editVisible) {
      IconButton(
          onClick = onEdit, modifier = Modifier.offset(y = editYOffset).testTag(editTestTag)) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = editContentDescription,
                tint = MessagingColors.secondaryText,
                modifier = Modifier.size(Dimensions.IconSize.standard))
          }
    }
  }
}

/**
 * Composable displaying the header of a post, including the author's avatar, name, and timestamp.
 *
 * @param post The post whose header is to be displayed.
 * @param author The author of the post.
 */
@Composable
private fun PostHeader(post: Post, author: Account?) {
  Row(
      modifier = Modifier.fillMaxWidth().testTag(PostTags.POST_HEADER),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
        ProfilePicture(
            profilePictureUrl = author?.photoUrl,
            size = Dimensions.AvatarSize.small,
            backgroundColor = MessagingColors.redditOrange,
            modifier = Modifier.clearAndSetSemantics { testTag = PostTags.POST_AVATAR })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraSmall)) {
              Text(
                  text = author?.name ?: UNKNOWN_USER_PLACEHOLDER,
                  style = MaterialTheme.typography.labelMedium,
                  fontSize = Dimensions.TextSize.medium,
                  fontWeight = FontWeight.SemiBold,
                  color = MessagingColors.primaryText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.testTag(PostTags.POST_AUTHOR))
              Text(
                  text = "•",
                  fontSize = Dimensions.TextSize.medium,
                  color = MessagingColors.secondaryText)
              Text(
                  text = formatDateTime(post.timestamp),
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = Dimensions.TextSize.medium,
                  color = MessagingColors.secondaryText,
                  modifier = Modifier.testTag(PostTags.POST_DATE))
            }
      }
}

/**
 * Composable displaying a comment thread card with expandable replies.
 *
 * @param root The root comment of the thread.
 * @param currentUser The current user's account information.
 * @param resolveUser Lambda to resolve a user ID to an Account.
 * @param onReply Lambda to invoke when replying to a comment.
 * @param onDelete Lambda to invoke when deleting a comment.
 * @param onReplyingStateChanged Lambda to notify when reply field focus changes.
 * @param expandedStates Map storing the expanded state for all comments.
 */
@Composable
private fun ThreadCard(
    root: Comment,
    currentUser: Account,
    verified: Boolean,
    resolveUser: ResolveUser,
    onReply: (parentId: String, text: String) -> Unit,
    onDelete: (Comment) -> Unit,
    onReplyingStateChanged: (commentId: String, isReplying: Boolean) -> Unit,
    expandedStates: MutableMap<String, Boolean>
) {
  val expanded = expandedStates[root.id] ?: false

  Column(modifier = Modifier.fillMaxWidth()) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MessagingColors.messagingSurface,
        shadowElevation = Dimensions.Elevation.none) {
          Column(
              modifier =
                  Modifier.testTag(PostTags.threadCard(root.id))
                      .padding(
                          top = Dimensions.Padding.large,
                          start = Dimensions.Padding.large,
                          end = Dimensions.Padding.large,
                          bottom =
                              if (root.children.isNotEmpty()) Dimensions.Padding.small
                              else Dimensions.Padding.large)) {
                CommentItem(
                    comment = root,
                    author = resolveUser(root.authorId),
                    verified = verified,
                    currentUser = currentUser,
                    isMine = (root.authorId == currentUser.uid),
                    hasReplies = root.children.isNotEmpty(),
                    isExpanded = expanded,
                    onReply = { text ->
                      onReply(root.id, text)
                      expandedStates[root.id] = true
                    },
                    onDelete = { onDelete(root) },
                    onReplyingStateChanged = onReplyingStateChanged,
                    onCardClick = { expandedStates[root.id] = !expanded })

                AnimatedVisibility(
                    visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                      if (root.children.isNotEmpty()) {
                        Spacer(Modifier.height(Dimensions.Spacing.medium))
                        CommentsTree(
                            comments = root.children,
                            currentUser = currentUser,
                            verified = verified,
                            resolveUser = resolveUser,
                            onReply = onReply,
                            onDelete = onDelete,
                            onReplyingStateChanged = onReplyingStateChanged,
                            expandedStates = expandedStates,
                            depth = 1,
                            gutterColor = MessagingColors.redditOrange)
                      }
                    }
              }
        }
    HorizontalDivider(
        color = MessagingColors.divider, thickness = Dimensions.DividerThickness.standard)
  }
}

/**
 * Recursive composable displaying a tree of comments with indentation and gutters.
 *
 * @param comments List of comments to display.
 * @param currentUser The current user's account information.
 * @param resolveUser Lambda to resolve a user ID to an Account.
 * @param onReply Lambda to invoke when replying to a comment.
 * @param onDelete Lambda to invoke when deleting a comment.
 * @param onReplyingStateChanged Lambda to notify when reply field focus changes.
 * @param expandedStates Map storing the expanded state for all comments.
 * @param depth The current depth in the comment tree.
 * @param gutterColor The color of the gutter lines.
 */
@Composable
private fun CommentsTree(
    comments: List<Comment>,
    currentUser: Account,
    verified: Boolean,
    resolveUser: ResolveUser,
    onReply: (parentId: String, text: String) -> Unit,
    onDelete: (Comment) -> Unit,
    onReplyingStateChanged: (commentId: String, isReplying: Boolean) -> Unit,
    expandedStates: MutableMap<String, Boolean>,
    depth: Int = 0,
    gutterColor: Color = MaterialTheme.colorScheme.outline
) {
  if (depth > 0) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag(PostTags.treeDepth(depth))) {
      ThreadGutter(
          depth = depth,
          color = gutterColor,
          modifier = Modifier.fillMaxHeight().testTag(PostTags.gutterDepth(depth)))
      Spacer(Modifier.width(ThreadStyle.GapAfter))

      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(Dimensions.Padding.large)) {
            comments.forEach { c ->
              val expanded = expandedStates[c.id] ?: false

              CommentItem(
                  comment = c,
                  author = resolveUser(c.authorId),
                  verified = verified,
                  currentUser = currentUser,
                  isMine = (c.authorId == currentUser.uid),
                  hasReplies = c.children.isNotEmpty(),
                  isExpanded = expanded,
                  onReply = { text ->
                    onReply(c.id, text)
                    expandedStates[c.id] = true
                  },
                  onDelete = { onDelete(c) },
                  onReplyingStateChanged = onReplyingStateChanged,
                  onCardClick =
                      if (c.children.isNotEmpty()) {
                        { expandedStates[c.id] = !expanded }
                      } else null)

              if (c.children.isNotEmpty()) {
                AnimatedVisibility(
                    visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                      CommentsTree(
                          comments = c.children,
                          currentUser = currentUser,
                          resolveUser = resolveUser,
                          verified = verified,
                          onReply = onReply,
                          onDelete = onDelete,
                          onReplyingStateChanged = onReplyingStateChanged,
                          expandedStates = expandedStates,
                          depth = depth + 1,
                          gutterColor = gutterColor)
                    }
              }
            }
          }
    }
  } else {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimensions.Padding.large),
        modifier = Modifier.testTag(PostTags.treeDepth(depth))) {
          comments.forEach { c ->
            val expanded = expandedStates[c.id] ?: false

            CommentItem(
                comment = c,
                author = resolveUser(c.authorId),
                verified = verified,
                currentUser = currentUser,
                isMine = (c.authorId == currentUser.uid),
                hasReplies = c.children.isNotEmpty(),
                isExpanded = expanded,
                onReply = { text -> onReply(c.id, text) },
                onDelete = { onDelete(c) },
                onReplyingStateChanged = onReplyingStateChanged,
                onCardClick =
                    if (c.children.isNotEmpty()) {
                      { expandedStates[c.id] = !expanded }
                    } else null)
            if (c.children.isNotEmpty()) {
              AnimatedVisibility(
                  visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                    CommentsTree(
                        comments = c.children,
                        currentUser = currentUser,
                        verified = verified,
                        resolveUser = resolveUser,
                        onReply = onReply,
                        onDelete = onDelete,
                        onReplyingStateChanged = onReplyingStateChanged,
                        expandedStates = expandedStates,
                        depth = 1,
                        gutterColor = gutterColor)
                  }
            }
          }
        }
  }
}

/**
 * Composable displaying the gutter lines for comment threading based on depth.
 *
 * @param depth The depth of the comment in the thread.
 * @param modifier Modifier to be applied to the gutter.
 * @param color The color of the gutter lines.
 */
@Composable
private fun ThreadGutter(
    depth: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
  if (depth <= 0) return

  val stepPx = with(LocalDensity.current) { ThreadStyle.Step.toPx() }
  val strokePx = with(LocalDensity.current) { ThreadStyle.Stroke.toPx() }

  Box(
      modifier =
          modifier
              .width(ThreadStyle.Step)
              .padding(vertical = ThreadStyle.VerticalInset)
              .drawBehind {
                val x = size.width - stepPx / 2f
                drawLine(
                    color = color,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokePx)
              })
}

/**
 * Composable displaying an individual comment item with reply and delete options.
 *
 * @param comment The comment to display.
 * @param author The author of the comment.
 * @param currentUser The current user's account information.
 * @param isMine Whether the comment was authored by the current user.
 * @param hasReplies Whether this comment has child replies.
 * @param isExpanded Whether the replies are currently visible.
 * @param onReply Lambda to invoke when replying to the comment.
 * @param onDelete Lambda to invoke when deleting the comment.
 * @param onReplyingStateChanged Lambda to notify when reply field focus changes.
 * @param onCardClick Optional lambda to invoke when the comment card is clicked.
 */
@Composable
private fun CommentItem(
    comment: Comment,
    author: Account?,
    currentUser: Account,
    isMine: Boolean,
    verified: Boolean,
    hasReplies: Boolean = false,
    isExpanded: Boolean = false,
    onReply: (String) -> Unit,
    onDelete: () -> Unit,
    onReplyingStateChanged: (commentId: String, isReplying: Boolean) -> Unit,
    onCardClick: (() -> Unit)? = null
) {
  var replying by rememberSaveable(comment.id) { mutableStateOf(false) }
  var replyText by rememberSaveable(comment.id) { mutableStateOf("") }
  val focusManager = LocalFocusManager.current

  val isBlocked = currentUser.relationships[comment.authorId] == RelationshipStatus.BLOCKED

  val clickMod = if (onCardClick != null) Modifier.clickable(onClick = onCardClick) else Modifier
  Column(modifier = clickMod.fillMaxWidth().testTag(PostTags.commentCard(comment.id))) {
    // Comment header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraSmall)) {
          ProfilePicture(
              profilePictureUrl = if (isBlocked) null else author?.photoUrl,
              size = Dimensions.AvatarSize.tiny,
              backgroundColor = MessagingColors.redditOrange,
              modifier = Modifier.clearAndSetSemantics {})
          Text(
              text = if (isBlocked) "Blocked User" else author?.name ?: UNKNOWN_USER_PLACEHOLDER,
              style = MaterialTheme.typography.labelMedium,
              fontSize = Dimensions.TextSize.small,
              fontWeight = FontWeight.SemiBold,
              color = MessagingColors.primaryText,
              modifier = Modifier.testTag(PostTags.commentAuthor(comment.id)))
          if (!isBlocked) {
            Text(
                text = "•",
                fontSize = Dimensions.TextSize.small,
                color = MessagingColors.secondaryText)
            Text(
                text = formatDateTime(comment.timestamp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = Dimensions.TextSize.small,
                color = MessagingColors.secondaryText,
                modifier = Modifier.testTag(PostTags.commentDate(comment.id)))
          }
          Spacer(Modifier.weight(1f))
          if (isMine) {
            IconButton(
                onClick = onDelete,
                modifier =
                    Modifier.size(Dimensions.AvatarSize.small)
                        .testTag(PostTags.commentDeleteBtn(comment.id))) {
                  Icon(
                      Icons.Default.Delete,
                      contentDescription = "Delete comment",
                      tint = MessagingColors.redditOrange,
                      modifier = Modifier.size(Dimensions.IconSize.medium))
                }
          }
          if (verified && !isBlocked)
              IconButton(
                  onClick = { replying = !replying },
                  modifier =
                      Modifier.size(Dimensions.AvatarSize.small)
                          .testTag(PostTags.commentReplyToggle(comment.id))) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = MessagingColors.secondaryText,
                        modifier = Modifier.size(Dimensions.IconSize.medium))
                  }
        }

    Spacer(Modifier.height(Dimensions.Spacing.medium))

    // Comment text
    if (isBlocked) {
      Text(
          text = BLOCKED_USER_STRING,
          style = MaterialTheme.typography.bodyMedium,
          fontSize = Dimensions.TextSize.standard,
          color = MessagingColors.secondaryText,
          fontStyle = FontStyle.Italic,
          modifier = Modifier.testTag(PostTags.commentText(comment.id)))
    } else {
      Text(
          text = comment.text,
          style = MaterialTheme.typography.bodyMedium,
          fontSize = Dimensions.TextSize.standard,
          color = MessagingColors.primaryText,
          modifier = Modifier.testTag(PostTags.commentText(comment.id)))
    }

    // "See replies" button when there are replies
    if (hasReplies) {
      Spacer(Modifier.height(Dimensions.Spacing.extraSmall))
      TextButton(
          onClick = { onCardClick?.invoke() },
          modifier = Modifier.padding(start = Dimensions.Spacing.none)) {
            Icon(
                imageVector =
                    if (isExpanded) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (isExpanded) HIDE_REPLIES_TEXT else SEE_REPLIES_TEXT,
                modifier = Modifier.size(Dimensions.IconSize.small),
                tint = MessagingColors.redditOrange)
            Spacer(Modifier.width(Dimensions.Spacing.small))
            Text(
                text = if (isExpanded) HIDE_REPLIES_TEXT else SEE_REPLIES_TEXT,
                style = MaterialTheme.typography.labelMedium,
                fontSize = Dimensions.TextSize.medium,
                fontWeight = FontWeight.Bold,
                color = MessagingColors.redditOrange)
          }
    }

    // Reply field
    if (replying) {
      Spacer(Modifier.height(Dimensions.Spacing.extraSmall))
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
            FocusableInputField(
                value = replyText,
                onValueChange = { if (it.length <= MAX_COMMENT_LENGTH) replyText = it },
                modifier =
                    Modifier.weight(1f)
                        .onFocusChanged { focusState ->
                          onReplyingStateChanged(comment.id, focusState.isFocused)
                        }
                        .semantics { contentDescription = "Reply input" }
                        .testTag(PostTags.commentReplyField(comment.id)),
                placeholder = {
                  Text(
                      REPLY_TEXT_ZONE_PLACEHOLDER,
                      fontSize = Dimensions.TextSize.standard,
                      color = MessagingColors.secondaryText)
                },
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = Dimensions.TextSize.standard),
                singleLine = true,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(
                        onSend = {
                          val text = replyText.trim()
                          if (text.isNotEmpty()) {
                            onReply(text)
                            replyText = ""
                            replying = false
                            focusManager.clearFocus(force = true)
                          }
                        }),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent))
            IconButton(
                enabled = replyText.isNotBlank(),
                onClick = {
                  val text = replyText.trim()
                  if (text.isNotEmpty()) {
                    onReply(text)
                    replyText = ""
                    replying = false
                    focusManager.clearFocus(force = true)
                  }
                },
                modifier = Modifier.testTag(PostTags.commentReplySend(comment.id))) {
                  Icon(
                      Icons.AutoMirrored.Filled.Send,
                      contentDescription = "Send reply",
                      tint = MessagingColors.redditOrange)
                }
          }
    }
  }
}

/**
 * Formats a Firebase Timestamp into a human-readable date and time string.
 *
 * @param ts The Timestamp to format.
 * @return A formatted date and time string.
 */
private fun formatDateTime(ts: Timestamp): String {
  val d = ts.toDate()
  return DateFormat.format(TIMESTAMP_COMMENT_FORMAT, d).toString()
}
