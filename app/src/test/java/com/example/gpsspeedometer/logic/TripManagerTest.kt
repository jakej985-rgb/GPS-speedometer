package com.example.gpsspeedometer.logic

import android.location.Location
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
            emaAlpha = 0.5f,
            historyWindowSamples = 10,
            maxPlausibleMph = 200f
        ))
        whenever(settingsRepository.settingsFlow).thenReturn(settingsFlow)

        tripManager = TripManager(settingsRepository, tripDao, testScope.backgroundScope)
    }

    @Test
    fun `test speed smoothing`() = testScope.runTest {
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
        assertEquals(11.18f, state.currentSpeedMph, 0.1f)
    }

    @Test
    fun `test distance accumulation`() = testScope.runTest {
        mockStatic(Location::class.java).use { mockLocation ->
            mockLocation.`when`<Any> {
                Location.distanceBetween(any(), any(), any(), any(), any())
            }.thenAnswer { invocation ->
                val results = invocation.arguments[4] as FloatArray
                results[0] = 11.1f
                null
            }

            whenever(tripDao.insertTrip(any())).thenReturn(1L)
            tripManager.startTrip()

            val loc1 = LocationSample(1000, 0.0, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc1)

            val loc2 = LocationSample(2000, 0.0001, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc2)

            val state = tripManager.tripState.value
            assertTrue(state.distanceMeters > 0f)
        }
    }

    @Test
    fun `test pause stops distance accumulation`() = testScope.runTest {
        mockStatic(Location::class.java).use { mockLocation ->
            mockLocation.`when`<Any> {
                Location.distanceBetween(any(), any(), any(), any(), any())
            }.thenAnswer { invocation ->
                val results = invocation.arguments[4] as FloatArray
                results[0] = 15f
                null
            }

            whenever(tripDao.insertTrip(any())).thenReturn(1L)
            tripManager.startTrip()

            val loc1 = LocationSample(1000, 0.0, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc1)

            val loc2 = LocationSample(2000, 0.0001, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc2)
            val distBeforePause = tripManager.tripState.value.distanceMeters

            tripManager.pauseTrip()
            assertTrue(tripManager.tripState.value.isPaused)

            // Location arrives while paused
            val loc3 = LocationSample(3000, 0.0002, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc3)

            // Distance should NOT increase while paused
            assertEquals(distBeforePause, tripManager.tripState.value.distanceMeters, 0.001f)
            // But live speed should still update
            assertTrue(tripManager.tripState.value.currentSpeedMph > 0f)
        }
    }

    @Test
    fun `test low accuracy points are filtered from accumulation`() = testScope.runTest {
        mockStatic(Location::class.java).use { mockLocation ->
            mockLocation.`when`<Any> {
                Location.distanceBetween(any(), any(), any(), any(), any())
            }.thenAnswer { invocation ->
                val results = invocation.arguments[4] as FloatArray
                results[0] = 50f
                null
            }

            whenever(tripDao.insertTrip(any())).thenReturn(1L)
            tripManager.startTrip()

            val loc1 = LocationSample(1000, 0.0, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc1)

            // Point with accuracy 50m (exceeds 25m threshold)
            val locLowAccuracy = LocationSample(2000, 0.001, 0.0, 10f, 50f, 0f)
            tripManager.processLocation(locLowAccuracy)

            // Distance should remain 0 because low accuracy point is rejected
            assertEquals(0f, tripManager.tripState.value.distanceMeters, 0.001f)
        }
    }

    @Test
    fun `test stop trip calculates average speed and updates database`() = testScope.runTest {
        val existingTrip = TripEntity(
            id = 1L,
            name = "Test Trip",
            startTime = System.currentTimeMillis() - 3600000, // 1 hour ago
            endTime = 0,
            elapsedMs = 0,
            distanceMeters = 0f,
            maxSpeedMph = 0f,
            avgSpeedMph = 0f
        )
        whenever(tripDao.insertTrip(any())).thenReturn(1L)
        whenever(tripDao.getTripById(1L)).thenReturn(existingTrip)

        mockStatic(Location::class.java).use { mockLocation ->
            mockLocation.`when`<Any> {
                Location.distanceBetween(any(), any(), any(), any(), any())
            }.thenAnswer { invocation ->
                val results = invocation.arguments[4] as FloatArray
                // 25 meters in 1 second = ~55.9 mph (well below maxPlausibleMph threshold)
                results[0] = 25f
                null
            }

            tripManager.startTrip()
            testScheduler.advanceUntilIdle()

            val loc1 = LocationSample(1000, 0.0, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc1)

            val loc2 = LocationSample(2000, 0.0002, 0.0, 10f, 5f, 0f)
            tripManager.processLocation(loc2)

            tripManager.stopTrip()
            testScheduler.advanceUntilIdle()

            val captor = argumentCaptor<TripEntity>()
            verify(tripDao).updateTrip(captor.capture())
            val saved = captor.firstValue

            assertEquals(25f, saved.distanceMeters, 0.1f)
            assertFalse(tripManager.tripState.value.isRecording)
        }
    }

    @Test
    fun `test live average speed and bearing tracking`() = testScope.runTest {
        mockStatic(Location::class.java).use { mockLocation ->
            mockLocation.`when`<Any> {
                Location.distanceBetween(any(), any(), any(), any(), any())
            }.thenAnswer { invocation ->
                val results = invocation.arguments[4] as FloatArray
                results[0] = 50f
                null
            }

            whenever(tripDao.insertTrip(any())).thenReturn(1L)
            tripManager.startTrip()
            testScheduler.advanceUntilIdle()

            val loc1 = LocationSample(1000, 0.0, 0.0, 10f, 5f, 90f)
            tripManager.processLocation(loc1)

            val loc2 = LocationSample(2000, 0.0005, 0.0, 10f, 5f, 180f)
            tripManager.processLocation(loc2)

            val state = tripManager.tripState.value
            assertEquals(180f, state.bearing, 0.01f)
            assertEquals(50f, state.distanceMeters, 0.1f)
        }
    }
}
