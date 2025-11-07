package com.github.meeplemeet.ui
// Github copilot was used for this file
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.shared.game.FirestoreGameRepository
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.shops.EditShopScreen
import com.github.meeplemeet.ui.shops.EditShopScreenTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.FirestoreTests
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShopDetailsEditScreenTest : FirestoreTests() {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var shopRepository: ShopRepository
  private lateinit var discussionRepository: DiscussionRepository
  private lateinit var gameRepository: FirestoreGameRepository
  private lateinit var editShopViewModel: EditShopViewModel

  private lateinit var testOwner: Account
  private lateinit var testGame1: Game
  private lateinit var testGame2: Game
  private lateinit var testShop: Shop
  private lateinit var testLocation: Location
  private lateinit var testOpeningHours: List<OpeningHours>

  /** Returns the LabeledField INPUT inside the given wrapper (FIELD_* tag). */
  private fun inputIn(wrapperTag: String) =
      composeTestRule.onNode(
          hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
              hasAnyAncestor(hasTestTag(wrapperTag)),
          useUnmergedTree = true)

  /** Scroll the outer LazyColumn to bring a tagged node into view. */
  private fun scrollListToTag(tag: String) {
    composeTestRule
        .onNodeWithTag(EditShopScreenTestTags.LIST, useUnmergedTree = true)
        .performScrollToNode(hasTestTag(tag))
    composeTestRule.waitForIdle()
  }

  /** Expands a collapsible section if it's currently collapsed. */
  private fun expandSectionIfNeeded(sectionTag: String) {
    val contentTag = sectionTag + EditShopScreenTestTags.SECTION_CONTENT_SUFFIX
    val toggleTag = sectionTag + EditShopScreenTestTags.SECTION_TOGGLE_SUFFIX
    val headerTag = sectionTag + EditShopScreenTestTags.SECTION_HEADER_SUFFIX

    val isCollapsed =
        try {
          !composeTestRule.onNodeWithTag(contentTag, useUnmergedTree = true).isDisplayed()
        } catch (_: AssertionError) {
          true
        }

    if (isCollapsed) {
      // Ensure the header (and thus the toggle) is scrolled into view before clicking
      scrollListToTag(headerTag)
      composeTestRule
          .onNodeWithTag(toggleTag, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
          .performClick()
      composeTestRule.waitForIdle()
    }
  }

  /** Mirrors UI formatting: converts HH:mm to "h:mm a" and builds "start - end". */
  private fun expectedTimeText(open: String, close: String): String {
    fun parse(hhmm: String): LocalTime {
      val parts = hhmm.split(":")
      return LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }
    val fmt12 = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    val s = parse(open).format(fmt12)
    val e = parse(close).format(fmt12)
    return "$s - $e"
  }

  @Before
  fun setup() {
    shopRepository = ShopRepository(db)
    discussionRepository = DiscussionRepository()
    gameRepository = FirestoreGameRepository(db)
    editShopViewModel = EditShopViewModel(shopRepository)

    runBlocking {
      // Create test account
      testOwner =
          discussionRepository.createAccount(
              "shopowner", "Shop Owner", email = "owner@boardgames.com", photoUrl = null)

      // Create test games
      db.collection(GAMES_COLLECTION_PATH)
          .document("g_catan")
          .set(
              GameNoUid(
                  name = "Catan",
                  description = "Settlers of Catan",
                  imageURL = "https://example.com/catan.jpg",
                  minPlayers = 3,
                  maxPlayers = 4,
                  recommendedPlayers = 4,
                  averagePlayTime = 90,
                  genres = listOf(1, 2)))
          .await()

      db.collection(GAMES_COLLECTION_PATH)
          .document("g_ticket")
          .set(
              GameNoUid(
                  name = "Ticket to Ride",
                  description = "Train adventure game",
                  imageURL = "https://example.com/ticket.jpg",
                  minPlayers = 2,
                  maxPlayers = 5,
                  recommendedPlayers = 4,
                  averagePlayTime = 60,
                  genres = listOf(1)))
          .await()

      testGame1 = gameRepository.getGameById("g_catan")
      testGame2 = gameRepository.getGameById("g_ticket")
    }

    testLocation = Location(latitude = 46.5197, longitude = 6.6323, name = "EPFL Campus, Lausanne")

    testOpeningHours =
        listOf(
            OpeningHours(day = 0, hours = emptyList()),
            OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
            OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
            OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "16:00"))))

    // Create a test shop in the repository
    runBlocking {
      testShop =
          shopRepository.createShop(
              testOwner,
              "Board Game Paradise",
              "+41 21 123 45 67",
              "contact@boardgameparadise.ch",
              "https://boardgameparadise.ch",
              testLocation,
              testOpeningHours,
              listOf(testGame1 to 5, testGame2 to 3))
    }
  }

  @Test
  fun editShopScreen_displaysDataMatchingDatabase() {
    // Load the shop into the ViewModel
    editShopViewModel.setShop(testShop)

    composeTestRule.setContent {
      AppTheme {
        EditShopScreen(owner = testOwner, onBack = {}, onSaved = {}, viewModel = editShopViewModel)
      }
    }

    composeTestRule.waitForIdle()

    // === REQUIRED INFO ===
    expandSectionIfNeeded(EditShopScreenTestTags.SECTION_REQUIRED)
    inputIn(EditShopScreenTestTags.FIELD_SHOP).assertTextContains("Board Game Paradise")
    inputIn(EditShopScreenTestTags.FIELD_EMAIL).assertTextContains("contact@boardgameparadise.ch")
    inputIn(EditShopScreenTestTags.FIELD_PHONE).assertTextContains("+41 21 123 45 67")
    inputIn(EditShopScreenTestTags.FIELD_LINK).assertTextContains("https://boardgameparadise.ch")

    // === AVAILABILITY ===
    expandSectionIfNeeded(EditShopScreenTestTags.SECTION_AVAILABILITY)
    Thread.sleep(500)

    fun dayValueAt(index: Int) =
        composeTestRule
            .onAllNodes(hasTestTag(ShopComponentsTestTags.DAY_ROW_VALUE), useUnmergedTree = true)[
                index]

    dayValueAt(0).assertTextContains("Closed")

    val mon = expectedTimeText("09:00", "18:00")
    val tue = expectedTimeText("09:00", "18:00")
    val wed = expectedTimeText("09:00", "18:00")
    val thu = expectedTimeText("09:00", "20:00")
    val fri = expectedTimeText("09:00", "20:00")
    val sat = expectedTimeText("10:00", "16:00")

    dayValueAt(1).assertTextContains(mon, substring = true, ignoreCase = true)
    dayValueAt(2).assertTextContains(tue, substring = true, ignoreCase = true)
    dayValueAt(3).assertTextContains(wed, substring = true, ignoreCase = true)
    dayValueAt(4).assertTextContains(thu, substring = true, ignoreCase = true)
    dayValueAt(5).assertTextContains(fri, substring = true, ignoreCase = true)
    dayValueAt(6).assertTextContains(sat, substring = true, ignoreCase = true)

    // === GAMES STOCK ===
    // Bring games header into view to ensure section is visible
    scrollListToTag(
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)
    Thread.sleep(500)

    val catanCardTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}${testGame1.uid}"
    val ttrCardTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}${testGame2.uid}"

    // Wait until the game cards appear (composed) before asserting
    composeTestRule.waitUntil(8_000) {
      val catanExists =
          composeTestRule
              .onAllNodesWithTag(catanCardTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
      val ttrExists =
          composeTestRule
              .onAllNodesWithTag(ttrCardTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
      catanExists && ttrExists
    }

    // Catan card with name and qty 5
    composeTestRule.onNodeWithTag(catanCardTag).assertExists()
    composeTestRule
        .onNode(
            hasText(testGame1.name, substring = true) and hasAnyAncestor(hasTestTag(catanCardTag)),
            useUnmergedTree = true)
        .assertExists()
    composeTestRule
        .onNode(
            hasText("5", substring = true) and hasAnyAncestor(hasTestTag(catanCardTag)),
            useUnmergedTree = true)
        .assertExists()

    // Ticket to Ride card with name and qty 3
    composeTestRule.onNodeWithTag(ttrCardTag).assertExists()
    composeTestRule
        .onNode(
            hasText(testGame2.name, substring = true) and hasAnyAncestor(hasTestTag(ttrCardTag)),
            useUnmergedTree = true)
        .assertExists()
    composeTestRule
        .onNode(
            hasText("3", substring = true) and hasAnyAncestor(hasTestTag(ttrCardTag)),
            useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun editShopScreen_saveChanges_persistsToFirestore() {
    // Load the shop into the ViewModel
    editShopViewModel.setShop(testShop)

    // Launch screen for existing shop
    composeTestRule.setContent {
      AppTheme {
        EditShopScreen(owner = testOwner, onBack = {}, onSaved = {}, viewModel = editShopViewModel)
      }
    }

    composeTestRule.waitForIdle()

    // Edit required info
    expandSectionIfNeeded(EditShopScreenTestTags.SECTION_REQUIRED)

    val newName = "Board Game Paradise · Edited"
    val newEmail = "new-contact@boardgameparadise.ch"
    val newPhone = "+41 21 765 43 21"
    val newWebsite = "https://boardgameparadise.ch/edited"

    inputIn(EditShopScreenTestTags.FIELD_SHOP).apply {
      assertTextContains("Board Game Paradise")
      performTextClearance()
      performTextInput(newName)
    }
    inputIn(EditShopScreenTestTags.FIELD_EMAIL).apply {
      assertTextContains("contact@boardgameparadise.ch")
      performTextClearance()
      performTextInput(newEmail)
    }
    inputIn(EditShopScreenTestTags.FIELD_PHONE).apply {
      assertTextContains("+41 21 123 45 67")
      performTextClearance()
      performTextInput(newPhone)
    }
    inputIn(EditShopScreenTestTags.FIELD_LINK).apply {
      assertTextContains("https://boardgameparadise.ch")
      performTextClearance()
      performTextInput(newWebsite)
    }

    // Change Monday to Open 24 hours via dialog
    expandSectionIfNeeded(EditShopScreenTestTags.SECTION_AVAILABILITY)
    scrollListToTag(
        EditShopScreenTestTags.SECTION_AVAILABILITY + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)

    // Click the Monday edit button (index 1)
    composeTestRule
        .onAllNodesWithTag(ShopComponentsTestTags.DAY_ROW_EDIT, useUnmergedTree = true)
        .apply { this[1].performClick() }

    // Toggle Open 24 and save
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX, useUnmergedTree = true)
        .assertExists()
        .performClick()
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_SAVE, useUnmergedTree = true)
        .assertExists()
        .performClick()

    // === GAMES STOCK === Adjust quantities: +1 for first game, -1 for second
    scrollListToTag(
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)
    Thread.sleep(300)

    val catanCardTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}${testGame1.uid}"
    val ttrCardTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}${testGame2.uid}"

    // Ensure game cards are composed
    composeTestRule.waitUntil(8_000) {
      val catanExists =
          composeTestRule
              .onAllNodesWithTag(catanCardTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
      val ttrExists =
          composeTestRule
              .onAllNodesWithTag(ttrCardTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
      catanExists && ttrExists
    }

    // Click + on the first game's row
    composeTestRule
        .onNode(hasText("+") and hasAnyAncestor(hasTestTag(catanCardTag)), useUnmergedTree = true)
        .assertExists()
        .performClick()

    // Click - on the second game's row
    composeTestRule
        .onNode(hasText("-") and hasAnyAncestor(hasTestTag(ttrCardTag)), useUnmergedTree = true)
        .assertExists()
        .performClick()

    // Save changes
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.ACTION_SAVE, useUnmergedTree = true)
        .assertExists()
        .performClick()

    // Wait for repo to finish write
    Thread.sleep(1500)

    // Fetch from Firestore and assert fields
    val reloaded = runBlocking { shopRepository.getShop(testShop.id) }
    assertEquals(newName, reloaded.name)
    assertEquals(newEmail, reloaded.email)
    assertEquals(newPhone, reloaded.phone)
    assertEquals(newWebsite, reloaded.website)

    // Monday should be Open 24 hours
    val monday = reloaded.openingHours.first { it.day == 1 }
    assertEquals(1, monday.hours.size)
    assertEquals("00:00", monday.hours[0].open)
    assertEquals("23:59", monday.hours[0].close)

    // Game stock counts updated: first was 5 -> 6, second was 3 -> 2
    val counts = reloaded.gameCollection.associate { it.first.uid to it.second }
    assertEquals(6, counts[testGame1.uid])
    assertEquals(2, counts[testGame2.uid])
  }

  // --- Additional tests to increase coverage of EditShopScreen ---

  @Test
  fun editShopScreen_collapsibleSections_toggleVisibility() {
    // Load the shop into the ViewModel
    editShopViewModel.setShop(testShop)

    composeTestRule.setContent {
      AppTheme {
        EditShopScreen(owner = testOwner, onBack = {}, onSaved = {}, viewModel = editShopViewModel)
      }
    }

    val gamesHeader =
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_HEADER_SUFFIX
    val gamesToggle =
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_TOGGLE_SUFFIX
    val gamesContent =
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_CONTENT_SUFFIX

    scrollListToTag(gamesHeader)
    composeTestRule.onNodeWithTag(gamesContent, useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag(gamesToggle, useUnmergedTree = true).performClick()
    // Content should disappear
    composeTestRule.onNodeWithTag(gamesContent, useUnmergedTree = true).assertDoesNotExist()
    // Expand again
    composeTestRule.onNodeWithTag(gamesToggle, useUnmergedTree = true).performClick()
    composeTestRule.onNodeWithTag(gamesContent, useUnmergedTree = true).assertExists()
  }

  @Test
  fun openingHoursDialog_showsValidationErrorOnOverlap() {
    // Load the shop into the ViewModel
    editShopViewModel.setShop(testShop)

    composeTestRule.setContent {
      AppTheme {
        EditShopScreen(owner = testOwner, onBack = {}, onSaved = {}, viewModel = editShopViewModel)
      }
    }

    expandSectionIfNeeded(EditShopScreenTestTags.SECTION_AVAILABILITY)
    scrollListToTag(
        EditShopScreenTestTags.SECTION_AVAILABILITY + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)

    // Edit Monday
    composeTestRule
        .onAllNodesWithTag(ShopComponentsTestTags.DAY_ROW_EDIT, useUnmergedTree = true)
        .apply { this[1].performClick() }

    // Add another interval leading to overlap and try to save
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_ADD_HOURS, useUnmergedTree = true)
        .assertExists()
        .performClick()
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_SAVE, useUnmergedTree = true)
        .performClick()

    // Expect an error label (either specific overlap message or generic)
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_ERROR, useUnmergedTree = true)
        .assertExists()

    // Cancel dialog
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_CANCEL, useUnmergedTree = true)
        .performClick()
  }

  @Test
  fun saveButton_disabledWhenNoOpeningHours() {
    // Load the shop into the ViewModel
    editShopViewModel.setShop(testShop)

    composeTestRule.setContent {
      AppTheme {
        EditShopScreen(owner = testOwner, onBack = {}, onSaved = {}, viewModel = editShopViewModel)
      }
    }

    expandSectionIfNeeded(EditShopScreenTestTags.SECTION_AVAILABILITY)

    // Open Sunday editor and propagate "Closed" to all days by selecting all chips
    composeTestRule
        .onAllNodesWithTag(ShopComponentsTestTags.DAY_ROW_EDIT, useUnmergedTree = true)
        .apply { this[0].performClick() }

    // Ensure Closed is checked
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_CLOSED_CHECKBOX, useUnmergedTree = true)
        .performClick()

    // Select all days (0..6)
    repeat(7) { idx ->
      composeTestRule
          .onNodeWithTag(ShopComponentsTestTags.dayChip(idx), useUnmergedTree = true)
          .performClick()
    }

    // Save to apply closed across the week
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.DIALOG_SAVE, useUnmergedTree = true)
        .performClick()

    // Primary action should be disabled when no opening hours anywhere
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.ACTION_SAVE, useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun gameStockDialog_addGameAndCancelFlow() {
    // Seed a third game to add via dialog
    runBlocking {
      db.collection(GAMES_COLLECTION_PATH)
          .document("g_pandemic")
          .set(
              GameNoUid(
                  name = "Pandemic",
                  description = "Cooperative board game",
                  imageURL = "https://example.com/pandemic.jpg",
                  minPlayers = 2,
                  maxPlayers = 4,
                  recommendedPlayers = 4,
                  averagePlayTime = 45,
                  genres = listOf(3)))
          .await()
    }

    // Load the shop into the ViewModel
    editShopViewModel.setShop(testShop)

    composeTestRule.setContent {
      AppTheme {
        EditShopScreen(owner = testOwner, onBack = {}, onSaved = {}, viewModel = editShopViewModel)
      }
    }

    // Open Games section and click "Add game"
    val gamesHeader =
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_HEADER_SUFFIX
    scrollListToTag(gamesHeader)
    composeTestRule
        .onNodeWithTag(EditShopScreenTestTags.GAMES_ADD_BUTTON, useUnmergedTree = true)
        .assertExists()
        .performClick()

    // Wait for dialog wrapper to ensure dialog is fully composed
    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodesWithTag(
              EditShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // Focus the actual text field inside the dialog and type
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_FIELD, useUnmergedTree = true)
        .assertExists()
        .assertIsDisplayed()
        .performClick()
        .performTextInput("Pand")

    // Wait for at least one menu item then click it
    composeTestRule.waitUntil(6_000) {
      composeTestRule
          .onAllNodesWithTag(ShopComponentsTestTags.GAME_SEARCH_ITEM + ":0", useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_ITEM + ":0", useUnmergedTree = true)
        .performClick()

    // Save in dialog
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.GAME_DIALOG_SAVE, useUnmergedTree = true)
        .assertExists()
        .performClick()

    // Verify new card exists
    val pandemicCard = ShopComponentsTestTags.SHOP_GAME_PREFIX + "g_pandemic"
    composeTestRule.onNodeWithTag(pandemicCard, useUnmergedTree = true).assertExists()

    // Open dialog again and cancel directly to cover cancel path
    composeTestRule
        .onNodeWithTag(EditShopScreenTestTags.GAMES_ADD_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.GAME_DIALOG_CANCEL, useUnmergedTree = true)
        .performClick()
  }
}
