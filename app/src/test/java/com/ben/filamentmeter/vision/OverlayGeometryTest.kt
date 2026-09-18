package com.ben.filamentmeter.vision

import org.junit.Assert.*
import org.junit.Test

class OverlayGeometryTest {
    private val center=FailureBox(1,.8f,.25f,.25f,.75f,.75f)
    @Test fun fitAccountsForLetterboxing() {
        val r=mapDetection(center,1600f,900f,1000f,1000f,true,1f)!!
        assertEquals(250f,r.left,.01f); assertEquals(359.375f,r.top,.01f)
        assertEquals(750f,r.right,.01f); assertEquals(640.625f,r.bottom,.01f)
    }
    @Test fun cropAndZoomMatchCenteredImage() {
        val r=mapDetection(center,1600f,900f,1000f,1000f,false,2f)!!
        assertEquals(0f,r.left,.01f); assertEquals(0f,r.top,.01f)
        assertEquals(1000f,r.right,.01f); assertEquals(1000f,r.bottom,.01f)
    }
    @Test fun offscreenBoxesAndInvalidViewAreNotDrawn() {
        assertNull(mapDetection(center.copy(left=0f,right=.1f),1600f,900f,1000f,1000f,false,3f))
        assertNull(mapDetection(center,0f,900f,1000f,1000f,true,1f))
    }
    @Test fun oldAndFutureResultsAreHidden() {
        assertTrue(detectionOverlayFresh(1000,13000))
        assertFalse(detectionOverlayFresh(1000,13001))
        assertFalse(detectionOverlayFresh(0,1000))
        assertFalse(detectionOverlayFresh(2000,1000))
    }
}
