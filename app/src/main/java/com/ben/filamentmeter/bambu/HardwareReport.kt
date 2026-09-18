package com.ben.filamentmeter.bambu

import com.ben.filamentmeter.model.*
import org.json.JSONObject

/** Keeps identity and attachment telemetry distinct; omitted fields are delta updates. */
class HardwareReport {
    private val identities = mutableMapOf<Int, AmsModel>()

    fun applyInfo(state: PrinterState, info: JSONObject): PrinterState {
        val modules = info.optJSONArray("module") ?: return state
        var model = state.model
        for (i in 0 until modules.length()) {
            val module = modules.optJSONObject(i) ?: continue
            val name = module.optString("name")
            if (name == "ota" || name == "mc") {
                val reported = PrinterModel.identify(module.optString("product_name"), module.optString("sn"))
                if (reported != PrinterModel.UNKNOWN) model = reported
            }
            val type = AmsModel.identify(name, module.optString("sn"))
            val id = name.substringAfter('/', "").toIntOrNull()
            if (id != null && type != AmsModel.UNKNOWN) identities[id] = type
        }
        // Version inventory describes identity, not whether an accessory is still attached.
        return state.copy(model = model, amsUnits = state.amsUnits.map {
            it.copy(model = identities[it.id] ?: it.model)
        })
    }

    fun applyPrint(state: PrinterState, print: JSONObject): PrinterState {
        val ams = print.optJSONObject("ams") ?: return state
        val units = state.amsUnits.associateBy { it.id }.toMutableMap()
        val active = if (ams.has("tray_now")) ams.optInt("tray_now", 255).takeIf { it in 0..253 } else state.activeTrayId
        val list = ams.optJSONArray("ams")
        if (list != null && list.length() == 0) units.clear()
        if (list != null) for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: continue
            val id = entry.optInt("id", i)
            val old = units[id] ?: AmsUnit(id)
            val serialType = AmsModel.identify(serial = entry.optString("sn"))
            if (serialType != AmsModel.UNKNOWN) identities[id] = serialType
            val type = identities[id] ?: old.model
            val trays = old.trays.associateBy { it.id }.toMutableMap()
            entry.optJSONArray("tray")?.let { array ->
                if (array.length() == 0) trays.clear()
                for (j in 0 until array.length()) {
                    val tray = array.optJSONObject(j) ?: continue
                    val trayId = tray.optInt("id", j)
                    val prior = trays[trayId]
                    val raw = tray.optString("tray_color", prior?.colorHex ?: "888888").removePrefix("#")
                    trays[trayId] = AmsTray(trayId,
                        if (raw.length >= 6) "#${raw.take(6)}" else "#888888",
                        tray.optString("tray_type", prior?.type ?: "Empty").ifBlank { "Empty" },
                        tray.optString("tray_sub_brands", prior?.subBrands ?: ""))
                }
            }
            units[id] = old.copy(model = type, trays = trays.values.sortedBy { it.id },
                humidity = entry.optString("humidity", old.humidity),
                humidityPercent = if (entry.has("humidity_raw"))
                    entry.optString("humidity_raw").toIntOrNull()?.takeIf { it in 0..100 }
                    else old.humidityPercent,
                temperature = entry.optString("temp", old.temperature))
        }
        if (ams.has("ams_exist_bits")) {
            // Bambu reports this mask as hexadecimal text. Bits 0–3 are four-slot
            // units; bits 4–11 are single-slot HT units with device IDs 128–135.
            val rawBits = ams.opt("ams_exist_bits")
            val bits = if (rawBits is Number) rawBits.toLong() else
                rawBits?.toString()?.removePrefix("0x")?.toLongOrNull(16)
            if (bits != null && bits >= 0) {
                for (bit in 0..11) {
                    val id = if (bit < 4) bit else 128 + bit - 4
                    if (bits and (1L shl bit) == 0L) units.remove(id)
                    else if (id !in units) units[id] = AmsUnit(id,
                        identities[id] ?: if (id >= 128) AmsModel.HT else AmsModel.UNKNOWN)
                }
            }
        }
        val result = units.values.sortedBy { it.id }.map { unit ->
            unit.copy(trays = unit.trays.map {
                it.copy(active = if (unit.id >= 128) active == unit.id else active == unit.id * 4 + it.id)
            })
        }
        (state.amsUnits.map { it.id } - units.keys).forEach { identities.remove(it) }
        val first = result.firstOrNull()
        return state.copy(amsDetected = state.amsDetected || list != null || ams.has("ams_exist_bits"),
            amsUnits = result, amsTrays = first?.trays ?: emptyList(),
            amsHumidity = first?.humidity ?: "", amsTemp = first?.temperature ?: "", activeTrayId = active)
    }
}
