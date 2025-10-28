// This file was firstly done by hand and improved bit by bit using ChatGPT-5 Thinking Extend
// Modifications by hand were then done and LLM was used to add test tags
// Copilot was used to generate docstrings
package com.github.meeplemeet.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.structures.*
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.model.viewmodels.PostViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

/* ================================================================
 * Types & Styling
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
 * Composables
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
    usersViewModel: FirestoreViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
  val post: Post? by postViewModel.postFlow(postId).collectAsState()

  val userCache = remember { mutableStateMapOf<String, Account>() }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current

  var topComment by rememberSaveable { mutableStateOf("") }
  var isSending by remember { mutableStateOf(false) }

  LaunchedEffect(account.uid) { userCache[account.uid] = account }

  LaunchedEffect(post?.id, post?.comments?.hashCode()) {
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
          PostContent(
              post = post!!,
              currentUser = account,
              onDeletePost = {
                postViewModel.deletePost(account, post!!)
                onBack()
              },
              onReply = { parentId, text ->
                postViewModel.addComment(account, post!!, parentId, text)
              },
              onDeleteComment = { comment ->
                postViewModel.removeComment(account, post!!, comment)
              },
              resolveUser = { uid -> userCache[uid] },
              modifier = Modifier.padding(padding))
        }
      }
}

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
            text = "Post",
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(PostTags.TOP_TITLE))
      })
}

/**
 * Bottom composer bar for writing and sending comments.
 *
 * @param value The current text in the input field.
 * @param onValueChange Lambda to invoke when the input text changes.
 * @param onAttach Lambda to invoke when the attach button is pressed.
 * @param sendEnabled Boolean indicating if the send button should be enabled.
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
            decorationBox = { inner ->
              if (value.isEmpty())
                  Text("Share your thoughts...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * @param currentUser The currently logged-in user.
 * @param onDeletePost Lambda to invoke when the post is deleted.
 * @param onReply Lambda to invoke when a reply is made to a comment.
 * @param onDeleteComment Lambda to invoke when a comment is deleted.
 * @param resolveUser Function to resolve a user ID to an Account object.
 * @param modifier Modifier to be applied to the LazyColumn.
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

        items(post.comments, key = { it.id }) { root ->
          ThreadCard(
              root = root,
              currentUser = currentUser,
              resolveUser = resolveUser,
              onReply = onReply,
              onDelete = onDeleteComment)
        }
      }
}

/**
 * Composable displaying a post card with its header, title, body, tags, and delete option.
 *
 * @param post The post to display.
 * @param author The author of the post.
 * @param currentUser The currently logged-in user.
 * @param onDelete Lambda to invoke when the post is deleted.
 */
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
              horizontalGap = 8.dp,
              verticalGap = 8.dp,
              modifier = Modifier.testTag(PostTags.POST_TAGS_ROW)) {
                post.tags.forEach { tag ->
                  AssistChip(
                      onClick = {},
                      label = { Text("#$tag") },
                      colors =
                          AssistChipDefaults.assistChipColors(
                              containerColor = MaterialTheme.colorScheme.tertiary,
                              labelColor = MaterialTheme.colorScheme.onBackground),
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
                    .testTag(PostTags.POST_AVATAR))
        Spacer(Modifier.width(10.dp))
        Column {
          Text(
              text = author?.handle ?: "<Username>",
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
 * @param currentUser The currently logged-in user.
 * @param resolveUser Function to resolve a user ID to an Account object.
 * @param onReply Lambda to invoke when a reply is made to the root comment.
 * @param onDelete Lambda to invoke when the root comment is deleted.
 * @param gutterColor Color of the gutter lines indicating comment depth.
 */
@Composable
private fun ThreadCard(
    root: Comment,
    currentUser: Account,
    resolveUser: ResolveUser,
    onReply: (parentId: String, text: String) -> Unit,
    onDelete: (Comment) -> Unit,
    gutterColor: Color = MaterialTheme.colorScheme.outline
) {
  var expanded by rememberSaveable(root.id) { mutableStateOf(false) }

  MeepleCard(modifier = Modifier.testTag(PostTags.threadCard(root.id))) {
    CommentItem(
        comment = root,
        author = resolveUser(root.authorId),
        isMine = (root.authorId == currentUser.uid),
        onReply = { text -> onReply(root.id, text) },
        onDelete = { onDelete(root) },
        onCardClick = { expanded = !expanded })

    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
      if (root.children.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        CommentsTree(
            comments = root.children,
            currentUser = currentUser,
            resolveUser = resolveUser,
            onReply = onReply,
            onDelete = onDelete,
            depth = 1,
            gutterColor = gutterColor)
      }
    }
  }
}

/**
 * Recursive composable displaying a tree of comments with indentation and gutters.
 *
 * @param comments The list of comments to display at the current depth.
 * @param currentUser The currently logged-in user.
 * @param resolveUser Function to resolve a user ID to an Account object.
 * @param onReply Lambda to invoke when a reply is made to a comment.
 * @param onDelete Lambda to invoke when a comment is deleted.
 * @param depth The current depth in the comment tree.
 * @param gutterColor Color of the gutter lines indicating comment depth.
 */
@Composable
private fun CommentsTree(
    comments: List<Comment>,
    currentUser: Account,
    resolveUser: ResolveUser,
    onReply: (parentId: String, text: String) -> Unit,
    onDelete: (Comment) -> Unit,
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
          var expanded by rememberSaveable(c.id) { mutableStateOf(false) }

          CommentItem(
              comment = c,
              author = resolveUser(c.authorId),
              isMine = (c.authorId == currentUser.uid),
              onReply = { text -> onReply(c.id, text) },
              onDelete = { onDelete(c) },
              onCardClick =
                  if (c.children.isNotEmpty()) {
                    { expanded = !expanded }
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
                  depth = 1,
                  gutterColor = gutterColor)
            }
          }
        }
  }
}

/**
 * Composable drawing vertical gutter lines to indicate comment depth.
 *
 * @param depth The depth of the comment in the thread.
 * @param modifier Modifier to be applied to the gutter box.
 * @param color Color of the gutter lines.
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
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = strokePx)
              })
}

/**
 * Composable displaying an individual comment item with author, timestamp, text, and reply/delete
 * options.
 *
 * @param comment The comment to display.
 * @param author The author of the comment.
 * @param isMine Boolean indicating if the comment was made by the current user.
 * @param onReply Lambda to invoke when a reply is made to the comment.
 * @param onDelete Lambda to invoke when the comment is deleted.
 * @param onCardClick Optional lambda to invoke when the comment card is clicked (for
 *   expanding/collapsing).
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
                      .background(MaterialTheme.colorScheme.primary))
          Spacer(Modifier.width(8.dp))
          Column(Modifier.weight(1f)) {
            Text(
                text = author?.handle ?: "<Username>",
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
                placeholder = { Text("Write a reply…") },
                singleLine = true,
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
 * Composable representing a styled card used for posts and comments.
 *
 * @param modifier Modifier to be applied to the card.
 * @param contentPadding Padding values to apply inside the card.
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
 * A custom layout that arranges its children in a horizontal flow, wrapping to the next line as
 * needed.
 *
 * @param modifier Modifier to be applied to the FlowRow.
 * @param horizontalGap Horizontal gap between items.
 * @param verticalGap Vertical gap between rows.
 * @param content Composable content to be arranged in the FlowRow.
 */
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit
) {
  Layout(content = content, modifier = modifier) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val rows = mutableListOf<MutableList<Placeable>>()
    val rowWidths = mutableListOf<Int>()
    val rowHeights = mutableListOf<Int>()

    var currentRow = mutableListOf<Placeable>()
    var currentWidth = 0
    var currentHeight = 0

    placeables.forEach { p ->
      val projected =
          if (currentRow.isEmpty()) p.width else currentWidth + horizontalGap.roundToPx() + p.width
      if (projected <= constraints.maxWidth) {
        currentRow.add(p)
        currentWidth = projected
        currentHeight = maxOf(currentHeight, p.height)
      } else {
        rows.add(currentRow)
        rowWidths.add(currentWidth)
        rowHeights.add(currentHeight)
        currentRow = mutableListOf(p)
        currentWidth = p.width
        currentHeight = p.height
      }
    }
    if (currentRow.isNotEmpty()) {
      rows.add(currentRow)
      rowWidths.add(currentWidth)
      rowHeights.add(currentHeight)
    }

    val rawWidth = rowWidths.maxOrNull() ?: 0
    val rawHeight =
        rowHeights.sum() + (rowHeights.size - 1).coerceAtLeast(0) * verticalGap.roundToPx()
    val width = constraints.constrainWidth(rawWidth)
    val height = constraints.constrainHeight(rawHeight)

    layout(width, height) {
      var y = 0
      rows.forEachIndexed { idx, row ->
        var x = 0
        row.forEachIndexed { i, placeable ->
          placeable.placeRelative(x, y)
          x += placeable.width + if (i != row.lastIndex) horizontalGap.roundToPx() else 0
        }
        y += rowHeights[idx] + if (idx != rows.lastIndex) verticalGap.roundToPx() else 0
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
  return DateFormat.format("MMM d, yyyy · HH:mm", d).toString()
}
