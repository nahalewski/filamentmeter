package com.ben.filamentmeter.data

import android.content.Context
import com.ben.filamentmeter.model.AppSettings
import com.ben.filamentmeter.model.PrinterState

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("filament_meter_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        printerIp = prefs.getString("printer_ip", "") ?: "",
        serialNumber = prefs.getString("serial", "") ?: "",
        accessCode = prefs.getString("access_code", "") ?: "",
        spoolPrice = prefs.getString("spool_price", "24.99")?.toDoubleOrNull() ?: 24.99,
        spoolWeightGrams = prefs.getString("spool_weight", "1000")?.toDoubleOrNull() ?: 1000.0,
        jobFilamentGrams = prefs.getString("job_grams", "100")?.toDoubleOrNull() ?: 100.0,
        purgingWasteGrams = prefs.getString("waste_purging_grams", "0")?.toDoubleOrNull() ?: 0.0,
        failedPrintWasteGrams = prefs.getString("waste_failed_print_grams", "0")?.toDoubleOrNull() ?: 0.0,
        scrapWasteGrams = prefs.getString("waste_scrap_grams", "0")?.toDoubleOrNull() ?: 0.0
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("printer_ip", settings.printerIp.trim())
            .putString("serial", settings.serialNumber.trim())
            .putString("access_code", settings.accessCode.trim())
            .putString("spool_price", settings.spoolPrice.toString())
            .putString("spool_weight", settings.spoolWeightGrams.toString())
            .putString("job_grams", settings.jobFilamentGrams.toString())
            .putString("waste_purging_grams", settings.purgingWasteGrams.toString())
            .putString("waste_failed_print_grams", settings.failedPrintWasteGrams.toString())
            .putString("waste_scrap_grams", settings.scrapWasteGrams.toString())
            .apply()
    }

    fun saveWidgetSnapshot(printer: PrinterState, settings: AppSettings) {
        val completed = printer.connected && printer.gcodeState.equals("FINISH", true)
        val isRunning = printer.isPrinting || completed
        val progress = if (completed) 1.0 else if (isRunning) printer.progressPercent.coerceIn(0, 100) / 100.0 else 0.0
        val gramsUsed = if (isRunning) settings.jobFilamentGrams * progress else 0.0
        val cost = if (isRunning) gramsUsed * settings.pricePerGram else 0.0
        val percent = if (completed) 100 else if (isRunning) printer.progressPercent.coerceIn(0, 100) else 0
        val jobName = if (isRunning) {
            printer.jobName
        } else if (printer.connected) {
            if (printer.gcodeState.equals("FINISH", true)) "Print Finished (Idle)" else "Printer Idle"
        } else {
            "No active print"
        }
        val trayColorsStr = printer.amsTrays.joinToString(",") { it.colorHex }

        prefs.edit()
            .putBoolean("widget_connected", printer.connected)
            .putString("widget_model", printer.model.name)
            .putString("widget_ams_models", printer.amsUnits.joinToString(",") { it.model.name })
            .putString("widget_hardware_label", "${printer.model.label} · ${printer.amsCountLabel} · ${printer.amsDescription}")
            .putString("widget_job", jobName)
            .putInt("widget_percent", percent)
            .putInt("widget_layer", if (isRunning) printer.currentLayer else 0)
            .putInt("widget_total_layers", if (isRunning) printer.totalLayers else 0)
            .putString("widget_grams", gramsUsed.toString())
            .putString("widget_cost", cost.toString())
            .putString("widget_tray_colors", trayColorsStr)
            .putInt("widget_active_tray", if (isRunning) (printer.activeTrayId ?: -1) else -1)
            .apply()
    }

    fun widgetSnapshot(): WidgetSnapshot {
        val colorsRaw = prefs.getString("widget_tray_colors", "") ?: ""
        val trayColors = if (colorsRaw.isNotBlank()) colorsRaw.split(",") else emptyList()
        val activeTray = prefs.getInt("widget_active_tray", -1)

        return WidgetSnapshot(
            model = prefs.getString("widget_model", "UNKNOWN") ?: "UNKNOWN",
            amsModels = (prefs.getString("widget_ams_models", "") ?: "").split(",").filter { it.isNotBlank() },
            hardwareLabel = prefs.getString("widget_hardware_label", "Hardware not identified") ?: "Hardware not identified",
            connected = prefs.getBoolean("widget_connected", false),
            jobName = prefs.getString("widget_job", "No active print") ?: "No active print",
            percent = prefs.getInt("widget_percent", 0),
            currentLayer = prefs.getInt("widget_layer", 0),
            totalLayers = prefs.getInt("widget_total_layers", 0),
            gramsUsed = prefs.getString("widget_grams", "0")?.toDoubleOrNull() ?: 0.0,
            cost = prefs.getString("widget_cost", "0")?.toDoubleOrNull() ?: 0.0,
            trayColors = trayColors,
            activeTrayId = if (activeTray in 0..3) activeTray else null
        )
    }
}

data class WidgetSnapshot(
    val model: String = "UNKNOWN",
    val amsModels: List<String> = emptyList(),
    val hardwareLabel: String = "Hardware not identified",
    val connected: Boolean,
    val jobName: String,
    val percent: Int,
    val currentLayer: Int,
    val totalLayers: Int,
    val gramsUsed: Double,
    val cost: Double,
    val trayColors: List<String> = emptyList(),
    val activeTrayId: Int? = null
)
