package com.github.meeplemeet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.EditSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.space_renter.EditSpaceRenterScreen
import com.github.meeplemeet.ui.space_renter.EditSpaceRenterScreenTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("Uses new Business Component which will be implemented later")
@RunWith(AndroidJUnit4::class)
class EditSpaceRenterScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var vm: EditSpaceRenterViewModel
  private lateinit var renter: SpaceRenter
  private lateinit var owner: Account
  val testOpeningHours =
      listOf(
          OpeningHours(day = 0, hours = emptyList()),
          OpeningHours(day = 1, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 2, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 3, hours = listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(day = 4, hours = listOf(TimeSlot("09:00", "20:00"))),
          OpeningHours(day = 5, hours = listOf(TimeSlot("09:00", "20:00"))),
          OpeningHours(day = 6, hours = listOf(TimeSlot("10:00", "16:00"))))
  val testLocation =
      Location(latitude = 46.5197, longitude = 6.6323, name = "EPFL Campus, Lausanne")

  @Before
  fun setup() {
    runBlocking {
      // Force online mode for tests
      com.github.meeplemeet.model.offline.OfflineModeManager.setNetworkStatusForTesting(true)

      vm = EditSpaceRenterViewModel()
      owner =
          accountRepository.createAccount(
              name = "owner1", userHandle = "@owner", email = "owner@example.com", photoUrl = null)
      renter =
          SpaceRenter(
              owner = owner,
              name = "Test Space Renter",
              phone = "12345678",
              email = "owner@example.com",
              website = "https://example.com",
              address = testLocation,
              openingHours = testOpeningHours,
              spaces = listOf(Space(seats = 10, costPerHour = 15.0)),
              id = "renter1",
              photoCollectionUrl = emptyList())
      val noUid = com.github.meeplemeet.model.space_renter.toNoUid(renter)
      db.collection("space_renters").document(renter.id).set(noUid).await()
      renter = spaceRenterRepository.getSpaceRenter(renter.id)
    }
  }

  private fun ComposeTestRule.onTag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

  private fun ComposeTestRule.onTags(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true)

  private fun scrollListToTag(tag: String) {
    try {
      compose.onTag(EditSpaceRenterScreenTestTags.LIST).performScrollToNode(hasTestTag(tag))
      compose.waitForIdle()
    } catch (e: AssertionError) {
      // Ignore: node may already be visible or not present inside the lazy list
    }
  }

  private fun ensureSectionExpanded(sectionBaseTag: String) {
    val toggleTag = sectionBaseTag + "_header"
    val contentTag = sectionBaseTag + "_content"

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

  private fun spaceRowTag(index: Int) = "space_row_$index"

  private fun seatsFieldTag(index: Int) = spaceRowTag(index) + "_seats"

  private fun priceFieldTag(index: Int) = spaceRowTag(index) + "_price"

  private fun deleteButtonTag(index: Int) = spaceRowTag(index) + "_delete"

  /** Returns the LabeledField INPUT inside the given wrapper (FIELD_* tag). */
  private fun inputIn(wrapperTag: String) =
      compose.onNode(
          hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
              hasAnyAncestor(hasTestTag(wrapperTag)),
          useUnmergedTree = true)

  @Test
  fun all_edit_space_renter_tests() {
    // Disable bottom bar hiding to ensure ActionBar is always visible in tests
    UiBehaviorConfig.hideBottomBarWhenInputFocused = false

    var backCalled = false
    var updatedCalled = false

    // Make the renter valid by putting one non-empty OpeningHours entry (one TimeSlot).
    val validRenter =
        renter.copy(
            openingHours =
                renter.openingHours.mapIndexed { idx, oh ->
                  if (idx == 0)
                      oh.copy(
                          hours =
                              listOf(
                                  com.github.meeplemeet.model.shops.TimeSlot(
                                      open = "16:00", close = "20:00")))
                  else oh
                })

    val currentRenterState = mutableStateOf(renter)

    compose.setContent {
      AppTheme {
        EditSpaceRenterScreen(
            spaceRenter = currentRenterState.value,
            owner = owner,
            onBack = { backCalled = true },
            onUpdated = { updatedCalled = true },
            viewModel = vm,
            online = true,
        )
      }
    }

    checkpoint("screen_prefills_data_and_allows_editing") {
      // Top & list exist
      compose.onTag(EditSpaceRenterScreenTestTags.SCAFFOLD).assertExists()
      compose.onTag(EditSpaceRenterScreenTestTags.TOPBAR).assertExists()
      compose.onTag(EditSpaceRenterScreenTestTags.TITLE).assertExists()
      compose.onTag(EditSpaceRenterScreenTestTags.LIST).assertExists()

      // Check that initial fields are prefilled
      ensureSectionExpanded(EditSpaceRenterScreenTestTags.SECTION_REQUIRED)
      compose.onNodeWithText(renter.name).assertExists()
      compose.onNodeWithText(renter.email).assertExists()
      compose.onNodeWithText(renter.phone).assertExists()
      compose.onNodeWithText(renter.website).assertExists()

      // Check that spaces are prefilled
      ensureSectionExpanded(EditSpaceRenterScreenTestTags.SECTION_SPACES)
      compose.onTag(spaceRowTag(0)).assertExists()
      compose.onTag(seatsFieldTag(0)).assertTextEquals("10")
      compose.onTag(priceFieldTag(0)).assertTextEquals("15")

      // Edit space values
      compose.onTag(seatsFieldTag(0)).performTextClearance()
      compose.onTag(seatsFieldTag(0)).performTextInput("12")
      compose.onTag(priceFieldTag(0)).performTextClearance()
      compose.onTag(priceFieldTag(0)).performTextInput("18.5")
      compose.waitForIdle()
      compose.onTag(seatsFieldTag(0)).assertTextEquals("12")
      compose.onTag(priceFieldTag(0)).assertTextEquals("18.5")

      // Discard updates
      compose.onTag(EditSpaceRenterScreenTestTags.NAV_BACK).performClick()
      assertTrue(backCalled)
    }

    checkpoint("screen_saves_on_update") {
      // Switch to valid renter for save test
      currentRenterState.value = validRenter
      compose.waitForIdle()
      // Make a change to enable the save button
      ensureSectionExpanded(EditSpaceRenterScreenTestTags.SECTION_REQUIRED)

      val nameEditable = inputIn(ShopFormTestTags.FIELD_SHOP)
      nameEditable.assertExists()
      nameEditable.performTextClearance()
      nameEditable.performTextInput("Updated Space Name")
      compose.waitForIdle()

      // Save updates
      compose.onTag(ShopComponentsTestTags.ACTION_SAVE).performClick()
      compose.waitForIdle()
      compose.waitUntil { updatedCalled }
    }

    // Reset config to default for other tests
    UiBehaviorConfig.hideBottomBarWhenInputFocused = true
  }
}
