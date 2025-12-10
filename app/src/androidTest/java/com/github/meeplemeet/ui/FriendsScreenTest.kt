// This file was done first by hand, corrected and improved using ChatGPT-5
// and finally completed by copilot. Comments were done by ChatGPT-5
package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.FriendsScreenViewModel
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.model.navigation.LocalNavigationVM
import com.github.meeplemeet.model.navigation.NavigationViewModel
import com.github.meeplemeet.ui.account.FriendsManagementTestTags
import com.github.meeplemeet.ui.account.FriendsScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FriendsScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var viewModel: FriendsScreenViewModel
  private lateinit var navViewModel: NavigationViewModel

  private lateinit var currentUser: Account
  private lateinit var friend1: Account
  private lateinit var friend2: Account
  private lateinit var friend3: Account
  private lateinit var friend4: Account
  private lateinit var friend5: Account
  private lateinit var friend6: Account
  private lateinit var friend7: Account
  private lateinit var friend8: Account

  private lateinit var stranger: Account
  private lateinit var blockedUser: Account

  @Before
  fun setup() {
    viewModel = FriendsScreenViewModel(accountRepository, handlesRepository)
    navViewModel = NavigationViewModel(accountRepository)

    runBlocking {
      val suffix = System.currentTimeMillis()

      currentUser =
          accountRepository.createAccount(
              userHandle = "meeple_host_$suffix",
              name = "Game Master",
              email = "host_$suffix@meeple.test",
              photoUrl = null)

      friend1 =
          accountRepository.createAccount(
              userHandle = "meeple_catan_$suffix",
              name = "Catan Carl",
              email = "catan_$suffix@meeple.test",
              photoUrl = null)

      friend2 =
          accountRepository.createAccount(
              userHandle = "meeple_ticket_$suffix",
              name = "Ticket Tina",
              email = "ticket_$suffix@meeple.test",
              photoUrl = null)

      friend3 =
          accountRepository.createAccount(
              userHandle = "meeple_pandemic_$suffix",
              name = "Pandemic Pam",
              email = "pandemic_$suffix@meeple.test",
              photoUrl = null)

      friend4 =
          accountRepository.createAccount(
              userHandle = "extra_azul_$suffix",
              name = "Azul Alex",
              email = "azul_$suffix@meeple.test",
              photoUrl = null)

      friend5 =
          accountRepository.createAccount(
              userHandle = "extra_wingspan_$suffix",
              name = "Wingspan Wendy",
              email = "wingspan_$suffix@meeple.test",
              photoUrl = null)

      friend6 =
          accountRepository.createAccount(
              userHandle = "extra_gloomhaven_$suffix",
              name = "Gloomhaven Greg",
              email = "gloomhaven_$suffix@meeple.test",
              photoUrl = null)

      friend7 =
          accountRepository.createAccount(
              userHandle = "extra_carcassonne_$suffix",
              name = "Carcassonne Chloe",
              email = "carcassonne_$suffix@meeple.test",
              photoUrl = null)

      friend8 =
          accountRepository.createAccount(
              userHandle = "extra_dixit_$suffix",
              name = "Dixit Dan",
              email = "dixit_$suffix@meeple.test",
              photoUrl = null)

      // Stranger that we block via search
      stranger =
          accountRepository.createAccount(
              userHandle = "meeple_spy_$suffix",
              name = "Spyfall Sam",
              email = "spy_$suffix@meeple.test",
              photoUrl = null)

      // Already-blocked user
      blockedUser =
          accountRepository.createAccount(
              userHandle = "meeple_blocked_$suffix",
              name = "Blocked Codenames",
              email = "blocked_$suffix@meeple.test",
              photoUrl = null)

      // Register handles so searchByHandle() can find them
      handlesRepository.createAccountHandle(currentUser.uid, currentUser.handle)
      handlesRepository.createAccountHandle(friend1.uid, friend1.handle)
      handlesRepository.createAccountHandle(friend2.uid, friend2.handle)
      handlesRepository.createAccountHandle(friend3.uid, friend3.handle)
      handlesRepository.createAccountHandle(friend4.uid, friend4.handle)
      handlesRepository.createAccountHandle(friend5.uid, friend5.handle)
      handlesRepository.createAccountHandle(friend6.uid, friend6.handle)
      handlesRepository.createAccountHandle(friend7.uid, friend7.handle)
      handlesRepository.createAccountHandle(friend8.uid, friend8.handle)
      handlesRepository.createAccountHandle(stranger.uid, stranger.handle)
      handlesRepository.createAccountHandle(blockedUser.uid, blockedUser.handle)

      // Make friend1..friend8 friends of currentUser
      suspend fun befriend(other: Account) {
        accountRepository.sendFriendRequest(currentUser, other.uid)
        accountRepository.acceptFriendRequest(other.uid, currentUser.uid)
      }

      befriend(friend1)
      befriend(friend2)
      befriend(friend3)
      befriend(friend4)
      befriend(friend5)
      befriend(friend6)
      befriend(friend7)
      befriend(friend8)

      // Block one user
      accountRepository.blockUser(currentUser.uid, blockedUser.uid)

      // Refresh all accounts from Firestore to get relationships maps populated
      currentUser = accountRepository.getAccount(currentUser.uid)
      friend1 = accountRepository.getAccount(friend1.uid)
      friend2 = accountRepository.getAccount(friend2.uid)
      friend3 = accountRepository.getAccount(friend3.uid)
      friend4 = accountRepository.getAccount(friend4.uid)
      friend5 = accountRepository.getAccount(friend5.uid)
      friend6 = accountRepository.getAccount(friend6.uid)
      friend7 = accountRepository.getAccount(friend7.uid)
      friend8 = accountRepository.getAccount(friend8.uid)
      stranger = accountRepository.getAccount(stranger.uid)
      blockedUser = accountRepository.getAccount(blockedUser.uid)
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 1 – Smoke test: top bar, search bar, friends list & scrollbar
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun friendsScreen_smoke_showsTopBarSearchAndFriendList() {
    compose.setContent {
      CompositionLocalProvider(LocalNavigationVM provides navViewModel) {
        AppTheme(themeMode = ThemeMode.DARK) {
          FriendsScreen(
              account = currentUser,
              viewModel = viewModel,
              onBack = {},
          )
        }
      }
    }

    // Let the first composition settle
    compose.waitForIdle()

    // Sanity: screen root is there
    compose.waitUntilAtLeastOneExists(
        hasTestTag(FriendsManagementTestTags.SCREEN_ROOT),
        timeoutMillis = 5_000,
    )

    /* 1  Top bar presence ---------------------------------------------------- */
    checkpoint("Top bar is visible with back button") {
      compose
          .onNodeWithTag(FriendsManagementTestTags.TOP_BAR, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(FriendsManagementTestTags.TOP_BAR_BACK, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
          .assertHasClickAction()
    }

    /* 2  Friends list -------------------------------------------------------- */
    checkpoint("Friends list is visible") {
      // Wait until at least one friend row exists -> means friends have been loaded
      compose.waitUntil(timeoutMillis = 5_000) {
        compose
            .onAllNodes(
                SemanticsMatcher("friend row") { node ->
                  val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                  tag?.startsWith(FriendsManagementTestTags.FRIEND_ITEM_PREFIX) == true
                },
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      // The list container itself should exist & be visible
      compose
          .onNodeWithTag(
              FriendsManagementTestTags.FRIEND_LIST,
              useUnmergedTree = true,
          )
          .assertExists()
          .assertIsDisplayed()
    }

    /* 3  Friend rows, avatars and actions ------------------------------------ */
    checkpoint("Friend rows, avatars and actions are present for each friend") {
      // Wait until at least one friend row exists -> means friends have been loaded
      compose.waitUntil(timeoutMillis = 5_000) {
        compose
            .onAllNodes(
                SemanticsMatcher("friend row") { node ->
                  val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                  tag?.startsWith(FriendsManagementTestTags.FRIEND_ITEM_PREFIX) == true
                },
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      // Collect visible friend rows
      val friendRows =
          compose
              .onAllNodes(
                  SemanticsMatcher("friend row") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.FRIEND_ITEM_PREFIX) == true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      assert(friendRows.isNotEmpty()) { "Expected at least one friend row in the list." }

      // Collect visible avatars, block buttons and action buttons
      val avatars =
          compose
              .onAllNodes(
                  SemanticsMatcher("friend avatar") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.AVATAR_PREFIX) == true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      val blockButtons =
          compose
              .onAllNodes(
                  SemanticsMatcher("friend block button") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.FRIEND_BLOCK_BUTTON_PREFIX) == true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      val actionButtons =
          compose
              .onAllNodes(
                  SemanticsMatcher("friend action button") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.FRIEND_ACTION_BUTTON_PREFIX) == true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      assert(avatars.size >= friendRows.size) {
        "Expected at least one avatar per friend row; rows=${friendRows.size}, avatars=${avatars.size}"
      }
      assert(blockButtons.size >= friendRows.size) {
        "Expected at least one block button per friend row; rows=${friendRows.size}, blocks=${blockButtons.size}"
      }
      assert(actionButtons.size >= friendRows.size) {
        "Expected at least one action button per friend row; rows=${friendRows.size}, actions=${actionButtons.size}"
      }
    }

    /* 4  Scrollbar ----------------------------------------------------------- */
    checkpoint("Custom scrollbar track and thumb are visible with enough friends") {
      compose.waitUntil(timeoutMillis = 3_000) {
        compose
            .onAllNodesWithTag(
                FriendsManagementTestTags.SCROLLBAR_TRACK,
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      compose
          .onNodeWithTag(
              FriendsManagementTestTags.SCROLLBAR_TRACK,
              useUnmergedTree = true,
          )
          .assertExists()
          .assertIsDisplayed()

      compose
          .onNodeWithTag(
              FriendsManagementTestTags.SCROLLBAR_THUMB,
              useUnmergedTree = true,
          )
          .assertExists()
          .assertIsDisplayed()
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 2 – Search mode: dropdown results, clear button, toggle sections
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun friendsScreen_search_showsDropdownAndHidesFriendsSection() {
    compose.setContent {
      CompositionLocalProvider(LocalNavigationVM provides navViewModel) {
        AppTheme(themeMode = ThemeMode.DARK) {
          FriendsScreen(
              account = currentUser,
              viewModel = viewModel,
              onBack = {},
          )
        }
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD), timeoutMillis = 5_000)

    val searchField =
        compose.onNodeWithTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD, useUnmergedTree = true)

    /* 1  Type query -> clear icon + dropdown appear ------------------------- */
    checkpoint("Typing search query shows clear icon and search dropdown") {
      searchField.performClick()
      // prefix shared by all non-current handles
      searchField.performTextInput("meeple_")

      // Clear icon should now be visible
      compose.waitUntilAtLeastOneExists(
          hasTestTag(FriendsManagementTestTags.SEARCH_CLEAR), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(FriendsManagementTestTags.SEARCH_CLEAR, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
          .assertHasClickAction()

      // Wait for suggestions to flow through and dropdown to be rendered
      compose.waitUntilAtLeastOneExists(
          hasTestTag(FriendsManagementTestTags.SEARCH_RESULTS_DROPDOWN), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(FriendsManagementTestTags.SEARCH_RESULTS_DROPDOWN, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    /* 2  Friends section hidden while searching ----------------------------- */
    checkpoint("Friends list is hidden while search results are visible") {
      compose
          .onAllNodesWithTag(FriendsManagementTestTags.FRIEND_LIST, useUnmergedTree = true)
          .assertCountEquals(0)
    }

    /* 3  Search result rows and their actions exist ------------------------- */
    checkpoint("Search results show multiple accounts with avatars and actions") {
      // Wait until we have at least 3 search result rows
      compose.waitUntil(timeoutMillis = 5_000) {
        compose
            .onAllNodes(
                SemanticsMatcher("search result row") { node ->
                  val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                  tag?.startsWith(FriendsManagementTestTags.SEARCH_RESULT_ITEM_PREFIX) == true
                },
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .size >= 3
      }

      val rows =
          compose
              .onAllNodes(
                  SemanticsMatcher("search result row") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.SEARCH_RESULT_ITEM_PREFIX) == true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      assert(rows.size >= 3) { "Expected at least 3 search result rows, got ${rows.size}" }

      val avatars =
          compose
              .onAllNodes(
                  SemanticsMatcher("search result avatar") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.AVATAR_PREFIX) == true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      val blockButtons =
          compose
              .onAllNodes(
                  SemanticsMatcher("search result block") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.SEARCH_RESULT_BLOCK_BUTTON_PREFIX) ==
                        true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      val actionButtons =
          compose
              .onAllNodes(
                  SemanticsMatcher("search result action") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag?.startsWith(FriendsManagementTestTags.SEARCH_RESULT_ACTION_BUTTON_PREFIX) ==
                        true
                  },
                  useUnmergedTree = true,
              )
              .fetchSemanticsNodes()

      assert(avatars.size >= rows.size) {
        "Expected at least one avatar per search row; rows=${rows.size}, avatars=${avatars.size}"
      }
      assert(blockButtons.size >= rows.size) {
        "Expected at least one block button per search row; rows=${rows.size}, blocks=${blockButtons.size}"
      }
      assert(actionButtons.isNotEmpty()) {
        "Expected at least one search result row with an action button"
      }
    }

    /* 4  Clear query -> back to friends list -------------------------------- */
    checkpoint("Clearing search restores friends section and hides dropdown") {
      compose
          .onNodeWithTag(FriendsManagementTestTags.SEARCH_CLEAR, useUnmergedTree = true)
          .performClick()

      compose.waitUntilAtLeastOneExists(
          hasTestTag(FriendsManagementTestTags.FRIEND_LIST), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(FriendsManagementTestTags.FRIEND_LIST, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()

      compose
          .onAllNodesWithTag(
              FriendsManagementTestTags.SEARCH_RESULTS_DROPDOWN, useUnmergedTree = true)
          .assertCountEquals(0)
    }
  }
  // ────────────────────────────────────────────────────────────────────────────
  // TEST 3 – Remove friend from list updates Firestore relationships
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun friendsScreen_removeFriend_updatesRepository() {
    compose.setContent {
      CompositionLocalProvider(LocalNavigationVM provides navViewModel) {
        AppTheme(themeMode = ThemeMode.DARK) {
          FriendsScreen(
              account = currentUser,
              viewModel = viewModel,
              onBack = {},
          )
        }
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(FriendsManagementTestTags.FRIEND_LIST), timeoutMillis = 5_000)

    checkpoint("Precondition: friend1 is a FRIEND in repository") {
      val accounts = runBlocking {
        accountRepository.getAccounts(listOf(currentUser.uid, friend1.uid))
      }
      val updatedCurrent = accounts[0]
      val updatedFriend = accounts[1]

      assert(updatedCurrent.relationships[friend1.uid] == RelationshipStatus.FRIEND) {
        "Expected currentUser to have FRIEND relationship with friend1 (Catan Carl)"
      }
      assert(updatedFriend.relationships[currentUser.uid] == RelationshipStatus.FRIEND) {
        "Expected friend1 (Catan Carl) to have FRIEND relationship with currentUser"
      }
    }

    /* 1  Click remove-friend icon ------------------------------------------- */
    checkpoint("Remove-friend button for friend1 can be clicked") {
      compose
          .onNodeWithTag(
              FriendsManagementTestTags.FRIEND_ACTION_BUTTON_PREFIX + friend1.uid,
              useUnmergedTree = true)
          .assertExists()
          .assertHasClickAction()
          .performClick()
    }

    /* 2  Repository relationship removed on both sides ---------------------- */
    checkpoint("Clicking remove-friend removes relationship in Firestore") {
      val accounts = runBlocking {
        accountRepository.getAccounts(listOf(currentUser.uid, friend1.uid))
      }
      val updatedCurrent = accounts[0]
      val updatedFriend = accounts[1]

      assert(updatedCurrent.relationships[friend1.uid] == null) {
        "Expected currentUser.relationships[friend1] to be null after removal, was " +
            updatedCurrent.relationships[friend1.uid]
      }
      assert(updatedFriend.relationships[currentUser.uid] == null) {
        "Expected friend1.relationships[currentUser] to be null after removal, was " +
            updatedFriend.relationships[currentUser.uid]
      }
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TEST 4 – Block from search results updates Firestore relationships
  // ────────────────────────────────────────────────────────────────────────────

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun friendsScreen_blockFromSearch_updatesRepository() {
    compose.setContent {
      CompositionLocalProvider(LocalNavigationVM provides navViewModel) {
        AppTheme(themeMode = ThemeMode.DARK) {
          FriendsScreen(
              account =
                  currentUser.copy( // ensure we start without relationship to stranger
                      relationships = currentUser.relationships.filterKeys { it != stranger.uid }),
              viewModel = viewModel,
              onBack = {},
          )
        }
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD), timeoutMillis = 5_000)

    val searchField =
        compose.onNodeWithTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD, useUnmergedTree = true)

    /* 1  Type query to bring up stranger row -------------------------------- */
    checkpoint("Search dropdown shows stranger row") {
      searchField.performClick()
      searchField.performTextInput("meeple_spy_")

      compose.waitUntilAtLeastOneExists(
          hasTestTag(FriendsManagementTestTags.SEARCH_RESULTS_DROPDOWN), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(
              FriendsManagementTestTags.SEARCH_RESULT_ITEM_PREFIX + stranger.uid,
              useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    /* 2  Click block icon in stranger search row ---------------------------- */
    checkpoint("Block button on stranger row can be clicked") {
      compose
          .onNodeWithTag(
              FriendsManagementTestTags.SEARCH_RESULT_BLOCK_BUTTON_PREFIX + stranger.uid,
              useUnmergedTree = true)
          .assertExists()
          .assertHasClickAction()
          .performClick()
    }

    /* 3  Repository now has BLOCKED relationship ---------------------------- */
    checkpoint("Clicking block sets BLOCKED relationship in Firestore") {
      val updatedCurrent = runBlocking { accountRepository.getAccount(currentUser.uid) }

      assert(updatedCurrent.relationships[stranger.uid] == RelationshipStatus.BLOCKED) {
        "Expected currentUser to BLOCK stranger (Spyfall Sam) after clicking block, " +
            "but got ${updatedCurrent.relationships[stranger.uid]}"
      }
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun friendsScreen_unblockFromSearch_updatesRepository() {
    // currentUser already has blockedUser as BLOCKED in setup()
    compose.setContent {
      CompositionLocalProvider(LocalNavigationVM provides navViewModel) {
        AppTheme(themeMode = ThemeMode.DARK) {
          FriendsScreen(
              account = currentUser,
              viewModel = viewModel,
              onBack = {},
          )
        }
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD), timeoutMillis = 5_000)

    val searchField =
        compose.onNodeWithTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD, useUnmergedTree = true)

    // 1. Show blocked user in search results
    checkpoint("Search dropdown shows blocked user row") {
      searchField.performClick()
      searchField.performTextInput("meeple_blocked_")

      compose.waitUntilAtLeastOneExists(
          hasTestTag(FriendsManagementTestTags.SEARCH_RESULTS_DROPDOWN), timeoutMillis = 5_000)

      compose
          .onNodeWithTag(
              FriendsManagementTestTags.SEARCH_RESULT_ITEM_PREFIX + blockedUser.uid,
              useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
    }

    // 2. Tap block/unblock icon
    checkpoint("Block button on blocked user row can be clicked to unblock") {
      compose
          .onNodeWithTag(
              FriendsManagementTestTags.SEARCH_RESULT_BLOCK_BUTTON_PREFIX + blockedUser.uid,
              useUnmergedTree = true)
          .assertExists()
          .assertHasClickAction()
          .performClick()
    }

    // 3. Repository is now neutral
    checkpoint("Clicking block on a BLOCKED user unblocks in Firestore") {
      val updatedCurrent = runBlocking { accountRepository.getAccount(currentUser.uid) }

      assert(updatedCurrent.relationships[blockedUser.uid] == null) {
        "Expected currentUser to UNBLOCK blockedUser (Blocked Codenames), but relationship is " +
            updatedCurrent.relationships[blockedUser.uid]
      }
    }
  }
}
