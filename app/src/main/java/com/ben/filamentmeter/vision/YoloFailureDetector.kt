package com.ben.filamentmeter.vision

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class YoloFailureDetector(context: Context) : AutoCloseable {
    private val interpreter: Interpreter
    private val input: ByteBuffer
    private val output: ByteBuffer
    private val count: Int
    init {
        val bytes = context.assets.open("failure_detector.tflite").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(hash == "583ca573c3dcdc900584153f169b03ed054676030c393b38415b3cfda3b7f6eb") { "Unrecognized failure model" }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
        try {
            require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1,640,640,3)))
            require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
            val shape = interpreter.getOutputTensor(0).shape()
            require(shape.size == 3 && shape[0] == 1 && shape[1] == 7 && shape[2] in 1..10000)
            require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
            count = shape[2]
            input = ByteBuffer.allocateDirect(640*640*3*4).order(ByteOrder.nativeOrder())
            output = ByteBuffer.allocateDirect(count*7*4).order(ByteOrder.nativeOrder())
        } catch (e: Exception) { interpreter.close(); throw e }
    }
    fun detect(frame: Bitmap, threshold: Float): List<FailureBox> {
        // Match this model author's resize-to-square, RGB /255 preprocessing.
        val resized = Bitmap.createScaledBitmap(frame,640,640,true)
        val pixels = IntArray(640*640)
        resized.getPixels(pixels,0,640,0,0,640,640)
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 255)/255f)
            input.putFloat(((p shr 8) and 255)/255f)
            input.putFloat((p and 255)/255f)
        }
        if (resized !== frame) resized.recycle()
        input.rewind(); output.rewind()
        interpreter.run(input,output)
        output.rewind()
        val values = FloatArray(count*7)
        output.asFloatBuffer().get(values)
        return YoloOutput.decode(values,count,threshold)
    }
    override fun close() = interpreter.close()
}
