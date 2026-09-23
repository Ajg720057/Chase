package com.chase.lifeboard.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Format {
    private val zone get() = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayShort = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val dayWithYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

    fun toDateTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun toDate(millis: Long): LocalDate = toDateTime(millis).toLocalDate()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    fun relativeDay(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> if (date.year == today.year) date.format(dayShort) else date.format(dayWithYear)
        }
    }

    fun due(millis: Long): String {
        val dt = toDateTime(millis)
        return "${relativeDay(dt.toLocalDate())}, ${dt.format(time)}"
    }

    fun time(millis: Long): String = toDateTime(millis).format(time)
    fun longDay(date: LocalDate): String = date.format(dayLong)
}
