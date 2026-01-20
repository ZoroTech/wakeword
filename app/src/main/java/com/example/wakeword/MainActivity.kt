package com.example.wakeword

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.wakeword.audio.AudioProcessor
import com.example.wakeword.ui.theme.WakewordTheme

class MainActivity : ComponentActivity() {

    private val audioProcessor = AudioProcessor()
    private var hasPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            WakewordTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WakeWordScreen(
                        modifier = Modifier.padding(innerPadding),
                        audioProcessor = audioProcessor,
                        hasPermission = hasPermission,
                        onRequestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioProcessor.stopRecording()
    }
}

@Composable
fun WakeWordScreen(
    modifier: Modifier = Modifier,
    audioProcessor: AudioProcessor,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var isListening by remember { mutableStateOf(false) }
    var mfccValues by remember { mutableStateOf<FloatArray?>(null) }
    var detectionStatus by remember { mutableStateOf("Ready") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Wake Word Detector",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Status: $detectionStatus",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                if (mfccValues != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "MFCC Features (13):",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = mfccValues!!.joinToString(", ") { "%.2f".format(it) },
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!hasPermission) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text("Grant Microphone Permission")
            }
        } else {
            Button(
                onClick = {
                    if (isListening) {
                        audioProcessor.stopRecording()
                        isListening = false
                        detectionStatus = "Stopped"
                    } else {
                        audioProcessor.startRecording { mfcc ->
                            mfccValues = mfcc
                            detectionStatus = "Listening..."
                        }
                        isListening = true
                        detectionStatus = "Listening..."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isListening) "Stop Listening" else "Start Listening")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Configuration:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sample Rate: 16kHz", fontSize = 14.sp)
                Text("Frame Size: 400 (25ms)", fontSize = 14.sp)
                Text("Frame Step: 160 (10ms)", fontSize = 14.sp)
                Text("MFCC Coefficients: 13", fontSize = 14.sp)
                Text("Mel Filters: 26", fontSize = 14.sp)
            }
        }
    }
}