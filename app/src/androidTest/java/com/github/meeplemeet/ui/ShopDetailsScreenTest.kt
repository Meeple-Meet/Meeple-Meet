package com.github.meeplemeet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.shops.ShopScreen
import com.github.meeplemeet.ui.shops.ShopTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShopDetailsScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var vm: ShopViewModel
  private lateinit var dummyShop: Shop
  private lateinit var shop: Shop
  private lateinit var currentUser: Account
  private lateinit var owner: Account

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

  // --- helpers for reading text from semantics ---

  /** Returns all text strings currently present in the semantics tree. */
  private fun allTextsOnScreen(): List<String> {
    val matcher = SemanticsMatcher("any") { true }
    val nodes = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()

    return nodes.flatMap { node ->
      val texts = node.config.getOrNull(SemanticsProperties.Text) ?: emptyList()
      texts.map { it.text }
    }
  }

  /** True if there's any semantics node whose text equals [text]. */
  private fun hasTextAnywhere(text: String): Boolean {
    return compose
        .onAllNodes(hasText(text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
  }

  /** Names of games currently visible on screen (based on "Game Name X" texts). */
  private fun currentGameNames(): Set<String> {
    return allTextsOnScreen().filter { it.startsWith("Game Name ") }.toSet()
  }

  private fun availabilityHeader() = compose.onNodeWithTag("AVAILABILITY_HEADER")

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

    // Generate 20 games
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
              minAge = 6,
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
  fun shopScreen_smoke_currentUser_contactAvailabilityGames() {
    compose.setContent {
      AppTheme(themeMode = ThemeMode.DARK) {
        ShopScreen(
            shopId = shop.id, account = currentUser, onBack = {}, onEdit = {}, viewModel = vm)
      }
    }

    compose.waitForIdle()
    compose.waitUntilAtLeastOneExists(hasText(dummyShop.name), timeoutMillis = 5_000)

    /* 1  shop + contact section is mounted ----------------------------------------- */
    checkpoint("Shop header & contact section visible") {
      compose.onNodeWithText(dummyShop.name).assertExists()

      // Just check that the contact rows are present; detailed behaviour is tested in
      // ShopComponentsTest.
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_PHONE_TEXT).assertExists()
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_EMAIL_TEXT).assertExists()
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_WEBSITE_TEXT).assertExists()
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_ADDRESS_TEXT).assertExists()
    }

    /* 2  availability section integration ------------------------------------------ */
    checkpoint("Availability section visible and weekly dialog can be opened") {
      availabilityHeader().assertExists()

      availabilityHeader().performClick()

      // Today row exists (component-level formatting is covered in ShopComponentsTest)
      dummyOpeningHours.forEach { entry ->
        val tag = ShopComponentsTestTags.SHOP_DAY_PREFIX + entry.day
        checkpoint("Availability day exists: $tag") { compose.onNodeWithTag(tag).assertExists() }
      }

      // Bottom sheet shows the Close button from the component
      compose.waitForIdle()
      compose.onNodeWithText("Close").assertExists().performClick()
    }

    /* 3  pagination between pages --------------------------------------------------- */
    checkpoint("Pager pagination shows different games on different pages") {
      // Swipe specifically on the HorizontalPager, not the whole root
      val pagerNode = compose.onNodeWithTag(ShopTestTags.SHOP_GAME_PAGER)
      pagerNode.assertExists()

      val page0Names = currentGameNames()
      assert(page0Names.isNotEmpty()) { "Expected some games on first page" }

      // Go to next page
      pagerNode.performTouchInput { swipeLeft() }
      compose.waitForIdle()

      val page1Names = currentGameNames()
      assert(page1Names.isNotEmpty()) { "Expected some games on second page" }

      // With 20 games and 8 per page, the sets of names should differ between pages
      assert(page0Names != page1Names) {
        "Expected different games on different pages, but got same sets: $page0Names"
      }
    }

    /* 4  back button ---------------------------------------------------------------- */
    checkpoint("Back button exists and is clickable") {
      compose
          .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
          .assertExists()
          .performClick()
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun shopScreen_editButton_onlyForOwner() {
    var editCalled = false
    lateinit var switchToOwner: () -> Unit

    compose.setContent {
      AppTheme(themeMode = ThemeMode.DARK) {
        val ownerState = remember { mutableStateOf(false) }
        switchToOwner = { ownerState.value = true }

        val account = if (ownerState.value) owner else currentUser

        ShopScreen(
            shopId = shop.id,
            account = account,
            onBack = {},
            onEdit = { editCalled = true },
            viewModel = vm)
      }
    }

    compose.waitForIdle()
    compose.waitUntilAtLeastOneExists(hasText(dummyShop.name), timeoutMillis = 5_000)

    /* 1  non-owner: no edit button -------------------------------------------------- */
    checkpoint("Edit button hidden for non-owner") {
      compose
          .onAllNodesWithTag(ShopTestTags.SHOP_EDIT_BUTTON, useUnmergedTree = true)
          .assertCountEquals(0)
    }

    compose.runOnUiThread { switchToOwner() }
    compose.waitForIdle()

    /* 2  owner: edit button visible & clickable ------------------------------------- */
    checkpoint("Edit button visible and clickable for owner") {
      compose
          .onNodeWithTag(ShopTestTags.SHOP_EDIT_BUTTON, useUnmergedTree = true)
          .assertExists()
          .assertIsDisplayed()
          .assertHasClickAction()
          .performClick()

      // onEdit should have been invoked
      assert(editCalled)
    }
  }
}
