package com.inexra.nova

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private data class ChatMessage(val text: String, val fromNova: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val lockScreenEnabled = getSharedPreferences("nova_settings", MODE_PRIVATE)
            .getBoolean("lock_screen_access", false)
        setShowWhenLocked(lockScreenEnabled)
        setTurnScreenOn(lockScreenEnabled)
        setContent {
            NovaScreen(
                initialLockScreenAccess = lockScreenEnabled,
                onLockScreenAccessChanged = { enabled ->
                    getSharedPreferences("nova_settings", MODE_PRIVATE).edit()
                        .putBoolean("lock_screen_access", enabled).apply()
                    setShowWhenLocked(enabled)
                    setTurnScreenOn(enabled)
                },
                onSpeak = { say(it) }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova-response")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaScreen(
    initialLockScreenAccess: Boolean,
    onLockScreenAccessChanged: (Boolean) -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var showNovaSettings by remember { mutableStateOf(false) }
    var lockScreenAccess by remember { mutableStateOf(initialLockScreenAccess) }
    var backgroundMode by remember {
        mutableStateOf(context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE).getBoolean("background_mode", false))
    }
    var geminiApiKey by remember {
        mutableStateOf(context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE).getString("gemini_api_key", "") ?: "")
    }
    var geminiApiKeyDraft by remember { mutableStateOf(geminiApiKey) }
    var wakePhraseMode by remember {
        mutableStateOf(context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE).getBoolean("wake_phrase_mode", false))
    }
    var status by remember { mutableStateOf("Ready · local-first foundation") }
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    "Hey! I'm Nova. I can run phone commands, search the web in-app, and use your optional Gemini key for AI summaries. Enable Hey Nova listening in Settings to try on-device wake detection.",
                    true
                )
            )
        )
    }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        status = if (result.values.any { it }) {
            "Location permission granted · ask Nova for the weather again"
        } else {
            "Location permission denied · enable it in Android Settings to get local weather"
        }
    }

    val wakePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val prefs = context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("wake_phrase_mode", true).apply()
            wakePhraseMode = true
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, WakePhraseService::class.java)
                )
            }.onFailure {
                wakePhraseMode = false
                prefs.edit().putBoolean("wake_phrase_mode", false).apply()
                status = "Android couldn't start the wake listener. Open Nova and try again."
            }
        } else {
            wakePhraseMode = false
            status = "Microphone permission is required for Hey Nova listening"
        }
    }

    fun respond(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        messages = messages + ChatMessage(text, false)
        val lower = text.lowercase(Locale.ROOT)
        var asyncAiRequest = false
        val answer = when {
            lower.contains("weather") || lower.contains("forecast") || lower.contains("temperature outside") -> {
                val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fineGranted && !coarseGranted) {
                    locationPermissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                    "I need your location to check your local weather. Please allow location access, then ask me again."
                } else {
                    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                    val provider = when {
                        locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) -> android.location.LocationManager.NETWORK_PROVIDER
                        locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) -> android.location.LocationManager.GPS_PROVIDER
                        else -> null
                    }
                    if (provider == null) {
                        "Please turn on Location in Android Quick Settings, then ask me for the weather again."
                    } else {
                        status = "Getting your location for the local weather…"
                        try {
                            locationManager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                                if (location != null) {
                                    val query = "weather near ${location.latitude},${location.longitude} today"
                                    val opened = runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query)))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }.isSuccess
                                    status = if (opened) "Opened local weather using your phone's location" else "Couldn't open weather results"
                                } else {
                                    status = "Couldn't get a location fix · check that Location is enabled"
                                    onSpeak("I couldn't get your location. Please check that Location is enabled and try again.")
                                }
                            }
                            "Getting your location and opening today's local weather. Your location is used on your phone to find nearby weather results."
                        } catch (_: SecurityException) {
                            "Location access wasn't available. Please allow it in Android Settings and try again."
                        } catch (_: Exception) {
                            "I couldn't get your location. Please make sure Location is enabled and try again."
                        }
                    }
                }
            }
            lower in listOf("hi", "hello", "hey", "hey nova") ->
                "Hey! I'm Nova. Ask me to open an app, search Google, set a timer, set an alarm, or find directions."
            lower == "settings" || lower == "open settings" || lower == "phone settings" -> {
                val opened = runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (opened) "Opening Android Settings." else "I couldn't open Android Settings."
            }
            lower.startsWith("set a timer for ") || lower.startsWith("timer for ") -> {
                val phrase = text.substringAfter("for ").trim()
                val number = Regex("\\d+").find(phrase)?.value?.toIntOrNull()
                val seconds = when {
                    "hour" in phrase.lowercase(Locale.ROOT) -> (number ?: 1) * 3600
                    "minute" in phrase.lowercase(Locale.ROOT) -> (number ?: 1) * 60
                    else -> number ?: 60
                }
                val opened = runCatching {
                    context.startActivity(Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                        .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 86400))
                        .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Nova timer")
                        .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (opened) "Opening the Clock app to set a timer for $phrase. Confirm it there."
                else "I couldn't open a timer app."
            }
            lower.startsWith("set an alarm") || lower.startsWith("set alarm") -> {
                val opened = runCatching {
                    context.startActivity(Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (opened) "Opening the Clock app to set an alarm. Choose the time and confirm it there."
                else "I couldn't open an alarm app."
            }
            lower.startsWith("call ") || lower.startsWith("dial ") -> {
                val number = text.substringAfter(" ").trim().filter { it.isDigit() || it == '+' }
                val opened = number.isNotBlank() && runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (opened) "The dialer is open with that number. Review it and place the call yourself."
                else "I couldn't open the dialer. Try saying 'call' followed by a phone number."
            }
            lower.startsWith("directions to ") || lower.startsWith("navigate to ") || lower.startsWith("take me to ") -> {
                val destination = text.substringAfter(" to ").trim()
                val mapIntent = Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("google.navigation:q=" + android.net.Uri.encode(destination)))
                    .setPackage("com.google.android.apps.maps")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = destination.isNotBlank() && runCatching { context.startActivity(mapIntent) }.isSuccess
                if (opened) "Opening Google Maps directions to $destination."
                else "I couldn't open Google Maps. Try 'open Maps' first or install a maps app."
            }
            lower.startsWith("open ") -> {
                val name = text.substringAfter("open ").trim()
                val knownApps = mapOf(
                    "youtube" to "com.google.android.youtube",
                    "chrome" to "com.android.chrome",
                    "maps" to "com.google.android.apps.maps",
                    "google maps" to "com.google.android.apps.maps",
                    "gmail" to "com.google.android.gm",
                    "photos" to "com.google.android.apps.photos",
                    "play store" to "com.android.vending",
                    "camera" to "com.android.camera2",
                    "phone" to "com.google.android.dialer",
                    "messages" to "com.google.android.apps.messaging",
                    "clock" to "com.google.android.deskclock",
                    "calendar" to "com.google.android.calendar"
                )
                val packageName = knownApps[name.lowercase(Locale.ROOT)]
                val appIntent = packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
                val opened = appIntent != null && runCatching {
                    context.startActivity(appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (opened) "Opening $name."
                else "I couldn't open $name. It may not be installed, or this phone may use a different app package."
            }
            lower.startsWith("search google for ") || lower.startsWith("search for ") || lower.startsWith("google ") -> {
                val query = when {
                    lower.startsWith("search google for ") -> text.substring(18).trim()
                    lower.startsWith("search for ") -> text.substring(11).trim()
                    else -> text.substring(7).trim()
                }
                asyncAiRequest = query.isNotBlank()
                if (asyncAiRequest) {
                    NovaAiClient.searchAndAnswer(query, geminiApiKey) { result ->
                        messages = messages + ChatMessage(result, true)
                        status = "Web search and AI response complete"
                        onSpeak(result)
                    }
                    "Searching the web and preparing your answer…"
                } else "Tell me what you'd like me to search for."
            }
            lower.startsWith("what is ") || lower.startsWith("who is ") ||
                lower.startsWith("when is ") || lower.startsWith("where is ") ||
                lower.startsWith("why ") || lower.startsWith("how ") ||
                lower.endsWith("?") || lower.contains(" latest ") || lower.contains(" news ") -> {
                asyncAiRequest = true
                NovaAiClient.searchAndAnswer(text, geminiApiKey) { result ->
                    messages = messages + ChatMessage(result, true)
                    status = "Web search and AI response complete"
                    onSpeak(result)
                }
                "Searching the web and preparing your answer…"
            }
            lower == "help" || lower.contains("what can you do") ->
                "Try: 'open YouTube', 'open Camera', 'open Gmail', 'directions to Lucknow station', 'set a timer for 5 minutes', 'set an alarm', 'call 9876543210', or 'search Google for space news'. Calling requires you to confirm in the dialer. Web search and Gemini summaries are available when connected; Gemini requires your own API key. Hey Nova listening requires an on-device speech-recognition pack."
            else -> {
                // Search ordinary messages automatically; no "search Google" prefix needed.
                val query = text.trim()
                asyncAiRequest = query.isNotBlank()
                if (asyncAiRequest) {
                    NovaAiClient.searchAndAnswer(query, geminiApiKey) { result ->
                        messages = messages + ChatMessage(result, true)
                        status = "Web search and AI response complete"
                        onSpeak(result)
                    }
                    "Searching the web and preparing your answer…"
                } else "Please type or say something for me to help with."
            }
        }
        messages = messages + ChatMessage(answer, true)
        status = if (asyncAiRequest) "Searching the web · waiting for results" else "Ready · commands run on device; web/AI uses internet"
        onSpeak(answer)
        input = ""
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Microphone permission granted · tap mic again to speak" else "Microphone permission denied"
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        if (activity?.intent?.getBooleanExtra("nova_wake_detected", false) == true) {
            activity.intent.removeExtra("nova_wake_detected")
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                SpeechRecognizer.isRecognitionAvailable(context)) {
                val offlineAvailable = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                if (offlineAvailable) {
                    runCatching {
                        recognizer?.destroy()
                        val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        recognizer = sr
                        sr.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) { status = "Hey Nova heard · listening for your command" }
                            override fun onBeginningOfSpeech() { listening = true }
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() { listening = false }
                            override fun onError(error: Int) {
                                listening = false
                                status = "Wake detected, but command listening ended · code $error"
                            }
                            override fun onResults(results: Bundle?) {
                                listening = false
                                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                                if (spoken != null) respond(spoken) else status = "Wake detected, but no command was recognized"
                            }
                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        sr.startListening(speechIntent)
                        listening = true
                    }.onFailure {
                        status = "Hey Nova heard · tap the microphone to give a command"
                    }
                } else {
                    status = "Hey Nova heard · install an offline speech pack, then tap the microphone to speak"
                }
            } else {
                status = "Hey Nova heard · tap the microphone to give a command"
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9C8CFF),
            secondary = Color(0xFF67E8D2),
            background = Color(0xFF090B12),
            surface = Color(0xFF151927)
        )
    ) {
        if (showNovaSettings) {
            AlertDialog(
                onDismissRequest = { showNovaSettings = false },
                title = { Text("Nova settings") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AI answers & web search", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = geminiApiKeyDraft,
                            onValueChange = { geminiApiKeyDraft = it },
                            label = { Text("Gemini API key (optional)") },
                            placeholder = { Text("Paste your own key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "With a key, Nova searches the web and asks Gemini to summarize results inside the app. Without a key, Nova shows web results. Requests use the internet; your key is stored in Nova's private app preferences.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        TextButton(
                            onClick = {
                                geminiApiKey = geminiApiKeyDraft.trim()
                                context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("gemini_api_key", geminiApiKey).apply()
                                status = if (geminiApiKey.isBlank()) "Gemini key cleared" else "Gemini API key saved on this device"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Gemini API key") }
                        Divider(color = Color(0xFF30364A))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Hey Nova wake phrase", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "When enabled, Nova uses Android's on-device speech recognizer to listen for “Hey Nova” and open the app. No cloud recognizer fallback is used. It can use more battery than a dedicated wake-word model.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = wakePhraseMode,
                                onCheckedChange = { enabled ->
                                    val prefs = context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE)
                                    if (enabled) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            runCatching {
                                                androidx.core.content.ContextCompat.startForegroundService(
                                                    context, Intent(context, WakePhraseService::class.java)
                                                )
                                                wakePhraseMode = true
                                                prefs.edit().putBoolean("wake_phrase_mode", true).apply()
                                                status = "Hey Nova listening started · on-device recognition only"
                                            }.onFailure {
                                                wakePhraseMode = false
                                                prefs.edit().putBoolean("wake_phrase_mode", false).apply()
                                                status = "Couldn't start wake phrase listener. Check microphone permission and try again."
                                            }
                                        } else {
                                            wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    } else {
                                        wakePhraseMode = false
                                        prefs.edit().putBoolean("wake_phrase_mode", false).apply()
                                        context.stopService(Intent(context, WakePhraseService::class.java))
                                        status = "Hey Nova wake phrase stopped"
                                    }
                                }
                            )
                        }
                        Text(
                            "Android may require the wake listener to be enabled again after a reboot or force-stop. A dedicated Hey Nova neural model is not bundled because a free downloadable English model for that exact phrase is not available in the public model set; this fallback uses on-device speech recognition and is less efficient.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Divider(color = Color(0xFF30364A))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Lock-screen access", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Allow Nova to appear over the lock screen when Android launches it. Your PIN, password, or biometrics are still required to unlock the phone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = lockScreenAccess,
                                onCheckedChange = {
                                    lockScreenAccess = it
                                    onLockScreenAccessChanged(it)
                                }
                            )
                        }
                        Divider(color = Color(0xFF30364A))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Keep Nova available in background", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Keeps a small foreground service running after you leave or swipe away from Nova. A persistent notification will be shown.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = backgroundMode,
                                onCheckedChange = { enabled ->
                                    backgroundMode = enabled
                                    context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("background_mode", enabled).apply()
                                    val serviceIntent = Intent(context, BackgroundKeepAliveService::class.java)
                                    if (enabled) {
                                        runCatching {
                                            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                                        }.onFailure {
                                            backgroundMode = false
                                            context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE)
                                                .edit().putBoolean("background_mode", false).apply()
                                            status = "Android could not start background mode. Open Nova and try again."
                                        }
                                    } else {
                                        context.stopService(serviceIntent)
                                        status = "Nova background mode stopped"
                                    }
                                }
                            )
                        }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }.onFailure {
                                    status = "Open Android Settings > Apps > Nova > Battery and choose Unrestricted if available."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open battery optimization settings")
                        }
                        Text(
                            "For best persistence, allow background activity / unrestricted battery use for Nova if your phone offers it. Android can still stop apps after Force stop. Hey Nova wake-word detection needs its own offline model and is not enabled in this build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNovaSettings = false }) { Text("Done") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
                        }
                    }) { Text("Phone security") }
                }
            )
        }

        Scaffold(
            containerColor = Color(0xFF090B12),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("NOVA", fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Text(
                                "YOUR PRIVATE AI COMPANION",
                                fontSize = 10.sp,
                                color = Color(0xFF9C8CFF),
                                letterSpacing = 1.4.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNovaSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Android settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF090B12),
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF101421))
                        .navigationBarsPadding().imePadding().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                status = "No speech recognition service installed"
                            } else {
                                runCatching {
                                    recognizer?.destroy()
                                    val onDeviceAvailable = Build.VERSION.SDK_INT >= 31 &&
                                        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                                    val sr = if (onDeviceAvailable) {
                                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                                    } else {
                                        SpeechRecognizer.createSpeechRecognizer(context)
                                    }
                                    recognizer = sr
                                    sr.setRecognitionListener(object : RecognitionListener {
                                        override fun onReadyForSpeech(params: Bundle?) {
                                            status = if (onDeviceAvailable) "Listening · on-device recognizer" else "Listening · OS recognizer may use network"
                                        }
                                        override fun onBeginningOfSpeech() { listening = true }
                                        override fun onRmsChanged(rmsdB: Float) {}
                                        override fun onBufferReceived(buffer: ByteArray?) {}
                                        override fun onEndOfSpeech() { listening = false }
                                        override fun onError(error: Int) {
                                            listening = false
                                            status = "Voice input ended · code " + error
                                        }
                                        override fun onResults(results: Bundle?) {
                                            listening = false
                                            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                                            if (spoken != null) respond(spoken) else status = "No speech recognized"
                                        }
                                        override fun onPartialResults(partialResults: Bundle?) {}
                                        override fun onEvent(eventType: Int, params: Bundle?) {}
                                    })
                                    val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                                    sr.startListening(speechIntent)
                                    listening = true
                                }.onFailure {
                                    listening = false
                                    status = "Couldn't start voice input"
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp).background(
                            if (listening) Color(0xFFB84666) else Color(0xFF292440),
                            CircleShape
                        )
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = Color.White)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Nova anything…", color = Color.Gray) },
                        shape = RoundedCornerShape(18.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF9C8CFF),
                            unfocusedBorderColor = Color(0xFF34394C)
                        )
                    )
                    IconButton(
                        onClick = { respond(input) },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(48.dp).background(Color(0xFF7B68EE), CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send message", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(154.dp).background(
                        Brush.linearGradient(
                            listOf(Color(0xFF25204A), Color(0xFF111B2A), Color(0xFF101421))
                        ),
                        RoundedCornerShape(24.dp)
                    ).padding(18.dp)
                ) {
                    Column(Modifier.align(Alignment.CenterStart)) {
                        Text(
                            "YOUR AI. YOUR DEVICE.",
                            color = Color(0xFF67E8D2),
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "A more independent\nassistant starts here.",
                            color = Color.White,
                            fontSize = 23.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = Color(0xFFB5B7CA), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "CONVERSATION",
                    color = Color(0xFF8F93AA),
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(messages) { message ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.fromNova) Arrangement.Start else Arrangement.End
                        ) {
                            Column(
                                Modifier.widthIn(max = 310.dp)
                                    .background(
                                        if (message.fromNova) Color(0xFF171B2B) else Color(0xFF37305D),
                                        RoundedCornerShape(18.dp)
                                    ).padding(13.dp)
                            ) {
                                Text(
                                    if (message.fromNova) "NOVA" else "YOU",
                                    color = if (message.fromNova) Color(0xFF67E8D2) else Color(0xFFC9C0FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(message.text, color = Color(0xFFF2F2F8), fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
