// ChatGPT-5 Extended Thinking generated tests code following ideas and combinations
// given as entry. It also corrected errors and improved code efficiency.
package com.github.meeplemeet.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.sessions.CreateSessionViewModel
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.ui.components.ComponentsTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.sessions.*
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.google.firebase.Timestamp
import io.mockk.*
import java.lang.reflect.Method
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class CreateSessionScreenTest {

    @get:Rule val compose = createComposeRule()

    // Repos / VMs
    private lateinit var discussionRepository: DiscussionRepository
    private lateinit var viewModel: DiscussionViewModel
    private lateinit var sessionRepo: SessionRepository
    private lateinit var fakeGameRepo: FakeGameRepository
    private lateinit var sessionVM: SessionViewModel
    private lateinit var createSessionVM: TestCreateSessionViewModel

    // Test data
    private val me = Account(uid = "user1", handle = "", name = "Marco", email = "marco@epfl.ch")
    private val alex = Account(uid = "user2", handle = "", name = "Alexandre", email = "alex@epfl.ch")
    private val dany = Account(uid = "user3", handle = "", name = "Dany", email = "dany@epfl.ch")
    private val discussionId = "discussion1"

    private lateinit var injectedDiscussionFlow: MutableStateFlow<Discussion?>
    private lateinit var baseDiscussion: Discussion

    // Short wait util
    private fun waitForAtLeastOne(
        matcher: SemanticsMatcher,
        timeoutMs: Long = 1_000L,
        useUnmergedTree: Boolean = false
    ) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Node helpers
    private fun titleInput() = compose.onNodeWithTag(SessionCreationTestTags.FORM_TITLE_FIELD)

    private fun createBtn() = compose.onNodeWithTag(SessionCreationTestTags.CREATE_BUTTON)

    private fun discardBtn() = compose.onNodeWithTag(SessionCreationTestTags.DISCARD_BUTTON)

    private fun backBtn() = compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON)

    // Game/Location come from reusable components
    private fun allInputs() = compose.onAllNodes(hasSetTextAction())

    private fun gameInput() = allInputs()[1]

    private fun locationInput() = allInputs()[2]

    private fun clearMatcher() = hasContentDescription("Clear")

    private fun setContent(
        discussion: Discussion = baseDiscussion,
        onBack: () -> Unit = {},
        vm: CreateSessionViewModel = createSessionVM
    ) {
        compose.setContent {
            AppTheme {
                CreateSessionScreen(account = me, discussion = discussion, viewModel = vm, onBack = onBack)
            }
        }
    }

    private class FakeGameRepository : GameRepository {
        var throwOnSearch: Boolean = false

        override suspend fun getGames(maxResults: Int) = emptyList<Game>()

        override suspend fun getGameById(gameID: String): Game {
            throw RuntimeException("not used")
        }

        override suspend fun getGamesByGenre(genreID: Int, maxResults: Int) = emptyList<Game>()

        override suspend fun getGamesByGenres(genreIDs: List<Int>, maxResults: Int) = emptyList<Game>()

        override suspend fun searchGamesByNameContains(
            query: String,
            maxResults: Int,
            ignoreCase: Boolean
        ): List<Game> {
            if (throwOnSearch) throw RuntimeException("boom")
            return emptyList()
        }
    }

    private inner class TestCreateSessionViewModel(
        sessionRepository: SessionRepository,
        gameRepository: GameRepository
    ) : CreateSessionViewModel(sessionRepository, gameRepository) {
        // Delegate getAccounts to the mocked DiscussionViewModel
        override fun getAccounts(uids: List<String>, onResult: (List<Account>) -> Unit) {
            viewModel.getAccounts(uids, onResult)
        }
    }

    @Before
    fun setUp() {
        discussionRepository = mockk(relaxed = true)
        viewModel = spyk(DiscussionViewModel(discussionRepository))

        baseDiscussion =
            Discussion(
                uid = discussionId,
                name = "Board Night",
                description = "",
                messages = emptyList(),
                participants = listOf(me.uid, alex.uid, dany.uid),
                admins = listOf(me.uid),
                creatorId = me.uid)

        val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
        discussionFlowsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = discussionFlowsField.get(viewModel) as MutableMap<String, StateFlow<Discussion?>>
        injectedDiscussionFlow = MutableStateFlow(baseDiscussion)
        map[discussionId] = injectedDiscussionFlow

        every { viewModel.getAccounts(any(), any()) } answers
                {
                    val disc = firstArg<List<String>>()
                    val cb = secondArg<(List<Account>) -> Unit>()
                    val accounts =
                        disc.distinct().mapNotNull { uid ->
                            when (uid) {
                                me.uid -> me
                                alex.uid -> alex
                                dany.uid -> dany
                                "u4" -> Account(uid = "u4", handle = "newb", name = "Newbie", email = "n@x")
                                else -> null
                            }
                        }
                    cb(accounts)
                }

        sessionRepo = mockk(relaxed = true)
        fakeGameRepo = FakeGameRepository()
        sessionVM = SessionViewModel(sessionRepository = sessionRepo)
        createSessionVM = TestCreateSessionViewModel(sessionRepo, fakeGameRepo)
    }

    // Grouped UI: bars, snackbar, back/discard
    @Test
    fun ui_chrome_present_and_nav_discard_callbacks_fire() {
        val backCount = AtomicInteger(0)
        setContent(onBack = { backCount.incrementAndGet() })

        compose.onNodeWithTag(ComponentsTestTags.TOP_APP_BAR).assertExists()
        compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
        compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertExists()
        compose.onNodeWithTag(SessionCreationTestTags.SNACKBAR_HOST).assertExists()
        compose.onNodeWithTag(SessionCreationTestTags.CONTENT_COLUMN).assertExists()

        // Back button callback
        backBtn().performClick()
        compose.waitUntil(1_000) { backCount.get() == 1 }

        // Discard also calls onBack
        discardBtn().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(1_000) { backCount.get() == 2 }
    }

    // Participants
    @Test
    fun participants_visibility_and_self_protection_and_add_remove() {
        setContent()
        compose.waitForIdle()

        // Initial candidates show two non-me names
        compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
        compose.onAllNodesWithText("Dany").assertCountEquals(1)

        // Self is present once and not removable by tap
        compose.onNodeWithText("Marco").assertIsDisplayed().performClick()
        compose.onNodeWithText("Marco").assertIsDisplayed()
        compose.onAllNodesWithText("Alexandre").assertCountEquals(1)

        // Add then remove keeps a single visible copy
        compose.onAllNodesWithText("Alexandre").onLast().performClick()
        compose.runOnIdle {}
        compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
        compose.onAllNodesWithText("Alexandre").onFirst().performClick()
        compose.runOnIdle {}
        compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
    }

    @Test
    fun participants_variations_via_flow_updates() {
        lateinit var updateDiscussion: (Discussion) -> Unit

        compose.setContent {
            AppTheme {
                var disc by remember { mutableStateOf(baseDiscussion) }
                updateDiscussion = { newDisc -> disc = newDisc }
                CreateSessionScreen(
                    account = me, discussion = disc, viewModel = createSessionVM, onBack = {})
            }
        }

        // Only me -> no candidates
        compose.runOnUiThread {
            updateDiscussion(baseDiscussion.copy(uid = "disc_only_me", participants = listOf(me.uid)))
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Alexandre").assertCountEquals(0)
        compose.onAllNodesWithText("Dany").assertCountEquals(0)

        compose.runOnUiThread {
            updateDiscussion(
                baseDiscussion.copy(
                    uid = "disc_dups",
                    participants = listOf(me.uid, alex.uid, alex.uid, dany.uid, alex.uid)))
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Alexandre").assertCountEquals(1)

        // Empty list -> no candidates, but me still present
        compose.runOnUiThread {
            updateDiscussion(baseDiscussion.copy(uid = "disc_empty", participants = emptyList()))
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Alexandre").assertCountEquals(0)
        compose.onAllNodesWithText("Dany").assertCountEquals(0)
        compose.onAllNodesWithText("Marco").assertCountEquals(1)
    }

    // Create button
    @Test
    fun create_button_is_disabled_until_valid_conditions() {
        // Start with minimal participants so the range check is deterministic
        injectedDiscussionFlow.value = baseDiscussion.copy(participants = listOf(me.uid))
        setContent()

        titleInput().performTextInput("Friday Night Board Game Jam")
        gameInput().performTextInput("Root")
        locationInput().performTextInput("Table A1")

        // No date/time yet -> disabled
        createBtn().assertIsNotEnabled()
    }

    // Game search
    @Test
    fun game_search_clear_and_error() {
        setContent()
        compose.waitForIdle()

        // Trigger repo error to test error display
        fakeGameRepo.throwOnSearch = true
        gameInput().performTextInput("Catan")
        // Wait for debounce (500ms) + execution + rendering
        waitForAtLeastOne(hasTestTag(SessionCreationTestTags.GAME_SEARCH_ERROR), timeoutMs = 2_000L)
        compose.onNodeWithTag(SessionCreationTestTags.GAME_SEARCH_ERROR).assertExists()
    }

    // Component-level button
    @Test
    fun create_and_discard_button_components_behave() {
        val createClicked = AtomicBoolean(false)
        val discardClicked = AtomicBoolean(false)

        compose.setContent {
            AppTheme {
                Column {
                    CreateSessionButton(
                        formToSubmit = SessionForm(title = "t"),
                        enabled = true,
                        onCreate = { createClicked.set(true) })
                    DiscardButton(onDiscard = { discardClicked.set(true) })
                }
            }
        }

        compose.onNodeWithTag(SessionCreationTestTags.CREATE_BUTTON).assertIsEnabled().performClick()
        compose.onNodeWithTag(SessionCreationTestTags.DISCARD_BUTTON).assertIsEnabled().performClick()

        compose.waitUntil(1_000) { createClicked.get() && discardClicked.get() }
    }

    // Organisation section
    @Test
    fun organisation_section_shows_location_label_and_search_field() {
        compose.setContent {
            AppTheme {
                OrganisationSection(
                    date = null,
                    time = null,
                    onDateChange = {},
                    onTimeChange = {},
                    account = me,
                    discussion = baseDiscussion,
                    onLocationPicked = {},
                    gameUi = GameUIState(),
                    viewModel = createSessionVM)
            }
        }
        compose.onAllNodesWithText("Location").onFirst().assertExists()
    }

    // Repo/VM interactions
    @Test
    fun initial_load_fetches_participants_once() {
        setContent()
        compose.waitForIdle()
        verify(exactly = 1) { viewModel.getAccounts(any(), any()) }
    }

    // Timestamp conversions
    companion object {
        private lateinit var toTs: Method
        private lateinit var toTsDefault: Method

        @JvmStatic
        @BeforeClass
        fun cacheReflection() {
            val cls = Class.forName("com.github.meeplemeet.ui.sessions.CreateSessionScreenKt")
            toTs =
                cls.getDeclaredMethod(
                    "toTimestamp", LocalDate::class.java, LocalTime::class.java, ZoneId::class.java)
                    .apply { isAccessible = true }
            toTsDefault =
                cls.getDeclaredMethod(
                    "toTimestamp\$default",
                    LocalDate::class.java,
                    LocalTime::class.java,
                    ZoneId::class.java,
                    Int::class.javaPrimitiveType,
                    Any::class.java)
                    .apply { isAccessible = true }
        }
    }

    @Test
    fun toTimestamp_conversions_and_defaults() {
        // explicit zone matches epoch millis
        val date1 = LocalDate.of(2025, 1, 2)
        val time1 = LocalTime.of(13, 45, 30, 123_000_000)
        val zoneZurich = ZoneId.of("Europe/Zurich")
        val expectedMillis = date1.atTime(time1).atZone(zoneZurich).toInstant().toEpochMilli()
        val ts = toTs.invoke(null, date1, time1, zoneZurich) as Timestamp
        assert(ts.toDate().time == expectedMillis)

        // default zone equals explicit system default
        val date2 = LocalDate.of(2025, 5, 6)
        val time2 = LocalTime.of(9, 10, 0, 0)
        val sys = ZoneId.systemDefault()
        val tsExplicit = toTs.invoke(null, date2, time2, sys) as Timestamp
        val maskForZoneDefault = 1 shl 2
        val tsDefault =
            toTsDefault.invoke(null, date2, time2, null, maskForZoneDefault, null) as Timestamp
        assert(tsExplicit.toDate().time == tsDefault.toDate().time)

        // returns "now" when date or time is null
        val before = System.currentTimeMillis()
        val ts1 = toTs.invoke(null, null, null, ZoneId.of("UTC")) as Timestamp
        val ts2 = toTs.invoke(null, null, LocalTime.of(12, 0), ZoneId.of("UTC")) as Timestamp
        val ts3 = toTs.invoke(null, null, null, ZoneId.systemDefault()) as Timestamp
        val after = System.currentTimeMillis()
        val window = (before - 10_000)..(after + 10_000)
        assert(ts1.toDate().time in window)
        assert(ts2.toDate().time in window)
        assert(ts3.toDate().time in window)

        // roundtrip consistency across zones
        val date3 = LocalDate.of(2025, 10, 15)
        val time3 = LocalTime.of(8, 30, 45, 0)
        listOf("Europe/Zurich", "Asia/Tokyo", "America/New_York").map(ZoneId::of).forEach { z ->
            val expected = ZonedDateTime.of(date3, time3, z).toInstant().toEpochMilli()
            val got = (toTs.invoke(null, date3, time3, z) as Timestamp).toDate().time
            assert(got == expected)
        }
    }
}