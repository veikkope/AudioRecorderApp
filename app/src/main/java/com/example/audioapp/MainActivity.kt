package com.example.audioapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {

    // Audio recording and playback variables
    private var audioRecorder: AudioRecord? = null
    private var recordingFile: File? = null
    private var bufferSize: Int = 0
    private var isPermissionGranted = false

    // Coroutine scope for handling asynchronous tasks
    private val recordingScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize buffer size for audio recording
        bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT
        )

        // Register permission request launcher
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            isPermissionGranted = granted
            if (!granted) {
                Log.e(TAG, "Audio recording permission denied")
            }
        }

        // Check and request permissions
        checkAndRequestPermissions(permissionLauncher)

        setContent {
            var isRecording by remember { mutableStateOf(false) }
            var isPlaying by remember { mutableStateOf(false) }

            AudioRecorderApp(
                isRecording = isRecording,
                onRecordClick = {
                    if (isRecording) {
                        stopRecording()
                        isRecording = false
                    } else {
                        if (isPermissionGranted) {
                            startRecording()
                            isRecording = true
                        } else {
                            Log.e(TAG, "Permission not granted to record audio")
                        }
                    }
                },
                onPlayClick = {
                    if (!isPlaying) {
                        isPlaying = true
                        recordingScope.launch {
                            playAudio()
                            isPlaying = false
                        }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        stopRecording()
        recordingScope.cancel()
    }


     // Checks for audio recording permission and requests it if not granted.

    private fun checkAndRequestPermissions(
        permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    ) {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                isPermissionGranted = true
            }
            else -> {
                // Request permission
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }


      //Starts audio recording and saves the data to a PCM file.

    private fun startRecording() {
        try {
            // Create a file to save the recording
            recordingFile = File(externalCacheDir, "audiorecordtest.pcm")
            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            audioRecorder?.startRecording()

            // Launch a coroutine to handle recording
            recordingScope.launch {
                writeAudioDataToFile()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}")
        }
    }


     // Stops the audio recording and releases resources.

    private fun stopRecording() {
        audioRecorder?.apply {
            stop()
            release()
        }
        audioRecorder = null
    }


     // Writes audio data to the PCM file.

    private suspend fun writeAudioDataToFile() {
        recordingFile?.let { file ->
            FileOutputStream(file).use { fos ->
                val data = ByteArray(bufferSize)
                while (audioRecorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecorder?.read(data, 0, bufferSize) ?: 0
                    if (read > 0) {
                        fos.write(data, 0, read)
                    }
                }
            }
        }
    }


     // Plays the recorded audio from the PCM file.

    private suspend fun playAudio() = withContext(Dispatchers.IO) {
        recordingFile?.let { file ->
            FileInputStream(file).use { fis ->
                val audioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build(),
                    AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                audioTrack.play()

                val buffer = ByteArray(bufferSize)
                var bytesRead: Int

                try {
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        audioTrack.write(buffer, 0, bytesRead)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error playing audio: ${e.message}")
                } finally {
                    audioTrack.stop()
                    audioTrack.release()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}

@Composable
fun AudioRecorderApp(
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Record Button
        Button(
            onClick = onRecordClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isRecording) "Stop Recording" else "Start Recording")
        }

        // Recording Indicator
        if (isRecording) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Recording...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Play Button
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Play Audio")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioRecorderAppPreview() {
    AudioRecorderApp(
        isRecording = false,
        onRecordClick = {},
        onPlayClick = {}
    )
}
