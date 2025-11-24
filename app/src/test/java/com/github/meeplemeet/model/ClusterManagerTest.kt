package com.github.meeplemeet.model

import com.github.meeplemeet.model.map.cluster.Cluster
import com.github.meeplemeet.model.map.cluster.ClusterItem
import com.github.meeplemeet.model.map.cluster.ClusterManager
import com.github.meeplemeet.model.map.cluster.ClusterStrategy
import junit.framework.TestCase.assertEquals
import kotlin.test.Test

class ClusterManagerTest {

  private val clusterManager =
      ClusterManager(
          strategy =
              object : ClusterStrategy {
                override fun clusterize(
                    items: List<ClusterItem>,
                    zoomLevel: Float
                ): List<Cluster<ClusterItem>> {
                  if (items.isEmpty()) return emptyList()

                  // Regroup all items into a single cluster
                  val centerLat = items.map { it.lat }.average()
                  val centerLng = items.map { it.lng }.average()
                  return listOf(Cluster(centerLat, centerLng, items))
                }
              })

  data class TestItem(val id: Int, val lat: Double, val lng: Double)

  @Test
  fun clusterShouldMapAndRemapItemsCorrectly() {
    val items = listOf(TestItem(1, 0.0, 0.0), TestItem(2, 1.0, 1.0), TestItem(3, 2.0, 2.0))

    val clusters =
        clusterManager.cluster(items, zoomLevel = 10f) { item -> ClusterItem(item.lat, item.lng) }

    assertEquals(1, clusters.size)

    val cluster = clusters.first()
    assertEquals(items.sortedBy { it.id }, cluster.items.sortedBy { it.id })

    val expectedLat = items.map { it.lat }.average()
    val expectedLng = items.map { it.lng }.average()
    assertEquals(expectedLat, cluster.centerLat)
    assertEquals(expectedLng, cluster.centerLng)
  }

  @Test
  fun clusterShouldHandleEmptyList() {
    val clusters =
        clusterManager.cluster(emptyList<TestItem>(), zoomLevel = 5f) { item ->
          ClusterItem(item.lat, item.lng)
        }
    assertEquals(0, clusters.size)
  }
}
