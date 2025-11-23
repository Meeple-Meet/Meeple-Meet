// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.space_renter.CreateSpaceRenterViewModel
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.components.SpaceRenterComponentsTestTags
import com.github.meeplemeet.ui.space_renter.CreateSpaceRenterScreen
import com.github.meeplemeet.ui.space_renter.CreateSpaceRenterScreenTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateSpaceRenterScreenTest : FirestoreTests() {

  /* ───────────────────────────────── RULES ───────────────────────────────── */

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

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

  /** Fill required TEXT fields (name, email). Intentionally avoids the location field. */
  private fun fillRequiredTextFields(
      name: String = "Meeple Hub",
      email: String = "space@host.com"
  ) {
    ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_REQUIRED)

    inputIn(ShopFormTestTags.FIELD_SHOP).assertExists().performTextClearance()
    inputIn(ShopFormTestTags.FIELD_SHOP).performTextInput(name)

    inputIn(ShopFormTestTags.FIELD_EMAIL).assertExists().performTextClearance()
    inputIn(ShopFormTestTags.FIELD_EMAIL).performTextInput(email)
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
    compose.onTag(ShopComponentsTestTags.DIALOG_SAVE).assertExists().performClick()
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

  @Test
  fun all_tests() {
    var backCalled = false
    val vm = CreateSpaceRenterViewModel()

    compose.setContent {
      AppTheme {
        CreateSpaceRenterScreen(
            owner = owner, onBack = { backCalled = true }, onCreated = {}, viewModel = vm)
      }
    }

    checkpoint("interaction_smoke") {
      // Top & list exist
      compose.onTag(CreateSpaceRenterScreenTestTags.SCAFFOLD).assertExists()
      compose.onTag(CreateSpaceRenterScreenTestTags.TOPBAR).assertExists()
      compose.onTag(CreateSpaceRenterScreenTestTags.TITLE).assertExists()
      compose.onTag(CreateSpaceRenterScreenTestTags.LIST).assertExists()

      // Fill text fields
      fillRequiredTextFields()

      // Set hours and add a space
      setAnyOpeningHoursViaDialog()
      addSpace()
      compose.onTag(spaceRowTag(0)).assertExists()

      // Seats clamping
      compose.onTag(seatsFieldTag(0)).performTextClearance()
      compose.onTag(seatsFieldTag(0)).performTextInput("0")
      compose.onTag(priceFieldTag(0)).performClick()
      compose.waitForIdle()
      compose.onTag(seatsFieldTag(0)).assertTextEquals("1")

      // Price normalization
      compose.onTag(priceFieldTag(0)).performTextClearance()
      compose.onTag(priceFieldTag(0)).performTextInput("-1")
      compose.onTag(seatsFieldTag(0)).performClick()
      compose.waitForIdle()
      compose.onTag(priceFieldTag(0)).assertTextEquals("1")

      // Discard
      compose.onTag(ShopComponentsTestTags.ACTION_DISCARD).performClick()
      assertTrue(backCalled)
    }

    checkpoint("spacesSection_add_edit_delete_and_input_normalization") {
      // Clear the form by clicking discard if needed
      // Spaces section tests continue with the same composition

      // Empty indicator should now be shown after discard cleared the form
      ensureSectionExpanded(CreateSpaceRenterScreenTestTags.SECTION_SPACES)
      compose.onTag(SpaceRenterComponentsTestTags.SPACES_EMPTY_TEXT).assertExists()

      // Add first space
      addSpace()
      compose.onTag(spaceRowTag(0)).assertExists()
      compose.onTag(SpaceRenterComponentsTestTags.SPACES_EMPTY_TEXT).assertDoesNotExist()

      // Edit seats & price — decimals normalize to single '.' and digits
      compose.onTag(seatsFieldTag(0)).performTextClearance()
      compose.onTag(seatsFieldTag(0)).performTextInput("10")
      compose.onTag(priceFieldTag(0)).performClick()
      compose.waitForIdle()
      compose.onTag(seatsFieldTag(0)).assertTextEquals("10")

      compose.onTag(priceFieldTag(0)).performTextClearance()
      compose.onTag(priceFieldTag(0)).performTextInput("10,5a.9")
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

    checkpoint("screen_smoke_renders_topbar_and_list") {
      // Already verified in interaction_smoke checkpoint above
      compose.onTag(CreateSpaceRenterScreenTestTags.TOPBAR).assertExists()
      compose.onTag(CreateSpaceRenterScreenTestTags.TITLE).assertExists()
      compose.onTag(CreateSpaceRenterScreenTestTags.LIST).assertExists()
    }
  }
}
