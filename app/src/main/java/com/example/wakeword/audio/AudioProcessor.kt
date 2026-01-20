package com.example.wakeword.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlin.math.abs

class AudioProcessor {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val audioBuffer = mutableListOf<Short>()
    private val windowSize = 16000
    private val hopSize = 4000

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(onMFCCExtracted: (FloatArray) -> Unit) {
        if (isRecording) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        isRecording = true
        audioBuffer.clear()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            audioRecord?.startRecording()
            val tempBuffer = ShortArray(bufferSize)

            while (isRecording && isActive) {
                val read = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                if (read > 0) {
                    synchronized(audioBuffer) {
                        for (i in 0 until read) {
                            audioBuffer.add(tempBuffer[i])
                        }

                        if (audioBuffer.size >= windowSize) {
                            val audioData = audioBuffer.take(windowSize)

                            val newBuffer = audioBuffer.drop(hopSize)
                            audioBuffer.clear()
                            audioBuffer.addAll(newBuffer)

                            val floatAudio = FloatArray(audioData.size) { i ->
                                audioData[i].toFloat() / Short.MAX_VALUE
                            }

                            val mfcc = MFCC.computeMFCC(floatAudio, sampleRate)

                            launch(Dispatchers.Main) {
                                onMFCCExtracted(mfcc)
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun processAudioFile(audioData: ShortArray): FloatArray {
        val floatAudio = FloatArray(audioData.size) { i ->
            audioData[i].toFloat() / Short.MAX_VALUE
        }
        return MFCC.computeMFCC(floatAudio, sampleRate)
    }

    fun normalizeAudio(audio: FloatArray): FloatArray {
        val maxAbs = audio.maxOfOrNull { abs(it) } ?: 1f
        return if (maxAbs > 0f) {
            FloatArray(audio.size) { i -> audio[i] / maxAbs }
        } else {
            audio
        }
    }
}
