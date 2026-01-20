package com.example.wakeword.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
            val buffer = ShortArray(bufferSize)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    synchronized(audioBuffer) {
                        audioBuffer.addAll(buffer.take(read))

                        if (audioBuffer.size >= sampleRate * recordingDurationMs / 1000) {
                            val audioData = audioBuffer.take(sampleRate * recordingDurationMs / 1000)
                            audioBuffer.clear()

                            val floatAudio = FloatArray(audioData.size) { i ->
                                audioData[i].toFloat() / Short.MAX_VALUE
                            }

                            val mfcc = MFCC.computeMFCC(floatAudio, sampleRate)

                            withContext(Dispatchers.Main) {
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
