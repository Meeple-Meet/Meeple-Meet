package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.AnnotatedString
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.shops.ShopScreen
import com.github.meeplemeet.ui.shops.ShopTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FakeClipboardManager : androidx.compose.ui.platform.ClipboardManager {
  var copiedText: String? = null

  override fun getText(): AnnotatedString? {
    return copiedText?.let { AnnotatedString(it) }
  }

  override fun setText(annotatedString: AnnotatedString) {
    copiedText = annotatedString.text
  }
}

class ShopDetailsScreenTest : FirestoreTests() {

  @get:Rule val composeTestRule = createComposeRule()
  private lateinit var vm: ShopViewModel
  private lateinit var dummyShop: Shop
  private lateinit var shop: Shop
  private lateinit var currentUser: Account
  private lateinit var owner: Account

  // Use a consistent address for testing
  private val address =
      Location(latitude = 0.0, longitude = 0.0, name = "123 Meeple St, Boardgame City")

  private val dummyOpeningHours =
      listOf(
          OpeningHours(
              day = 0, hours = listOf(TimeSlot("09:00", "18:00"), TimeSlot("19:00", "21:00"))),
          OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 5, hours = listOf(TimeSlot("10:00", "16:00"))),
          OpeningHours(day = 6, hours = emptyList()))

  private lateinit var dummyGames: List<Pair<Game, Int>>

  // Helper to seed Firestore
  private fun seedGamesForTest(db: FirebaseFirestore, games: List<Game>) = runBlocking {
    games.forEach { game ->
      val data =
          mapOf(
              "name" to game.name,
              "imageURL" to game.imageURL,
              "description" to game.description,
              "minPlayers" to game.minPlayers,
              "maxPlayers" to game.maxPlayers)
      db.collection("games").document(game.uid).set(data).await()
    }
  }

  @Before
  fun setup() {
    val testDb = FirebaseFirestore.getInstance()

    // Generate 20 games. Game 0 has 100 items (tests 99+), Game 1 has 5.
    val gamesList =
        List(20) { i ->
          Game(
              uid = "g$i",
              name = "Game Name $i",
              imageURL = "https://example.com/game$i.png",
              description = "Description $i",
              minPlayers = 2,
              maxPlayers = 4,
              recommendedPlayers = 4,
              averagePlayTime = 60,
              genres = emptyList())
        }

    seedGamesForTest(testDb, gamesList)

    vm = ShopViewModel()

    dummyGames =
        gamesList.mapIndexed { index, game ->
          val stock = if (index == 0) 100 else 5
          game to stock
        }

    currentUser = runBlocking {
      accountRepository.createAccount(
          userHandle = "testuser_${System.currentTimeMillis()}",
          name = "Alice",
          email = "alice@test.com",
          photoUrl = null)
    }
    owner = runBlocking {
      accountRepository.createAccount(
          userHandle = "owner_${System.currentTimeMillis()}",
          name = "Owner",
          email = "Owner@test.com",
          photoUrl = null)
    }

    dummyShop =
        Shop(
            id = "1",
            name = "Meeple Meet",
            owner = owner,
            phone = "123-456-7890",
            email = "info@meeplehaven.com",
            address = address,
            website = "www.meeplemeet.com",
            openingHours = dummyOpeningHours,
            gameCollection = dummyGames)

    shop = runBlocking {
      shopRepository.createShop(
          owner = owner,
          name = dummyShop.name,
          phone = dummyShop.phone,
          email = dummyShop.email,
          website = dummyShop.website,
          address = dummyShop.address,
          openingHours = dummyShop.openingHours,
          gameCollection = dummyShop.gameCollection)
    }

    shop = runBlocking { shopRepository.getShop(shop.id) }
    currentUser = runBlocking { accountRepository.getAccount(currentUser.uid) }
    owner = runBlocking { accountRepository.getAccount(owner.uid) }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test_shopScreenDisplaysCorrectly() {
    val fakeClipboard = FakeClipboardManager()

    composeTestRule.setContent {
      CompositionLocalProvider(LocalClipboardManager provides fakeClipboard) {
        AppTheme(themeMode = ThemeMode.DARK) {
          ShopScreen(
              shopId = shop.id, account = currentUser, onBack = {}, onEdit = {}, viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilAtLeastOneExists(hasText(dummyShop.name), timeoutMillis = 5_000)

    // --- Info Checks ---
    composeTestRule.onNodeWithText(dummyShop.name).assertExists()

    // FIX: Check children of the Column for the text
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_PHONE_TEXT)
        .onChildren()
        .filter(hasText(dummyShop.phone))
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_EMAIL_TEXT)
        .onChildren()
        .filter(hasText(dummyShop.email))
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_WEBSITE_TEXT)
        .onChildren()
        .filter(hasText(dummyShop.website))
        .assertCountEquals(1)
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_ADDRESS_TEXT)
        .onChildren()
        .filter(hasText(dummyShop.address.name))
        .assertCountEquals(1)

    // --- Clipboard Checks ---
    val contactItems =
        listOf(
            ShopTestTags.SHOP_PHONE_BUTTON to dummyShop.phone,
            ShopTestTags.SHOP_EMAIL_BUTTON to dummyShop.email)
    contactItems.forEach { (buttonTag, expectedText) ->
      composeTestRule.onNodeWithTag(buttonTag).performClick()
      assert(fakeClipboard.copiedText == expectedText)
    }

    // --- Availability Checks (FIXED) ---
    // 1. Check that TODAY is displayed
    val todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    composeTestRule
        .onNodeWithTag("${ShopTestTags.SHOP_DAY_PREFIX}${todayIndex}_HOURS")
        .assertExists()

    // 2. OPEN THE DIALOG to see other days
    composeTestRule.onNodeWithTag("${ShopTestTags.SHOP_DAY_PREFIX}NAVIGATE").performClick()
    composeTestRule.waitForIdle()

    // 3. Now verify all days exist
    dummyOpeningHours.forEach { openingHours ->
      val dayTag = "${ShopTestTags.SHOP_DAY_PREFIX}${openingHours.day}"
      composeTestRule.waitUntilAtLeastOneExists(hasTestTag(dayTag), timeoutMillis = 2000)
      composeTestRule.onNodeWithTag(dayTag).assertExists()
    }

    // 4. Close dialog
    composeTestRule.onNodeWithText("Close").performClick()

    // --- Game List Check ---
    // Only checking the first visible game to ensure list loaded
    val firstGame = dummyGames[0].first
    val gameTag = "${ShopTestTags.SHOP_GAME_PREFIX}${firstGame.uid}"
    composeTestRule.onNodeWithTag(gameTag, useUnmergedTree = true).assertExists()

    // --- Back Button Check ---
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertExists().performClick()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test_gamePaginationAndStockLogic() {
    composeTestRule.setContent {
      AppTheme(themeMode = ThemeMode.DARK) {
        ShopScreen(
            shopId = shop.id, account = currentUser, onBack = {}, onEdit = {}, viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilAtLeastOneExists(hasText(dummyShop.name), timeoutMillis = 5_000)

    composeTestRule.onNodeWithTag(ShopTestTags.SHOP_GAME_PAGER).assertExists()

    // --- 1. Check Stock Logic: "99+" ---
    val game0 = dummyGames[0].first

    // Name Check (Use Unmerged Tree because parent is clickable)
    composeTestRule
        .onNodeWithTag("${ShopTestTags.SHOP_GAME_NAME_PREFIX}${game0.uid}", useUnmergedTree = true)
        .assertTextEquals(game0.name)

    // Stock Check (FIXED: Check children for text)
    composeTestRule
        .onNodeWithTag("${ShopTestTags.SHOP_GAME_STOCK_PREFIX}${game0.uid}", useUnmergedTree = true)
        .assertIsDisplayed()
        .onChildren() // Look inside the Box
        .filter(hasText("99+")) // Find the text
        .assertCountEquals(1) // Assert it exists

    // --- 2. Check Stock Logic: Exact number (Game 1) ---
    val game1 = dummyGames[1].first
    composeTestRule
        .onNodeWithTag("${ShopTestTags.SHOP_GAME_STOCK_PREFIX}${game1.uid}", useUnmergedTree = true)
        .onChildren()
        .filter(hasText("5"))
        .assertCountEquals(1)

    // --- 3. Pagination Checks ---
    val gamePage2 = dummyGames[8].first

    // Verify game on page 2 is NOT visible yet
    composeTestRule
        .onNodeWithTag(
            "${ShopTestTags.SHOP_GAME_NAME_PREFIX}${gamePage2.uid}", useUnmergedTree = true)
        .assertDoesNotExist()

    // Swipe
    composeTestRule.onNodeWithTag(ShopTestTags.SHOP_GAME_PAGER).performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()

    // Verify game on page 2 IS visible now
    composeTestRule
        .onNodeWithTag(
            "${ShopTestTags.SHOP_GAME_NAME_PREFIX}${gamePage2.uid}", useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(gamePage2.name)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test_shopScreenEditButtonVisibleForOwner() {
    var edit = false
    composeTestRule.setContent {
      ShopScreen(
          shopId = shop.id, account = owner, onBack = {}, onEdit = { edit = true }, viewModel = vm)
    }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilAtLeastOneExists(hasText(dummyShop.name), timeoutMillis = 5_000)

    composeTestRule.onNodeWithTag(ShopTestTags.SHOP_EDIT_BUTTON).assertExists().performClick()
    assert(edit)
  }
}
