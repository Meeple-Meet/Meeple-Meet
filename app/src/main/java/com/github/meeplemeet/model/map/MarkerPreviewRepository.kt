package com.github.meeplemeet.model.map

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.MarkerPreview.*
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.google.firebase.Timestamp
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Resolves a StorableGeoPin into a MarkerPreview by fetching and transforming the linked entity.
 *
 * Combines data from Shop, Session, SpaceRenter and Game repositories to build a minimal preview
 * model for map display.
 *
 * This class does not cache or persist data. It assumes session pins are already filtered by
 * participation before resolution.
 */
class MarkerPreviewRepository(
    private val shopRepository: ShopRepository = RepositoryProvider.shops,
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
    private val spaceRenterRepository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
) {

  /**
   * Transforms a list of [StorableGeoPin]s into their corresponding [MarkerPreview]s in parallel.
   *
   * Each pin is fetched according to its type:
   * - [PinType.SHOP] → fetches the shop and builds a [ShopMarkerPreview]
   * - [PinType.SESSION] → fetches the session and game to build a [SessionMarkerPreview]
   * - [PinType.SPACE] → fetches the space renter and builds a [SpaceMarkerPreview]
   *
   * @param pins List of pins to transform
   * @return List of [MarkerPreview?]s. A preview may be `null` if the corresponding entity could
   *   not be fetched.
   */
  suspend fun getMarkerPreviews(pins: List<StorableGeoPin>): List<MarkerPreview?> = coroutineScope {
    val shopPins = pins.filter { it.type == PinType.SHOP }
    val sessionPins = pins.filter { it.type == PinType.SESSION }
    val spacePins = pins.filter { it.type == PinType.SPACE }

    // Launch 3 request in parallel
    val shopsDeferred = async {
      shopPins
          .map { pin ->
            async {
              val shop = shopRepository.getShop(pin.uid)
              ShopMarkerPreview(
                  name = shop.name,
                  address = shop.address.name,
                  open = isOpenNow(shop.openingHours))
            }
          }
          .awaitAll()
    }

    val sessionsDeferred = async {
      sessionPins
          .map { pin ->
            async {
              val session = discussionRepository.getDiscussion(pin.uid).session
              session?.let {
                SessionMarkerPreview(
                    title = it.name,
                    address = it.location.name,
                    game = it.gameName,
                    date = formatTimeStamp(it.date))
              }
            }
          }
          .awaitAll()
    }

    val spacesDeferred = async {
      spacePins
          .map { pin ->
            async {
              val space = spaceRenterRepository.getSpaceRenter(pin.uid)
              SpaceMarkerPreview(
                  name = space.name,
                  address = space.address.name,
                  open = isOpenNow(space.openingHours))
            }
          }
          .awaitAll()
    }

    // Merge all results, order not guaranteed
    val shopResults = shopsDeferred.await()
    val sessionResults = sessionsDeferred.await()
    val spaceResults = spacesDeferred.await()

    shopResults + sessionResults + spaceResults
  }

  /**
   * Transforms a single [StorableGeoPin] into a [MarkerPreview].
   *
   * This function fetches the linked entity (Shop, Session, or SpaceRenter) and builds a minimal
   * preview for map display. It does not filter sessions by user participation.
   *
   * @param pin The geo-pin to transform.
   * @return A [MarkerPreview] for the pin, or null if resolution fails.
   */
  suspend fun getMarkerPreview(pin: StorableGeoPin): MarkerPreview? {
    return when (pin.type) {
      PinType.SHOP -> {
        val shop = shopRepository.getShop(pin.uid)
        shop.let {
          ShopMarkerPreview(
              name = it.name, address = it.address.name, open = isOpenNow(it.openingHours))
        }
      }
      PinType.SESSION -> {
        val session = discussionRepository.getDiscussion(pin.uid).session
        session?.let {
          SessionMarkerPreview(
              title = it.name,
              address = it.location.name,
              game = it.gameName,
              date = formatTimeStamp(it.date))
        }
      }
      PinType.SPACE -> {
        val space = spaceRenterRepository.getSpaceRenter(pin.uid)
        space.let {
          SpaceMarkerPreview(
              name = it.name, address = it.address.name, open = isOpenNow(it.openingHours))
        }
      }
    }
  }

  /**
   * Determines whether the current time falls within any opening slot for today.
   *
   * @param openingHours List of declared opening hours
   * @return True if currently open, false otherwise
   */
  private fun isOpenNow(openingHours: List<OpeningHours>): Boolean {
    val now = LocalDateTime.now()
    val currentDay = now.dayOfWeek.value
    val nowTime = now.toLocalTime()

    val todaySlots = openingHours.find { it.day == currentDay }?.hours ?: return false

    return todaySlots.any { slot ->
      val start = LocalTime.parse(slot.open)
      val end = LocalTime.parse(slot.close)
      nowTime.isAfter(start) && nowTime.isBefore(end)
    }
  }

  /**
   * Formats a Firestore Timestamp into a UI-friendly string.
   *
   * Format: "dd/MM/yyyy at HH:mm"
   *
   * @param timestamp The Firestore timestamp to format
   * @return A formatted date string
   */
  private fun formatTimeStamp(timestamp: Timestamp): String {
    val instant = timestamp.toDate().toInstant()
    val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm")
    return localDateTime.format(formatter)
  }
}
