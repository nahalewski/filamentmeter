package com.ben.filamentmeter.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ben.filamentmeter.bambu.PrintPreviewLoader
import com.ben.filamentmeter.model.*
import kotlinx.coroutines.CancellationException

@Composable
fun CurrentPrintCard(printer: PrinterState, settings: AppSettings) {
    if(!printer.isPrinting && !(printer.connected && printer.gcodeState=="FINISH")) return
    val context=LocalContext.current
    val identity="${settings.serialNumber}|${printer.jobId}|${printer.jobName}|${printer.jobFile}|${printer.jobPlate}"
    var retry by remember(identity) { mutableIntStateOf(0) }
    var preview by remember(identity) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(identity) { mutableStateOf(true) }
    var expanded by remember(identity) { mutableStateOf(false) }
    LaunchedEffect(identity,settings.printerIp,settings.accessCode,retry) {
        loading=true;preview=null
        try { preview=PrintPreviewLoader.load(context,settings,printer) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { preview=null }
        finally { loading=false }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(printer.isPrinting) "CURRENT PRINT" else "COMPLETED PRINT",style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.primary)
            Text(printer.jobName,style=MaterialTheme.typography.titleMedium)
            preview?.let { bitmap ->
                Image(bitmap.asImageBitmap(),"Model preview for ${printer.jobName}",
                    Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium)
                        .background(Color(0xFF8795A1)).clickable { expanded=true },contentScale=ContentScale.Fit)
                Text("Sliced model preview · tap to enlarge",style=MaterialTheme.typography.bodySmall)
            } ?: if(loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading model preview from printer…",style=MaterialTheme.typography.bodySmall)
            } else {
                Text("Model preview unavailable. The printer must expose the current 3MF file with a matching plate thumbnail through LAN file access.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={retry++}) { Text("Retry preview") }
            }
        }
    }
    if(expanded) preview?.let { bitmap -> Dialog(onDismissRequest={expanded=false}) {
        Surface(shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(printer.jobName,style=MaterialTheme.typography.titleMedium)
                Image(bitmap.asImageBitmap(),"Enlarged model preview",Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height)
                    .clip(MaterialTheme.shapes.medium).background(Color(0xFF8795A1)))
                TextButton(onClick={expanded=false}) { Text("Close") }
            }
        }
    } }
}
