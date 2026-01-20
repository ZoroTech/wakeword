package com.example.wakeword.audio

import java.util.ArrayDeque

class ConfidenceSmoother(private val windowSize: Int = 5) {

    private val values = ArrayDeque<Float>()

    fun add(value: Float): Float {
        if (values.size >= windowSize) {
            values.removeFirst()
        }
        values.addLast(value)
        return values.average().toFloat()
    }

    fun reset() {
        values.clear()
    }
}
