package com.ben.filamentmeter

import androidx.test.platform.app.InstrumentationRegistry
import com.ben.filamentmeter.bambu.*
import com.ben.filamentmeter.data.SettingsStore
import com.ben.filamentmeter.model.PrinterState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only hardware integration test, explicitly run on a configured test phone. */
class CurrentPrintPreviewTest {
    @Test fun retrievesTheActualCurrentPrintPreview(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val config=SettingsStore(context).load()
        assertTrue("Test phone must have printer credentials",config.accessCode.isNotBlank())
        val ready=CountDownLatch(1)
        var state=PrinterState()
        val client=BambuMqttClient(onState={if(it.connected && it.jobFile.endsWith(".3mf",true)) {state=it;ready.countDown()}},onError={})
        try {
            client.connect(config)
            assertTrue("Printer must report the active archive",ready.await(25,TimeUnit.SECONDS))
            val image=PrintPreviewLoader.load(context,config,state)
            assertTrue(image.width>0 && image.height>0)
            java.io.File(context.filesDir,"current-print-preview-test.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            Unit
        } finally {client.disconnect()}
    }
}
