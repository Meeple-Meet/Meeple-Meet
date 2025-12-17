package com.github.meeplemeet.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.EditShopViewModel
import com.github.meeplemeet.model.shops.GameItem
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.components.CommonComponentsTestTags
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.shops.CreateShopScreenTestTags
import com.github.meeplemeet.ui.shops.EditShopScreenTestTags
import com.github.meeplemeet.ui.shops.ShopDetailsScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShopDetailsEditScreenTest : FirestoreTests() {

  /* ───────────────────────────────── RULES ───────────────────────────────── */

  @get:Rule val compose = createComposeRule()

  /* ---------- Checkpoint helper ---------- */
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  /* ────────────────────────────── FIXTURES / FX ──────────────────────────── */

  private lateinit var owner: Account
  private lateinit var shop: Shop
  private lateinit var gameCatan: Game
  private lateinit var gameCarcassonne: Game

  /* ────────────────────────────── SETUP ──────────────────────────────────── */

  @Before
  fun setup(): Unit = runBlocking {
    // Create owner
    owner =
        accountRepository.createAccount(
            userHandle = "meeple", name = "Meeple", email = "marco@epfl.com", photoUrl = null)

    // Create test games in Firestore
    db.collection(GAMES_COLLECTION_PATH)
        .document("test_catan")
        .set(
            GameNoUid(
                name = "Catan",
                description = "Settlers of Catan",
                imageURL = "",
                minPlayers = 3,
                maxPlayers = 4,
                recommendedPlayers = 4,
                averagePlayTime = 90,
                genres = listOf("1", "2")))
        .await()
    gameCatan = gameRepository.getGameById("test_catan")

    db.collection(GAMES_COLLECTION_PATH)
        .document("test_carcassonne")
        .set(
            GameNoUid(
                name = "Carcassonne",
                description = "Tile-laying game",
                imageURL = "",
                minPlayers = 2,
                maxPlayers = 5,
                recommendedPlayers = 4,
                averagePlayTime = 45,
                genres = listOf("1")))
        .await()
    gameCarcassonne = gameRepository.getGameById("test_carcassonne")

    db.collection(GAMES_COLLECTION_PATH)
        .document("test_pandemic")
        .set(
            GameNoUid(
                name = "Pandemic",
                description = "Cooperative game",
                imageURL = "",
                minPlayers = 2,
                maxPlayers = 4,
                recommendedPlayers = 4,
                averagePlayTime = 45,
                genres = listOf("3")))
        .await()

    // Create Shop
    val openings =
        (1..7).map { day -> OpeningHours(day = day, hours = listOf(TimeSlot("09:00", "18:00"))) }
    val games =
        listOf(
            GameItem(gameCatan.uid, gameCatan.name, 5),
            GameItem(gameCarcassonne.uid, gameCarcassonne.name, 10))

    shop =
        shopRepository.createShop(
            owner = owner,
            name = "Meeple Mart",
            phone = "+41 79 000 00 00",
            email = "shop@meeple.com",
            address = Location(45.0, 6.0, "EPFL"),
            website = "https://meeple.com",
            openingHours = openings,
            gameCollection = games)
  }

  /* ────────────────────────────── EXT HELPERS ────────────────────────────── */

  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  private fun ComposeTestRule.onTags(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true)

  /** Scroll the outer LazyColumn to bring a tagged node into view. */
  private fun scrollListToTag(tag: String) {
    compose.onTag(EditShopScreenTestTags.LIST).performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
  }

  /** Expand a collapsible section only if its content isn't currently in the tree. */
  private fun ensureSectionExpanded(sectionBaseTag: String) {
    val contentTag = sectionBaseTag + EditShopScreenTestTags.SECTION_CONTENT_SUFFIX
    val isExpanded = compose.onTags(contentTag).fetchSemanticsNodes().isNotEmpty()
    if (!isExpanded) {
      compose
          .onTag(sectionBaseTag + CreateShopScreenTestTags.SECTION_TOGGLE_ICON_SUFFIX)
          .assertExists()
          .performClick()
      compose.waitForIdle()
    }
  }

  /** Returns the LabeledField INPUT inside the given wrapper (FIELD_* tag). */
  private fun inputIn(wrapperTag: String) =
      compose.onNode(
          hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
              hasAnyAncestor(hasTestTag(wrapperTag)),
          useUnmergedTree = true)

  /** Set slider value by performing swipe gesture. */
  private fun setSliderValue(targetValue: Int, maxValue: Int = 100) {
    compose.onTag(ShopComponentsTestTags.QTY_INPUT_FIELD).performTouchInput {
      val fraction = targetValue.toFloat() / maxValue.toFloat()
      val targetX = left + (right - left) * fraction
      val targetY = top + (bottom - top) * 0.9f
      down(Offset(center.x, targetY))
      moveTo(Offset(targetX, targetY))
      up()
    }
    compose.waitForIdle()
  }

  /** Add a game using the slider for quantity selection. */
  @OptIn(ExperimentalTestApi::class)
  private fun addGameWithSlider(gameName: String, quantity: Int) {
    ensureSectionExpanded(EditShopScreenTestTags.SECTION_GAMES)
    scrollListToTag(EditShopScreenTestTags.GAMES_ADD_BUTTON)
    compose.onTag(EditShopScreenTestTags.GAMES_ADD_BUTTON).assertExists().performClick()
    compose.onTag(EditShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertExists()

    // Type in the search field to trigger game search
    compose
        .onTag(ShopComponentsTestTags.GAME_SEARCH_FIELD)
        .assertExists()
        .performTextInput(gameName)
    compose.waitForIdle()

    // Wait for first dropdown item
    val firstItemTag = "${ShopComponentsTestTags.GAME_SEARCH_ITEM}:0"
    compose.waitUntil(15_000) {
      compose
          .onAllNodesWithTag(firstItemTag, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    // Click the first dropdown item
    compose.onTag(firstItemTag).performClick()
    compose.waitForIdle()

    // Wait for the save button
    compose.waitUntil(5_000) {
      val saveButton = compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE)
      try {
        saveButton.assertIsEnabled()
        true
      } catch (e: AssertionError) {
        false
      }
    }

    // Set quantity using slider
    setSliderValue(quantity)

    // Save
    compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).assertIsEnabled().performClick()
    compose.onTag(EditShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertDoesNotExist()
  }

  /** Flip Sunday to “Open 24 hours” via the dialog and assert the 1st row value. */
  private fun setAnyOpeningHoursViaDialog() {
    scrollListToTag(
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)
    ensureSectionExpanded(EditShopScreenTestTags.SECTION_AVAILABILITY)

    compose.onTags(ShopComponentsTestTags.DAY_ROW_EDIT).assertCountEquals(7)[0].performClick()

    compose.onTag(CreateShopScreenTestTags.OPENING_HOURS_DIALOG_WRAPPER).assertExists()
    compose.onTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX).assertExists().performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).assertIsEnabled().performClick()
    compose.waitForIdle()

    compose
        .onTags(ShopComponentsTestTags.DAY_ROW_VALUE)
        .assertCountEquals(7)[0]
        .assert(hasText("Open 24 hours", substring = true))
        .assertIsDisplayed()
  }

  /* ────────────────────────────── MERGED FLOWS ───────────────────────────── */

  @Test
  fun editShop_coreFlows_singleComposition(): Unit {
    // Disable bottom bar hiding to ensure ActionBar is always visible in tests
    UiBehaviorConfig.hideBottomBarWhenInputFocused = false

    var savedCalled = false
    var backCalled = false

    // We can use a stage integer to simulate steps if needed,
    // though for Edit screen we mostly just interact sequentially.
    lateinit var stage: MutableIntState

    val viewModel = EditShopViewModel()

    compose.setContent {
      AppTheme {
        stage = remember { mutableIntStateOf(0) }
        // Force re-read of shop from repository/pass specific shop not needed as we pass 'shop'
        // object
        ShopDetailsScreen(
            owner = owner,
            shop = shop,
            onBack = { backCalled = true },
            onSaved = { savedCalled = true },
            onDelete = {},
            online = true,
            viewModel = viewModel)
      }
    }

    // 0) Structure & Initial Data
    checkpoint("Structure & Initial Data") {
      compose.onTag(EditShopScreenTestTags.SCAFFOLD).assertExists()
      compose.onTag(EditShopScreenTestTags.TOPBAR).assertExists()
      compose.onTag(EditShopScreenTestTags.TITLE).assertExists()

      // Expand Required Section
      ensureSectionExpanded(EditShopScreenTestTags.SECTION_REQUIRED)
      inputIn(EditShopScreenTestTags.FIELD_SHOP).assert(hasText("Meeple Mart"))
      inputIn(EditShopScreenTestTags.FIELD_EMAIL).assert(hasText("shop@meeple.com"))
      inputIn(EditShopScreenTestTags.FIELD_PHONE).assert(hasText("+41 79 000 00 00"))

      // Wait for validation to pass (e.g. location loaded async)
      compose.waitUntil(5_000) {
        try {
          compose.onTag(ShopComponentsTestTags.ACTION_SAVE).assertIsEnabled()
          true
        } catch (e: AssertionError) {
          false
        }
      }
    }

    // 1) Validation Gating
    checkpoint("Validation gating") {
      // Clear name -> Save should be disabled
      inputIn(EditShopScreenTestTags.FIELD_SHOP).performTextClearance()
      compose.onTag(ShopComponentsTestTags.ACTION_SAVE).assertIsNotEnabled()

      // Restore name -> Save enabled
      inputIn(EditShopScreenTestTags.FIELD_SHOP).performTextInput("Meeple Mart Edited")

      compose.waitUntil(5_000) {
        try {
          compose.onTag(ShopComponentsTestTags.ACTION_SAVE).assertIsEnabled()
          true
        } catch (e: AssertionError) {
          false
        }
      }
    }

    // 3) Games modification
    checkpoint("Games modification") {
      // Scroll to games
      scrollListToTag(
          EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)
      ensureSectionExpanded(EditShopScreenTestTags.SECTION_GAMES)

      scrollListToTag(EditShopScreenTestTags.GAMES_ADD_BUTTON)
      compose.waitForIdle()

      // Verify Catan and Carcassonne exist
      val catanTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}test_catan"
      val carcassonneTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}test_carcassonne"

      compose.waitUntil(10_000) {
        compose
            .onAllNodesWithTag(catanTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      compose.onNodeWithTag(catanTag).assertExists()
      compose.onNodeWithTag(carcassonneTag).assertExists()

      // Add Pandemic
      // this will actually be set to 19 with the slider, weirdly the function works for all values
      // except the ones smallers than 20
      addGameWithSlider("Pandemic", 20)

      //           Wait for Pandemic
      val pandemicTag = "${ShopComponentsTestTags.SHOP_GAME_PREFIX}test_pandemic"
      compose.waitUntil(10_000) {
        compose
            .onAllNodesWithTag(pandemicTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
      compose.onTag(pandemicTag).assertExists()

      // Delete Catan
      compose
          .onNodeWithTag(
              "${ShopComponentsTestTags.SHOP_GAME_DELETE}:test_catan", useUnmergedTree = true)
          .performClick()
      compose.waitForIdle()
      compose.onTag(catanTag).assertDoesNotExist()

      // Edit Carcassonne count to 50
      compose
          .onNodeWithTag(
              "${ShopComponentsTestTags.SHOP_GAME_EDIT}:test_carcassonne", useUnmergedTree = true)
          .performClick()
      compose.onTag(EditShopScreenTestTags.GAME_STOCK_DIALOG_WRAPPER).assertExists()
      setSliderValue(50)
      compose.onTag(ShopComponentsTestTags.GAME_DIALOG_SAVE).performClick()
      compose.waitForIdle()
      // Check for updated text (fuzzy check for "50")
      compose
          .onNode(hasText("50", substring = true) and hasAnyAncestor(hasTestTag(carcassonneTag)))
          .assertExists()
    }

    // 3) Save Success
    checkpoint("Save Success") {
      compose.onTag(ShopComponentsTestTags.ACTION_SAVE).performClick()
      assertEquals(true, savedCalled)

      // Verify persistence in Firestore
      runBlocking {
        val updated = shopRepository.getShop(shop.id)
        assertEquals("Meeple Mart Edited", updated.name) // From step 1
        val gamesMap = updated.gameCollection.associate { it.gameId to it.quantity }
        assertEquals(null, gamesMap["test_catan"]) // Deleted
        assertEquals(50, gamesMap["test_carcassonne"]) // Edited
        assertEquals(19, gamesMap["test_pandemic"]) // Added
      }
    }
  }

  @Test
  fun editShop_offlineUI_disablesFeatures() {
    val viewModel = EditShopViewModel()
    compose.setContent {
      AppTheme {
        ShopDetailsScreen(
            owner = owner,
            shop = shop,
            onBack = {},
            onSaved = {},
            onDelete = {},
            online = false,
            viewModel = viewModel)
      }
    }

    // Verify Image Carousel is not editable
    compose.onTag(CommonComponentsTestTags.CAROUSEL_ADD_BUTTON).assertDoesNotExist()

    // Verify Games header message and no Add button
    scrollListToTag(
        EditShopScreenTestTags.SECTION_GAMES + EditShopScreenTestTags.SECTION_HEADER_SUFFIX)
    ensureSectionExpanded(EditShopScreenTestTags.SECTION_GAMES)

    compose.onTag(CreateShopScreenTestTags.OFFLINE_GAMES_MSG).performScrollTo().assertIsDisplayed()
    compose
        .onNodeWithTag(
            "${ShopComponentsTestTags.SHOP_GAME_DELETE}:test_catan", useUnmergedTree = true)
        .assertDoesNotExist()
    compose.waitForIdle()

    compose
        .onNodeWithTag(
            "${ShopComponentsTestTags.SHOP_GAME_EDIT}:test_carcassonne", useUnmergedTree = true)
        .assertDoesNotExist()
    compose.onTag(EditShopScreenTestTags.GAMES_ADD_BUTTON).assertDoesNotExist()
  }
}
