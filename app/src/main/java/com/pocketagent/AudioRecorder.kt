package com.pocketagent

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class AudioRecorder(private val sampleRate: Int = 16000) {

    /**
     * Record audio with intelligent silence detection.
     * Stops when silence is detected after speech, or after maxDurationMs.
     */
    fun recordToWav(
        outputFile: File,
        maxDurationMs: Long = 15000,
        silenceThreshold: Short = 800,
        silenceDurationMs: Long = 1500,
        minSpeechMs: Long = 500,
    ): File {
        val bufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufSize
        )

        val pcmData = ByteArrayOutputStream()
        val buffer = ByteArray(bufSize)
        val shortBuffer = ShortArray(bufSize / 2)

        recorder.startRecording()

        val startTime = System.currentTimeMillis()
        var lastSoundTime = startTime
        var hasSpeechStarted = false

        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = now - startTime

            // Hard max duration
            if (elapsed > maxDurationMs) break

            val read = recorder.read(buffer, 0, buffer.size)
            if (read <= 0) continue

            pcmData.write(buffer, 0, read)

            // Analyze audio level for silence detection
            val shortRead = read / 2
            for (i in 0 until shortRead) {
                shortBuffer[i] = ((buffer[i * 2 + 1].toInt() shl 8) or
                    (buffer[i * 2].toInt() and 0xFF)).toShort()
            }

            val maxAmplitude = shortBuffer.take(shortRead).maxOfOrNull { abs(it.toInt()) } ?: 0

            if (maxAmplitude > silenceThreshold) {
                lastSoundTime = now
                if (!hasSpeechStarted && elapsed > 200) {
                    hasSpeechStarted = true
                }
            }

            // Stop if we had speech and now silence for silenceDurationMs
            if (hasSpeechStarted && (now - lastSoundTime) > silenceDurationMs) {
                break
            }

            // Also stop if no speech detected for 4 seconds (nobody talking)
            if (!hasSpeechStarted && elapsed > 4000) {
                break
            }
        }

        recorder.stop()
        recorder.release()

        writeWav(outputFile, pcmData.toByteArray())
        return outputFile
    }

    private fun writeWav(file: File, pcmData: ByteArray) {
        val dos = DataOutputStream(FileOutputStream(file))
        val dataLen = pcmData.size
        val totalLen = dataLen + 36

        dos.writeBytes("RIFF")
        dos.writeInt(Integer.reverseBytes(totalLen))
        dos.writeBytes("WAVE")
        dos.writeBytes("fmt ")
        dos.writeInt(Integer.reverseBytes(16))
        dos.writeShort(java.lang.Short.reverseBytes(1).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(1).toInt())
        dos.writeInt(Integer.reverseBytes(sampleRate))
        dos.writeInt(Integer.reverseBytes(sampleRate * 2))
        dos.writeShort(java.lang.Short.reverseBytes(2).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(16).toInt())
        dos.writeBytes("data")
        dos.writeInt(Integer.reverseBytes(dataLen))
        dos.write(pcmData)
        dos.close()
    }
}
