package com.ben.filamentmeter.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ben.filamentmeter.vision.FailureBox
import com.ben.filamentmeter.vision.mapDetection
import kotlin.math.roundToInt

@Composable
fun DetectionOverlay(boxes: List<FailureBox>, imageWidth: Int, imageHeight: Int,
    fit: Boolean, zoom: Float, modifier: Modifier = Modifier) {
    val density=LocalDensity.current
    val textSize=with(density) { 12.sp.toPx() }
    val padding=with(density) { 5.dp.toPx() }
    val stroke=with(density) { 2.dp.toPx() }
    val paint=remember(textSize) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize=textSize; color=android.graphics.Color.WHITE; typeface=android.graphics.Typeface.DEFAULT_BOLD
    } }
    Canvas(modifier.semantics {
        contentDescription=boxes.joinToString("; ") { "Possible ${it.label}, ${(it.confidence*100).roundToInt()} percent confidence" }
    }) {
        clipRect {
            boxes.forEach { box ->
                val rect=mapDetection(box,imageWidth.toFloat(),imageHeight.toFloat(),size.width,size.height,fit,zoom) ?: return@forEach
                val color=when(box.kind) { 0 -> Color(0xFF64D8FF); 1 -> Color(0xFFFFAD42); else -> Color(0xFFFF70B6) }
                val origin=Offset(rect.left,rect.top)
                val extent=Size(rect.right-rect.left,rect.bottom-rect.top)
                drawRect(color.copy(alpha=.09f),origin,extent)
                drawRect(color,origin,extent,style=Stroke(stroke))
                val label="Possible ${when(box.kind) { 0 -> "layer shift"; 1 -> "spaghetti"; else -> "lifting" }} · ${(box.confidence*100).roundToInt()}%"
                val labelWidth=(paint.measureText(label)+padding*2).coerceAtMost(size.width)
                val labelHeight=paint.fontMetrics.descent-paint.fontMetrics.ascent+padding*2
                val x=rect.left.coerceAtMost((size.width-labelWidth).coerceAtLeast(0f))
                val y=(rect.top-labelHeight).coerceIn(0f,(size.height-labelHeight).coerceAtLeast(0f))
                drawRect(Color.Black.copy(alpha=.85f),Offset(x,y),Size(labelWidth,labelHeight))
                drawLine(color,Offset(x,y+labelHeight),Offset(x+labelWidth,y+labelHeight),stroke)
                drawContext.canvas.nativeCanvas.drawText(label,x+padding,y+padding-paint.fontMetrics.ascent,paint)
            }
        }
    }
}
