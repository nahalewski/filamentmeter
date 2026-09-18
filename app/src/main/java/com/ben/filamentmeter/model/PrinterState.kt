package com.ben.filamentmeter.model

data class AmsTray(
    val id: Int,
    val colorHex: String,
    val type: String,
    val subBrands: String = "",
    val active: Boolean = false
)

val defaultAmsTrays = listOf(
    AmsTray(id = 0, colorHex = "#2850E0", type = "PLA", active = false),
    AmsTray(id = 1, colorHex = "#F98C36", type = "PLA", active = false),
    AmsTray(id = 2, colorHex = "#161616", type = "PLA", active = false),
    AmsTray(id = 3, colorHex = "#161616", type = "PLA", active = false)
)

data class PrinterState(
    val connected: Boolean = false,
    val statusText: String = "Disconnected",
    val gcodeState: String = "IDLE",
    val progressPercent: Int = 0,
    val currentLayer: Int = 0,
    val totalLayers: Int = 0,
    val remainingMinutes: Int = 0,
    val jobName: String = "No active print",
    val nozzleTemp: Double = 0.0,
    val bedTemp: Double = 0.0,
    val chamberLight: Boolean = false,
    val cameraRecording: Boolean = false,
    val wifiSignal: String = "",
    val printError: Long = 0,
    val hmsCodes: List<String> = emptyList(),
    val issueText: String = "",
    val issueUrl: String = "https://wiki.bambulab.com/en/hms/home",
    val model: PrinterModel = PrinterModel.UNKNOWN,
    val amsDetected: Boolean = false,
    val amsUnits: List<AmsUnit> = emptyList(),
    val amsTrays: List<AmsTray> = emptyList(),
    val amsHumidity: String = "",
    val amsTemp: String = "",
    val activeTrayId: Int? = null
) {
    val printerName get() = if (model == PrinterModel.UNKNOWN) "Bambu Lab printer" else "Bambu Lab ${model.label}"
    val amsCountLabel get() = when {
        !connected -> "Accessories offline"
        !amsDetected -> "Detecting AMS units…"
        amsUnits.isEmpty() -> "No AMS connected"
        else -> "${amsUnits.size} AMS ${if (amsUnits.size == 1) "unit" else "units"} connected"
    }
    val amsDescription get() = when {
        !connected -> "Accessories offline"
        !amsDetected -> "Detecting accessories…"
        amsUnits.isEmpty() -> "No AMS connected"
        else -> amsUnits.groupingBy { it.model.label }.eachCount().entries.joinToString(" + ") {
            if (it.value > 1) "${it.value} × ${it.key}" else it.key
        }
    }
    val canResume get() = connected && gcodeState.equals("PAUSE", true)
    val canPause get() = connected && gcodeState.equals("RUNNING", true)
    val canStop get() = isPrinting
    val displayStatus get() = when {
        !connected -> "Offline"
        printError != 0L || gcodeState.equals("FAILED", true) -> "Error"
        canResume -> "Paused"
        isPrinting -> "Printing"
        gcodeState.equals("FINISH", true) -> "Completed"
        else -> "Idle"
    }
    val isPrinting: Boolean
        get() = connected && (gcodeState.equals("RUNNING", ignoreCase = true) ||
                gcodeState.equals("PREPARE", ignoreCase = true) ||
                gcodeState.equals("PAUSE", ignoreCase = true))
}
