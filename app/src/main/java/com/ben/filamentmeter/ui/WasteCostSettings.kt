package com.ben.filamentmeter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ben.filamentmeter.model.AppSettings
import java.util.Locale

@Composable
fun WasteCostSettings(
    purge: String, failed: String, scraps: String, settings: AppSettings,
    onPurge: (String) -> Unit, onFailed: (String) -> Unit, onScrap: (String) -> Unit
) {
    Text("FILAMENT WASTED", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text("Enter the total grams you have weighed for each category. These manual counters stay saved until you change them. Tap Save setup below to save.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    WasteField("Purging", purge, settings.purgingWasteCost, onPurge)
    WasteField("Failed prints", failed, settings.failedPrintWasteCost, onFailed)
    WasteField("Scraps", scraps, settings.scrapWasteCost, onScrap)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Total filament wasted", fontWeight = FontWeight.Bold)
            Text("${String.format(Locale.US, "%.1f", settings.totalWasteGrams)} g · ${wasteMoney(settings.totalWasteCost)}",
                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text("Estimated using the spool price and weight above. Changing the spool price recalculates all waste costs. Waste is tracked separately from the print estimate; count each piece only once.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WasteField(label: String, value: String, cost: Double, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, suffix = { Text("g") }, supportingText = { Text("Waste cost: ${wasteMoney(cost)}") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, shape = RoundedCornerShape(18.dp))
}

private fun wasteMoney(value: Double) = String.format(Locale.US, "$%.2f", value)
