package com.ben.filamentmeter.model

enum class PrinterModel(val label: String) {
    P1S("P1S"), P2S("P2S"), X1C("X1 Carbon"), A1_MINI("A1 mini"),
    P1P("P1P"), X1("X1"), X1E("X1E"), A1("A1"), UNKNOWN("Unknown model");

    companion object {
        fun identify(model: String = "", serial: String = ""): PrinterModel {
            val normalized = model.uppercase().removePrefix("BAMBU LAB ").replace(" ", "").replace("_", "")
            return when (normalized) {
                "P1S", "C12" -> P1S
                "P2S", "C13" -> P2S
                "X1C", "X1CARBON", "BLP001" -> X1C
                "A1MINI", "N1" -> A1_MINI
                "P1P", "C11" -> P1P
                "X1", "BLP002" -> X1
                "X1E", "C11S" -> X1E
                "A1", "N2S" -> A1
                else -> when (serial.trim().take(3).uppercase()) {
                    "01P" -> P1S; "22E" -> P2S; "00M" -> X1C; "030" -> A1_MINI
                    "01S" -> P1P; "00W" -> X1; "03W" -> X1E; "039" -> A1
                    else -> UNKNOWN
                }
            }
        }
    }
}

enum class AmsModel(val label: String) {
    AMS("AMS"), AMS2("AMS 2 Pro"), LITE("AMS Lite"), HT("AMS HT"), UNKNOWN("AMS · identifying");

    companion object {
        fun identify(module: String = "", serial: String = ""): AmsModel = when {
            serial.startsWith("19C", true) || module.startsWith("n3f/", true) -> AMS2
            serial.startsWith("19F", true) || module.startsWith("n3s/", true) -> HT
            serial.startsWith("03C", true) || module.startsWith("ams_f1/", true) -> LITE
            serial.startsWith("006", true) || module.startsWith("ams/", true) -> AMS
            else -> UNKNOWN
        }
    }
}

data class AmsUnit(
    val id: Int,
    val model: AmsModel = AmsModel.UNKNOWN,
    val trays: List<AmsTray> = emptyList(),
    val humidity: String = "",
    val humidityPercent: Int? = null,
    val temperature: String = ""
) {
    val displayName get() = "${model.label} ${if (id >= 128) id - 127 else id + 1}"
    val isActive get() = trays.any { it.active }
    val humidityLabel: String
        get() {
            // BambuStudio AMSItem.cpp uses index 5 for dry and index 1 for wet.
            // AMS 2 Pro/HT report humidity_raw as percent, not an A–E grade.
            if (model == AmsModel.AMS2 || model == AmsModel.HT) {
                return humidityPercent?.let { "$it% RH" } ?: "Unknown"
            }
            return when (humidity.trim().uppercase()) {
            "5", "A" -> "A · Dry"
            "4", "B" -> "B"
            "3", "C" -> "C"
            "2", "D" -> "D"
            "1", "E" -> "E · Wet"
            else -> "Unknown"
        }
        }
}
