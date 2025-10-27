// This file was developed with partial assistance from ChatGPT Thinking Extend and refined by hand.
// Certain elements stemmed from discussions with the LLM about testing ideas and possible
// combinations.

package com.github.meeplemeet.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.repositories.FirestoreGameRepository
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.repositories.FirestoreSessionRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Session
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class SessionDetailsScreenTest {
  private lateinit var firestoreRepo: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel
  private lateinit var sessionRepo: FirestoreSessionRepository
  private lateinit var gameRepo: FirestoreGameRepository
  private lateinit var sessionVM: FirestoreSessionViewModel

  private val member = Account(uid = "user2", handle = "", name = "Alex", email = "alex@epfl.ch")
  private val admin = Account(uid = "user1", handle = "Alice", name = "Alice", email = "*")

  private val currentUser = admin

  private val discussionId = "discussion1"

  private lateinit var injectedDiscussionFlow: MutableStateFlow<Discussion?>
  private lateinit var baseDiscussion: Discussion

  @Before
  fun setUp() {
    firestoreRepo = mockk(relaxed = true)
    viewModel = spyk(FirestoreViewModel(firestoreRepo))

    baseDiscussion =
        Discussion(
            uid = discussionId,
            name = "Friday Night Meetup",
            description = "Let's play some board games!",
            creatorId = currentUser.uid,
            participants = listOf(currentUser.uid, member.uid),
            admins = listOf(currentUser.uid),
            session =
                Session(participants = listOf(currentUser.uid, member.uid), maxParticipants = 5))

    val field = viewModel::class.java.getDeclaredField("discussionFlows")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val map = field.get(viewModel) as MutableMap<String, StateFlow<Discussion?>>
    injectedDiscussionFlow = MutableStateFlow(baseDiscussion)
    map[discussionId] = injectedDiscussionFlow

    sessionRepo = mockk(relaxed = true)
    gameRepo = mockk(relaxed = true)
    sessionVM = spyk(FirestoreSessionViewModel(baseDiscussion, sessionRepo, gameRepo))
  }

  private fun readIntFromText(tag: String): Int =
      composeTestRule
          .onNodeWithTag(tag)
          .onChild()
          .assertIsDisplayed()
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text]
          .first()
          .text
          .toInt()

  @get:Rule val composeTestRule = createComposeRule()

  private val initialForm =
      SessionForm(
          title = "Friday Night Meetup",
          proposedGame = "",
          minPlayers = 3,
          maxPlayers = 6,
          participants =
              listOf(
                  Account(uid = "1", handle = "user1", name = "user1", email = "user1@example.com"),
                  Account(
                      uid = "2", handle = "johndoe", name = "John Doe", email = "john@example.com"),
                  Account(uid = "3", handle = "alice", name = "Alice", email = "alice@example.com"),
                  Account(uid = "4", handle = "bob", name = "Bob", email = "bob@example.com"),
                  Account(
                      uid = "5", handle = "robert", name = "Robert", email = "robert@example.com")),
          date = LocalDate.of(2025, 10, 15),
          time = java.time.LocalTime.of(19, 0),
          locationText = "Student Lounge")

  @Test
  fun display_admin_sees_all_core_sections_and_delete() {
    every { viewModel.getAccounts(any(), any()) } answers
        {
          secondArg<(List<Account>) -> Unit>().invoke(initialForm.participants)
        }

    composeTestRule.setContent {
      SessionDetailsScreen(
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          account = currentUser,
          initial = initialForm,
          discussion = baseDiscussion,
          onBack = {})
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertExists()
    composeTestRule.onAllNodesWithTag(SessionTestTags.PROPOSED_GAME).assertCountEquals(2)
    composeTestRule.onAllNodesWithTag(SessionTestTags.PROPOSED_GAME)[0].assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertExists()
    composeTestRule.onAllNodesWithTag(SessionTestTags.LOCATION_FIELD).assertCountEquals(2)
    composeTestRule.onAllNodesWithTag(SessionTestTags.LOCATION_FIELD)[0].assertIsDisplayed()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertExists()
  }

  @Test
  fun display_member_sees_core_sections_no_delete() {
    val memberUser = member.copy(uid = "user2")

    every { viewModel.getAccounts(any(), any()) } answers
        {
          secondArg<(List<Account>) -> Unit>().invoke(initialForm.participants)
        }

    injectedDiscussionFlow.value = baseDiscussion.copy(admins = listOf(admin.uid))

    composeTestRule.setContent {
      SessionDetailsScreen(
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          account = memberUser,
          initial = initialForm,
          discussion = baseDiscussion,
          onBack = {})
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.LOCATION_FIELD).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertDoesNotExist()
  }

  @Test
  fun date_open_and_confirm_closes_dialog() {
    composeTestRule.setContent {
      OrganizationSection(form = initialForm, onFormChange = {}, editable = true)
    }

    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICKER_OK_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_FIELD).assertExists()
  }

  @Test
  fun time_open_and_confirm_closes_dialog() {
    composeTestRule.setContent {
      OrganizationSection(form = initialForm, onFormChange = {}, editable = true)
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_FIELD).assertExists()
  }

  @Test
  fun participants_render_and_removal_button_triggers_callback() {
    val removed = mutableListOf<Account>()

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = admin,
          editable = true,
          onFormChange = { _, _ -> },
          onRemoveParticipant = { removed.add(it) })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onNodeWithTag("remove:John Doe").performClick()
    assert(removed.any { it.name == "John Doe" })
  }

  @Test
  fun member_cannot_remove_participants() {
    val removed = mutableListOf<Account>()
    val memberUser = member.copy(uid = "user2")

    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm,
          account = memberUser,
          editable = false,
          onFormChange = { _, _ -> },
          onRemoveParticipant = { removed.add(it) })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.PARTICIPANT_CHIPS).assertExists()
    composeTestRule.onAllNodes(hasTestTag("remove:John Doe")).assertCountEquals(0)
  }

  @Test
  fun slider_min_max_bubbles_present_and_values_match() {
    composeTestRule.setContent {
      ParticipantsSection(
          form = initialForm.copy(minPlayers = 3, maxPlayers = 6),
          account = admin,
          editable = true,
          onFormChange = { _, _ -> },
          onRemoveParticipant = {})
    }

    composeTestRule.onNodeWithTag("discrete_pill_slider").assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.MIN_PLAYERS).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.MAX_PLAYERS).assertExists()

    val min = readIntFromText(SessionTestTags.MIN_PLAYERS)
    val max = readIntFromText(SessionTestTags.MAX_PLAYERS)
    assert(min == 3)
    assert(max == 6)
  }

  @Test
  fun quit_button_click_is_wired_and_navigates_back_admin() {
    var backCalled = false

    composeTestRule.setContent {
      SessionDetailsScreen(
          account = admin,
          discussion = baseDiscussion,
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          initial = initialForm,
          onBack = { backCalled = true })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()
    assert(backCalled)
  }

  @Test
  @Ignore("ViewModel needs changes for this to work")
  fun quit_button_click_is_wired_and_navigates_back_member() {
    val memberUser = member.copy(uid = "user2")
    val other = Account(uid = "other", handle = "", name = "Other", email = "o@example.com")
    var backCalled = false

    // Rebuild a base discussion that actually has member + other in the session
    val memberDiscussion =
        baseDiscussion.copy(
            admins = listOf(admin.uid), // member is NOT admin
            session =
                Session(participants = listOf(memberUser.uid, other.uid), maxParticipants = 5),
            participants = listOf(memberUser.uid, other.uid))

    // Recreate the viewmodel with this discussion (so sessionVM sees the correct session)
    sessionVM = spyk(FirestoreSessionViewModel(memberDiscussion, sessionRepo, gameRepo))

    // Inject this new discussion into the flow
    injectedDiscussionFlow.value = memberDiscussion

    every { viewModel.getAccounts(any(), any()) } answers
        {
          secondArg<(List<Account>) -> Unit>().invoke(listOf(memberUser, other))
        }

    composeTestRule.setContent {
      SessionDetailsScreen(
          account = admin,
          discussion = baseDiscussion,
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          initial = initialForm.copy(participants = listOf(memberUser, other)),
          onBack = { backCalled = true })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.QUIT_BUTTON).performClick()

    assert(backCalled)
  }

  @Test
  fun delete_button_click_is_wired_and_calls_onback() {
    var backCalled = false

    composeTestRule.setContent {
      DeleteSessionBTN(
          sessionViewModel = sessionVM,
          currentUser = admin,
          discussion = baseDiscussion,
          userIsAdmin = true,
          onback = { backCalled = true })
    }

    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.DELETE_SESSION_BUTTON).performClick()
    assert(backCalled)
  }

  @Test
  fun admin_title_and_proposed_game_are_editable() {
    every { viewModel.getAccounts(any(), any()) } answers
        {
          secondArg<(List<Account>) -> Unit>().invoke(initialForm.participants)
        }

    composeTestRule.setContent {
      SessionDetailsScreen(
          account = admin,
          discussion = baseDiscussion,
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          initial = initialForm,
          onBack = {})
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assert(hasSetTextAction())
    composeTestRule.onAllNodesWithTag(SessionTestTags.PROPOSED_GAME)[1].assert(hasSetTextAction())
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).assertExists()
  }

  @Test
  fun member_title_and_proposed_game_are_readOnly() {
    val memberUser = member.copy(uid = "user2")
    every { viewModel.getAccounts(any(), any()) } answers
        {
          secondArg<(List<Account>) -> Unit>().invoke(initialForm.participants)
        }
    injectedDiscussionFlow.value = baseDiscussion.copy(admins = listOf(admin.uid))

    composeTestRule.setContent {
      SessionDetailsScreen(
          account = memberUser,
          discussion = baseDiscussion,
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          initial = initialForm,
          onBack = {})
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TITLE).assert(!hasSetTextAction())
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.PROPOSED_GAME).assert(!hasSetTextAction())
    composeTestRule.onNodeWithTag(SessionTestTags.DATE_PICK_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun topbar_onReturn_triggers_updateSession_for_admin() {
    var backCalled = false

    composeTestRule.setContent {
      SessionDetailsScreen(
          account = admin,
          discussion = baseDiscussion,
          viewModel = viewModel,
          sessionViewModel = sessionVM,
          initial = initialForm,
          onBack = { backCalled = true })
    }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON)
        .assertExists()
        .performSemanticsAction(SemanticsActions.OnClick)

    verify(atLeast = 1) {
      sessionVM.updateSession(any(), any(), any(), any(), any(), any(), any(), any(), any())
    }
    assert(backCalled)
  }

  @Test
  fun organizationSection_editable_shows_locationSearchField() {
    var newForm: SessionForm? = null

    composeTestRule.setContent {
      OrganizationSection(
          form = initialForm.copy(locationText = ""),
          onFormChange = { newForm = it },
          editable = true)
    }

    val container = composeTestRule.onAllNodesWithTag(SessionTestTags.LOCATION_FIELD)[0]
    container.assertExists()

    val input = container.onChildren().filter(hasSetTextAction())[0]
    input.performTextInput("Student")

    composeTestRule.onNodeWithText("Student Lounge").performClick()

    assert(newForm?.locationText == "Student Lounge")
  }

  @Test
  fun userChip_shows_avatar_and_no_remove_when_not_admin() {
    val user = Account(uid = "u1", handle = "h", name = "Bob", email = "b@example.com")

    composeTestRule.setContent {
      UserChip(user = user, account = user, onRemove = {}, showRemoveBTN = false)
    }

    composeTestRule.onNodeWithText("B").assertExists()
    composeTestRule.onAllNodesWithTag("remove:${user.name}").assertCountEquals(0)
  }

  @Test
  fun userChip_shows_remove_icon_for_admin() {
    val user = Account(uid = "u1", handle = "h", name = "Bob", email = "b@example.com")
    val adminUser = Account(uid = "u2", handle = "adm", name = "Admin", email = "a@x.com")

    composeTestRule.setContent {
      UserChip(user = user, account = adminUser, onRemove = {}, showRemoveBTN = true)
    }

    composeTestRule.onNodeWithTag("remove:${user.name}").assertExists()
  }

  @Test
  fun timeField_shows_and_hides_dialog_on_pick() {
    var picked: java.time.LocalTime? = null
    composeTestRule.setContent {
      TimeField(value = "10:00", onValueChange = { picked = it }, editable = true)
    }

    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(SessionTestTags.TIME_PICKER_OK_BUTTON).performClick()
    assert(picked != null)
  }

  @Test
  fun deleteSessionBTN_not_shown_for_non_admin() {
    composeTestRule.setContent {
      DeleteSessionBTN(
          sessionViewModel = sessionVM,
          currentUser = member,
          discussion = baseDiscussion,
          userIsAdmin = false,
          onback = {})
    }

    composeTestRule.onAllNodesWithTag(SessionTestTags.DELETE_SESSION_BUTTON).assertCountEquals(0)
  }
}
