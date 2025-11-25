package com.github.meeplemeet.model.map.cluster

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Strategy interface for clustering a list of items on a map. Implementations define the clustering
 * algorithm.
 *
 * The strategy operates on [ClusterItem]s and returns clusters that group items of the same type.
 */
fun interface ClusterStrategy {

  /**
   * Groups the given items into clusters based on the strategy.
   *
   * @param items The list of items to cluster.
   * @param zoomLevel The current map zoom level. Can be used to adjust clustering density.
   * @return A list of [Cluster]s containing the input items.
   */
  fun clusterize(items: List<ClusterItem>, zoomLevel: Float): List<Cluster<ClusterItem>>
}

private const val DEFAULT_THRESHOLD_KM = 1.0

/**
 * A simple distance-based clustering strategy.
 *
 * Items closer than a threshold (adjusted for zoom) are grouped into clusters.
 *
 * @property baseThresholdKm Base distance threshold in kilometers.
 * @property zoomToThreshold Function to compute the effective threshold based on zoom.
 */
class DistanceBasedClusterStrategy(
    private val baseThresholdKm: Double = DEFAULT_THRESHOLD_KM,
    private val zoomToThreshold: (Float) -> Double = { zoom ->
      baseThresholdKm / 2.0.pow(zoom / 5.0)
    }
) : ClusterStrategy {

  /**
   * Clusters the given items based on distance and zoom level.
   *
   * @param items The list of items to cluster.
   * @param zoomLevel Current map zoom level.
   * @return List of [Cluster]s containing the clustered [ClusterItem]s.
   */
  override fun clusterize(items: List<ClusterItem>, zoomLevel: Float): List<Cluster<ClusterItem>> {
    val threshold = zoomToThreshold(zoomLevel)
    val clusters = mutableListOf<Cluster<ClusterItem>>()
    val remaining = items.toMutableList()

    while (remaining.isNotEmpty()) {
      val base = remaining.removeFirst()
      val clusterItems = mutableListOf(base)

      val iterator = remaining.iterator()
      while (iterator.hasNext()) {
        val other = iterator.next()
        if (distanceKm(base, other) <= threshold) {
          clusterItems.add(other)
          iterator.remove()
        }
      }

      val centerLat = clusterItems.map { it.lat }.average()
      val centerLng = clusterItems.map { it.lng }.average()
      clusters.add(Cluster(centerLat, centerLng, clusterItems))
    }

    return clusters
  }

  /**
   * Computes the distance in kilometers between two geographical points.
   *
   * Uses the Haversine formula to calculate great-circle distance.
   *
   * @param a First point
   * @param b Second point
   * @return Distance in kilometers
   */
  private fun distanceKm(a: ClusterItem, b: ClusterItem): Double {
    val r = 6371.0 // Earth radius
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)

    val haversine = sin(dLat / 2).pow(2.0) + sin(dLng / 2).pow(2.0) * cos(lat1) * cos(lat2)
    return 2 * r * asin(sqrt(haversine))
  }
}
