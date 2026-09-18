package com.ben.filamentmeter.vision

data class OverlayRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Matches a centered Compose Image with Fit/Crop, then center-origin graphicsLayer zoom. */
fun mapDetection(box: FailureBox, imageWidth: Float, imageHeight: Float,
    viewWidth: Float, viewHeight: Float, fit: Boolean, zoom: Float): OverlayRect? {
    if (listOf(imageWidth,imageHeight,viewWidth,viewHeight,zoom).any { !it.isFinite() || it<=0 }) return null
    val scale = (if(fit) minOf(viewWidth/imageWidth,viewHeight/imageHeight)
        else maxOf(viewWidth/imageWidth,viewHeight/imageHeight)) * zoom
    val width=imageWidth*scale; val height=imageHeight*scale
    val x=(viewWidth-width)/2; val y=(viewHeight-height)/2
    val left=(x+box.left*width).coerceIn(0f,viewWidth)
    val top=(y+box.top*height).coerceIn(0f,viewHeight)
    val right=(x+box.right*width).coerceIn(0f,viewWidth)
    val bottom=(y+box.bottom*height).coerceIn(0f,viewHeight)
    return if(right>left && bottom>top) OverlayRect(left,top,right,bottom) else null
}

fun detectionOverlayFresh(checkedAt: Long, now: Long): Boolean = checkedAt>0 && now-checkedAt in 0..12_000
