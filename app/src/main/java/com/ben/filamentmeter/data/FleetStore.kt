package com.ben.filamentmeter.data

import android.content.Context
import com.ben.filamentmeter.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PrinterProfile(val settings: AppSettings, val name: String, val model: String) {
    val id get() = settings.serialNumber
}
data class CostRecord(val printerId: String, val job: String, val kind: String,
    val started: Long, val ended: Long = 0, val grams: Double, val pricePerGram: Double,
    val progress: Int = 0, val status: String = "", val jobId: String = "") {
    val cost get() = grams * pricePerGram * if (kind == "Print") progress / 100.0 else 1.0
}

/** Shared on the main thread by the service and settings UI; records survive process restarts. */
class FleetStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("printer_fleet", 0)
    companion object { val revision = MutableStateFlow(0L) }
    fun profiles(): List<PrinterProfile> {
        if (!prefs.contains("profiles")) {
            val old = SettingsStore(context).load()
            if (old.serialNumber.isNotBlank()) save(old)
            else prefs.edit().putString("profiles", "[]").apply()
        }
        val array = JSONArray(prefs.getString("profiles", "[]"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PrinterProfile(AppSettings(o.optString("ip"), o.getString("serial"), o.optString("code"),
                o.optDouble("price",24.99), o.optDouble("weight",1000.0), o.optDouble("grams",100.0),
                o.optDouble("purge",0.0), o.optDouble("failed",0.0), o.optDouble("scrap",0.0)),
                o.optString("name"), o.optString("model"))
        }
    }
    fun save(s: AppSettings, name: String? = null, model: String? = null) {
        if (s.serialNumber.isBlank()) return
        val all = if (prefs.contains("profiles")) profiles() else emptyList()
        val old = all.find { it.id == s.serialNumber }
        val next = PrinterProfile(s, name?.takeIf { it.isNotBlank() } ?: old?.name
            ?: "${PrinterModel.identify(serial=s.serialNumber).label} ${all.size+1}", model ?: old?.model ?: "")
        val updated = all.filterNot { it.id == next.id } + next
        prefs.edit().putString("profiles", JSONArray().apply { updated.forEach { p ->
            val a = p.settings
            put(JSONObject().put("ip",a.printerIp).put("serial",a.serialNumber).put("code",a.accessCode)
                .put("price",a.spoolPrice).put("weight",a.spoolWeightGrams).put("grams",a.jobFilamentGrams)
                .put("purge",a.purgingWasteGrams).put("failed",a.failedPrintWasteGrams).put("scrap",a.scrapWasteGrams)
                .put("name",p.name).put("model",p.model))
        } }.toString()).apply()
        val before = old?.settings ?: AppSettings()
        listOf(Triple("Purging",s.purgingWasteGrams,before.purgingWasteGrams),
            Triple("Failed prints",s.failedPrintWasteGrams,before.failedPrintWasteGrams),
            Triple("Scraps",s.scrapWasteGrams,before.scrapWasteGrams)).forEach { (kind,now,previous) ->
            if (now != previous) append(CostRecord(s.serialNumber, if (old == null) "Opening balance" else "Counter adjustment",
                kind,System.currentTimeMillis(),System.currentTimeMillis(),now-previous,s.pricePerGram))
        }
        revision.value++
    }
    fun records(): List<CostRecord> {
        val a = JSONArray(prefs.getString("records","[]"))
        return (0 until a.length()).map { i -> val o=a.getJSONObject(i)
            CostRecord(o.getString("printer"),o.getString("job"),o.getString("kind"),o.getLong("start"),
                o.optLong("end"),o.getDouble("grams"),o.getDouble("rate"),o.optInt("progress"),o.optString("status"),o.optString("jobId")) }
    }
    private fun write(records: List<CostRecord>) {
        prefs.edit().putString("records",JSONArray().apply { records.forEach { r -> put(JSONObject()
            .put("printer",r.printerId).put("job",r.job).put("kind",r.kind).put("start",r.started).put("end",r.ended)
            .put("grams",r.grams).put("rate",r.pricePerGram).put("progress",r.progress).put("status",r.status).put("jobId",r.jobId)) } }.toString()).apply()
        revision.value++
    }
    private fun append(record: CostRecord) = write(records()+record)
    fun observe(s: AppSettings, state: PrinterState) {
        if (!state.connected || state.gcodeState.isBlank()) return
        val records = records().toMutableList()
        val index = records.indexOfLast { it.printerId==s.serialNumber && it.kind=="Print" && it.ended==0L }
        val old = records.getOrNull(index)
        val differentJob = old != null && old.jobId.isNotBlank() && state.jobId.isNotBlank() && old.jobId != state.jobId
        if (old != null && (differentJob || old.job != state.jobName || (!state.isPrinting && state.gcodeState !in listOf("FINISH","FAILED")))) {
            records[index] = old.copy(ended=System.currentTimeMillis(),status="End not observed")
            write(records)
            if (state.isPrinting) observe(s,state)
            return
        }
        if (old == null && !state.isPrinting) return
        val current = old ?: CostRecord(s.serialNumber,state.jobName,"Print",System.currentTimeMillis(),
            grams=s.jobFilamentGrams,pricePerGram=s.pricePerGram)
        val ended = state.gcodeState in listOf("FINISH","FAILED")
        val next = current.copy(progress=if(state.gcodeState=="FINISH") 100 else state.progressPercent.coerceIn(0,100),
            ended=if(ended) System.currentTimeMillis() else 0, status=state.gcodeState, jobId=state.jobId.ifBlank { current.jobId })
        if (next == old) return
        if (index >= 0) records[index]=next else records.add(next)
        write(records)
    }
}
