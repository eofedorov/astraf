package com.astraf.hrgpslogger

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object GpxExporter {

    private val gpxTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun fileNameForCsv(csvFileName: String): String =
        csvFileName.removeSuffix(".csv") + ".gpx"

    fun exportToFile(samples: List<TrackCsvSample>, outputFile: File) {
        require(samples.isNotEmpty()) { "Track has no points" }
        outputFile.parentFile?.mkdirs()
        BufferedWriter(
            OutputStreamWriter(FileOutputStream(outputFile, false), StandardCharsets.UTF_8),
        ).use { writer ->
            writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.newLine()
            writer.write(
                """<gpx version="1.1" creator="HR GPS Logger" xmlns="http://www.topografix.com/GPX/1/1">""",
            )
            writer.newLine()
            writer.write("  <trk>")
            writer.newLine()
            writer.write("    <name>Ride</name>")
            writer.newLine()
            writer.write("    <trkseg>")
            writer.newLine()

            var currentSegmentId: Int? = null
            samples.forEach { sample ->
                val point = sample.point
                if (currentSegmentId != null && point.segmentId != currentSegmentId) {
                    writer.write("    </trkseg>")
                    writer.newLine()
                    writer.write("    <trkseg>")
                    writer.newLine()
                }
                currentSegmentId = point.segmentId
                writeTrackPoint(writer, sample)
            }

            writer.write("    </trkseg>")
            writer.newLine()
            writer.write("  </trk>")
            writer.newLine()
            writer.write("</gpx>")
            writer.newLine()
        }
    }

    private fun writeTrackPoint(writer: BufferedWriter, sample: TrackCsvSample) {
        val point = sample.point
        val time = gpxTimeFormatter.format(Instant.ofEpochMilli(point.timestampMillis))
        writer.write("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
        writer.newLine()
        point.altitudeMeters?.let { altitude ->
            writer.write("        <ele>$altitude</ele>")
            writer.newLine()
        }
        writer.write("        <time>$time</time>")
        writer.newLine()
        sample.bpm?.let { bpm ->
            writer.write(
                "        <extensions><hr>$bpm</hr></extensions>",
            )
            writer.newLine()
        }
        writer.write("      </trkpt>")
        writer.newLine()
    }
}
