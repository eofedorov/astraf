package com.astraf.hrgpslogger.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astraf.hrgpslogger.RobolectricTestApp
import com.astraf.hrgpslogger.TrackSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    application = RobolectricTestApp::class,
    instrumentedPackages = ["androidx.loader.content"],
    sdk = [36],
)
class TrackCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trackCard_displaysCorrectMetrics() {
        val track = TrackSummary(
            fileName = "test.csv",
            filePath = "/test.csv",
            startedAtMillis = 1684755300000L, // Some date
            pointCount = 100,
            isActive = false,
            distanceMeters = 40560.0,
            durationMillis = 4421000L, // 1:13:41
            movingTimeMillis = 4_000_000L,
            averageSpeedKmh = 34.6f,
            maxSpeedKmh = 57.9f,
            averageHeartRateBpm = 148,
            totalClimbMeters = 76f,
            routePoints = emptyList(),
            displayName = null,
            stravaActivityId = null,
        )

        composeTestRule.setContent {
            TrackCard(
                track = track,
                onOpen = {},
                onLongPress = {},
                onMenuClick = {},
            )
        }

        val distanceText = "${com.astraf.hrgpslogger.ui.formatDistanceNumber(40560.0)} ${com.astraf.hrgpslogger.ui.formatDistanceUnit(40560.0)}"
        composeTestRule.onNodeWithText(distanceText).assertIsDisplayed()

        val durationText = com.astraf.hrgpslogger.ui.formatDuration(4421000L)
        composeTestRule.onNodeWithText(durationText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Длительность").assertIsDisplayed()

        val avgSpeedText = com.astraf.hrgpslogger.ui.formatSpeedKmh(34.6f)
        composeTestRule.onNodeWithText(avgSpeedText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ср. скорость").assertIsDisplayed()

        val elevationText = com.astraf.hrgpslogger.ui.formatListElevationMeters(76f)
        composeTestRule.onNodeWithText(elevationText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Набор высоты").assertIsDisplayed()

        val maxSpeedText = "${com.astraf.hrgpslogger.ui.formatSpeedKmhNumber(57.9f)} км/ч"
        composeTestRule.onNodeWithText(maxSpeedText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Макс. скорость").assertIsDisplayed()

        val hrText = com.astraf.hrgpslogger.ui.formatHeartRateBpm(148)
        composeTestRule.onNodeWithText(hrText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ср. пульс").assertIsDisplayed()
    }
}