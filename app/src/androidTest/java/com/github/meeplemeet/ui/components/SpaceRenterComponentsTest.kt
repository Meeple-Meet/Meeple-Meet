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
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterSearchViewModel
import com.github.meeplemeet.ui.components.SpaceRenterComponentsTestTags as Tags
import com.github.meeplemeet.ui.sessions.SessionTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaceRenterComponentsTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()
  private val contentState = mutableStateOf<@Composable () -> Unit>({})
  private var renderToken by mutableStateOf(0)

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  /* ---------- Helpers ---------- */
  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  private fun setContentThemed(content: @Composable () -> Unit) {
    compose.runOnIdle {
      contentState.value = content
      renderToken++
    }
    compose.waitForIdle()
  }

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
  fun all_tests() {
    compose.setContent {
      val token = renderToken
      AppTheme(themeMode = ThemeMode.LIGHT) { key(token) { contentState.value() } }
    }

    checkpoint("spacesHeaderRow_renders_titles") { spacesHeaderRow_renders_titles() }
    checkpoint("spaceRow_editMode_toggles_delete_and_numeric_filters_apply") {
      spaceRow_editMode_toggles_delete_and_numeric_filters_apply()
    }
    checkpoint("spacesList_empty_then_populated_callbacks_fire") {
      spacesList_empty_then_populated_callbacks_fire()
    }
    checkpoint("requiredInfoSection_fields_and_email_validation_and_location_bar") {
      requiredInfoSection_fields_and_email_validation_and_location_bar()
    }
  }

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
  fun requiredInfoSection_fields_and_email_validation_and_location_bar() {
    val owner = previewOwner()
    val vm = SpaceRenterSearchViewModel()

    setContentThemed {
      // Keep a draft renter as state; composable reads values from it.
      var renter by remember {
        mutableStateOf(
            SpaceRenter(
                id = "",
                owner = owner,
                name = "",
                phone = "",
                email = "",
                website = "",
                address = Location(name = ""),
                openingHours = emptyList(),
                spaces = emptyList(),
                photoCollectionUrl = emptyList()))
      }

      SpaceRenterRequiredInfoSection(
          spaceRenter = renter,
          onSpaceName = { renter = renter.copy(name = it) },
          onEmail = { renter = renter.copy(email = it) },
          // test wants digits-only behavior -> filter in the callback
          onPhone = { renter = renter.copy(phone = it.filter(Char::isDigit)) },
          onLink = { renter = renter.copy(website = it) },
          onPickLocation = { loc -> renter = renter.copy(address = loc) },
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
