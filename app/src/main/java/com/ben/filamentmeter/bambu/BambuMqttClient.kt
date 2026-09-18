package com.ben.filamentmeter.bambu

import android.util.Log
import com.ben.filamentmeter.model.AmsTray
import com.ben.filamentmeter.model.AppSettings
import com.ben.filamentmeter.model.PrinterState
import com.ben.filamentmeter.model.PrinterModel
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BambuMqttClient(
    private val onState: (PrinterState) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "BambuMqtt"
    }

    private val worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "PrinterMonitor").apply { isDaemon = true }
    }
    private var client: MqttClient? = null
    @Volatile private var current = PrinterState()
    @Volatile private var lastStatusReportTimeMs = 0L
    private var hardware = HardwareReport()
    private var lastVersionRequestMs = 0L
    private var currentSettings: AppSettings? = null
    @Volatile private var shouldBeConnected = false
    private var sequenceCounter = 1
    private var lastMessageTimeMs = 0L
    private var lastRefreshMs = 0L
    private var commandGeneration = 0

    private fun enqueue(action: () -> Unit) {
        if (!worker.isShutdown) runCatching { worker.execute(action) }
    }

    init {
        worker.scheduleWithFixedDelay({
            if (shouldBeConnected) runCatching {
                val settings = currentSettings ?: return@scheduleWithFixedDelay
                val now = android.os.SystemClock.elapsedRealtime()
                when {
                    client?.isConnected != true -> doConnect(settings)
                    now - lastMessageTimeMs > 90_000 -> {
                        current = current.copy(connected = false, statusText = "Reconnecting…")
                        onState(current)
                        doConnect(settings)
                    }
                    now - lastMessageTimeMs > 35_000 && now - lastRefreshMs > 30_000 -> {
                        lastRefreshMs = now
                        requestPushAll(settings)
                    }
                }
            }.onFailure { Log.w(TAG, "Connection retry failed", it) }
        }, 8, 8, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun connect(settings: AppSettings) {
        if (settings.printerIp.isBlank() || settings.serialNumber.isBlank() || settings.accessCode.isBlank()) {
            onError("Printer IP, serial number, and LAN access code are required."); return
        }
        shouldBeConnected = true
        enqueue {
            if (currentSettings?.serialNumber != settings.serialNumber || currentSettings?.printerIp != settings.printerIp) {
                hardware = HardwareReport()
                current = PrinterState(model = PrinterModel.identify(serial = settings.serialNumber))
            }
            currentSettings = settings
            doConnect(settings)
        }
    }

    private fun closeClient() {
        val old = client
        client = null
        runCatching { old?.disconnectForcibly(500, 500) }
        runCatching { old?.close() }
    }

    private fun doConnect(settings: AppSettings) {
        if (!shouldBeConnected) return
        closeClient()
        lastStatusReportTimeMs = 0L
        current = current.copy(connected = false, statusText = "Connecting…")
        onState(current)
        try {
            val mqtt = MqttClient("ssl://${settings.printerIp}:8883", "FilamentMeter_${UUID.randomUUID().toString().take(8)}", MemoryPersistence())
            client = mqtt
            mqtt.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) = Unit
                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
                override fun connectionLost(cause: Throwable?) { enqueue {
                    if (client === mqtt && shouldBeConnected) {
                        lastStatusReportTimeMs = 0L
                        current = current.copy(connected = false, statusText = "Reconnecting…")
                        onState(current)
                    }
                } }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.payload?.toString(Charsets.UTF_8) ?: return
                    enqueue {
                        if (client === mqtt && shouldBeConnected) {
                            lastMessageTimeMs = android.os.SystemClock.elapsedRealtime()
                            parseReport(payload)
                        }
                    }
                }
            })
            mqtt.connect(MqttConnectOptions().apply {
                userName = "bblp"; password = settings.accessCode.toCharArray()
                isAutomaticReconnect = false; isCleanSession = true
                connectionTimeout = 15; keepAliveInterval = 30
                socketFactory = trustAllSslSocketFactory()
            })
            if (!shouldBeConnected) { closeClient(); return }
            lastMessageTimeMs = android.os.SystemClock.elapsedRealtime()
            mqtt.subscribe("device/${settings.serialNumber}/report", 0)
            requestPushAll(settings)
            publishCommand("info", JSONObject().put("command", "get_version"))
        } catch (e: Exception) {
            closeClient()
            current = current.copy(connected = false, statusText = "Reconnecting… Check printer IP and LAN access code.")
            onState(current)
            Log.w(TAG, "Printer connection failed", e)
        }
    }

    fun disconnect() {
        shouldBeConnected = false
        enqueue {
            closeClient()
            current = current.copy(connected = false, statusText = "Disconnected")
            onState(current)
            worker.shutdown()
        }
    }

    private fun requestPushAll(settings: AppSettings) {
        val mqtt = client ?: return
        if (!mqtt.isConnected) return

        val topic = "device/${settings.serialNumber}/request"
        val seq = (sequenceCounter++).toString()
        val body = JSONObject().apply {
            put("pushing", JSONObject().apply {
                put("sequence_id", seq)
                put("command", "pushall")
            })
        }

        Log.d(TAG, "Requesting full printer status (seq=$seq)")
        try {
            mqtt.publish(topic, MqttMessage(body.toString().toByteArray()).apply {
                qos = 0
                isRetained = false
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Publishing pushall failed: ${t.message}", t)
        }
    }

    fun printCommand(command: String) = enqueue {
        if (System.currentTimeMillis() - lastStatusReportTimeMs > 30_000) {
            onError("Waiting for fresh printer status. Try again in a moment.")
            currentSettings?.let { settings -> requestPushAll(settings) }
            return@enqueue
        }
        val allowed = when (command) {
            "resume" -> current.canResume
            "pause" -> current.canPause
            "stop" -> current.canStop
            else -> false
        }
        if (!allowed) { onError("This command is unavailable for the current printer state."); return@enqueue }
        publishCommand("print", JSONObject().put("command", command))
    }

    fun setLight(on: Boolean) = publishCommand("system", JSONObject()
        .put("command", "ledctrl").put("led_node", "chamber_light")
        .put("led_mode", if (on) "on" else "off").put("led_on_time", 500)
        .put("led_off_time", 500).put("loop_times", 0).put("interval_time", 0))

    fun setRecording(on: Boolean) = publishCommand("camera", JSONObject()
        .put("command", "ipcam_record_set").put("control", if (on) "enable" else "disable"))

    private fun publishCommand(section: String, body: JSONObject) {
        enqueue {
            try {
                val mqtt = client ?: error("Printer is offline")
                check(mqtt.isConnected) { "Printer is offline" }
                val settings = currentSettings ?: error("Configure a printer first")
                body.put("sequence_id", java.util.UUID.randomUUID().toString())
                mqtt.publish("device/${settings.serialNumber}/request",
                    MqttMessage(JSONObject().put(section, body).toString().toByteArray()).apply { qos = 0 })
                if (section == "print") {
                    val command = body.optString("command")
                    val generation = ++commandGeneration
                    worker.schedule({
                    if (generation != commandGeneration || !shouldBeConnected) return@schedule
                    val confirmed = when (command) {
                        "resume" -> current.canPause || current.gcodeState.equals("FINISH", true)
                        "pause" -> current.canResume
                        "stop" -> current.connected && !current.isPrinting
                        else -> true
                    }
                    if (!confirmed) onError("The printer has not confirmed $command. Check its screen and LAN control permissions before retrying.")
                    }, 12, java.util.concurrent.TimeUnit.SECONDS)
                }
            } catch (e: Exception) { onError(e.message ?: "Command could not be sent") }
        }
    }

    private fun parseReport(payload: String) {
        try {
            val root = JSONObject(payload)
            root.optJSONObject("info")?.let {
                current = hardware.applyInfo(current, it)
                onState(current)
            }
            listOf("system", "camera").forEach { section ->
                root.optJSONObject(section)?.let { response ->
                    if (response.optString("result").equals("fail", true)) {
                        onError("Printer rejected ${response.optString("command")}: ${response.optString("reason", "check printer permissions")}")
                    }
                }
            }
            val print = root.optJSONObject("print") ?: return
            if (print.optString("result").equals("fail", true)) {
                onError("Printer rejected ${print.optString("command", "command")}: ${print.optString("reason", "check printer permissions")}")
            }
            if (print.has("gcode_state") || (lastStatusReportTimeMs != 0L &&
                    listOf("mc_percent", "nozzle_temper", "ams", "layer_num").any { print.has(it) }))
                lastStatusReportTimeMs = System.currentTimeMillis()

            current = hardware.applyPrint(current, print)
            if (print.has("ams")) current.amsUnits.forEach {
                Log.d(TAG, "AMS ${it.id}: humidity index=${it.humidity}, percent=${it.humidityPercent}, display=${it.humidityLabel}")
            }
            if (print.has("ams") && System.currentTimeMillis() - lastVersionRequestMs > 60_000) {
                lastVersionRequestMs = System.currentTimeMillis()
                publishCommand("info", JSONObject().put("command", "get_version"))
            }

            val hms = print.optJSONArray("hms")?.let { items ->
                (0 until items.length()).mapNotNull { index -> items.optJSONObject(index)?.let {
                    "%08X%08X".format(it.optLong("attr") and 0xFFFFFFFFL, it.optLong("code") and 0xFFFFFFFFL)
                } }.filter { it != "0000000000000000" }
            } ?: current.hmsCodes
            val error = if (print.has("print_error")) print.optLong("print_error") else current.printError
            val codes = (if (error != 0L) listOf("%08X".format(error and 0xFFFFFFFFL)) else emptyList()) + hms
            val issue = codes.distinct().joinToString("\n\n") { code ->
                "${code.chunked(4).joinToString("-")}: ${com.ben.filamentmeter.monitor.IssueCatalog.describe(code)}"
            }

            // P1 printers often send delta updates, so preserve prior values when a field is absent.
            current = current.copy(
                connected = lastStatusReportTimeMs != 0L,
                statusText = "Connected",
                gcodeState = optStringKeep(print, "gcode_state", current.gcodeState),
                progressPercent = optIntKeep(print, "mc_percent", current.progressPercent).coerceIn(0, 100),
                currentLayer = optIntKeep(print, "layer_num", current.currentLayer),
                totalLayers = optIntKeep(print, "total_layer_num", current.totalLayers),
                remainingMinutes = optIntKeep(print, "mc_remaining_time", current.remainingMinutes),
                jobName = optStringKeep(print, "subtask_name", current.jobName),
                nozzleTemp = optDoubleKeep(print, "nozzle_temper", current.nozzleTemp),
                bedTemp = optDoubleKeep(print, "bed_temper", current.bedTemp),
                chamberLight = print.optJSONArray("lights_report")?.let { lights ->
                    (0 until lights.length()).mapNotNull { lights.optJSONObject(it) }
                        .firstOrNull { it.optString("node") == "chamber_light" }
                        ?.optString("mode")?.let { it == "on" }
                } ?: current.chamberLight,
                cameraRecording = print.optJSONObject("ipcam")?.let {
                    if (it.has("ipcam_record")) it.optString("ipcam_record") == "enable" else current.cameraRecording
                } ?: current.cameraRecording,
                wifiSignal = optStringKeep(print, "wifi_signal", current.wifiSignal),
                printError = error,
                hmsCodes = hms,
                issueText = issue,
                issueUrl = hms.firstOrNull()?.let { "https://wiki.bambulab.com/en/x1/troubleshooting/hmscode/${it.chunked(4).joinToString("_").lowercase()}" }
                    ?: "https://wiki.bambulab.com/en/hms/home",
            )

            Log.d(TAG, "parseReport: state=${current.gcodeState}, percent=${current.progressPercent}%, layer=${current.currentLayer}/${current.totalLayers}, nozzle=${current.nozzleTemp}°C, bed=${current.bedTemp}°C")
            onState(current)
        } catch (t: Throwable) {
            Log.e(TAG, "Error in parseReport: ${t.message}", t)
        }
    }

    private fun formatHexColor(raw: String): String {
        val clean = raw.trim().removePrefix("#")
        return when {
            clean.length >= 6 -> "#" + clean.substring(0, 6).uppercase()
            clean.isNotEmpty() -> "#" + clean.uppercase()
            else -> "#888888"
        }
    }

    private fun optStringKeep(obj: JSONObject, key: String, old: String): String =
        if (obj.has(key) && !obj.isNull(key)) obj.optString(key, old) else old

    private fun optIntKeep(obj: JSONObject, key: String, old: Int): Int =
        if (obj.has(key) && !obj.isNull(key)) obj.optInt(key, old) else old

    private fun optDoubleKeep(obj: JSONObject, key: String, old: Double): Double =
        if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key, old) else old

    private fun trustAllSslSocketFactory() = SSLContext.getInstance("TLS").apply {
        init(
            null,
            arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            ),
            SecureRandom()
        )
    }.socketFactory
}
