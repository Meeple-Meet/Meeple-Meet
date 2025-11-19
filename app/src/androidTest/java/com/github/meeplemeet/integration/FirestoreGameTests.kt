package com.github.meeplemeet.integration

import com.github.meeplemeet.model.GameNotFoundException
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FirestoreGameTests : FirestoreTests() {
  @get:Rule val ck = Checkpoint.rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  @Before
  fun setup() {
    runBlocking {
      // Insert baseline games used by multiple tests
      addGameDoc("g_catan", "Catan", genres = listOf("1", "2"))
      addGameDoc("g_carcassonne", "Carcassonne", genres = listOf("2"))
      addGameDoc("g_chess", "Chess", genres = listOf("3"))
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      val snapshot = db.collection(GAMES_COLLECTION_PATH).get().await()
      val batch = db.batch()
      snapshot.documents.forEach { batch.delete(it.reference) }
      batch.commit().await()
    }
  }

  private fun addGameDoc(id: String, name: String, genres: List<String> = emptyList()) =
      runBlocking {
        db.collection(GAMES_COLLECTION_PATH)
            .document(id)
            .set(GameNoUid(name = name, genres = genres))
            .await()
      }

  @Test
  fun smoke_getGamesById_and_getGameById() = runTest {
    checkpoint("getGamesById returns multiple games") {
      runBlocking {
        val results = gameRepository.getGamesById("g_catan", "g_carcassonne")
        val names = results.map { it.name }
        assertTrue(names.contains("Catan"))
        assertTrue(names.contains("Carcassonne"))
        assertFalse(names.contains("Chess"))
      }
    }

    checkpoint("getGamesById returns empty list when ids missing") {
      runBlocking {
        val results = gameRepository.getGamesById("nonexistent1", "nonexistent2")
        assertTrue(results.isEmpty())
      }
    }

    checkpoint("getGameById returns expected game") {
      runBlocking {
        val game: Game = gameRepository.getGameById("g_catan")
        assertEquals("Catan", game.name)
        assertEquals("g_catan", game.uid)
      }
    }
  }

  @Test(expected = GameNotFoundException::class)
  fun getGameById_throws_when_missing() = runTest { gameRepository.getGameById("non-existent-id") }

  @Test(expected = IllegalArgumentException::class)
  fun getGamesById_throws_when_more_than_20_ids() = runTest {
    val ids = (1..21).map { "id_$it" }.toTypedArray()
    gameRepository.getGamesById(*ids)
  }

  @Test
  fun smoke_searchGamesByNameContains() = runTest {
    checkpoint("searchGamesByNameContains returns matching games ignoreCase true") {
      runBlocking {
        val results =
            gameRepository.searchGamesByNameContains(
                query = "cat", maxResults = 10, ignoreCase = true)
        val resultNames = results.map { it.name.lowercase() }
        assertTrue(resultNames.any { it.contains("cat") })
        assertTrue(resultNames.contains("catan"))
      }
    }

    checkpoint("searchGamesByNameContains respects maxResults and ranking") {
      runBlocking {
        addGameDoc("g_catan_jr", "Catan Junior")
        addGameDoc("g_concatenate", "Concatenate")
        val results =
            gameRepository.searchGamesByNameContains(
                query = "cat", maxResults = 2, ignoreCase = true)
        assertTrue(results.size <= 2)
        assertTrue(results.any { it.name == "Catan" } || results.any { it.name == "Catan Junior" })
      }
    }

    checkpoint("searchGamesByNameContains is empty for blank query") {
      runBlocking {
        val results =
            gameRepository.searchGamesByNameContains(query = "", maxResults = 10, ignoreCase = true)
        assertTrue(results.isEmpty())
      }
    }

    checkpoint("searchGamesByNameContains caseSensitive no match when case differs") {
      runBlocking {
        val resultsCaseSensitive =
            gameRepository.searchGamesByNameContains(
                query = "cat", maxResults = 10, ignoreCase = false)
        assertTrue(resultsCaseSensitive.none { it.name == "Catan" })
      }
    }

    checkpoint("searchGamesByNameContains prioritizes prefix and respects maxResults") {
      runBlocking {
        addGameDoc("g_catan_jr2", "Catan Junior", genres = emptyList())
        addGameDoc("g_concatenate2", "Concatenate", genres = emptyList())
        addGameDoc("g_catapult", "catapult", genres = emptyList())
        val results =
            gameRepository.searchGamesByNameContains(
                query = "cat", maxResults = 2, ignoreCase = true)
        assertTrue(results.size <= 2)
        val first = results.firstOrNull()
        assertNotNull(first)
        assertTrue(first!!.name.startsWith("cat", ignoreCase = true))
      }
    }

    checkpoint("searchGamesByNameContains ignoreCase variants") {
      runBlocking {
        addGameDoc("g_MyGameAllLower", "mygame", genres = emptyList())
        addGameDoc("g_MyGameCapital", "MyGame", genres = emptyList())
        val resIgnoreTrue =
            gameRepository.searchGamesByNameContains(
                query = "myg", maxResults = 10, ignoreCase = true)
        assertTrue(
            resIgnoreTrue.any {
              it.name.equals("mygame", ignoreCase = true) ||
                  it.name.equals("MyGame", ignoreCase = true)
            })
        val resIgnoreFalse =
            gameRepository.searchGamesByNameContains(
                query = "myg", maxResults = 10, ignoreCase = false)
        assertTrue(resIgnoreFalse.any { it.name == "mygame" })
        assertTrue(resIgnoreFalse.none { it.name == "MyGame" })
      }
    }
  }
}
