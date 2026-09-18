package com.ben.filamentmeter

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.ben.filamentmeter.data.*
import com.ben.filamentmeter.model.*
import org.junit.Assert.*
import org.junit.Test

class FleetStoreTest {
    private fun isolated(): Context = object: ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val prefix = "test_fleet_${System.nanoTime()}_"
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix+name,mode)
    }
    @Test fun legacyProfileAndWasteMigrateOnce() {
        val context=isolated()
        SettingsStore(context).save(AppSettings(serialNumber="ONE",purgingWasteGrams=12.0))
        val fleet=FleetStore(context)
        assertEquals(1,fleet.profiles().size)
        assertEquals(1,FleetStore(context).profiles().size)
        assertEquals(12.0,fleet.records().sumOf { it.grams },.001)
        fleet.save(fleet.profiles().first().settings)
        assertEquals(1,fleet.records().size)
    }
    @Test fun printersAndPriceSnapshotsRemainSeparateAcrossRestarts() {
        val context=isolated(); val fleet=FleetStore(context)
        val one=AppSettings(serialNumber="ONE",spoolPrice=20.0,jobFilamentGrams=100.0)
        val two=one.copy(serialNumber="TWO",spoolPrice=40.0)
        fleet.save(one,"Workshop","H2S");fleet.save(two,"Office","P1S")
        val running=PrinterState(connected=true,gcodeState="RUNNING",jobName="Same job",progressPercent=50)
        fleet.observe(one,running);fleet.observe(two,running)
        fleet.save(one.copy(spoolPrice=80.0))
        FleetStore(context).observe(one.copy(spoolPrice=80.0),running.copy(gcodeState="FINISH",progressPercent=100))
        val records=fleet.records()
        assertEquals(2,records.size)
        assertEquals(2.0,records.first { it.printerId=="ONE" }.cost,.001)
        assertEquals(2.0,records.first { it.printerId=="TWO" }.cost,.001)
        assertEquals("Workshop",fleet.profiles().first { it.id=="ONE" }.name)
        fleet.observe(one,running.copy(gcodeState="FINISH",progressPercent=100))
        assertEquals(2,fleet.records().size)
    }
    @Test fun staleFinishAndOfflineDoNotInventRecords() {
        val fleet=FleetStore(isolated()); val config=AppSettings(serialNumber="ONE")
        fleet.observe(config,PrinterState(connected=true,gcodeState="FINISH",jobName="Old print"))
        fleet.observe(config,PrinterState(connected=false,gcodeState="RUNNING",jobName="Offline"))
        assertTrue(fleet.records().isEmpty())
    }
    @Test fun repeatedFileWithNewTaskIdIsANewRecord() {
        val fleet=FleetStore(isolated()); val config=AppSettings(serialNumber="ONE")
        val first=PrinterState(connected=true,gcodeState="RUNNING",jobName="Repeated file",jobId="123",progressPercent=50)
        fleet.observe(config,first)
        fleet.observe(config,first.copy(jobId="456",progressPercent=10))
        assertEquals(2,fleet.records().size)
        assertEquals("End not observed",fleet.records().first().status)
        assertEquals(0L,fleet.records().last().ended)
    }
    @Test fun wasteAdjustmentsKeepTheirPrices() {
        val fleet=FleetStore(isolated())
        val config=AppSettings(serialNumber="ONE",spoolPrice=20.0,purgingWasteGrams=10.0)
        fleet.save(config);fleet.save(config.copy(spoolPrice=40.0,purgingWasteGrams=15.0))
        assertEquals(2,fleet.records().size)
        assertEquals(.4,fleet.records().sumOf { it.cost },.001)
        assertEquals(15.0,fleet.records().sumOf { it.grams },.001)
    }
    @Test fun h2sDiscoveryModelUsesH2sArtwork() {
        assertEquals(PrinterModel.H2S,PrinterModel.identify("O1S"))
        assertEquals(PrinterModel.H2S,PrinterModel.identify("Bambu Lab H2S"))
        assertNotEquals(0,R.drawable.hardware_h2s)
    }
}
