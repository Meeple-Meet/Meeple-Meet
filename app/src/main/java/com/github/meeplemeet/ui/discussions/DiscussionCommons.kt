/** Common components and utilities shared between discussion screens */
package com.github.meeplemeet.ui.discussions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.github.meeplemeet.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

/** Constants shared across discussion screens */
object DiscussionCommons {
  const val YOU_SENDER_NAME = "You"
  const val UNKNOWN_SENDER_NAME = "Unknown"
  const val TIME_FORMAT = "HH:mm"
  const val NO_MESSAGES_DEFAULT_TEXT = "(No messages yet)"
}

/**
 * Reusable profile picture component with circular shape and optional image
 *
 * @param profilePictureUrl Optional URL to the profile picture
 * @param size Size of the circular profile picture
 * @param backgroundColor Background color when no image is provided
 * @param modifier Optional modifier
 */
@Composable
fun ProfilePicture(
    profilePictureUrl: String?,
    size: Dp,
    backgroundColor: androidx.compose.ui.graphics.Color = AppColors.neutral,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  Box(
      modifier =
          modifier.size(size).clip(CircleShape).background(backgroundColor, CircleShape).clickable {
            onClick()
          }) {
        if (profilePictureUrl != null) {
          AsyncImage(
              model = profilePictureUrl,
              contentDescription = "Profile Picture",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop)
        }
      }
}

/** Formats a date as "Today", "Yesterday" or "MMM dd, yyyy". */
fun formatDateBubble(date: Date): String {
  val today = Calendar.getInstance()
  val cal = Calendar.getInstance().apply { time = date }

  return when {
    isSameDay(cal, today) -> "Today"
    isSameDay(cal, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
    else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
  }
}

/** Returns true if [current] and [previous] are on different calendar days. */
fun shouldShowDateHeader(current: Date, previous: Date?): Boolean {
  if (previous == null) return true
  val calCurrent = Calendar.getInstance().apply { time = current }
  val calPrev = Calendar.getInstance().apply { time = previous }
  return !(calCurrent.get(Calendar.YEAR) == calPrev.get(Calendar.YEAR) &&
      calCurrent.get(Calendar.DAY_OF_YEAR) == calPrev.get(Calendar.DAY_OF_YEAR))
}

/** Returns true if two [Calendar] instances represent the same day. */
fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
  return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
      cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
