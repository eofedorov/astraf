package com.astraf.hrgpslogger

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class StatsPeriod {
    Week,
    Month,
    Year,
    AllTime,
}

data class StatsPeriodRange(
    val startInclusive: LocalDate,
    val endInclusive: LocalDate,
)

data class ActivityBucket(
    val key: String,
    val label: String,
    val distanceMeters: Double,
    val rideCount: Int,
    val tooltipDateLabel: String,
)

data class ActivityDay(
    val date: LocalDate,
    val distanceMeters: Double,
    val rideCount: Int,
    val movingTimeMillis: Long,
    val intensity: Float,
)

data class HeatmapLayout(
    val weeks: List<List<ActivityDay?>>,
    val monthLabels: List<String>,
)

data class StatsRecord(
    val type: StatsRecordType,
    val titleKey: StatsRecordType,
    val valueLabel: String,
    val dateLabel: String,
    val trackFileName: String?,
    val dayDate: LocalDate?,
)

enum class StatsRecordType {
    LongestRide,
    BiggestClimb,
    HighestAvgSpeed,
    MaxSpeed,
    MostActiveDay,
}

data class DistanceDistributionBucket(
    val label: String,
    val rideCount: Int,
    val percent: Int,
    val fraction: Float,
)

data class BestRides(
    val longest: TrackSummary?,
    val fastest: TrackSummary?,
    val biggestClimb: TrackSummary?,
)

data class ActivityMapTrack(
    val fileName: String,
    val routePoints: List<RoutePreviewPoint>,
)

data class StatsSummary(
    val totalDistanceMeters: Double,
    val totalMovingTimeMillis: Long,
    val rideCount: Int,
    val totalClimbMeters: Float,
    val averageMovingSpeedKmh: Float?,
    val maxSpeedKmh: Float?,
)

data class StatsSnapshot(
    val period: StatsPeriod,
    val periodRange: StatsPeriodRange,
    val summary: StatsSummary,
    val activityBuckets: List<ActivityBucket>,
    val heatmapDays: List<ActivityDay>,
    val heatmapLayout: HeatmapLayout,
    val records: List<StatsRecord>,
    val distribution: List<DistanceDistributionBucket>,
    val mapTracks: List<ActivityMapTrack>,
    val bestRides: BestRides,
    val tracksInPeriod: List<TrackSummary>,
)

object StatisticsCalculator {

    private const val MOVING_SPEED_MIN_DISTANCE_KM = 20.0
    private const val YEARLY_BAR_CHART_THRESHOLD_MONTHS = 24
    private const val ALL_TIME_HEATMAP_MONTHS = 12
    private const val YEAR_HEATMAP_COLUMNS = 31

    private val weekFields = WeekFields.of(DayOfWeek.MONDAY, 1)

    fun isEligibleForStats(track: TrackSummary): Boolean =
        !track.isActive &&
            track.hasGpsData &&
            track.pointCount >= 2 &&
            (track.distanceMeters ?: 0.0) > 0.0 &&
            track.routePoints.isNotEmpty()

    fun filterEligible(tracks: List<TrackSummary>): List<TrackSummary> =
        tracks.filter(::isEligibleForStats)

    fun hasAnyEligible(tracks: List<TrackSummary>): Boolean =
        tracks.any(::isEligibleForStats)

    fun periodRange(
        period: StatsPeriod,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId),
    ): StatsPeriodRange = when (period) {
        StatsPeriod.Week -> {
            val start = today.with(weekFields.dayOfWeek(), 1L)
            StatsPeriodRange(start, start.plusDays(6))
        }
        StatsPeriod.Month -> {
            val start = today.withDayOfMonth(1)
            StatsPeriodRange(start, start.with(TemporalAdjusters.lastDayOfMonth()))
        }
        StatsPeriod.Year -> {
            val start = today.withDayOfYear(1)
            StatsPeriodRange(start, start.with(TemporalAdjusters.lastDayOfYear()))
        }
        StatsPeriod.AllTime -> StatsPeriodRange(LocalDate.MIN, LocalDate.MAX)
    }

    fun tracksInPeriod(
        tracks: List<TrackSummary>,
        period: StatsPeriod,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId),
    ): List<TrackSummary> {
        val eligible = filterEligible(tracks)
        if (period == StatsPeriod.AllTime) return eligible.sortedByDescending { it.startedAtMillis }
        val range = periodRange(period, zoneId, today)
        return eligible.filter { track ->
            val date = track.localDate(zoneId)
            !date.isBefore(range.startInclusive) && !date.isAfter(range.endInclusive)
        }.sortedByDescending { it.startedAtMillis }
    }

    fun buildSnapshot(
        allTracks: List<TrackSummary>,
        period: StatsPeriod,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
    ): StatsSnapshot {
        val today = now.toLocalDate()
        val periodTracks = tracksInPeriod(allTracks, period, zoneId, today)
        val range = periodRange(period, zoneId, today)
        return StatsSnapshot(
            period = period,
            periodRange = range,
            summary = buildSummary(periodTracks),
            activityBuckets = buildActivityBuckets(periodTracks, period, zoneId, today),
            heatmapDays = buildHeatmapDays(periodTracks, period, zoneId, today),
            heatmapLayout = buildHeatmapLayout(periodTracks, period, zoneId, today),
            records = buildRecords(periodTracks, zoneId),
            distribution = buildDistribution(periodTracks),
            mapTracks = periodTracks.map { ActivityMapTrack(it.fileName, it.routePoints) },
            bestRides = buildBestRides(periodTracks),
            tracksInPeriod = periodTracks,
        )
    }

    fun buildSummary(tracks: List<TrackSummary>): StatsSummary {
        val totalDistance = tracks.sumOf { it.distanceMeters ?: 0.0 }
        val totalMovingTime = tracks.sumOf { it.movingTimeMillis ?: 0L }
        val totalClimb = tracks.sumOf { (it.totalClimbMeters ?: 0f).toDouble() }.toFloat()
        val avgSpeed = if (totalMovingTime > 0L) {
            (totalDistance / (totalMovingTime / 1000.0) * 3.6).toFloat()
        } else {
            null
        }
        val maxSpeed = tracks.mapNotNull { it.maxSpeedKmh }.maxOrNull()
        return StatsSummary(
            totalDistanceMeters = totalDistance,
            totalMovingTimeMillis = totalMovingTime,
            rideCount = tracks.size,
            totalClimbMeters = totalClimb,
            averageMovingSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeed,
        )
    }

    fun buildActivityBuckets(
        tracks: List<TrackSummary>,
        period: StatsPeriod,
        zoneId: ZoneId,
        today: LocalDate,
    ): List<ActivityBucket> = when (period) {
        StatsPeriod.Week -> buildWeeklyBuckets(tracks, zoneId, today)
        StatsPeriod.Month -> buildDailyBucketsForMonth(tracks, zoneId, today)
        StatsPeriod.Year -> buildMonthlyBucketsForYear(tracks, zoneId, today)
        StatsPeriod.AllTime -> buildAllTimeBuckets(tracks, zoneId)
    }

    private fun buildWeeklyBuckets(
        tracks: List<TrackSummary>,
        zoneId: ZoneId,
        today: LocalDate,
    ): List<ActivityBucket> {
        val weekStart = today.with(weekFields.dayOfWeek(), 1L)
        val dayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        return (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dayTracks = tracks.filter { it.localDate(zoneId) == date }
            ActivityBucket(
                key = date.toString(),
                label = dayLabels[offset],
                distanceMeters = dayTracks.sumOf { it.distanceMeters ?: 0.0 },
                rideCount = dayTracks.size,
                tooltipDateLabel = formatDayMonth(date),
            )
        }
    }

    private fun buildDailyBucketsForMonth(
        tracks: List<TrackSummary>,
        zoneId: ZoneId,
        today: LocalDate,
    ): List<ActivityBucket> {
        val monthStart = today.withDayOfMonth(1)
        val daysInMonth = monthStart.lengthOfMonth()
        return (1..daysInMonth).map { day ->
            val date = monthStart.withDayOfMonth(day)
            val dayTracks = tracks.filter { it.localDate(zoneId) == date }
            ActivityBucket(
                key = date.toString(),
                label = day.toString(),
                distanceMeters = dayTracks.sumOf { it.distanceMeters ?: 0.0 },
                rideCount = dayTracks.size,
                tooltipDateLabel = formatDayMonth(date),
            )
        }
    }

    private fun buildMonthlyBucketsForYear(
        tracks: List<TrackSummary>,
        zoneId: ZoneId,
        today: LocalDate,
    ): List<ActivityBucket> {
        val monthLabels = listOf(
            "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
        )
        return (1..12).map { month ->
            val ym = YearMonth.of(today.year, month)
            val monthTracks = tracks.filter {
                YearMonth.from(it.localDate(zoneId)) == ym
            }
            ActivityBucket(
                key = ym.toString(),
                label = monthLabels[month - 1],
                distanceMeters = monthTracks.sumOf { it.distanceMeters ?: 0.0 },
                rideCount = monthTracks.size,
                tooltipDateLabel = monthLabels[month - 1],
            )
        }
    }

    private fun buildAllTimeBuckets(
        tracks: List<TrackSummary>,
        zoneId: ZoneId,
    ): List<ActivityBucket> {
        if (tracks.isEmpty()) return emptyList()
        val months = tracks
            .map { YearMonth.from(it.localDate(zoneId)) }
            .distinct()
            .sorted()
        if (months.size > YEARLY_BAR_CHART_THRESHOLD_MONTHS) {
            val byYear = tracks.groupBy { it.localDate(zoneId).year }
            return byYear.keys.sorted().map { year ->
                val yearTracks = byYear[year].orEmpty()
                ActivityBucket(
                    key = year.toString(),
                    label = year.toString(),
                    distanceMeters = yearTracks.sumOf { it.distanceMeters ?: 0.0 },
                    rideCount = yearTracks.size,
                    tooltipDateLabel = year.toString(),
                )
            }
        }
        val monthLabels = listOf(
            "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
        )
        return months.map { ym ->
            val monthTracks = tracks.filter { YearMonth.from(it.localDate(zoneId)) == ym }
            ActivityBucket(
                key = ym.toString(),
                label = "${monthLabels[ym.monthValue - 1]} ${ym.year % 100}",
                distanceMeters = monthTracks.sumOf { it.distanceMeters ?: 0.0 },
                rideCount = monthTracks.size,
                tooltipDateLabel = "${monthLabels[ym.monthValue - 1]} ${ym.year}",
            )
        }
    }

    fun buildHeatmapDays(
        tracks: List<TrackSummary>,
        period: StatsPeriod,
        zoneId: ZoneId,
        today: LocalDate,
    ): List<ActivityDay> {
        val dates = heatmapDateRange(period, zoneId, today)
        val maxDistance = tracks.maxOfOrNull { it.distanceMeters ?: 0.0 } ?: 0.0
        return dates.map { date ->
            val dayTracks = tracks.filter { it.localDate(zoneId) == date }
            val distance = dayTracks.sumOf { it.distanceMeters ?: 0.0 }
            val movingTime = dayTracks.sumOf { it.movingTimeMillis ?: 0L }
            ActivityDay(
                date = date,
                distanceMeters = distance,
                rideCount = dayTracks.size,
                movingTimeMillis = movingTime,
                intensity = if (maxDistance > 0.0 && distance > 0.0) {
                    (distance / maxDistance).toFloat().coerceIn(0.15f, 1f)
                } else {
                    0f
                },
            )
        }
    }

    fun buildHeatmapLayout(
        tracks: List<TrackSummary>,
        period: StatsPeriod,
        zoneId: ZoneId,
        today: LocalDate,
    ): HeatmapLayout {
        val days = buildHeatmapDays(tracks, period, zoneId, today)
        return when (period) {
            StatsPeriod.Week -> HeatmapLayout(
                weeks = listOf(days.map { it as ActivityDay? }),
                monthLabels = emptyList(),
            )
            StatsPeriod.Month -> buildMonthCalendarLayout(days, today)
            StatsPeriod.Year -> buildYearHeatmapLayout(days, today)
            StatsPeriod.AllTime -> buildAllTimeHeatmapLayout(days, zoneId, today)
        }
    }

    private fun buildMonthCalendarLayout(days: List<ActivityDay>, today: LocalDate): HeatmapLayout {
        val monthStart = today.withDayOfMonth(1)
        val firstDow = monthStart.dayOfWeek.value
        val leading = (firstDow - DayOfWeek.MONDAY.value + 7) % 7
        val cells = MutableList<ActivityDay?>(leading) { null } + days.map { it as ActivityDay? }
        val weeks = cells.chunked(7).map { week ->
            if (week.size < 7) week + List(7 - week.size) { null } else week
        }
        return HeatmapLayout(weeks = weeks, monthLabels = emptyList())
    }

    private fun buildYearHeatmapLayout(days: List<ActivityDay>, today: LocalDate): HeatmapLayout {
        val daysByDate = days.associateBy { it.date }
        val monthLabels = listOf(
            "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
        )
        val weeks = (1..12).map { month ->
            val daysInMonth = YearMonth.of(today.year, month).lengthOfMonth()
            (1..YEAR_HEATMAP_COLUMNS).map { day ->
                if (day <= daysInMonth) {
                    daysByDate[LocalDate.of(today.year, month, day)]
                } else {
                    null
                }
            }
        }
        return HeatmapLayout(weeks = weeks, monthLabels = monthLabels)
    }

    private fun buildAllTimeHeatmapLayout(
        days: List<ActivityDay>,
        zoneId: ZoneId,
        today: LocalDate,
    ): HeatmapLayout {
        val byMonth = days.groupBy { YearMonth.from(it.date) }
        val months = byMonth.keys.sorted()
        val monthLabels = months.map { ym ->
            val names = listOf(
                "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
                "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
            )
            "${names[ym.monthValue - 1]} ${ym.year % 100}"
        }
        val weeks = months.map { ym ->
            val daysByDayOfMonth = byMonth[ym].orEmpty().associateBy { it.date.dayOfMonth }
            val daysInMonth = ym.lengthOfMonth()
            (1..YEAR_HEATMAP_COLUMNS).map { day ->
                if (day <= daysInMonth) {
                    daysByDayOfMonth[day]
                } else {
                    null
                }
            }
        }
        return HeatmapLayout(weeks = weeks, monthLabels = monthLabels)
    }

    private fun heatmapDateRange(
        period: StatsPeriod,
        zoneId: ZoneId,
        today: LocalDate,
    ): List<LocalDate> = when (period) {
        StatsPeriod.Week -> {
            val start = today.with(weekFields.dayOfWeek(), 1L)
            (0..6).map { start.plusDays(it.toLong()) }
        }
        StatsPeriod.Month -> {
            val start = today.withDayOfMonth(1)
            (0 until start.lengthOfMonth()).map { start.plusDays(it.toLong()) }
        }
        StatsPeriod.Year -> {
            val start = today.withDayOfYear(1)
            (0 until start.lengthOfYear()).map { start.plusDays(it.toLong()) }
        }
        StatsPeriod.AllTime -> {
            val startMonth = YearMonth.from(today).minusMonths((ALL_TIME_HEATMAP_MONTHS - 1).toLong())
            val dates = mutableListOf<LocalDate>()
            var ym = startMonth
            val endYm = YearMonth.from(today)
            while (!ym.isAfter(endYm)) {
                var d = ym.atDay(1)
                val last = ym.atEndOfMonth()
                while (!d.isAfter(last)) {
                    dates.add(d)
                    d = d.plusDays(1)
                }
                ym = ym.plusMonths(1)
            }
            dates
        }
    }

    fun buildRecords(tracks: List<TrackSummary>, zoneId: ZoneId): List<StatsRecord> {
        if (tracks.isEmpty()) return emptyList()
        val longest = tracks.maxByOrNull { it.distanceMeters ?: 0.0 }
        val biggestClimb = tracks.maxByOrNull { it.totalClimbMeters ?: 0f }
        val fastestEligible = tracks.filter { (it.distanceMeters ?: 0.0) >= MOVING_SPEED_MIN_DISTANCE_KM * 1000 }
        val fastest = fastestEligible.maxByOrNull { it.averageSpeedKmh ?: 0f }
        val maxSpeedTrack = tracks.maxByOrNull { it.maxSpeedKmh ?: 0f }
        val mostActiveDay = tracks
            .groupBy { it.localDate(zoneId) }
            .maxByOrNull { (_, dayTracks) -> dayTracks.sumOf { it.distanceMeters ?: 0.0 } }

        return buildList {
            longest?.let { track ->
                add(
                    StatsRecord(
                        type = StatsRecordType.LongestRide,
                        titleKey = StatsRecordType.LongestRide,
                        valueLabel = formatDistanceKm(track.distanceMeters ?: 0.0),
                        dateLabel = formatDayMonth(track.localDate(zoneId)),
                        trackFileName = track.fileName,
                        dayDate = null,
                    ),
                )
            }
            biggestClimb?.takeIf { (it.totalClimbMeters ?: 0f) > 0f }?.let { track ->
                add(
                    StatsRecord(
                        type = StatsRecordType.BiggestClimb,
                        titleKey = StatsRecordType.BiggestClimb,
                        valueLabel = "${(track.totalClimbMeters ?: 0f).toInt()} м",
                        dateLabel = formatDayMonth(track.localDate(zoneId)),
                        trackFileName = track.fileName,
                        dayDate = null,
                    ),
                )
            }
            fastest?.let { track ->
                add(
                    StatsRecord(
                        type = StatsRecordType.HighestAvgSpeed,
                        titleKey = StatsRecordType.HighestAvgSpeed,
                        valueLabel = formatSpeedOneDecimal(track.averageSpeedKmh),
                        dateLabel = formatDayMonth(track.localDate(zoneId)),
                        trackFileName = track.fileName,
                        dayDate = null,
                    ),
                )
            }
            maxSpeedTrack?.takeIf { (it.maxSpeedKmh ?: 0f) > 0f }?.let { track ->
                add(
                    StatsRecord(
                        type = StatsRecordType.MaxSpeed,
                        titleKey = StatsRecordType.MaxSpeed,
                        valueLabel = formatSpeedOneDecimal(track.maxSpeedKmh),
                        dateLabel = formatDayMonth(track.localDate(zoneId)),
                        trackFileName = track.fileName,
                        dayDate = null,
                    ),
                )
            }
            mostActiveDay?.let { (date, dayTracks) ->
                val distance = dayTracks.sumOf { it.distanceMeters ?: 0.0 }
                add(
                    StatsRecord(
                        type = StatsRecordType.MostActiveDay,
                        titleKey = StatsRecordType.MostActiveDay,
                        valueLabel = formatDistanceKm(distance),
                        dateLabel = formatDayMonth(date),
                        trackFileName = null,
                        dayDate = date,
                    ),
                )
            }
        }
    }

    fun buildDistribution(tracks: List<TrackSummary>): List<DistanceDistributionBucket> {
        if (tracks.isEmpty()) return emptyList()
        val buckets = listOf(
            "До 20 км" to tracks.count { km(it) < 20.0 },
            "20–50 км" to tracks.count { km(it) in 20.0..<50.0 },
            "50–100 км" to tracks.count { km(it) in 50.0..<100.0 },
            "100+ км" to tracks.count { km(it) >= 100.0 },
        )
        val total = tracks.size
        return buckets.map { (label, count) ->
            val percent = if (total > 0) ((count * 100.0) / total).toInt() else 0
            DistanceDistributionBucket(
                label = label,
                rideCount = count,
                percent = percent,
                fraction = if (total > 0) count.toFloat() / total else 0f,
            )
        }
    }

    fun buildBestRides(tracks: List<TrackSummary>): BestRides {
        val longest = tracks.maxByOrNull { it.distanceMeters ?: 0.0 }
        val biggestClimb = tracks.maxByOrNull { it.totalClimbMeters ?: 0f }
        val fastest = tracks
            .filter { (it.distanceMeters ?: 0.0) >= MOVING_SPEED_MIN_DISTANCE_KM * 1000 }
            .maxByOrNull { it.averageSpeedKmh ?: 0f }
        return BestRides(longest = longest, fastest = fastest, biggestClimb = biggestClimb)
    }

    fun tracksForDay(
        allInPeriod: List<TrackSummary>,
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TrackSummary> =
        allInPeriod.filter { it.localDate(zoneId) == date }.sortedByDescending { it.startedAtMillis }

    private fun TrackSummary.localDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(startedAtMillis).atZone(zoneId).toLocalDate()

    private fun km(track: TrackSummary): Double = (track.distanceMeters ?: 0.0) / 1000.0

    private fun formatDayMonth(date: LocalDate): String {
        val months = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )
        return "${date.dayOfMonth} ${months[date.monthValue - 1]}"
    }

    private fun formatDistanceKm(meters: Double): String {
        val km = meters / 1000.0
        return if (km < 100.0) {
            String.format(Locale.getDefault(), "%.1f км", km)
        } else {
            String.format(Locale.getDefault(), "%.0f км", km)
        }
    }

    private fun formatSpeedOneDecimal(speed: Float?): String =
        speed?.let { String.format(Locale.getDefault(), "%.1f км/ч", it) } ?: "—"
}
