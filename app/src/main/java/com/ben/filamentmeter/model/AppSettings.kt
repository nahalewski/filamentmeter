package com.ben.filamentmeter.model

data class AppSettings(
    val printerIp: String = "",
    val serialNumber: String = "",
    val accessCode: String = "",
    val spoolPrice: Double = 24.99,
    val spoolWeightGrams: Double = 1000.0,
    val jobFilamentGrams: Double = 100.0
) {
    val pricePerGram: Double
        get() = if (spoolWeightGrams > 0.0) spoolPrice / spoolWeightGrams else 0.0

    val estimatedJobCost: Double
        get() = jobFilamentGrams * pricePerGram
}
