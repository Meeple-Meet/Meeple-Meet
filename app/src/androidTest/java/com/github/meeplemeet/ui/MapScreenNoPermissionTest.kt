package com.github.meeplemeet.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.map.MapViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.maps.android.compose.CameraPositionState
import io.mockk.mockk
import java.util.TimeZone
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Test suite for the MapScreen without giving location permission. */
@RunWith(AndroidJUnit4::class)
class MapScreenNoPermissionTest : FirestoreTests(), OnMapsSdkInitializedCallback {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var mockNavigation: NavigationActions
  private lateinit var regularAccount: Account
  private lateinit var currentAccountState: MutableState<Account>
  private var renderTrigger by mutableStateOf(0)

  @Before
  fun setup() {
    try {
      MapsInitializer.initialize(
          InstrumentationRegistry.getInstrumentation().targetContext,
          MapsInitializer.Renderer.LATEST,
          this)
    } catch (_: Exception) {}

    mockNavigation = mockk(relaxed = true)

    runBlocking {
      regularAccount =
          accountRepository.createAccount(
              "regular_user_no_perm", "Regular User No Perm", "regularnp@test.com", photoUrl = null)
    }

    currentAccountState = mutableStateOf(regularAccount)
    renderTrigger = 0
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {}

  @Test
  fun test_mapWithoutLocationPermission() {
    val cameraState = CameraPositionState()

    composeRule.setContent {
      val trigger = renderTrigger
      AppTheme {
        key(trigger) {
          MapScreen(
              viewModel = MapViewModel(),
              navigation = mockNavigation,
              account = currentAccountState.value,
              onFABCLick = {},
              onRedirect = {},
              cameraPositionState = cameraState,
              forceNoPermission = true)
        }
      }
    }

    Thread.sleep(1500)

    checkpoint("mapScreen_displaysWithoutPermission") {
      composeRule.waitForIdle()
      composeRule.onNodeWithTag(MapScreenTestTags.GOOGLE_MAP_SCREEN).assertIsDisplayed()
    }

    checkpoint("recenterButton_hiddenWithoutPermission") {
      composeRule.waitForIdle()
      Thread.sleep(500)
      composeRule.onNodeWithTag(MapScreenTestTags.RECENTER_BUTTON).assertDoesNotExist()
    }

    checkpoint("scaleBar_displaysWithoutPermission") {
      composeRule.waitForIdle()
      Thread.sleep(500)
      composeRule.onNodeWithTag(MapScreenTestTags.SCALE_BAR).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.SCALE_BAR_DISTANCE).assertIsDisplayed()
    }

    checkpoint("filterButtons_workWithoutPermission") {
      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_BUTTON).performClick()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SHOP_CHIP).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SPACE_CHIP).assertIsDisplayed()
      composeRule.onNodeWithTag(MapScreenTestTags.FILTER_SESSIONS_CHIP).assertIsDisplayed()
    }

    checkpoint("mapScreen_usesTimezoneBasedFallbackLocation") {
      composeRule.waitForIdle()
      val expected = getApproximateLocationFromTimezone()
      val actual = cameraState.position.target

      assertEquals(expected.latitude, actual.latitude, 0.5)
      assertEquals(expected.longitude, actual.longitude, 0.5)
    }
  }

  @Test
  fun test_getApproximateLocationFromTimezone_allBranches() {
    val original = TimeZone.getDefault()

    try {
      checkpoint("timezone_europe") {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("Europe", loc.name)
        assertEquals(50.0, loc.latitude, 0.1)
      }

      checkpoint("timezone_america_east") {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("East Coast", loc.name)
        assertEquals(40.0, loc.latitude, 0.1)
      }

      checkpoint("timezone_america_west") {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("West Coast", loc.name)
        assertEquals(37.0, loc.latitude, 0.1)
      }

      checkpoint("timezone_america_central") {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("Central", loc.name)
        assertEquals(35.0, loc.latitude, 0.1)
      }

      checkpoint("timezone_america_other") {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("Americas", loc.name)
      }

      checkpoint("timezone_asia") {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("Asia", loc.name)
        assertEquals(35.0, loc.latitude, 0.1)
      }

      checkpoint("timezone_africa") {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("Africa", loc.name)
        assertEquals(0.0, loc.latitude, 0.1)
      }

      checkpoint("timezone_australia") {
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("Australia", loc.name)
        assertEquals(-25.0, loc.latitude, 0.1)
      }

      checkpoint("timezone_fallback") {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val loc = getApproximateLocationFromTimezone()
        assertEquals("World", loc.name)
        assertEquals(0.0, loc.latitude, 0.1)
        assertEquals(0.0, loc.longitude, 0.1)
      }
    } finally {
      TimeZone.setDefault(original)
    }
  }
}
