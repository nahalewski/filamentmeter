package com.ben.filamentmeter.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ben.filamentmeter.data.SettingsStore
import com.ben.filamentmeter.monitor.AlertNotifications

@Composable
fun AlertSoundSettings() {
    val context=LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { AlertNotifications.createChannels(context) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Alert sounds & vibration",style=MaterialTheme.typography.titleMedium)
            Text("Spaghetti: three quick tones. Lifting: two rising tones. Layer shift: high then low. Each has a different vibration pattern.",style=MaterialTheme.typography.bodySmall)
            AlertNotifications.failures.forEach { alert ->
                Text(alert.name,style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={
                        AlertNotifications.createChannels(context)
                        val manager=context.getSystemService(NotificationManager::class.java)
                        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                            manager.getNotificationChannel(alert.channel)?.importance == NotificationManager.IMPORTANCE_NONE) {
                            message="Notifications are blocked. Open notification settings to allow alerts."
                        } else {
                            runCatching { manager.notify(200+alert.kind,AlertNotifications.build(context,alert.kind,
                                "Sound and vibration test only — no print failure detected.",SettingsStore(context).load().serialNumber,true)) }
                                .onSuccess { message="${alert.name} test sent. Sound follows your phone's notification volume and Do Not Disturb settings." }
                                .onFailure { message="Could not send test. Check notification permission in Settings." }
                        }
                    }) { Text("Test alert") }
                    TextButton(onClick={
                        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,alert.channel))
                    }) { Text("Sound & vibration") }
                }
            }
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,AlertNotifications.EVENTS))}) {
                Text("Printer event sound & vibration")
            }
            Text("Progress stays silent. Android notification permission, notification volume, silent mode and Do Not Disturb still apply.",style=MaterialTheme.typography.bodySmall)
            message?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
