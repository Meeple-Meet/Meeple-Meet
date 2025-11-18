package com.github.meeplemeet.integration

import com.github.meeplemeet.model.GameNotFoundException
import com.github.meeplemeet.model.shared.game.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FirestoreGameTests : FirestoreTests() {
  @Before
  fun setup() {
    // Clean collection and insert some baseline documents
    runBlocking {
      // Delete existing documents in the collection (defensive)
      val existing = db.collection(GAMES_COLLECTION_PATH).get().await()
      existing.documents.forEach { it.reference.delete().await() }

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
  fun getGameById_returns_expected_game() = runTest {
    val game: Game = gameRepository.getGameById("g_catan")
    assertEquals("Catan", game.name)
    assertEquals("g_catan", game.uid)
  }

  @Test(expected = GameNotFoundException::class)
  fun getGameById_throws_when_missing() = runTest { gameRepository.getGameById("non-existent-id") }

  @Test
  fun getGamesById_returns_multiple_games() = runTest {
    val results = gameRepository.getGamesById("g_catan", "g_carcassonne")
    val names = results.map { it.name }
    assertTrue(names.contains("Catan"))
    assertTrue(names.contains("Carcassonne"))
    assertFalse(names.contains("Chess"))
  }

  @Test
  fun getGamesById_returns_empty_list_when_ids_missing() = runTest {
    val results = gameRepository.getGamesById("nonexistent1", "nonexistent2")
    assertTrue(results.isEmpty())
  }

  @Test(expected = IllegalArgumentException::class)
  fun getGamesById_throws_when_more_than_20_ids() = runTest {
    val ids = (1..21).map { "id_$it" }.toTypedArray()
    gameRepository.getGamesById(*ids)
  }

  @Test
  fun searchGamesByNameContains_returns_matching_games_ignoreCase_true() = runTest {
    // baseline already has "Catan" and "Carcassonne"
    val results =
        gameRepository.searchGamesByNameContains(query = "cat", maxResults = 10, ignoreCase = true)
    val resultNames = results.map { it.name.lowercase() }
    assertTrue(resultNames.any { it.contains("cat") })
    assertTrue(resultNames.contains("catan"))
  }

  @Test
  fun searchGamesByNameContains_respects_maxResults_and_ranking() = runTest {
    // add more docs to test maxResults and ordering
    runBlocking {
      addGameDoc("g_catan_jr", "Catan Junior")
      addGameDoc("g_concatenate", "Concatenate")
    }

    val results =
        gameRepository.searchGamesByNameContains(query = "cat", maxResults = 2, ignoreCase = true)

    assertTrue(results.size <= 2)
    assertTrue(results.any { it.name == "Catan" } || results.any { it.name == "Catan Junior" })
  }

  @Test
  fun searchGamesByNameContains_is_empty_for_blank_query() = runTest {
    val results =
        gameRepository.searchGamesByNameContains(query = "", maxResults = 10, ignoreCase = true)
    assertTrue(results.isEmpty())
  }

  @Test
  fun searchGamesByNameContains_caseSensitive_noMatch_when_caseDiffers() = runTest {
    // baseline: "Catan" exists from setup
    val resultsCaseSensitive =
        gameRepository.searchGamesByNameContains(query = "cat", maxResults = 10, ignoreCase = false)
    // "Catan" starts with 'C' — case-sensitive 'cat' should NOT match "Catan"
    assertTrue(resultsCaseSensitive.none { it.name == "Catan" })
  }

  @Test
  fun searchGamesByNameContains_prioritizes_prefix_and_respects_maxResults() = runTest {
    // add docs: one that startsWith "cat", one that contains "cat" later, one unrelated
    runBlocking {
      addGameDoc("g_catan_jr", "Catan Junior", genres = emptyList())
      addGameDoc("g_concatenate", "Concatenate", genres = emptyList())
      addGameDoc("g_catapult", "catapult", genres = emptyList())
    }

    val results =
        gameRepository.searchGamesByNameContains(query = "cat", maxResults = 2, ignoreCase = true)

    // respect du maxResults
    assertTrue(results.size <= 2)

    val first = results.firstOrNull()
    assertNotNull(first)
    assertTrue(first!!.name.startsWith("cat", ignoreCase = true))
  }

  @Test
  fun searchGamesByNameContains_ignoreCase_variants() = runTest {
    runBlocking {
      addGameDoc("g_MyGameAllLower", "mygame", genres = emptyList())
      addGameDoc("g_MyGameCapital", "MyGame", genres = emptyList())
    }

    val resIgnoreTrue =
        gameRepository.searchGamesByNameContains(query = "myg", maxResults = 10, ignoreCase = true)
    assertTrue(
        resIgnoreTrue.any {
          it.name.equals("mygame", ignoreCase = true) || it.name.equals("MyGame", ignoreCase = true)
        })

    val resIgnoreFalse =
        gameRepository.searchGamesByNameContains(query = "myg", maxResults = 10, ignoreCase = false)
    // case-sensitive: "myg" should match "mygame" but not "MyGame"
    assertTrue(resIgnoreFalse.any { it.name == "mygame" })
    assertTrue(resIgnoreFalse.none { it.name == "MyGame" })
  }
}
