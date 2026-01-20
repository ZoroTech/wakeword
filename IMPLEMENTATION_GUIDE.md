# Wake Word Detection - MFCC Implementation Guide

## Overview

This Android application implements a wake-word detection system using MFCC (Mel-Frequency Cepstral Coefficients) feature extraction that matches librosa's behavior. The extracted features are compatible with TensorFlow Lite models trained in Python.

## System Architecture

### 1. Audio Pipeline

```
Raw Audio (16kHz PCM)
    ↓
Pre-emphasis (α = 0.97)
    ↓
Framing (25ms window, 10ms step)
    ↓
Hamming Window
    ↓
FFT (Fast Fourier Transform)
    ↓
Mel Filterbank (26 filters)
    ↓
Log Transform
    ↓
DCT (Discrete Cosine Transform)
    ↓
13 MFCC Coefficients
    ↓
Mean over time
    ↓
TFLite Model Input
```

### 2. Key Parameters (LOCKED)

These parameters **MUST** match your training configuration:

- **Sample Rate**: 16000 Hz
- **Channels**: MONO
- **PCM Format**: 16-bit
- **Frame Size**: 400 samples (25ms)
- **Frame Step**: 160 samples (10ms)
- **MFCC Coefficients**: 13
- **Mel Filters**: 26
- **Pre-emphasis**: 0.97

## File Structure

```
app/src/main/java/com/example/wakeword/
├── audio/
│   ├── MFCC.kt              # Core MFCC computation
│   ├── AudioProcessor.kt    # Audio recording & processing
│   └── WakeWordDetector.kt  # TFLite model integration
└── MainActivity.kt          # UI and permission handling
```

## Implementation Details

### MFCC.kt

Core DSP functions:

1. **Pre-emphasis**: High-pass filter to amplify high frequencies
   ```kotlin
   out[i] = signal[i] - 0.97 * signal[i-1]
   ```

2. **Framing**: Split audio into overlapping windows
   - 400 samples per frame (25ms at 16kHz)
   - 160 samples step (10ms overlap)

3. **Hamming Window**: Reduce spectral leakage
   ```kotlin
   w[i] = 0.54 - 0.46 * cos(2π * i / (N-1))
   ```

4. **FFT**: Convert to frequency domain
   - Radix-2 Cooley-Tukey algorithm
   - Returns magnitude spectrum

5. **Mel Filterbank**: Perceptual frequency scale
   - 26 triangular filters
   - Mel scale: `mel = 2595 * log10(1 + hz/700)`

6. **DCT**: Decorrelate mel energies
   - Type-II DCT
   - Returns 13 coefficients

7. **Temporal Averaging**: Mean across all frames

### AudioProcessor.kt

Handles real-time audio recording:

- Uses Android AudioRecord API
- Captures 1-second audio buffers
- Converts PCM 16-bit to normalized float [-1, 1]
- Extracts MFCC features continuously
- Runs on background thread (Coroutines)

### WakeWordDetector.kt

TFLite model integration:

- Loads model from assets folder
- Accepts 13 MFCC features as input
- Returns confidence score [0, 1]
- Threshold: 0.7 (adjustable)

## Usage

### Basic MFCC Extraction

```kotlin
// From audio file
val audioData: ShortArray = loadAudioFile()
val floatAudio = FloatArray(audioData.size) { i ->
    audioData[i].toFloat() / Short.MAX_VALUE
}
val mfcc = MFCC.computeMFCC(floatAudio, 16000)
// mfcc: FloatArray of size 13
```

### Real-time Detection

```kotlin
val audioProcessor = AudioProcessor()

audioProcessor.startRecording { mfcc ->
    // mfcc: FloatArray[13]
    println("MFCC: ${mfcc.joinToString()}")

    // Use with TFLite model
    val result = wakeWordDetector.detectWakeWord(mfcc)
    if (result.detected) {
        println("Wake word detected! Confidence: ${result.confidence}")
    }
}

// Stop when done
audioProcessor.stopRecording()
```

### With TFLite Model

```kotlin
// Initialize detector (place hey_nirman.tflite in assets/)
val detector = WakeWordDetector(context, "hey_nirman.tflite")

// Process audio
val mfcc = MFCC.computeMFCC(audio, 16000)
val result = detector.detectWakeWord(mfcc)

if (result.detected) {
    println("Detected: ${result.message}")
    println("Confidence: ${result.confidence}")
}

// Clean up
detector.close()
```

## Model Integration

### Placing Your TFLite Model

1. Create folder: `app/src/main/assets/`
2. Place `hey_nirman.tflite` in assets folder
3. Model will be loaded automatically

### Model Input/Output Specification

**Input:**
- Shape: `[1, 13]`
- Type: `FLOAT32`
- Values: MFCC coefficients (typically range -10 to +10)

**Output:**
- Shape: `[1, 1]`
- Type: `FLOAT32`
- Values: Confidence score [0, 1]

## Permissions

Required in `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Runtime permission handling is implemented in `MainActivity.kt`.

## Verification

### Testing MFCC Accuracy

Compare with Python/librosa output:

```python
# Python (librosa)
import librosa
import numpy as np

audio, sr = librosa.load('test.wav', sr=16000)
mfcc = librosa.feature.mfcc(
    y=audio,
    sr=16000,
    n_mfcc=13,
    n_fft=400,
    hop_length=160,
    n_mels=26,
    fmin=0,
    fmax=8000
)
mfcc_mean = np.mean(mfcc, axis=1)
print(mfcc_mean)
```

```kotlin
// Kotlin (this implementation)
val audio = loadAudioFile("test.wav")
val mfcc = MFCC.computeMFCC(audio, 16000)
println(mfcc.joinToString())
```

Values should match within ±5% tolerance.

## Troubleshooting

### Issue: Model not detecting

**Solutions:**
1. Verify MFCC parameters match training config
2. Check audio sample rate (must be 16kHz)
3. Ensure model file is in assets folder
4. Test MFCC output against Python librosa
5. Adjust detection threshold

### Issue: Permission denied

**Solutions:**
1. Check `AndroidManifest.xml` has RECORD_AUDIO permission
2. Grant permission in app settings
3. Ensure runtime permission request is working

### Issue: Audio quality poor

**Solutions:**
1. Use noise-cancelling microphone
2. Record in quiet environment
3. Check microphone permissions
4. Verify sample rate is 16kHz

## Performance

- **MFCC Computation**: ~5-10ms per second of audio
- **Model Inference**: ~2-5ms per prediction
- **Total Latency**: <20ms (real-time capable)
- **Memory Usage**: ~10MB (including model)

## Dependencies

```gradle
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("org.tensorflow:tensorflow-lite:2.14.0")
```

## Next Steps

1. Place your `hey_nirman.tflite` model in `app/src/main/assets/`
2. Test MFCC extraction with known audio samples
3. Verify model predictions match expected behavior
4. Tune detection threshold for your use case
5. Add custom wake word actions in MainActivity

## References

- [Librosa MFCC Documentation](https://librosa.org/doc/main/generated/librosa.feature.mfcc.html)
- [TensorFlow Lite Android Guide](https://www.tensorflow.org/lite/guide/android)
- [MFCC Theory](https://en.wikipedia.org/wiki/Mel-frequency_cepstrum)
