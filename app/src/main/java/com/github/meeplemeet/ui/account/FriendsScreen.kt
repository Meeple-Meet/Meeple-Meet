// This file was done first by hand, corrected and improved using ChatGPT-5
// and finally completed by copilot
package com.github.meeplemeet.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.theme.Dimensions

// ─────────────────────────────────────────────────────────────────────────────
//  TEST TAGS
// ─────────────────────────────────────────────────────────────────────────────

object FriendsManagementTestTags {
  const val SCREEN_ROOT = "FRIENDS_SCREEN_ROOT"

  const val TOP_BAR = "FRIENDS_TOP_BAR"
  const val TOP_BAR_BACK = "FRIENDS_TOP_BAR_BACK"

  const val SEARCH_TEXT_FIELD = "FRIENDS_SEARCH_TEXT_FIELD"
  const val SEARCH_CLEAR = "FRIENDS_SEARCH_CLEAR"

  const val SECTION_TITLE_FRIENDS = "FRIENDS_SECTION_TITLE"
  const val FRIEND_LIST = "FRIENDS_LIST"
  const val FRIEND_ITEM_PREFIX = "FRIEND_ITEM_"
  const val FRIEND_BLOCK_BUTTON_PREFIX = "FRIEND_BLOCK_BUTTON_"
  const val FRIEND_ACTION_BUTTON_PREFIX = "FRIEND_ACTION_BUTTON_"

  const val SEARCH_RESULTS_DROPDOWN = "FRIENDS_SEARCH_DROPDOWN"
  const val SEARCH_RESULT_ITEM_PREFIX = "FRIENDS_SEARCH_ITEM_"
  const val SEARCH_RESULT_BLOCK_BUTTON_PREFIX = "FRIENDS_SEARCH_BLOCK_BUTTON_"
  const val SEARCH_RESULT_ACTION_BUTTON_PREFIX = "FRIENDS_SEARCH_ACTION_BUTTON_"

  const val AVATAR_PREFIX = "FRIENDS_AVATAR_"

  const val SCROLLBAR_TRACK = "FRIENDS_SCROLLBAR_TRACK"
  const val SCROLLBAR_THUMB = "FRIENDS_SCROLLBAR_THUMB"
}

// ─────────────────────────────────────────────────────────────────────────────
//  MAGIC NUMBERS
// ─────────────────────────────────────────────────────────────────────────────

object FriendsManagementDefaults {

  const val TITLE = "Manage your friends"
  const val SECTION_TITLE_FRIENDS = "Your friends :"
  const val SEARCH_PLACEHOLDER = "Search users"
  const val RESET_QUERY_TEXT = ""

  object Layout {
    val BETWEEN_SEARCH_AND_CONTENT = Dimensions.Spacing.large
    val ITEM_VERTICAL_PADDING = Dimensions.Padding.small
    val ITEM_HORIZONTAL_PADDING = Dimensions.Padding.extraMedium
    val ITEM_INNER_HORIZONTAL_SPACING = Dimensions.Spacing.medium
    val ITEM_AVATAR_NAME_SPACING = Dimensions.Spacing.medium
    val ITEM_ACTIONS_SPACING = Dimensions.Spacing.small
    val SECTION_TITLE_BOTTOM_PADDING = Dimensions.Padding.small

    val ROW_MIN_HEIGHT = 72.dp
    const val MAX_VISIBLE_FRIEND_ROWS = 6
    const val MAX_VISIBLE_SEARCH_ROWS = 6
  }

  object Avatar {
    val SIZE = Dimensions.AvatarSize.medium
    const val DEFAULT_TEXT_AVATAR = "?"
  }

  object Scrollbar {
    val TRACK_WIDTH = 15.dp
    val TRACK_PADDING = Dimensions.Padding.tiny
    const val MIN_ITEMS_FOR_SCROLLBAR = 8
    const val SCROLLBAR_ALPHA = 0.1f

    const val EPS = 0.001f
    const val MIN_SCROLLBAR_SIZE_PERCENTAGE = 0.15f
    const val MAX_SCROLLBAR_SIZE_PERCENTAGE = 1f
    const val MIN_COUNT_FOR_SCROLLBAR = 1
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Enums
// ─────────────────────────────────────────────────────────────────────────────

/** Secondary action types for user rows in the friends management screen. */
enum class UserRowSecondaryAction {
  ADD_FRIEND,
  REMOVE_FRIEND,
}

/** Contexts in which a user row can be displayed in the friends management screen. */
enum class UserRowContext {
  FRIEND_LIST,
  SEARCH_RESULTS,
}

/** Data class representing scroll metrics for the custom scrollbar. */
private data class ScrollMetrics(
    val progress: Float,
    val thumbFraction: Float,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function representing the Friends Management screen, allowing users to view, search,
 * add, remove, block, and unblock friends.
 *
 * @param account The current user's account.
 * @param viewModel The ViewModel managing the profile screen state and actions.
 * @param onBack Callback invoked when the back button is pressed.
 */
@Composable
fun FriendsScreen(
    account: Account,
    viewModel: ProfileScreenViewModel = viewModel(),
    onBack: () -> Unit,
) {
  val rawSuggestions by viewModel.handleSuggestions.collectAsStateWithLifecycle()

  var searchQuery by rememberSaveable { mutableStateOf("") }

  val friendIds =
      remember(account.relationships) {
        account.relationships.filterValues { it == RelationshipStatus.FRIEND }.keys.toList()
      }

  var friends by remember { mutableStateOf<List<Account>>(emptyList()) }

  LaunchedEffect(friendIds) {
    if (friendIds.isEmpty()) {
      friends = emptyList()
    } else {
      viewModel.getAccounts(friendIds) { result -> friends = result }
    }
  }

  LaunchedEffect(searchQuery) { viewModel.searchByHandle(searchQuery.trim()) }

  val suggestions =
      remember(rawSuggestions, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else rawSuggestions
      }

  Scaffold(
      topBar = {
        FriendsTopBar(
            onBack = onBack,
        )
      },
      containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .testTag(FriendsManagementTestTags.SCREEN_ROOT),
    ) {
      FriendsSearchBar(
          query = searchQuery,
          onQueryChange = { searchQuery = it },
          onClearQuery = { searchQuery = FriendsManagementDefaults.RESET_QUERY_TEXT },
          modifier = Modifier.fillMaxWidth(),
      )

      FriendsManagementContent(
          account = account,
          friends = friends,
          suggestions = suggestions,
          searchQuery = searchQuery,
          viewModel = viewModel,
      )
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Content area for the friends screen, displaying either search results or the friends list based
 * on the search query.
 *
 * @param account The current user's account.
 * @param friends The list of current friends.
 * @param suggestions The list of user suggestions based on the search query.
 * @param searchQuery The current search query.
 * @param viewModel The ViewModel managing the profile screen state and actions.
 */
@Composable
private fun FriendsManagementContent(
    account: Account,
    friends: List<Account>,
    suggestions: List<Account>,
    searchQuery: String,
    viewModel: ProfileScreenViewModel,
) {
  val isSearching = searchQuery.isNotBlank() && suggestions.isNotEmpty()

  if (isSearching) {
    FriendsSearchResultsDropdown(
        currentAccount = account,
        results = suggestions.filter { it.uid != account.uid },
        onBlockToggle = { other ->
          if (account.relationships[other.uid] == RelationshipStatus.BLOCKED) {
            viewModel.unblockUser(account, other)
          } else {
            viewModel.blockUser(account, other)
          }
        },
        onAddFriend = { other -> viewModel.sendFriendRequest(account, other) },
        onRemoveFriend = { other -> viewModel.removeFriend(account, other) },
        modifier = Modifier.fillMaxWidth(),
    )
  } else {
    Spacer(Modifier.height(FriendsManagementDefaults.Layout.BETWEEN_SEARCH_AND_CONTENT))

    Text(
        text = FriendsManagementDefaults.SECTION_TITLE_FRIENDS,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier.padding(Dimensions.Padding.medium)
                .fillMaxWidth()
                .testTag(FriendsManagementTestTags.SECTION_TITLE_FRIENDS),
    )

    Spacer(Modifier.height(FriendsManagementDefaults.Layout.SECTION_TITLE_BOTTOM_PADDING))

    FriendsList(
        currentAccount = account,
        friends = friends,
        modifier = Modifier.fillMaxWidth(),
        onBlockToggle = { friend ->
          if (account.relationships[friend.uid] == RelationshipStatus.BLOCKED) {
            viewModel.unblockUser(account, friend)
          } else {
            viewModel.blockUser(account, friend)
          }
        },
        onRemoveFriend = { friend -> viewModel.removeFriend(account, friend) },
    )
  }
}

/**
 * Top bar for the Friends Management screen with a back button and title.
 *
 * @param onBack Callback invoked when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsTopBar(
    onBack: () -> Unit,
) {
  CenterAlignedTopAppBar(
      modifier = Modifier.testTag(FriendsManagementTestTags.TOP_BAR),
      colors =
          TopAppBarDefaults.centerAlignedTopAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
          ),
      title = {
        Text(
            text = FriendsManagementDefaults.TITLE,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
      },
      navigationIcon = {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag(FriendsManagementTestTags.TOP_BAR_BACK),
        ) {
          Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
          )
        }
      },
  )
}

/**
 * Search bar for searching users to add as friends.
 *
 * @param query The current search query.
 * @param onQueryChange Callback invoked when the search query changes.
 * @param onClearQuery Callback invoked to clear the search query.
 * @param modifier Modifier to be applied to the search bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
  FocusableInputField(
      value = query,
      onValueChange = onQueryChange,
      modifier =
          modifier
              .height(Dimensions.ContainerSize.timeFieldHeight)
              .testTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD)
              .shadow(elevation = Dimensions.Elevation.high, shape = RectangleShape, clip = false)
              .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium),
      placeholder = {
        Text(
            FriendsManagementDefaults.SEARCH_PLACEHOLDER,
            style = MaterialTheme.typography.bodyMedium,
        )
      },
      singleLine = true,
      leadingIcon = {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
        )
      },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(
              onClick = onClearQuery,
              modifier = Modifier.testTag(FriendsManagementTestTags.SEARCH_CLEAR),
          ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
            )
          }
        }
      },
      textStyle = MaterialTheme.typography.bodyMedium,
      shape = RectangleShape,
      colors =
          TextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              disabledContainerColor = MaterialTheme.colorScheme.surface,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent,
          ),
  )
}

/**
 * A row representing a user in the friends management context, with options to block/unblock and
 * add/remove as a friend.
 *
 * @param user The user account to display.
 * @param isBlocked Whether the user is currently blocked.
 * @param secondaryAction The secondary action to display (add/remove friend).
 * @param context The context in which this row is used (friend list or search results).
 * @param onBlockClick Callback invoked when the block/unblock button is clicked.
 * @param onSecondaryActionClick Callback invoked when the secondary action button is clicked.
 */
@Composable
fun RelationshipUserRow(
    user: Account,
    isBlocked: Boolean,
    secondaryAction: UserRowSecondaryAction,
    context: UserRowContext,
    onBlockClick: () -> Unit,
    onSecondaryActionClick: () -> Unit,
) {
  val itemPrefix =
      when (context) {
        UserRowContext.FRIEND_LIST -> FriendsManagementTestTags.FRIEND_ITEM_PREFIX
        UserRowContext.SEARCH_RESULTS -> FriendsManagementTestTags.SEARCH_RESULT_ITEM_PREFIX
      }

  val blockButtonPrefix =
      when (context) {
        UserRowContext.FRIEND_LIST -> FriendsManagementTestTags.FRIEND_BLOCK_BUTTON_PREFIX
        UserRowContext.SEARCH_RESULTS -> FriendsManagementTestTags.SEARCH_RESULT_BLOCK_BUTTON_PREFIX
      }

  val actionButtonPrefix =
      when (context) {
        UserRowContext.FRIEND_LIST -> FriendsManagementTestTags.FRIEND_ACTION_BUTTON_PREFIX
        UserRowContext.SEARCH_RESULTS ->
            FriendsManagementTestTags.SEARCH_RESULT_ACTION_BUTTON_PREFIX
      }

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)
              .defaultMinSize(minHeight = FriendsManagementDefaults.Layout.ROW_MIN_HEIGHT)
              .testTag("$itemPrefix${user.uid}")
              .padding(
                  vertical = FriendsManagementDefaults.Layout.ITEM_VERTICAL_PADDING,
                  horizontal = FriendsManagementDefaults.Layout.ITEM_HORIZONTAL_PADDING,
              ),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    UserAvatar(
        account = user,
        modifier =
            Modifier.size(FriendsManagementDefaults.Avatar.SIZE)
                .testTag("${FriendsManagementTestTags.AVATAR_PREFIX}${user.uid}"),
    )

    Spacer(Modifier.width(FriendsManagementDefaults.Layout.ITEM_AVATAR_NAME_SPACING))

    Column(
        modifier = Modifier.weight(weight = 1f),
        verticalArrangement = Arrangement.Center,
    ) {
      Text(
          text = user.name,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onBackground,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = "@${user.handle}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }

    Spacer(Modifier.width(FriendsManagementDefaults.Layout.ITEM_INNER_HORIZONTAL_SPACING))

    Row(
        horizontalArrangement =
            Arrangement.spacedBy(
                FriendsManagementDefaults.Layout.ITEM_ACTIONS_SPACING,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
          onClick = onBlockClick,
          modifier = Modifier.testTag("$blockButtonPrefix${user.uid}"),
      ) {
        Icon(
            imageVector = Icons.Default.Block,
            contentDescription = if (isBlocked) "Unblock" else "Block",
            tint =
                if (isBlocked) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
      }

      val (icon, description, tint) =
          when (secondaryAction) {
            UserRowSecondaryAction.ADD_FRIEND ->
                Triple(
                    Icons.Default.PersonAdd,
                    "Add friend",
                    MaterialTheme.colorScheme.onBackground,
                )
            UserRowSecondaryAction.REMOVE_FRIEND ->
                Triple(
                    Icons.Default.PersonRemove,
                    "Remove friend",
                    MaterialTheme.colorScheme.onBackground,
                )
          }

      IconButton(
          onClick = onSecondaryActionClick,
          modifier = Modifier.testTag("$actionButtonPrefix${user.uid}"),
      ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
        )
      }
    }
  }
}

/**
 * Composable to display a user's avatar. If the user has a photo URL, it loads and displays the
 * image; otherwise, it shows the user's initials in a colored circle.
 *
 * @param account The user account whose avatar is to be displayed.
 * @param modifier Modifier to be applied to the avatar.
 */
@Composable
private fun UserAvatar(
    account: Account,
    modifier: Modifier = Modifier,
) {
  val placeholderColor = MaterialTheme.colorScheme.primary
  val initials =
      remember(account.name) {
        account.name.trim().takeIf { it.isNotEmpty() }?.first()?.uppercase()
            ?: FriendsManagementDefaults.Avatar.DEFAULT_TEXT_AVATAR
      }

  Box(
      modifier =
          modifier
              .clip(CircleShape)
              .background(placeholderColor)
              .shadow(Dimensions.Elevation.minimal, CircleShape, clip = true),
      contentAlignment = Alignment.Center,
  ) {
    if (account.photoUrl != null) {
      AsyncImage(
          model = account.photoUrl,
          contentDescription = "Profile picture of ${account.name}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
      )
    } else {
      Text(
          text = initials,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimary,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/**
 * Composable to display a scrollable list of friends with block and remove actions.
 *
 * @param currentAccount The current user's account.
 * @param friends The list of friend accounts to display.
 * @param onBlockToggle Callback invoked when a friend is blocked or unblocked.
 * @param onRemoveFriend Callback invoked when a friend is removed.
 * @param modifier Modifier to be applied to the friends list.
 */
@Composable
fun FriendsList(
    currentAccount: Account,
    friends: List<Account>,
    onBlockToggle: (Account) -> Unit,
    onRemoveFriend: (Account) -> Unit,
    modifier: Modifier = Modifier,
) {

  val listHeight =
      remember(friends.size) {
        val visibleRows =
            friends.size.coerceAtMost(FriendsManagementDefaults.Layout.MAX_VISIBLE_FRIEND_ROWS)
        FriendsManagementDefaults.Layout.ROW_MIN_HEIGHT * visibleRows
      }

  Box(
      modifier =
          modifier
              .heightIn(max = listHeight)
              .fillMaxWidth()
              .testTag(FriendsManagementTestTags.FRIEND_LIST),
  ) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
      itemsIndexed(friends, key = { _, friend -> friend.uid }) { index, friend ->
        val isBlocked = (currentAccount.relationships[friend.uid] == RelationshipStatus.BLOCKED)

        RelationshipUserRow(
            user = friend,
            isBlocked = isBlocked,
            secondaryAction = UserRowSecondaryAction.REMOVE_FRIEND,
            context = UserRowContext.FRIEND_LIST,
            onBlockClick = { onBlockToggle(friend) },
            onSecondaryActionClick = { onRemoveFriend(friend) },
        )

        if (index < friends.lastIndex) {
          HorizontalDivider(
              color = MaterialTheme.colorScheme.onBackground,
              thickness = Dimensions.DividerThickness.standard,
          )
        }
      }
    }

    if (friends.size >= FriendsManagementDefaults.Scrollbar.MIN_ITEMS_FOR_SCROLLBAR) {
      FriendsScrollBar(
          listState = listState,
          itemCount = friends.size,
          modifier = Modifier.align(Alignment.CenterEnd),
      )
    }
  }
}

/**
 * Composable to display a custom scrollbar for a lazy list.
 *
 * @param listState The state of the lazy list to track scroll position.
 * @param itemCount The total number of items in the list.
 * @param modifier Modifier to be applied to the scrollbar.
 */
@Composable
private fun FriendsScrollBar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxHeight()
              .width(FriendsManagementDefaults.Scrollbar.TRACK_WIDTH)
              .padding(FriendsManagementDefaults.Scrollbar.TRACK_PADDING)
              .clip(CircleShape)
              .background(
                  MaterialTheme.colorScheme.background.copy(
                      alpha = FriendsManagementDefaults.Scrollbar.SCROLLBAR_ALPHA),
              )
              .testTag(FriendsManagementTestTags.SCROLLBAR_TRACK),
  ) {
    val metrics by remember {
      derivedStateOf {
        val visibleItems =
            listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(
                FriendsManagementDefaults.Scrollbar.MIN_COUNT_FOR_SCROLLBAR)
        val totalItems =
            itemCount.coerceAtLeast(FriendsManagementDefaults.Scrollbar.MIN_COUNT_FOR_SCROLLBAR)

        // Scroll progress
        val lastStartIndex =
            (totalItems - visibleItems).coerceAtLeast(
                FriendsManagementDefaults.Scrollbar.MIN_COUNT_FOR_SCROLLBAR)
        val rawProgress =
            if (totalItems <= visibleItems) 0f
            else listState.firstVisibleItemIndex.toFloat() / lastStartIndex.toFloat()
        val progress = rawProgress.coerceIn(0f, 1f)

        // How much of the list is visible
        val rawThumbFraction = visibleItems.toFloat() / totalItems.toFloat()
        val thumbFraction =
            rawThumbFraction.coerceIn(
                FriendsManagementDefaults.Scrollbar.MIN_SCROLLBAR_SIZE_PERCENTAGE,
                FriendsManagementDefaults.Scrollbar.MAX_SCROLLBAR_SIZE_PERCENTAGE)

        ScrollMetrics(progress = progress, thumbFraction = thumbFraction)
      }
    }

    val thumbFraction = metrics.thumbFraction
    val remaining = (1f - thumbFraction)

    val rawTop = remaining * metrics.progress
    val rawBottom = remaining * (1f - metrics.progress)

    val topWeight = rawTop + FriendsManagementDefaults.Scrollbar.EPS
    val bottomWeight = rawBottom + FriendsManagementDefaults.Scrollbar.EPS
    val thumbWeight = thumbFraction + FriendsManagementDefaults.Scrollbar.EPS

    Column(modifier = Modifier.fillMaxSize()) {
      Spacer(modifier = Modifier.weight(topWeight))
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(thumbWeight)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.background)
                  .testTag(FriendsManagementTestTags.SCROLLBAR_THUMB),
      )
      Spacer(modifier = Modifier.weight(bottomWeight))
    }
  }
}

/**
 * Composable to display a dropdown list of search results for adding friends.
 *
 * @param currentAccount The current user's account.
 * @param results The list of account search results to display.
 * @param onBlockToggle Callback invoked when a user is blocked or unblocked.
 * @param onAddFriend Callback invoked when a user is added as a friend.
 * @param onRemoveFriend Callback invoked when a user is removed as a friend.
 * @param modifier Modifier to be applied to the search results dropdown.
 */
@Composable
fun FriendsSearchResultsDropdown(
    currentAccount: Account,
    results: List<Account>,
    onBlockToggle: (Account) -> Unit,
    onAddFriend: (Account) -> Unit,
    onRemoveFriend: (Account) -> Unit,
    modifier: Modifier = Modifier,
) {

  val dropdownMaxHeight =
      remember(results.size) {
        val visibleRows =
            results.size.coerceAtMost(FriendsManagementDefaults.Layout.MAX_VISIBLE_SEARCH_ROWS)
        FriendsManagementDefaults.Layout.ROW_MIN_HEIGHT * visibleRows
      }

  Surface(
      modifier =
          modifier
              .heightIn(max = dropdownMaxHeight)
              .testTag(FriendsManagementTestTags.SEARCH_RESULTS_DROPDOWN),
      tonalElevation = Dimensions.Elevation.low,
      shadowElevation = Dimensions.Elevation.low,
      shape = RectangleShape,
      color = MaterialTheme.colorScheme.surface,
  ) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
      items(results, key = { it.uid }) { other ->
        val relStatus = currentAccount.relationships[other.uid]
        val isFriend = (relStatus == RelationshipStatus.FRIEND)
        val isBlocked = (relStatus == RelationshipStatus.BLOCKED)

        val action =
            if (isFriend) UserRowSecondaryAction.REMOVE_FRIEND
            else UserRowSecondaryAction.ADD_FRIEND

        RelationshipUserRow(
            user = other,
            isBlocked = isBlocked,
            secondaryAction = action,
            context = UserRowContext.SEARCH_RESULTS,
            onBlockClick = { onBlockToggle(other) },
            onSecondaryActionClick = {
              if (isFriend) onRemoveFriend(other) else onAddFriend(other)
            },
        )
      }
    }
  }
}
