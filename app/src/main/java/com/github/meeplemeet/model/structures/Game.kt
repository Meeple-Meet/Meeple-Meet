package com.github.meeplemeet.model.structures

data class Game(
  val name: String,
  val description: String,
  val rules: String,
  val minPlayers: UInt,
  val maxPlayers: UInt,
)