package com.aira.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        setContent {
            AiraScreen(
                onListen = { beginListening() },
                onSpeak = { speak(it) },
                onCommand = { handleCommand(it) }
            )
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = textToSpeech?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                textToSpeech?.setLanguage(Locale("en", "IN"))
            }
        }
    }

    private fun speak(message: String) {
        if (ttsReady) {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "aira-response")
        }
    }

    private fun beginListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "इस फोन में speech recognition उपलब्ध नहीं है", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) {
                    Toast.makeText(this@MainActivity, "आवाज़ समझ नहीं आई, फिर कोशिश करें", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (spoken.isNotBlank()) handleCommand(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(rawCommand: String) {
        val command = rawCommand.trim()
        val normalized = command.lowercase(Locale.ROOT)
        when {
            normalized.contains("youtube") || normalized.contains("यूट्यूब") -> {
                openApp("com.google.android.youtube", "YouTube")
            }
            normalized.contains("whatsapp") || normalized.contains("व्हाट्सएप") -> {
                openApp("com.whatsapp", "WhatsApp")
            }
            normalized.contains("spotify") || normalized.contains("स्पॉटिफाई") -> {
                openApp("com.spotify.music", "Spotify")
            }
            normalized.contains("alarm") || normalized.contains("अलार्म") -> {
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM)
                launchIntent(alarmIntent, "अलार्म ऐप उपलब्ध नहीं है")
            }
            normalized.contains("call") || normalized.contains("कॉल") || normalized.contains("फोन लगाओ") -> {
                val number = command.filter { it.isDigit() || it == '+' }
                if (number.isNotBlank()) {
                    launchIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")), "डायलर नहीं खुल पाया")
                } else {
                    speak("किस नंबर पर कॉल करना है? नंबर डायलर में डालकर कॉल कर सकते हैं।")
                }
            }
            normalized.contains("message") || normalized.contains("sms") || normalized.contains("मैसेज") -> {
                launchIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), "मैसेज ऐप उपलब्ध नहीं है")
            }
            normalized.contains("battery") || normalized.contains("बैटरी") -> {
                val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("आपके फोन की बैटरी $level प्रतिशत है।")
            }
            normalized.contains("time") || normalized.contains("समय") || normalized.contains("टाइम") -> {
                val time = java.text.SimpleDateFormat("h:mm a", Locale("en", "IN")).format(java.util.Date())
                speak("अभी समय $time है।")
            }
            else -> speak("मैंने सुना: $command. अभी मैं यह कमांड नहीं कर सकती।")
        }
    }

    private fun openApp(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            speak("$appName खोल रही हूँ।")
        } else {
            speak("$appName इस फोन में इंस्टॉल नहीं है।")
        }
    }

    private fun launchIntent(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            speak(errorMessage)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun AiraScreen(
    onListen: () -> Unit,
    onSpeak: (String) -> Unit,
    onCommand: (String) -> Unit
) {
    var heardText by remember { mutableStateOf("आपकी आवाज़ का इंतज़ार है…") }
    var commandText by remember { mutableStateOf("") }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onListen()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080D1B)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF101B35), Color(0xFF080D1B), Color(0xFF11152B))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "A I R A",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp
                    )
                    Text(
                        text = "YOUR PERSONAL VOICE ASSISTANT",
                        color = Color(0xFF9DB6E8),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .background(Color(0xFF192B50), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .background(Color(0xFF294A80), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Light)
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = heardText,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "माइक दबाएँ और AIRA से बात करें",
                        color = Color(0xFF9DB6E8),
                        fontSize = 13.sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    androidx.compose.ui.platform.LocalContext.current,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                onListen()
                            } else {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(82.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B83E6))
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "बोलने के लिए दबाएँ",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "AIRA • VERSION 0.1",
                        color = Color(0xFF687DA8),
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}
