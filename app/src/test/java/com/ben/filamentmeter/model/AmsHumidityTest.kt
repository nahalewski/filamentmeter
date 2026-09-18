package com.ben.filamentmeter.model

import com.ben.filamentmeter.bambu.HardwareReport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AmsHumidityTest {
    @Test fun rawIndexMatchesBambuDisplayInDryToWetOrder() {
        val expected = listOf("E · Wet", "D", "C", "B", "A · Dry")
        (1..5).forEach { index ->
            assertEquals(expected[index - 1], AmsUnit(0, AmsModel.AMS, humidity = index.toString()).humidityLabel)
        }
        listOf("", "0", "6", "-1", "invalid").forEach {
            assertEquals("Unknown", AmsUnit(0, AmsModel.AMS, humidity = it).humidityLabel)
        }
    }

    @Test fun liveReportFiveIsAAndOmittedHumidityPreservesIt() {
        val parser = HardwareReport()
        val state = parser.applyPrint(PrinterState(), JSONObject("""{"ams":{"ams":[{"id":0,"sn":"006TEST","humidity":"5"}]}}"""))
        assertEquals("A · Dry", state.amsUnits.single().humidityLabel)
        val delta = parser.applyPrint(state, JSONObject("""{"ams":{"ams":[{"id":0,"temp":"25"}]}}"""))
        assertEquals("A · Dry", delta.amsUnits.single().humidityLabel)
    }

    @Test fun percentHumidityIsNeverTreatedAsAnIndex() {
        val parser = HardwareReport()
        val state = parser.applyPrint(PrinterState(), JSONObject("""{"ams":{"ams":[{"id":0,"sn":"19CTEST","humidity":"5","humidity_raw":"12"}]}}"""))
        assertEquals("12% RH", state.amsUnits.single().humidityLabel)
        val delta = parser.applyPrint(state, JSONObject("""{"ams":{"ams":[{"id":0,"temp":"25"}]}}"""))
        assertEquals("12% RH", delta.amsUnits.single().humidityLabel)
        val invalid = parser.applyPrint(state, JSONObject("""{"ams":{"ams":[{"id":0,"humidity_raw":"255"}]}}"""))
        assertEquals("Unknown", invalid.amsUnits.single().humidityLabel)
        assertEquals("Unknown", AmsUnit(0, AmsModel.AMS2, humidity = "5").humidityLabel)
    }
}
