// Class based on DiscussionsIntegrationTest.kt and adapted for DiscussionsOverviewScreen
// Tests were partially done using ChatGPT-5 Thinking Extended and partially done manually
package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionPreview
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionsOverviewScreenTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var vm: FirestoreViewModel
  private lateinit var repo: FirestoreRepository
  private lateinit var nav: NavigationActions
  private lateinit var testScope: TestScope

  private val me = Account(uid = "me", name = "Marco", email = "test_marco@epfl.ch")
  private val bob = Account(uid = "u2", name = "Bob", email = "test_bob@epfl.ch")
  private val zoe = Account(uid = "u3", name = "Zoe", email = "test_zow@epfl.ch")

  private var d1 =
      Discussion(
          uid = "d1",
          name = "Catan Crew",
          description = "",
          messages = emptyList(),
          participants = listOf(me.uid, bob.uid),
          admins = listOf(me.uid),
          creatorId = me.uid)
  private var d2 =
      Discussion(
          uid = "d2",
          name = "Gloomhaven",
          description = "",
          messages = emptyList(),
          participants = listOf(me.uid, bob.uid),
          admins = listOf(me.uid),
          creatorId = me.uid)
  private var d3 =
      Discussion(
          uid = "d3",
          name = "Weekend Plan",
          description = "",
          messages = emptyList(),
          participants = listOf(me.uid),
          admins = listOf(me.uid),
          creatorId = me.uid)

  private var p1 =
      DiscussionPreview(
          uid = "d1",
          lastMessage = "Bring snacks",
          lastMessageSender = me.uid,
          lastMessageAt = Timestamp(Date(System.currentTimeMillis())),
          unreadCount = 3)
  private var p2 =
      DiscussionPreview(
          uid = "d2",
          lastMessage = "Ready at 7?",
          lastMessageSender = bob.uid,
          lastMessageAt = Timestamp(Date(System.currentTimeMillis() - 60_000L)),
          unreadCount = 1)

  @Before
  fun setup() {

    // Mockings
    val dispatcher = StandardTestDispatcher()
    testScope = TestScope(dispatcher)
    nav = mockk(relaxed = true)
    repo = mockk(relaxed = true)

    coEvery { repo.getAccount("u2") } returns bob
    coEvery { repo.getAccount("u3") } returns zoe
    coEvery { repo.getAccount("me") } returns me

    vm = FirestoreViewModel(repo)

    // Injections
    val discussionFlowsField = vm::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val discussionMap = discussionFlowsField.get(vm) as MutableMap<String, StateFlow<Discussion?>>
    discussionMap["d1"] = MutableStateFlow(d1)
    discussionMap["d2"] = MutableStateFlow(d2)
    discussionMap["d3"] = MutableStateFlow(d3)

    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    previewMap[me.uid] = MutableStateFlow(mapOf("d1" to p1, "d2" to p2))
  }

  /* ================================================================
   * Tests
   * ================================================================ */

  @Test
  fun should_pass_test_setup() {
    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    assert(me.name == "Marco")
    assert(bob.name == "Bob")
    assert(d1.name == "Catan Crew")
    assert(p1.lastMessageSender == me.uid)
    assert(p2.lastMessageSender == bob.uid)
  }

  @Test
  fun overview_back_button_calls_navigation_goBack() {
    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Back").performClick()
    verify { nav.goBack() }
  }

  @Test
  fun overview_shows_discussion_cards_with_names_messages_and_unread_counts() {
    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()

    compose.onNodeWithText("Catan Crew").assertIsDisplayed()
    compose.onNodeWithText("Gloomhaven").assertIsDisplayed()

    compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Bob: Ready at 7?", substring = true).assertIsDisplayed()

    compose.onNodeWithText("3").assertIsDisplayed()
    compose.onNodeWithText("1").assertIsDisplayed()
  }

  @Test
  fun overview_empty_state_shows_no_discussions_text() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    previewMap[me.uid] = MutableStateFlow(emptyMap())

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("No discussions yet").assertIsDisplayed()
  }

  @Test
  fun overview_non_empty_state_hides_empty_message() {
    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("No discussions yet").assertDoesNotExist()
  }

  @Test
  fun overview_uses_default_discussion_name_when_flow_is_null() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val p4 =
        DiscussionPreview(
            uid = "d4",
            lastMessage = "Ping",
            lastMessageSender = me.uid,
            lastMessageAt = Timestamp.now(),
            unreadCount = 0)
    previewMap[me.uid]!!.value = previewMap[me.uid]!!.value.toMutableMap().apply { put("d4", p4) }

    val discussionFlowsField = vm::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val discussionMap = discussionFlowsField.get(vm) as MutableMap<String, StateFlow<Discussion?>>
    discussionMap["d4"] = MutableStateFlow<Discussion?>(null)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Discussion").assertIsDisplayed()
    compose.onNodeWithText("You: Ping", substring = true).assertIsDisplayed()
  }

  @Test
  fun overview_renders_no_messages_placeholder_when_last_message_blank() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val p3 =
        DiscussionPreview(
            uid = "d3",
            lastMessage = "",
            lastMessageSender = me.uid,
            lastMessageAt = Timestamp.now(),
            unreadCount = 0)
    previewMap[me.uid]!!.value = previewMap[me.uid]!!.value.toMutableMap().apply { put("d3", p3) }

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("(No messages yet)").assertIsDisplayed()
    compose.onNodeWithText("Weekend Plan").assertIsDisplayed()
  }

  @Test
  fun overview_handles_blank_senderId_without_prefix() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val p5 =
        DiscussionPreview(
            uid = "d1",
            lastMessage = "Hello there",
            lastMessageSender = "",
            lastMessageAt = Timestamp.now(),
            unreadCount = 0)
    previewMap[me.uid]!!.value = mapOf("d1" to p5)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Hello there").assertIsDisplayed()
  }

  @Test
  fun overview_prefixes_non_me_sender_name_when_known() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val pKnown =
        DiscussionPreview(
            uid = "d2",
            lastMessage = "See you!",
            lastMessageSender = "u3", // Zoe
            lastMessageAt = Timestamp.now(),
            unreadCount = 2)
    previewMap[me.uid]!!.value = mapOf("d2" to pKnown)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Zoe: See you!", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Gloomhaven").assertIsDisplayed()
    compose.onNodeWithText("2").assertIsDisplayed()
  }

  @Test
  fun overview_shows_zero_unread_badge() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val pZero =
        DiscussionPreview(
            uid = "d1",
            lastMessage = "All read",
            lastMessageSender = me.uid,
            lastMessageAt = Timestamp.now(),
            unreadCount = 0)
    previewMap[me.uid]!!.value = mapOf("d1" to pZero)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("0").assertIsDisplayed()
    compose.onNodeWithText("Catan Crew").assertIsDisplayed()
    compose.onNodeWithText("You: All read", substring = true).assertIsDisplayed()
  }

  @Test
  fun overview_updates_when_preview_flow_changes() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    val previewsFlow = previewMap[me.uid]!!

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
    compose.onNodeWithText("3").assertIsDisplayed()

    val updatedP1 = p1.copy(lastMessage = "Changed plan", unreadCount = 9)
    previewsFlow.value = mapOf("d1" to updatedP1, "d2" to p2)

    compose.waitForIdle()
    compose.onNodeWithText("You: Changed plan", substring = true).assertIsDisplayed()
    compose.onNodeWithText("9").assertIsDisplayed()
  }

  @Test
  fun overview_sorts_cards_by_latest_preview_time_descending() {
    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()

    val catanTop = compose.onNodeWithText("Catan Crew").fetchSemanticsNode().boundsInRoot.top
    val gloomTop = compose.onNodeWithText("Gloomhaven").fetchSemanticsNode().boundsInRoot.top
    assert(catanTop < gloomTop)

    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val newerP2 = p2.copy(lastMessageAt = Timestamp(Date(System.currentTimeMillis() + 1_000L)))
    previewMap[me.uid]!!.value = mapOf("d1" to p1, "d2" to newerP2)

    compose.waitForIdle()

    val catanTop2 = compose.onNodeWithText("Catan Crew").fetchSemanticsNode().boundsInRoot.top
    val gloomTop2 = compose.onNodeWithText("Gloomhaven").fetchSemanticsNode().boundsInRoot.top
    assert(gloomTop2 < catanTop2)
  }

  @Test
  fun overview_updates_sender_prefix_when_sender_changes() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    val flow = previewMap[me.uid]!!

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Bob: Ready at 7?", substring = true).assertIsDisplayed()

    val p2Zoe = p2.copy(lastMessageSender = "u3")
    flow.value = mapOf("d1" to p1, "d2" to p2Zoe)

    compose.waitForIdle()
    compose.onNodeWithText("Zoe: Ready at 7?", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Bob: Ready at 7?", substring = true).assertDoesNotExist()
  }

  @Test
  fun overview_non_me_sender_with_blank_name_has_no_prefix() {
    coEvery { repo.getAccount("ux") } returns Account(uid = "ux", name = "", email = "test@epfl.ch")

    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    val pUnknownName =
        DiscussionPreview(
            uid = "d1",
            lastMessage = "Hi!",
            lastMessageSender = "ux",
            lastMessageAt = Timestamp.now(),
            unreadCount = 5)
    previewMap[me.uid]!!.value = mapOf("d1" to pUnknownName)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Hi!").assertIsDisplayed()
    compose.onNodeWithText("5").assertIsDisplayed()
  }

  @Test
  fun overview_large_unread_count_is_displayed() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    val pLarge = p1.copy(unreadCount = 123, lastMessage = "Big day")
    previewMap[me.uid]!!.value = mapOf("d1" to pLarge)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("123").assertIsDisplayed()
    compose.onNodeWithText("You: Big day", substring = true).assertIsDisplayed()
  }

  @Test
  fun overview_switches_from_empty_to_non_empty_via_flow_update() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previewMap =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    val flow = previewMap[me.uid]!!
    flow.value = emptyMap()

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("No discussions yet").assertIsDisplayed()

    flow.value = mapOf("d1" to p1)
    compose.waitForIdle()
    compose.onNodeWithText("No discussions yet").assertDoesNotExist()
    compose.onNodeWithText("Catan Crew").assertIsDisplayed()
    compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
  }

  @Test
  fun overview_switches_from_non_empty_to_empty_via_flow_update() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>
    val flow = map[me.uid]!!
    flow.value = mapOf("d1" to p1)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Catan Crew").assertIsDisplayed()

    flow.value = emptyMap()
    compose.waitForIdle()
    compose.onNodeWithText("No discussions yet").assertIsDisplayed()
    compose.onNodeWithText("Catan Crew").assertDoesNotExist()
  }

  @Test
  fun overview_discussion_title_updates_from_default_when_flow_goes_from_null_to_value() {
    // Prepare a preview d5 with null discussion flow, then later provide a Discussion
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previews =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val p5 =
        DiscussionPreview(
            uid = "d5",
            lastMessage = "Heads up",
            lastMessageSender = me.uid,
            lastMessageAt = Timestamp.now(),
            unreadCount = 0)
    previews[me.uid]!!.value = mapOf("d5" to p5)

    val discussionFlowsField = vm::class.java.getDeclaredField("discussionFlows")
    discussionFlowsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val flows = discussionFlowsField.get(vm) as MutableMap<String, StateFlow<Discussion?>>
    val d5Flow = MutableStateFlow<Discussion?>(null)
    flows["d5"] = d5Flow

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Discussion").assertIsDisplayed()
    compose.onNodeWithText("You: Heads up", substring = true).assertIsDisplayed()

    val d5 =
        Discussion(
            uid = "d5",
            name = "New Room",
            description = "",
            messages = emptyList(),
            participants = listOf(me.uid),
            admins = listOf(me.uid),
            creatorId = me.uid)
    d5Flow.value = d5
    compose.waitForIdle()

    compose.onNodeWithText("New Room").assertIsDisplayed()
    compose.onNodeWithText("Discussion").assertDoesNotExist()
  }

  @Test
  fun overview_you_sender_with_blank_message_has_no_prefix_and_shows_placeholder() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previews =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val pBlankMine =
        DiscussionPreview(
            uid = "d1",
            lastMessage = "",
            lastMessageSender = me.uid, // isMe = true
            lastMessageAt = Timestamp.now(),
            unreadCount = 4)
    previews[me.uid]!!.value = mapOf("d1" to pBlankMine)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    // Placeholder but NO "You:" prefix anywhere
    compose.onNodeWithText("(No messages yet)").assertIsDisplayed()
    compose.onNodeWithText("You:", substring = true).assertDoesNotExist()
    compose.onNodeWithText("4").assertIsDisplayed()
  }

  @Test
  fun overview_negative_unread_count_is_rendered_as_text() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previews =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val pNeg = p1.copy(unreadCount = -3)
    previews[me.uid]!!.value = mapOf("d1" to pNeg)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("-3").assertIsDisplayed()
  }

  @Test
  fun overview_3_item_sorting_places_most_recent_on_top() {
    val previewStatesField = vm::class.java.getDeclaredField("previewStates")
    previewStatesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val previews =
        previewStatesField.get(vm)
            as MutableMap<String, MutableStateFlow<Map<String, DiscussionPreview>>>

    val now = System.currentTimeMillis()
    val pNewest =
        DiscussionPreview(
            uid = "d3",
            lastMessage = "Tomorrow?",
            lastMessageSender = me.uid,
            lastMessageAt = Timestamp(Date(now + 5000L)),
            unreadCount = 7)
    previews[me.uid]!!.value = mapOf("d1" to p1, "d2" to p2, "d3" to pNewest)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(viewModel = vm, currentUser = me, navigation = nav) }
    }
    compose.waitForIdle()

    val topD3 = compose.onNodeWithText("Weekend Plan").fetchSemanticsNode().boundsInRoot.top
    val topD1 = compose.onNodeWithText("Catan Crew").fetchSemanticsNode().boundsInRoot.top
    val topD2 = compose.onNodeWithText("Gloomhaven").fetchSemanticsNode().boundsInRoot.top
    assert(topD3 < topD1 && topD3 < topD2)
    compose.onNodeWithText("You: Tomorrow?", substring = true).assertIsDisplayed()
    compose.onNodeWithText("7").assertIsDisplayed()
  }
}
