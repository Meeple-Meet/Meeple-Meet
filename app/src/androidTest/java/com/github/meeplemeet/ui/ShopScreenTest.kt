package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shared.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShopScreenIntegrationTest: FirestoreTests() {

    @get:Rule val composeTestRule = createComposeRule()
    private lateinit var vm : ShopViewModel
    private lateinit var discussionRepo: DiscussionRepository
    private lateinit var repo: ShopRepository
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var shop: Shop
    private lateinit var currentUser: Account
    private lateinit var owner: Account

    val address = Location(
        latitude = 0.0,
        longitude = 0.0,
        name = "123 Meeple St, Boardgame City"
    )

    val dummyOpeningHours = listOf(
        OpeningHours(day = 0, hours = listOf(Pair("09:00", "18:00"), Pair("19:00", "21:00"))),
        OpeningHours(day = 1, hours = listOf(Pair("09:00", "18:00"))),
        OpeningHours(day = 2, hours = listOf(Pair("09:00", "18:00"))),
        OpeningHours(day = 3, hours = listOf(Pair("09:00", "18:00"))),
        OpeningHours(day = 4, hours = listOf(Pair("09:00", "18:00"))),
        OpeningHours(day = 5, hours = listOf(Pair("10:00", "16:00"))),
        OpeningHours(day = 6, hours = emptyList())
    )

    val dummyGame = Game(uid = "g1", name = "Catan", imageURL = "test", description = "this game is cool", minPlayers = 1, maxPlayers = 4, recommendedPlayers = null, averagePlayTime = null, genres = emptyList())
    private val dummyGames = listOf(
        Pair(
            Game(uid = "g1", name = "Catan", imageURL = "test", description = "this game is cool", minPlayers = 1, maxPlayers = 4, recommendedPlayers = null, averagePlayTime = null, genres = emptyList()),
            2
        ),
        Pair(
            Game(uid = "g2", name = "Carcassone", imageURL = "test", description = "this game is cool", minPlayers = 1, maxPlayers = 4, recommendedPlayers = null, averagePlayTime = null, genres = emptyList()),
            1
        ))

    private val dummyShop = Shop(
        id = "1",
        name = "Meeple Meet",
        owner = account,
        phone = "123-456-7890",
        email = "info@meeplehaven.com",
        address = address,
        website = "www.meeplemeet.com",
        openingHours = dummyOpeningHours,
        gameCollection = dummyGames
    )
    @Before
    fun setup() {

        repo = ShopRepository()
        discussionRepo = DiscussionRepository()
        vm = ShopViewModel(repo)

        currentUser = runBlocking {
            discussionRepo.createAccount(
                userHandle = "testuser_${System.currentTimeMillis()}",
                name = "Alice",
                email = "alice@test.com",
                photoUrl = null
            )
        }
        owner = runBlocking {
            discussionRepo.createAccount(
                userHandle = "testuser_${System.currentTimeMillis()}",
                name = "Owner",
                email = "Owner@test.com",
                photoUrl = null
            )}

        shop = runBlocking {
            repo.createShop(
                owner = owner,
                name = dummyShop.name,
                phone = dummyShop.phone,
                email = dummyShop.email,
                website = dummyShop.website,
                address = dummyShop.address,
                openingHours = dummyShop.openingHours,
                gameCollection = dummyShop.gameCollection
            )
        }
        shop = runBlocking {
            repo.getShop(shop.id)
        }
        currentUser = runBlocking {
            discussionRepo.getAccount(currentUser.uid)
        }
        owner = runBlocking {
            discussionRepo.getAccount(owner.uid)
        }

    }


    @Test
    fun test_shopScreenDisplaysCorrectly() {
        composeTestRule.setContent {
            ShopScreen(
                shopId = shop.id,
                account = currentUser,
                onBack = {},
                onEdit = {},
                viewModel = vm
            )
        }

        // Verify shop name is displayed
        composeTestRule.onNodeWithText(dummyShop.name).assertExists()

        // Verify contact info is displayed using tags
        composeTestRule.onNodeWithTag(TestTags.SHOP_PHONE_TEXT).assertExists().assertTextEquals(dummyShop.phone)
        composeTestRule.onNodeWithTag(TestTags.SHOP_EMAIL_TEXT).assertExists().assertTextEquals(dummyShop.email)
        composeTestRule.onNodeWithTag(TestTags.SHOP_WEBSITE_TEXT).assertExists().assertTextEquals(dummyShop.website)
        composeTestRule.onNodeWithTag(TestTags.SHOP_ADDRESS_TEXT).assertExists().assertTextEquals(dummyShop.address.name)

        // Verify availability (opening hours) for each day
        dummyOpeningHours.forEach { openingHours ->
            val dayTag = "${TestTags.SHOP_DAY_PREFIX}${openingHours.day}"
            val hoursTag = "${TestTags.SHOP_DAY_PREFIX}${openingHours.day}_HOURS"
            composeTestRule.onNodeWithTag(dayTag).assertExists()
            composeTestRule.onNodeWithTag(hoursTag).assertExists()
            if (openingHours.hours.isEmpty()) {
                composeTestRule.onNodeWithTag(hoursTag).assertTextEquals("Closed")
            } else {
                val hoursText = openingHours.hours.joinToString(", ") { "${it.first} - ${it.second}" }
                composeTestRule.onNodeWithTag(hoursTag).assertTextEquals(hoursText)
            }
        }

        // Verify game list is displayed by tag using SHOP_GAME_PREFIX
        dummyShop.gameCollection.forEach { (game, quantity) ->
            val gameTag = "${TestTags.SHOP_GAME_PREFIX}${game.uid}"
            composeTestRule.onNodeWithTag(gameTag).assertExists()
            composeTestRule.onNodeWithTag(gameTag).assertTextContains(game.name)
            composeTestRule.onNodeWithTag(gameTag).assertTextContains(quantity.toString())
        }

        // Verify back button and perform click
        composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertExists().performClick()

        // Verify edit button and perform click
        composeTestRule.onNodeWithContentDescription("Edit").assertDoesNotExist()
    }
}
