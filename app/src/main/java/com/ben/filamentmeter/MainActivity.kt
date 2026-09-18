package com.ben.filamentmeter

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ben.filamentmeter.model.AmsTray
import com.ben.filamentmeter.model.AppSettings
import com.ben.filamentmeter.model.PrinterState
import com.ben.filamentmeter.ui.AmsVisualRenderer
import com.ben.filamentmeter.ui.PrinterScreen
import com.ben.filamentmeter.ui.ConnectedHardwareCard
import com.ben.filamentmeter.ui.LanAccessCodeHelp
import com.ben.filamentmeter.ui.theme.FilamentMeterTheme
import com.ben.filamentmeter.widget.FilamentMeterWidgetProvider
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var widgetCommand by mutableStateOf<String?>(null)
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        widgetCommand = intent.getStringExtra("printer_command")
        setContent { FilamentMeterTheme { App(vm, widgetCommand) { widgetCommand = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetCommand = intent.getStringExtra("printer_command")
    }
}

private enum class Tab { Meter, Printer, Settings }

@Composable
private fun App(vm: MainViewModel, widgetCommand: String?, clearWidgetCommand: () -> Unit) {
    val printer by vm.printer.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val discoveryState by vm.discoveryState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.Meter) }
    LaunchedEffect(widgetCommand) { if (widgetCommand != null) tab = Tab.Printer }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = tab == Tab.Meter,
                    onClick = { tab = Tab.Meter },
                    icon = { Icon(Icons.Default.AttachMoney, null) },
                    label = { Text("Meter") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Printer,
                    onClick = { tab = Tab.Printer },
                    icon = { Icon(painterResource(R.drawable.ic_bambu_printer), null, Modifier.size(24.dp)) },
                    label = { Text("Printer") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Settings,
                    onClick = { tab = Tab.Settings },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Setup") }
                )
            }
        }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            when (tab) {
                Tab.Printer -> PrinterScreen(printer, settings, vm::printCommand, vm::setLight,
                    vm::setRecording, { tab = Tab.Settings }, vm::connect)
                Tab.Meter -> MeterScreen(
                    printer = printer,
                    settings = settings,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect
                )
                Tab.Settings -> SettingsScreen(
                    settings = settings,
                    discoveryState = discoveryState,
                    onStartDiscovery = vm::startDiscovery,
                    onCancelDiscovery = vm::cancelDiscovery,
                    onResetDiscovery = vm::resetDiscovery,
                    onSave = {
                        vm.saveSettings(it)
                        tab = Tab.Meter
                    }
                )
            }
        }
    }

    widgetCommand?.takeIf { it != "view" }?.let { command ->
        val allowed = when (command) {
            "resume" -> printer.canResume
            "pause" -> printer.canPause
            "stop" -> printer.canStop
            else -> false
        }
        AlertDialog(onDismissRequest = clearWidgetCommand,
            title = { Text(when(command) { "resume" -> "Resume print?"; "pause" -> "Pause print?"; else -> "Stop print?" }) },
            text = { Text(when {
                !printer.connected -> "Connecting to verify the current printer state…"
                !allowed -> "This action is unavailable while the printer is ${printer.displayStatus.lowercase()}."
                command == "stop" -> "Cancel ${printer.jobName}? This job cannot be resumed."
                else -> printer.jobName
            }) },
            confirmButton = { TextButton(enabled = allowed, onClick = { vm.printCommand(command); clearWidgetCommand() }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = clearWidgetCommand) { Text("Cancel") } })
    }

    error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { Button(onClick = vm::clearError) { Text("Got it") } },
            title = { Text("Printer message") },
            text = { Text(it) }
        )
    }
}

@Composable
private fun MeterScreen(
    printer: PrinterState,
    settings: AppSettings,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isRunning = printer.isPrinting
    val progress = if (isRunning) printer.progressPercent.coerceIn(0, 100) / 100.0 else 0.0
    val gramsUsed = if (isRunning) settings.jobFilamentGrams * progress else 0.0
    val currentCost = if (isRunning) gramsUsed * settings.pricePerGram else 0.0
    val totalCost = if (isRunning) settings.estimatedJobCost else 0.0
    val remaining = if (isRunning) (settings.jobFilamentGrams - gramsUsed).coerceAtLeast(0.0) else 0.0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "FILAMENT",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "METER",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
            }
            LiveChip(printer.connected, if (isRunning) printer.statusText else if (printer.connected) "IDLE" else "OFFLINE")
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        ),
                        RoundedCornerShape(30.dp)
                    )
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (isRunning) printer.jobName
                        else if (printer.connected) {
                            if (printer.gcodeState.equals("FINISH", true)) "Print Finished • Ready"
                            else "Printer Idle"
                        } else "No active print",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    Text(
                        "COST USED",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )

                    AnimatedContent(currentCost, label = "cost") { value ->
                        Text(
                            money(value),
                            fontSize = 58.sp,
                            lineHeight = 60.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { if (isRunning) printer.progressPercent / 100f else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isRunning) "${printer.progressPercent}% complete"
                            else if (printer.connected) "Standby • Ready"
                            else "Offline",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isRunning && printer.totalLayers > 0)
                                "Layer ${printer.currentLayer}/${printer.totalLayers}"
                            else if (printer.gcodeState.equals("FINISH", true) && printer.jobName.isNotBlank())
                                "Last: ${printer.jobName}"
                            else "Layer —",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PremiumMetric(
                Modifier.weight(1f),
                title = "Used",
                value = if (isRunning) "${oneDecimal(gramsUsed)} g" else "0.0 g",
                accent = isRunning
            )
            PremiumMetric(
                Modifier.weight(1f),
                title = "Remaining",
                value = if (isRunning) "${oneDecimal(remaining)} g" else "—"
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PremiumMetric(
                Modifier.weight(1f),
                title = "Final cost",
                value = if (isRunning) money(totalCost) else "—"
            )
            PremiumMetric(
                Modifier.weight(1f),
                title = "Time left",
                value = if (isRunning) formatMinutes(printer.remainingMinutes) else "—"
            )
        }

        ConnectedHardwareCard(printer)
        printer.amsUnits.forEach { unit ->
            if (unit.trays.isNotEmpty()) {
                Text("${unit.displayName} · Live filament", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    unit.trays.forEach { tray -> AmsSlotView(tray, Modifier.weight(1f)) }
                }
            }
        }

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${printer.model.label.uppercase()} LIVE DATA", fontWeight = FontWeight.Black)
                        Text(
                            printer.gcodeState,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                InfoRow("Nozzle", "${oneDecimal(printer.nozzleTemp)} °C")
                InfoRow("Bed", "${oneDecimal(printer.bedTemp)} °C")
                InfoRow("Cost / gram", "\$${fourDecimals(settings.pricePerGram)}")
                InfoRow("Sliced filament", "${oneDecimal(settings.jobFilamentGrams)} g")
            }
        }

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Widgets,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Home-screen widget", fontWeight = FontWeight.Bold)
                    Text(
                        "Shows print progress, filament cost, grams used and current layer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Button(
            onClick = if (printer.connected) onDisconnect else onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = if (printer.connected) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(
                if (printer.connected) "Disconnect" else "Connect printer",
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun AmsSlotView(tray: AmsTray, modifier: Modifier = Modifier) {
    val trayColor = parseHexColor(tray.colorHex)
    Column(
        modifier = modifier.padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (tray.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = if (tray.active) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(trayColor, CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    )
                    Text(
                        "Bay ${tray.id + 1}",
                        fontSize = 11.sp,
                        fontWeight = if (tray.active) FontWeight.Black else FontWeight.Bold,
                        color = if (tray.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    tray.type,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                if (tray.active) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            "FEEDING",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getSpoolDrawable(hex: String): Int {
    val clean = hex.removePrefix("#").trim()
    val r: Int
    val g: Int
    val b: Int
    try {
        val rgb = when (clean.length) {
            6 -> clean.toInt(16)
            8 -> clean.substring(0, 6).toInt(16)
            else -> 0x888888
        }
        r = (rgb shr 16) and 0xFF
        g = (rgb shr 8) and 0xFF
        b = rgb and 0xFF
    } catch (_: Throwable) {
        return R.drawable.spool_white
    }

    val candidates = listOf(
        Triple(0x16, 0x16, 0x16) to R.drawable.spool_black,
        Triple(0xF0, 0xF0, 0xF0) to R.drawable.spool_white,
        Triple(0x80, 0x80, 0x80) to R.drawable.spool_grey,
        Triple(0x28, 0x50, 0xE0) to R.drawable.spool_blue,
        Triple(0xF9, 0x8C, 0x36) to R.drawable.spool_orange,
        Triple(0xD0, 0x20, 0x20) to R.drawable.spool_red,
        Triple(0x20, 0xB0, 0x40) to R.drawable.spool_green,
        Triple(0xF0, 0xD0, 0x20) to R.drawable.spool_yellow,
        Triple(0x80, 0x30, 0xC0) to R.drawable.spool_purple,
        Triple(0xD0, 0x30, 0x90) to R.drawable.spool_magenta
    )

    return candidates.minByOrNull { (rgb, _) ->
        val dr = r - rgb.first
        val dg = g - rgb.second
        val db = b - rgb.third
        dr * dr + dg * dg + db * db
    }?.second ?: R.drawable.spool_white
}

private fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#").trim()
    val fullHex = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> "FF888888"
    }
    return try {
        Color(fullHex.toLong(16))
    } catch (_: Throwable) {
        Color.Gray
    }
}

@Composable
private fun PremiumMetric(
    modifier: Modifier,
    title: String,
    value: String,
    accent: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (accent)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LiveChip(connected: Boolean, status: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = if (connected)
            MaterialTheme.colorScheme.primary.copy(alpha = .12f)
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                modifier = Modifier.size(15.dp),
                tint = if (connected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (connected) status.uppercase() else "OFFLINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    discoveryState: DiscoveryState,
    onStartDiscovery: () -> Unit,
    onCancelDiscovery: () -> Unit,
    onResetDiscovery: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var ip by remember(settings) { mutableStateOf(settings.printerIp) }
    var serial by remember(settings) { mutableStateOf(settings.serialNumber) }
    var accessCode by remember(settings) { mutableStateOf(settings.accessCode) }
    var spoolPrice by remember(settings) { mutableStateOf(settings.spoolPrice.toString()) }
    var spoolWeight by remember(settings) { mutableStateOf(settings.spoolWeightGrams.toString()) }
    var jobGrams by remember(settings) { mutableStateOf(settings.jobFilamentGrams.toString()) }

    LaunchedEffect(discoveryState) {
        if (discoveryState is DiscoveryState.Found) {
            if (ip.isBlank()) ip = discoveryState.printer.ip
            if (serial.isBlank()) serial = discoveryState.printer.serialNumber
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("SETUP", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            "Printer connection and filament pricing",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionLabel("PRINTER CONNECTION")

        when (discoveryState) {
            is DiscoveryState.Idle -> {
                OutlinedButton(
                    onClick = onStartDiscovery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-detect printer on network", fontWeight = FontWeight.SemiBold)
                }
            }
            is DiscoveryState.Scanning -> {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Scanning Wi-Fi network...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Listening for Bambu broadcast", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onCancelDiscovery) {
                            Text("Cancel")
                        }
                    }
                }
            }
            is DiscoveryState.Found -> {
                val printer = discoveryState.printer
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Found ${printer.name}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "IP: ${printer.ip}  •  SN: ${printer.serialNumber}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    ip = printer.ip
                                    serial = printer.serialNumber
                                    onResetDiscovery()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Use printer details")
                            }
                            OutlinedButton(
                                onClick = onResetDiscovery,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            is DiscoveryState.NotFound -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "No printer detected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Ensure printer is powered on and connected to this Wi-Fi.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onStartDiscovery) {
                            Text("Retry")
                        }
                        TextButton(onClick = onResetDiscovery) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Printer IP") },
            placeholder = { Text("192.168.0.120") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        OutlinedTextField(
            value = serial,
            onValueChange = { serial = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Serial number") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        OutlinedTextField(
            value = accessCode,
            onValueChange = { accessCode = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("LAN access code") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        SectionLabel("FILAMENT (1kg • 1.75mm PLA)")
        OutlinedTextField(
            value = spoolPrice,
            onValueChange = { spoolPrice = cleanDecimal(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Spool price") },
            prefix = { Text("$") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        OutlinedTextField(
            value = spoolWeight,
            onValueChange = { spoolWeight = cleanDecimal(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Spool weight") },
            suffix = { Text("g") },
            supportingText = { Text("Standard 1kg (1000g) spool of 1.75mm PLA.") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        OutlinedTextField(
            value = jobGrams,
            onValueChange = { jobGrams = cleanDecimal(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Sliced filament") },
            suffix = { Text("g") },
            supportingText = { Text("Use the gram estimate from Bambu Studio / OrcaSlicer.") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            onClick = {
                onSave(
                    AppSettings(
                        printerIp = ip.trim(),
                        serialNumber = serial.trim(),
                        accessCode = accessCode.trim(),
                        spoolPrice = spoolPrice.toDoubleOrNull() ?: 0.0,
                        spoolWeightGrams = spoolWeight.toDoubleOrNull() ?: 1000.0,
                        jobFilamentGrams = jobGrams.toDoubleOrNull() ?: 0.0
                    )
                )
            }
        ) {
            Text("Save setup", fontWeight = FontWeight.Black)
        }

        SectionLabel("HOMESCREEN WIDGET")
        val context = LocalContext.current
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(18.dp),
            onClick = {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val myProvider = ComponentName(context, FilamentMeterWidgetProvider::class.java)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                }
            }
        ) {
            Icon(Icons.Default.Widgets, null)
            Spacer(Modifier.width(8.dp))
            Text("Pin Widget to Home Screen", fontWeight = FontWeight.Bold)
        }

        Text(
            "Background monitoring keeps the widget and quiet progress notification updated. Connect starts monitoring; Disconnect stops it. Keep the phone on the printer’s local network. Android force-stop or restricted battery settings can interrupt monitoring.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        OutlinedButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
            Text("Printer notification settings")
        }
        LanAccessCodeHelp()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

private fun cleanDecimal(value: String): String =
    value.filter { it.isDigit() || it == '.' }.let { filtered ->
        val firstDot = filtered.indexOf('.')
        if (firstDot == -1) filtered
        else filtered.substring(0, firstDot + 1) +
            filtered.substring(firstDot + 1).replace(".", "")
    }

private fun money(value: Double): String =
    String.format(Locale.US, "$%.2f", value.coerceAtLeast(0.0))

private fun oneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private fun fourDecimals(value: Double): String =
    String.format(Locale.US, "%.4f", value.coerceAtLeast(0.0))

private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
