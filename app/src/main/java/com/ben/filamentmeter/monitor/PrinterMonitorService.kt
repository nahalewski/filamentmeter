package com.ben.filamentmeter.monitor

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ben.filamentmeter.MainActivity
import com.ben.filamentmeter.R
import com.ben.filamentmeter.bambu.BambuMqttClient
import com.ben.filamentmeter.data.SettingsStore
import com.ben.filamentmeter.model.PrinterState
import com.ben.filamentmeter.widget.FilamentMeterWidgetProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import com.ben.filamentmeter.vision.CameraMonitor
import com.ben.filamentmeter.vision.CameraSupervisor

object PrinterMonitor {
    val fleetStates = MutableStateFlow<Map<String, PrinterState>>(emptyMap())
    val state = MutableStateFlow(PrinterState())
    val error = MutableStateFlow<String?>(null)
    var client: BambuMqttClient? = null
    fun enabled(context: Context) = context.getSharedPreferences("monitor", 0).getBoolean("enabled", true)
    fun start(context: Context) {
        context.getSharedPreferences("monitor", 0).edit().putBoolean("enabled", true).apply()
        ContextCompat.startForegroundService(context, Intent(context, PrinterMonitorService::class.java))
    }
    fun stop(context: Context) {
        context.getSharedPreferences("monitor", 0).edit().putBoolean("enabled", false).apply()
        context.stopService(Intent(context, PrinterMonitorService::class.java))
    }
}

class PrinterMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clients = mutableMapOf<String, BambuMqttClient>()
    private val configurations = mutableMapOf<String, com.ben.filamentmeter.model.AppSettings>()
    private val trackers = mutableMapOf<String, PrinterEvents>()
    private lateinit var fleet: com.ben.filamentmeter.data.FleetStore
    private lateinit var manager: NotificationManager
    private lateinit var store: SettingsStore
    private var lastRender = ""
    private var widgetAt = 0L
    private var alive = true
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var cameraSupervisor: CameraSupervisor? = null

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        fleet = com.ben.filamentmeter.data.FleetStore(this)
        IssueCatalog.load(this)
        manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("printer_progress", "Printer progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Quiet ongoing progress and connection status"
            setSound(null, null); enableVibration(false)
        })
        AlertNotifications.createChannels(this)
        startForeground(100, notification(PrinterMonitor.state.value, false))
        CameraMonitor.load(this)
        cameraSupervisor = CameraSupervisor(this,scope) { kind, warning ->
            manager.notify(102, AlertNotifications.build(this,kind,warning,store.load().serialNumber))
        }
        wakeLock = getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "FilamentMeter:printerMonitor")
            .apply { setReferenceCounted(false); acquire(10 * 60_000L) }
        scope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                wakeLock?.acquire(10 * 60_000L)
            }
        }
    }

    private fun createClient(id: String): BambuMqttClient = BambuMqttClient(onState = { reported -> scope.launch {
            if (!alive) return@launch
            val profile = fleet.profiles().find { it.id == id } ?: return@launch
            val state = if (reported.model == com.ben.filamentmeter.model.PrinterModel.UNKNOWN)
                reported.copy(model=com.ben.filamentmeter.model.PrinterModel.identify(profile.model,id)) else reported
            PrinterMonitor.fleetStates.value = PrinterMonitor.fleetStates.value + (id to state)
            fleet.observe(profile.settings,state)
            val active = store.load().serialNumber == id
            if (active) PrinterMonitor.state.value = state
            val event = trackers.getOrPut(id) { PrinterEvents() }.accept(state)
            val render = "${state.connected}|${state.statusText}|${state.gcodeState}|${state.progressPercent}|${state.remainingMinutes}|${state.jobName}|${state.issueText}"
            if (active && render != lastRender) {
                manager.notify(100, notification(state, false)); lastRender = render
            }
            val prefs = getSharedPreferences("monitor", 0)
            val oldIssue = prefs.getString("lastIssue_$id", "")
            if (state.connected) prefs.edit().putString("lastIssue_$id", state.issueText).apply()
            if (event != null && (event != "Printer needs attention" || oldIssue != state.issueText))
                manager.notify(id, 101, notification(state, true, "${profile.name}: $event", id))
            val now = android.os.SystemClock.elapsedRealtime()
            if (active && (event != null || now - widgetAt >= 5000 || !state.connected)) {
                widgetAt = now
                store.saveWidgetSnapshot(state, profile.settings)
                withContext(Dispatchers.IO) {
                    FilamentMeterWidgetProvider.refreshAll(this@PrinterMonitorService)
                }
            }
        } }, onError = { if (store.load().serialNumber == id) PrinterMonitor.error.value = it })

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val next = store.load()
        if (!PrinterMonitor.enabled(this) || next.printerIp.isBlank() || next.serialNumber.isBlank() || next.accessCode.isBlank()) {
            stopSelf(); return START_NOT_STICKY
        }
        for (profile in fleet.profiles()) {
            val config = profile.settings
            if (config.printerIp.isBlank() || config.accessCode.isBlank()) continue
            val client = clients.getOrPut(profile.id) { createClient(profile.id) }
            val old = configurations[profile.id]
            if (old == null || old.printerIp != config.printerIp || old.accessCode != config.accessCode) {
                trackers[profile.id]?.reset()
                configurations[profile.id] = config
                client.connect(config)
            }
        }
        PrinterMonitor.client = clients[next.serialNumber]
        PrinterMonitor.state.value = PrinterMonitor.fleetStates.value[next.serialNumber] ?: PrinterState()
        lastRender = ""
        manager.notify(100, notification(PrinterMonitor.state.value, false))
        store.saveWidgetSnapshot(PrinterMonitor.state.value,next)
        FilamentMeterWidgetProvider.refreshAll(this)
        cameraSupervisor?.update(next)
        return START_STICKY
    }

    private fun notification(state: PrinterState, alert: Boolean, event: String? = null, printerId: String = store.load().serialNumber): Notification {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java)
            .setData(android.net.Uri.parse("filamentmeter://printer/${android.net.Uri.encode(printerId)}"))
            .putExtra("printer_id",printerId)
            .putExtra("printer_command", "view").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = event ?: if (!state.connected) "Printer · Reconnecting" else "${state.printerName} · ${state.displayStatus}"
        val detail = if (!state.connected) "Waiting for the printer on your local network. Reconnecting automatically."
            else state.issueText.ifBlank { when {
                state.gcodeState.equals("PREPARE", true) -> "${state.jobName} · Preparing printer. Progress will appear when printing begins."
                event?.endsWith("Print started") == true -> "${state.jobName} · Print started. Follow the ongoing notification for live progress."
                state.canResume -> "${state.jobName} · Paused at ${state.progressPercent}%. Check the printer, then resume when ready."
                state.isPrinting -> "${state.jobName} · ${state.progressPercent}% · ${state.remainingMinutes} min remaining"
                state.gcodeState.equals("FINISH", true) -> "${state.jobName} · Print completed"
                state.gcodeState.equals("FAILED", true) -> "Print stopped or failed. Check the printer screen for details before restarting."
                else -> "Connected and ready. Monitoring in the background."
            } }
        return NotificationCompat.Builder(this, if (alert) AlertNotifications.EVENTS else "printer_progress")
            .setSmallIcon(R.drawable.ic_bambu_printer).setContentTitle(title).setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail)).setContentIntent(open)
            .setOnlyAlertOnce(!alert).setSilent(!alert).setOngoing(!alert).setAutoCancel(alert)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setShowWhen(alert)
            .apply {
                if (!alert && (state.isPrinting || state.gcodeState.equals("FINISH", true)))
                    setProgress(100, if (state.gcodeState.equals("FINISH", true)) 100 else state.progressPercent, state.gcodeState.equals("PREPARE", true))
                if (state.issueText.isNotBlank()) addAction(0, "Troubleshooting", PendingIntent.getActivity(this@PrinterMonitorService, 11,
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(state.issueUrl)), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }.build()
    }

    override fun onDestroy() {
        alive = false
        cameraSupervisor?.close()
        PrinterMonitor.client = null
        clients.values.forEach { it.disconnect() }
        clients.clear()
        PrinterMonitor.fleetStates.value = PrinterMonitor.fleetStates.value.mapValues { it.value.copy(connected=false,statusText="Disconnected") }
        PrinterMonitor.state.value = PrinterMonitor.state.value.copy(connected = false, statusText = "Disconnected")
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        store.saveWidgetSnapshot(PrinterMonitor.state.value, store.load())
        FilamentMeterWidgetProvider.refreshAll(this)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
