package com.github.meeplemeet.model

import com.github.meeplemeet.model.map.cluster.ClusterItem
import com.github.meeplemeet.model.map.cluster.ClusterThresholds
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

  // ============================================================================
  // BASIC CLUSTERING TESTS
  // ============================================================================

  @Test
  fun emptyInputList() {
    val strategy = DistanceBasedClusterStrategy()
    val result = strategy.clusterize(emptyList(), 14f)
    assertTrue(result.isEmpty())
  }

  @Test
  fun singleItemCluster() {
    val strategy = DistanceBasedClusterStrategy()
    val item = km(0.0, 0.0)
    val result = strategy.clusterize(listOf(item), 14f)

    assertEquals(1, result.size)
    assertEquals(1, result[0].items.size)
    assertEquals(item, result[0].items.first())
  }

  @Test
  fun twoItemsFarApartProduceTwoClusters() {
    // Use a fixed threshold of 1km via custom lambda
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 1.0 })

    val a = km(0.0, 0.0)
    val b = km(10.0, 0.0) // 10 km apart

    val result = strategy.clusterize(listOf(a, b), 14f)
    assertEquals(2, result.size)
  }

  @Test
  fun twoItemsCloseProduceOneCluster() {
    // Fixed threshold 1km
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 1.0 })

    val a = km(0.0, 0.0)
    val b = km(0.5, 0.0) // ~0.5 km

    val result = strategy.clusterize(listOf(a, b), 14f)
    assertEquals(1, result.size)
    assertEquals(2, result[0].items.size)
  }

  @Test
  fun centroidIsAverage() {
    // Fixed threshold 5km
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 5.0 })

    val a = km(0.0, 0.0) // origin
    val b = km(2.0, 2.0) // 2 km east and 2 km north

    val result = strategy.clusterize(listOf(a, b), 14f)
    val cluster = result.first()

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

    val result = strategy.clusterize(listOf(a, b), 14f)

    assertEquals(1, result.size)
    assertEquals(2, result[0].items.size)
  }

  @Test
  fun negativeCoordinatesAreHandled() {
    // Fixed threshold 2km
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 2.0 })

    val a = ClusterItem(-10.0, -20.0)
    val b = ClusterItem(-10.01, -20.01)

    val result = strategy.clusterize(listOf(a, b), 14f)

    assertEquals(1, result.size)
  }

  @Test
  fun clusteringIsOrderIndependent() {
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 1.0 })

    val items = listOf(km(0.0, 0.0), km(0.5, 0.0), km(1.0, 0.0))

    val normal = strategy.clusterize(items, 14f)
    val reversed = strategy.clusterize(items.reversed(), 14f)

    assertEquals(normal.size, reversed.size)
    assertEquals(normal[0].items.size, reversed[0].items.size)
  }

  @Test
  fun thresholdBoundaryIncluded() {
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 1.0 })

    val a = km(0.0, 0.0)
    val b = km(0.9, 0.0) // slightly below 1 km

    val result = strategy.clusterize(listOf(a, b), 14f)

    assertEquals(1, result.size) // <= threshold → same cluster
  }

  // ============================================================================
  // ZOOM-DEPENDENT CLUSTERING TESTS
  // ============================================================================

  @Test
  fun defaultStrategy_lowerZoomCreatesLargerClusters() {
    val strategy = DistanceBasedClusterStrategy() // Uses default piecewiseExponentialScaling

    val a = km(0.0, 0.0)
    val b = km(3.0, 0.0) // 3 km apart

    // Low zoom (10) → large threshold → cluster together
    val lowZoom = strategy.clusterize(listOf(a, b), 10f)
    assertEquals(1, lowZoom.size)

    // High zoom (18) → small threshold → separate clusters
    val highZoom = strategy.clusterize(listOf(a, b), 18f)
    assertEquals(2, highZoom.size)
  }

  @Test
  fun customZoomThresholdIsUsed() {
    var capturedZoom = 0f

    val strategy =
        DistanceBasedClusterStrategy(
            zoomToThreshold = { zoom ->
              capturedZoom = zoom
              100.0 // Very large threshold
            })

    strategy.clusterize(listOf(km(0.0, 0.0)), 42f)
    assertEquals(42f, capturedZoom)
  }

  @Test
  fun zoomEffectMakesClustersSmallerAtHighZoom() {
    val strategy = DistanceBasedClusterStrategy() // Default strategy

    val close = km(0.0, 0.0)
    val medium = km(2.0, 0.0) // 2 km
    val far = km(8.0, 0.0) // 8 km

    val items = listOf(close, medium, far)

    // Low zoom → large threshold → fewer clusters
    val lowZoom = strategy.clusterize(items, 10f)
    assertTrue(lowZoom.size < 3)

    // High zoom → small threshold → more clusters
    val highZoom = strategy.clusterize(items, 18f)
    assertEquals(3, highZoom.size)
  }

  // ============================================================================
  // LARGE-SCALE CLUSTERING TESTS
  // ============================================================================

  @Test
  fun manyItemsRandomScatter() {
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 1.0 })

    val items = List(20) { i -> km(i.toDouble(), i.toDouble()) } // spread diagonally

    val result = strategy.clusterize(items, 14f)

    // Each point is ~1.41 km from next (diagonal), so with threshold 1km they should separate
    assertEquals(20, result.size)
  }

  @Test
  fun manyRandomItemsProduceManyClusters() {
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 1.0 })

    // 200 items spaced by 1 km → none should merge
    val items = List(200) { i -> km(i.toDouble(), 0.0) }

    val result = strategy.clusterize(items, 14f)
    assertEquals(200, result.size)
  }

  @Test
  fun largeDenseCloudProducesSingleCluster() {
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 0.5 }) // 500m

    // 300 points inside an area of approximately 200m
    val items =
        List(300) { i ->
          val dx = (i % 20) * 0.01 // ~10m steps
          val dy = (i / 20) * 0.01
          km(dx, dy)
        }

    val result = strategy.clusterize(items, 14f)

    assertEquals(1, result.size)
    assertEquals(300, result[0].items.size)
  }

  @Test
  fun twoLargeGroupsFormTwoClusters() {
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = { 1.0 })

    // First dense group
    val groupA = List(100) { km(it * 0.005, 0.0) } // ~0.5 km span

    // Second dense group located 10 km further
    val groupB = List(100) { km(10.0 + it * 0.005, 0.0) }

    val items = groupA + groupB

    val result = strategy.clusterize(items, 14f)

    assertEquals(2, result.size)

    // Each cluster must contain exactly 100 items
    assertTrue(result.all { it.items.size == 100 })
  }

  // ============================================================================
  // CLUSTER THRESHOLD STRATEGY TESTS
  // ============================================================================

  @Test
  fun linearScaling_decreasesThresholdWithZoom() {
    val strategy = DistanceBasedClusterStrategy(zoomToThreshold = ClusterThresholds.linearScaling)

    val a = km(0.0, 0.0)
    val b = km(0.5, 0.0) // 0.5 km apart

    // At low zoom (10), threshold is ~1km → cluster
    val lowZoom = strategy.clusterize(listOf(a, b), 10f)
    assertEquals(1, lowZoom.size)

    // At high zoom (18), threshold is ~0.04km → separate
    val highZoom = strategy.clusterize(listOf(a, b), 18f)
    assertEquals(2, highZoom.size)
  }

  @Test
  fun piecewiseLinearScaling_behavesCorrectly() {
    val strategy =
        DistanceBasedClusterStrategy(zoomToThreshold = ClusterThresholds.piecewiseLinearScaling)

    val a = km(0.0, 0.0)
    val b = km(1.5, 0.0) // 1.5 km apart

    // At zoom 10, threshold is ~2km → cluster
    val zoom10 = strategy.clusterize(listOf(a, b), 10f)
    assertEquals(1, zoom10.size)

    // At zoom 16, threshold is ~0.15km → separate
    val zoom16 = strategy.clusterize(listOf(a, b), 16f)
    assertEquals(2, zoom16.size)
  }

  @Test
  fun exponentialScaling_decreasesRapidly() {
    val strategy =
        DistanceBasedClusterStrategy(zoomToThreshold = ClusterThresholds.exponentialScaling)

    val a = km(0.0, 0.0)
    val b = km(5.0, 0.0) // 5 km apart

    // At zoom 10, threshold is ~16km → cluster
    val zoom10 = strategy.clusterize(listOf(a, b), 10f)
    assertEquals(1, zoom10.size)

    // At zoom 14, threshold is ~1km → separate
    val zoom14 = strategy.clusterize(listOf(a, b), 14f)
    assertEquals(2, zoom14.size)

    // At zoom 18, threshold is ~0.06km → definitely separate
    val zoom18 = strategy.clusterize(listOf(a, b), 18f)
    assertEquals(2, zoom18.size)
  }

  @Test
  fun piecewiseExponentialScaling_defaultBehavior() {
    val strategy = DistanceBasedClusterStrategy() // Default uses piecewiseExponentialScaling

    val a = km(0.0, 0.0)
    val b = km(2.0, 0.0) // 2 km apart

    // At zoom 10, threshold is ~5km → cluster
    val zoom10 = strategy.clusterize(listOf(a, b), 10f)
    assertEquals(1, zoom10.size)

    // At zoom 14, threshold is ~1km → separate
    val zoom14 = strategy.clusterize(listOf(a, b), 14f)
    assertEquals(2, zoom14.size)

    // At zoom 18, threshold is ~0.06km → separate
    val zoom18 = strategy.clusterize(listOf(a, b), 18f)
    assertEquals(2, zoom18.size)
  }

  @Test
  fun allScalingStrategies_produceSmallerThresholdsAtHigherZoom() {
    val strategies =
        listOf(
            ClusterThresholds.linearScaling,
            ClusterThresholds.piecewiseLinearScaling,
            ClusterThresholds.exponentialScaling,
            ClusterThresholds.piecewiseExponentialScaling)

    strategies.forEach { thresholdFn ->
      val threshold10 = thresholdFn(10f)
      val threshold14 = thresholdFn(14f)
      val threshold18 = thresholdFn(18f)

      assertTrue("Threshold should decrease with zoom", threshold10 > threshold14)
      assertTrue("Threshold should decrease with zoom", threshold14 > threshold18)
    }
  }

  @Test
  fun linearScaling_hasMinimumThreshold() {
    val threshold = ClusterThresholds.linearScaling(100f) // Very high zoom
    assertTrue("Linear scaling should have a minimum threshold", threshold >= 0.03)
  }

  @Test
  fun scalingStrategies_produceReasonableValues() {
    val zoom14 = 14f

    val linear = ClusterThresholds.linearScaling(zoom14)
    val piecewiseLinear = ClusterThresholds.piecewiseLinearScaling(zoom14)
    val exponential = ClusterThresholds.exponentialScaling(zoom14)
    val piecewiseExponential = ClusterThresholds.piecewiseExponentialScaling(zoom14)

    // All should be positive
    assertTrue(linear > 0)
    assertTrue(piecewiseLinear > 0)
    assertTrue(exponential > 0)
    assertTrue(piecewiseExponential > 0)

    // All should be reasonable (between 10m and 10km)
    assertTrue(linear in 0.01..10.0)
    assertTrue(piecewiseLinear in 0.01..10.0)
    assertTrue(exponential in 0.01..10.0)
    assertTrue(piecewiseExponential in 0.01..10.0)
  }

  // ============================================================================
  // EDGE CASES & BOUNDARY CONDITIONS
  // ============================================================================

  @Test
  fun extremelyHighZoom_producesVerySmallThreshold() {
    val strategy = DistanceBasedClusterStrategy()

    val a = km(0.0, 0.0)
    val b = km(0.01, 0.0) // 10 meters apart

    // At zoom 22 (very high), even 10m should separate
    val result = strategy.clusterize(listOf(a, b), 22f)
    assertEquals(2, result.size)
  }

  @Test
  fun extremelyLowZoom_clustersEverything() {
    val strategy = DistanceBasedClusterStrategy()

    val a = km(0.0, 0.0)
    val b = km(100.0, 0.0) // 100 km apart

    // At zoom 1 (very low), even 100km should cluster
    val result = strategy.clusterize(listOf(a, b), 1f)
    assertEquals(1, result.size)
  }

  @Test
  fun zeroZoom_handledGracefully() {
    val strategy = DistanceBasedClusterStrategy()

    val items = listOf(km(0.0, 0.0), km(50.0, 0.0))

    val result = strategy.clusterize(items, 0f)

    // Should not crash, should produce valid clusters
    assertTrue(result.isNotEmpty())
    assertTrue(result.all { it.items.isNotEmpty() })
  }

  @Test
  fun negativeZoom_handledGracefully() {
    val strategy = DistanceBasedClusterStrategy()

    val items = listOf(km(0.0, 0.0), km(10.0, 0.0))

    val result = strategy.clusterize(items, -5f)

    // Should not crash, should produce valid clusters
    assertTrue(result.isNotEmpty())
    assertTrue(result.all { it.items.isNotEmpty() })
  }
}
