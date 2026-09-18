package com.ben.filamentmeter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ben.filamentmeter.R
import com.ben.filamentmeter.model.*

fun PrinterModel.artwork(): Int? = when (this) {
    PrinterModel.P1S -> R.drawable.hardware_p1s
    PrinterModel.P2S -> R.drawable.hardware_p2s
    PrinterModel.X1C -> R.drawable.hardware_x1c
    PrinterModel.A1_MINI -> R.drawable.hardware_a1mini
    else -> null
}

fun AmsModel.artwork(): Int? = when (this) {
    AmsModel.AMS -> R.drawable.hardware_ams
    AmsModel.AMS2 -> R.drawable.hardware_ams2
    else -> null
}

@Composable
fun ConnectedHardwareCard(printer: PrinterState) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (printer.connected) "CONNECTED HARDWARE" else "PRINTER HARDWARE",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art = printer.model.artwork()
                if (art != null) Image(painterResource(art), printer.printerName,
                    Modifier.size(132.dp), contentScale = ContentScale.Fit)
                else Icon(painterResource(R.drawable.ic_bambu_printer), "Printer model not pictured",
                    Modifier.size(80.dp).padding(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(printer.printerName, fontWeight = FontWeight.Bold)
                    Text(printer.displayStatus, color = if (printer.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(printer.amsCountLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    if (printer.amsUnits.isNotEmpty()) Text(printer.amsDescription, style = MaterialTheme.typography.bodySmall)
                    if (printer.model == PrinterModel.UNKNOWN) Text("Model not yet identified", style = MaterialTheme.typography.bodySmall)
                }
            }
            printer.amsUnits.forEach { unit ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    unit.model.artwork()?.let { art ->
                        Image(painterResource(art), unit.model.label, Modifier.width(132.dp).height(96.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(unit.displayName, fontWeight = FontWeight.Bold)
                        Text(when { !printer.connected -> "Offline"; unit.isActive -> "Active filament source"; else -> "Connected" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (printer.connected && unit.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (unit.temperature.isNotBlank()) Text("${unit.temperature} °C", style = MaterialTheme.typography.bodySmall)
                        Text("Humidity ${unit.humidityLabel}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (printer.amsUnits.any { it.model.artwork() != null }) Text("Hardware artwork · live filament colors shown in Meter",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
