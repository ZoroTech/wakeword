package com.example.wakeword.ml

import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelInterpreter(assetManager: AssetManager) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(assetManager, "hey_nirman.tflite")
        interpreter = Interpreter(model)
    }

    private fun loadModelFile(assetManager: AssetManager, fileName: String): ByteBuffer {
        val inputStream = assetManager.open(fileName)
        val fileSize = inputStream.available()
        val buffer = ByteBuffer.allocateDirect(fileSize).apply {
            order(ByteOrder.nativeOrder())
        }
        val bytes = inputStream.readBytes()
        buffer.put(bytes)
        buffer.rewind()
        return buffer
    }

    fun predict(mfcc: FloatArray): Float {
        // TFLite expects a 2D input: [1][N_MFCC]
        val inputBuffer = ByteBuffer.allocateDirect(mfcc.size * 4).apply {
            order(ByteOrder.nativeOrder())
            for (value in mfcc) putFloat(value)
        }

        val outputBuffer = ByteBuffer.allocateDirect(4).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter.run(inputBuffer, outputBuffer.rewind())

        return outputBuffer.float
    }

    fun close() {
        interpreter.close()
    }
}
