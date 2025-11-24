package com.github.meeplemeet.model.map.cluster

/**
 * Represents a single geolocated element that can be clustered.
 *
 * @property lat The latitude of the item, in degrees.
 * @property lng The longitude of the item, in degrees.
 */
data class ClusterItem(val lat: Double, val lng: Double)

/**
 * Represents a group of clustered items, along with the geographic center of the cluster.
 *
 * @property centerLat The latitude of the cluster's center, in degrees.
 * @property centerLng The longitude of the cluster's center, in degrees.
 * @property items The list of items contained in this cluster.
 */
data class Cluster(val centerLat: Double, val centerLng: Double, val items: List<ClusterItem>)
