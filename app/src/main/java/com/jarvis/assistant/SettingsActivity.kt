package com.jarvis.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

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
        b.bubbleSwitch.isChecked = p.bubbleEnabled
        b.versionText.text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

        b.saveBtn.setOnClickListener {
            p.apiKey = b.apiKeyInput.text.toString().trim()
            p.model = b.modelInput.text.toString().trim().ifEmpty { "gemini-2.5-flash" }
            p.userName = b.nameInput.text.toString().trim()
            p.city = b.cityInput.text.toString().trim()
            p.sosName = b.sosNameInput.text.toString().trim()
            p.sosNumber = b.sosNumberInput.text.toString().trim()
            p.wakeEnabled = b.wakeSwitch.isChecked
            p.wakePhrase = b.wakePhraseInput.text.toString().trim().ifEmpty { "hey baby" }
            p.bubbleEnabled = b.bubbleSwitch.isChecked

            val svc = Intent(this, WakeService::class.java)
            if (p.wakeEnabled) {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
            } else {
                stopService(svc)
            }

            val overlay = Intent(this, OverlayService::class.java)
            if (p.bubbleEnabled) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Allow 'Display over other apps' for Jarvis, then Save again.", Toast.LENGTH_LONG).show()
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(overlay) else startService(overlay)
                }
            } else {
                stopService(overlay)
            }

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        b.updateBtn.setOnClickListener { checkForUpdate() }
    }

    private fun checkForUpdate() {
        b.updateBtn.isEnabled = false
        b.updateBtn.text = "Checking\u2026"
        lifecycleScope.launch {
            val rel = UpdateManager.check()
            b.updateBtn.isEnabled = true
            b.updateBtn.text = "Check for updates"
            if (rel == null) {
                Toast.makeText(this@SettingsActivity, "You're on the latest version.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Update available: ${rel.name}")
                .setMessage(if (rel.notes.isBlank()) "A newer version is ready to install." else rel.notes)
                .setPositiveButton("Update") { _, _ -> doUpdate(rel) }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun doUpdate(rel: UpdateManager.Release) {
        if (!UpdateManager.ensureInstallPermission(this)) {
            Toast.makeText(this, "Allow installs for Jarvis, then tap Update again.", Toast.LENGTH_LONG).show()
            return
        }
        b.updateBtn.isEnabled = false
        b.updateBtn.text = "Downloading\u2026"
        lifecycleScope.launch {
            val file = UpdateManager.download(this@SettingsActivity, rel.apkUrl)
            b.updateBtn.isEnabled = true
            b.updateBtn.text = "Check for updates"
            if (file == null) {
                Toast.makeText(this@SettingsActivity, "Download failed. Try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            UpdateManager.install(this@SettingsActivity, file)
        }
    }
}
