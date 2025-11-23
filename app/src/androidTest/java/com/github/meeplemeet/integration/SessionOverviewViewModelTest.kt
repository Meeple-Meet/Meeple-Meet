package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.sessions.SessionOverviewViewModel
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import junit.framework.TestCase.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test

/**
 * Integration test for SessionOverviewViewModel.
 *
 * Uses real Firestore repositories (no mocks) and verifies that sessionMapFlow emits the correct
 * live map of discussion-id → Session when sessions are created / updated / deleted.
 */
class SessionOverviewViewModelTest : FirestoreTests() {

  private lateinit var viewModel: SessionOverviewViewModel
  private lateinit var account: Account
  private lateinit var existingGameId: String
  private val testLocation = Location(46.5197, 6.5665, "EPFL")

  @Before
  fun setup() = runBlocking {
    viewModel = SessionOverviewViewModel()
    account =
        accountRepository.createAccount(
            "sessionTestUser", "Session Tester", "session@test.com", photoUrl = null)
    addGameDoc("g_chess", "Chess", genres = listOf("1", "2"))
    existingGameId = "g_chess"
  }

  @Test
  fun sessionMapFlow_emitsEmptyMap_whenNoSessions() = runBlocking {
    val map = viewModel.sessionMapFlow(account.uid).first()
    assertTrue(map.isEmpty())
  }

  @Test
  fun sessionMapFlow_emitsMapWithNewSession() = runBlocking {
    // create a discussion + session
    val discussion = discussionRepository.createDiscussion("Game Night", "Let's play", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    sessionRepository.createSession(
        discussion.uid, "Chess Night", game.uid, Timestamp.now(), testLocation, account.uid)

    delay(100) // wait for Firestore snapshot

    val map = viewModel.sessionMapFlow(account.uid).first()
    assertEquals(1, map.size)
    assertEquals("Chess Night", map[discussion.uid]?.name)
  }

  @Test
  fun sessionMapFlow_removesEntry_whenSessionDeleted() = runBlocking {
    // create
    val discussion =
        discussionRepository.createDiscussion("To Be Deleted", "Will disappear", account.uid)
    val game = gameRepository.getGameById(existingGameId)

    sessionRepository.createSession(
        discussion.uid, "Delete Me", game.uid, Timestamp.now(), testLocation, account.uid)
    delay(100)
    assertEquals(1, viewModel.sessionMapFlow(account.uid).first().size)

    // delete
    sessionRepository.deleteSession(discussion.uid)
    delay(100)

    val map = viewModel.sessionMapFlow(account.uid).first()
    assertTrue(map.isEmpty())
  }

  @Test
  fun getGameNameByGameId_returnsName_whenGameExists() = runBlocking {
    val name = viewModel.getGameNameByGameId(existingGameId)
    assertEquals("Chess", name)
  }

  @Test
  fun getGameNameByGameId_returnsNull_whenGameMissing() = runBlocking {
    val name = viewModel.getGameNameByGameId("nonexistent")
    assertNull(name)
  }

  private fun addGameDoc(id: String, name: String, genres: List<String> = emptyList()) =
      runBlocking {
        db.collection(GAMES_COLLECTION_PATH)
            .document(id)
            .set(GameNoUid(name = name, genres = genres))
            .await()
      }
}
