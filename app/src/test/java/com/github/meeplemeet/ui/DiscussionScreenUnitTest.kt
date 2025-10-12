package com.github.meeplemeet.ui

import java.text.SimpleDateFormat
import java.util.*
import org.junit.Assert.*
import org.junit.Test

class DiscussionScreenUnitTest {

  /** Tests that isSameDay returns true for two dates on the same day */
  @Test
  fun isSameDay_returns_true_for_same_day() {
    val cal1 = Calendar.getInstance().apply { set(2025, 0, 1) }
    val cal2 = Calendar.getInstance().apply { set(2025, 0, 1) }
    assertTrue(isSameDay(cal1, cal2))
  }

  /** Tests that isSameDay returns false for dates on different days */
  @Test
  fun isSameDay_returns_false_for_different_days() {
    val cal1 = Calendar.getInstance().apply { set(2025, 0, 1) }
    val cal2 = Calendar.getInstance().apply { set(2025, 0, 2) }
    assertFalse(isSameDay(cal1, cal2))
  }

  /** Tests that shouldShowDateHeader returns true if previous date is null */
  @Test
  fun shouldShowDateHeader_returns_true_when_previous_is_null() {
    val now = Date()
    assertTrue(shouldShowDateHeader(now, null))
  }

  /** Tests that shouldShowDateHeader returns false for messages on the same day */
  @Test
  fun shouldShowDateHeader_returns_false_for_same_day() {
    val now = Date()
    val laterSameDay = Date(now.time + 1000 * 60)
    assertFalse(shouldShowDateHeader(now, laterSameDay))
  }

  /** Tests that shouldShowDateHeader returns true for messages on different days */
  @Test
  fun shouldShowDateHeader_returns_true_for_different_days() {
    val now = Calendar.getInstance().apply { set(2025, 0, 1) }.time
    val nextDay = Calendar.getInstance().apply { set(2025, 0, 2) }.time
    assertTrue(shouldShowDateHeader(nextDay, now))
  }

  /** Tests that formatDateBubble returns "Today" for today's date */
  @Test
  fun formatDateBubble_returns_Today_for_todays_date() {
    val today = Date()
    assertEquals("Today", formatDateBubble(today))
  }

  /** Tests that formatDateBubble returns "Yesterday" for yesterday's date */
  @Test
  fun formatDateBubble_returns_Yesterday_for_yesterdays_date() {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
    assertEquals("Yesterday", formatDateBubble(yesterday))
  }

  /** Tests that formatDateBubble formats older dates correctly */
  @Test
  fun formatDateBubble_returns_formatted_date_for_older_date() {
    val date = Calendar.getInstance().apply { set(2020, 0, 1) }.time
    val expected = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
    assertEquals(expected, formatDateBubble(date))
  }
}
