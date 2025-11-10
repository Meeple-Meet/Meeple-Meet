// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
package com.github.meeplemeet.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.space_renter.CreateSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.components.SpaceRenterComponentsTestTags
import com.github.meeplemeet.ui.sessions.SessionTestTags
import com.github.meeplemeet.ui.space_renter.AddSpaceRenterContent
import com.github.meeplemeet.ui.space_renter.AddSpaceRenterUi
import com.github.meeplemeet.ui.space_renter.CreateSpaceRenterScreen
import com.github.meeplemeet.ui.space_renter.CreateSpaceRenterScreenTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateSpaceRenterScreenTest : FirestoreTests() {

  /* ───────────────────────────────── RULES ───────────────────────────────── */

  @get:Rule val compose = createComposeRule()

  /* ────────────────────────────── FIXTURES / FX ──────────────────────────── */

  private val owner =
      Account(uid = "owner1", handle = "@owner", name = "Owner", email = "owner@example.com")

  /* ────────────────────────────── EXT HELPERS ────────────────────────────── */

  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  private fun ComposeTestRule.onTags(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true)

  /** Scroll the outer LazyColumn to bring a tagged node into view. */
  private fun scrollListToTag(tag: String) {
    compose.onTag(CreateSpaceRenterScreenTestTags.LIST).performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
  }

  /** Expand a collapsible section only if its content isn't currently in the tree. */
  private fun ensureSectionExpanded(sectionBaseTag: String) {
    val toggleTag = sectionBaseTag + ShopFormTestTags.SECTION_TOGGLE_SUFFIX
    val contentTag = sectionBaseTag + ShopFormTestTags.SECTION_CONTENT_SUFFIX

    scrollListToTag(toggleTag)
    compose.waitForIdle()

    val isExpanded = compose.onTags(contentTag).fetchSemanticsNodes().isNotEmpty()
    if (!isExpanded) {
      compose.onTag(toggleTag).assertExists().performClick()
      compose.waitForIdle()
    }

    scrollListToTag(contentTag)
    compose.waitForIdle()
  }

  /** Returns the LabeledField INPUT inside the given wrapper (FIELD_* tag). */
  private fun inputIn(wrapperTag: String) =
      compose.onNode(
          hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
              hasAnyAncestor(hasTestTag(wrapperTag)),
          useUnmergedTree = true)

  /** Convenience accessors for Space row sub-tags. */
  private fun spaceRowTag(index: Int) = SpaceRenterComponentsTestTags.SPACE_ROW_PREFIX + index

  private fun seatsFieldTag(index: Int) =
      spaceRowTag(index) + SpaceRenterComponentsTestTags.SPACE_ROW_SEATS_FIELD_SUFFIX

  private fun priceFieldTag(index: Int) =
      spaceRowTag(index) + SpaceRenterComponentsTestTags.SPACE_ROW_PRICE_FIELD_SUFFIX

  private fun deleteButtonTag(index: Int) =
      spaceRowTag(index) + SpaceRenterComponentsTestTags.SPACE_ROW_DELETE_SUFFIX

  /** Fill required fields (name, email, address). */
  private fun fillRequiredFields(
      name: String = "Meeple Hub",
      email: String = "space@host.com",
      address: String = "123 Meeple Street"
  ) {
    ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_REQUIRED)

    // Name
    inputIn(ShopFormTestTags.FIELD_SHOP).assertExists().performTextClearance()
    inputIn(ShopFormTestTags.FIELD_SHOP).performTextInput(name)

    // Email
    inputIn(ShopFormTestTags.FIELD_EMAIL).assertExists().performTextClearance()
    inputIn(ShopFormTestTags.FIELD_EMAIL).performTextInput(email)

    // Address
    compose.onTag(SessionTestTags.LOCATION_FIELD).assertExists().performClick()
    compose.onTag(SessionTestTags.LOCATION_FIELD).performTextClearance()
    compose.onTag(SessionTestTags.LOCATION_FIELD).performTextInput(address)
  }

  /** Optional fields (phone, website). */
  private fun fillOptionalFields(
      phone: String = "+41 79 555 55 55",
      website: String = "https://example.com"
  ) {
    ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_REQUIRED)
    inputIn(ShopFormTestTags.FIELD_PHONE).assertExists().performTextClearance()
    inputIn(ShopFormTestTags.FIELD_PHONE).performTextInput(phone)
    inputIn(ShopFormTestTags.FIELD_LINK).assertExists().performTextClearance()
    inputIn(ShopFormTestTags.FIELD_LINK).performTextInput(website)
  }

  /** Flip Sunday to “Open 24 hours” via dialog. */
  private fun setAnyOpeningHoursViaDialog() {
    ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_AVAILABILITY)

    scrollListToTag(
        CreateSpaceRenterScreenTestTags.SECTION_AVAILABILITY +
            ShopFormTestTags.SECTION_CONTENT_SUFFIX)
    compose.waitForIdle()

    compose.onTags(ShopComponentsTestTags.DAY_ROW_EDIT).assertCountEquals(7)[0].performClick()

    compose.onTag(ShopFormTestTags.OPENING_HOURS_DIALOG_WRAPPER).assertExists()
    compose.onTag(ShopComponentsTestTags.DIALOG_OPEN24_CHECKBOX).assertExists().performClick()
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).assertIsEnabled().performClick()
    compose.waitForIdle()
  }

  /** Open "Spaces" section and click Add Space. */
  private fun addSpace() {
    ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_SPACES)
    scrollListToTag(CreateSpaceRenterScreenTestTags.SPACES_ADD_BUTTON)
    compose.onTag(CreateSpaceRenterScreenTestTags.SPACES_ADD_BUTTON).assertExists().performClick()
    compose.waitForIdle()
  }

  /* ────────────────────────────── TESTS ────────────────────────────── */

  /**
   * Core validation gating:
   * - create disabled initially and until required pieces are set
   * - enabled after: name + email + address + opening hours + >=1 space
   * - seats and price inputs are clamped by component (cannot make them invalid)
   * - discard clears and disables
   */
  @Test
  fun validation_and_spaces_gating_singleComposition() {
    var backCalled = false

    compose.setContent {
      AppTheme {
        CreateSpaceRenterScreen(
            owner = owner,
            onBack = { backCalled = true },
            onCreated = {},
            viewModel = CreateSpaceRenterViewModel())
      }
    }

    compose.onTag(CreateSpaceRenterScreenTestTags.SCAFFOLD).assertExists()
    compose.onTag(CreateSpaceRenterScreenTestTags.TOPBAR).assertExists()
    compose.onTag(CreateSpaceRenterScreenTestTags.TITLE).assertExists()
    compose.onTag(CreateSpaceRenterScreenTestTags.NAV_BACK).assertExists()
    compose.onTag(CreateSpaceRenterScreenTestTags.LIST).assertExists()

    // Initially disabled
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()

    // Fill required text fields
    fillRequiredFields()
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()

    // Set hours
    setAnyOpeningHoursViaDialog()
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()

    // Add one space -> enabled
    addSpace()
    compose.onTag(spaceRowTag(0)).assertExists()
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled()

    // The component clamps bad input: trying "0" seats shows "1" (min = 1)
    compose.onTag(seatsFieldTag(0)).performTextClearance()
    compose.onTag(seatsFieldTag(0)).performTextInput("0")
    // Move focus to price to trigger clamping/normalization
    compose.onTag(priceFieldTag(0)).performClick()
    compose.waitForIdle()
    compose.onTag(seatsFieldTag(0)).assertTextEquals("1")
    // Still enabled
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled()

    // Trying negative price "-1" is sanitized to "1" (no minus sign accepted)
    compose.onTag(priceFieldTag(0)).performTextClearance()
    compose.onTag(priceFieldTag(0)).performTextInput("-1")
    // Move focus back to seats to trigger normalization
    compose.onTag(seatsFieldTag(0)).performClick()
    compose.waitForIdle()
    compose.onTag(priceFieldTag(0)).assertTextEquals("1")
    // Still enabled
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled()

    // Discard clears and disables
    compose.onTag(ShopComponentsTestTags.ACTION_DISCARD).performClick()
    assertTrue(backCalled)

    ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_REQUIRED)
    compose
        .onAllNodes(
            hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
                hasAnyAncestor(
                    hasTestTag(
                        CreateSpaceRenterScreenTestTags.SECTION_REQUIRED +
                            ShopFormTestTags.SECTION_CONTENT_SUFFIX)),
            useUnmergedTree = true)
        .apply {
          this[0].assert(hasText(""))
          this[1].assert(hasText(""))
        }
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsNotEnabled()
  }

  /**
   * Spaces section behaviors:
   * - empty message present initially
   * - add two rows
   * - edit seats/price including decimal normalization & clamping
   * - delete a row
   */
  @Test
  fun spacesSection_add_edit_delete_and_input_normalization() {
    compose.setContent {
      AppTheme {
        CreateSpaceRenterScreen(
            owner = owner, onBack = {}, onCreated = {}, viewModel = CreateSpaceRenterViewModel())
      }
    }

    // Empty indicator
    ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_SPACES)
    compose.onTag(SpaceRenterComponentsTestTags.SPACES_EMPTY_TEXT).assertExists()

    // Add first space
    addSpace()
    compose.onTag(spaceRowTag(0)).assertExists()
    compose.onTag(SpaceRenterComponentsTestTags.SPACES_EMPTY_TEXT).assertDoesNotExist()

    // Edit seats & price — decimals normalize to single '.' and digits
    compose.onTag(seatsFieldTag(0)).performTextClearance()
    compose.onTag(seatsFieldTag(0)).performTextInput("10")
    // Shift focus to price
    compose.onTag(priceFieldTag(0)).performClick()
    compose.waitForIdle()
    compose.onTag(seatsFieldTag(0)).assertTextEquals("10")

    compose.onTag(priceFieldTag(0)).performTextClearance()
    compose.onTag(priceFieldTag(0)).performTextInput("10,5a.9")
    // Shift focus to seats to trigger normalization
    compose.onTag(seatsFieldTag(0)).performClick()
    compose.waitForIdle()
    compose.onTag(priceFieldTag(0)).assertTextEquals("10.59")

    // Negative ignored -> remains non-negative
    compose.onTag(priceFieldTag(0)).performTextClearance()
    compose.onTag(priceFieldTag(0)).performTextInput("-2")
    compose.onTag(seatsFieldTag(0)).performClick()
    compose.waitForIdle()
    compose.onTag(priceFieldTag(0)).assertTextEquals("2")

    // Add second space
    addSpace()
    compose.onTag(spaceRowTag(1)).assertExists()

    // Delete second row
    compose.onTag(deleteButtonTag(1)).assertExists().performClick()
    compose.waitForIdle()
    compose.onTag(spaceRowTag(1)).assertDoesNotExist()
    compose.onTag(spaceRowTag(0)).assertExists()
  }

  /**
   * Submit success path using AddSpaceRenterContent directly to capture payload:
   * - provide onCreate that records all arguments
   * - ensure phone & website are passed through
   */
  @Test
  fun submit_success_carries_phone_and_website() {
    var capturedName: String? = null
    var capturedEmail: String? = null
    var capturedPhone: String? = null
    var capturedWebsite: String? = null
    var capturedAddress: Location? = null
    var capturedWeek: List<OpeningHours>? = null
    var capturedSpaces: List<Space>? = null
    var onCreatedCalled = false

    compose.setContent {
      AppTheme {
        val vm = CreateSpaceRenterViewModel()
        val locationUi by vm.locationUIState.collectAsState()

        AddSpaceRenterContent(
            owner = owner,
            onBack = {},
            onCreated = { onCreatedCalled = true },
            onCreate = { n, e, p, w, addr, week, spaces ->
              capturedName = n
              capturedEmail = e
              capturedPhone = p
              capturedWebsite = w
              capturedAddress = addr
              capturedWeek = week
              capturedSpaces = spaces
            },
            locationUi = locationUi,
            viewModel = vm)
      }
    }

    // Required + optional + hours + one space
    fillRequiredFields("Meeple Space", "host@example.com", "42 Boardgame Ave")
    fillOptionalFields("+41 79 000 00 00", "https://meeple.space")
    setAnyOpeningHoursViaDialog()
    addSpace()

    // Submit
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).assertIsEnabled().performClick()
    compose.waitForIdle()

    assertEquals("Meeple Space", capturedName)
    assertEquals("host@example.com", capturedEmail)
    assertEquals("+41 79 000 00 00", capturedPhone)
    assertEquals("https://meeple.space", capturedWebsite)
    assertEquals("42 Boardgame Ave", capturedAddress?.name)
    assertTrue(!capturedWeek.isNullOrEmpty())
    assertTrue(!capturedSpaces.isNullOrEmpty())
    assertTrue(onCreatedCalled)
  }

  /** Submit error path: IllegalArgumentException → shows its message. */
  @Test
  fun submit_error_shows_snackbar_message_illegalArgument() {
    compose.setContent {
      AppTheme {
        val vm = CreateSpaceRenterViewModel()
        val locationUi by vm.locationUIState.collectAsState()

        AddSpaceRenterContent(
            owner = owner,
            onBack = {},
            onCreated = { error("shouldn't be called on error") },
            onCreate = { _, _, _, _, _, _, _ ->
              throw IllegalArgumentException("Custom validation message")
            },
            locationUi = locationUi,
            viewModel = vm)
      }
    }
    fillRequiredFields()
    setAnyOpeningHoursViaDialog()
    addSpace()
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).performClick()

    // Wait for snackbar text to appear
    compose.waitUntil(timeoutMillis = 5_000) {
      compose
          .onAllNodes(hasText("Custom validation message", substring = true))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    compose.onNode(hasText("Custom validation message", substring = true)).assertExists()
  }

  /** Submit error path: generic Exception → shows generic error string. */
  @Test
  fun submit_error_shows_snackbar_message_generic() {
    compose.setContent {
      AppTheme {
        val vm = CreateSpaceRenterViewModel()
        val locationUi by vm.locationUIState.collectAsState()

        AddSpaceRenterContent(
            owner = owner,
            onBack = {},
            onCreated = { error("shouldn't be called on error") },
            onCreate = { _, _, _, _, _, _, _ -> throw RuntimeException("boom") },
            locationUi = locationUi,
            viewModel = vm)
      }
    }
    fillRequiredFields()
    setAnyOpeningHoursViaDialog()
    addSpace()
    compose.onTag(ShopComponentsTestTags.ACTION_CREATE).performClick()

    // Wait for snackbar text to appear
    compose.waitUntil(timeoutMillis = 5_000) {
      compose
          .onAllNodes(hasText(AddSpaceRenterUi.Strings.ERROR_CREATE, substring = true))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    compose.onNode(hasText(AddSpaceRenterUi.Strings.ERROR_CREATE, substring = true)).assertExists()
  }

  /* ─────────────────────────────── SMOKE ─────────────────────────────────── */

  @Test
  fun screen_smoke_renders_topbar_and_list() {
    compose.setContent {
      AppTheme {
        CreateSpaceRenterScreen(
            owner = owner, onBack = {}, onCreated = {}, viewModel = CreateSpaceRenterViewModel())
      }
    }
    compose.onTag(CreateSpaceRenterScreenTestTags.TOPBAR).assertExists()
    compose.onTag(CreateSpaceRenterScreenTestTags.TITLE).assertExists()
    compose.onTag(CreateSpaceRenterScreenTestTags.LIST).assertExists()
  }
}
