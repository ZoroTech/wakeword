package com.example.wakeword.audio

import kotlin.math.*

object MFCC {

    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE = 400
    private const val FRAME_STEP = 160
    private const val N_MFCC = 13
    private const val N_MELS = 26

    fun preEmphasis(signal: FloatArray, alpha: Float = 0.97f): FloatArray {
        val out = FloatArray(signal.size)
        out[0] = signal[0]
        for (i in 1 until signal.size) {
            out[i] = signal[i] - alpha * signal[i - 1]
        }
        return out
    }

    fun hammingWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (size - 1))).toFloat()
        }
    }

    fun framing(signal: FloatArray, frameSize: Int, frameStep: Int): Array<FloatArray> {
        val numFrames = 1 + (signal.size - frameSize) / frameStep
        return Array(numFrames) { i ->
            signal.copyOfRange(i * frameStep, i * frameStep + frameSize)
        }
    }

    fun fftMagnitude(frame: FloatArray): DoubleArray {
        val n = frame.size
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        for (i in frame.indices) real[i] = frame[i].toDouble()

        var bits = 0
        var temp = n
        while (temp > 1) {
            bits++
            temp = temp shr 1
        }

        for (i in 0 until n) {
            var j = 0
            for (b in 0 until bits) {
                j = (j shl 1) or ((i shr b) and 1)
            }
            if (j > i) {
                val tempReal = real[i]
                val tempImag = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tempReal
                imag[j] = tempImag
            }
        }

        var m = 2
        while (m <= n) {
            val half = m / 2
            val theta = -2.0 * PI / m
            for (k in 0 until half) {
                val wr = cos(theta * k)
                val wi = sin(theta * k)
                for (j in k until n step m) {
                    val tReal = wr * real[j + half] - wi * imag[j + half]
                    val tImag = wr * imag[j + half] + wi * real[j + half]
                    real[j + half] = real[j] - tReal
                    imag[j + half] = imag[j] - tImag
                    real[j] += tReal
                    imag[j] += tImag
                }
            }
            m *= 2
        }

        return DoubleArray(n / 2) {
            sqrt(real[it] * real[it] + imag[it] * imag[it])
        }
    }

    private fun hzToMel(hz: Double) = 2595 * log10(1 + hz / 700)

    private fun melToHz(mel: Double) = 700 * (10.0.pow(mel / 2595) - 1)

    fun melFilterBank(
        fftSize: Int,
        sampleRate: Int,
        numFilters: Int
    ): Array<DoubleArray> {

        val lowMel = hzToMel(0.0)
        val highMel = hzToMel(sampleRate / 2.0)
        val melPoints = DoubleArray(numFilters + 2) {
            lowMel + it * (highMel - lowMel) / (numFilters + 1)
        }

        val hzPoints = melPoints.map { melToHz(it) }
        val bins = hzPoints.map { floor((fftSize + 1) * it / sampleRate).toInt() }

        return Array(numFilters) { i ->
            DoubleArray(fftSize / 2) { j ->
                when {
                    j < bins[i] -> 0.0
                    j <= bins[i + 1] ->
                        (j - bins[i]).toDouble() / (bins[i + 1] - bins[i])
                    j <= bins[i + 2] ->
                        (bins[i + 2] - j).toDouble() / (bins[i + 2] - bins[i + 1])
                    else -> 0.0
                }
            }
        }
    }

    fun computeMFCC(
        audio: FloatArray,
        sampleRate: Int = SAMPLE_RATE
    ): FloatArray {

        val emphasized = preEmphasis(audio)
        val frames = framing(emphasized, FRAME_SIZE, FRAME_STEP)
        val window = hammingWindow(FRAME_SIZE)
        val melFilters = melFilterBank(FRAME_SIZE, sampleRate, N_MELS)

        val mfccSum = DoubleArray(N_MFCC)

        for (frame in frames) {
            for (i in frame.indices) frame[i] *= window[i]
            val spectrum = fftMagnitude(frame)

            val melEnergies = DoubleArray(N_MELS)
            for (i in melFilters.indices) {
                melEnergies[i] =
                    melFilters[i].mapIndexed { idx, w -> w * spectrum[idx] }.sum()
            }

            val logMel = melEnergies.map { ln(it + 1e-9) }

            for (k in 0 until N_MFCC) {
                var sum = 0.0
                for (n in logMel.indices) {
                    sum += logMel[n] * cos(PI * k * (n + 0.5) / logMel.size)
                }
                mfccSum[k] += sum
            }
        }

        return FloatArray(N_MFCC) { i -> (mfccSum[i] / frames.size).toFloat() }
    }
}
