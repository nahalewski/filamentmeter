package com.ben.filamentmeter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ben.filamentmeter.vision.CameraMonitor
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FailureDetectionCard(connected: Boolean) {
    val context = LocalContext.current
    val options by CameraMonitor.options.collectAsStateWithLifecycle()
    val status by CameraMonitor.vision.collectAsStateWithLifecycle()
    var threshold by remember(options.threshold) { mutableFloatStateOf(options.threshold) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("YOLO print watch",fontWeight = FontWeight.Bold)
                    Text("Experimental · runs on this phone",style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = options.enabled,enabled = connected || options.enabled,
                    onCheckedChange = { CameraMonitor.configure(context,it) })
            }
            Text("Looks for spaghetti / loose filament, layer shifts, and warping / lifting. Alerts need three consecutive detections. It never pauses or stops the printer automatically.",style = MaterialTheme.typography.bodySmall)
            Text("Live camera boxes mark candidates from the latest scan, with labels and confidence. They appear immediately, before an alert is confirmed, and expire after 12 seconds. Boxes mark a suspected region, not an exact raised-edge outline.",style = MaterialTheme.typography.bodySmall)
            Text(if (!connected && !options.enabled) "Connect to your printer to enable detection." else status.message)
            if (options.enabled) {
                Text("Confidence threshold: ${(threshold*100).toInt()}%")
                Slider(value = threshold,onValueChange = { threshold = it },valueRange = .5f.. .95f,
                    onValueChangeFinished = { CameraMonitor.configure(context,true,threshold) })
                Text("Higher thresholds reduce false alerts but may miss failures. Checks run every 5 seconds after a 60-second warm-up, including in the background while printing. Camera monitoring uses extra battery.",style = MaterialTheme.typography.bodySmall)
                if (status.checkedAt > 0) Text("Last checked ${SimpleDateFormat("HH:mm:ss",Locale.US).format(Date(status.checkedAt))} · ${status.inferenceMs} ms",style = MaterialTheme.typography.bodySmall)
            }
            status.warning?.let { warning ->
                Text(warning,color = MaterialTheme.colorScheme.error,fontWeight = FontWeight.Bold)
                status.evidence?.let { Image(it.asImageBitmap(),"Last suspected failure with detection boxes",Modifier.fillMaxWidth().aspectRatio(it.width.toFloat()/it.height)) }
                Text("Review this captured frame and the live camera. Check for loose filament, a detached part, or raised edges. If the print is failing, use Pause above and inspect the printer.",style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    CameraMonitor.dismiss()
                    context.getSystemService(android.app.NotificationManager::class.java).cancel(102)
                }) { Text("Dismiss warning") }
            }
            Text("Not a guarantee: low light, occlusion, and camera angle can hide a failure. The model was trained on another printer; lifting detection on the P1S is unvalidated.",style = MaterialTheme.typography.bodySmall)
        }
    }
}
