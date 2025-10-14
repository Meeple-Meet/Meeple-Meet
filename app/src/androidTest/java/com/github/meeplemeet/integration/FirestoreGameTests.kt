package com.github.meeplemeet.integration

import com.github.meeplemeet.model.GameNotFoundException
import com.github.meeplemeet.model.repositories.FirestoreGameRepository
import com.github.meeplemeet.model.repositories.GAMES_COLLECTION_PATH
import com.github.meeplemeet.model.structures.Game
import com.github.meeplemeet.model.structures.GameNoUid
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FirestoreGameTests : FirestoreTests() {
  private lateinit var repository: FirestoreGameRepository

  @Before
  fun setup() {
    repository = FirestoreGameRepository(db)

    // Clean collection and insert some baseline documents
    runBlocking {
      // Delete existing documents in the collection (defensive)
      val existing = db.collection(GAMES_COLLECTION_PATH).get().await()
      existing.documents.forEach { it.reference.delete().await() }

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
  fun getGames_returns_up_to_maxResults() = runTest {
    runBlocking {
      for (i in 1..5) {
        addGameDoc("g_extra_$i", "ExtraGame$i", genres = listOf(1))
      }
    }

    val results = repository.getGames(maxResults = 3)
    assertEquals(3, results.size)
  }

  @Test
  fun getGames_returns_all_games_without_exception() = runTest {
    val results = repository.getGames(maxResults = 10)

    assertTrue(results.size >= 3)
    assertTrue(results.any { it.name == "Catan" })
    assertTrue(results.any { it.name == "Carcassonne" })
    assertTrue(results.any { it.name == "Chess" })
  }

  @Test
  fun getGameById_returns_expected_game() = runTest {
    val game: Game = repository.getGameById("g_catan")
    assertEquals("Catan", game.name)
    assertEquals("g_catan", game.uid)
  }

  @Test(expected = GameNotFoundException::class)
  fun getGameById_throws_when_missing() = runTest { repository.getGameById("non-existent-id") }

  @Test
  fun getGameByName_returns_first_matching_game() = runTest {
    val result = repository.getGameByName("Carcassonne")
    assertNotNull(result)
    assertEquals("Carcassonne", result!!.name)
  }

  @Test
  fun getGameByName_returns_null_when_none() = runTest {
    val result = repository.getGameByName("DoesNotExist")
    assertNull(result)
  }

  @Test
  fun getGamesByGenre_returns_only_games_containing_genre() = runTest {
    val results = repository.getGamesByGenre(2, maxResults = 10)
    val names = results.map { it.name }
    assertTrue(names.contains("Catan"))
    assertTrue(names.contains("Carcassonne"))
    assertFalse(names.contains("Chess"))
  }

  @Test
  fun getGamesByGenre_respects_maxResults() = runTest {
    // insert 5 games that contain genre 99
    runBlocking {
      for (i in 1..5) {
        addGameDoc("g_gen_99_$i", "Genre99Game$i", genres = listOf(99))
      }
    }

    val results = repository.getGamesByGenre(99, maxResults = 2)
    assertEquals(2, results.size)
    // ensure all results contain the genre
    assertTrue(results.all { 99 in it.genres })
  }

  @Test
  fun getGamesByGenres_returns_only_games_containing_all_genres() = runTest {
    // add a document that has genres 1 and 2
    runBlocking {
      addGameDoc("g_complex", "ComplexGame", genres = listOf(1, 2, 3))
      addGameDoc("g_partial", "PartialGame", genres = listOf(1, 2))
      addGameDoc("g_other", "OtherGame", genres = listOf(2, 3))
    }

    val results = repository.getGamesByGenres(listOf(1, 2), maxResults = 10)
    val names = results.map { it.name }
    assertTrue(names.contains("ComplexGame"))
    assertTrue(names.contains("PartialGame"))
    assertFalse(names.contains("OtherGame"))
  }

  @Test
  fun getGamesByGenres_respects_maxResults_and_intersection() = runTest {
    // create several games with genres [10,20], some extra ones that don't match fully
    runBlocking {
      addGameDoc("g_both_1", "BothOne", genres = listOf(10, 20))
      addGameDoc("g_both_2", "BothTwo", genres = listOf(10, 20))
      addGameDoc("g_both_3", "BothThree", genres = listOf(10, 20))
      addGameDoc("g_partial", "Only10", genres = listOf(10))
      addGameDoc("g_other", "Only20", genres = listOf(20))
    }

    val results = repository.getGamesByGenres(listOf(10, 20), maxResults = 2)
    // limited by maxResults
    assertEquals(2, results.size)
    // all returned games must contain both genres
    assertTrue(results.all { game -> listOf(10, 20).all { it in game.genres } })
  }

  @Test
  fun searchGamesByNameContains_returns_matching_games_ignoreCase_true() = runTest {
    // baseline already has "Catan" and "Carcassonne"
    val results =
        repository.searchGamesByNameContains(query = "cat", maxResults = 10, ignoreCase = true)
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
        repository.searchGamesByNameContains(query = "cat", maxResults = 2, ignoreCase = true)

    assertTrue(results.size <= 2)
    assertTrue(results.any { it.name == "Catan" } || results.any { it.name == "Catan Junior" })
  }

  @Test
  fun searchGamesByNameContains_is_empty_for_blank_query() = runTest {
    val results =
        repository.searchGamesByNameContains(query = "", maxResults = 10, ignoreCase = true)
    assertTrue(results.isEmpty())
  }

  @Test
  fun searchGamesByNameContains_caseSensitive_noMatch_when_caseDiffers() = runTest {
    // baseline: "Catan" exists from setup
    val resultsCaseSensitive =
        repository.searchGamesByNameContains(query = "cat", maxResults = 10, ignoreCase = false)
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
        repository.searchGamesByNameContains(query = "cat", maxResults = 2, ignoreCase = true)

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
        repository.searchGamesByNameContains(query = "myg", maxResults = 10, ignoreCase = true)
    assertTrue(
        resIgnoreTrue.any {
          it.name.equals("mygame", ignoreCase = true) || it.name.equals("MyGame", ignoreCase = true)
        })

    val resIgnoreFalse =
        repository.searchGamesByNameContains(query = "myg", maxResults = 10, ignoreCase = false)
    // case-sensitive: "myg" should match "mygame" but not "MyGame"
    assertTrue(resIgnoreFalse.any { it.name == "mygame" })
    assertTrue(resIgnoreFalse.none { it.name == "MyGame" })
  }
}
