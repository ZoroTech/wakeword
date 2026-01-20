package com.example.wakeword.audio

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class  WakeWordDetector(context: Context, modelPath: String) {

    private var interpreter: Interpreter? = null
    private val threshold = 0.7f

    init {
        try {
            val modelFile = loadModelFile(context, modelPath)
            interpreter = Interpreter(modelFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detectWakeWord(mfccFeatures: FloatArray): DetectionResult {
        if (interpreter == null) {
            return DetectionResult(false, 0f, "Model not initialized")
        }

        if (mfccFeatures.size != 13) {
            return DetectionResult(false, 0f, "Invalid MFCC size: expected 13, got ${mfccFeatures.size}")
        }

        try {
            val inputBuffer = ByteBuffer.allocateDirect(13 * 4).apply {
                order(ByteOrder.nativeOrder())
                mfccFeatures.forEach { putFloat(it) }
                rewind()
            }

            val outputBuffer = Array(1) { FloatArray(1) }

            interpreter?.run(inputBuffer, outputBuffer)

            val confidence = outputBuffer[0][0]
            val detected = confidence >= threshold

            return DetectionResult(
                detected = detected,
                confidence = confidence,
                message = if (detected) "Wake word detected!" else "No wake word"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return DetectionResult(false, 0f, "Error: ${e.message}")
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    data class DetectionResult(
        val detected: Boolean,
        val confidence: Float,
        val message: String
    )
}
