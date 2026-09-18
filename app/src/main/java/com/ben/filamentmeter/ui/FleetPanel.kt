package com.ben.filamentmeter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ben.filamentmeter.data.*
import com.ben.filamentmeter.model.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun money(value: Double) = String.format(Locale.US,"$%.2f",value)

@Composable
fun PrinterSelector(profiles: List<PrinterProfile>, active: String, onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=4.dp)) {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),enabled=profiles.isNotEmpty()) {
            Text("Active printer: ${profiles.find { it.id==active }?.name ?: "Choose printer"} ▾")
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {
            profiles.forEach { p -> DropdownMenuItem(text={Text(p.name)},onClick={expanded=false;onSelect(p.id)}) }
        }
    }
}

@Composable
fun FleetPanel(profiles: List<PrinterProfile>, states: Map<String,PrinterState>, active: String,
    records: List<CostRecord>, onSelect: (String)->Unit, detailed: Boolean = false) {
    Text("YOUR PRINTERS",style=MaterialTheme.typography.titleLarge)
    if(profiles.isEmpty()) Text("Add your first printer in Setup. Each printer needs its own LAN access code.")
    profiles.forEach { p ->
        val state = states[p.id] ?: PrinterState(model=PrinterModel.identify(p.model,p.id))
        var expanded by remember(p.id) { mutableStateOf(false) }
        var visibleRecords by remember(p.id) { mutableIntStateOf(20) }
        val history = records.filter { it.printerId==p.id && it.kind=="Print" }
        val waste = records.filter { it.printerId==p.id && it.kind!="Print" }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row {
                    (state.model.artwork() ?: PrinterModel.identify(p.model,p.id).artwork())?.let {
                        Image(painterResource(it),p.name,Modifier.size(84.dp))
                    }
                    Column(Modifier.weight(1f).padding(start=8.dp)) {
                        Text(p.name,style=MaterialTheme.typography.titleMedium)
                        Text(if(state.connected) state.displayStatus else "Offline / connecting")
                        if(state.isPrinting) {
                            Text("${state.jobName} · ${state.progressPercent}%")
                            LinearProgressIndicator(progress={state.progressPercent/100f},modifier=Modifier.fillMaxWidth())
                        }
                        Text("${history.size} observed prints · ${money(history.sumOf { it.cost })} estimated material")
                    }
                }
                TextButton(onClick={onSelect(p.id)},enabled=p.id!=active) { Text(if(p.id==active) "Active printer" else "Select printer") }
                if(detailed) {
                    Text("Current estimate: ${money(p.settings.estimatedJobCost)} per print · ${p.settings.jobFilamentGrams} g")
                    Text("Filament: ${money(p.settings.spoolPrice)} / ${p.settings.spoolWeightGrams} g")
                    Text("Recorded waste: ${money(waste.sumOf { it.cost })} · ${String.format(Locale.US,"%.1f",waste.sumOf { it.grams })} g")
                    listOf("Purging","Failed prints","Scraps").forEach { category ->
                        Text("$category: ${money(waste.filter { it.kind==category }.sumOf { it.cost })}",style=MaterialTheme.typography.bodySmall)
                    }
                    val finished = history.filter { it.ended > 0 }
                    if(finished.isNotEmpty()) Text("Average observed print: ${money(finished.sumOf { it.cost }/finished.size)} (including stopped prints)")
                    TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide history and waste records" else "Print history and waste records") }
                    if(expanded) {
                        Text("Estimates use the price and sliced grams saved when first observed. Electricity and machine costs are not included. Waste is reported separately and may overlap failed-print material.",style=MaterialTheme.typography.bodySmall)
                        if(history.isEmpty()) Text("No observed prints yet. Earlier printer history is not imported.")
                        history.sortedByDescending { it.started }.take(visibleRecords).forEach { r ->
                            HorizontalDivider()
                            Text(r.job,style=MaterialTheme.typography.titleSmall)
                            Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(r.started))} · ${r.status}")
                            Text("${r.progress}% · ${money(r.cost)} estimated used / ${money(r.grams*r.pricePerGram)} full print")
                        }
                        Text("WASTE RECORDS",style=MaterialTheme.typography.titleSmall)
                        if(waste.isEmpty()) Text("No waste recorded. Adjust this printer’s waste counters below and save.")
                        waste.sortedByDescending { it.started }.take(visibleRecords).forEach { r ->
                            Text("${DateFormat.getDateInstance().format(Date(r.started))} · ${r.kind}: ${r.grams} g · ${money(r.cost)} · ${r.job}")
                        }
                        if(history.size>visibleRecords || waste.size>visibleRecords)
                            TextButton(onClick={visibleRecords+=20}) { Text("Show older records") }
                    }
                }
            }
        }
    }
}
