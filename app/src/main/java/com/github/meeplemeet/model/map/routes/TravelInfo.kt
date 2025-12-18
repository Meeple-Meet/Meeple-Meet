package com.github.meeplemeet.model.map.routes

/**
 * Available modes of transportation for route calculation.
 *
 * Used to determine both approximate and real travel times.
 */
enum class TransportMode {
  /** Walking speed, typically ~5 km/h */
  WALKING,
  /** Bicycling speed, typically ~15 km/h */
  BICYCLING,
  /** Driving speed, typically ~50 km/h in urban areas */
  DRIVING
}

/**
 * Default speeds in meters per second used for approximate travel time calculation.
 *
 * These are used only for local estimation to avoid unnecessary API calls.
 */
object DefaultTravelSpeeds {
  const val WALKING = 5_000 / 3_600.0
  const val BICYCLING = 15_000 / 3_600.0
  const val DRIVING = 50_000 / 3_600.0
}

/**
 * Travel information between two geographic points.
 *
 * Can represent either an approximate value (local calculation using distance + default speed) or a
 * precise value retrieved from a routing API.
 *
 * @property distanceMeters Distance between origin and destination in meters.
 * @property durationSeconds Estimated duration of the trip in seconds.
 * @property isApproximate True if the travel info is approximate; false if retrieved from a routing
 *   API.
 * @property mode Mode of transportation used to compute this travel info.
 */
data class TravelInfo(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val isApproximate: Boolean,
    val mode: TransportMode
)
