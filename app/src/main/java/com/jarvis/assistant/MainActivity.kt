package com.jarvis.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var tts: TextToSpeech? = null
    private val history = mutableListOf<Pair<String, String>>()

    private val neededPerms = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) sendMessage(spoken)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }

        requestPermissionsIfNeeded()

        b.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.sendBtn.setOnClickListener { sendTyped() }
        b.micBtn.setOnClickListener { startListening() }

        greet()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.apiKey.isBlank()) {
            addBubble("Tip: open Settings (top-right gear) and paste your free Gemini key so I can think.", false)
        }
    }

    private fun greet() {
        val name = prefs.userName.ifBlank { "there" }
        val hour = SimpleDateFormat("H", Locale.getDefault()).format(Date()).toInt()
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
        val msg = "$part, $name. Jarvis here. How can I help?"
        addBubble(msg, false)
        speak(msg)
    }

    private fun requestPermissionsIfNeeded() {
        val missing = neededPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening…")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            addBubble("Voice input isn't available on this device.", false)
        }
    }

    private fun sendTyped() {
        val text = b.messageInput.text.toString().trim()
        if (text.isEmpty()) return
        b.messageInput.setText("")
        sendMessage(text)
    }

    private fun sendMessage(text: String) {
        addBubble(text, true)
        history.add("user" to text)
        trimHistory()
        b.statusText.text = "thinking…"

        lifecycleScope.launch {
            val reply = GeminiClient.generate(prefs.apiKey, prefs.model, systemPrompt(), history)
            b.statusText.text = "online"

            if (reply.startsWith("ERROR:")) {
                val short = reply.removePrefix("ERROR:").trim()
                addBubble("⚠ $short", false)
                return@launch
            }

            val action = tryParseAction(reply)
            if (action != null) {
                val confirmation = ActionHandler.handle(this@MainActivity, action, prefs)
                history.add("model" to confirmation)
                addBubble(confirmation, false)
                speak(confirmation)
            } else {
                history.add("model" to reply)
                addBubble(reply, false)
                speak(reply)
            }
            trimHistory()
        }
    }

    private fun tryParseAction(reply: String): JSONObject? {
        var t = reply.trim()
        // strip ```json ... ``` fences if the model added them
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
        val now = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        return """
You are Jarvis, a warm, concise, slightly witty personal assistant for $name.
Current date and time: $now.
Speak naturally and briefly, like a helpful human assistant. Keep replies to 1-3 sentences unless asked for detail.

When $name wants you to perform a phone action, reply with ONLY a JSON object and nothing else (no markdown, no explanation). Use these exact shapes:
- Set an alarm: {"action":"alarm","hour":7,"minute":30,"label":"wake up"}
- Reminder at a clock time: same as alarm.
- Countdown timer: {"action":"timer","seconds":600,"label":"tea"}
- Call someone: {"action":"call","contact":"Mom"} or {"action":"call","number":"+15551234"}
- Send a text: {"action":"sms","contact":"Dad","message":"On my way"}
- Emergency SOS (sends location to their trusted contact): {"action":"sos"}
- Weather: {"action":"weather"}
- Add a calendar event: {"action":"calendar","title":"Dentist","location":"Clinic"}
- Web search: {"action":"search","query":"best pizza near me"}

Compute relative times (e.g. "in 10 minutes", "tomorrow 6am") yourself using the current time.
For anything else — questions, chat, advice — just reply normally in plain text. Never mix JSON and plain text.
        """.trimIndent()
    }

    private fun trimHistory() {
        while (history.size > 20) history.removeAt(0)
    }

    private fun speak(text: String) {
        if (text.length > 600) {
            tts?.speak(text.take(600), TextToSpeech.QUEUE_FLUSH, null, "j")
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j")
        }
    }

    private fun addBubble(text: String, isUser: Boolean) {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            setPadding(36, 24, 36, 24)
            setBackgroundResource(if (isUser) R.drawable.bubble_user else R.drawable.bubble_jarvis)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 10
            gravity = if (isUser) Gravity.END else Gravity.START
            marginStart = if (isUser) 80 else 0
            marginEnd = if (isUser) 0 else 80
        }
        tv.layoutParams = lp
        b.chatContainer.addView(tv)
        b.chatScroll.post { b.chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
