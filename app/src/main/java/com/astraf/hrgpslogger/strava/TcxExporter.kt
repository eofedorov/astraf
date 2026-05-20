package com.astraf.hrgpslogger.strava

import com.astraf.hrgpslogger.GeoDistance
import com.astraf.hrgpslogger.TrackCsvSample
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TcxExporter {

    private val tcxTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    fun externalIdForCsv(fileName: String): String =
        fileName.removeSuffix(".csv") + ".tcx"

    fun exportToFile(samples: List<TrackCsvSample>, outputFile: File) {
        require(samples.isNotEmpty()) { "Track has no points" }
        outputFile.parentFile?.mkdirs()
        BufferedWriter(
            OutputStreamWriter(FileOutputStream(outputFile, false), StandardCharsets.UTF_8),
        ).use { writer ->
            writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.newLine()
            writer.write(
                """<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""",
            )
            writer.newLine()
            writer.write("  <Activities>")
            writer.newLine()
            writer.write("""    <Activity Sport="Biking">""")
            writer.newLine()
            val startInstant = Instant.ofEpochMilli(samples.first().point.timestampMillis)
            writer.write("      <Id>${tcxTimeFormatter.format(startInstant)}</Id>")
            writer.newLine()
            writer.write("      <Lap StartTime=\"${tcxTimeFormatter.format(startInstant)}\">")
            writer.newLine()

            val totalSeconds = (
                samples.last().point.timestampMillis - samples.first().point.timestampMillis
                ).coerceAtLeast(0L) / 1000.0
            val totalDistance = cumulativeDistanceMeters(samples)
            writer.write("        <TotalTimeSeconds>$totalSeconds</TotalTimeSeconds>")
            writer.newLine()
            writer.write("        <DistanceMeters>$totalDistance</DistanceMeters>")
            writer.newLine()
            writer.write("        <Track>")
            writer.newLine()

            var distanceMeters = 0.0
            samples.forEachIndexed { index, sample ->
                if (index > 0) {
                    val previous = samples[index - 1]
                    if (previous.point.segmentId == sample.point.segmentId) {
                        distanceMeters += GeoDistance.distanceMeters(previous.point, sample.point)
                    }
                }
                writeTrackpoint(writer, sample, distanceMeters)
            }

            writer.write("        </Track>")
            writer.newLine()
            writer.write("      </Lap>")
            writer.newLine()
            writer.write("    </Activity>")
            writer.newLine()
            writer.write("  </Activities>")
            writer.newLine()
            writer.write("</TrainingCenterDatabase>")
            writer.newLine()
        }
    }

    fun cumulativeDistanceMeters(samples: List<TrackCsvSample>): Double {
        var total = 0.0
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            if (previous.point.segmentId == current.point.segmentId) {
                total += GeoDistance.distanceMeters(previous.point, current.point)
            }
        }
        return total
    }

    private fun writeTrackpoint(
        writer: BufferedWriter,
        sample: TrackCsvSample,
        distanceMeters: Double,
    ) {
        val point = sample.point
        val time = tcxTimeFormatter.format(Instant.ofEpochMilli(point.timestampMillis))
        writer.write("          <Trackpoint>")
        writer.newLine()
        writer.write("            <Time>$time</Time>")
        writer.newLine()
        writer.write("            <Position>")
        writer.newLine()
        writer.write("              <LatitudeDegrees>${point.latitude}</LatitudeDegrees>")
        writer.newLine()
        writer.write("              <LongitudeDegrees>${point.longitude}</LongitudeDegrees>")
        writer.newLine()
        writer.write("            </Position>")
        writer.newLine()
        writer.write("            <DistanceMeters>$distanceMeters</DistanceMeters>")
        writer.newLine()
        sample.bpm?.let { bpm ->
            writer.write("            <HeartRateBpm>")
            writer.newLine()
            writer.write("              <Value>$bpm</Value>")
            writer.newLine()
            writer.write("            </HeartRateBpm>")
            writer.newLine()
        }
        writer.write("          </Trackpoint>")
        writer.newLine()
    }
}
