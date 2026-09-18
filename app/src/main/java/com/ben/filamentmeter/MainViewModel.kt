package com.ben.filamentmeter

import android.app.Application
import com.ben.filamentmeter.monitor.PrinterMonitor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ben.filamentmeter.bambu.BambuDiscovery
import com.ben.filamentmeter.bambu.BambuMqttClient
import com.ben.filamentmeter.bambu.DiscoveredPrinter
import com.ben.filamentmeter.data.SettingsStore
import com.ben.filamentmeter.model.AppSettings
import com.ben.filamentmeter.model.PrinterState
import com.ben.filamentmeter.widget.FilamentMeterWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data object Scanning : DiscoveryState
    data class Found(val printers: List<DiscoveredPrinter>) : DiscoveryState
    data object NotFound : DiscoveryState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val fleet = com.ben.filamentmeter.data.FleetStore(application)
    val fleetRevision = com.ben.filamentmeter.data.FleetStore.revision
    val fleetStates = PrinterMonitor.fleetStates
    fun selectPrinter(id: String) {
        val selected = fleet.profiles().find { it.id == id } ?: return
        PrinterMonitor.client = null
        com.ben.filamentmeter.vision.CameraMonitor.frame.value = com.ben.filamentmeter.vision.CameraFrame(message="Switching printer")
        com.ben.filamentmeter.vision.CameraMonitor.vision.value = com.ben.filamentmeter.vision.VisionStatus()
        PrinterMonitor.state.value = PrinterState(model = com.ben.filamentmeter.model.PrinterModel.identify(selected.model,id))
        store.save(selected.settings)
        _settings.value = selected.settings
        connect()
    }
    private val store = SettingsStore(application)
    private val appContext = application.applicationContext

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _printer = PrinterMonitor.state
    val printer: StateFlow<PrinterState> = _printer.asStateFlow()

    private val _error = PrinterMonitor.error
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        fleet.profiles()
        autoConnectIfConfigured()
    }

    private fun autoConnectIfConfigured() {
        val current = _settings.value
        if (PrinterMonitor.enabled(appContext) && current.printerIp.isNotBlank() && current.serialNumber.isNotBlank() && current.accessCode.isNotBlank()) {
            connect()
        }
    }

    fun saveSettings(settings: AppSettings, name: String? = null, model: String? = null) {
        val safe = settings.copy(
            printerIp = settings.printerIp.trim(),
            serialNumber = settings.serialNumber.trim().uppercase(),
            accessCode = settings.accessCode.trim(),
            spoolPrice = settings.spoolPrice.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
            spoolWeightGrams = settings.spoolWeightGrams.takeIf { it.isFinite() }?.coerceAtLeast(1.0) ?: 1000.0,
            jobFilamentGrams = settings.jobFilamentGrams.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
            purgingWasteGrams = settings.purgingWasteGrams.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
            failedPrintWasteGrams = settings.failedPrintWasteGrams.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
            scrapWasteGrams = settings.scrapWasteGrams.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        )
        if (safe.serialNumber.isBlank() || safe.printerIp.isBlank() || safe.accessCode.isBlank()) {
            _error.value = "Enter the printer IP, serial number and LAN access code before saving."; return
        }
        if (_settings.value.serialNumber != safe.serialNumber) {
            PrinterMonitor.client = null
            PrinterMonitor.state.value = PrinterState()
        }
        fleet.save(safe,name,model)
        store.save(safe)
        _settings.value = safe
        store.saveWidgetSnapshot(_printer.value, safe)
        viewModelScope.launch(Dispatchers.IO) {
            FilamentMeterWidgetProvider.refreshAll(appContext)
        }
        if (safe.printerIp.isNotBlank() && safe.serialNumber.isNotBlank() && safe.accessCode.isNotBlank()) {
            connect()
        }
    }

    fun connect() {
        _error.value = null
        PrinterMonitor.start(appContext)
    }

    fun disconnect() {
        PrinterMonitor.stop(appContext)
    }

    fun clearError() {
        _error.value = null
    }

    fun printCommand(command: String) { PrinterMonitor.client?.printCommand(command) ?: run { _error.value = "Connect to the printer first." } }
    fun setLight(on: Boolean) { PrinterMonitor.client?.setLight(on) }
    fun setRecording(on: Boolean) { PrinterMonitor.client?.setRecording(on) }

    fun startDiscovery() {
        discoveryJob?.cancel()
        _discoveryState.value = DiscoveryState.Scanning
        discoveryJob = viewModelScope.launch {
            val printers = BambuDiscovery.discoverAll(appContext, timeoutMs = 10000)
            _discoveryState.value = if (printers.isNotEmpty()) {
                DiscoveryState.Found(printers)
            } else {
                DiscoveryState.NotFound
            }
        }
    }

    fun cancelDiscovery() {
        discoveryJob?.cancel()
        _discoveryState.value = DiscoveryState.Idle
    }

    fun resetDiscovery() {
        _discoveryState.value = DiscoveryState.Idle
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        super.onCleared()
    }
}
