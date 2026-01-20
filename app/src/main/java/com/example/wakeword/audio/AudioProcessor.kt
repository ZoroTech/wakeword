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
    private val recordingDurationMs = 1000

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
        // We calculate exactly how many samples we need for 1 second (16000 samples)
        val requiredSamples = sampleRate * recordingDurationMs / 1000
        audioBuffer.clear()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            audioRecord?.startRecording()
            val tempBuffer = ShortArray(bufferSize)

            while (isRecording && isActive) {
                val read = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                if (read > 0) {
                    synchronized(audioBuffer) {
                        // Add only the read elements
                        for (i in 0 until read) {
                            audioBuffer.add(tempBuffer[i])
                        }

                        // Process once we have enough data
                        if (audioBuffer.size >= requiredSamples) {
                            // Take exactly one window of data
                            val audioData = audioBuffer.take(requiredSamples)

                            // Remove the processed data from the start of the buffer
                            // Use repeat to clear or just clear all if you don't want sliding windows
                            audioBuffer.clear()

                            val floatAudio = FloatArray(audioData.size) { i ->
                                audioData[i].toFloat() / Short.MAX_VALUE
                            }

                            // Compute MFCC on the background thread
                            val mfcc = MFCC.computeMFCC(floatAudio, sampleRate)

                            // Dispatch only the result to the main thread
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
