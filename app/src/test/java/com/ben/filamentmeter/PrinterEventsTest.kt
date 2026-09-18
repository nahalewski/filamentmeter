package com.ben.filamentmeter

import com.ben.filamentmeter.model.PrinterState
import com.ben.filamentmeter.monitor.PrinterEvents
import org.junit.Assert.*
import org.junit.Test

class PrinterEventsTest {
    private val idle = PrinterState(connected = true)
    @Test fun progressIsQuietAndEventsAreOnce() {
        val events = PrinterEvents()
        assertNull(events.accept(idle))
        val print = idle.copy(gcodeState = "RUNNING", jobName = "Test")
        assertEquals("Print started", events.accept(print))
        (1..80).forEach { assertNull(events.accept(print.copy(progressPercent = it))) }
        assertEquals("Print paused", events.accept(print.copy(gcodeState = "PAUSE")))
        assertNull(events.accept(print.copy(gcodeState = "PAUSE", progressPercent = 80)))
        assertEquals("Print resumed", events.accept(print))
        assertEquals("Print completed", events.accept(print.copy(gcodeState = "FINISH")))
        assertNull(events.accept(print.copy(gcodeState = "FINISH")))
    }
    @Test fun reconnectDoesNotRepeatStartOrHistoricalCompletion() {
        val events = PrinterEvents()
        val print = idle.copy(gcodeState = "RUNNING")
        assertNull(events.accept(print))
        assertNull(events.accept(print.copy(connected = false)))
        assertNull(events.accept(print))
        events.reset()
        assertNull(events.accept(print.copy(gcodeState = "FINISH")))
    }
    @Test fun newIssueAlertsButProgressDoesNotRepeatIt() {
        val events = PrinterEvents()
        events.accept(idle)
        val error = idle.copy(gcodeState = "PAUSE", issueText = "Filament ran out")
        assertEquals("Printer needs attention", events.accept(error))
        assertNull(events.accept(error.copy(progressPercent = 50)))
        assertNull(events.accept(error.copy(connected = false)))
        assertNull(events.accept(error))
        assertEquals("Printer needs attention", events.accept(error.copy(issueText = "Feed jam")))
    }
}
