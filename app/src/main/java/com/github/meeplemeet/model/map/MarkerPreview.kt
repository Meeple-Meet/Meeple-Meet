package com.github.meeplemeet.model.map

/**
 * Lightweight UI model displayed when a user taps on a map marker.
 *
 * Used to display contextual information on the map screen (e.g. bottom sheet, popup) without
 * loading the full entity. Each subtype corresponds to a specific PinType.
 * - ShopPreview: basic info about a shop, including open status
 * - SessionPreview: summary of a game session, including title, game name, location, and date
 * - SpacePreview: basic info about a space renter, including open status
 */
sealed class MarkerPreview(open val name: String, open val address: String) {

  data class ShopMarkerPreview(
      override val name: String,
      override val address: String,
      val open: Boolean
  ) : MarkerPreview(name, address)

  data class SessionMarkerPreview(
      val title: String,
      override val address: String,
      val game: String,
      val date: String
  ) : MarkerPreview(title, address)

  data class SpaceMarkerPreview(
      override val name: String,
      override val address: String,
      val open: Boolean
  ) : MarkerPreview(name, address)
}
