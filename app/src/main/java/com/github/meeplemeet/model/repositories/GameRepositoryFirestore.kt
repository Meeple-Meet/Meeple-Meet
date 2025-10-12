package com.github.meeplemeet.model.systems

import com.github.meeplemeet.model.GameNotFoundException
import com.github.meeplemeet.model.structures.Game
import com.github.meeplemeet.model.structures.GameNoUid
import com.github.meeplemeet.model.structures.fromNoUid
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** * Firestore collection path for storing and retrieving [Game] documents. */
const val GAMES_COLLECTION_PATH = "gamesCollection"

/**
 * Implementation of [GameRepository] backed by Firestore.
 *
 * This repository retrieves and filters [Game] objects from the "gamesCollection" collection in
 * Firestore. Each document in Firestore represents a [GameNoUid], with its document ID used as the
 * [Game.uid].
 */
class GameRepositoryFirestore(db: FirebaseFirestore) : GameRepository {

  private val gamesCollection = db.collection(GAMES_COLLECTION_PATH)

  /** Retrieves all games (within [maxResults]) from Firestore. */
  override suspend fun getGames(maxResults: Int): List<Game> {
    val snapshot = gamesCollection.get().await()
    return mapSnapshotToGames(snapshot.documents)
  }

  /** Retrieves a single game by its Firestore document ID. */
  override suspend fun getGameById(gameID: String): Game {
    val snapshot = gamesCollection.document(gameID).get().await()
    val game = snapshot.toObject(GameNoUid::class.java)
    return game?.let { fromNoUid(gameID, it) } ?: throw GameNotFoundException("Game not found")
  }

  /**
   * Retrieves a single game by its exact name.
   *
   * This performs a case-sensitive search in Firestore.
   */
  override suspend fun getGameByName(name: String): Game? {
    val query = gamesCollection.whereEqualTo("name", name).get().await()
    val results = mapSnapshotToGames(query.documents)
    return results.firstOrNull()
  }

  /**
   * Retrieves all games (within [maxResults]) that include a given genre ID.
   *
   * This uses Firestore's `array-contains` query operator.
   */
  override suspend fun getGamesByGenre(genreID: Int, maxResults: Int): List<Game> {
    val query =
        gamesCollection
            .whereArrayContains("genres", genreID)
            .limit(maxResults.toLong())
            .get()
            .await()
    return mapSnapshotToGames(query.documents)
  }

  /**
   * Retrieves all games (within [maxResults]) that include **all** of the specified genre IDs.
   *
   * Since Firestore doesn't support multiple `array-contains` filters simultaneously, this method
   * performs the intersection manually on the client side.
   */
  override suspend fun getGamesByGenres(genreIDs: List<Int>, maxResults: Int): List<Game> {
    if (genreIDs.isEmpty()) return emptyList()

    // Fetch all games that match at least one of the genres (no multiple array-contains allowed on
    // Firestore)
    val firstGenre = genreIDs.first()
    val initialQuery = gamesCollection.whereArrayContains("genres", firstGenre).get().await()
    val initialGames = mapSnapshotToGames(initialQuery.documents)

    // Filter locally to include only games that have all the specified genres
    return initialGames.filter { game -> genreIDs.all { it in game.genres } }.take(maxResults)
  }

  /**
   * Searches for games whose names contain the specified [query].
   *
   * This search is performed locally after fetching all games, and supports optional
   * case-insensitive matching. Results are limited to [maxResults].
   */
  override suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int,
      ignoreCase: Boolean
  ): List<Game> {
    if (query.isBlank()) return emptyList()

    val snapshot = gamesCollection.get().await()
    val allGames = mapSnapshotToGames(snapshot.documents)

    // Filter games based on whether their names contain the query
    val filteredGames =
        if (ignoreCase) {
          allGames.filter { it.name.contains(query, ignoreCase = true) }
        } else {
          allGames.filter { it.name.contains(query) }
        }

    // Sort results to prioritize names starting with the query
    val sortedGames = filteredGames.sortedBy { !it.name.startsWith(query, ignoreCase) }

    return sortedGames.take(maxResults)
  }

  /** Converts a Firestore query result into a list of [Game] objects. */
  private fun mapSnapshotToGames(documents: List<DocumentSnapshot>): List<Game> {
    return documents.mapNotNull { doc ->
      doc.toObject(GameNoUid::class.java)?.let { fromNoUid(doc.id, it) }
    }
  }
}
