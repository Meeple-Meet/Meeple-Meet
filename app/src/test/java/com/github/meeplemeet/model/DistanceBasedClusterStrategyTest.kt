package com.github.meeplemeet.model

import com.github.meeplemeet.model.map.cluster.ClusterItem
import com.github.meeplemeet.model.map.cluster.DistanceBasedClusterStrategy
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlin.test.Test

class DistanceBasedClusterStrategyTest {

  /**
   * Utility to move items by dx, dy expressed in KM. 1° latitude ~= 111 km → used for synthetic
   * test points.
   */
  private fun km(dx: Double, dy: Double) = ClusterItem(lat = dx / 111.0, lng = dy / 111.0)

  @Test
  fun emptyInputList() {
    val strategy = DistanceBasedClusterStrategy()
    val result = strategy.clusterize(emptyList(), 5f)
    assertTrue(result.isEmpty())
  }

  @Test
  fun singleItemCluster() {
    val strategy = DistanceBasedClusterStrategy()
    val item = km(0.0, 0.0)
    val result = strategy.clusterize(listOf(item), 0f)

    assertEquals(1, result.size)
    assertEquals(1, result[0].items.size)
    assertEquals(item, result[0].items.first())
  }

  @Test
  fun twoItemsFarApartProduceTwoClusters() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 1.0)

    val a = km(0.0, 0.0)
    val b = km(10.0, 0.0) // 10 km apart

    val result = strategy.clusterize(listOf(a, b), 0f)
    assertEquals(2, result.size)
  }

  @Test
  fun twoItemsCloseProduceOneCluster() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 1.0)

    val a = km(0.0, 0.0)
    val b = km(0.5, 0.0) // ~0.5 km

    val result = strategy.clusterize(listOf(a, b), 0f)
    assertEquals(1, result.size)
    assertEquals(2, result[0].items.size)
  }

  @Test
  fun centroidIsAverage() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 5.0)

    // Two points close enough to be clustered
    val a = km(0.0, 0.0) // origin
    val b = km(2.0, 2.0) // 2 km east and 2 km north

    val result = strategy.clusterize(listOf(a, b), 0f)
    val cluster = result.first()

    // The cluster center should be the average of lat/lng
    val expectedLat = (a.lat + b.lat) / 2
    val expectedLng = (a.lng + b.lng) / 2

    assertEquals(expectedLat, cluster.centerLat, 1e-6)
    assertEquals(expectedLng, cluster.centerLng, 1e-6)
  }

  @Test
  fun identicalItemsProduceZeroDistance() {
    val strategy = DistanceBasedClusterStrategy()

    val a = ClusterItem(46.5191, 6.6323)
    val b = ClusterItem(46.5191, 6.6323)

    val result = strategy.clusterize(listOf(a, b), 0f)

    assertEquals(1, result.size)
    assertEquals(2, result[0].items.size)
  }

  @Test
  fun negativeCoordinatesAreHandled() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 2.0)

    val a = ClusterItem(-10.0, -20.0)
    val b = ClusterItem(-10.01, -20.01)

    val result = strategy.clusterize(listOf(a, b), 0f)

    assertEquals(1, result.size)
  }

  @Test
  fun clusteringIsOrderIndependent() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 1.0)

    val items = listOf(km(0.0, 0.0), km(0.5, 0.0), km(1.0, 0.0))

    val normal = strategy.clusterize(items, 0f)
    val reversed = strategy.clusterize(items.reversed(), 0f)

    assertEquals(normal.size, reversed.size)
    assertEquals(normal[0].items.size, reversed[0].items.size)
  }

  /**
   * Test the default zoomToThreshold indirectly: We feed two points whose distance is known, and
   * check at which zoom they cluster or separate.
   */
  @Test
  fun zoomLowersThresholdExponentially() {
    val a = km(0.0, 0.0)
    val b = km(7.9, 0.0) // slightly below 8 km

    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 16.0)

    // Zoom 0 → threshold = 16 km → they cluster
    val clusterZoom0 = strategy.clusterize(listOf(a, b), 0f)
    assertEquals(1, clusterZoom0.size)

    // Zoom 5 → threshold = 8 km → still cluster
    val clusterZoom5 = strategy.clusterize(listOf(a, b), 5f)
    assertEquals(1, clusterZoom5.size)

    // Zoom 10 → threshold = 4 km → they split
    val clusterZoom10 = strategy.clusterize(listOf(a, b), 10f)
    assertEquals(2, clusterZoom10.size)
  }

  @Test
  fun customZoomThresholdIsUsed() {
    var called = false

    val strategy =
        DistanceBasedClusterStrategy(
            baseThresholdKm = 1.0,
            zoomToThreshold = {
              called = true
              123.0
            })

    strategy.clusterize(listOf(km(0.0, 0.0)), 42f)
    assertTrue(called)
  }

  @Test
  fun zoomEffectMakesClustersSmallerAtHighZoom() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 10.0)

    val close = km(0.0, 0.0)
    val medium = km(5.0, 0.0) // 5 km
    val far = km(20.0, 0.0) // 20 km

    val items = listOf(close, medium, far)

    val lowZoom = strategy.clusterize(items, 0f)
    val highZoom = strategy.clusterize(items, 10f)

    assertEquals(2, lowZoom.size) // close+medium, then far
    assertEquals(3, highZoom.size) // all separate
  }

  @Test
  fun manyItemsRandomScatter() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 1.0)

    val items =
        List(20) { i ->
          km(i.toDouble(), i.toDouble()) // spread diagonally
        }

    val result = strategy.clusterize(items, 0f)

    assertEquals(20, result.size)
  }

  @Test
  fun thresholdBoundaryIncluded() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 1.0)

    val a = km(0.0, 0.0)
    val b = km(0.9, 0.0) // slightly below 1 km

    val result = strategy.clusterize(listOf(a, b), 0f)

    assertEquals(1, result.size) // <= threshold → same cluster
  }

  @Test
  fun manyRandomItemsProduceManyClusters() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 1.0)

    // 200 items spaced by 1 km → none should merge
    val items = List(200) { i -> km(i.toDouble(), 0.0) }

    val result = strategy.clusterize(items, 0f)
    assertEquals(200, result.size)
  }

  @Test
  fun largeDenseCloudProducesSingleCluster() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 0.5) // 500m

    // 300 points inside an area of approximately 200m
    val items =
        List(300) { i ->
          val dx = (i % 20) * 0.01 // ~10m steps
          val dy = (i / 20) * 0.01
          km(dx, dy)
        }

    val result = strategy.clusterize(items, 0f)

    assertEquals(1, result.size)
    assertEquals(300, result[0].items.size)
  }

  @Test
  fun twoLargeGroupsFormTwoClusters() {
    val strategy = DistanceBasedClusterStrategy(baseThresholdKm = 1.0)

    // First dense group
    val groupA = List(100) { km(it * 0.005, 0.0) } // ~0.5 km span

    // Second dense group located 10 km further
    val groupB = List(100) { km(10.0 + it * 0.005, 0.0) }

    val items = groupA + groupB

    val result = strategy.clusterize(items, 0f)

    assertEquals(2, result.size)

    // Each cluster must contain exactly 100 items
    assertTrue(result.any { it.items.size == 100 })
    assertTrue(result.any { it.items.size == 100 })
  }
}
