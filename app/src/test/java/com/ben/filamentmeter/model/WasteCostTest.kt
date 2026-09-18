package com.ben.filamentmeter.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WasteCostTest {
    @Test fun wasteCategoriesTotalWithoutChangingPrintEstimate() {
        val settings = AppSettings(spoolPrice = 20.0, spoolWeightGrams = 1000.0,
            jobFilamentGrams = 100.0, purgingWasteGrams = 10.0,
            failedPrintWasteGrams = 50.0, scrapWasteGrams = 15.0)
        assertEquals(0.20, settings.purgingWasteCost, 0.00001)
        assertEquals(1.0, settings.failedPrintWasteCost, 0.00001)
        assertEquals(0.30, settings.scrapWasteCost, 0.00001)
        assertEquals(75.0, settings.totalWasteGrams, 0.00001)
        assertEquals(1.50, settings.totalWasteCost, 0.00001)
        assertEquals(2.0, settings.estimatedJobCost, 0.00001)
        assertEquals(3.0, settings.copy(spoolPrice = 40.0).totalWasteCost, 0.00001)
    }
    @Test fun existingSettingsDefaultToNoWasteAndZeroWeightIsSafe() {
        assertEquals(0.0, AppSettings().totalWasteCost, 0.0)
        assertEquals(0.0, AppSettings(spoolWeightGrams = 0.0, scrapWasteGrams = 5.0).totalWasteCost, 0.0)
    }
}
