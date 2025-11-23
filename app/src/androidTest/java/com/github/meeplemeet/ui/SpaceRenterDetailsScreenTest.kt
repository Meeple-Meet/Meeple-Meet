package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterViewModel
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.SpaceRenterComponentsTestTags
import com.github.meeplemeet.ui.space_renter.SpaceRenterScreen
import com.github.meeplemeet.ui.space_renter.SpaceRenterTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpaceRenterDetailsScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var vm: SpaceRenterViewModel
  private lateinit var renter: SpaceRenter
  private lateinit var currentUser: Account
  private lateinit var owner: Account

  private var theme by mutableStateOf(ThemeMode.LIGHT)

  /* ------- semantic helpers ------- */
  private fun phoneText() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_PHONE_TEXT, useUnmergedTree = true)

  private fun emailText() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_EMAIL_TEXT, useUnmergedTree = true)

  private fun addressText() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_ADDRESS_TEXT, useUnmergedTree = true)

  private fun websiteText() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_WEBSITE_TEXT, useUnmergedTree = true)

  private fun phoneBtn() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_PHONE_BUTTON, useUnmergedTree = true)

  private fun emailBtn() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_EMAIL_BUTTON, useUnmergedTree = true)

  private fun addressBtn() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_ADDRESS_BUTTON, useUnmergedTree = true)

  private fun websiteBtn() =
      compose.onNodeWithTag(ShopComponentsTestTags.SHOP_WEBSITE_BUTTON, useUnmergedTree = true)

  private fun spaceRow(i: Int) =
      compose.onNodeWithTag(
          SpaceRenterComponentsTestTags.SPACE_ROW_PREFIX + i, useUnmergedTree = true)

  private fun availabilityHeader() = compose.onNodeWithTag("AVAILABILITY_HEADER")

  private fun reservationNoSelection() = compose.onNodeWithTag("RESERVATION_NO_SELECTION")

  private fun reservationSelected() = compose.onNodeWithTag("RESERVATION_WITH_SELECTION")

  /* -------------------------------- */

  private val address = Location(0.0, 0.0, "123 Meeple St, Boardgame City")

  private val dummyOpeningHours =
      listOf(
          OpeningHours(0, listOf(TimeSlot("09:00", "18:00"), TimeSlot("19:00", "21:00"))),
          OpeningHours(1, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(2, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(3, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(4, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(5, listOf(TimeSlot("10:00", "16:00"))),
          OpeningHours(6, emptyList()))

  private val dummySpaces =
      listOf(
          Space(4, 10.0),
          Space(8, 20.0),
          Space(1, 50.0),
          Space(1, 0.0),
          Space(3, 2.0),
          Space(5, 21.0),
          Space(6, 15.0))

  @Before
  fun setup() = runBlocking {
    vm = SpaceRenterViewModel()

    currentUser = accountRepository.createAccount("user_${now()}", "Alice", "alice@test.com", null)
    owner = accountRepository.createAccount("owner_${now()}", "Owner", "owner@test.com", null)

    val created =
        spaceRenterRepository.createSpaceRenter(
            owner,
            "Meeple Spaces",
            "555-123-0000",
            "spaces@meeple.com",
            "www.meeplespaces.com",
            address,
            dummyOpeningHours,
            dummySpaces)

    renter = spaceRenterRepository.getSpaceRenter(created.id)
    currentUser = accountRepository.getAccount(currentUser.uid)
    owner = accountRepository.getAccount(owner.uid)
  }

  class FakeClipboardManager : androidx.compose.ui.platform.ClipboardManager {
    var copiedText: String? = null

    override fun getText(): AnnotatedString? {
      return copiedText?.let { AnnotatedString(it) }
    }

    override fun setText(text: androidx.compose.ui.text.AnnotatedString) {
      copiedText = text.text
    }
  }

  @Test
  fun all_space_renter_details_tests() {
    val clipboard = FakeClipboardManager()
    var fired = false

    compose.setContent {
      CompositionLocalProvider(LocalClipboardManager provides clipboard) {
        AppTheme(themeMode = theme) {
          SpaceRenterScreen(
              spaceId = renter.id,
              account = owner,
              viewModel = vm,
              onBack = {},
              onEdit = { fired = true })
        }
      }
    }

    compose.waitUntil(timeoutMillis = 5000) { vm.spaceRenter.value != null }

    checkpoint("Title visible") { compose.onNodeWithText(renter.name).assertExists() }

    /* CONTACT SECTION */
    checkpoint("Phone text") { phoneText().assertTextEquals(renter.phone) }
    checkpoint("Email text") { emailText().assertTextEquals(renter.email) }
    checkpoint("Address text") { addressText().assertTextEquals(renter.address.name) }
    checkpoint("Website text") { websiteText().assertTextEquals(renter.website) }

    listOf(
            phoneBtn() to renter.phone,
            emailBtn() to renter.email,
            addressBtn() to renter.address.name,
            websiteBtn() to renter.website)
        .forEach { (btn, expected) ->
          checkpoint("Copy via ${btn.fetchSemanticsNode().config}") {
            btn.performClick()
            assert(clipboard.copiedText == expected)
          }
        }

    /* AVAILABILITY SECTION POPUP */

    checkpoint("Availability header exists") { availabilityHeader().assertExists() }

    availabilityHeader().performClick()

    dummyOpeningHours.forEach { entry ->
      val tag = SpaceRenterTestTags.SPACE_RENTER_DAY_PREFIX + entry.day
      checkpoint("Availability day exists: $tag") { compose.onNodeWithTag(tag).assertExists() }
    }

    Espresso.pressBack()

    checkpoint("Availability popup dismissed") {
      availabilityHeader().performClick()
      compose.onNodeWithText("Close").assertExists().performClick()
    }

    /* Spaces section */

    compose.onRoot().performTouchInput { swipeUp() }
    compose.waitForIdle()

    var currentPageStart = 0

    dummySpaces.forEachIndexed { index, _ ->
      val i = index + 1

      // If the index is not on the current page, swipe to the next one
      if (index < currentPageStart || index >= currentPageStart + 3) {
        // Swipe from any visible space on the current page
        spaceRow(currentPageStart).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        currentPageStart += 3
      }

      checkpoint("Space row exists: $i") { spaceRow(index).assertExists() }
    }

    currentPageStart = (dummySpaces.size / 3) * 3
    if (currentPageStart >= dummySpaces.size) currentPageStart -= 3

    // Loop backward: last index → 0
    for (index in dummySpaces.indices.reversed()) {

      // If the index is not in the current visible page, swipe back (right)
      if (index < currentPageStart || index >= currentPageStart + 3) {

        // Swipe from any visible row on the current page (use the start index)
        spaceRow(currentPageStart).performTouchInput { swipeRight() }
        compose.waitForIdle()

        currentPageStart -= 3
        if (currentPageStart < 0) currentPageStart = 0
      }

      val i = index + 1
      checkpoint("Space row exists (reverse): $i") { spaceRow(index).assertExists() }
    }

    /* RESERVATION BAR — NO SELECTION */
    checkpoint("Reservation bar shows 'select a space' state") {
      reservationNoSelection().assertExists()
    }

    /* SELECT FIRST SPACE */
    spaceRow(0).performClick()

    checkpoint("Reservation bar shows selected state") { reservationSelected().assertExists() }
  }

  @Test
  fun edit_button_visible_for_owner() {
    var fired = false

    compose.setContent {
      SpaceRenterScreen(
          spaceId = renter.id, account = owner, viewModel = vm, onEdit = { fired = true })
    }

    compose.waitUntil(timeoutMillis = 5000) { vm.spaceRenter.value != null }

    compose
        .onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_EDIT_BUTTON)
        .assertExists()
        .performClick()

    assert(fired)
    checkpoint("edit_button_visible_for_owner") {
      compose
          .onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_EDIT_BUTTON)
          .assertExists()
          .performClick()
      assert(fired)
    }
  }

  /* ------ helper ------ */
  private fun now() = System.currentTimeMillis().toString()
}
