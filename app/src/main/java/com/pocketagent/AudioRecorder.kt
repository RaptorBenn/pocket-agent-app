package com.pocketagent

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val sampleRate: Int = 16000) {

    fun recordToWav(outputFile: File, durationMs: Long = 6000): File {
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

        recorder.startRecording()
        val endTime = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < endTime) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) pcmData.write(buffer, 0, read)
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

        // RIFF header
        dos.writeBytes("RIFF")
        dos.writeInt(Integer.reverseBytes(totalLen))
        dos.writeBytes("WAVE")
        // fmt chunk
        dos.writeBytes("fmt ")
        dos.writeInt(Integer.reverseBytes(16))
        dos.writeShort(java.lang.Short.reverseBytes(1).toInt()) // PCM
        dos.writeShort(java.lang.Short.reverseBytes(1).toInt()) // mono
        dos.writeInt(Integer.reverseBytes(sampleRate))
        dos.writeInt(Integer.reverseBytes(sampleRate * 2)) // byte rate
        dos.writeShort(java.lang.Short.reverseBytes(2).toInt()) // block align
        dos.writeShort(java.lang.Short.reverseBytes(16).toInt()) // bits per sample
        // data chunk
        dos.writeBytes("data")
        dos.writeInt(Integer.reverseBytes(dataLen))
        dos.write(pcmData)
        dos.close()
    }
}
