package com.github.meeplemeet.model.map

import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.map.PinPreview.*
import com.github.meeplemeet.model.sessions.GameRepository
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.google.firebase.Timestamp
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Resolves a MapPin into a PinPreview by fetching and transforming the linked entity.
 *
 * Combines data from Shop, Session, SpaceRenter and Game repositories to build a minimal preview
 * model for map display.
 *
 * This class does not cache or persist data. It assumes session pins are already filtered by
 * participation before resolution.
 */
class PinPreviewRepository(
    private val shopRepository: ShopRepository,
    private val discussionRepository: DiscussionRepository,
    private val spaceRenterRepository: SpaceRenterRepository,
    private val gameRepository: GameRepository
) {

  /**
   * Resolves the given MapPin into a PinPreview.
   *
   * @param pin The MapPin to resolve
   * @return A PinPreview containing minimal info for display, or null if resolution fails
   */
  suspend fun getPinPreview(pin: MapPin): PinPreview? {
    return when (pin.type) {
      PinType.SHOP -> {
        val shop = shopRepository.getShop(pin.uid)
        shop.let {
          ShopPreview(name = it.name, address = it.address.name, open = isOpenNow(it.openingHours))
        }
      }
      PinType.SESSION -> {
        val session = discussionRepository.getDiscussion(pin.uid).session
        session?.let {
          SessionPreview(
              title = it.name,
              game = gameRepository.getGameById(it.gameId).name,
              address = it.location.name,
              date = formatTimeStamp(it.date))
        }
      }
      PinType.SPACE -> {
        val space = spaceRenterRepository.getSpaceRenter(pin.uid)
        space.let {
          SpacePreview(name = it.name, address = it.address.name, open = isOpenNow(it.openingHours))
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
