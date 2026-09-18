package com.ben.filamentmeter.model

data class AppSettings(
    val printerIp: String = "",
    val serialNumber: String = "",
    val accessCode: String = "",
    val spoolPrice: Double = 24.99,
    val spoolWeightGrams: Double = 1000.0,
    val jobFilamentGrams: Double = 100.0,
    val purgingWasteGrams: Double = 0.0,
    val failedPrintWasteGrams: Double = 0.0,
    val scrapWasteGrams: Double = 0.0
) {
    val pricePerGram: Double
        get() = if (spoolWeightGrams > 0.0) spoolPrice / spoolWeightGrams else 0.0

    val estimatedJobCost: Double
        get() = jobFilamentGrams * pricePerGram

    val totalWasteGrams get() = purgingWasteGrams + failedPrintWasteGrams + scrapWasteGrams
    val purgingWasteCost get() = purgingWasteGrams * pricePerGram
    val failedPrintWasteCost get() = failedPrintWasteGrams * pricePerGram
    val scrapWasteCost get() = scrapWasteGrams * pricePerGram
    val totalWasteCost get() = totalWasteGrams * pricePerGram
}
