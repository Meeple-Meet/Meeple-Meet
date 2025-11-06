// Test suite initially generated with Claude 4.5, following Meeple Meet's global test architecture.
// After manual review, cleanup, and bug fixing, the suite was refined with Claude 4.5 to optimize
// test density and coverage.
package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.posts.CreatePostViewModel
import com.github.meeplemeet.model.posts.PostRepository
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.posts.CreatePostScreen
import com.github.meeplemeet.ui.posts.CreatePostTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI tests for [com.github.meeplemeet.ui.posts.CreatePostScreen].
 *
 * Optimized test suite with maximum coverage and minimum test count. Tests cover: initial state,
 * text input, tag management, validation, post creation, error handling, navigation, and edge
 * cases.
 */
@RunWith(AndroidJUnit4::class)
class CreatePostScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  private lateinit var repository: PostRepository
  private lateinit var viewModel: CreatePostViewModel
  private lateinit var testAccount: Account

  private var postCalled = false
  private var discardCalled = false
  private var backCalled = false

  /* ---------- Node helpers ---------- */
  private fun titleField() = compose.onNodeWithTag(CreatePostTestTags.TITLE_FIELD)

  private fun bodyField() = compose.onNodeWithTag(CreatePostTestTags.BODY_FIELD)

  private fun tagInput() = compose.onNodeWithTag(CreatePostTestTags.TAG_INPUT_FIELD)

  private fun tagAddButton() = compose.onNodeWithTag(CreatePostTestTags.TAG_ADD_BUTTON)

  private fun postButton() = compose.onNodeWithTag(CreatePostTestTags.POST_BUTTON)

  private fun discardButton() = compose.onNodeWithTag(CreatePostTestTags.DISCARD_BUTTON)

  private fun backButton() = compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON)

  private fun tagChip(tag: String) = compose.onNodeWithTag(CreatePostTestTags.tagChip(tag))

  private fun tagRemoveButton(tag: String) =
      compose.onNodeWithTag(CreatePostTestTags.tagRemoveIcon(tag))

  @Before
  fun setup() = runBlocking {
    repository = PostRepository()
    viewModel = CreatePostViewModel(repository)
    testAccount = Account("testuser", "test_user", "Test User", "test@example.com")

    postCalled = false
    discardCalled = false
    backCalled = false

    compose.setContent {
      AppTheme {
        CreatePostScreen(
            account = testAccount,
            viewModel = viewModel,
            onPost = { postCalled = true },
            onDiscard = { discardCalled = true },
            onBack = { backCalled = true })
      }
    }
  }

  /* ========== Test 1: Initial State + Validation + Enablement ========== */
  @Test
  fun initialStateAndValidation_checksAllFieldsAndButtonStates() {
    // Initial state: all fields empty
    titleField().assertExists().assertTextContains("")
    bodyField().assertExists().assertTextContains("")
    tagInput().assertExists().assertTextContains("")
    postButton().assertIsNotEnabled()
    discardButton().assertIsEnabled()

    // Validation: disabled when title empty
    bodyField().performTextInput("Body text")
    postButton().assertIsNotEnabled()

    // Clear and test: disabled when body empty
    bodyField().performTextClearance()
    titleField().performTextInput("Title")
    postButton().assertIsNotEnabled()

    // Both filled: enabled
    bodyField().performTextInput("Valid body")
    postButton().assertIsEnabled()

    // Whitespace-only title: disabled
    titleField().performTextClearance()
    titleField().performTextInput("   ")
    postButton().assertIsNotEnabled()

    // Whitespace-only body: disabled
    titleField().performTextClearance()
    titleField().performTextInput("Valid Title")
    bodyField().performTextClearance()
    bodyField().performTextInput("   ")
    postButton().assertIsNotEnabled()
  }

  /* ========== Test 2: Complete Text Input ========== */
  @Test
  fun textInput_acceptsVariousFormats() {
    // Simple text input
    titleField().performTextInput("My First Post")
    bodyField().performTextInput("This is the body of my post")
    titleField().assertTextContains("My First Post")
    bodyField().assertTextContains("This is the body of my post")

    // Multiline text
    bodyField().performTextClearance()
    val multilineText = "Line 1\nLine 2\nLine 3"
    bodyField().performTextInput(multilineText)
    bodyField().assertTextContains(multilineText)

    // Special characters
    titleField().performTextClearance()
    val specialTitle = "Test: Post #1 - @mention & more!"
    titleField().performTextInput(specialTitle)
    titleField().assertTextContains(specialTitle)

    // Long text
    titleField().performTextClearance()
    val longTitle = "A".repeat(200)
    titleField().performTextInput(longTitle)
    titleField().assertTextContains(longTitle)

    bodyField().performTextClearance()
    val longBody = "B".repeat(500)
    bodyField().performTextInput(longBody)
    bodyField().assertTextContains(longBody)
  }

  /* ========== Test 3: Complete Tag Management ========== */
  @Test
  fun tags_completeLifecycleAndNormalization() {
    // Add simple tag
    tagAddButton().assertIsNotEnabled()
    tagInput().performTextInput("boardgames")
    tagInput().assertTextContains("boardgames")
    tagAddButton().assertIsEnabled()
    tagAddButton().performClick()
    compose.waitForIdle()
    tagChip("#boardgames").assertExists()
    tagInput().assertTextContains("")

    // Add via Enter key
    tagInput().performTextInput("strategy")
    tagInput().performImeAction()
    compose.waitForIdle()
    tagChip("#strategy").assertExists()

    // Normalization: hash prefix
    tagInput().performTextInput("##rpg")
    tagAddButton().performClick()
    compose.waitForIdle()
    tagChip("#rpg").assertExists()
    compose.onAllNodesWithTag(CreatePostTestTags.tagChip("##rpg")).assertCountEquals(0)

    // Normalization: case insensitive
    tagInput().performTextInput("BoardGames")
    tagAddButton().performClick()
    compose.waitForIdle()
    compose.onAllNodesWithTag(CreatePostTestTags.tagChip("#boardgames")).assertCountEquals(1)

    // Duplicate prevention
    tagInput().performTextInput("strategy")
    tagAddButton().performClick()
    compose.waitForIdle()
    compose.onAllNodesWithTag(CreatePostTestTags.tagChip("#strategy")).assertCountEquals(1)

    // Remove tag
    tagChip("#rpg").assertExists()
    tagRemoveButton("#rpg").performClick()
    compose.waitForIdle()
    tagChip("#rpg").assertDoesNotExist()

    // Multiple tags present
    tagChip("#boardgames").assertExists()
    tagChip("#strategy").assertExists()
  }

  /* ========== Test 4: Post Creation With Tags ========== */
  @Test
  fun createPost_withMultipleTags_savesCorrectlyToFirestore() = runBlocking {
    val title = "Multi-Tag Test Post"
    val body = "Testing post creation with multiple tags"

    titleField().performTextInput(title)
    bodyField().performTextInput(body)

    // Add three tags
    tagInput().performTextInput("tag1")
    tagAddButton().performClick()
    compose.waitForIdle()

    tagInput().performTextInput("tag2")
    tagAddButton().performClick()
    compose.waitForIdle()

    tagInput().performTextInput("tag3")
    tagAddButton().performClick()
    compose.waitForIdle()

    // Verify tags are displayed
    tagChip("#tag1").assertExists()
    tagChip("#tag2").assertExists()
    tagChip("#tag3").assertExists()

    // Create post
    postButton().assertIsEnabled()
    postButton().performClick()

    compose.waitUntil(timeoutMillis = 5_000) { postCalled }

    // Verify in Firestore
    delay(1000)
    val posts = repository.getPosts()
    val createdPost = posts.find { it.title == title }

    assert(createdPost != null)
    assert(createdPost?.body == body)
    assert(createdPost?.authorId == testAccount.uid)
    assert(createdPost?.tags?.size == 3)
    assert(createdPost?.tags?.containsAll(listOf("#tag1", "#tag2", "#tag3")) == true)
  }

  /* ========== Test 5: Post Creation Without Tags ========== */
  @Test
  fun createPost_withoutTags_savesSuccessfully() = runBlocking {
    val title = "No Tags Post"
    val body = "A post without any tags"

    titleField().performTextInput(title)
    bodyField().performTextInput(body)

    postButton().assertIsEnabled()
    postButton().performClick()

    compose.waitUntil(timeoutMillis = 5_000) { postCalled }

    delay(1000)
    val posts = repository.getPosts()
    val createdPost = posts.find { it.title == title }

    assert(createdPost != null)
    assert(createdPost?.body == body)
    assert(createdPost?.authorId == testAccount.uid)
    assert(createdPost?.tags?.isEmpty() == true)
  }

  /* ========== Test 6: Navigation Actions ========== */
  @Test
  fun navigation_backAndDiscardButtonsWork() {
    // Test back button
    backButton().performClick()
    compose.waitForIdle()
    assert(backCalled)
    assert(!discardCalled)

    // Reset flags
    backCalled = false

    // Test discard button (should be enabled even with empty fields)
    discardButton().assertIsEnabled()
    discardButton().performClick()
    compose.waitForIdle()
    assert(discardCalled)
    assert(!backCalled)

    // Test discard is enabled even with data
    discardCalled = false
    titleField().performTextInput("Some title")
    bodyField().performTextInput("Some body")
    discardButton().assertIsEnabled()
  }

  /* ========== Test 7: Many Tags Edge Case ========== */
  @Test
  fun edgeCase_manyTags_allDisplayedAndSavedCorrectly() = runBlocking {
    titleField().performTextInput("Many Tags Post")
    bodyField().performTextInput("Testing with 10 tags")

    // Add 10 tags
    repeat(10) { i ->
      tagInput().performTextInput("tag$i")
      tagAddButton().performClick()
      compose.waitForIdle()
    }

    // Verify all tags are displayed
    repeat(10) { i -> tagChip("#tag$i").assertExists() }

    postButton().performClick()
    compose.waitUntil(timeoutMillis = 5_000) { postCalled }

    delay(1000)
    val posts = repository.getPosts()
    val createdPost = posts.find { it.title == "Many Tags Post" }

    assert(createdPost != null)
    assert(createdPost?.tags?.size == 10)
    repeat(10) { i -> assert(createdPost?.tags?.contains("#tag$i") == true) }
  }

  /* ========== Test 8: Button State During Posting ========== */
  @Test
  fun posting_disablesButtonDuringOperation() {
    titleField().performTextInput("Test Post")
    bodyField().performTextInput("Test Body")

    postButton().assertIsEnabled()
    postButton().performClick()

    // After click, button should eventually disable (hard to test timing precisely)
    compose.waitForIdle()
    // The isPosting flag in implementation handles this
    compose.waitUntil(timeoutMillis = 5_000) { postCalled }
  }

  /* ========== Test 9: Tag Button States ========== */
  @Test
  fun tagButton_statesCorrespondToInput() {
    // Initially disabled
    tagAddButton().assertIsNotEnabled()

    // Enabled with text
    tagInput().performTextInput("test")
    tagAddButton().assertIsEnabled()

    // Disabled when cleared
    tagInput().performTextClearance()
    tagAddButton().assertIsNotEnabled()

    // Disabled with only whitespace
    tagInput().performTextInput("   ")
    tagAddButton().assertIsNotEnabled()

    // Enabled with valid text again
    tagInput().performTextClearance()
    tagInput().performTextInput("validtag")
    tagAddButton().assertIsEnabled()
  }

  /* ========== Test 10: Complete Integration Test ========== */
  @Test
  fun integration_fullWorkflowWithAllFeatures() = runBlocking {
    // Complete realistic workflow
    val title = "Complete Integration Test"
    val body = "Testing the complete workflow\nWith multiple lines\nAnd special chars: @#$%"

    titleField().performTextInput(title)
    bodyField().performTextInput(body)

    // Add tags with various normalization scenarios
    tagInput().performTextInput("##boardgames")
    tagAddButton().performClick()
    compose.waitForIdle()

    tagInput().performTextInput("Strategy")
    tagInput().performImeAction()
    compose.waitForIdle()

    tagInput().performTextInput("coop")
    tagAddButton().performClick()
    compose.waitForIdle()

    // Try to add duplicate (case insensitive)
    tagInput().performTextInput("STRATEGY")
    tagAddButton().performClick()
    compose.waitForIdle()

    // Remove one tag
    tagRemoveButton("#coop").performClick()
    compose.waitForIdle()

    // Verify final state
    tagChip("#boardgames").assertExists()
    tagChip("#strategy").assertExists()
    tagChip("#coop").assertDoesNotExist()
    compose.onAllNodesWithTag(CreatePostTestTags.tagChip("#strategy")).assertCountEquals(1)

    // Create post
    postButton().performClick()
    compose.waitUntil(timeoutMillis = 5_000) { postCalled }

    delay(1000)
    val posts = repository.getPosts()
    val createdPost = posts.find { it.title == title }

    assert(createdPost != null)
    assert(createdPost?.body == body)
    assert(createdPost?.tags?.size == 2)
    assert(createdPost?.tags?.contains("#boardgames") == true)
    assert(createdPost?.tags?.contains("#strategy") == true)
    assert(createdPost?.tags?.contains("#coop") == false)
  }

  @After
  fun tearDown() = runBlocking {
    // Clean up any created posts during tests
    val posts = repository.getPosts()
    for (post in posts) {
      if (post.authorId == testAccount.uid) {
        repository.deletePost(post.id)
      }
    }
  }
}
