package com.example.wakeword

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import com.example.wakeword.audio.ConfidenceSmoother
import com.example.wakeword.ui.theme.WakewordTheme
import com.example.wakeword.ml.ModelInterpreter

class MainActivity : ComponentActivity() {

    private val audioProcessor = AudioProcessor()
    private var hasPermission by mutableStateOf(false)
    private lateinit var modelInterpreter: ModelInterpreter
    private var serviceDetectionMessage by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val confidence = intent?.getFloatExtra("confidence", 0f) ?: 0f
            serviceDetectionMessage = "Service detected wake word! (${"%.0f".format(confidence * 100)}%)"
            Log.d("MainActivity", "Received wake word broadcast: $confidence")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelInterpreter = ModelInterpreter(assets)

        enableEdgeToEdge()

        hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val filter = IntentFilter("com.example.wakeword.WAKE_WORD_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, filter)
        }

        setContent {
            WakewordTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WakeWordScreen(
                        modifier = Modifier.padding(innerPadding),
                        audioProcessor = audioProcessor,
                        modelInterpreter = modelInterpreter,
                        hasPermission = hasPermission,
                        serviceDetectionMessage = serviceDetectionMessage,
                        onRequestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onStartService = {
                            WakeWordService.start(this)
                        },
                        onStopService = {
                            WakeWordService.stop(this)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioProcessor.stopRecording()
        unregisterReceiver(wakeWordReceiver)
    }
}

@Composable
fun WakeWordScreen(
    modifier: Modifier = Modifier,
    audioProcessor: AudioProcessor,
    modelInterpreter: ModelInterpreter,
    hasPermission: Boolean,
    serviceDetectionMessage: String?,
    onRequestPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val smoother = remember { ConfidenceSmoother(5) }
    var consecutiveHits by remember { mutableIntStateOf(0) }
    var isListening by remember { mutableStateOf(false) }
    var mfccValues by remember { mutableStateOf<FloatArray?>(null) }
    var detectionStatus by remember { mutableStateOf("Ready") }
    var confidenceScore by remember { mutableFloatStateOf(0f) }
    var lastDetectionTime by remember { mutableLongStateOf(0L) }
    val cooldownMs = 2000L
    val REQUIRED_HITS = 3
    val THRESHOLD = 0.45f

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
            Text(
                text = "UI-Based Detection (Testing)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isListening) {
                        audioProcessor.stopRecording()
                        isListening = false
                        detectionStatus = "Stopped"
                    } else {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            onRequestPermission()
                            return@Button
                        }

                        audioProcessor.startRecording { mfcc ->
                            mfccValues = mfcc

                            val rawConfidence = modelInterpreter.predict(mfcc)
                            val confidence = smoother.add(rawConfidence)
                            confidenceScore = confidence

                            Log.d("WakeWordConfidence", "raw=$rawConfidence smooth=$confidence hits=$consecutiveHits")

                            val currentTime = System.currentTimeMillis()

                            if (confidence > THRESHOLD) {
                                consecutiveHits++
                            } else {
                                consecutiveHits = 0
                            }

                            if (consecutiveHits >= REQUIRED_HITS && (currentTime - lastDetectionTime) > cooldownMs) {
                                lastDetectionTime = currentTime
                                consecutiveHits = 0
                                smoother.reset()

                                detectionStatus = "HEY NIRMAN DETECTED!"
                                Log.d("WakeWord", "Detected with smoothed confidence: $confidence (raw: $rawConfidence)")
                            } else if (confidence <= THRESHOLD) {
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
                Text(if (isListening) "Stop UI Detection" else "Start UI Detection")
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Background Service (Production)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (serviceDetectionMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = serviceDetectionMessage,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Start Service")
                }

                Button(
                    onClick = onStopService,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Service")
                }
            }
        }
    }
}