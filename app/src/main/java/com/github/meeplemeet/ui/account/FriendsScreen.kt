// This file was done first by hand, corrected and improved using ChatGPT-5
// and finally completed by copilot
package com.github.meeplemeet.ui.account

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.FriendsScreenViewModel
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
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

  const val TABS = "FRIENDS_TABS"
  const val TAB_FRIENDS = "FRIENDS_TAB_FRIENDS"
  const val TAB_REQUESTS = "FRIENDS_TAB_REQUESTS"
  const val TAB_BLOCKED = "FRIENDS_TAB_BLOCKED"

  const val FRIEND_LIST = "FRIENDS_LIST"
  const val SENT_REQUESTS_LIST = "FRIENDS_SENT_REQUESTS_LIST"
  const val BLOCKED_LIST = "FRIENDS_BLOCKED_LIST"
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
  const val SEARCH_PLACEHOLDER = "Search users"
  const val RESET_QUERY_TEXT = ""

  object Layout {
    val FRIENDS_TABS_HEIGHT = 48.dp
    val BETWEEN_SEARCH_AND_TABS = Dimensions.Spacing.xxLarge

    val ITEM_VERTICAL_PADDING = Dimensions.Padding.small
    val ITEM_HORIZONTAL_PADDING = Dimensions.Padding.extraMedium
    val ITEM_INNER_HORIZONTAL_SPACING = Dimensions.Spacing.medium
    val ITEM_AVATAR_NAME_SPACING = Dimensions.Spacing.medium
    val ITEM_ACTIONS_SPACING = Dimensions.Spacing.small

    val ROW_MIN_HEIGHT = 72.dp
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
//  Enums & helpers
// ─────────────────────────────────────────────────────────────────────────────

enum class UserRowSecondaryAction {
  ADD_FRIEND,
  REMOVE_FRIEND,
  REQUEST_SENT,
}

enum class UserRowContext {
  FRIEND_LIST,
  SEARCH_RESULTS,
}

enum class FriendsTab {
  FRIENDS,
  REQUESTS,
  BLOCKED,
}

private data class ScrollMetrics(
    val progress: Float,
    val thumbFraction: Float,
)

private data class FriendsLists(
    val friends: List<Account>,
    val sentRequests: List<Account>,
    val blockedUsers: List<Account>,
)

private val RelationshipStatus?.isFriend: Boolean
  get() = (this == RelationshipStatus.FRIEND)

private val RelationshipStatus?.isBlocked: Boolean
  get() = (this == RelationshipStatus.BLOCKED)

private val RelationshipStatus?.isRequestSent: Boolean
  get() = (this == RelationshipStatus.SENT)

/**
 * Extension function to get a list of account IDs for a specific relationship status.
 *
 * @param status The relationship status to filter by.
 * @return List of account IDs matching the specified relationship status.
 */
private fun Account.idsFor(status: RelationshipStatus): List<String> =
    relationships.filterValues { it == status }.keys.toList()

/**
 * Loads accounts based on the provided IDs or returns an empty list if the IDs list is empty.
 *
 * @param ids List of account IDs to load.
 * @param onResult Callback function to handle the loaded accounts.
 */
private fun FriendsScreenViewModel.loadAccountsOrEmpty(
    ids: List<String>,
    context: Context,
    onResult: (List<Account>) -> Unit,
) {
  if (ids.isEmpty()) {
    onResult(emptyList())
  } else {
    getAccounts(ids, context, onResult)
  }
}

/**
 * Toggles the block status of another user for the current user.
 *
 * @param current The current user's account.
 * @param other The other user's account to block or unblock.
 */
private fun FriendsScreenViewModel.toggleBlock(
    current: Account,
    other: Account,
) {
  val status = current.relationships[other.uid]
  if (status.isBlocked) {
    unblockUser(current, other)
  } else {
    blockUser(current, other)
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function to display the Friends Management Screen.
 *
 * @param navigation Navigation actions for screen transitions.
 * @param viewModel ViewModel for friends-related operations.
 * @param account The current user's account.
 * @param onBack Callback function to be invoked when the back button is pressed.
 */
@Composable
fun FriendsScreen(
    navigation: NavigationActions,
    viewModel: FriendsScreenViewModel = viewModel(),
    account: Account,
    onBack: () -> Unit,
) {
  val context = LocalContext.current

  val rawSuggestions by viewModel.handleSuggestions.collectAsStateWithLifecycle()

  var searchQuery by rememberSaveable { mutableStateOf("") }
  var selectedTab by rememberSaveable { mutableStateOf(FriendsTab.FRIENDS) }

  val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
  val isSearching = trimmedQuery.isNotBlank()

  val friendIds = remember(account.relationships) { account.idsFor(RelationshipStatus.FRIEND) }
  val sentRequestIds = remember(account.relationships) { account.idsFor(RelationshipStatus.SENT) }
  val blockedIds = remember(account.relationships) { account.idsFor(RelationshipStatus.BLOCKED) }

  var friends by remember { mutableStateOf<List<Account>>(emptyList()) }
  var sentRequests by remember { mutableStateOf<List<Account>>(emptyList()) }
  var blockedUsers by remember { mutableStateOf<List<Account>>(emptyList()) }

  LaunchedEffect(friendIds) { viewModel.loadAccountsOrEmpty(friendIds, context) { friends = it } }

  LaunchedEffect(sentRequestIds) {
    viewModel.loadAccountsOrEmpty(sentRequestIds, context) { sentRequests = it }
  }

  LaunchedEffect(blockedIds) {
    viewModel.loadAccountsOrEmpty(blockedIds, context) { blockedUsers = it }
  }

  LaunchedEffect(trimmedQuery) { viewModel.searchByHandle(trimmedQuery) }

  val suggestions =
      remember(rawSuggestions, trimmedQuery) {
        if (trimmedQuery.isBlank()) emptyList() else rawSuggestions
      }

  Scaffold(
      topBar = { FriendsTopBar(onBack = onBack) },
      bottomBar = {
        BottomNavigationMenu(
            currentScreen = MeepleMeetScreen.Profile,
            onTabSelected = { screen -> navigation.navigateTo(screen) },
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
      )

      if (!isSearching) {
        Spacer(Modifier.height(FriendsManagementDefaults.Layout.BETWEEN_SEARCH_AND_TABS))
        FriendsTabSwitcher(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )
      }

      FriendsManagementContent(
          account = account,
          lists =
              FriendsLists(
                  friends = friends,
                  sentRequests = sentRequests,
                  blockedUsers = blockedUsers,
              ),
          suggestions = suggestions,
          searchQuery = searchQuery,
          selectedTab = selectedTab,
          viewModel = viewModel,
      )
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function to display the main content of the Friends Management Screen.
 *
 * @param account The current user's account.
 * @param lists The lists of friends, sent requests, and blocked users.
 * @param suggestions The list of suggested accounts based on the search query.
 * @param searchQuery The current search query.
 * @param selectedTab The currently selected tab in the friends management screen.
 * @param viewModel ViewModel for friends-related operations.
 */
@Composable
private fun FriendsManagementContent(
    account: Account,
    lists: FriendsLists,
    suggestions: List<Account>,
    searchQuery: String,
    selectedTab: FriendsTab,
    viewModel: FriendsScreenViewModel,
) {
  val isSearching = searchQuery.isNotBlank()

  if (isSearching) {
    FriendsSearchResultsDropdown(
        currentAccount = account,
        results = suggestions.filter { it.uid != account.uid },
        onBlockToggle = { other -> viewModel.toggleBlock(account, other) },
        onAddFriend = { other -> viewModel.sendFriendRequest(account, other) },
        onRemoveFriend = { other -> viewModel.removeFriend(account, other) },
        onCancelRequest = { other -> viewModel.rejectFriendRequest(account, other) },
    )
  } else {
    when (selectedTab) {
      FriendsTab.FRIENDS -> {
        FriendsList(
            currentAccount = account,
            friends = lists.friends,
            onBlockToggle = { friend -> viewModel.toggleBlock(account, friend) },
            onRemoveFriend = { friend -> viewModel.removeFriend(account, friend) },
        )
      }
      FriendsTab.REQUESTS -> {
        SentRequestsList(
            currentAccount = account,
            sentRequests = lists.sentRequests,
            onBlockToggle = { other -> viewModel.toggleBlock(account, other) },
            onCancelRequest = { other -> viewModel.rejectFriendRequest(account, other) },
        )
      }
      FriendsTab.BLOCKED -> {
        BlockedUsersList(
            blockedUsers = lists.blockedUsers,
            onUnblock = { other -> viewModel.unblockUser(account, other) },
        )
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar & search
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function to display the top bar of the Friends Management Screen.
 *
 * @param onBack Callback function to be invoked when the back button is pressed.
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
 * Composable function to display the search bar for friends management.
 *
 * @param query The current search query.
 * @param onQueryChange Callback function to handle changes to the search query.
 * @param onClearQuery Callback function to clear the search query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
  FocusableInputField(
      value = query,
      onValueChange = onQueryChange,
      modifier =
          Modifier.fillMaxWidth()
              .height(Dimensions.ContainerSize.timeFieldHeight)
              .testTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD)
              .shadow(
                  elevation = Dimensions.Elevation.high,
                  shape = RectangleShape,
                  clip = false,
              )
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

// ─────────────────────────────────────────────────────────────────────────────
//  Friends tabs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function to display the tab switcher for friends management.
 *
 * @param selectedTab The currently selected tab.
 * @param onTabSelected Callback function to handle tab selection changes.
 */
@Composable
private fun FriendsTabSwitcher(
    selectedTab: FriendsTab,
    onTabSelected: (FriendsTab) -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .height(FriendsManagementDefaults.Layout.FRIENDS_TABS_HEIGHT)
              .background(MaterialTheme.colorScheme.background)
              .testTag(FriendsManagementTestTags.TABS),
  ) {
    FriendsTab.entries.forEach { tab ->
      val isSelected = (tab == selectedTab)
      val label =
          when (tab) {
            FriendsTab.FRIENDS -> "Friends"
            FriendsTab.REQUESTS -> "Requests"
            FriendsTab.BLOCKED -> "Blocked"
          }

      val bgColor =
          if (isSelected) {
            MaterialTheme.colorScheme.outline
          } else {
            MaterialTheme.colorScheme.background
          }

      val textColor =
          if (isSelected) {
            MaterialTheme.colorScheme.onBackground
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          }

      val tabTag =
          when (tab) {
            FriendsTab.FRIENDS -> FriendsManagementTestTags.TAB_FRIENDS
            FriendsTab.REQUESTS -> FriendsManagementTestTags.TAB_REQUESTS
            FriendsTab.BLOCKED -> FriendsManagementTestTags.TAB_BLOCKED
          }

      Box(
          modifier =
              Modifier.weight(1f)
                  .fillMaxHeight()
                  .background(bgColor)
                  .border(
                      width = 0.dp,
                      color = MaterialTheme.colorScheme.outline,
                      shape = RectangleShape,
                  )
                  .clickable { onTabSelected(tab) }
                  .testTag(tabTag),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Row & avatar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function to display a user row with relationship actions.
 *
 * @param user The account of the user to display.
 * @param isBlocked Boolean indicating if the user is blocked.
 * @param secondaryAction The secondary action to display for the user row.
 * @param context The context in which the user row is displayed.
 * @param onBlockClick Callback function to handle block/unblock action.
 * @param onSecondaryActionClick Callback function to handle the secondary action.
 * @param showSecondaryAction Boolean indicating whether to show the secondary action button.
 */
@Composable
fun RelationshipUserRow(
    user: Account,
    isBlocked: Boolean,
    secondaryAction: UserRowSecondaryAction,
    context: UserRowContext,
    onBlockClick: () -> Unit,
    onSecondaryActionClick: () -> Unit,
    showSecondaryAction: Boolean = true,
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
            Arrangement.spacedBy(FriendsManagementDefaults.Layout.ITEM_ACTIONS_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
          onClick = onBlockClick,
          modifier = Modifier.testTag("$blockButtonPrefix${user.uid}"),
      ) {
        val blockIcon = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block

        Icon(
            imageVector = blockIcon,
            contentDescription = if (isBlocked) "Unblock" else "Block",
        )
      }

      if (showSecondaryAction) {
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
              UserRowSecondaryAction.REQUEST_SENT ->
                  Triple(
                      Icons.Default.Schedule,
                      "Friend request sent",
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
}

/**
 * Composable function to display a user's avatar.
 *
 * @param account The account of the user whose avatar is to be displayed.
 * @param modifier Modifier to be applied to the avatar.
 */
@Composable
private fun UserAvatar(
    account: Account,
    modifier: Modifier = Modifier,
) {
  val initials =
      remember(account.name) {
        account.name.trim().takeIf { it.isNotEmpty() }?.first()?.uppercase()
            ?: FriendsManagementDefaults.Avatar.DEFAULT_TEXT_AVATAR
      }

  val context = LocalContext.current
  val viewModel: FriendsScreenViewModel = viewModel()

  var avatarBytes by remember(account.uid) { mutableStateOf<ByteArray?>(null) }

  LaunchedEffect(account.uid) {
    viewModel.loadAccountProfilePicture(
        accountId = account.uid,
        context = context,
    ) { bytes ->
      avatarBytes = bytes
    }
  }

  Box(
      modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary),
      contentAlignment = Alignment.Center,
  ) {
    if (avatarBytes != null) {
      AsyncImage(
          model = avatarBytes,
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

// ─────────────────────────────────────────────────────────────────────────────
//  Generic list container + specific lists
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generic composable function to display a list of friends with a scrollbar.
 *
 * @param T The type of items in the list.
 * @param items The list of items to display.
 * @param listTestTag The test tag for the list container.
 * @param rowContent Composable function to define the content of each row in the list.
 */
@Composable
private fun <T> FriendsListContainer(
    items: List<T>,
    listTestTag: String,
    rowContent: @Composable (index: Int, item: T) -> Unit,
) {
  Box(
      modifier = Modifier.fillMaxWidth().testTag(listTestTag),
  ) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
      itemsIndexed(items) { index, item ->
        rowContent(index, item)

        if (index < items.lastIndex) {
          HorizontalDivider(
              color = MaterialTheme.colorScheme.onBackground,
              thickness = Dimensions.DividerThickness.standard,
          )
        }
      }
    }

    if (items.size >= FriendsManagementDefaults.Scrollbar.MIN_ITEMS_FOR_SCROLLBAR) {
      FriendsScrollBar(
          listState = listState,
          itemCount = items.size,
          modifier = Modifier.align(Alignment.CenterEnd),
      )
    }
  }
}

/**
 * Composable function to display the friends list.
 *
 * @param currentAccount The current user's account.
 * @param friends The list of friends to display.
 * @param onBlockToggle Callback function to handle block/unblock action.
 * @param onRemoveFriend Callback function to handle removing a friend.
 */
@Composable
fun FriendsList(
    currentAccount: Account,
    friends: List<Account>,
    onBlockToggle: (Account) -> Unit,
    onRemoveFriend: (Account) -> Unit,
) {
  FriendsListContainer(
      items = friends,
      listTestTag = FriendsManagementTestTags.FRIEND_LIST,
  ) { _, friend ->
    val status = currentAccount.relationships[friend.uid]
    val isBlocked = status.isBlocked

    RelationshipUserRow(
        user = friend,
        isBlocked = isBlocked,
        secondaryAction = UserRowSecondaryAction.REMOVE_FRIEND,
        context = UserRowContext.FRIEND_LIST,
        onBlockClick = { onBlockToggle(friend) },
        onSecondaryActionClick = { onRemoveFriend(friend) },
    )
  }
}

/**
 * Composable function to display the sent friend requests list.
 *
 * @param currentAccount The current user's account.
 * @param sentRequests The list of sent friend requests to display.
 * @param onBlockToggle Callback function to handle block/unblock action.
 * @param onCancelRequest Callback function to handle canceling a friend request.
 */
@Composable
private fun SentRequestsList(
    currentAccount: Account,
    sentRequests: List<Account>,
    onBlockToggle: (Account) -> Unit,
    onCancelRequest: (Account) -> Unit,
) {
  FriendsListContainer(
      items = sentRequests,
      listTestTag = FriendsManagementTestTags.SENT_REQUESTS_LIST,
  ) { _, other ->
    val status = currentAccount.relationships[other.uid]
    val isBlocked = status.isBlocked

    RelationshipUserRow(
        user = other,
        isBlocked = isBlocked,
        secondaryAction = UserRowSecondaryAction.REQUEST_SENT,
        context = UserRowContext.FRIEND_LIST,
        onBlockClick = { onBlockToggle(other) },
        onSecondaryActionClick = { onCancelRequest(other) },
    )
  }
}

/**
 * Composable function to display the blocked users list.
 *
 * @param blockedUsers The list of blocked users to display.
 * @param onUnblock Callback function to handle unblocking a user.
 */
@Composable
private fun BlockedUsersList(
    blockedUsers: List<Account>,
    onUnblock: (Account) -> Unit,
) {
  FriendsListContainer(
      items = blockedUsers,
      listTestTag = FriendsManagementTestTags.BLOCKED_LIST,
  ) { _, other ->
    RelationshipUserRow(
        user = other,
        isBlocked = true,
        secondaryAction = UserRowSecondaryAction.ADD_FRIEND,
        context = UserRowContext.FRIEND_LIST,
        onBlockClick = { onUnblock(other) },
        onSecondaryActionClick = {},
        showSecondaryAction = false,
    )
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Scrollbar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function to display a custom scrollbar for the friends list.
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
                      alpha = FriendsManagementDefaults.Scrollbar.SCROLLBAR_ALPHA,
                  ),
              )
              .testTag(FriendsManagementTestTags.SCROLLBAR_TRACK),
  ) {
    val metrics by remember {
      derivedStateOf {
        val visibleItems =
            listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(
                FriendsManagementDefaults.Scrollbar.MIN_COUNT_FOR_SCROLLBAR,
            )
        val totalItems =
            itemCount.coerceAtLeast(
                FriendsManagementDefaults.Scrollbar.MIN_COUNT_FOR_SCROLLBAR,
            )

        val lastStartIndex =
            (totalItems - visibleItems).coerceAtLeast(
                FriendsManagementDefaults.Scrollbar.MIN_COUNT_FOR_SCROLLBAR,
            )
        val rawProgress =
            if (totalItems <= visibleItems) 0f
            else listState.firstVisibleItemIndex.toFloat() / lastStartIndex.toFloat()
        val progress = rawProgress.coerceIn(0f, 1f)

        val rawThumbFraction = visibleItems.toFloat() / totalItems.toFloat()
        val thumbFraction =
            rawThumbFraction.coerceIn(
                FriendsManagementDefaults.Scrollbar.MIN_SCROLLBAR_SIZE_PERCENTAGE,
                FriendsManagementDefaults.Scrollbar.MAX_SCROLLBAR_SIZE_PERCENTAGE,
            )

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

// ─────────────────────────────────────────────────────────────────────────────
//  Search dropdown
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable function to display the search results dropdown for friends management.
 *
 * @param currentAccount The current user's account.
 * @param results The list of accounts matching the search query.
 * @param onBlockToggle Callback function to handle block/unblock action.
 * @param onAddFriend Callback function to handle adding a friend.
 * @param onRemoveFriend Callback function to handle removing a friend.
 * @param onCancelRequest Callback function to handle canceling a friend request.
 */
@Composable
fun FriendsSearchResultsDropdown(
    currentAccount: Account,
    results: List<Account>,
    onBlockToggle: (Account) -> Unit,
    onAddFriend: (Account) -> Unit,
    onRemoveFriend: (Account) -> Unit,
    onCancelRequest: (Account) -> Unit,
) {
  val visibleResults =
      remember(results, currentAccount.uid) { results.filter { it.uid != currentAccount.uid } }

  if (visibleResults.isEmpty()) return

  val dropdownMaxHeight =
      remember(visibleResults.size) {
        val visibleRows =
            visibleResults.size.coerceAtMost(
                FriendsManagementDefaults.Layout.MAX_VISIBLE_SEARCH_ROWS,
            )
        FriendsManagementDefaults.Layout.ROW_MIN_HEIGHT * visibleRows
      }

  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(max = dropdownMaxHeight)
              .testTag(FriendsManagementTestTags.SEARCH_RESULTS_DROPDOWN),
      tonalElevation = Dimensions.Elevation.low,
      shadowElevation = Dimensions.Elevation.low,
      shape = RectangleShape,
      color = MaterialTheme.colorScheme.surface,
  ) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
      items(visibleResults, key = { it.uid }) { other ->
        val status = currentAccount.relationships[other.uid]
        val isFriend = status.isFriend
        val isBlocked = status.isBlocked
        val isSent = status.isRequestSent

        val secondaryAction: UserRowSecondaryAction? =
            when {
              isBlocked -> null
              isFriend -> UserRowSecondaryAction.REMOVE_FRIEND
              isSent -> UserRowSecondaryAction.REQUEST_SENT
              else -> UserRowSecondaryAction.ADD_FRIEND
            }

        RelationshipUserRow(
            user = other,
            isBlocked = isBlocked,
            secondaryAction = secondaryAction ?: UserRowSecondaryAction.ADD_FRIEND,
            context = UserRowContext.SEARCH_RESULTS,
            onBlockClick = { onBlockToggle(other) },
            onSecondaryActionClick = {
              when {
                isFriend -> onRemoveFriend(other)
                isSent -> onCancelRequest(other)
                else -> onAddFriend(other)
              }
            },
            showSecondaryAction = secondaryAction != null,
        )
      }
    }
  }
}
