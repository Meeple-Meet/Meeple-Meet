package com.github.meeplemeet.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreatePostScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  /* ---------- Checkpoint helper ---------- */
  @get:Rule val ck = Checkpoint.rule()

  fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

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

  /* ========== ONE FAT TEST – EVERYTHING INSIDE CHECKPOINTS ========== */
  @Test
  fun fullCreatePostSmoke_allInCheckpoints() = runBlocking {

    /* 1. initial state + validation */
    checkpoint("Initial state – empty fields, buttons correct") {
      titleField().assertTextContains("")
      bodyField().assertTextContains("")
      tagInput().assertTextContains("")
      postButton().assertIsNotEnabled()
      discardButton().assertIsEnabled()
    }

    checkpoint("Disabled when title empty") {
      bodyField().performTextInput("Body")
      postButton().assertIsNotEnabled()
    }

    checkpoint("Disabled when body empty") {
      bodyField().performTextClearance()
      titleField().performTextInput("Title")
      postButton().assertIsNotEnabled()
    }

    checkpoint("Enabled when both non-empty") {
      bodyField().performTextInput("Valid body")
      postButton().assertIsEnabled()
    }

    checkpoint("Disabled on whitespace-only title") {
      titleField().performTextClearance()
      titleField().performTextInput("   ")
      postButton().assertIsNotEnabled()
    }

    checkpoint("Disabled on whitespace-only body") {
      titleField().performTextClearance()
      titleField().performTextInput("Valid Title")
      bodyField().performTextClearance()
      bodyField().performTextInput("   ")
      postButton().assertIsNotEnabled()
    }

    /* 2. text input varieties */
    checkpoint("Simple text input accepted") {
      titleField().performTextClearance()
      bodyField().performTextClearance()
      titleField().performTextInput("My First Post")
      bodyField().performTextInput("This is the body")
      titleField().assertTextContains("My First Post")
      bodyField().assertTextContains("This is the body")
    }

    checkpoint("Multiline body") {
      bodyField().performTextClearance()
      val multi = "Line 1\nLine 2\nLine 3"
      bodyField().performTextInput(multi)
      bodyField().assertTextContains(multi)
    }

    checkpoint("Special chars in title") {
      titleField().performTextClearance()
      val special = "Test: Post #1 - @mention & more!"
      titleField().performTextInput(special)
      titleField().assertTextContains(special)
    }

    checkpoint("Long text") {
      titleField().performTextClearance()
      bodyField().performTextClearance()
      val longTitle = "A".repeat(200)
      val longBody = "B".repeat(500)
      titleField().performTextInput(longTitle)
      bodyField().performTextInput(longBody)
      titleField().assertTextContains(longTitle)
      bodyField().assertTextContains(longBody)
    }

    /* 3. tag life-cycle */
    checkpoint("Add simple tag") {
      tagInput().performTextInput("boardgames")
      tagAddButton().performClick()
      tagChip("#boardgames").assertExists()
      tagInput().assertTextContains("")
      removeAllTags()
    }

    checkpoint("Add via IME") {
      tagInput().performTextInput("strategy")
      tagInput().performImeAction()
      tagChip("#strategy").assertExists()
      removeAllTags()
    }

    checkpoint("Normalize hash prefix") {
      tagInput().performTextInput("##rpg")
      tagAddButton().performClick()
      tagChip("#rpg").assertExists()
      compose.onAllNodesWithTag(CreatePostTestTags.tagChip("##rpg")).assertCountEquals(0)
      removeAllTags()
    }

    checkpoint("Case-insensitive duplicate blocked") {
      tagInput().performTextInput("BoardGames")
      tagAddButton().performClick()
      compose.onAllNodesWithTag(CreatePostTestTags.tagChip("#boardgames")).assertCountEquals(1)
      removeAllTags()
    }

    /* 4. post with tags */
    checkpoint("Fill fields + three tags") {
      titleField().performTextClearance()
      bodyField().performTextClearance()
      titleField().performTextInput("Multi-Tag Test")
      bodyField().performTextInput("Testing post with tags")

      listOf("tag1", "tag2", "tag3").forEach { t ->
        tagInput().performTextInput(t)
        tagAddButton().performClick()
      }
      tagChip("#tag1").assertExists()
      tagChip("#tag2").assertExists()
      tagChip("#tag3").assertExists()
    }

    checkpoint("Create post – saved in Firestore") {
      postButton().performClick()
      compose.waitUntil(timeoutMillis = 800) { postCalled }
      runBlocking {
        val posts = repository.getPosts()
        val created = posts.find { it.title == "Multi-Tag Test" }
        assert(created != null)
        assert(created?.body == "Testing post with tags")
        assert(created?.authorId == testAccount.uid)
        assert(created?.tags?.size == 3)
        assert(created?.tags?.containsAll(listOf("#tag1", "#tag2", "#tag3")) == true)
        removeAllTags()
      }
    }

    /* 5. post without tags */
    checkpoint("Post without tags") {
      titleField().performTextClearance()
      bodyField().performTextClearance()
      titleField().performTextInput("No Tags Post")
      bodyField().performTextInput("A post without any tags")

      postCalled = false
      postButton().performClick()
      compose.waitUntil(timeoutMillis = 800) { postCalled }

      runBlocking {
        val posts = repository.getPosts()
        val created = posts.find { it.title == "No Tags Post" }
        assert(created != null)
        assert(created?.tags?.isEmpty() == true)
      }
      removeAllTags()
    }

    /* 6. navigation */
    checkpoint("Back button callback") {
      backCalled = false
      backButton().performClick()
      assert(backCalled)
    }

    checkpoint("Discard button callback") {
      discardCalled = false
      discardButton().performClick()
      assert(discardCalled)
    }

    /* 7. many tags edge case */
    checkpoint("Add 10 tags quickly") {
      titleField().performTextClearance()
      bodyField().performTextClearance()
      titleField().performTextInput("Many Tags Post")
      bodyField().performTextInput("Testing with 10 tags")

      repeat(10) { i ->
        tagInput().performTextInput("tag$i")
        tagAddButton().performClick()
      }
      repeat(10) { i -> tagChip("#tag$i").assertExists() }
    }

    checkpoint("Create 10-tag post") {
      postCalled = false
      postButton().performClick()
      compose.waitUntil(timeoutMillis = 800) { postCalled }

      runBlocking {
        val posts = repository.getPosts()
        val created = posts.find { it.title == "Many Tags Post" }
        assert(created?.tags?.size == 10)
        repeat(10) { i -> assert(created?.tags?.contains("#tag$i") == true) }
      }
    }
  }

  @Test
  fun fullIntegration() {
    /* 8. integration workflow */
    checkpoint("Full integration – realistic workflow") {
      titleField().performTextClearance()
      bodyField().performTextClearance()
      val title = "Integration Post"
      val body = "Complete flow\nWith lines\nAnd special: @#$%"
      titleField().performTextInput(title)
      bodyField().performTextInput(body)

      tagInput().performTextInput("##boardgames")
      tagAddButton().performClick()
      tagInput().performTextInput("Strategy")
      tagInput().performImeAction()
      tagInput().performTextInput("coop")
      tagAddButton().performClick()
      tagInput().performTextInput("STRATEGY") // duplicate
      tagAddButton().performClick()
      tagRemoveButton("#coop").performClick()

      tagChip("#boardgames").assertExists()
      tagChip("#strategy").assertExists()
      tagChip("#coop").assertDoesNotExist()
    }

    checkpoint("Create integration post") {
      postCalled = false
      postButton().performClick()
      compose.waitUntil(timeoutMillis = 800) { postCalled }

      runBlocking {
        val posts = repository.getPosts()
        val created = posts.find { it.title == "Integration Post" }
        assert(created != null)
        assert(created?.body == "Complete flow\nWith lines\nAnd special: @#$%")
        assert(created?.tags?.size == 2)
        assert(created?.tags?.contains("#boardgames") == true)
        assert(created?.tags?.contains("#strategy") == true)
      }
    }
  }

  @After
  fun tearDown() = runBlocking {
    repository
        .getPosts()
        .filter { it.authorId == testAccount.uid }
        .forEach { repository.deletePost(it.id) }
  }

  private fun removeAllTags() {
    with(compose) {
      while (true) {
        // 1. collect all tags that start with our prefix
        val removeTags =
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag))
                .fetchSemanticsNodes()
                .mapNotNull { node ->
                  node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf {
                    it.startsWith("create_post_tag_remove:")
                  }
                }
        if (removeTags.isEmpty()) break // nothing left → finished

        // 2. click the first one; if it no longer exists we are done
        val tag = removeTags.first()
        val node = onNode(hasTestTag(tag))
        if (!node.isDisplayed()) break // row disappeared → done
        node.performClick()
        waitForIdle() // wait for UI to settle
      }
    }
  }
}
