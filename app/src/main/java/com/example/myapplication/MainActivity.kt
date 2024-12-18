package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizerResult: ActivityResultLauncher<Intent>
    private var recordedText by mutableStateOf("")
    private var wordCount by mutableIntStateOf(0)
    private var wordsPerMinute by mutableDoubleStateOf(0.0)
    private var startTime: Long = 0
    private var stopTime: Long = 0
    private var isRecording by mutableStateOf(false)
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var speechRecognizer: SpeechRecognizer
    private var timerDuration by mutableStateOf(Duration.ZERO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startRecording()
            } else {
                Toast.makeText(this, "Разрешение на запись отклонено!", Toast.LENGTH_SHORT).show()
            }
        }

        speechRecognizerResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            //We no longer need this callback since we are using SpeechRecognizer class
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
            }

            override fun onBeginningOfSpeech() {
                startTime = System.currentTimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Ошибка распознавания речи: $error", Toast.LENGTH_LONG).show()
                isRecording = false
            }

            override fun onResults(results: Bundle?) {
                if (results != null) {
                    val recognizedText = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    Log.d("MainActivity", "Recognized Text: $recognizedText")
                    recordedText = recognizedText
                    stopTime = System.currentTimeMillis()
                    updateWordCount(recordedText)
                    updateWordsPerMinute()
                    isRecording = false
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    recordedText = it
                    Log.d("MainActivity", "Partial Text: $recordedText")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        setContent {
            MyApplicationTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        wordCount = wordCount,
                        wordsPerMinute = wordsPerMinute,
                        isRecording = isRecording,
                        recordedText = recordedText,
                        timerDuration = timerDuration,
                        onRecordClick = {
                            if (isRecordAudioPermissionGranted()) {
                                startRecording()
                            } else {
                                requestRecordAudioPermission()
                            }
                        },
                        onTimerChange = { timerDuration = it }
                    )
                }
            }
        }
    }

    private fun updateWordCount(text: String) {
        wordCount = countWords(text)
    }

    private fun startRecording() {
        isRecording = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru"))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    private fun isRecordAudioPermissionGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestRecordAudioPermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun countWords(text: String): Int =
        if (text.isBlank()) 0 else text.trim().split(" ").size

    private fun updateWordsPerMinute() {
        if (startTime != 0L && stopTime != 0L) {
            val durationInSeconds = (stopTime - startTime) / 1000
            wordsPerMinute = if (durationInSeconds > 0) {
                countWords(recordedText).toDouble() / (durationInSeconds / 60.0)
            } else 0.0
        } else {
            wordsPerMinute = 0.0
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    wordCount: Int,
    wordsPerMinute: Double,
    isRecording: Boolean,
    recordedText: String,
    timerDuration: Duration,
    onRecordClick: () -> Unit,
    onTimerChange: (Duration) -> Unit
) {
    var currentTimerDuration by remember { mutableStateOf(timerDuration) }
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(key1 = isRecording) {

        timerJob?.cancel()


        if (isRecording) {
            currentTimerDuration = Duration.ZERO // Reset the timer here
            onTimerChange(currentTimerDuration) // Set timer to 0
            timerJob = scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    currentTimerDuration += 1.seconds
                    onTimerChange(currentTimerDuration)
                }
            }
        }
    }

    val formattedTime = remember(currentTimerDuration) {
        val totalSeconds = currentTimerDuration.inWholeSeconds
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(top = 45.dp), // Add top padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Align content to the top
    ) {

        Button(
            onClick = onRecordClick,
            enabled = !isRecording,
            shape = CircleShape,
            modifier = Modifier.size(160.dp) // Set a fixed size for the circular button
        ) {
            Image(
                painter = painterResource(id = R.drawable.micr), // Replace with your image resource
                contentDescription = "Start Recording",  // Optional description for accessibility
                modifier = Modifier.size(85.dp) // adjust size of your image
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = "$formattedTime", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(11.dp))
        Text(text = "Количество слов: $wordCount", fontSize = 18.sp)
        Text(text = String.format(Locale.getDefault(), "Слов в минуту: %.2f", wordsPerMinute), fontSize = 18.sp)
        Spacer(modifier = Modifier.height(1.dp))
        Text(text = " $recordedText", fontSize = 16.sp)
    }

}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyApplicationTheme {
        MainScreen(
            wordCount = 5,
            wordsPerMinute = 60.0,
            isRecording = false,
            recordedText = "Hello, world!",
            timerDuration = Duration.ZERO,
            onRecordClick = {},
            onTimerChange = {}
        )
    }
}