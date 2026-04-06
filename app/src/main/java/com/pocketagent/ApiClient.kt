package com.pocketagent

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ApiClient(
    private val llmUrl: String = "http://127.0.0.1:8080",
    private val sttUrl: String = "http://127.0.0.1:8081",
    private val ttsUrl: String = "http://127.0.0.1:8082",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun transcribe(audioFile: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "input.wav",
                audioFile.asRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("$sttUrl/transcribe")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        return json.optString("text", "")
    }

    fun chat(userMessage: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are Pocket Agent, a helpful AI on a phone. " +
                    "Be very concise, 1-3 sentences. No markdown or special formatting.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", "gemma-4-e4b")
            put("messages", messages)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url("$llmUrl/v1/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    fun speak(text: String, outputFile: File): File {
        val payload = JSONObject().apply { put("text", text) }

        val request = Request.Builder()
            .url("$ttsUrl/speak")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        outputFile.outputStream().use { out ->
            response.body?.byteStream()?.copyTo(out)
        }
        return outputFile
    }

    fun isHealthy(): Boolean {
        return try {
            val r = client.newCall(Request.Builder().url("$llmUrl/health").build()).execute()
            r.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
