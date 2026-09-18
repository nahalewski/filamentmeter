package com.ben.filamentmeter.vision

data class FailureBox(val kind: Int, val confidence: Float, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val label get() = listOf("Layer shift", "Spaghetti / loose filament", "Warping / lifting")[kind]
}

object YoloOutput {
    /** Exported YOLO detect head: normalized xywh, followed by three class scores. */
    fun decode(values: FloatArray, count: Int, threshold: Float): List<FailureBox> {
        require(values.size == 7 * count)
        val candidates = (0 until count).mapNotNull { i ->
            val kind = (0..2).maxBy { values[(it + 4) * count + i] }
            val confidence = values[(kind + 4) * count + i]
            val x = values[i]; val y = values[count + i]
            val w = values[2 * count + i]; val h = values[3 * count + i]
            if (!confidence.isFinite() || confidence !in threshold..1f || !x.isFinite() || !y.isFinite() ||
                !w.isFinite() || !h.isFinite() || w <= 0 || h <= 0) null
            else FailureBox(kind, confidence, (x-w/2).coerceIn(0f,1f), (y-h/2).coerceIn(0f,1f),
                (x+w/2).coerceIn(0f,1f), (y+h/2).coerceIn(0f,1f)).takeIf { it.right > it.left && it.bottom > it.top }
        }.sortedByDescending { it.confidence }.take(300)
        val selected = mutableListOf<FailureBox>()
        for (box in candidates) {
            if (selected.none { it.kind == box.kind && overlap(it, box) > .45f }) selected += box
            if (selected.size == 12) break
        }
        return selected
    }
    private fun overlap(a: FailureBox, b: FailureBox): Float {
        val intersection = (minOf(a.right,b.right)-maxOf(a.left,b.left)).coerceAtLeast(0f) *
            (minOf(a.bottom,b.bottom)-maxOf(a.top,b.top)).coerceAtLeast(0f)
        val union = (a.right-a.left)*(a.bottom-a.top)+(b.right-b.left)*(b.bottom-b.top)-intersection
        return if (union > 0) intersection / union else 0f
    }
}

/** Require the same class in 3 fresh consecutive samples; alert once per episode. */
class FailureGate {
    private val streaks = IntArray(3)
    private var lastSample = -1L
    private val lastAlert = LongArray(3) { -1L }
    private val latched = BooleanArray(3)
    private val clearCount = IntArray(3)
    fun reset() { streaks.fill(0); lastSample = -1; lastAlert.fill(-1L); latched.fill(false); clearCount.fill(0) }
    fun sample(boxes: List<FailureBox>, now: Long): FailureBox? {
        if (lastSample >= 0 && now - lastSample > 20_000) { streaks.fill(0); clearCount.fill(0) }
        if (lastSample >= 0 && now <= lastSample) return null
        lastSample = now
        val classes = boxes.map { it.kind }.toSet()
        for (i in streaks.indices) {
            streaks[i] = if (i in classes) streaks[i] + 1 else 0
            clearCount[i] = if (i in classes) 0 else clearCount[i]+1
            if (clearCount[i] >= 6) latched[i] = false
        }
        val match = boxes.firstOrNull { streaks[it.kind] >= 3 && !latched[it.kind] &&
            (lastAlert[it.kind] < 0 || now-lastAlert[it.kind] >= 300_000) } ?: return null
        latched[match.kind] = true; lastAlert[match.kind] = now
        return match
    }
}
