package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateManager {

    private const val RELEASES =
        "https://api.github.com/repos/robingp/jarvis-upload/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String)

    /** Returns a Release if a newer version exists, else null. Returns null on any error. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(RELEASES)
                .header("Accept", "application/vnd.github+json").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                val tag = json.optString("tag_name", "")          // e.g. "v3"
                val code = tag.filter { it.isDigit() }.toIntOrNull() ?: return@withContext null
                val name = json.optString("name", tag)
                val notes = json.optString("body", "")
                val assets = json.optJSONArray("assets") ?: return@withContext null
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url"); break
                    }
                }
                if (apkUrl == null) return@withContext null
                if (code <= BuildConfig.VERSION_CODE) return@withContext null
                Release(code, name, notes, apkUrl)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the APK to cache, returns the file or null. */
    suspend fun download(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val file = File(context.cacheDir, "jarvis-update.apk")
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
                file
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True if we can install; if not, sends user to the permission screen. */
    fun ensureInstallPermission(context: Context): Boolean {
        return if (context.packageManager.canRequestPackageInstalls()) {
            true
        } else {
            val i = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(i) } catch (e: Exception) {}
            false
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }
}
