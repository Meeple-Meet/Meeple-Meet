package com.github.meeplemeet.utils

import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.sessions.GameRepository

class FakeGameRepo() : GameRepository {
  var returnedGames: List<Game> = emptyList()
  var shouldThrow: Boolean = false
  var lastQuery: String? = null
  var returnedGame: Game? = null

  override suspend fun getGames(maxResults: Int): List<Game> {
    throw NotImplementedError("FakeGameRepo: unused method in this test context")
  }

  override suspend fun getGameById(gameID: String): Game {
    if (shouldThrow) throw RuntimeException("boom")
    return returnedGame ?: throw NoSuchElementException("No game found")
  }

  override suspend fun getGamesByGenre(genreID: Int, maxResults: Int): List<Game> {
    throw NotImplementedError("FakeGameRepo: unused method in this test context")
  }

  override suspend fun getGamesByGenres(genreIDs: List<Int>, maxResults: Int): List<Game> {
    throw NotImplementedError("FakeGameRepo: unused method in this test context")
  }

  override suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int,
      ignoreCase: Boolean
  ): List<Game> {
    lastQuery = query
    if (shouldThrow) throw RuntimeException("boom")
    return returnedGames
  }
}
