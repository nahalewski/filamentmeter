package com.ben.filamentmeter.monitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.ben.filamentmeter.MainActivity
import com.ben.filamentmeter.R

object AlertNotifications {
    const val EVENTS = "printer_events_sound_v1"
    data class Alert(val kind: Int, val channel: String, val name: String, val sound: String, val vibration: LongArray)
    val failures = listOf(
        Alert(1,"vision_spaghetti_v1","Spaghetti","alert_spaghetti",longArrayOf(0,150,100,150,100,150)),
        Alert(2,"vision_lifting_v1","Lifting / warping","alert_lifting",longArrayOf(0,400,180,400)),
        Alert(0,"vision_layer_shift_v1","Layer shift","alert_layer_shift",longArrayOf(0,150,120,450)))
    fun forKind(kind: Int) = failures.first { it.kind==kind }
    fun soundUri(context: Context, alert: Alert): Uri = Uri.parse("android.resource://${context.packageName}/raw/${alert.sound}")
    fun createChannels(context: Context) {
        val manager=context.getSystemService(NotificationManager::class.java)
        val audio=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        manager.createNotificationChannel(NotificationChannel(EVENTS,"Printer events · sound and vibration",NotificationManager.IMPORTANCE_DEFAULT).apply {
            description="Print started, completed, paused, or needs attention"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),audio)
            enableVibration(true); vibrationPattern=longArrayOf(0,250,120,250)
        })
        failures.forEach { alert ->
            manager.createNotificationChannel(NotificationChannel(alert.channel,"YOLO · ${alert.name}",NotificationManager.IMPORTANCE_HIGH).apply {
                description="Confirmed repeated detections of possible ${alert.name.lowercase()}; inspect the print"
                setSound(soundUri(context,alert),audio)
                enableVibration(true); vibrationPattern=alert.vibration
            })
        }
    }
    fun build(context: Context, kind: Int, warning: String, printerId: String, test: Boolean = false): Notification {
        val alert=forKind(kind)
        val open=PendingIntent.getActivity(context,12,Intent(context,MainActivity::class.java)
            .setData(Uri.parse("filamentmeter://vision/${Uri.encode(printerId)}/${alert.kind}"))
            .putExtra("printer_id",printerId).putExtra("printer_command","view")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context,alert.channel)
            .setSmallIcon(R.drawable.ic_bambu_printer)
            .setContentTitle(if(test) "Test alert · ${alert.name}" else "Possible ${alert.name.lowercase()}")
            .setContentText(warning)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if(test) warning else "$warning\nThe printer has not been paused. Tap to review the image."))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
    }
}
