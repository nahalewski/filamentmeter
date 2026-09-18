package com.ben.filamentmeter.monitor

import com.ben.filamentmeter.model.PrinterState

/** Connection gaps and percentage updates must never replay an event. */
class PrinterEvents {
    private var previous: PrinterState? = null
    fun reset() { previous = null }
    fun accept(state: PrinterState): String? {
        if (!state.connected) return null
        val old = previous
        previous = state
        if (state.issueText.isNotBlank() && state.issueText != old?.issueText) return "Printer needs attention"
        if (old == null) return null
        if (state.gcodeState == old.gcodeState && state.jobName == old.jobName) return null
        return when (state.gcodeState.uppercase()) {
            "PAUSE" -> "Print paused"
            "FINISH" -> if (old.isPrinting) "Print completed" else null
            "FAILED" -> "Print stopped or failed"
            "RUNNING", "PREPARE" -> when {
                old.canResume -> "Print resumed"
                !old.isPrinting || old.jobName != state.jobName -> "Print started"
                else -> null
            }
            else -> null
        }
    }
}
