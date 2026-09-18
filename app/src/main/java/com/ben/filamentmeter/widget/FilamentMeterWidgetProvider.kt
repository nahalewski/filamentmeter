package com.ben.filamentmeter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.view.View
import android.content.res.Configuration
import android.widget.RemoteViews
import com.ben.filamentmeter.MainActivity
import com.ben.filamentmeter.R
import com.ben.filamentmeter.data.SettingsStore
import com.ben.filamentmeter.ui.AmsVisualRenderer
import com.ben.filamentmeter.ui.artwork
import com.ben.filamentmeter.model.PrinterModel
import com.ben.filamentmeter.model.AmsModel
import android.graphics.BitmapFactory
import java.util.Locale

class FilamentMeterWidgetProvider : AppWidgetProvider() {

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FilamentMeterWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val snapshot = SettingsStore(context).widgetSnapshot()
            val views = RemoteViews(context.packageName, R.layout.widget_filament_meter)

            val launchIntent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_root, pending)
            val options = manager.getAppWidgetOptions(widgetId)
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val width = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            views.setViewVisibility(R.id.widget_controls, View.GONE)
            listOf(R.id.widget_play to "resume", R.id.widget_pause to "pause", R.id.widget_stop to "stop").forEach { (id, command) ->
                val controlIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("printer_command", command)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                views.setOnClickPendingIntent(id, PendingIntent.getActivity(context, id, controlIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            views.setTextViewText(R.id.widget_job_name, snapshot.jobName)
            views.setTextViewText(
                R.id.widget_status,
                if (snapshot.connected) "LIVE" else "OFFLINE"
            )
            views.setTextViewText(
                R.id.widget_cost,
                String.format(Locale.US, "$%.2f", snapshot.cost)
            )
            views.setTextViewText(R.id.widget_percent, "${snapshot.percent}%")
            views.setProgressBar(R.id.widget_progress, 100, snapshot.percent.coerceIn(0, 100), false)

            val printerModel = runCatching { PrinterModel.valueOf(snapshot.model) }.getOrDefault(PrinterModel.UNKNOWN)
            val amsModel = snapshot.amsModels.firstOrNull()?.let { runCatching { AmsModel.valueOf(it) }.getOrNull() }
            val art = amsModel?.artwork() ?: printerModel.artwork()
            if (art != null) {
                val bitmap = BitmapFactory.decodeResource(context.resources, art, BitmapFactory.Options().apply { inSampleSize = 2 })
                views.setImageViewBitmap(R.id.widget_ams_image, bitmap)
            } else views.setImageViewResource(R.id.widget_ams_image, R.drawable.ic_bambu_printer)
            views.setContentDescription(R.id.widget_ams_image, snapshot.hardwareLabel)
            views.setTextViewText(R.id.widget_hardware_label, snapshot.hardwareLabel)

            val layerText = if (snapshot.totalLayers > 0)
                "Layer ${snapshot.currentLayer}/${snapshot.totalLayers}"
            else
                "Layer —"

            views.setTextViewText(
                R.id.widget_subtitle,
                String.format(Locale.US, "%.1f g used • %s", snapshot.gramsUsed, layerText)
            )

            val responsive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val expanded = RemoteViews(views).apply { setViewVisibility(R.id.widget_controls, View.VISIBLE) }
                RemoteViews(mapOf(SizeF(250f, 110f) to views, SizeF(250f, 240f) to expanded))
            } else views.apply {
                setViewVisibility(R.id.widget_controls, if (width >= 250 && height >= 240) View.VISIBLE else View.GONE)
            }
            manager.updateAppWidget(widgetId, responsive)
        }
    }
}
