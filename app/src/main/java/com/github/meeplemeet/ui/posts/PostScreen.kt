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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.posts.Comment
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.posts.PostViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

/* ================================================================
 * Magic Numbers
 * ================================================================ */
const val ERROR_NOT_SENT_COMMENT: String = "Couldn't send comment. Please try again."
const val ERROR_NOT_DELETED_POST: String = "Couldn't delete post. Please try again."
const val ERROR_SEND_REPLY: String = "Couldn't send reply. Please try again."
const val ERROR_NOT_DELETED_COMMENT: String = "Couldn't delete comment. Please try again."

const val TOPBAR_TITLE: String = "Post"
const val COMMENT_TEXT_ZONE_PLACEHOLDER: String = "Share your thoughts..."
const val REPLY_TEXT_ZONE_PLACEHOLDER: String = "Write a reply…"
const val UNKNOWN_USER_PLACEHOLDER: String = "<Unknown User>"
const val TIMESTAMP_COMMENT_FORMAT: String = "MMM d, yyyy · HH:mm"

/* ================================================================
 * Setups
 * ================================================================ */

private typealias ResolveUser = (String) -> Account?

private object ThreadStyle {
  val Step: Dp = 8.dp
  val GapAfter: Dp = 5.dp
  val Stroke: Dp = 2.dp
  val VerticalInset: Dp = 6.dp
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

/* ================================================================
 * Main screen
 * ================================================================ */

/**
 * Composable function representing the Post screen, displaying a post and its comments.
 *
 * @param account The current user's account information.
 * @param postId The ID of the post to display.
 * @param postViewModel ViewModel for managing post data.
 * @param usersViewModel ViewModel for managing user data.
 * @param onBack Lambda to invoke when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    account: Account,
    postId: String,
    postViewModel: PostViewModel = viewModel(),
    usersViewModel: DiscussionViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
  val post: Post? by postViewModel.postFlow(postId).collectAsState()

  val userCache = remember { mutableStateMapOf<String, Account>() }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val snackbarHostState = remember { SnackbarHostState() }

  var topComment by rememberSaveable { mutableStateOf("") }
  var isSending by remember { mutableStateOf(false) }

  // Track if post was ever loaded to distinguish between loading and deleted states
  var postEverLoaded by remember { mutableStateOf(false) }

  // Auto-navigate back if post is deleted after being loaded
  LaunchedEffect(post) {
    if (post != null) {
      postEverLoaded = true
    } else if (postEverLoaded) {
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
    val toFetch =
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
    toFetch.forEach { uid -> usersViewModel.getOtherAccount(uid) { acc -> userCache[uid] = acc } }
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
        ComposerBar(
            value = topComment,
            onValueChange = { topComment = it },
            onAttach = { /* TODO attachments */},
            sendEnabled = !isSending && topComment.isNotBlank() && post != null,
            onSend = {
              val p = post ?: return@ComposerBar
              scope.launch {
                isSending = true
                try {
                  postViewModel.addComment(
                      author = account, post = p, parentId = p.id, text = topComment.trim())
                  topComment = ""
                  focusManager.clearFocus(force = true)
                } catch (_: Throwable) {
                  snackbarHostState.showSnackbar(ERROR_NOT_SENT_COMMENT)
                } finally {
                  isSending = false
                }
              }
            })
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
              onDeletePost = {
                scope.launch {
                  runCatching { postViewModel.deletePost(account, currentPost) }
                      .onFailure { snackbarHostState.showSnackbar(ERROR_NOT_DELETED_POST) }
                }
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
      navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.testTag(PostTags.NAV_BACK_BTN)) {
          Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
      title = {
        Text(
            text = TOPBAR_TITLE,
            color = MaterialTheme.colorScheme.onBackground,
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
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .imePadding()
              .navigationBarsPadding()
              .padding(horizontal = 12.dp, vertical = 4.dp)
              .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag(PostTags.COMPOSER_BAR),
      verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAttach, modifier = Modifier.testTag(PostTags.COMPOSER_ATTACH)) {
          Icon(Icons.Default.AttachFile, contentDescription = "Attach")
        }
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier.weight(1f)
                    .semantics { contentDescription = "Comment input" }
                    .testTag(PostTags.COMPOSER_INPUT),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
            decorationBox = { inner ->
              if (value.isEmpty())
                  Text(
                      COMMENT_TEXT_ZONE_PLACEHOLDER,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
              inner()
            })
        Spacer(Modifier.width(6.dp))
        IconButton(
            enabled = sendEnabled,
            onClick = onSend,
            modifier = Modifier.testTag(PostTags.COMPOSER_SEND)) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
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
 * @param resolveUser Lambda to resolve a user ID to an Account.
 * @param modifier Modifier to be applied to the content.
 */
@Composable
private fun PostContent(
    post: Post,
    currentUser: Account,
    onDeletePost: () -> Unit,
    onReply: (parentId: String, text: String) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    resolveUser: ResolveUser,
    modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()
  val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

  LazyColumn(
      state = listState,
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(PostTags.LIST),
      contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
          PostCard(
              post = post,
              author = resolveUser(post.authorId),
              currentUser = currentUser,
              onDelete = onDeletePost)
        }

        items(items = post.comments, key = { it.id }, contentType = { "root_comment" }) { root ->
          ThreadCard(
              root = root,
              currentUser = currentUser,
              resolveUser = resolveUser,
              onReply = onReply,
              onDelete = onDeleteComment,
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostCard(post: Post, author: Account?, currentUser: Account, onDelete: () -> Unit) {
  MeepleCard {
    Box(Modifier.fillMaxWidth().testTag(PostTags.postCard(post.id))) {
      Column {
        PostHeader(post = post, author = author)
        Spacer(Modifier.height(8.dp))
        Text(
            text = post.title,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold),
            modifier = Modifier.testTag(PostTags.POST_TITLE))
        Spacer(Modifier.height(12.dp))
        Text(
            text = post.body,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.testTag(PostTags.POST_BODY))

        if (post.tags.isNotEmpty()) {
          Spacer(Modifier.height(12.dp))
          FlowRow(
              modifier = Modifier.testTag(PostTags.POST_TAGS_ROW),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)) {
                post.tags.forEach { tag ->
                  AssistChip(
                      enabled = false,
                      onClick = {},
                      label = { Text(tag) },
                      colors =
                          AssistChipDefaults.assistChipColors(
                              containerColor = MaterialTheme.colorScheme.tertiary,
                              labelColor = MaterialTheme.colorScheme.onBackground,
                              disabledContainerColor = MaterialTheme.colorScheme.tertiary,
                              disabledLabelColor = MaterialTheme.colorScheme.onBackground),
                      modifier = Modifier.testTag(PostTags.tagChip(tag)),
                  )
                }
              }
        }
      }

      if (post.authorId == currentUser.uid) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd).testTag(PostTags.POST_DELETE_BTN)) {
              Icon(Icons.Default.Delete, "Delete post", tint = MaterialTheme.colorScheme.error)
            }
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
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp)
              .testTag(PostTags.POST_HEADER),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clearAndSetSemantics { testTag = PostTags.POST_AVATAR })
        Spacer(Modifier.width(10.dp))
        Column {
          Text(
              text = author?.name ?: UNKNOWN_USER_PLACEHOLDER,
              style =
                  MaterialTheme.typography.labelLarge.copy(
                      color = MaterialTheme.colorScheme.onBackground,
                      fontWeight = FontWeight.SemiBold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag(PostTags.POST_AUTHOR))
          Text(
              text = formatDateTime(post.timestamp),
              style =
                  MaterialTheme.typography.labelSmall.copy(
                      color = MaterialTheme.colorScheme.onSurfaceVariant),
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
 * @param expandedStates Map storing the expanded state for all comments.
 * @param gutterColor The color of the gutter lines.
 */
@Composable
private fun ThreadCard(
    root: Comment,
    currentUser: Account,
    resolveUser: ResolveUser,
    onReply: (parentId: String, text: String) -> Unit,
    onDelete: (Comment) -> Unit,
    expandedStates: MutableMap<String, Boolean>,
    gutterColor: Color = MaterialTheme.colorScheme.outline
) {
  val expanded = expandedStates[root.id] ?: false

  MeepleCard(modifier = Modifier.testTag(PostTags.threadCard(root.id))) {
    CommentItem(
        comment = root,
        author = resolveUser(root.authorId),
        isMine = (root.authorId == currentUser.uid),
        onReply = { text ->
          onReply(root.id, text)
          expandedStates[root.id] = true
        },
        onDelete = { onDelete(root) },
        onCardClick = { expandedStates[root.id] = !expanded })

    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
      if (root.children.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        CommentsTree(
            comments = root.children,
            currentUser = currentUser,
            resolveUser = resolveUser,
            onReply = onReply,
            onDelete = onDelete,
            expandedStates = expandedStates,
            depth = 1,
            gutterColor = gutterColor)
      }
    }
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
 * @param expandedStates Map storing the expanded state for all comments.
 * @param depth The current depth in the comment tree.
 * @param gutterColor The color of the gutter lines.
 */
@Composable
private fun CommentsTree(
    comments: List<Comment>,
    currentUser: Account,
    resolveUser: ResolveUser,
    onReply: (parentId: String, text: String) -> Unit,
    onDelete: (Comment) -> Unit,
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

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        comments.forEach { c ->
          val expanded = expandedStates[c.id] ?: false

          CommentItem(
              comment = c,
              author = resolveUser(c.authorId),
              isMine = (c.authorId == currentUser.uid),
              onReply = { text ->
                onReply(c.id, text)
                expandedStates[c.id] = true
              },
              onDelete = { onDelete(c) },
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
                      onReply = onReply,
                      onDelete = onDelete,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag(PostTags.treeDepth(depth))) {
          comments.forEach { c ->
            CommentItem(
                comment = c,
                author = resolveUser(c.authorId),
                isMine = (c.authorId == currentUser.uid),
                onReply = { text -> onReply(c.id, text) },
                onDelete = { onDelete(c) },
                onCardClick = null)
            if (c.children.isNotEmpty()) {
              CommentsTree(
                  comments = c.children,
                  currentUser = currentUser,
                  resolveUser = resolveUser,
                  onReply = onReply,
                  onDelete = onDelete,
                  expandedStates = expandedStates,
                  depth = 1,
                  gutterColor = gutterColor)
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
              .width(ThreadStyle.Step * depth)
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
 * @param isMine Whether the comment was authored by the current user.
 * @param onReply Lambda to invoke when replying to the comment.
 * @param onDelete Lambda to invoke when deleting the comment.
 * @param onCardClick Optional lambda to invoke when the comment card is clicked.
 */
@Composable
private fun CommentItem(
    comment: Comment,
    author: Account?,
    isMine: Boolean,
    onReply: (String) -> Unit,
    onDelete: () -> Unit,
    onCardClick: (() -> Unit)? = null
) {
  var replying by rememberSaveable(comment.id) { mutableStateOf(false) }
  var replyText by rememberSaveable(comment.id) { mutableStateOf("") }
  val focusManager = LocalFocusManager.current

  val base = Modifier.fillMaxWidth()
  val clickable = onCardClick?.let { base.clickable(onClick = it) } ?: base

  MeepleCard(
      modifier = clickable.testTag(PostTags.commentCard(comment.id)),
      contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
              modifier =
                  Modifier.size(24.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary)
                      .clearAndSetSemantics {})
          Spacer(Modifier.width(8.dp))
          Column(Modifier.weight(1f)) {
            Text(
                text = author?.name ?: UNKNOWN_USER_PLACEHOLDER,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold),
                modifier = Modifier.testTag(PostTags.commentAuthor(comment.id)))
            Text(
                text = formatDateTime(comment.timestamp),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.testTag(PostTags.commentDate(comment.id)))
          }
          if (isMine) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(PostTags.commentDeleteBtn(comment.id))) {
                  Icon(
                      Icons.Default.Delete,
                      contentDescription = "Delete comment",
                      tint = MaterialTheme.colorScheme.error)
                }
          }
          IconButton(
              onClick = { replying = !replying },
              modifier = Modifier.testTag(PostTags.commentReplyToggle(comment.id))) {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply")
              }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = comment.text,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.testTag(PostTags.commentText(comment.id)))

        if (replying) {
          Spacer(Modifier.height(10.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                modifier =
                    Modifier.weight(1f)
                        .semantics { contentDescription = "Reply input" }
                        .testTag(PostTags.commentReplyField(comment.id)),
                placeholder = { Text(REPLY_TEXT_ZONE_PLACEHOLDER) },
                singleLine = true,
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
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    ))
            Spacer(Modifier.width(8.dp))
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
                  Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply")
                }
          }
        }
      }
}

/**
 * Composable representing a styled card used throughout the MeepleMeet app.
 *
 * @param modifier Modifier to be applied to the card.
 * @param contentPadding Padding values for the content inside the card.
 * @param content Composable content to be displayed inside the card.
 */
@Composable
private fun MeepleCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
  Card(
      modifier = modifier,
      shape = MaterialTheme.shapes.large,
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface,
              contentColor = MaterialTheme.colorScheme.onBackground),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
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
