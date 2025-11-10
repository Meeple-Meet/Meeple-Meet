// This file was initially done by hand and refactored and improved by ChatGPT-5 Extend Thinking
// Combinations to tests were given to the LLM so it could generate the code more easily
package com.github.meeplemeet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenterSearchViewModel
import com.github.meeplemeet.ui.components.SpaceRenterComponentsTestTags as Tags
import com.github.meeplemeet.ui.sessions.SessionTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.FirestoreTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaceRenterComponentsTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  /* ---------- Helpers ---------- */
  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  private fun setContentThemed(content: @Composable () -> Unit) =
      compose.setContent { AppTheme(themeMode = ThemeMode.LIGHT) { content() } }

  private fun spaceRowTag(index: Int) = Tags.SPACE_ROW_PREFIX + index

  private fun seatsFieldTag(index: Int) = spaceRowTag(index) + Tags.SPACE_ROW_SEATS_FIELD_SUFFIX

  private fun priceFieldTag(index: Int) = spaceRowTag(index) + Tags.SPACE_ROW_PRICE_FIELD_SUFFIX

  private fun deleteButtonTag(index: Int) = spaceRowTag(index) + Tags.SPACE_ROW_DELETE_SUFFIX

  private fun ComposeTestRule.fieldInputUnder(parentTag: String) =
      onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(parentTag)), useUnmergedTree = true)

  private fun previewOwner() =
      Account(
          uid = "preview_uid",
          handle = "@previewer",
          name = "Preview Owner",
          email = "preview@example.com")

  /* =======================================================================
   * 1) SpacesHeaderRow
   * ======================================================================= */
  @Test
  fun spacesHeaderRow_renders_titles() {
    val places = "Places"
    val price = "Price"

    setContentThemed { SpacesHeaderRow(placesTitle = places, priceTitle = price) }

    compose.onTag(Tags.SPACES_HEADER).assertExists().assertIsDisplayed()
    compose.onTag(Tags.SPACES_HEADER_PLACES).assertExists().assertTextEquals(places)
    compose.onTag(Tags.SPACES_HEADER_PRICE).assertExists().assertTextEquals(price)
  }

  /* =======================================================================
   * 2) SpaceRow
   * ======================================================================= */
  @Test
  fun spaceRow_editMode_toggles_delete_and_numeric_filters_apply() {
    var updated: Space? = null
    var deleted = 0

    setContentThemed {
      Column(Modifier.padding(12.dp)) {
        // Read-only row: no delete button
        SpaceRow(
            index = 0,
            space = Space(seats = 6, costPerHour = 12.0),
            onChange = { updated = it },
            onDelete = { deleted++ },
            isEditing = false)
        // Editing row: delete button visible
        SpaceRow(
            index = 1,
            space = Space(seats = 2, costPerHour = 3.5),
            onChange = { updated = it },
            onDelete = { deleted++ },
            isEditing = true)
      }
    }

    // row 0 (read-only): no delete button
    compose.onTag(deleteButtonTag(0)).assertDoesNotExist()

    // row 1 (editing): delete present and clickable
    compose.onTag(deleteButtonTag(1)).assertExists().performClick()
    assertEquals(1, deleted)

    // Seats: replace atomically -> 12
    compose.onTag(seatsFieldTag(1)).performTextReplacement("1a2")
    compose.waitForIdle()
    assertEquals(12, updated?.seats)

    // Price: replace atomically -> 10.59
    compose.onTag(priceFieldTag(1)).performTextReplacement("10,5a.9")
    compose.waitForIdle()
    assertEquals(10.59, updated?.costPerHour ?: 0.0, 1e-6)
  }

  /* =======================================================================
   * 3) SpacesList
   * ======================================================================= */
  @Test
  fun spacesList_empty_then_populated_callbacks_fire() {
    val changes = mutableListOf<Pair<Int, Space>>()
    val deletions = mutableListOf<Int>()

    lateinit var setter: (List<Space>) -> Unit

    setContentThemed {
      var source by remember { mutableStateOf<List<Space>>(emptyList()) }
      setter = { new -> source = new }

      SpacesList(
          spaces = source,
          onChange = { idx, sp -> changes += idx to sp },
          onDelete = { idx -> deletions += idx })
    }

    // Empty state visible
    compose.onTag(Tags.SPACES_LIST).assertExists().assertIsDisplayed()
    compose.onTag(Tags.SPACES_EMPTY_TEXT).assertExists().assertIsDisplayed()

    // Populate with 2 spaces
    compose.runOnUiThread { setter(listOf(Space(4, 8.0), Space(6, 12.5))) }
    compose.waitForIdle()

    // Header + two rows present
    compose.onTag(Tags.SPACES_HEADER).assertExists()
    compose.onTag(spaceRowTag(0)).assertExists()
    compose.onTag(spaceRowTag(1)).assertExists()

    // Change row 0 seats -> parent receives
    compose.onTag(seatsFieldTag(0)).performTextReplacement("99")
    compose.waitForIdle()
    assertTrue(changes.isNotEmpty())
    assertEquals(0, changes.last().first)
    assertEquals(99, changes.last().second.seats)

    // Delete row 1 -> parent receives index
    compose.onTag(deleteButtonTag(1)).performClick()
    compose.waitForIdle()
    assertTrue(deletions.contains(1))
  }

  /* =======================================================================
   * 4) SpaceRenterRequiredInfoSection
   * ======================================================================= */
  @Test
  fun requiredInfoSection_fields_and_email_validation_and_location_bar() {
    val owner = previewOwner()
    val vm = SpaceRenterSearchViewModel()

    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
    var link by mutableStateOf("")
    var picked by mutableStateOf<Location?>(null)

    setContentThemed {
      SpaceRenterRequiredInfoSection(
          spaceRenter = null,
          spaceName = name,
          onSpaceName = { name = it },
          email = email,
          onEmail = { email = it },
          phone = phone,
          onPhone = { phone = it.filter { ch -> ch.isDigit() } },
          link = link,
          onLink = { link = it },
          onPickLocation = { picked = it },
          viewModel = vm,
          owner = owner)
    }

    // Name
    compose.fieldInputUnder(ShopFormTestTags.FIELD_SHOP).performTextReplacement("Meeple Space")
    compose.fieldInputUnder(ShopFormTestTags.FIELD_SHOP).assertTextEquals("Meeple Space")

    // Email -> invalid then valid
    compose.fieldInputUnder(ShopFormTestTags.FIELD_EMAIL).performTextReplacement("foo")
    compose
        .onNodeWithText("Enter a valid email address.", useUnmergedTree = true)
        .assertExists()
        .assertIsDisplayed()
    compose.fieldInputUnder(ShopFormTestTags.FIELD_EMAIL).performTextReplacement("host@example.com")
    compose
        .onNodeWithText("Enter a valid email address.", useUnmergedTree = true)
        .assertDoesNotExist()

    // Phone (digits only via our onPhone)
    compose.fieldInputUnder(ShopFormTestTags.FIELD_PHONE).performTextReplacement("123-45a")
    compose.fieldInputUnder(ShopFormTestTags.FIELD_PHONE).assertTextEquals("12345")

    // Link
    compose.fieldInputUnder(ShopFormTestTags.FIELD_LINK).performTextReplacement("https://x.y")
    compose.fieldInputUnder(ShopFormTestTags.FIELD_LINK).assertTextEquals("https://x.y")

    // Location field exists
    compose.onTag(SessionTestTags.LOCATION_FIELD).assertExists().assertIsDisplayed()
  }
}
