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

object PrinterMonitor {
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
    private lateinit var mqtt: BambuMqttClient
    private lateinit var manager: NotificationManager
    private lateinit var store: SettingsStore
    private var settings: com.ben.filamentmeter.model.AppSettings? = null
    private val events = PrinterEvents()
    private var lastRender = ""
    private var widgetAt = 0L
    private var alive = true
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        IssueCatalog.load(this)
        manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("printer_progress", "Printer progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Quiet ongoing progress and connection status"
            setSound(null, null); enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel("printer_events", "Printer events", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Print started, completed, paused, or needs attention"
        })
        startForeground(100, notification(PrinterMonitor.state.value, false))
        wakeLock = getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "FilamentMeter:printerMonitor")
            .apply { setReferenceCounted(false); acquire(10 * 60_000L) }
        scope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                wakeLock?.acquire(10 * 60_000L)
            }
        }
        mqtt = BambuMqttClient(onState = { state -> scope.launch {
            if (!alive) return@launch
            PrinterMonitor.state.value = state
            val event = events.accept(state)
            val render = "${state.connected}|${state.statusText}|${state.gcodeState}|${state.progressPercent}|${state.remainingMinutes}|${state.jobName}|${state.issueText}"
            if (render != lastRender) {
                manager.notify(100, notification(state, false)); lastRender = render
            }
            val prefs = getSharedPreferences("monitor", 0)
            val oldIssue = prefs.getString("lastIssue", "")
            if (state.connected) prefs.edit().putString("lastIssue", state.issueText).apply()
            if (event != null && (event != "Printer needs attention" || oldIssue != state.issueText))
                manager.notify(101, notification(state, true, event))
            val now = android.os.SystemClock.elapsedRealtime()
            if (event != null || now - widgetAt >= 5000 || !state.connected) {
                widgetAt = now
                withContext(Dispatchers.IO) {
                    store.saveWidgetSnapshot(state, store.load())
                    FilamentMeterWidgetProvider.refreshAll(this@PrinterMonitorService)
                }
            }
        } }, onError = { PrinterMonitor.error.value = it })
        PrinterMonitor.client = mqtt
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val next = store.load()
        if (!PrinterMonitor.enabled(this) || next.printerIp.isBlank() || next.serialNumber.isBlank() || next.accessCode.isBlank()) {
            stopSelf(); return START_NOT_STICKY
        }
        val old = settings
        if (old == null || old.printerIp != next.printerIp || old.serialNumber != next.serialNumber || old.accessCode != next.accessCode) {
            events.reset()
            mqtt.connect(next)
        }
        settings = next
        return START_STICKY
    }

    private fun notification(state: PrinterState, alert: Boolean, event: String? = null): Notification {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java)
            .putExtra("printer_command", "view").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = event ?: if (!state.connected) "Printer · Reconnecting" else "${state.printerName} · ${state.displayStatus}"
        val detail = if (!state.connected) "Waiting for the printer on your local network. Reconnecting automatically."
            else state.issueText.ifBlank { when {
                state.gcodeState.equals("PREPARE", true) -> "${state.jobName} · Preparing printer. Progress will appear when printing begins."
                event == "Print started" -> "${state.jobName} · Print started. Follow the ongoing notification for live progress."
                state.canResume -> "${state.jobName} · Paused at ${state.progressPercent}%. Check the printer, then resume when ready."
                state.isPrinting -> "${state.jobName} · ${state.progressPercent}% · ${state.remainingMinutes} min remaining"
                state.gcodeState.equals("FINISH", true) -> "${state.jobName} · Print completed"
                state.gcodeState.equals("FAILED", true) -> "Print stopped or failed. Check the printer screen for details before restarting."
                else -> "Connected and ready. Monitoring in the background."
            } }
        return NotificationCompat.Builder(this, if (alert) "printer_events" else "printer_progress")
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
        PrinterMonitor.client = null
        mqtt.disconnect()
        PrinterMonitor.state.value = PrinterMonitor.state.value.copy(connected = false, statusText = "Disconnected")
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        store.saveWidgetSnapshot(PrinterMonitor.state.value, store.load())
        FilamentMeterWidgetProvider.refreshAll(this)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
