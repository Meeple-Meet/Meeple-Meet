package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.GameFetchException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
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
    return game?.let { fromNoUid(gameID, it) } ?: throw GameFetchException("")
  }

  /**
   * Retrieves multiple [Game] objects by their unique identifiers.
   *
   * This method allows batch fetching of games by their document IDs. The number of IDs provided
   * must not exceed 20. If more than 20 IDs are passed, an [IllegalArgumentException] is thrown. If
   * some of the provided IDs do not correspond to existing documents, they are simply omitted from
   * the result list.
   *
   * @param gameIDs the unique IDs of the games to retrieve (maximum 20).
   * @return a [List] of [Game] objects corresponding to the provided IDs. The list may contain
   *   fewer items than requested if some IDs are invalid or not found.
   * @throws IllegalArgumentException if more than 20 IDs are provided.
   */
  override suspend fun getGamesById(vararg gameIDs: String): List<Game> {
    if (gameIDs.isEmpty()) return emptyList()
    require(gameIDs.size <= 20) { "A maximum of 20 IDs can be requested at once." }

    val chunks = gameIDs.toList().chunked(10)
    val results = mutableListOf<Game>()

    for (chunk in chunks) {
      val snapshot = games.whereIn(FieldPath.documentId(), chunk).get().await()
      results.addAll(mapSnapshotToGames(snapshot.documents))
    }

    return results
  }

  /**
   * Searches for games whose names contain the specified [query].
   *
   * This search is performed locally after fetching all games. Results are limited to [maxResults].
   */
  override suspend fun searchGamesByName(query: String, maxResults: Int): List<GameSearchResult> {
    if (query.isBlank()) return emptyList()

    val snapshot = games.get().await()
    val allGames = mapSnapshotToGames(snapshot.documents)
    val filteredGames = allGames.filter { it.name.contains(query, ignoreCase = true) }

    // Sort results to prioritize names starting with the query
    val sortedGames = filteredGames.sortedBy { !it.name.startsWith(query, true) }

    return sortedGames.take(maxResults).map { GameSearchResult(it.uid, it.name) }
  }

  /** Converts a Firestore query result into a list of [Game] objects. */
  private fun mapSnapshotToGames(documents: List<DocumentSnapshot>): List<Game> {
    return documents.mapNotNull { doc ->
      doc.toObject(GameNoUid::class.java)?.let { fromNoUid(doc.id, it) }
    }
  }
}
