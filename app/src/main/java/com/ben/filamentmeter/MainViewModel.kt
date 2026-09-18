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
    data class Found(val printer: DiscoveredPrinter) : DiscoveryState
    data object NotFound : DiscoveryState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
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
        autoConnectIfConfigured()
    }

    private fun autoConnectIfConfigured() {
        val current = _settings.value
        if (PrinterMonitor.enabled(appContext) && current.printerIp.isNotBlank() && current.serialNumber.isNotBlank() && current.accessCode.isNotBlank()) {
            connect()
        }
    }

    fun saveSettings(settings: AppSettings) {
        val safe = settings.copy(
            printerIp = settings.printerIp.trim(),
            serialNumber = settings.serialNumber.trim(),
            accessCode = settings.accessCode.trim(),
            spoolPrice = settings.spoolPrice.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
            spoolWeightGrams = settings.spoolWeightGrams.takeIf { it.isFinite() }?.coerceAtLeast(1.0) ?: 1000.0,
            jobFilamentGrams = settings.jobFilamentGrams.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        )
        store.save(safe)
        _settings.value = safe
        viewModelScope.launch(Dispatchers.IO) {
            store.saveWidgetSnapshot(_printer.value, safe)
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
            val printer = BambuDiscovery.discover(appContext, timeoutMs = 6000)
            _discoveryState.value = if (printer != null) {
                DiscoveryState.Found(printer)
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
