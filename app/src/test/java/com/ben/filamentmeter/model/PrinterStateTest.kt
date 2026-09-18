package com.ben.filamentmeter.model

import org.junit.Assert.*
import org.junit.Test

class PrinterStateTest {
    @Test fun offlinePrinterNeverAllowsCommands() {
        listOf("RUNNING", "PAUSE", "PREPARE", "IDLE", "FINISH").forEach { state ->
            val printer = PrinterState(gcodeState = state)
            assertFalse(printer.canResume)
            assertFalse(printer.canPause)
            assertFalse(printer.canStop)
            assertEquals("Offline", printer.displayStatus)
        }
    }

    @Test fun controlsFollowActualJobState() {
        val running = PrinterState(connected = true, gcodeState = "RUNNING")
        assertTrue(running.canPause)
        assertTrue(running.canStop)
        assertFalse(running.canResume)
        val paused = running.copy(gcodeState = "PAUSE")
        assertTrue(paused.canResume)
        assertTrue(paused.canStop)
        assertFalse(paused.canPause)
        listOf("IDLE", "FINISH", "FAILED").forEach { state ->
            val printer = running.copy(gcodeState = state)
            assertFalse(printer.canResume || printer.canPause || printer.canStop)
        }
    }

    @Test fun statusIncludesCompletionAndErrors() {
        assertEquals("Completed", PrinterState(connected = true, gcodeState = "FINISH").displayStatus)
        assertEquals("Error", PrinterState(connected = true, printError = 1).displayStatus)
        assertEquals("Paused", PrinterState(connected = true, gcodeState = "pause").displayStatus)
        assertEquals("Printing", PrinterState(connected = true, gcodeState = "PREPARE").displayStatus)
    }
}
