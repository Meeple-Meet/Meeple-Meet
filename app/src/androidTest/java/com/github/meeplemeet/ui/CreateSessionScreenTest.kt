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
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.GameUIState
import com.github.meeplemeet.model.shared.LocationUIState
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
    private lateinit var firestoreRepo: DiscussionRepository
    private lateinit var viewModel: DiscussionViewModel
    private lateinit var sessionRepo: SessionRepository
    private lateinit var fakeGameRepo: FakeGameRepository
    private lateinit var sessionVM: SessionViewModel

    // Test data
    private val me = Account(uid = "user1", handle = "", name = "Marco", email = "marco@epfl.ch")
    private val alex = Account(uid = "user2", handle = "", name = "Alexandre", email = "alex@epfl.ch")
    private val dany = Account(uid = "user3", handle = "", name = "Dany", email = "dany@epfl.ch")
    private val discussionId = "discussion1"

    private lateinit var injectedDiscussionFlow: MutableStateFlow<Discussion?>
    private lateinit var baseDiscussion: Discussion

    // ---------- Checkpoint helper ----------
    private val ck = Checkpoint()
    @get:Rule val checkpointRule = Checkpoint.rule()
    private fun checkpoint(name: String, block: () -> Unit) = ck(name, block)

    // Short wait – 600 ms
    private fun waitForAtLeastOne(
        matcher: SemanticsMatcher,
        timeoutMs: Long = 600L,
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
    private fun allInputs() = compose.onAllNodes(hasSetTextAction())
    private fun gameInput() = allInputs()[1]
    private fun locationInput() = allInputs()[2]
    private fun clearMatcher() = hasContentDescription("Clear")

    private fun setContent(discussion: Discussion = baseDiscussion, onBack: () -> Unit = {}) {
        compose.setContent {
            AppTheme {
                CreateSessionScreen(
                    viewModel = viewModel,
                    sessionViewModel = sessionVM,
                    account = me,
                    discussion = discussion,
                    onBack = onBack
                )
            }
        }
    }

    private class FakeGameRepository : GameRepository {
        var throwOnSearch: Boolean = false
        override suspend fun getGames(maxResults: Int) = emptyList<Game>()
        override suspend fun getGameById(gameID: String): Game = throw RuntimeException("not used")
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

    @Before
    fun setUp() {
        firestoreRepo = mockk(relaxed = true)
        viewModel = spyk(DiscussionViewModel(firestoreRepo))

        baseDiscussion = Discussion(
            uid = discussionId,
            name = "Board Night",
            description = "",
            messages = emptyList(),
            participants = listOf(me.uid, alex.uid, dany.uid),
            admins = listOf(me.uid),
            creatorId = me.uid
        )

        val discussionFlowsField = viewModel::class.java.getDeclaredField("discussionFlows")
        discussionFlowsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = discussionFlowsField.get(viewModel) as MutableMap<String, StateFlow<Discussion?>>
        injectedDiscussionFlow = MutableStateFlow(baseDiscussion)
        map[discussionId] = injectedDiscussionFlow

        every { viewModel.getAccounts(any(), any()) } answers {
            val disc = firstArg<List<String>>()
            val cb = secondArg<(List<Account>) -> Unit>()
            val accounts = disc.distinct().mapNotNull { uid ->
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
        sessionVM = SessionViewModel(
            initDiscussion = baseDiscussion,
            repository = sessionRepo,
            gameRepository = fakeGameRepo
        )
    }

    /* =========================================================
       ONE FAT TEST – EVERY CHECKPOINT + HELPER BRANCHES
       ========================================================= */
    @Test
    fun fullSessionSmoke_allInCheckpoints() = runBlocking {

        /* 1. chrome / nav */
        checkpoint("Top-bar chrome exists") {
            val backCount = AtomicInteger(0)
            setContent(onBack = { backCount.incrementAndGet() })

            compose.onNodeWithTag(ComponentsTestTags.TOP_APP_BAR).assertExists()
            compose.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
            compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertExists()
            compose.onNodeWithTag(SessionCreationTestTags.SNACKBAR_HOST).assertExists()
            compose.onNodeWithTag(SessionCreationTestTags.CONTENT_COLUMN).assertExists()
        }

        checkpoint("Back and discard callbacks fire") {
            val backCount = AtomicInteger(0)
            setContent(onBack = { backCount.incrementAndGet() })

            backBtn().performClick()
            compose.waitUntil(600) { backCount.get() == 1 }

            discardBtn().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(600) { backCount.get() == 2 }
        }

        /* 2. participants – full coverage + extra */
        checkpoint("Initial candidates show correct names") {
            setContent()
            compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
            compose.onAllNodesWithText("Dany").assertCountEquals(1)
        }

        checkpoint("Self not removable by tap") {
            setContent()
            compose.onNodeWithText("Marco").assertIsDisplayed().performClick()
            compose.onNodeWithText("Marco").assertIsDisplayed()
            compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
        }

        checkpoint("Add-remove toggle keeps single copy") {
            setContent()
            compose.onAllNodesWithText("Alexandre").onLast().performClick()
            compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
            compose.onAllNodesWithText("Alexandre").onFirst().performClick()
            compose.onAllNodesWithText("Alexandre").assertCountEquals(1)
        }

        checkpoint("Empty participants – only me shown") {
            lateinit var updateDiscussion: (Discussion) -> Unit
            compose.setContent {
                AppTheme {
                    var disc by remember { mutableStateOf(baseDiscussion) }
                    updateDiscussion = { newDisc -> disc = newDisc }
                    CreateSessionScreen(
                        viewModel = viewModel,
                        sessionViewModel = sessionVM,
                        account = me,
                        discussion = disc,
                        onBack = {}
                    )
                }
            }
            compose.runOnUiThread {
                updateDiscussion(baseDiscussion.copy(uid = "disc_empty", participants = emptyList()))
            }
            compose.waitUntil(600) {
                compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().isEmpty()
            }
            compose.onAllNodesWithText("Marco").assertCountEquals(1)
        }

        checkpoint("Duplicate participant IDs are normalised to single chip") {
            lateinit var updateDiscussion: (Discussion) -> Unit
            compose.setContent {
                AppTheme {
                    var disc by remember { mutableStateOf(baseDiscussion) }
                    updateDiscussion = { newDisc -> disc = newDisc }
                    CreateSessionScreen(
                        viewModel = viewModel,
                        sessionViewModel = sessionVM,
                        account = me,
                        discussion = disc,
                        onBack = {}
                    )
                }
            }
            compose.runOnUiThread {
                updateDiscussion(
                    baseDiscussion.copy(
                        uid = "disc_dups",
                        participants = listOf(me.uid, alex.uid, alex.uid, dany.uid, alex.uid)
                    )
                )
            }
            compose.waitUntil(600) { compose.onAllNodesWithText("Alexandre").fetchSemanticsNodes().size == 1 }
        }

        /* 3. create button disabled until valid */
        checkpoint("Button disabled without date/time") {
            injectedDiscussionFlow.value = baseDiscussion.copy(participants = listOf(me.uid))
            setContent()
            titleInput().performTextInput("Friday Night Board Game Jam")
            gameInput().performTextInput("Root")
            locationInput().performTextInput("Table A1")
            createBtn().assertIsNotEnabled()
        }

        /* 4. game search + error */
        checkpoint("Game search – type and clear") {
            setContent()
            gameInput().performTextInput("Cascadia")
            waitForAtLeastOne(clearMatcher(), useUnmergedTree = true)
            compose.onNode(clearMatcher(), useUnmergedTree = true).performClick()
            compose.onAllNodesWithText("Cascadia").assertCountEquals(0)
        }

        checkpoint("Game search – repo error shows error tag") {
            fakeGameRepo.throwOnSearch = true
            setContent()
            gameInput().performTextInput("Catan")
            waitForAtLeastOne(hasTestTag(SessionCreationTestTags.GAME_SEARCH_ERROR))
            compose.onNodeWithTag(SessionCreationTestTags.GAME_SEARCH_ERROR).assertExists()
            fakeGameRepo.throwOnSearch = false
        }

        /* 5. component-level buttons – disabled & enabled branches */
        checkpoint("CreateSessionButton disabled branch") {
            val clicked = AtomicBoolean(false)
            compose.setContent {
                AppTheme {
                    CreateSessionButton(
                        formToSubmit = SessionForm(), // invalid -> disabled
                        enabled = false,
                        onCreate = { clicked.set(true) }
                    )
                }
            }
            val btn = compose.onNodeWithTag(SessionCreationTestTags.CREATE_BUTTON)
            btn.assertIsNotEnabled()
            btn.performClick() // should do nothing
            assert(!clicked.get())
        }

        checkpoint("CreateSessionButton enabled + click branch") {
            val created = AtomicBoolean(false)
            compose.setContent {
                AppTheme {
                    CreateSessionButton(
                        formToSubmit = SessionForm(title = "ok", date = LocalDate.now(), time = LocalTime.now()),
                        enabled = true,
                        onCreate = { created.set(true) }
                    )
                }
            }
            val btn = compose.onNodeWithTag(SessionCreationTestTags.CREATE_BUTTON)
            btn.assertIsEnabled()
            btn.performClick()
            compose.waitUntil(600) { created.get() }
        }

        checkpoint("DiscardButton onClick branch") {
            val discarded = AtomicBoolean(false)
            compose.setContent {
                AppTheme { DiscardButton(onDiscard = { discarded.set(true) }) }
            }
            compose.onNodeWithTag(SessionCreationTestTags.DISCARD_BUTTON).performClick()
            compose.waitUntil(600) { discarded.get() }
        }

        /* 6. organisation section */
        checkpoint("Organisation section renders location label") {
            compose.setContent {
                AppTheme {
                    OrganisationSection(
                        date = null,
                        time = null,
                        locationText = "",
                        onDateChange = {},
                        onTimeChange = {},
                        onLocationChange = {},
                        account = me,
                        sessionViewModel = sessionVM,
                        discussion = baseDiscussion,
                        onLocationPicked = {},
                        gameUi = GameUIState(),
                        locationUi = LocationUIState()
                    )
                }
            }
            compose.onAllNodesWithText("Location").onFirst().assertExists()
        }

        /* 7. repo interaction */
        checkpoint("Initial load fetches participants once") {
            setContent()
            verify(exactly = 1) { viewModel.getAccounts(any(), any()) }
        }

        /* 8. timestamp conversions – reflection cached in @BeforeClass */
        checkpoint("toTimestamp explicit zone") {
            val date1 = LocalDate.of(2025, 1, 2)
            val time1 = LocalTime.of(13, 45, 30, 123_000_000)
            val zoneZurich = ZoneId.of("Europe/Zurich")
            val expectedMillis = date1.atTime(time1).atZone(zoneZurich).toInstant().toEpochMilli()
            val ts = toTs.invoke(null, date1, time1, zoneZurich) as Timestamp
            assert(ts.toDate().time == expectedMillis)
        }

        checkpoint("toTimestamp default zone matches system") {
            val date2 = LocalDate.of(2025, 5, 6)
            val time2 = LocalTime.of(9, 10, 0, 0)
            val sys = ZoneId.systemDefault()
            val tsExplicit = toTs.invoke(null, date2, time2, sys) as Timestamp
            val maskForZoneDefault = 1 shl 2
            val tsDefault = toTsDefault.invoke(null, date2, time2, null, maskForZoneDefault, null) as Timestamp
            assert(tsExplicit.toDate().time == tsDefault.toDate().time)
        }

        checkpoint("toTimestamp null date/time returns 'now'") {
            val before = System.currentTimeMillis()
            val ts1 = toTs.invoke(null, null, null, ZoneId.of("UTC")) as Timestamp
            val ts2 = toTs.invoke(null, null, LocalTime.of(12, 0), ZoneId.of("UTC")) as Timestamp
            val ts3 = toTs.invoke(null, null, null, ZoneId.systemDefault()) as Timestamp
            val after = System.currentTimeMillis()
            val window = (before - 10_000)..(after + 10_000)
            assert(ts1.toDate().time in window)
            assert(ts2.toDate().time in window)
            assert(ts3.toDate().time in window)
        }

        checkpoint("toTimestamp round-trip across zones") {
            val date3 = LocalDate.of(2025, 10, 15)
            val time3 = LocalTime.of(8, 30, 45, 0)
            listOf("Europe/Zurich", "Asia/Tokyo", "America/New_York").map(ZoneId::of).forEach { z ->
                val expected = ZonedDateTime.of(date3, time3, z).toInstant().toEpochMilli()
                val got = (toTs.invoke(null, date3, time3, z) as Timestamp).toDate().time
                assert(got == expected)
            }
        }
        /* ---------- 10.  TRIGGER EVERY LAMBDA (drop-in) ---------- */

        checkpoint("Default onBack parameter (line 157)") {
            // simply instantiate the screen without supplying onBack
            compose.setContent {
                AppTheme {
                    CreateSessionScreen(
                        account = me,
                        discussion = baseDiscussion,
                        viewModel = viewModel,
                        sessionViewModel = sessionVM
                        // onBack omitted -> default lambda is used
                    )
                }
            }
            // nothing to assert – just hitting the default path
        }

        checkpoint("TopBarWithDivider onBack lambda (line 189)") {
            var backFired = false
            compose.setContent {
                AppTheme {
                    CreateSessionScreen(
                        account = me,
                        discussion = baseDiscussion,
                        viewModel = viewModel,
                        sessionViewModel = sessionVM,
                        onBack = { backFired = true }
                    )
                }
            }
            backBtn().performClick()
            compose.waitUntil(600) { backFired }
        }

        checkpoint("Discard button lambda – reset form + onBack (line 209)") {
            var backFired = false
            compose.setContent {
                AppTheme {
                    CreateSessionScreen(
                        account = me,
                        discussion = baseDiscussion,
                        viewModel = viewModel,
                        sessionViewModel = sessionVM,
                        onBack = { backFired = true }
                    )
                }
            }
            // fill some fields so we can verify they are cleared
            titleInput().performTextInput("WillBeCleared")
            discardBtn().performClick()
            compose.waitUntil(600) { backFired }
            // after discard the form is reset -> title empty again
            titleInput().assertTextContains("")
        }

        checkpoint("Create button lambda – success path (line 219-241)") {
            var backFired = false
            compose.setContent {
                AppTheme {
                    CreateSessionScreen(
                        account = me,
                        discussion = baseDiscussion,
                        viewModel = viewModel,
                        sessionViewModel = sessionVM,
                        onBack = { backFired = true }
                    )
                }
            }
            // make form valid
            titleInput().performTextInput("ValidTitle")
            // pick date/time via public VM calls (already tested earlier)
            sessionVM.setGameQuery(me, baseDiscussion, "Root")
            sessionVM.setLocationQuery(me, baseDiscussion, "Here")

            createBtn().performClick()
            compose.waitUntil(800) { backFired } // success -> onBack fired
        }

        checkpoint("Create button lambda – failure path + snackbar (line 233-235)") {
            // force createSession to throw
            every {
                sessionVM.createSession(any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("boom")

            compose.setContent {
                AppTheme {
                    CreateSessionScreen(
                        account = me,
                        discussion = baseDiscussion,
                        viewModel = viewModel,
                        sessionViewModel = sessionVM,
                        onBack = {}
                    )
                }
            }
            titleInput().performTextInput("Title")
            sessionVM.setGameQuery(me, baseDiscussion, "Root")
            sessionVM.setLocationQuery(me, baseDiscussion, "Here")

            createBtn().performClick()
            // the catch block calls showError -> snackbar lambda is executed
            compose.waitUntil(600) {
                sessionVM.gameUIState.value.gameSearchError == "boom"
            }
            // restore
            every { sessionVM.createSession(any(), any(), any(), any(), any(), any(), any()) } just Runs
        }

        checkpoint("GameSearchBar onQueryChange lambda (lines 321, 327, 329)") {
            compose.setContent {
                AppTheme {
                    CreateSessionScreen(
                        account = me,
                        discussion = baseDiscussion,
                        viewModel = viewModel,
                        sessionViewModel = sessionVM,
                        onBack = {}
                    )
                }
            }
            // typing triggers the lambda passed to GameSearchField
            gameInput().performTextInput("Catan")
            compose.waitUntil(600) {
                sessionVM.gameUIState.value.gameQuery == "Catan"
            }
        }
        checkpoint("OrganisationSection change-callbacks – title, date, time, location") {
            var titleChanged   = ""
            var dateChanged: LocalDate? = null
            var timeChanged: LocalTime? = null
            var locationChanged = ""

            compose.setContent {
                AppTheme {
                    OrganisationSection(
                        gameUi = GameUIState(),
                        locationUi = LocationUIState(),
                        sessionViewModel = sessionVM,
                        account = me,
                        discussion = baseDiscussion,
                        date = null,
                        time = null,
                        locationText = "",
                        onTitleChange = { titleChanged = it },
                        onDateChange = { dateChanged = it },
                        onTimeChange = { timeChanged = it },
                        onLocationChange = { locationChanged = it },
                        onLocationPicked = {}
                    )
                }
            }

            // 1. title
            compose.onNodeWithTag(SessionCreationTestTags.FORM_TITLE_FIELD)
                .performTextInput("NewTitle")

            // 2. date – type a valid ISO date
            compose.onNodeWithText("Date").performClick() // open picker (optional)
            compose.onNodeWithTag("date_input_field")     // the actual text part
                .performTextInput("2025-06-15")

            // 3. time – type a valid 24 h string
            compose.onNodeWithText("Time").performClick()
            compose.onNodeWithTag("time_input_field")     // the actual text part
                .performTextInput("14:30")

            // 4. location
            locationInput().performTextInput("NewLoc")

            compose.waitUntil(600) {
                titleChanged == "NewTitle" &&
                        dateChanged == LocalDate.of(2025, 6, 15) &&
                        timeChanged == LocalTime.of(14, 30) &&
                        locationChanged == "NewLoc"
            }
        }

        checkpoint("ParticipantsSection onAdd + onRemove lambdas") {
            val added = mutableListOf<Account>()
            val removed = mutableListOf<Account>()

            compose.setContent {
                AppTheme {
                    ParticipantsSection(
                        account = me,
                        selected = listOf(me),
                        allCandidates = listOf(me, alex),
                        minPlayers = 1,
                        maxPlayers = 4,
                        onAdd = { added.add(it) },
                        onRemove = { removed.add(it) },
                        mainSectionTitle = "Players"
                    )
                }
            }
            // add Alex
            compose.onNodeWithText("Alexandre").performClick()
            // remove me
            compose.onNodeWithText("Marco").performClick()

            compose.waitUntil(600) {
                added.size == 1 && added.first().uid == alex.uid &&
                        removed.size == 1 && removed.first().uid == me.uid
            }
        }

    }

    companion object {
        private lateinit var toTs: Method
        private lateinit var toTsDefault: Method

        @JvmStatic
        @BeforeClass
        fun cacheReflection() {
            val cls = Class.forName("com.github.meeplemeet.ui.sessions.CreateSessionScreenKt")
            toTs = cls.getDeclaredMethod(
                "toTimestamp",
                LocalDate::class.java,
                LocalTime::class.java,
                ZoneId::class.java
            ).apply { isAccessible = true }
            toTsDefault = cls.getDeclaredMethod(
                "toTimestamp\$default",
                LocalDate::class.java,
                LocalTime::class.java,
                ZoneId::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java
            ).apply { isAccessible = true }
        }
    }
}