package com.github.meeplemeet.model.map.cluster

/**
 * Manager responsible for clustering items using a specified [ClusterStrategy].
 *
 * This class is generic and can cluster any type of items, as long as a mapper from the item type
 * to [ClusterItem] is provided.
 *
 * @property strategy The clustering strategy to use.
 */
class ClusterManager(private val strategy: ClusterStrategy) {

  /**
   * Clusters a list of items based on their mapped geolocation.
   *
   * @param items The list of items to cluster.
   * @param zoomLevel The current zoom level of the map. Can be used by the strategy to adjust
   *   cluster density.
   * @param mapper Function that maps an item [T] to a [ClusterItem] containing its geolocation for
   *   clustering.
   * @return A list of [Cluster] objects, each containing the original items of type [T].
   */
  fun <T> cluster(items: List<T>, zoomLevel: Float, mapper: (T) -> ClusterItem): List<Cluster<T>> {
    // Map each original item to a ClusterItem containing only location info
    val clusterItems = items.map { mapper(it) }

    // Use the clustering strategy to group ClusterItems into clusters
    val clustersOfItems = strategy.clusterize(clusterItems, zoomLevel)

    // Map back from ClusterItem clusters to clusters of original items
    return clustersOfItems.map { cluster ->
      val originalItems = items.filter { mapper(it) in cluster.items }
      Cluster(cluster.centerLat, cluster.centerLng, originalItems)
    }
  }
}
