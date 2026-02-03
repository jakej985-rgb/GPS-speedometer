package com.example.gpsspeedometer.logic

import com.example.gpsspeedometer.data.AppSettings
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.data.TripDao
import com.example.gpsspeedometer.data.TripEntity
import com.example.gpsspeedometer.domain.LocationSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TripManagerTest {

    private lateinit var tripManager: TripManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var tripDao: TripDao
    private val testScope = TestScope(UnconfinedTestDispatcher())

    @Before
    fun setup() {
        settingsRepository = mock()
        tripDao = mock()

        val settingsFlow = MutableStateFlow(AppSettings(
            accuracyThresholdMeters = 25f,
            minSegmentMeters = 3f,
            emaAlpha = 0.5f, // Use 0.5 for easier calculation
            historyWindowSamples = 10
        ))
        whenever(settingsRepository.settingsFlow).thenReturn(settingsFlow)

        tripManager = TripManager(settingsRepository, tripDao, testScope)
    }

    @Test
    fun `test speed smoothing`() = testScope.runTest {
        // Initial speed 0
        // Input speed 10 mps (~22.3 mph)
        // alpha 0.5
        // Smoothed = 0.5 * 22.3 + 0.5 * 0 = 11.15

        val location = LocationSample(
            timestamp = 1000,
            lat = 0.0,
            lng = 0.0,
            speedMps = 10f,
            accuracyMeters = 5f,
            bearing = 0f
        )

        tripManager.processLocation(location)

        val state = tripManager.tripState.value
        assertEquals(11.18f, state.currentSpeedMph, 0.1f) // 10 mps * 2.23694 = 22.3694. 22.3694 * 0.5 = 11.1847
    }

    @Test
    fun `test distance accumulation`() = testScope.runTest {
        // Mock start trip
        whenever(tripDao.insertTrip(any())).thenReturn(1L)
        tripManager.startTrip()

        // Point 1
        val loc1 = LocationSample(1000, 0.0, 0.0, 10f, 5f, 0f)
        tripManager.processLocation(loc1)

        // Point 2 (Valid segment)
        // Move 10 meters approx (0.0001 deg lat ~ 11m)
        val loc2 = LocationSample(2000, 0.0001, 0.0, 10f, 5f, 0f)
        tripManager.processLocation(loc2)

        val state = tripManager.tripState.value
        assert(state.distanceMeters > 0f)
    }
}
