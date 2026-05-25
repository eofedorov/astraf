package com.astraf.hrgpslogger.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

enum class DayPart {
    Night,
    Morning,
    Day,
    Evening,
}

fun dayPartFromHour(hour: Int): DayPart = when (hour) {
    in 0..5 -> DayPart.Night
    in 6..11 -> DayPart.Morning
    in 12..17 -> DayPart.Day
    else -> DayPart.Evening
}

fun dayPartLabel(part: DayPart): String = when (part) {
    DayPart.Night -> "ночью"
    DayPart.Morning -> "утром"
    DayPart.Day -> "днём"
    DayPart.Evening -> "вечером"
}

private val russianLocale = Locale.forLanguageTag("ru")
private val rideTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", russianLocale)
private val monthDayFormatter = DateTimeFormatter.ofPattern("d MMMM", russianLocale)
private val monthYearFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", russianLocale)
private val monthOnlyFormatter = DateTimeFormatter.ofPattern("LLLL", russianLocale)

fun formatRideCardHeader(
    startedAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: ZonedDateTime = ZonedDateTime.now(zoneId),
): String {
    val rideTime = Instant.ofEpochMilli(startedAtMillis).atZone(zoneId)
    val time = rideTimeFormatter.format(rideTime)
    return formatRideHumanDate(startedAtMillis, zoneId, now)
}

fun formatRideHumanDate(
    startedAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: ZonedDateTime = ZonedDateTime.now(zoneId),
): String {
    val rideTime = Instant.ofEpochMilli(startedAtMillis).atZone(zoneId)
    val rideDate = rideTime.toLocalDate()
    val today = now.toLocalDate()
    val dayPart = dayPartLabel(dayPartFromHour(rideTime.hour))

    return when (ChronoUnit.DAYS.between(rideDate, today)) {
        0L -> "Сегодня $dayPart"
        1L -> "Вчера $dayPart"
        else -> "${monthDayFormatter.format(rideTime)} $dayPart"
    }
}

fun formatTrackMonthSectionTitle(
    startedAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: LocalDate = LocalDate.now(zoneId),
): String {
    val rideDate = Instant.ofEpochMilli(startedAtMillis).atZone(zoneId).toLocalDate()
    return if (rideDate.year == now.year) {
        monthOnlyFormatter.format(rideDate).replaceFirstChar { it.titlecase(russianLocale) }
    } else {
        monthYearFormatter.format(rideDate).replaceFirstChar { it.titlecase(russianLocale) }
    }
}

fun formatListElevationGain(meters: Float?): String =
    meters?.let { "↑ ${it.roundToInt()} м" } ?: "↑ —"

fun formatListElevationMeters(meters: Float?): String =
    meters?.let { "${it.roundToInt()} м" } ?: "—"

fun formatListMaxSpeed(speedKmh: Float?): String =
    speedKmh?.let { "Макс: ${"%.1f".format(Locale.getDefault(), it)} км/ч" } ?: "Макс: —"

fun formatListHeartRate(bpm: Int?): String =
    bpm?.let { "Пульс: $it" } ?: "Пульс: —"

data class TrackMonthSection(
    val title: String,
    val tracks: List<com.astraf.hrgpslogger.TrackSummary>,
)

fun groupTracksByMonth(
    tracks: List<com.astraf.hrgpslogger.TrackSummary>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<TrackMonthSection> {
    if (tracks.isEmpty()) return emptyList()
    val grouped = tracks.groupBy { track ->
        Instant.ofEpochMilli(track.startedAtMillis)
            .atZone(zoneId)
            .toLocalDate()
            .withDayOfMonth(1)
    }
    return grouped.entries
        .sortedByDescending { it.key }
        .map { (_, monthTracks) ->
            val first = monthTracks.first()
            TrackMonthSection(
                title = formatTrackMonthSectionTitle(first.startedAtMillis, zoneId),
                tracks = monthTracks.sortedByDescending { it.startedAtMillis },
            )
        }
}
