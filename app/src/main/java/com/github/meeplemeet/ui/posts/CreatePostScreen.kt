package com.github.meeplemeet.ui.posts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.posts.CreatePostViewModel
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch

object CreatePostTestTags {

  const val TITLE_FIELD = "create_post_title_field"
  const val BODY_FIELD = "create_post_body_field"
  const val TAG_INPUT_FIELD = "create_post_tag_search_field"
  const val TAG_ADD_BUTTON = "create_post_tag_search_icon"

  const val TAGS_ROW = "create_post_tags_row"

  fun tagChip(tag: String) = "create_post_tag_chip:$tag"

  fun tagRemoveIcon(tag: String) = "create_post_tag_remove:$tag"

  const val POST_BUTTON = "create_post_post_btn"
  const val DISCARD_BUTTON = "create_post_draft_btn"

  const val SNACKBAR_HOST = "create_post_snackbar_host"
}

private const val TITLE_FIELD_PLACEHOLDER = "Choose a title for your post"
private const val BODY_FIELD_PLACEHOLDER =
    "Look for people, ask about games, or just share what's on your mind..."

object CreatePostScreenUi {
  val extraLargePadding = Dimensions.Padding.extraLarge
  val largePadding = Dimensions.Padding.large
  val mediumPadding = Dimensions.Padding.medium
  val smallIconSize = Dimensions.IconSize.small
  val xxxLargePadding = Dimensions.Padding.xxxLarge
  val defaultSpacing: Dp = 16.dp
  val spacerWidth: Dp = 8.dp
  val mediumSpacing = Dimensions.Spacing.medium
}

/**
 * Screen for creating a new post, including title, body, and tags. Tags can be freely added and are
 * displayed as chips below the input field. Tags are directly formatted internally.
 *
 * @param account The account of the user creating the post.
 * @param viewModel The view model managing the post creation logic.
 * @param onPost Callback invoked when the post is successfully created.
 * @param onDiscard Callback invoked when the discard button is pressed.
 * @param onBack Callback invoked when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePostScreen(
    account: Account,
    viewModel: CreatePostViewModel = viewModel(),
    onPost: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onBack: () -> Unit = {}
) {
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  val bodyScroll = rememberScrollState()
  var tagInput by remember { mutableStateOf("") }
  val selectedTags = remember { mutableStateListOf<String>() }

  var isPosting by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current

  /**
   * Normalizes a tag by trimming whitespace, removing leading hashes, and converting to lowercase
   * with a single leading hash.
   *
   * @param input The raw tag input.
   * @return The normalized tag, or an empty string if invalid.
   */
  fun normalizeTag(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return ""

    val withoutHashes = t.trimStart('#').trim()
    if (withoutHashes.isEmpty()) return ""
    return "#" + withoutHashes.lowercase()
  }

  /**
   * Adds the current tag input to the selected tags after normalization. Clears the tag input field
   * afterwards.
   */
  fun addTag() {
    val normalized = normalizeTag(tagInput)
    if (normalized.isNotEmpty() && normalized !in selectedTags) {
      selectedTags.add(normalized)
    }
    tagInput = ""
  }

  Scaffold(
      containerColor = AppColors.primary,
      topBar = {
        Column {
          CenterAlignedTopAppBar(
              title = {
                Text(
                    text = MeepleMeetScreen.CreatePost.title,
                    modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
              },
              navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON)) {
                      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
              },
              colors =
                  TopAppBarDefaults.centerAlignedTopAppBarColors(
                      containerColor = AppColors.primary,
                      titleContentColor = AppColors.textIcons,
                      navigationIconContentColor = AppColors.textIcons))
          HorizontalDivider(
              modifier =
                  Modifier.fillMaxWidth(0.7f)
                      .padding(horizontal = 0.dp)
                      .align(Alignment.CenterHorizontally),
              thickness = 1.dp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
      },
      bottomBar = {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = CreatePostScreenUi.xxxLargePadding, vertical = 25.dp),
            horizontalArrangement = Arrangement.spacedBy(CreatePostScreenUi.defaultSpacing)) {
              OutlinedButton(
                  onClick = onDiscard,
                  modifier = Modifier.weight(1f).testTag(CreatePostTestTags.DISCARD_BUTTON),
                  shape = CircleShape,
                  border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                  colors =
                      ButtonDefaults.outlinedButtonColors(
                          contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(CreatePostScreenUi.spacerWidth))
                    Text("Discard", style = MaterialTheme.typography.titleMedium)
                  }

              // Post button
              Button(
                  onClick = {
                    scope.launch {
                      isPosting = true
                      try {
                        viewModel.createPost(
                            title = title,
                            body = body,
                            author = account,
                            tags = selectedTags.toList())
                        onPost()
                      } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: "Failed to create post")
                      } finally {
                        isPosting = false
                      }
                    }
                  },
                  enabled = title.isNotBlank() && body.isNotBlank() && !isPosting,
                  modifier = Modifier.weight(1f).testTag(CreatePostTestTags.POST_BUTTON),
                  shape = CircleShape,
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.secondary,
                          contentColor = MaterialTheme.colorScheme.onBackground)) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(CreatePostScreenUi.spacerWidth))
                    Text("Post", style = MaterialTheme.typography.titleMedium)
                  }
            }
      },
      snackbarHost = {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.testTag(CreatePostTestTags.SNACKBAR_HOST))
      }) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(
                        horizontal = CreatePostScreenUi.extraLargePadding,
                        vertical = CreatePostScreenUi.extraLargePadding)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CreatePostScreenUi.defaultSpacing)) {

              // Title field
              OutlinedTextField(
                  value = title,
                  onValueChange = { title = it },
                  label = { Text("Title") },
                  placeholder = { Text(TITLE_FIELD_PLACEHOLDER) },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag(CreatePostTestTags.TITLE_FIELD))

              // Body field
              OutlinedTextField(
                  value = body,
                  onValueChange = { body = it },
                  label = { Text("Body") },
                  placeholder = { Text(BODY_FIELD_PLACEHOLDER) },
                  modifier =
                      Modifier.fillMaxWidth()
                          .height(200.dp)
                          .verticalScroll(bodyScroll)
                          .testTag(CreatePostTestTags.BODY_FIELD),
                  maxLines = Int.MAX_VALUE)

              // Auto-scroll to bottom when body becomes to long
              LaunchedEffect(body) { bodyScroll.animateScrollTo(bodyScroll.maxValue) }

              // Tag input field
              OutlinedTextField(
                  value = tagInput,
                  onValueChange = { tagInput = it },
                  label = { Text("Add tags") },
                  placeholder = { Text("Add new tags here") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag(CreatePostTestTags.TAG_INPUT_FIELD),
                  trailingIcon = {
                    IconButton(
                        onClick = { addTag() },
                        enabled = tagInput.trim().isNotBlank(),
                        modifier = Modifier.testTag(CreatePostTestTags.TAG_ADD_BUTTON)) {
                          Icon(
                              Icons.Default.AddCircleOutline,
                              contentDescription = "Add tag",
                              tint =
                                  if (tagInput.trim().isNotBlank())
                                      MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                  },
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                  keyboardActions =
                      KeyboardActions(
                          onDone = {
                            addTag()
                            focusManager.clearFocus()
                          }),
                  shape = CircleShape)

              // Selected tags display (wrap + max 3 lines then vertical scroll)
              if (selectedTags.isNotEmpty()) {
                val horizontalSpacing = 8.dp
                val verticalSpacing = 8.dp
                val chipHeight = 36.dp
                val maxLines = 3
                val maxHeight = chipHeight * maxLines + verticalSpacing * (maxLines - 1)
                val verticalScrollState = rememberScrollState()

                // Auto-scroll to bottom when tags change
                LaunchedEffect(selectedTags.size) {
                  verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                }

                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .verticalScroll(verticalScrollState)
                            .testTag(CreatePostTestTags.TAGS_ROW)) {
                      FlowRow(
                          horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                          verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                          modifier = Modifier.fillMaxWidth()) {
                            selectedTags.forEach { tag ->
                              Surface(
                                  shape = RoundedCornerShape(20.dp),
                                  color = MaterialTheme.colorScheme.background,
                                  border = BorderStroke(1.dp, AppColors.textIconsFade),
                                  modifier =
                                      Modifier.defaultMinSize(minHeight = chipHeight)
                                          .testTag(CreatePostTestTags.tagChip(tag))) {
                                    Row(
                                        modifier =
                                            Modifier.padding(
                                                horizontal = CreatePostScreenUi.largePadding,
                                                vertical = CreatePostScreenUi.mediumPadding),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                CreatePostScreenUi.mediumSpacing)) {
                                          // tag text
                                          Text(
                                              text = tag,
                                              style = MaterialTheme.typography.labelLarge,
                                              color = MaterialTheme.colorScheme.onSurface)

                                          // remove icon on the right
                                          IconButton(
                                              onClick = { selectedTags.remove(tag) },
                                              modifier =
                                                  Modifier.size(20.dp)
                                                      .testTag(
                                                          CreatePostTestTags.tagRemoveIcon(tag))) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove tag",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier =
                                                        Modifier.size(
                                                            CreatePostScreenUi.smallIconSize))
                                              }
                                        }
                                  }
                            }
                          }
                    }
              }
            }
      }
}
