package com.pocketagent

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AssistantActivity : ComponentActivity() {

    private val api = ApiClient()
    private val recorder = AudioRecorder()

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPipeline()
    }

    private var _state = mutableStateOf<AssistantState>(AssistantState.Idle)
    private var _transcript = mutableStateOf("")
    private var _response = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AssistantScreen(
                state = _state.value,
                transcript = _transcript.value,
                response = _response.value,
                onMicTap = { requestMicAndStart() },
                onDismiss = { finish() }
            )
        }

        // Always auto-start listening on launch
        requestMicAndStart()
    }

    private fun requestMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startPipeline()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startPipeline() {
        val scope = lifecycleScope
        scope.launch {
            try {
                // Step 1: Record
                _state.value = AssistantState.Listening
                _transcript.value = ""
                _response.value = ""

                val audioFile = withContext(Dispatchers.IO) {
                    recorder.recordToWav(File(cacheDir, "input.wav"))
                }

                // Step 2: Transcribe
                _state.value = AssistantState.Transcribing
                val transcript = withContext(Dispatchers.IO) {
                    api.transcribe(audioFile)
                }

                if (transcript.isBlank()) {
                    _state.value = AssistantState.Error("Didn't catch that")
                    return@launch
                }
                _transcript.value = transcript

                // Step 3: Think
                _state.value = AssistantState.Thinking
                val response = withContext(Dispatchers.IO) {
                    api.chat(transcript)
                }
                _response.value = response

                // Step 4: Speak
                _state.value = AssistantState.Speaking
                val speechFile = withContext(Dispatchers.IO) {
                    api.speak(response, File(cacheDir, "response.wav"))
                }

                withContext(Dispatchers.IO) {
                    playAudio(speechFile)
                }

                _state.value = AssistantState.Done

            } catch (e: Exception) {
                _state.value = AssistantState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun playAudio(file: File) {
        if (!file.exists() || file.length() == 0L) return
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.prepare()
        mp.start()
        while (mp.isPlaying) Thread.sleep(100)
        mp.release()
    }

}

sealed class AssistantState {
    object Idle : AssistantState()
    object Listening : AssistantState()
    object Transcribing : AssistantState()
    object Thinking : AssistantState()
    object Speaking : AssistantState()
    object Done : AssistantState()
    data class Error(val message: String) : AssistantState()
}

@Composable
fun AssistantScreen(
    state: AssistantState,
    transcript: String,
    response: String,
    onMicTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val statusText = when (state) {
        AssistantState.Idle -> "Tap to speak"
        AssistantState.Listening -> "Listening..."
        AssistantState.Transcribing -> "Transcribing..."
        AssistantState.Thinking -> "Thinking..."
        AssistantState.Speaking -> "Speaking..."
        AssistantState.Done -> "Done"
        is AssistantState.Error -> state.message
    }

    val micColor = when (state) {
        AssistantState.Listening -> Color(0xFFE53935)
        AssistantState.Idle, AssistantState.Done -> Color(0xFF1E88E5)
        else -> Color(0xFF757575)
    }

    val isAnimating = state == AssistantState.Listening

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .clickable { if (state == AssistantState.Done || state is AssistantState.Error) onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Pocket Agent",
                color = Color.White,
                fontSize = 28.sp,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Mic button with pulse animation
            val scale by animateFloatAsState(
                targetValue = if (isAnimating) 1.2f else 1f,
                animationSpec = if (isAnimating) infiniteRepeatable(
                    tween(600), RepeatMode.Reverse
                ) else tween(300),
                label = "mic_pulse"
            )

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(micColor, CircleShape)
                    .clickable { if (state == AssistantState.Idle || state == AssistantState.Done) onMicTap() },
                contentAlignment = Alignment.Center
            ) {
                Text("🎤", fontSize = 48.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = statusText,
                color = Color(0xAAFFFFFF),
                fontSize = 18.sp,
            )

            if (transcript.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "You: $transcript",
                    color = Color(0xAAFFFFFF),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (response.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = response,
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
