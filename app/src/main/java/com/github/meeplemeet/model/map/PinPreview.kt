package com.github.meeplemeet.model.map

/**
 * Represents a lightweight preview of a MapPin.
 *
 * Used to display contextual information on the map screen (e.g. bottom sheet, popup) without
 * loading the full entity. Each subtype corresponds to a specific PinType.
 * - ShopPreview: basic info about a shop, including open status
 * - SessionPreview: summary of a game session, including title, game name, location, and date
 * - SpacePreview: basic info about a space renter, including open status
 */
sealed class PinPreview {

  data class ShopPreview(val name: String, val address: String, val open: Boolean) : PinPreview()

  data class SessionPreview(
      val title: String,
      val game: String,
      val address: String,
      val date: String
  ) : PinPreview()

  data class SpacePreview(val name: String, val address: String, val open: Boolean) : PinPreview()
}
