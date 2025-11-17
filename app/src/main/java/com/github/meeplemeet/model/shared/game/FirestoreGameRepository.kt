package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.GameNotFoundException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** * Firestore collection path for storing and retrieving [Game] documents. */
const val GAMES_COLLECTION_PATH = "games"

/**
 * Implementation of [GameRepository] backed by Firestore.
 *
 * This repository retrieves and filters [Game] objects from the "gamesCollection" collection in
 * Firestore. Each document in Firestore represents a [GameNoUid], with its document ID used as the
 * [Game.uid].
 */
class FirestoreGameRepository(db: FirebaseFirestore = FirebaseProvider.db) : GameRepository {
  private val games = db.collection(GAMES_COLLECTION_PATH)

  /** Retrieves a single game by its Firestore document ID. */
  override suspend fun getGameById(gameID: String): Game {
    val snapshot = games.document(gameID).get().await()
    val game = snapshot.toObject(GameNoUid::class.java)
    return game?.let { fromNoUid(gameID, it) } ?: throw GameNotFoundException()
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

    val snapshot = games.get().await()
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
