package com.github.meeplemeet.end2end

import android.Manifest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.meeplemeet.HttpClientProvider
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.ui.MapScreenTestTags
import com.github.meeplemeet.ui.account.CreateAccountTestTags
import com.github.meeplemeet.ui.auth.OnBoardingTestTags
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.components.SessionComponentsTestTags
import com.github.meeplemeet.ui.components.ShopComponentsTestTags
import com.github.meeplemeet.ui.components.ShopFormTestTags
import com.github.meeplemeet.ui.discussions.AddDiscussionTestTags
import com.github.meeplemeet.ui.discussions.DiscussionTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.posts.CreatePostTestTags
import com.github.meeplemeet.ui.posts.FeedsOverviewTestTags
import com.github.meeplemeet.ui.shops.CreateShopScreenTestTags
import com.github.meeplemeet.utils.AuthUtils.closeKeyboardSafely
import com.github.meeplemeet.utils.AuthUtils.waitUntilWithCatch
import com.github.meeplemeet.utils.FirestoreTests
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import java.util.UUID
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test for M2: Sign up a user, create 3 other users through repos, create a discussion
 * from the UI, and add all 3 members to it. Ask to users the name of the store through a poll,
 * create the store, create a post to advertise it, wait for reactions.
 */
@RunWith(AndroidJUnit4::class)
class E2E_M2 : FirestoreTests(), OnMapsSdkInitializedCallback {
  // Generic retry helper used for waiting on backend state convergence
  private suspend fun retryUntil(
      timeoutMs: Long = 30_000,
      intervalMs: Long = 500,
      predicate: suspend () -> Boolean
  ) {
    try {
      withTimeout(timeoutMs) {
        while (!predicate()) {
          continue
        }
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError("Condition not met within ${timeoutMs}ms", e)
    }
  }

  private suspend fun waitUntilAuthReady() = retryUntil { auth.currentUser != null }

  private suspend fun waitUntilHandleCreated(handle: String) = retryUntil {
    val doc = handlesRepository.collection.document(handle).get().await()
    doc.exists() && doc.getString("accountId")?.isNotBlank() == true
  }

  private suspend fun waitUntilShopOwnerTrue(handle: String) = retryUntil {
    val uid = handlesRepository.collection.document(handle).get().await().getString("accountId")
    if (uid.isNullOrBlank()) return@retryUntil false
    val acc = accountRepository.getAccount(uid)
    acc.shopOwner
  }

  override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {}

  @get:Rule
  val permissionRule: GrantPermissionRule =
      GrantPermissionRule.grant(
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  // Helper to get the actual text input inside a FIELD_* wrapper
  private fun inputIn(wrapperTag: String) =
      composeTestRule.onNode(
          hasTestTag(ShopComponentsTestTags.LABELED_FIELD_INPUT) and
              hasAnyAncestor(hasTestTag(wrapperTag)),
          useUnmergedTree = true)

  // Helper to scroll the Create Shop LazyColumn to a given child tag
  private fun scrollListToTag(tag: String) {
    composeTestRule
        .onNodeWithTag(CreateShopScreenTestTags.LIST)
        .performScrollToNode(hasTestTag(tag))
    composeTestRule.waitForIdle()
  }

  private fun ensureSectionExpanded(sectionBaseTag: String) {
    val contentTag = sectionBaseTag + CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX
    val isExpanded =
        composeTestRule.onAllNodesWithTag(contentTag).fetchSemanticsNodes().isNotEmpty()
    if (!isExpanded) {
      composeTestRule.waitUntilWithCatch({
        composeTestRule
            .onNodeWithTag(sectionBaseTag + CreateShopScreenTestTags.SECTION_TOGGLE_ICON_SUFFIX)
            .assertExists()
            .performClick()
        true
      })
      composeTestRule.waitForIdle()
    }
  }

  @Before
  fun setup() {
    try {
      MapsInitializer.initialize(
          InstrumentationRegistry.getInstrumentation().targetContext,
          MapsInitializer.Renderer.LATEST,
          this)
    } catch (_: Exception) {}

    val originalClient = HttpClientProvider.client
    val mockClient =
        originalClient
            .newBuilder()
            .addInterceptor { chain ->
              val request = chain.request()
              if (request.url.queryParameter("q") == "EPFL") {
                val responseBody =
                    """[{"lat":46.5191,"lon":6.5668,"display_name":"EPFL, Lausanne, Switzerland"}]"""
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBody.toResponseBody("application/json".toMediaType()))
                    .build()
              } else {
                chain.proceed(request)
              }
            }
            .build()
    HttpClientProvider.client = mockClient
  }

  @After
  fun tearDown() {
    HttpClientProvider.client = OkHttpClient()
  }

  @Test
  fun signUpUser_createOtherUsers_createDiscussionAndAddMembers() {
    // Generate unique identifiers for test data with UUID to allow multiple test runs
    val uniqueId = UUID.randomUUID().toString().take(8)
    val mainUserEmail = "shopowner_${uniqueId}@mail.com"
    val mainUserPassword = "Password123!"
    val mainUserHandle = "shop_$uniqueId"
    val mainUserName = "Shop_Owner_v.$uniqueId"

    // Sign up the main user through UI, the rest of the other interactions will be done through
    // repositories
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
          .assertExists()
          .performTextInput(mainUserEmail)
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
          .assertExists()
          .performTextInput(mainUserPassword)
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .assertExists()
          .performTextInput(mainUserPassword)
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.closeKeyboardSafely()
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })

    composeTestRule.waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          composeTestRule
              .onAllNodesWithTag(CreateAccountTestTags.SUBMIT_BUTTON, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

    // Fill Create Account
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreateAccountTestTags.HANDLE_FIELD, useUnmergedTree = true)
          .assertExists()
          .performTextInput(mainUserHandle)
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreateAccountTestTags.USERNAME_FIELD, useUnmergedTree = true)
          .assertExists()
          .performTextInput(mainUserName)
      true
    })
    composeTestRule.closeKeyboardSafely()
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreateAccountTestTags.CHECKBOX_OWNER)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(CreateAccountTestTags.CHECKBOX_OWNER).assertIsOn()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreateAccountTestTags.SUBMIT_BUTTON, useUnmergedTree = true)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })
    // Skip the OnBoarding screen
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(OnBoardingTestTags.SKIP_BUTTON).assertExists().performClick()
      true
    })

    composeTestRule.waitForIdle()

    // Backend convergence: wait for auth user, handle and shopOwner flag before proceeding
    runBlocking {
      waitUntilAuthReady()
      waitUntilHandleCreated(mainUserHandle)
      waitUntilShopOwnerTrue(mainUserHandle)
    }

    composeTestRule.waitUntilWithCatch(
        timeoutMs = 20_000,
        predicate = {
          composeTestRule
              .onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

    // Create 3 other users through repositories
    val aliceHandle = "alice_$uniqueId"
    val bobHandle = "bob_$uniqueId"
    val eveHandle = "eve_$uniqueId"

    runBlocking {
      createUserThroughRepo("Alice", aliceHandle, "user1_${uniqueId}@test.com")
      createUserThroughRepo("Bob", bobHandle, "user2_${uniqueId}@test.com")
      createUserThroughRepo("Eve", eveHandle, "user3_${uniqueId}@test.com")
    }

    // Create a discussion and add all 3 members
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag("Add Discussion").assertExists().performClick()
      true
    })
    composeTestRule.waitForIdle()
    val discussionTitle = "My store's name $uniqueId"
    val discussionDescription = "Let's discuss my store's name! "

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(AddDiscussionTestTags.ADD_TITLE)
          .assertExists()
          .performTextInput(discussionTitle)
      true
    })

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(AddDiscussionTestTags.ADD_DESCRIPTION)
          .assertExists()
          .performTextInput(discussionDescription)
      true
    })

    composeTestRule.waitForIdle()

    addMemberToDiscussion(aliceHandle)
    addMemberToDiscussion(bobHandle)
    addMemberToDiscussion(eveHandle)

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(AddDiscussionTestTags.CREATE_DISCUSSION_BUTTON)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })

    composeTestRule.waitForIdle()

    // Verify the discussion appears in the list
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 20_000,
        predicate = {
          composeTestRule
              .onAllNodesWithText(discussionTitle, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

    // Start the discussion
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithText(discussionTitle, useUnmergedTree = true)
          .assertIsDisplayed()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(DiscussionTestTags.INPUT_FIELD, useUnmergedTree = true)
          .assertExists()
          .performTextInput("Hey")
      true
    })

    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(DiscussionTestTags.SEND_BUTTON).assertExists().performClick()
      true
    })

    composeTestRule.waitForIdle()

    // Wait for message to appear
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule.onNodeWithText("Hey", useUnmergedTree = true).assertExists()
          true
        })

    // Make other users respond through repository
    val latestDiscussion = runBlocking {
      discussionRepository.getDiscussion(
          discussionRepository.collection
              .whereEqualTo("name", discussionTitle)
              .get()
              .await()
              .documents
              .first()
              .id)
    }

    runBlocking {
      // Get account IDs from handles
      val aliceUid =
          handlesRepository.collection.document(aliceHandle).get().await().getString("accountId")!!
      val bobUid =
          handlesRepository.collection.document(bobHandle).get().await().getString("accountId")!!
      val eveUid =
          handlesRepository.collection.document(eveHandle).get().await().getString("accountId")!!

      val aliceAccount = accountRepository.getAccount(aliceUid)
      val bobAccount = accountRepository.getAccount(bobUid)
      val eveAccount = accountRepository.getAccount(eveUid)

      // Alice responds
      discussionRepository.sendMessageToDiscussion(latestDiscussion, aliceAccount, "Hi there!")
      composeTestRule.waitForIdle()

      // Bob responds
      discussionRepository.sendMessageToDiscussion(latestDiscussion, bobAccount, "Hello everyone!")
      composeTestRule.waitForIdle()

      // Eve responds
      discussionRepository.sendMessageToDiscussion(latestDiscussion, eveAccount, "Hey team!")
      composeTestRule.waitForIdle()
    }

    // Wait for all messages to appear on screen
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          composeTestRule.onNodeWithText("Hi there!", useUnmergedTree = true).assertExists()
          composeTestRule.onNodeWithText("Hello everyone!", useUnmergedTree = true).assertExists()
          composeTestRule.onNodeWithText("Hey team!", useUnmergedTree = true).assertExists()
          true
        })

    // Create a poll to ask other users for a store name
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(DiscussionTestTags.ATTACHMENT_BUTTON)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch(
        timeoutMs = 3000,
        predicate = {
          composeTestRule
              .onAllNodesWithTag(DiscussionTestTags.ATTACHMENT_POLL_OPTION)
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(DiscussionTestTags.ATTACHMENT_POLL_OPTION)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Fill in poll details
    val pollQuestion = "What should I name my shop?"
    val pollOptions = listOf("Name 1", "Name 2", "Name 3", "Name 4")

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(DiscussionTestTags.QUESTION_FIELD)
          .assertExists()
          .performTextInput(pollQuestion)
      true
    })

    composeTestRule.waitForIdle()

    // Add poll options
    pollOptions.forEachIndexed { index, option ->
      if (index > 1) {
        composeTestRule.waitUntilWithCatch({
          composeTestRule.onNodeWithTag(DiscussionTestTags.ADD_OPTION_BUTTON).performClick()
          true
        })
        composeTestRule.waitForIdle()
      }

      composeTestRule.waitUntilWithCatch({
        composeTestRule
            .onAllNodesWithTag(DiscussionTestTags.OPTION_TEXT_FIELD)[index]
            .performTextInput(option)
        true
      })
      composeTestRule.waitForIdle()
    }

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(DiscussionTestTags.CREATE_POLL_CONFIRM)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Wait for poll to appear
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onNodeWithText(pollQuestion, useUnmergedTree = true, substring = true)
              .assertExists()
          true
        })

    // Make the store owner vote through the UI
    composeTestRule.waitUntilWithCatch(
        {
          composeTestRule.onNodeWithText("Name 1", useUnmergedTree = true).performClick()
          true
        },
        timeoutMs = 15_000)

    // Make other users vote on the poll through repository
    val pollMessage = runBlocking {
      val messages = discussionRepository.getMessages(latestDiscussion.uid)
      messages.last { it.poll != null }
    }

    runBlocking {
      // Get account IDs from handles
      val aliceUid =
          handlesRepository.collection.document(aliceHandle).get().await().getString("accountId")!!
      val bobUid =
          handlesRepository.collection.document(bobHandle).get().await().getString("accountId")!!
      val eveUid =
          handlesRepository.collection.document(eveHandle).get().await().getString("accountId")!!

      // Each user votes for a different option
      discussionRepository.voteOnPoll(latestDiscussion.uid, pollMessage.uid, aliceUid, 0) // Name 1
      composeTestRule.waitForIdle()

      discussionRepository.voteOnPoll(latestDiscussion.uid, pollMessage.uid, bobUid, 2) // Name 3
      composeTestRule.waitForIdle()

      discussionRepository.voteOnPoll(latestDiscussion.uid, pollMessage.uid, eveUid, 1) // Name 2
      composeTestRule.waitForIdle()
    }

    // Wait for votes to be reflected (poll percentages should update)
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 50_000,
        predicate = {
          // First option should have 50%
          composeTestRule
              .onNodeWithText("50%", useUnmergedTree = true, substring = true)
              .assertExists()
          true
        })

    // Create a store with that name
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Wait to ensure we're back at discussions overview
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).assertExists()
          true
        })

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.MAP_TAB).assertExists().performClick()
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          composeTestRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).assertExists()
          true
        })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(MapScreenTestTags.ADD_FAB).performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Use the input inside the wrapper to fill in shop details
    composeTestRule.waitUntilWithCatch({
      inputIn(CreateShopScreenTestTags.FIELD_SHOP).assertExists().performTextInput("Name 1")
      true
    })
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch({
      inputIn(CreateShopScreenTestTags.FIELD_EMAIL)
          .assertExists()
          .performTextInput("shop_${uniqueId}@example.com")
      true
    })
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch({
      inputIn(CreateShopScreenTestTags.FIELD_PHONE).assertExists().performTextInput("0123456789")
      true
    })
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch({
      inputIn(CreateShopScreenTestTags.FIELD_LINK).assertExists().performTextInput("name1games.com")
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SessionComponentsTestTags.LOCATION_FIELD, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch(
        {
          composeTestRule
              .onNodeWithTag(SessionComponentsTestTags.LOCATION_FIELD, useUnmergedTree = true)
              .performTextInput("EPFL")
          true
        },
        timeoutMs = 30_000)
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch(
        timeoutMs = 15_000,
        predicate = {
          composeTestRule
              .onAllNodesWithTag(SessionComponentsTestTags.LOCATION_FIELD_ITEM + ":0")
              .fetchSemanticsNodes()
              .isNotEmpty()
        })

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SessionComponentsTestTags.LOCATION_FIELD_ITEM + ":0")
          .assertExists()
          .performClick()
      true
    })

    // Set weekday opening hours
    val availabilityToggleTag =
        CreateShopScreenTestTags.SECTION_AVAILABILITY +
            CreateShopScreenTestTags.SECTION_TOGGLE_SUFFIX
    val availabilityContentTag =
        CreateShopScreenTestTags.SECTION_AVAILABILITY +
            CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX
    scrollListToTag(
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX)

    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_AVAILABILITY)

    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onAllNodesWithTag(availabilityContentTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isNotEmpty() ||
              composeTestRule
                  .onAllNodesWithTag(availabilityToggleTag, useUnmergedTree = true)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
        })

    val contentPresent =
        composeTestRule
            .onAllNodesWithTag(availabilityContentTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    if (!contentPresent) {
      // Bring the toggle into view and expand
      scrollListToTag(availabilityToggleTag)
      composeTestRule.waitUntilWithCatch({
        composeTestRule
            .onNodeWithTag(availabilityToggleTag, useUnmergedTree = true)
            .assertExists()
            .performClick()
        true
      })
      composeTestRule.waitForIdle()
      composeTestRule.waitUntilWithCatch(
          timeoutMs = 5_000,
          predicate = {
            composeTestRule
                .onAllNodesWithTag(ShopComponentsTestTags.DAY_ROW_EDIT, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size >= 5
          })
    }
    // Ensure we have day edit buttons
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onAllNodesWithTag(ShopComponentsTestTags.DAY_ROW_EDIT, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .size >= 5
        })
    // Set opening hours for all days (07:00 – 22:00) using one dialog + backend normalization
    // 1. Open the edit dialog for the first day
    scrollListToTag(ShopComponentsTestTags.DAY_ROW_EDIT)
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onAllNodesWithTag(ShopComponentsTestTags.DAY_ROW_EDIT, useUnmergedTree = true)[0]
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()
    // 2. Wait for dialog
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onNodeWithTag(ShopComponentsTestTags.DIALOG_TITLE, useUnmergedTree = true)
              .assertExists()
          true
        })
    // 3. Select all days chips 0..6
    (0..6).forEach { idx ->
      composeTestRule.waitUntilWithCatch({
        composeTestRule
            .onNodeWithTag(ShopComponentsTestTags.dayChip(idx), useUnmergedTree = true)
            .assertExists()
            .performClick()
        true
      })
    }
    composeTestRule.waitForIdle()
    // 4. Ensure neither Open24 nor Closed are checked.
    // Toggle Closed off (it is often initially on for empty hours)
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(ShopComponentsTestTags.DIALOG_CLOSED_CHECKBOX, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()
    // Make sure Open24 is off (if it was on for some reason)
    // We optimistically click it only if needed; a single click when off leaves it off
    try {
      // Attempt to find and ensure it's unchecked by clicking if currently on (cannot read state
      // reliably)
      // If state was off this click would wrongly turn it on; safer to skip unless you have
      // semantics.
      // So we leave Open24 untouched here.
    } catch (_: Throwable) {}
    // 5. Save dialog (default interval currently 07:30–20:00; will normalize later)
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(ShopComponentsTestTags.DIALOG_SAVE, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Wait for Opening Hours dialog to dismiss
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onAllNodesWithTag(ShopComponentsTestTags.DIALOG_TITLE, useUnmergedTree = true)
              .fetchSemanticsNodes()
              .isEmpty()
        })

    // Seed a couple of games so the search dialog has results
    runBlocking {
      val gamesPath = com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
      db.collection(gamesPath)
          .document("g_catan")
          .set(
              com.github.meeplemeet.model.shared.game.GameNoUid(
                  name = "Catan",
                  description = "Settlers of Catan",
                  imageURL = "https://example.com/catan.jpg",
                  minPlayers = 3,
                  maxPlayers = 4,
                  recommendedPlayers = 4,
                  averagePlayTime = 90,
                  genres = listOf("1", "2")))
          .await()

      db.collection(gamesPath)
          .document("g_ticket")
          .set(
              com.github.meeplemeet.model.shared.game.GameNoUid(
                  name = "Ticket to Ride",
                  description = "Train adventure game",
                  imageURL = "https://example.com/ticket.jpg",
                  minPlayers = 2,
                  maxPlayers = 5,
                  recommendedPlayers = 4,
                  averagePlayTime = 60,
                  genres = listOf("1")))
          .await()
    }

    // Expand to Games section header and add first game via UI
    scrollListToTag(
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX)
    composeTestRule.waitForIdle()

    ensureSectionExpanded(CreateShopScreenTestTags.SECTION_GAMES)

    composeTestRule.waitForIdle()
    composeTestRule.closeKeyboardSafely()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch(
        {
          composeTestRule.closeKeyboardSafely()
          composeTestRule.onNodeWithTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON).performClick()
          true
        },
        timeoutMs = 50_000)

    // Wait for the Game Stock dialog to appear
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onNodeWithTag(ShopFormTestTags.GAME_STOCK_DIALOG_WRAPPER, useUnmergedTree = true)
              .assertExists()
          true
        })

    // Add Catan stock
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_FIELD, useUnmergedTree = true)
          .assertExists()
          .performTextInput("Catan")
      true
    })
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_ITEM + ":0", useUnmergedTree = true)
              .assertExists()
          true
        })

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_ITEM + ":0", useUnmergedTree = true)
          .performClick()
      true
    })

    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule
              .onNodeWithTag(ShopComponentsTestTags.GAME_DIALOG_SAVE, useUnmergedTree = true)
              .assertExists()
          true
        })

    // Increase quantity to 5
    setSliderValue(5)

    // Click save and wait for dialog to dismiss
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(ShopComponentsTestTags.GAME_DIALOG_SAVE, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })

    // Verify Catan card now appears in Games section
    runCatching {
      val gamesContentTag =
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX
      val gamesToggleTag =
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_TOGGLE_SUFFIX
      val gamesHeaderTag =
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX
      val contentNodes =
          composeTestRule
              .onAllNodesWithTag(gamesContentTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
      if (contentNodes.isEmpty()) {
        scrollListToTag(gamesHeaderTag)
        composeTestRule.waitUntilWithCatch({
          composeTestRule
              .onNodeWithTag(gamesToggleTag, useUnmergedTree = true)
              .assertExists()
              .performClick()
          true
        })
        composeTestRule.waitForIdle()
      }
    }
    val targetName = "Catan"
    val itemTag = "${ShopComponentsTestTags.GAME_SEARCH_ITEM}:$0"
    scrollListToTag(
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX)
    // Wait until either the item tag or the item text is present
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 20_000,
        predicate = {
          val byTag =
              composeTestRule
                  .onAllNodesWithTag(itemTag, useUnmergedTree = true)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          val byTextUnmerged =
              composeTestRule
                  .onAllNodes(hasText(targetName), useUnmergedTree = true)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          val byTextMerged =
              composeTestRule
                  .onAllNodesWithText(targetName, useUnmergedTree = false)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          byTag || byTextUnmerged || byTextMerged
        })
    composeTestRule.closeKeyboardSafely()
    composeTestRule.waitUntilWithCatch(
        {
          composeTestRule.closeKeyboardSafely()
          composeTestRule.onNodeWithTag(CreateShopScreenTestTags.GAMES_ADD_BUTTON).performClick()
          true
        },
        timeoutMs = 50_000)

    // Add Ticket to Ride stock
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      try {
        composeTestRule
            .onNodeWithTag(ShopFormTestTags.GAME_STOCK_DIALOG_WRAPPER, useUnmergedTree = true)
            .assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_FIELD, useUnmergedTree = true)
        .assertExists()
        .performTextInput("Ticket")

    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      try {
        composeTestRule
            .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_ITEM + ":0", useUnmergedTree = true)
            .assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.GAME_SEARCH_ITEM + ":0", useUnmergedTree = true)
        .performClick()

    composeTestRule.waitForIdle()

    // Wait for save button
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      try {
        composeTestRule
            .onNodeWithTag(ShopComponentsTestTags.GAME_DIALOG_SAVE, useUnmergedTree = true)
            .assertExists()
        true
      } catch (_: Throwable) {
        false
      }
    }

    // Increase quantity to 3
    setSliderValue(7)

    // Click save and wait for dialog to dismiss
    composeTestRule
        .onNodeWithTag(ShopComponentsTestTags.GAME_DIALOG_SAVE, useUnmergedTree = true)
        .assertExists()
        .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      try {
        composeTestRule
            .onAllNodesWithTag(ShopFormTestTags.GAME_STOCK_DIALOG_WRAPPER, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isEmpty()
      } catch (_: Throwable) {
        false
      }
    }

    composeTestRule.waitForIdle()

    // Verify both game cards appear
    runCatching {
      val gamesContentTag =
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_CONTENT_SUFFIX
      val gamesToggleTag =
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_TOGGLE_SUFFIX
      val gamesHeaderTag =
          CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX
      val contentNodes =
          composeTestRule
              .onAllNodesWithTag(gamesContentTag, useUnmergedTree = true)
              .fetchSemanticsNodes()
      if (contentNodes.isEmpty()) {
        scrollListToTag(gamesHeaderTag)
        composeTestRule
            .onNodeWithTag(gamesToggleTag, useUnmergedTree = true)
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(gamesToggleTag, useUnmergedTree = true)
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()
      }
    }
    scrollListToTag(
        CreateShopScreenTestTags.SECTION_GAMES + CreateShopScreenTestTags.SECTION_HEADER_SUFFIX)
    composeTestRule.closeKeyboardSafely()
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 20_000,
        predicate = {
          var byTag =
              composeTestRule
                  .onAllNodesWithTag(itemTag, useUnmergedTree = true)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          var byTextUnmerged =
              composeTestRule
                  .onAllNodes(hasText(targetName), useUnmergedTree = true)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          var byTextMerged =
              composeTestRule
                  .onAllNodesWithText(targetName, useUnmergedTree = false)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          val catan = byTag || byTextUnmerged || byTextMerged

          byTag =
              composeTestRule
                  .onAllNodesWithTag("Ticket", useUnmergedTree = true)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          byTextUnmerged =
              composeTestRule
                  .onAllNodes(hasText(targetName), useUnmergedTree = true)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          byTextMerged =
              composeTestRule
                  .onAllNodesWithText(targetName, useUnmergedTree = false)
                  .fetchSemanticsNodes()
                  .isNotEmpty()
          val ticket = byTag || byTextUnmerged || byTextMerged
          catan && ticket
        })

    // Create the store
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(ShopComponentsTestTags.ACTION_CREATE, useUnmergedTree = true)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Wait for store to be created and map to reload
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertExists()
          true
        })

    // Navigate to Discussions tab to create a new post announcing the store is live
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertExists().performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Wait to be on discussions overview
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          composeTestRule.onNodeWithTag(FeedsOverviewTestTags.ADD_POST_BUTTON).assertExists()
          true
        })

    // Create the new post
    val announcementTitle = "Our store is now open!"
    val announcementDescription = "The store is officially live! Come visit us!"

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(FeedsOverviewTestTags.ADD_POST_BUTTON)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreatePostTestTags.TITLE_FIELD)
          .assertExists()
          .performTextInput(announcementTitle)
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreatePostTestTags.BODY_FIELD)
          .assertExists()
          .performTextInput(announcementDescription)
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreatePostTestTags.TAG_INPUT_FIELD)
          .assertExists()
          .performTextInput("launch")
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(CreatePostTestTags.TAG_ADD_BUTTON).performClick()
      true
    })
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreatePostTestTags.POST_BUTTON)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Verify the new discussion appears in the list
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          composeTestRule.onNodeWithText(announcementTitle, useUnmergedTree = true).assertExists()
          true
        })

    // Open the new post
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithText(announcementTitle, useUnmergedTree = true)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()

    // Make other users react to the post through repository (as comments on the post)
    runBlocking {
      // Fetch the postId by title from posts collection
      val postId =
          postRepository.collection
              .whereEqualTo("title", announcementTitle)
              .get()
              .await()
              .documents
              .first()
              .id

      // Each user comments on the announcement post through backend
      commentOnPost(
          postId,
          mapOf(
              aliceHandle to "Congratulations!", bobHandle to "Amazing!", eveHandle to "Love it!"))
    }

    // Wait for all reactions to appear on screen in THE POST
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          composeTestRule.onNodeWithText("Congratulations!", useUnmergedTree = true).assertExists()
          composeTestRule.onNodeWithText("Amazing!", useUnmergedTree = true).assertExists()
          composeTestRule.onNodeWithText("Love it!", useUnmergedTree = true).assertExists()
          true
        })

    // Wait until author names are resolved (avoid <Unknown User>)
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 10_000,
        predicate = {
          composeTestRule
              .onNodeWithText("Eve", useUnmergedTree = true, substring = false)
              .assertExists()
          composeTestRule
              .onNodeWithText("Alice", useUnmergedTree = true, substring = false)
              .assertExists()
          composeTestRule
              .onNodeWithText("Bob", useUnmergedTree = true, substring = false)
              .assertExists()
          true
        })

    // Verify all reactions are displayed in THE POST
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText("Congratulations!", useUnmergedTree = true).assertIsDisplayed()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText("Amazing!", useUnmergedTree = true).assertIsDisplayed()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText("Love it!", useUnmergedTree = true).assertIsDisplayed()
      true
    })
  }

  /** Helper function to create a user through repositories */
  private suspend fun createUserThroughRepo(name: String, handle: String, email: String): Account {
    // Create account in repository
    val account =
        accountRepository.createAccount(
            userHandle = handle, name = name, email = email, photoUrl = null)

    // Create handle in repository
    handlesRepository.createAccountHandle(account.uid, handle)

    return account
  }

  /** Helper function to add a member to a discussion during creation */
  private fun addMemberToDiscussion(handle: String) {
    // Search for the user by handle
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(AddDiscussionTestTags.ADD_MEMBERS, useUnmergedTree = true)
          .assertExists()
          .performTextInput(handle)
      true
    })

    composeTestRule.waitForIdle()

    // Wait for search results to appear
    composeTestRule.waitUntilWithCatch(
        timeoutMs = 5_000,
        predicate = {
          composeTestRule.onNodeWithTag(AddDiscussionTestTags.ADD_MEMBERS_ELEMENT).assertExists()
          true
        })

    composeTestRule.waitForIdle()

    // Click on the member from search results to add them
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(AddDiscussionTestTags.ADD_MEMBERS_ELEMENT)
          .assertExists()
          .performClick()
      true
    })

    composeTestRule.waitForIdle()

    // Clear the search field for the next member
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(AddDiscussionTestTags.ADD_MEMBERS, useUnmergedTree = true)
          .performTextClearance()
      true
    })
  }

  /**
   * Helper: add top-level comments to a Post from a set of user handles. parentId is the postId for
   * top-level comments.
   */
  private suspend fun commentOnPost(postId: String, comments: Map<String, String>) {
    // Resolve handles -> account IDs
    val handleToUid: Map<String, String> =
        comments.keys.associateWith { handle ->
          handlesRepository.collection.document(handle).get().await().getString("accountId")!!
        }

    // Add each comment as a top-level comment on the post
    for ((handle, text) in comments) {
      val uid = handleToUid[handle]!!
      postRepository.addComment(postId = postId, text = text, authorId = uid, parentId = postId)
      composeTestRule.waitForIdle()
    }
  }
  /** Set slider value by performing swipe gesture. */
  private fun setSliderValue(targetValue: Int, maxValue: Int = 100) {
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(ShopComponentsTestTags.QTY_INPUT_FIELD).performTouchInput {
        val fraction = targetValue.toFloat() / maxValue.toFloat()
        val targetX = left + (right - left) * fraction
        // Slider is aligned to BottomCenter, so we should touch near the bottom
        val targetY = top + (bottom - top) * 0.9f

        down(Offset(center.x, targetY))
        moveTo(Offset(targetX, targetY))
        up()
      }
      true
    })
    composeTestRule.waitForIdle()
  }
}
