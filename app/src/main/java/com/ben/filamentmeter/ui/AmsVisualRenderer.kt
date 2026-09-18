package com.ben.filamentmeter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.ben.filamentmeter.R
import com.ben.filamentmeter.model.AmsTray

object AmsVisualRenderer {

    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null

    fun getSpoolDrawable(hex: String): Int {
        val clean = hex.removePrefix("#").trim()
        val r: Int
        val g: Int
        val b: Int
        try {
            val rgb = when (clean.length) {
                6 -> clean.toInt(16)
                8 -> clean.substring(0, 6).toInt(16)
                else -> 0x888888
            }
            r = (rgb shr 16) and 0xFF
            g = (rgb shr 8) and 0xFF
            b = rgb and 0xFF
        } catch (_: Throwable) {
            return R.drawable.spool_white
        }

        val candidates = listOf(
            Triple(0x12, 0x11, 0x11) to R.drawable.spool_black,
            Triple(0xEA, 0xE9, 0xEB) to R.drawable.spool_white,
            Triple(0x68, 0x67, 0x68) to R.drawable.spool_grey,
            Triple(0x9F, 0x9E, 0xA1) to R.drawable.spool_silver,
            Triple(0xE4, 0x01, 0x01) to R.drawable.spool_red,
            Triple(0xFB, 0x51, 0x00) to R.drawable.spool_orange,
            Triple(0xF8, 0xCA, 0x03) to R.drawable.spool_yellow,
            Triple(0x02, 0x8D, 0x17) to R.drawable.spool_green,
            Triple(0x00, 0x54, 0xE2) to R.drawable.spool_blue,
            Triple(0x6F, 0x08, 0xAA) to R.drawable.spool_purple,
            Triple(0xFC, 0x6D, 0x99) to R.drawable.spool_magenta,
            Triple(0x4F, 0x24, 0x0F) to R.drawable.spool_brown
        )

        return candidates.minByOrNull { (rgb, _) ->
            val dr = r - rgb.first
            val dg = g - rgb.second
            val db = b - rgb.third
            dr * dr + dg * dg + db * db
        }?.second ?: R.drawable.spool_white
    }

    @Synchronized
    fun renderAmsComposite(
        context: Context,
        trayColors: List<String>,
        activeTrayId: Int?
    ): Bitmap {
        val cacheKey = trayColors.joinToString(",") + "_active=" + (activeTrayId ?: -1)
        if (cacheKey == cachedKey && cachedBitmap != null && !cachedBitmap!!.isRecycled) {
            return cachedBitmap!!
        }

        val baseOpt = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val baseBmp = BitmapFactory.decodeResource(context.resources, R.drawable.ams_unit_base, baseOpt)
            ?: return Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)

        val width = baseBmp.width
        val height = baseBmp.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Draw AMS enclosure base (back wall, slot dividers)
        canvas.drawBitmap(baseBmp, 0f, 0f, null)

        // Slot positioning relative to base image: aligned over feeder funnels and rollers
        val slotCenterFractions = floatArrayOf(0.165f, 0.384f, 0.604f, 0.824f)
        val spoolWidth = width * 0.172f
        val spoolHeight = height * 0.680f
        val spoolTop = height * 0.160f

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = Color.parseColor("#7000E676")
        }

        // 2. Draw slotted 3D spools
        for (i in 0 until 4) {
            val colorHex = trayColors.getOrNull(i) ?: continue
            val spoolRes = getSpoolDrawable(colorHex)
            val spoolBmp = BitmapFactory.decodeResource(context.resources, spoolRes) ?: continue

            val centerX = width * slotCenterFractions[i]
            val left = centerX - spoolWidth / 2f
            val right = left + spoolWidth
            val bottom = spoolTop + spoolHeight

            val dstRect = RectF(left, spoolTop, right, bottom)

            // Draw active neon halo behind spool
            if (activeTrayId == i) {
                val haloRect = RectF(dstRect).apply {
                    inset(-4f, -4f)
                }
                glowPaint.strokeWidth = 14f
                glowPaint.color = Color.parseColor("#4000E676")
                canvas.drawRoundRect(haloRect, 20f, 20f, glowPaint)
                glowPaint.strokeWidth = 6f
                glowPaint.color = Color.parseColor("#9000E676")
                canvas.drawRoundRect(haloRect, 20f, 20f, glowPaint)
            }

            canvas.drawBitmap(spoolBmp, null, dstRect, null)
        }

        // 3. Draw AMS enclosure front (front lip covers bottom of spools)
        val frontBmp = BitmapFactory.decodeResource(context.resources, R.drawable.ams_unit_front, baseOpt)
        if (frontBmp != null) {
            canvas.drawBitmap(frontBmp, 0f, 0f, null)
        }

        // 4. Draw AMS status indicator LEDs on front lip
        val ledY = height * 0.732f
        val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until 4) {
            val ledX = width * slotCenterFractions[i]
            val isSlotPresent = i < trayColors.size
            val isActive = activeTrayId == i

            if (isActive) {
                // Active slot: glowing bright Bambu green LED
                ledPaint.color = Color.parseColor("#5500E676")
                canvas.drawCircle(ledX, ledY, 12f, ledPaint)
                ledPaint.color = Color.parseColor("#AA00E676")
                canvas.drawCircle(ledX, ledY, 7f, ledPaint)
                ledPaint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(ledX, ledY, 3.5f, ledPaint)
            } else if (isSlotPresent) {
                // Loaded idle slot: soft neutral indicator
                ledPaint.color = Color.parseColor("#55FFFFFF")
                canvas.drawCircle(ledX, ledY, 5f, ledPaint)
                ledPaint.color = Color.parseColor("#BBFFFFFF")
                canvas.drawCircle(ledX, ledY, 2.5f, ledPaint)
            } else {
                // Empty slot: dim off state
                ledPaint.color = Color.parseColor("#22FFFFFF")
                canvas.drawCircle(ledX, ledY, 2.5f, ledPaint)
            }
        }

        cachedKey = cacheKey
        cachedBitmap = result
        return result
    }

    fun renderAmsFromTrays(
        context: Context,
        trays: List<AmsTray>,
        activeTrayId: Int?
    ): Bitmap {
        return renderAmsComposite(
            context = context,
            trayColors = trays.map { it.colorHex },
            activeTrayId = activeTrayId
        )
    }
}
