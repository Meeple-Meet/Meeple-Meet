package com.github.meeplemeet.utils

import com.github.meeplemeet.model.repositories.GameRepository
import com.github.meeplemeet.model.structures.Game

class FakeGameRepo() : GameRepository {
  var returnedGames: List<Game> = emptyList()
  var shouldThrow: Boolean = false
  var lastQuery: String? = null

  override suspend fun getGames(maxResults: Int): List<Game> {
    TODO("Not used in tests")
  }

  override suspend fun getGameById(gameID: String): Game {
    TODO("Not used in tests")
  }

  override suspend fun getGameByName(name: String): Game? {
    TODO("Not used in tests")
  }

  override suspend fun getGamesByGenre(genreID: Int, maxResults: Int): List<Game> {
    TODO("Not used in tests")
  }

  override suspend fun getGamesByGenres(genreIDs: List<Int>, maxResults: Int): List<Game> {
    TODO("Not used in tests")
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
