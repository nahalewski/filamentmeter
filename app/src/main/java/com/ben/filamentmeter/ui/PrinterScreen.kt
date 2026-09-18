package com.ben.filamentmeter.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ben.filamentmeter.vision.CameraMonitor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ben.filamentmeter.model.AppSettings
import com.ben.filamentmeter.model.PrinterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrinterScreen(printer: PrinterState, settings: AppSettings,
    onCommand: (String) -> Unit, onLight: (Boolean) -> Unit, onRecording: (Boolean) -> Unit,
    onSetup: () -> Unit, onConnect: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraState by CameraMonitor.frame.collectAsStateWithLifecycle()
    val vision by CameraMonitor.vision.collectAsStateWithLifecycle()
    val visionOptions by CameraMonitor.options.collectAsStateWithLifecycle()
    var overlayClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visionOptions.enabled) {
        while (visionOptions.enabled && isActive) { overlayClock=System.currentTimeMillis(); delay(500) }
    }
    val frame = cameraState.bitmap
    val cameraMessage = cameraState.message
    val live = cameraState.bitmap != null && cameraState.message == "Live"
    val lastFrame = cameraState.at
    var enabled by rememberSaveable { mutableStateOf(true) }
    var auto by rememberSaveable { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var resolution by rememberSaveable { mutableIntStateOf(720) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var fit by rememberSaveable { mutableStateOf(true) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var stop by remember { mutableStateOf(false) }
    var recordingPrompt by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(enabled, foreground, retry, auto) {
        CameraMonitor.preview = enabled && foreground
        CameraMonitor.autoReconnect = auto
        onDispose { CameraMonitor.preview = false }
    }
    LaunchedEffect(retry) { if (retry > 0) CameraMonitor.retry++ }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri: Uri? ->
        val file = snapshot
        if (uri != null && file != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                    ?: error("Unable to open destination")
            } }.onSuccess { notice = "Snapshot saved" }.onFailure { notice = "Could not save snapshot" }
        }
    }
    fun capture() {
        val bitmap = frame ?: return
        if (saving) return
        saving = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "captures").apply { mkdirs() }
                val file = File(directory, "printer-${System.currentTimeMillis()}.jpg")
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                file
            } }.onSuccess { snapshot?.delete(); snapshot = it; notice = "Snapshot captured · save or share below" }
                .onFailure { notice = "Snapshot failed: ${it.message}" }
            saving = false
        }
    }
    val cameraView: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
            frame?.takeIf { enabled }?.let { bitmap ->
                val preview = remember(bitmap, resolution) {
                    if (bitmap.height > resolution) Bitmap.createScaledBitmap(bitmap,
                        bitmap.width * resolution / bitmap.height, resolution, true) else bitmap
                }
                Image(preview.asImageBitmap(), "Printer chamber camera", Modifier.fillMaxSize()
                    .graphicsLayer(scaleX = zoom, scaleY = zoom),
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
                if (live && printer.canPause && visionOptions.enabled &&
                    com.ben.filamentmeter.vision.detectionOverlayFresh(vision.checkedAt,overlayClock)) {
                    DetectionOverlay(vision.boxes,preview.width,preview.height,fit,zoom,Modifier.matchParentSize())
                }
            }
            if (!live || !enabled) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideocamOff, null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                Text(if (enabled) cameraMessage else "Camera hidden", color = Color.LightGray)
            }
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color.Black.copy(alpha = .6f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(printer.printerName, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (live) "● Live" else "○ Offline", color = if (live) MaterialTheme.colorScheme.primary else Color.Gray)
                IconButton(onClick = { fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Toggle fullscreen", tint = Color.White) }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(6.dp)) {
                Tool(Icons.Default.PhotoCamera, "Snapshot", live && !saving, action = { capture() })
                Tool(Icons.Default.Videocam, "Printer recording", printer.connected, printer.cameraRecording) { recordingPrompt = true }
                Tool(Icons.Default.MicOff, "Camera has no audio", false) {}
                Tool(Icons.Default.Lightbulb, "Chamber light", printer.connected, printer.chamberLight) { onLight(!printer.chamberLight) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = .7f)).padding(10.dp)) {
                Text(when {
                    !visionOptions.enabled -> "YOLO off · enable Print watch below"
                    !enabled -> "YOLO overlay hidden"
                    !live || !printer.canPause -> "YOLO · waiting for live printing camera"
                    !com.ben.filamentmeter.vision.detectionOverlayFresh(vision.checkedAt,overlayClock) -> "YOLO · ${vision.message}"
                    vision.boxes.isEmpty() -> "YOLO · no detections in last scan"
                    else -> "YOLO · ${vision.boxes.size} possible issue(s) · last scan ${(overlayClock-vision.checkedAt)/1000}s ago"
                },color=Color.White,fontSize=12.sp)
                Text(if (lastFrame == 0L) "Waiting for camera" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastFrame)),
                    color = Color.LightGray, fontSize = 11.sp)
            }
        }
    }
    if (fullscreen) Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        cameraView(Modifier.fillMaxSize())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PRINTER", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Chamber camera & controls", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = when (printer.displayStatus) {
                "Error" -> MaterialTheme.colorScheme.errorContainer
                "Printing" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }, shape = RoundedCornerShape(30.dp)) { Text(printer.displayStatus, Modifier.padding(12.dp)) }
        }
        cameraView(Modifier.fillMaxWidth().aspectRatio(1.35f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCommand("resume") }, enabled = printer.canResume, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, "Play / resume") }
            FilledTonalButton(onClick = { onCommand("pause") }, enabled = printer.canPause, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Pause, "Pause print") }
            Button(onClick = { stop = true }, enabled = printer.canStop, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Icon(Icons.Default.Stop, "Stop print") }
        }
        Text("Play resumes a paused print. Start new jobs from your slicer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CurrentPrintCard(printer,settings)
        FailureDetectionCard(printer.connected)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  ${printer.printerName}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(if (printer.connected) "● Online" else "○ Offline", fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nozzle  ${if (printer.connected) "${printer.nozzleTemp.toInt()}°C" else "—"}")
                    Text("Bed  ${if (printer.connected) "${printer.bedTemp.toInt()}°C" else "—"}")
                }
                Text(printer.jobName)
                LinearProgressIndicator(progress = { printer.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${printer.progressPercent}% · Layer ${printer.currentLayer}/${printer.totalLayers} · ${printer.remainingMinutes} min left", fontSize = 12.sp)
                if (printer.issueText.isNotBlank()) {
                    Text(printer.issueText, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(printer.issueUrl))) }) { Text("Official troubleshooting guide") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (printer.cameraRecording) "● REC · Printer SD card" else "Recording off", color = if (printer.cameraRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(if (printer.connected && printer.wifiSignal.isNotBlank()) "Wi-Fi ${printer.wifiSignal}" else "Wi-Fi —", fontSize = 12.sp)
                }
            }
        }
        ConnectedHardwareCard(printer)
        Text("CAMERA TOOLS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1080, 720, 480).forEach { height ->
                FilterChip(selected = resolution == height, onClick = { resolution = height }, label = { Text("${height}p") })
            }
        }
        Text("Preview size limit · native camera resolution is unchanged. P1S has no audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tool(Icons.Default.Remove, "Zoom out") { zoom = (zoom - .25f).coerceAtLeast(1f) }
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..3f, modifier = Modifier.weight(1f))
            Tool(Icons.Default.Add, "Zoom in") { zoom = (zoom + .25f).coerceAtMost(3f) }
            Text(String.format(Locale.US, "%.1f×", zoom), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tool(Icons.Default.SaveAlt, "Save snapshot", snapshot != null && !saving) { saveImage.launch(snapshot!!.name) }
            Tool(Icons.Default.DeleteOutline, "Delete snapshot", snapshot != null && !saving) { snapshot?.delete(); snapshot = null; notice = "Snapshot deleted" }
            Tool(Icons.Default.Share, "Share snapshot", snapshot != null && !saving) {
                runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.captures", snapshot!!)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share printer snapshot"))
                }.onFailure { notice = "Could not share snapshot" }
            }
            Tool(Icons.Default.Settings, "Printer setup", action = onSetup)
            Tool(Icons.Default.AspectRatio, "Fit / fill", selected = !fit) { fit = !fit }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(selected = auto, onClick = { auto = !auto }, label = { Text("Auto reconnect") })
            Tool(if (enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Show / hide camera", selected = enabled) { enabled = !enabled }
            Tool(Icons.Default.Refresh, "Reconnect camera") { enabled = true; retry++ }
        }
        if (!printer.connected) OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Connect printer") }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (stop) AlertDialog(onDismissRequest = { stop = false }, title = { Text("Stop this print?") }, text = { Text("This cancels the current job. It cannot be resumed.") }, confirmButton = { TextButton(onClick = { stop = false; onCommand("stop") }, enabled = printer.canStop) { Text("Stop print") } }, dismissButton = { TextButton(onClick = { stop = false }) { Text("Keep printing") } })
    if (recordingPrompt) AlertDialog(onDismissRequest = { recordingPrompt = false }, title = { Text("Printer recording") }, text = { Text("${if (printer.cameraRecording) "Disable" else "Enable"} recording on the printer's SD card. Recordings are managed on the printer; this does not record to your phone.") }, confirmButton = { TextButton(onClick = { recordingPrompt = false; onRecording(!printer.cameraRecording) }) { Text(if (printer.cameraRecording) "Disable" else "Enable") } }, dismissButton = { TextButton(onClick = { recordingPrompt = false }) { Text("Cancel") } })
}

@Composable
private fun Tool(icon: ImageVector, label: String, enabled: Boolean = true, selected: Boolean = false, action: () -> Unit) {
    FilledTonalIconButton(onClick = action, enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, label)
    }
}
