package com.ben.filamentmeter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun LicenseInfo() {
    var open by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Licenses and source")
    }
    if (open) {
        val notices = remember(context) {
            context.assets.open("third_party_notices.txt").bufferedReader().use { it.readText() }
        }
        val licenses = remember(context) {
            context.assets.list("licenses").orEmpty().sorted().filter { it.endsWith(".txt") }
        }
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        val licenseText = remember(selected, context) {
            selected?.let { context.assets.open("licenses/$it").bufferedReader().use { r -> r.readText() } }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(selected ?: "Licenses and source") },
            text = {
                key(selected) {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                        Text(licenseText ?: notices)
                        if (selected == null) {
                            licenses.forEach { name ->
                                TextButton(onClick = { selected = name }) { Text(name) }
                            }
                            TextButton(onClick = {
                                runCatching { uri.openUri("https://github.com/nahalewski/filamentmeter/tree/v0.1.0-beta.2") }
                                    .onFailure { error = true }
                            }) { Text("Get release source code") }
                            if (error) Text("Could not open a browser. The source URL is listed above.")
                        }
                    }
                }
            },
            dismissButton = { if (selected != null) TextButton(onClick = { selected = null }) { Text("Back") } },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}
