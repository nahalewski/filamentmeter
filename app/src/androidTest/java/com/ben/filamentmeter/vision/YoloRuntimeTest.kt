package com.ben.filamentmeter.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class YoloRuntimeTest {
    @Test fun actualModelRunsAndFindsReferenceSpaghetti() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        YoloFailureDetector(instrumentation.targetContext).use { detector ->
            val blank = Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888)
            assertTrue(detector.detect(blank,.7f).isEmpty())
            blank.recycle()
            // Author's published demonstration screenshot: evaluate just the camera photo,
            // excluding terminal output and the WhatsApp notification beneath it.
            val original = instrumentation.context.assets.open("reference-spaghetti.png").use { BitmapFactory.decodeStream(it) }
            val photo = Bitmap.createBitmap(original,975,23,original.width-975,665)
            val boxes = detector.detect(photo,.7f)
            android.util.Log.i("PrintVisionTest", "Reference detections: $boxes")
            assertTrue("Reference photo should contain spaghetti at the default 70% confidence",boxes.any { it.kind == 1 })
            assertTrue(boxes.all { it.left in 0f..1f && it.right in 0f..1f && it.bottom > it.top })
            photo.recycle(); original.recycle()
        }
    }
}
