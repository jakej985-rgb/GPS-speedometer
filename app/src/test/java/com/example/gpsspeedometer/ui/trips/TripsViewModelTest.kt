package com.example.gpsspeedometer.ui.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class TripsViewModelTest {

    @Test
    fun `test escapeCsv with normal text`() {
        val result = TripsViewModel.escapeCsv("Road Trip")
        assertEquals("Road Trip", result)
    }

    @Test
    fun `test escapeCsv with commas`() {
        val result = TripsViewModel.escapeCsv("Trip to Austin, TX")
        assertEquals("\"Trip to Austin, TX\"", result)
    }

    @Test
    fun `test escapeCsv with quotes`() {
        val result = TripsViewModel.escapeCsv("Trip \"Fast & Furious\"")
        assertEquals("\"Trip \"\"Fast & Furious\"\"\"", result)
    }

    @Test
    fun `test escapeCsv with newlines`() {
        val result = TripsViewModel.escapeCsv("Multi\nLine")
        assertEquals("\"Multi\nLine\"", result)
    }
}
