package com.aira.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val heardText = mutableStateOf("आपकी आवाज़ का इंतज़ार है…")
    private val selectedVoice = mutableStateOf("female_natural")
    private val voicePrefs by lazy { getSharedPreferences("aira_voice", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedVoice.value = voicePrefs.getString("preset", "female_natural") ?: "female_natural"
        textToSpeech = TextToSpeech(this, this)
        setContent {
            AiraScreen(
                heardText = heardText.value,
                selectedVoiceId = selectedVoice.value,
                onVoiceSelected = { id ->
                    selectedVoice.value = id
                    voicePrefs.edit().putString("preset", id).apply()
                    speak("नमस्ते, मैं AIRA हूँ। अब मेरी आवाज़ ऐसी होगी।")
                },
                onListen = { beginListening() },
                onOpenTtsSettings = { openTextToSpeechSettings() }
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
            val preset = VOICE_PRESETS.firstOrNull { it.id == selectedVoice.value }
                ?: VOICE_PRESETS.first()
            textToSpeech?.setPitch(preset.pitch)
            textToSpeech?.setSpeechRate(preset.rate)
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "aira-response")
        }
    }

    private fun openTextToSpeechSettings() {
        val settingsIntent = Intent("com.android.settings.TTS_SETTINGS")
        try {
            startActivity(settingsIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "फोन की सेटिंग्स नहीं खुल पाईं", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun beginListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            heardText.value = "इस फोन में speech recognition उपलब्ध नहीं है"
            Toast.makeText(this, heardText.value, Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    heardText.value = "सुन रही हूँ…"
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    heardText.value = "समझ रही हूँ…"
                }
                override fun onError(error: Int) {
                    heardText.value = "आवाज़ समझ नहीं आई। फिर कोशिश करें।"
                    Toast.makeText(this@MainActivity, heardText.value, Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (spoken.isNotBlank()) {
                        heardText.value = spoken
                        handleCommand(spoken)
                    } else {
                        heardText.value = "कुछ सुनाई नहीं दिया। फिर कोशिश करें।"
                    }
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
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
            normalized.contains("maps") || normalized.contains("मैप") || normalized.contains("नक्शा") -> {
                openApp("com.google.android.apps.maps", "Google Maps")
            }
            normalized.contains("chrome") || normalized.contains("क्रोम") || normalized.contains("ब्राउज़र") -> {
                openApp("com.android.chrome", "Chrome")
            }
            normalized.contains("camera") || normalized.contains("कैमरा") -> {
                launchIntent(Intent("android.media.action.IMAGE_CAPTURE"), "कैमरा ऐप उपलब्ध नहीं है")
            }
            normalized.contains("settings") || normalized.contains("सेटिंग") -> {
                launchIntent(Intent(android.provider.Settings.ACTION_SETTINGS), "सेटिंग्स नहीं खुलीं")
            }
            normalized.contains("search") || normalized.contains("सर्च") || normalized.contains("खोजो") -> {
                val query = command.replace(Regex("(?i)\\b(search|google|सर्च|खोजो)\\b"), "").trim()
                val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
                launchIntent(searchIntent, "वेब सर्च उपलब्ध नहीं है")
            }
            normalized.contains("alarm") || normalized.contains("अलार्म") -> {
                launchIntent(Intent(AlarmClock.ACTION_SET_ALARM), "अलार्म ऐप उपलब्ध नहीं है")
            }
            normalized.contains("call") || normalized.contains("कॉल") || normalized.contains("फोन लगाओ") -> {
                val number = command.filter { it.isDigit() || it == '+' }
                if (number.isNotBlank()) {
                    launchIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")), "डायलर नहीं खुल पाया")
                } else {
                    speak("किस नंबर पर कॉल करना है? मैं डायलर खोल सकती हूँ।")
                    launchIntent(Intent(Intent.ACTION_DIAL), "डायलर नहीं खुल पाया")
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
                val time = SimpleDateFormat("h:mm a", Locale("en", "IN")).format(Date())
                speak("अभी समय $time है।")
            }
            else -> {
                // No paid AI/API key: send general questions to a free web search.
                // The user can read results in their browser; this does not pretend to be an AI answer.
                val searchUri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(command))
                speak("मैं इंटरनेट पर इसका जवाब खोज रही हूँ।")
                launchIntent(
                    Intent(Intent.ACTION_VIEW, searchUri),
                    "इंटरनेट सर्च नहीं खुल पाई। कृपया इंटरनेट कनेक्शन जाँचें।"
                )
            }
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
private data class VoicePreset(val id: String, val label: String, val pitch: Float, val rate: Float)

private val VOICE_PRESETS = listOf(
    VoicePreset("female_soft", "महिला · सॉफ्ट", 1.18f, 0.96f),
    VoicePreset("female_natural", "महिला · नेचुरल", 1.10f, 1.00f),
    VoicePreset("female_bright", "महिला · ब्राइट", 1.28f, 1.04f),
    VoicePreset("female_sweet", "महिला · स्वीट", 1.22f, 0.90f),
    VoicePreset("male_deep", "पुरुष · डीप", 0.82f, 0.96f),
    VoicePreset("male_natural", "पुरुष · नेचुरल", 0.92f, 1.00f),
    VoicePreset("male_bright", "पुरुष · ब्राइट", 0.98f, 1.06f),
    VoicePreset("neutral_calm", "न्यूट्रल · शांत", 1.00f, 0.90f),
    VoicePreset("neutral_clear", "न्यूट्रल · क्लियर", 1.00f, 1.08f)
)

@Composable
private fun AiraScreen(
    heardText: String,
    selectedVoiceId: String,
    onVoiceSelected: (String) -> Unit,
    onListen: () -> Unit,
    onOpenTtsSettings: () -> Unit
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) onListen()
    }

    val quickCommands = listOf("YouTube खोलो", "WhatsApp खोलो", "बैटरी कितनी है?", "अभी समय क्या है?")

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070B16)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF17284A), Color(0xFF0A1021), Color(0xFF070B16))
                        )
                    )
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("A I R A", color = Color.White, fontSize = 29.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                        Text("YOUR PERSONAL AI ASSISTANT", color = Color(0xFF9DB6E8),
                            fontSize = 9.sp, letterSpacing = 1.8.sp)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF173D35), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("● READY", color = Color(0xFF72E2B1), fontSize = 10.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(44.dp))
                Box(
                    modifier = Modifier.size(214.dp).background(Color(0xFF142849), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(178.dp).background(Color(0xFF203F70), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF75C7FF), Color(0xFF477CEB), Color(0xFF765CE8))
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
                Text("नमस्ते! मैं AIRA हूँ", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = heardText,
                    color = Color(0xFFD5E2FA),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0x332A4775), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 15.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                VoiceSelector(selectedVoiceId = selectedVoiceId, onVoiceSelected = onVoiceSelected)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenTtsSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⚙ फोन की आवाज़ / TTS सेटिंग्स", color = Color.White)
                }
                Text(
                    "ज़रूरत पड़ने पर यहाँ से हिंदी आवाज़ डाउनलोड करें या फोन का TTS इंजन बदलें।",
                    color = Color(0xFF8098C4), fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) onListen()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.size(78.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B83E6))
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "बोलने के लिए दबाएँ",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    if (permissionDenied) "माइक अनुमति दें और फिर कोशिश करें"
                    else "बात करने के लिए माइक दबाएँ",
                    color = Color(0xFF9DB6E8), fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(28.dp))
                Text("TRY SAYING", color = Color(0xFF8098C4), fontSize = 10.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                quickCommands.forEach { example ->
                    Text("›  $example", color = Color(0xFFD6E4FF), fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }

                Spacer(modifier = Modifier.weight(1f))
                Text("AIRA  •  VERSION 0.1.0", color = Color(0xFF64799F),
                    fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
        }
    }
}


@Composable
private fun VoiceSelector(
    selectedVoiceId: String,
    onVoiceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = VOICE_PRESETS.firstOrNull { it.id == selectedVoiceId } ?: VOICE_PRESETS[1]
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("अपनी पसंद की आवाज़ चुनें", color = Color(0xFF9DB6E8), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(7.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("🔊  ${selected.label}  ·  बदलें", color = Color.White)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                VOICE_PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.label) },
                        onClick = {
                            expanded = false
                            onVoiceSelected(preset.id)
                        }
                    )
                }
            }
        }
        Text(
            "चुनी हुई आवाज़ सेव रहेगी। आवाज़ का वास्तविक प्रकार फोन के Text-to-Speech इंजन पर निर्भर है।",
            color = Color(0xFF8098C4), fontSize = 10.sp
        )
    }
}
