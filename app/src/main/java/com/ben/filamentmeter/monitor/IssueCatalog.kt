package com.ben.filamentmeter.monitor

import android.content.Context
import org.json.JSONObject

object IssueCatalog {
    private var descriptions: Map<String, String> = emptyMap()
    fun load(context: Context) {
        descriptions = runCatching {
            val data = JSONObject(context.assets.open("printer_errors.json").bufferedReader().use { it.readText() })
            data.keys().asSequence().associateWith { data.getString(it) }
        }.getOrDefault(emptyMap())
    }
    fun describe(code: String): String = descriptions[code.uppercase()]
        ?: "Printer reported an issue. Check its screen and open the official troubleshooting guide for this code."
}
