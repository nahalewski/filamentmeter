package com.ben.filamentmeter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LanAccessCodeHelp() {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var linkError by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    OutlinedButton(
        onClick = { linkError = false; showHelp = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, null)
        Spacer(Modifier.width(10.dp))
        Text("Find your LAN access code", fontWeight = FontWeight.SemiBold)
    }
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text("Find your LAN access code") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Turn on your printer and connect your phone to the same local network.")
                Text("2. On the printer’s screen, open the page for your model:")
                Text("P1S / P1P", fontWeight = FontWeight.Bold)
                Text("Settings (gear icon) → WLAN → LAN Only Mode.")
                Text("P2S / X1 Carbon / X1 series", fontWeight = FontWeight.Bold)
                Text("Settings → LAN Only.")
                Text("A1 mini / A1", fontWeight = FontWeight.Bold)
                Text("Settings → LAN Only Mode. On some firmware versions, scroll to page 3 of Settings.")
                Text("3. Find the Access Code on that page. If it isn’t visible, enable LAN Only mode and confirm the printer’s prompt.")
                Text("LAN Only mode disconnects the printer from Bambu’s cloud services, including Bambu Handy remote access.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("4. Enter the code exactly into LAN access code in Setup. Add the printer IP and serial number, then tap Save setup.")
                Text("Use the LAN access code, not your Wi-Fi password or account pairing PIN. If you reset the code on the printer, update it here too.")
                TextButton(onClick = {
                    runCatching { uriHandler.openUri("https://wiki.bambulab.com/en/knowledge-sharing/enable-lan-mode") }
                        .onFailure { linkError = true }
                }) { Text("Open Bambu’s illustrated guide") }
                if (linkError) Text("Could not open a browser. The steps above are available offline.")
            }
        },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } }
    )
}
