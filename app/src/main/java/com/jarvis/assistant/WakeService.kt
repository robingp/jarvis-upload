package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class WakeService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_wake"
        const val NOTIF_ID = 42
        private const val MODE_WAKE = 0
        private const val MODE_COMMAND = 1
    }

    private lateinit var prefs: Prefs
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val history = mutableListOf<Pair<String, String>>()

    private var mode = MODE_WAKE
    private var busy = false      // processing Gemini or speaking
    private var ttsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            stopSelf()
            return
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applySoftVoice(tts)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                busy = false
                main.post { restartListening() }
            }
            @Deprecated("deprecated") override fun onError(id: String?) {
                busy = false
                main.post { restartListening() }
            }
        })

        setupRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        main.post { restartListening() }
        return START_STICKY
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {
                if (mode != MODE_WAKE) return
                val text = firstResult(partial) ?: return
                if (heardWake(text)) {
                    onWakeDetected()
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                if (!busy) main.postDelayed({ restartListening() }, 400)
            }
            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text == null) {
                    if (!busy) restartListening()
                    return
                }
                if (mode == MODE_WAKE) {
                    if (heardWake(text)) onWakeDetected() else restartListening()
                } else {
                    handleCommand(text)
                }
            }
        })
    }

    private fun heardWake(text: String): Boolean {
        val phrase = prefs.wakePhrase.lowercase(Locale.getDefault()).trim()
        return text.lowercase(Locale.getDefault()).contains(phrase)
    }

    private fun onWakeDetected() {
        mode = MODE_COMMAND
        busy = true
        speak("Yes?")   // after speaking, listener resumes in command mode
    }

    private fun handleCommand(command: String) {
        busy = true
        recognizer?.cancel()
        history.add("user" to command)
        while (history.size > 16) history.removeAt(0)

        scope.launch {
            val reply = GeminiClient.generate(prefs.apiKey, prefs.model, systemPrompt(), history)
            val toSay: String
            if (reply.startsWith("ERROR:")) {
                toSay = "Sorry, " + reply.removePrefix("ERROR:").trim()
            } else {
                val action = tryParseAction(reply)
                toSay = if (action != null) {
                    ActionHandler.handle(this@WakeService, action, prefs)
                } else reply
                history.add("model" to toSay)
            }
            mode = MODE_WAKE   // next turn needs the wake phrase again
            speak(toSay)
        }
    }

    private fun restartListening() {
        if (busy) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.cancel()
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            main.postDelayed({ restartListening() }, 800)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            busy = false
            restartListening()
            return
        }
        busy = true
        tts?.speak(text.take(500), TextToSpeech.QUEUE_FLUSH, null, "wake")
    }

    private fun tryParseAction(reply: String): JSONObject? {
        var t = reply.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        if (!t.startsWith("{")) return null
        return try {
            val o = JSONObject(t)
            if (o.has("action")) o else null
        } catch (e: Exception) {
            null
        }
    }

    private fun systemPrompt(): String {
        val name = prefs.userName.ifBlank { "the user" }
        return "You are Jarvis, a warm, soft-spoken personal assistant for $name. " +
            "Keep spoken replies to one or two short sentences. " +
            "For phone actions reply with ONLY a JSON object: " +
            "{\"action\":\"alarm\",\"hour\":7,\"minute\":0,\"label\":\"..\"}, " +
            "{\"action\":\"timer\",\"seconds\":600}, {\"action\":\"call\",\"contact\":\"..\"}, " +
            "{\"action\":\"sms\",\"contact\":\"..\",\"message\":\"..\"}, {\"action\":\"sos\"}, " +
            "{\"action\":\"weather\"}, {\"action\":\"search\",\"query\":\"..\"}. " +
            "Otherwise just reply in plain text. Never mix JSON with text."
    }

    private fun firstResult(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun applySoftVoice(engine: TextToSpeech?) {
        engine ?: return
        engine.language = Locale.US
        engine.setSpeechRate(0.94f)
        engine.setPitch(1.08f)
        try {
            val soft = engine.voices?.firstOrNull {
                val n = it.name.lowercase(Locale.getDefault())
                it.locale.language == "en" && (n.contains("female") || n.contains("f00") || n.contains("network"))
            }
            if (soft != null) engine.voice = soft
        } catch (e: Exception) { /* keep default */ }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID, "Jarvis listening", NotificationManager.IMPORTANCE_LOW
        )
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText("Listening for \"${prefs.wakePhrase}\"")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try { recognizer?.destroy() } catch (e: Exception) {}
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }
}
