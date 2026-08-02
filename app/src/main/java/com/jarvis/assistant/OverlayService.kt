package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs

class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var bubble: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            startForeground(43, buildNotification())
        } catch (e: Exception) { /* keep going; some devices allow without */ }
        addBubble()
    }

    private fun addBubble() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val size = (62 * density).toInt()
        val pad = (10 * density).toInt()

        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            setBackgroundResource(R.drawable.bubble_orb)
            setPadding(pad, pad, pad, pad)
        }

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (16 * density).toInt()
        lp.y = (220 * density).toInt()

        iv.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false
            var longFired = false
            val longPress = Runnable {
                longFired = true
                analyzeScreen()
            }
            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x; initialY = lp.y
                        touchX = e.rawX; touchY = e.rawY
                        moved = false; longFired = false
                        handler.postDelayed(longPress, 650)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) {
                            moved = true
                            handler.removeCallbacks(longPress)
                        }
                        lp.x = initialX + dx; lp.y = initialY + dy
                        try { wm?.updateViewLayout(bubble, lp) } catch (ex: Exception) {}
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPress)
                        if (!moved && !longFired) openJarvis()
                    }
                }
                return true
            }
        })

        bubble = iv
        try { wm?.addView(iv, lp) } catch (e: Exception) { stopSelf() }
    }

    private fun openJarvis() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("listen", true)
        try { startActivity(i) } catch (e: Exception) {}
    }

    private fun analyzeScreen() {
        val svc = JarvisAccessibilityService.instance
        if (svc == null) {
            // Accessibility not enabled yet — send user to enable it
            try {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {}
            return
        }
        val text = svc.currentScreenText()
        val pkg = JarvisAccessibilityService.lastApp
        val prefs = Prefs(this)

        if (pkg.isBlank() || prefs.isAppAllowed(pkg)) {
            // already permitted (or unknown app) — analyze straight away
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("analyze_text", text)
            try { startActivity(i) } catch (e: Exception) {}
        } else {
            // ask the user's permission first
            val i = Intent(this, ConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("pkg", pkg)
                .putExtra("text", text)
            try { startActivity(i) } catch (e: Exception) {}
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            "jarvis_bubble", "Jarvis bubble", NotificationManager.IMPORTANCE_MIN
        )
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, "jarvis_bubble")
            .setContentTitle("Jarvis")
            .setContentText("Floating bubble active")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try { bubble?.let { wm?.removeView(it) } } catch (e: Exception) {}
        super.onDestroy()
    }
}
