package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.TimeSlot
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterViewModel
import com.github.meeplemeet.ui.components.SpaceRenterComponentsTestTags
import com.github.meeplemeet.ui.space_renter.SpaceRenterScreen
import com.github.meeplemeet.ui.space_renter.SpaceRenterTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpaceRenterDetailsScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  private lateinit var vm: SpaceRenterViewModel
  private lateinit var renter: SpaceRenter
  private lateinit var currentUser: Account
  private lateinit var owner: Account

  private val report = linkedMapOf<String, Boolean>()

  private var theme by mutableStateOf(ThemeMode.LIGHT)

  /* ------- semantic helpers ------- */
  private fun title() = compose.onNodeWithText(renter.name)

  private fun phoneText() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_PHONE_TEXT)

  private fun emailText() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_EMAIL_TEXT)

  private fun addressText() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_ADDRESS_TEXT)

  private fun websiteText() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_WEBSITE_TEXT)

  private fun phoneBtn() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_PHONE_BUTTON)

  private fun emailBtn() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_EMAIL_BUTTON)

  private fun addressBtn() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_ADDRESS_BUTTON)

  private fun websiteBtn() = compose.onNodeWithTag(SpaceRenterTestTags.SPACE_RENTER_WEBSITE_BUTTON)

  private fun dayTag(day: Int) = SpaceRenterTestTags.SPACE_RENTER_DAY_PREFIX + day

  private fun spaceRow(i: Int) = SpaceRenterComponentsTestTags.SPACE_ROW_PREFIX + i

  /* -------------------------------- */

  val address = Location(0.0, 0.0, "123 Meeple St, Boardgame City")

  val dummyOpeningHours =
      listOf(
          OpeningHours(0, listOf(TimeSlot("09:00", "18:00"), TimeSlot("19:00", "21:00"))),
          OpeningHours(1, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(2, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(3, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(4, listOf(TimeSlot("09:00", "18:00"))),
          OpeningHours(5, listOf(TimeSlot("10:00", "16:00"))),
          OpeningHours(6, emptyList()))

  private val dummySpaces = listOf(Space(4, 10.0), Space(8, 20.0))

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

  @Test
  fun full_smoke_all_cases() {
    val clipboard = FakeClipboardManager()

    compose.setContent {
      CompositionLocalProvider(LocalClipboardManager provides clipboard) {
        AppTheme(themeMode = theme) {
          SpaceRenterScreen(
              spaceId = renter.id, account = currentUser, viewModel = vm, onBack = {}, onEdit = {})
        }
      }
    }

    compose.waitUntil(timeoutMillis = 5000) { vm.spaceRenter.value != null }

    checkpoint("Title visible") { title().assertExists() }

    checkpoint("Phone text") { phoneText().assertTextEquals("- Phone: ${renter.phone}") }
    checkpoint("Email text") { emailText().assertTextEquals("- Email: ${renter.email}") }
    checkpoint("Address text") {
      addressText().assertTextEquals("- Address: ${renter.address.name}")
    }
    checkpoint("Website text") { websiteText().assertTextEquals("- Website: ${renter.website}") }

    listOf(
            phoneBtn() to "- Phone: ${renter.phone}",
            emailBtn() to "- Email: ${renter.email}",
            addressBtn() to "- Address: ${renter.address.name}",
            websiteBtn() to "- Website: ${renter.website}")
        .forEach { (btn, expected) ->
          checkpoint("Copy via ${btn}") {
            btn.performClick()
            assert(clipboard.copiedText == expected)
          }
        }

    dummyOpeningHours.forEach { entry ->
      val tag = dayTag(entry.day)
      checkpoint("Day label exists: $tag") { compose.onNodeWithTag(tag).assertExists() }
    }

    dummySpaces.forEachIndexed { i, sp ->
      val row = spaceRow(i)
      checkpoint("Space row exists: $row") { compose.onNodeWithTag(row).assertExists() }
    }

    val failed = report.filterValues { !it }.keys
    println(
        "Smoke: ${report.size - failed.size}/${report.size} OK" +
            (if (failed.isNotEmpty()) " → $failed" else ""))
    assertTrue("Failures: $failed", failed.isEmpty())
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
  }

  /* ------ helper ------ */
  private inline fun checkpoint(name: String, block: () -> Unit) {
    runCatching { block() }.onSuccess { report[name] = true }.onFailure { report[name] = false }
  }

  private fun now() = System.currentTimeMillis().toString()
}
