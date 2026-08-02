package com.jarvis.assistant

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ConsentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra("pkg") ?: ""
        val text = intent.getStringExtra("text") ?: ""
        val label = appLabel(pkg)

        AlertDialog.Builder(this)
            .setTitle("Allow Jarvis to read $label?")
            .setMessage("Jarvis will read what\u2019s currently on this screen so it can analyze it for you.")
            .setPositiveButton("Allow once") { _, _ -> proceed(pkg, text) }
            .setNeutralButton("Always allow") { _, _ ->
                if (pkg.isNotBlank()) Prefs(this).allowApp(pkg)
                proceed(pkg, text)
            }
            .setNegativeButton("Deny") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun proceed(pkg: String, text: String) {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("analyze_text", text)
            .putExtra("analyze_app", appLabel(pkg))
        startActivity(i)
        finish()
    }

    private fun appLabel(pkg: String): String {
        if (pkg.isBlank()) return "this app"
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "this app"
        }
    }
}
