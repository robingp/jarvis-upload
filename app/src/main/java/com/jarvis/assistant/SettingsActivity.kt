package com.jarvis.assistant

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
        b.nameInput.setText(p.userName)
        b.cityInput.setText(p.city)
        b.sosNameInput.setText(p.sosName)
        b.sosNumberInput.setText(p.sosNumber)

        b.saveBtn.setOnClickListener {
            p.apiKey = b.apiKeyInput.text.toString().trim()
            p.userName = b.nameInput.text.toString().trim()
            p.city = b.cityInput.text.toString().trim()
            p.sosName = b.sosNameInput.text.toString().trim()
            p.sosNumber = b.sosNumberInput.text.toString().trim()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
