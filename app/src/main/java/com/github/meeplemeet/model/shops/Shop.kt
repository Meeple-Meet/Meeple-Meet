package com.github.meeplemeet.model.shops

// Claude Code generated the documentation

import kotlinx.serialization.Serializable

@Serializable data class TimeSlot(val open: String? = null, val close: String? = null)

@Serializable data class OpeningHours(val day: Int = 0, val hours: List<TimeSlot> = emptyList())
