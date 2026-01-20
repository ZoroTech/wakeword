package com.example.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wakeword.audio.AudioProcessor
import com.example.wakeword.ui.theme.WakewordTheme
// Import your model interpreter (ensure the package name matches your file)
import com.example.wakeword.ml.ModelInterpreter

class MainActivity : ComponentActivity() {

    private val audioProcessor = AudioProcessor()
    private var hasPermission by mutableStateOf(false)
    // Initialize the model interpreter
    private lateinit var modelInterpreter: ModelInterpreter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the model from assets
        modelInterpreter = ModelInterpreter(assets)

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
                        modelInterpreter = modelInterpreter, // Pass it down
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
    modelInterpreter: ModelInterpreter,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current // Correct way to get context in Compose
    var isListening by remember { mutableStateOf(false) }
    var mfccValues by remember { mutableStateOf<FloatArray?>(null) }
    var detectionStatus by remember { mutableStateOf("Ready") }
    var confidenceScore by remember { mutableFloatStateOf(0f) }

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
                    fontWeight = FontWeight.Medium,
                    color = if (detectionStatus.contains("DETECTED")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                if (mfccValues != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Confidence: ${(confidenceScore * 100).toInt()}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!hasPermission) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)
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
                        // Use context from LocalContext.current
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            onRequestPermission()
                            return@Button
                        }

                        audioProcessor.startRecording { mfcc ->
                            mfccValues = mfcc

                            // --- PREDICTION LOGIC ---
                            val confidence = modelInterpreter.predict(mfcc)
                            confidenceScore = confidence

                            val THRESHOLD = 0.6f
                            if (confidence > THRESHOLD) {
                                detectionStatus = "HEY NIRMAN DETECTED! 🎯"
                                Log.d("WakeWord", "Detected with confidence: $confidence")
                            } else {
                                detectionStatus = "Listening..."
                            }
                        }
                        isListening = true
                        detectionStatus = "Listening..."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isListening) "Stop Listening" else "Start Listening")
            }
        }

        // Configuration Info Card... (rest of your UI remains the same)
    }
}