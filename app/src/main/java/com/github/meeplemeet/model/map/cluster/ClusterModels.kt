package com.github.meeplemeet.model.map.cluster

/**
 * Represents a single geolocated element that can be clustered.
 *
 * @property lat The latitude of the item, in degrees.
 * @property lng The longitude of the item, in degrees.
 */
data class ClusterItem(val lat: Double, val lng: Double)

/**
 * Represents a group of items clustered together on a map, along with the geographic center.
 *
 * @param T The type of the items contained in the cluster.
 * @property centerLat The latitude of the cluster's center, in degrees.
 * @property centerLng The longitude of the cluster's center, in degrees.
 * @property items The list of items contained in this cluster. Can be of any type [T].
 */
data class Cluster<T>(val centerLat: Double, val centerLng: Double, val items: List<T>)
