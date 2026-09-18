package com.ben.filamentmeter.vision

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import com.ben.filamentmeter.bambu.BambuCamera
import com.ben.filamentmeter.model.AppSettings
import com.ben.filamentmeter.monitor.PrinterMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class CameraFrame(val bitmap: Bitmap? = null, val message: String = "Connecting camera…", val at: Long = 0L)
data class VisionOptions(val enabled: Boolean = false, val threshold: Float = .7f)
data class VisionStatus(val message: String = "Detection off", val checkedAt: Long = 0L,
    val inferenceMs: Long = 0L, val boxes: List<FailureBox> = emptyList(),
    val warning: String? = null, val evidence: Bitmap? = null)

object CameraMonitor {
    val frame = MutableStateFlow(CameraFrame())
    val options = MutableStateFlow(VisionOptions())
    val vision = MutableStateFlow(VisionStatus())
    @Volatile var preview = false
    @Volatile var autoReconnect = true
    @Volatile var retry = 0
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("vision",0)
        options.value = VisionOptions(prefs.getBoolean("enabled",false), prefs.getFloat("threshold",.7f).coerceIn(.5f,.95f))
    }
    fun configure(context: Context, enabled: Boolean, threshold: Float = options.value.threshold) {
        options.value = VisionOptions(enabled,threshold.coerceIn(.5f,.95f))
        context.getSharedPreferences("vision",0).edit().putBoolean("enabled",enabled).putFloat("threshold",options.value.threshold).apply()
        if (!enabled) vision.value = VisionStatus()
        if (!enabled) context.getSystemService(android.app.NotificationManager::class.java).cancel(102)
        if (enabled) PrinterMonitor.start(context)
    }
    fun dismiss() { vision.value = vision.value.copy(warning = null,evidence = null) }
}

/** One printer socket is shared by the preview and background detection. */
class CameraSupervisor(private val context: Context, private val scope: CoroutineScope,
    private val alert: (Int, String) -> Unit) {
    @Volatile private var settings: AppSettings? = null
    @Volatile private var camera: BambuCamera? = null
    private var streamJob: Job? = null
    private var streamKey = ""
    @Volatile private var stopped = false
    private val controlJob = scope.launch {
        while (isActive) {
            val config = settings
            val enabled = CameraMonitor.options.value.enabled
            val running = PrinterMonitor.state.value.canPause
            val supported = PrinterMonitor.state.value.model in listOf(com.ben.filamentmeter.model.PrinterModel.P1S,
                com.ben.filamentmeter.model.PrinterModel.P1P,com.ben.filamentmeter.model.PrinterModel.A1,
                com.ben.filamentmeter.model.PrinterModel.A1_MINI)
            val wanted = supported && config != null && config.printerIp.isNotBlank() && config.accessCode.isNotBlank() &&
                (CameraMonitor.preview || enabled && running)
            val key = if (wanted) "${config!!.printerIp}|${config.serialNumber}|${config.accessCode}|${CameraMonitor.retry}" else ""
            if (key != streamKey) {
                camera?.close(); streamJob?.cancelAndJoin(); camera = null
                streamKey = key
                CameraMonitor.frame.value = CameraFrame(message = if (wanted) "Connecting camera…" else "Camera hidden")
                if (wanted) streamJob = scope.launch(Dispatchers.IO) { stream(config!!) }
            }
            if (!supported) {
                CameraMonitor.frame.value = CameraFrame(message="Camera streaming is not supported for this model yet")
                CameraMonitor.vision.value = VisionStatus(message="Detection needs a supported camera stream")
            }
            if (enabled && !running) CameraMonitor.vision.value = CameraMonitor.vision.value.copy(
                message = if (!PrinterMonitor.state.value.connected) "Waiting for printer connection" else "Waiting for an active print", boxes = emptyList())
            delay(500)
        }
    }
    fun update(next: AppSettings) {
        if (settings?.serialNumber != next.serialNumber || settings?.printerIp != next.printerIp) {
            context.getSystemService(android.app.NotificationManager::class.java).cancel(102)
            camera?.close(); streamJob?.cancel()
            CameraMonitor.frame.value = CameraFrame(message="Switching printer")
            CameraMonitor.vision.value = VisionStatus()
        }
        settings = next
    }
    fun close() {
        stopped = true; camera?.close(); streamJob?.cancel(); controlJob.cancel()
        CameraMonitor.frame.value = CameraFrame(message = "Monitoring stopped")
        CameraMonitor.vision.value = VisionStatus(message = "Monitoring stopped")
    }
    private suspend fun stream(config: AppSettings) {
        var model: YoloFailureDetector? = null
        val gate = FailureGate()
        var jobKey = ""
        var startedAt = 0L
        var sampledAt = 0L
        try {
            do {
                val connection = BambuCamera()
                camera = connection
                try {
                    connection.stream(config) frameReceived@{ bitmap ->
                        if (stopped || settings?.serialNumber != config.serialNumber || !currentCoroutineContextActive()) return@frameReceived
                        CameraMonitor.frame.value = CameraFrame(bitmap,"Live",System.currentTimeMillis())
                        val options = CameraMonitor.options.value
                        val printer = PrinterMonitor.state.value
                        if (!options.enabled || !printer.canPause) {
                            startedAt = 0; jobKey = ""; gate.reset()
                            return@frameReceived
                        }
                        val now = SystemClock.elapsedRealtime()
                        val identity = "${config.serialNumber}|${printer.jobName}"
                        if (identity != jobKey || startedAt == 0L) {
                            jobKey = identity; startedAt = now; gate.reset()
                            CameraMonitor.vision.value = VisionStatus(message = "Warming up · checks begin in 60 seconds")
                        }
                        if (now-startedAt < 60_000 || now-sampledAt < 5000) return@frameReceived
                        sampledAt = now
                        try {
                            if (model == null) model = YoloFailureDetector(context)
                            val boxes = model!!.detect(bitmap,options.threshold)
                            val duration = SystemClock.elapsedRealtime()-now
                            // Ignore a result if detection was disabled or the printer stopped during inference.
                            if (stopped || settings?.serialNumber != config.serialNumber || !CameraMonitor.options.value.enabled || !PrinterMonitor.state.value.canPause) return@frameReceived
                            val match = gate.sample(boxes,now)
                            val old = CameraMonitor.vision.value
                            val warning = match?.let { "Possible ${it.label.lowercase()} — inspect the print before continuing." }
                            CameraMonitor.vision.value = VisionStatus(
                                message = if (boxes.isEmpty()) "No failure detected in the latest frame" else "Possible issue · checking repeated frames",
                                checkedAt = System.currentTimeMillis(), inferenceMs = duration, boxes = boxes,
                                warning = warning ?: old.warning,
                                evidence = if (match != null) evidence(bitmap,boxes) else old.evidence)
                            android.util.Log.d("PrintVision","Analyzed frame: ${duration}ms, candidates=${boxes.size}")
                            if (warning != null && match != null) scope.launch {
                                if (!stopped && settings?.serialNumber == config.serialNumber && CameraMonitor.options.value.enabled)
                                    alert(match.kind,warning)
                            }
                        } catch (e: Exception) {
                            gate.sample(emptyList(),now)
                            CameraMonitor.vision.value = CameraMonitor.vision.value.copy(message = "Detection unavailable · ${e.message ?: "model error"}",boxes = emptyList())
                            android.util.Log.w("PrintVision","Detector unavailable",e)
                        }
                    }
                } catch (e: Exception) {
                    if (stopped) return
                    if (e is CancellationException) throw e
                    android.util.Log.w("PrintVision", "Camera stream unavailable: ${e.javaClass.simpleName}: ${e.message}")
                    CameraMonitor.frame.value = CameraFrame(message = "Camera unavailable · retrying LAN connection")
                    gate.sample(emptyList(),SystemClock.elapsedRealtime())
                    if (CameraMonitor.options.value.enabled) CameraMonitor.vision.value = CameraMonitor.vision.value.copy(message = "Camera unavailable · detection not active",boxes = emptyList())
                } finally { connection.close() }
                if (!CameraMonitor.autoReconnect && !CameraMonitor.options.value.enabled) break
                delay(5000)
            } while (currentCoroutineContext().isActive && !stopped)
        } finally { model?.close() }
    }
    private fun currentCoroutineContextActive() = !stopped && streamJob?.isActive != false
    private fun evidence(bitmap: Bitmap, boxes: List<FailureBox>): Bitmap {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888,true)
        val canvas = Canvas(copy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; strokeWidth = 4f; textSize = 26f }
        for (box in boxes) {
            paint.style = Paint.Style.STROKE
            canvas.drawRect(box.left*copy.width,box.top*copy.height,box.right*copy.width,box.bottom*copy.height,paint)
            paint.style = Paint.Style.FILL
            canvas.drawText("${box.label} ${(box.confidence*100).toInt()}%",box.left*copy.width,maxOf(28f,box.top*copy.height-6),paint)
        }
        return copy
    }
}
