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
      addGameDoc("g_catan", "Catan", genres = listOf(1, 2))
      addGameDoc("g_carcassonne", "Carcassonne", genres = listOf(2))
      addGameDoc("g_chess", "Chess", genres = listOf(3))
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

  private fun addGameDoc(id: String, name: String, genres: List<Int> = emptyList()) = runBlocking {
    db.collection(GAMES_COLLECTION_PATH)
        .document(id)
        .set(GameNoUid(name = name, genres = genres))
        .await()
  }

  @Test
  fun smoke_getGames_and_getById() = runTest {
    checkpoint("getGames returns up to maxResults") {
      runBlocking {
        for (i in 1..5) {
          addGameDoc("g_extra_$i", "ExtraGame$i", genres = listOf(1))
        }
        val results = gameRepository.getGames(maxResults = 3)
        assertEquals(3, results.size)
      }
    }

    checkpoint("getGames returns all baseline games") {
      runBlocking {
        val results = gameRepository.getGames(maxResults = 10)
        assertTrue(results.size >= 3)
        assertTrue(results.any { it.name == "Catan" })
        assertTrue(results.any { it.name == "Carcassonne" })
        assertTrue(results.any { it.name == "Chess" })
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

  @Test
  fun smoke_getGamesByGenre() = runTest {
    checkpoint("getGamesByGenre returns only games containing genre") {
      runBlocking {
        val results = gameRepository.getGamesByGenre(2, maxResults = 10)
        val names = results.map { it.name }
        assertTrue(names.contains("Catan"))
        assertTrue(names.contains("Carcassonne"))
        assertFalse(names.contains("Chess"))
      }
    }

    checkpoint("getGamesByGenre respects maxResults") {
      runBlocking {
        for (i in 1..5) {
          addGameDoc("g_gen_99_$i", "Genre99Game$i", genres = listOf(99))
        }
        val results = gameRepository.getGamesByGenre(99, maxResults = 2)
        assertEquals(2, results.size)
        assertTrue(results.all { 99 in it.genres })
      }
    }

    checkpoint("getGamesByGenres returns only games containing all genres") {
      runBlocking {
        addGameDoc("g_complex", "ComplexGame", genres = listOf(1, 2, 3))
        addGameDoc("g_partial", "PartialGame", genres = listOf(1, 2))
        addGameDoc("g_other", "OtherGame", genres = listOf(2, 3))
        val results = gameRepository.getGamesByGenres(listOf(1, 2), maxResults = 10)
        val names = results.map { it.name }
        assertTrue(names.contains("ComplexGame"))
        assertTrue(names.contains("PartialGame"))
        assertFalse(names.contains("OtherGame"))
      }
    }

    checkpoint("getGamesByGenres respects maxResults and intersection") {
      runBlocking {
        addGameDoc("g_both_1", "BothOne", genres = listOf(10, 20))
        addGameDoc("g_both_2", "BothTwo", genres = listOf(10, 20))
        addGameDoc("g_both_3", "BothThree", genres = listOf(10, 20))
        addGameDoc("g_partial", "Only10", genres = listOf(10))
        addGameDoc("g_other", "Only20", genres = listOf(20))
        val results = gameRepository.getGamesByGenres(listOf(10, 20), maxResults = 2)
        assertEquals(2, results.size)
        assertTrue(results.all { game -> listOf(10, 20).all { it in game.genres } })
      }
    }
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
