package com.ben.filamentmeter

import android.app.NotificationManager
import android.media.MediaPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.ben.filamentmeter.monitor.AlertNotifications
import org.junit.Assert.*
import org.junit.Test

class AlertNotificationsTest {
    @Test fun distinctSoundsVibrationsAndNotificationRouting() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(NotificationManager::class.java)
        AlertNotifications.createChannels(context)
        assertTrue(manager.areNotificationsEnabled())
        val sounds=mutableSetOf<String>()
        for(alert in AlertNotifications.failures) {
            val channel=manager.getNotificationChannel(alert.channel)
            assertEquals(NotificationManager.IMPORTANCE_HIGH,channel.importance)
            assertTrue(channel.shouldVibrate())
            assertArrayEquals(alert.vibration,channel.vibrationPattern)
            assertEquals(AlertNotifications.soundUri(context,alert),channel.sound)
            sounds.add(channel.sound.toString())
            val player=MediaPlayer()
            try {
                player.setDataSource(context,channel.sound);player.prepare()
                assertTrue("Sound must decode into a short playable clip",player.duration in 400..2000)
            } finally { player.release() }
            val notification=AlertNotifications.build(context,alert.kind,
                "Sound and vibration test only — no failure detected.","",true)
            assertEquals(alert.channel,notification.channelId)
            val id=200+alert.kind
            try {
                manager.notify(id,notification)
                android.os.SystemClock.sleep(1600)
                assertTrue(manager.activeNotifications.any { it.id==id && it.notification.channelId==alert.channel })
            } finally { manager.cancel(id) }
        }
        assertEquals(3,sounds.size)
        val events=manager.getNotificationChannel(AlertNotifications.EVENTS)
        assertTrue(events.shouldVibrate());assertNotNull(events.sound)
        manager.getNotificationChannel("printer_progress")?.let {
            assertNull(it.sound);assertFalse(it.shouldVibrate())
        }
    }
}
