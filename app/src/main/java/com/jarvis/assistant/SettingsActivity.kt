package com.jarvis.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val p = Prefs(this)
        b.apiKeyInput.setText(p.apiKey)
        b.modelInput.setText(p.model)
        b.nameInput.setText(p.userName)
        b.cityInput.setText(p.city)
        b.sosNameInput.setText(p.sosName)
        b.sosNumberInput.setText(p.sosNumber)
        b.wakeSwitch.isChecked = p.wakeEnabled
        b.wakePhraseInput.setText(p.wakePhrase)

        b.saveBtn.setOnClickListener {
            p.apiKey = b.apiKeyInput.text.toString().trim()
            p.model = b.modelInput.text.toString().trim().ifEmpty { "gemini-2.5-flash" }
            p.userName = b.nameInput.text.toString().trim()
            p.city = b.cityInput.text.toString().trim()
            p.sosName = b.sosNameInput.text.toString().trim()
            p.sosNumber = b.sosNumberInput.text.toString().trim()
            p.wakeEnabled = b.wakeSwitch.isChecked
            p.wakePhrase = b.wakePhraseInput.text.toString().trim().ifEmpty { "hey baby" }

            val svc = Intent(this, WakeService::class.java)
            if (p.wakeEnabled) {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
            } else {
                stopService(svc)
            }

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
