package com.astraf.hrgpslogger

import com.astraf.hrgpslogger.ui.components.xAxisLabelIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsBarChartTest {

    @Test
    fun xAxisLabelIndices_month_includesFirstAndLast() {
        val indices = xAxisLabelIndices(31)
        assertEquals(0, indices.first())
        assertEquals(30, indices.last())
        assertTrue(indices.contains(0))
        assertTrue(indices.contains(5))
        assertTrue(indices.contains(10))
    }

    @Test
    fun xAxisLabelIndices_week_showsAll() {
        assertEquals((0..6).toList(), xAxisLabelIndices(7))
    }
}
