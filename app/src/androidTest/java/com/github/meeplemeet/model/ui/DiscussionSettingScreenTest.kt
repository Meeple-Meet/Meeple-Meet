package com.github.meeplemeet.model.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionSettingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val currentAccount = Account(uid = "user1", name = "Alice")
    private val vm: FirestoreViewModel = mockk(relaxed = true)
    private val discussion = Discussion(
        uid = "d1",
        name = "Test Discussion",
        description = "This is a test discussion",
        creatorId = "user1",
        admins = mutableListOf("user2"),
        participants = mutableListOf("user1", "user2", "user3")
    )

    private lateinit var accountFlow: MutableStateFlow<Account?>
    private lateinit var discussionFlow: MutableStateFlow<Discussion?>

    @Before
    fun setup() {
        accountFlow = MutableStateFlow(currentAccount)
        discussionFlow = MutableStateFlow(
            discussion.copy(admins = mutableListOf("user1", "user2"))
        )
        // Stub getOtherAccount to return user3
        every { vm.getOtherAccount("user3", any()) } answers {
            val callback = secondArg<(Account) -> Unit>()
            callback(Account(uid = "user3", name = "Charlie"))
        }
    }

    // --- Node helpers ---
    private fun backBtn() = compose.onNodeWithTag("back_button")
    private fun deleteBtn() = compose.onNodeWithText("Delete Discussion")
    private fun leaveBtn() = compose.onNodeWithText("Leave Discussion")
    private fun nameField() = compose.onNodeWithTag("discussion_name")
    private fun descField() = compose.onNodeWithTag("discussion_description")
    private fun memberRow(uid: String) = compose.onNodeWithTag("member_row_$uid")
    private fun makeAdminBtn() = compose.onNodeWithText("Make Admin")

    private fun setContent() {
        compose.setContent {
            DiscussionSettingScreen(
                viewModel = vm,
                discussionId = discussion.uid,
                accountFlowProvider = { accountFlow },
                discussionFlowProvider = { discussionFlow }
            )
        }
    }


    // --- Tests ---
    @Test
    fun displaysMainUIElements() {
        setContent()
        compose.onNodeWithText("Discussion Settings").assertIsDisplayed()
        compose.onNodeWithText("Description:").assertIsDisplayed()
        compose.onNodeWithText("Members:").assertIsDisplayed()
        deleteBtn().assertIsDisplayed()
        leaveBtn().assertIsDisplayed()
    }

    @Test
    fun editNameAndDescription_updatesFields() = runTest {
        setContent()
        val newName = "New Discussion Name"
        val newDesc = "Updated Description"

        nameField().performTextReplacement(newName)
        descField().performTextReplacement(newDesc)

        backBtn().performClick()
    }

    @Test
    fun deleteDiscussion_showsDialog() = runTest {
        setContent()
        deleteBtn().performClick()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun leaveDiscussion_showsDialog() = runTest {
        setContent()
        leaveBtn().performClick()
        compose.onNodeWithText("Leave").assertIsDisplayed()
    }

    @Test
    fun clickingMember_showsAdminOptions() = runTest {
        setContent()
        memberRow("user3").performClick()
        makeAdminBtn().assertIsDisplayed()
    }

    @Test
    fun deleteButton_disabled_forMemberOnly() {
        val nonAdminDiscussion = discussion.copy(creatorId = "user2", admins = mutableListOf("user2"))
        discussionFlow.value = nonAdminDiscussion

        setContent()
        deleteBtn().assertIsNotEnabled()
        leaveBtn().assertIsEnabled()
    }
}