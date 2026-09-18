package com.ben.filamentmeter.model

import com.ben.filamentmeter.bambu.HardwareReport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HardwareReportTest {
    @Test fun fourUnitsHaveIndependentTraysAndActiveSource() {
        val parser = HardwareReport()
        val units = org.json.JSONArray()
        for (id in 0..3) units.put(JSONObject().put("id", id)
            .put("sn", if (id % 2 == 0) "006TEST$id" else "19CTEST$id")
            .put("humidity", (id + 1).toString()).put("humidity_raw", 10 + id)
            .put("tray", org.json.JSONArray().put(JSONObject().put("id", 0).put("tray_type", "PLA"))))
        val state = parser.applyPrint(PrinterState(connected = true), JSONObject().put("ams",
            JSONObject().put("ams_exist_bits", "f").put("tray_now", "12").put("ams", units)))
        assertEquals(listOf(0, 1, 2, 3), state.amsUnits.map { it.id })
        assertEquals("4 AMS units connected", state.amsCountLabel)
        assertEquals(listOf(false, false, false, true), state.amsUnits.map { it.isActive })
        assertEquals(listOf("1", "2", "3", "4"), state.amsUnits.map { it.humidity })
        val partial = parser.applyPrint(state, JSONObject("""{"ams":{"ams":[{"id":2,"temp":"32"}]}}"""))
        assertEquals(4, partial.amsUnits.size)
        assertEquals("32", partial.amsUnits[2].temperature)
        val detached = parser.applyPrint(partial, JSONObject("""{"ams":{"ams_exist_bits":"5"}}"""))
        assertEquals(listOf(0, 2), detached.amsUnits.map { it.id })
        assertEquals("2 AMS units connected", detached.amsCountLabel)
    }

    @Test fun eightMixedUnitsUseHtIdsAndHexAttachmentMask() {
        val parser = HardwareReport()
        val state = parser.applyPrint(PrinterState(connected = true), JSONObject("""{"ams":{"ams_exist_bits":"ff"}}"""))
        assertEquals(listOf(0, 1, 2, 3, 128, 129, 130, 131), state.amsUnits.map { it.id })
        assertEquals("8 AMS units connected", state.amsCountLabel)
        assertEquals("AMS HT 1", state.amsUnits[4].displayName)
        val active = parser.applyPrint(state, JSONObject("""{"ams":{"tray_now":"128","ams":[{"id":128,"sn":"19FTEST","tray":[{"id":0}]}]}}"""))
        assertTrue(active.amsUnits.first { it.id == 128 }.isActive)
        // Hex 10 is bit 4 (HT 1), not decimal 10 (four-slot units 2 and 4).
        val remaining = parser.applyPrint(active, JSONObject("""{"ams":{"ams_exist_bits":"10"}}"""))
        assertEquals(128, remaining.amsUnits.single().id)
        assertEquals("1 AMS unit connected", remaining.amsCountLabel)
    }

    @Test fun identifiesEverySuppliedPrinterAndUnknowns() {
        mapOf("01P" to PrinterModel.P1S, "22E" to PrinterModel.P2S,
            "00M" to PrinterModel.X1C, "030" to PrinterModel.A1_MINI).forEach { (prefix, expected) ->
            assertEquals(expected, PrinterModel.identify(serial = "${prefix}TEST"))
        }
        assertEquals(PrinterModel.P2S, PrinterModel.identify("C13"))
        assertEquals(PrinterModel.UNKNOWN, PrinterModel.identify(serial = "UNKNOWN"))
        assertTrue(PrinterState().amsTrays.isEmpty())
        assertFalse(PrinterState().amsDetected)
    }

    @Test fun mixedAmsModelsResolveEvenWhenInfoArrivesAfterTelemetry() {
        val parser = HardwareReport()
        var state = parser.applyPrint(PrinterState(), JSONObject("""{"ams":{"ams_exist_bits":"3","tray_now":"4","ams":[{"id":"0","tray":[{"id":"0","tray_type":"PLA","tray_color":"FF0000FF"}]},{"id":"1","tray":[{"id":"0","tray_type":"PETG"}]}]}}"""))
        assertEquals(2, state.amsUnits.size)
        assertEquals(AmsModel.UNKNOWN, state.amsUnits[0].model)
        state = parser.applyInfo(state, JSONObject("""{"module":[{"name":"ams/0","sn":"006TEST"},{"name":"n3f/1","sn":"19CTEST"}]}"""))
        assertEquals(AmsModel.AMS, state.amsUnits[0].model)
        assertEquals(AmsModel.AMS2, state.amsUnits[1].model)
        assertTrue(state.amsUnits[1].trays[0].active)
        assertFalse(state.amsUnits[0].trays[0].active)
        val delta = parser.applyPrint(state, JSONObject("""{"ams":{"ams":[{"id":"1","temp":"42"}]}}"""))
        assertEquals(2, delta.amsUnits.size)
        assertEquals("PETG", delta.amsUnits[1].trays[0].type)
        assertEquals(4, delta.activeTrayId)
    }

    @Test fun detachClearsHardwareWithoutReintroducingVersionInventory() {
        val parser = HardwareReport()
        val info = JSONObject("""{"module":[{"name":"ams/0","sn":"006TEST"}]}""")
        var state = parser.applyInfo(PrinterState(), info)
        state = parser.applyPrint(state, JSONObject("""{"ams":{"ams_exist_bits":"1"}}"""))
        assertEquals(AmsModel.AMS, state.amsUnits.single().model)
        state = parser.applyPrint(state, JSONObject("""{"ams":{"ams_exist_bits":"0"}}"""))
        state = parser.applyInfo(state, info)
        assertTrue(state.amsUnits.isEmpty())
        assertTrue(state.amsTrays.isEmpty())
        assertTrue(state.amsDetected)
        assertEquals("No AMS connected", state.copy(connected = true).amsDescription)
    }

    @Test fun omittedAccessoryDataDoesNotClearPreviouslyReportedHardware() {
        val parser = HardwareReport()
        val state = parser.applyPrint(PrinterState(), JSONObject("""{"ams":{"ams":[{"id":0,"sn":"19CTEST"}]}}"""))
        assertEquals(state, parser.applyPrint(state, JSONObject("""{"mc_percent":42}""")))
        assertEquals(AmsModel.AMS2, state.amsUnits.single().model)
        assertTrue(parser.applyPrint(state, JSONObject("""{"ams":{"ams":[]}}""")).amsUnits.isEmpty())
    }
}
