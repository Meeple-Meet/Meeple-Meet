package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.sessions.FirestoreGameRepository
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shared.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.firestore.FirebaseFirestore
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

  override fun setText(text: androidx.compose.ui.text.AnnotatedString) {
    copiedText = text.text
  }
}

class ShopDetailsScreenTest : FirestoreTests() {

  @get:Rule val composeTestRule = createComposeRule()
  private lateinit var vm: ShopViewModel
  private lateinit var discussionRepo: DiscussionRepository
  private lateinit var repo: ShopRepository
  private lateinit var games: List<Game>
  private lateinit var dummyShop: Shop
  private lateinit var shop: Shop
  private lateinit var currentUser: Account
  private lateinit var owner: Account
  private lateinit var gameRep: FirestoreGameRepository

  val address = Location(latitude = 0.0, longitude = 0.0, name = "123 Meeple St, Boardgame City")

  val dummyOpeningHours =
      listOf(
          OpeningHours(
              day = 0, hours = listOf(TimeSlot("09:00", "18:00"), TimeSlot("19:00", "21:00"))),
          OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 5, hours = listOf(TimeSlot("10:00", "16:00"))),
          OpeningHours(day = 6, hours = emptyList()))

  val dummyGame =
      Game(
          uid = "g1",
          name = "Catan",
          imageURL = "test",
          description = "this game is cool",
          minPlayers = 1,
          maxPlayers = 4,
          recommendedPlayers = null,
          averagePlayTime = null,
          genres = emptyList())
  private var dummyGames =
      listOf(
          Pair(
              Game(
                  uid = "g1",
                  name = "Catan",
                  imageURL = "test",
                  description = "this game is cool",
                  minPlayers = 1,
                  maxPlayers = 4,
                  recommendedPlayers = null,
                  averagePlayTime = null,
                  genres = emptyList()),
              2),
          Pair(
              Game(
                  uid = "g2",
                  name = "Carcassone",
                  imageURL = "test",
                  description = "this game is cool",
                  minPlayers = 1,
                  maxPlayers = 4,
                  recommendedPlayers = null,
                  averagePlayTime = null,
                  genres = emptyList()),
              1))

  fun seedGamesForTest(db: FirebaseFirestore) = runBlocking {
    val g1 =
        mapOf(
            "name" to "Catan",
            "imageURL" to "https://example.com/catan.png",
            "description" to "this game is cool",
            "minPlayers" to 1,
            "maxPlayers" to 4,
            "recommendedPlayers" to null,
            "averagePlayTime" to null,
            "genres" to emptyList<Int>())
    val g2 =
        mapOf(
            "name" to "Chess",
            "imageURL" to "https://example.com/chess.png",
            "description" to "mind game",
            "minPlayers" to 2,
            "maxPlayers" to 2,
            "recommendedPlayers" to null,
            "averagePlayTime" to null,
            "genres" to emptyList<Int>())
    db.collection("games").document("g1").set(g1).await()
    db.collection("games").document("g2").set(g2).await()
  }

  @Before
  fun setup() {
    val testDb = FirebaseFirestore.getInstance()

    // seed documents
    seedGamesForTest(testDb)

    repo = ShopRepository()
    gameRep = FirestoreGameRepository(testDb)
    discussionRepo = DiscussionRepository()
    vm = ShopViewModel(repo)

    games = runBlocking { gameRep.getGames(2) }
    dummyGames = listOf(Pair(games[0], 2), Pair(games[1], 1))

    currentUser = runBlocking {
      discussionRepo.createAccount(
          userHandle = "testuser_${System.currentTimeMillis()}",
          name = "Alice",
          email = "alice@test.com",
          photoUrl = null)
    }
    owner = runBlocking {
      discussionRepo.createAccount(
          userHandle = "testuser_${System.currentTimeMillis()}",
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
      repo.createShop(
          owner = owner,
          name = dummyShop.name,
          phone = dummyShop.phone,
          email = dummyShop.email,
          website = dummyShop.website,
          address = dummyShop.address,
          openingHours = dummyShop.openingHours,
          gameCollection = dummyShop.gameCollection)
    }
    shop = runBlocking { repo.getShop(shop.id) }
    currentUser = runBlocking { discussionRepo.getAccount(currentUser.uid) }
    owner = runBlocking { discussionRepo.getAccount(owner.uid) }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test_shopScreenDisplaysCorrectly() {
    val fakeClipboard = FakeClipboardManager()

    composeTestRule.setContent {
      CompositionLocalProvider(LocalClipboardManager provides fakeClipboard) {
        AppTheme(themeMode = ThemeMode.DARK) {
          ShopDetailsScreen(
              shopId = shop.id, account = currentUser, onBack = {}, onEdit = {}, viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilAtLeastOneExists(hasText(dummyShop.name), timeoutMillis = 5_000)
    // Verify shop name is displayed
    composeTestRule.onNodeWithText(dummyShop.name).assertExists()

    // Verify contact info is displayed using tags
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_PHONE_TEXT)
        .assertExists()
        .assertTextEquals("- Phone: ${dummyShop.phone}")
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_EMAIL_TEXT)
        .assertExists()
        .assertTextEquals("- Email: ${dummyShop.email}")
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_WEBSITE_TEXT)
        .assertExists()
        .assertTextEquals("- Website: ${dummyShop.website}")
    composeTestRule
        .onNodeWithTag(ShopTestTags.SHOP_ADDRESS_TEXT)
        .assertExists()
        .assertTextEquals("- Address: ${dummyShop.address.name}")

    // Verify that clicking the icon buttons copies content to clipboard
    val contactItems =
        listOf(
            ShopTestTags.SHOP_PHONE_BUTTON to "- Phone: ${dummyShop.phone}",
            ShopTestTags.SHOP_EMAIL_BUTTON to "- Email: ${dummyShop.email}",
            ShopTestTags.SHOP_ADDRESS_BUTTON to "- Address: ${dummyShop.address.name}",
            ShopTestTags.SHOP_WEBSITE_BUTTON to "- Website: ${dummyShop.website}")

    contactItems.forEach { (buttonTag, expectedText) ->
      composeTestRule.onNodeWithTag(buttonTag).performClick()
      assert(fakeClipboard.copiedText == expectedText) {
        "Clipboard content for $buttonTag does not match: ${fakeClipboard.copiedText}"
      }
    }

    // Verify availability (opening hours) for each day
    dummyOpeningHours.forEach { openingHours ->
      val dayTag = "${ShopTestTags.SHOP_DAY_PREFIX}${openingHours.day}"
      composeTestRule.onNodeWithTag(dayTag).assertExists()

      if (openingHours.hours.isEmpty()) {
        // Closed day
        composeTestRule.onNodeWithTag("${dayTag}_HOURS").assertExists().assertTextEquals("Closed")
      } else {
        // For each available time slot
        openingHours.hours.forEachIndexed { idx, slot ->
          val hoursTag = "${dayTag}_HOURS_${idx}"
          composeTestRule
              .onNodeWithTag(hoursTag)
              .assertExists()
              .onChildren()
              .filter(hasText("${slot.open} - ${slot.close}"))
              .assertCountEquals(1)
        }
      }
    }
    // Verify game list is displayed by tag using SHOP_GAME_PREFIX
    dummyShop.gameCollection.forEach { (game, quantity) ->
      val gameTag = "${ShopTestTags.SHOP_GAME_PREFIX}${game.uid}"

      // Find a node that has the test tag and contains the game name anywhere in its subtree
      composeTestRule.onNode(hasTestTag(gameTag) and hasText(game.name)).assertExists()

      // Verify the count text somewhere under the same tagged node
      composeTestRule.onNode(hasTestTag(gameTag) and hasText(quantity.toString())).assertExists()
    }

    // Verify back button and perform click
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertExists().performClick()

    // Verify edit button does not exist for non-owner
    composeTestRule.onNodeWithContentDescription("Edit").assertDoesNotExist()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test_shopScreenEditButtonVisibleForOwner() {
    var edit = false
    composeTestRule.setContent {
      ShopDetailsScreen(
          shopId = shop.id,
          account = owner, // Pass the owner account
          onBack = {},
          onEdit = { edit = true },
          viewModel = vm)
    }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilAtLeastOneExists(hasText(dummyShop.name), timeoutMillis = 5_000)

    // Verify edit button exists for owner
    composeTestRule.onNodeWithTag(ShopTestTags.SHOP_EDIT_BUTTON).assertExists().performClick()
    assert(edit)
  }
}
