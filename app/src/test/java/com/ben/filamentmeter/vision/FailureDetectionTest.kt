package com.ben.filamentmeter.vision

import org.junit.Assert.*
import org.junit.Test

class FailureDetectionTest {
    @Test fun differentFailureClassHasIndependentAlertCooldown() {
        val gate=FailureGate()
        gate.sample(listOf(box(1)),0); gate.sample(listOf(box(1)),5000)
        assertEquals(1,gate.sample(listOf(box(1)),10000)?.kind)
        gate.sample(listOf(box(2)),15000);gate.sample(listOf(box(2)),20000)
        assertEquals(2,gate.sample(listOf(box(2)),25000)?.kind)
        gate.sample(listOf(box(0)),30000);gate.sample(listOf(box(0)),35000)
        assertEquals(0,gate.sample(listOf(box(0)),40000)?.kind)
    }
    private fun box(kind: Int = 1) = FailureBox(kind,.9f,.1f,.2f,.5f,.7f)
    @Test fun requiresThreeFreshSameClassSamples() {
        val gate = FailureGate()
        assertNull(gate.sample(listOf(box()),0))
        assertNull(gate.sample(listOf(box()),0))
        assertNull(gate.sample(listOf(box()),5000))
        assertNotNull(gate.sample(listOf(box()),10000))
        (3..70).forEach { assertNull(gate.sample(listOf(box()),it*5000L)) }
    }
    @Test fun intermittentClassesAndCameraGapsDoNotConfirmFailure() {
        val gate = FailureGate()
        assertNull(gate.sample(listOf(box(0)),0))
        assertNull(gate.sample(listOf(box(1)),5000))
        assertNull(gate.sample(listOf(box(2)),10000))
        assertNull(gate.sample(listOf(box(2)),15000))
        assertNull(gate.sample(listOf(box(2)),60000))
        assertNull(gate.sample(emptyList(),65000))
        assertNull(gate.sample(listOf(box(2)),70000))
    }
    @Test fun clearEpisodeAndCooldownAreRequiredForAnotherAlert() {
        val gate = FailureGate()
        gate.sample(listOf(box()),0); gate.sample(listOf(box()),5000)
        assertNotNull(gate.sample(listOf(box()),10000))
        (3..8).forEach { gate.sample(emptyList(),it*5000L) }
        (9..11).forEach { assertNull(gate.sample(listOf(box()),it*5000L)) }
        gate.sample(listOf(box()),310000)
        gate.sample(listOf(box()),315000)
        assertNotNull(gate.sample(listOf(box()),320000))
    }
    @Test fun decodeUsesScoresAndSuppressesOverlappingSameClassBoxes() {
        val values = floatArrayOf(.5f,.51f,.5f,.5f, .5f,.5f, .4f,.4f, .1f,.1f, .9f,.8f, .1f,.1f)
        val boxes = YoloOutput.decode(values,2,.7f)
        assertEquals(1,boxes.size)
        assertEquals(1,boxes.single().kind)
        assertEquals(.25f,boxes.single().left,.001f)
        assertEquals(.7f,boxes.single().bottom,.001f)
        assertTrue(YoloOutput.decode(values,2,.95f).isEmpty())
    }
    @Test fun malformedScoresCannotTrigger() {
        val values = floatArrayOf(.5f,.5f,.5f,.5f,Float.NaN,2f,-1f)
        assertTrue(YoloOutput.decode(values,1,.7f).isEmpty())
    }
}
