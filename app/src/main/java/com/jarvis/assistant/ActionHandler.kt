package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

object ActionHandler {

    /** Returns a spoken confirmation message. Works from an Activity or a Service. */
    suspend fun handle(context: Context, action: JSONObject, prefs: Prefs): String {
        return when (action.optString("action")) {
            "alarm", "reminder" -> setAlarm(context, action)
            "timer" -> setTimer(context, action)
            "call" -> makeCall(context, action)
            "sms" -> sendSms(context, action)
            "sos" -> sos(context, prefs)
            "weather" -> WeatherClient.forCity(prefs.city)
            "calendar" -> addCalendar(context, action)
            "search" -> search(context, action)
            else -> action.optString("text", "Done.")
        }
    }

    private fun launch(context: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    private fun setAlarm(context: Context, a: JSONObject): String {
        val hour = a.optInt("hour", -1)
        val minute = a.optInt("minute", 0)
        val label = a.optString("label", "Jarvis alarm")
        if (hour < 0) return "I need a time to set that alarm."
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return try {
            launch(context, i)
            "Alarm set for %02d:%02d, %s.".format(hour, minute, label)
        } catch (e: Exception) {
            "I couldn't open the clock app."
        }
    }

    private fun setTimer(context: Context, a: JSONObject): String {
        val seconds = a.optInt("seconds", 0)
        val label = a.optString("label", "Jarvis timer")
        if (seconds <= 0) return "How long should the timer be?"
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return try {
            launch(context, i)
            "Timer started for ${seconds / 60} minutes ${seconds % 60} seconds."
        } catch (e: Exception) {
            "I couldn't start the timer."
        }
    }

    private fun makeCall(context: Context, a: JSONObject): String {
        val number = resolveNumber(context, a) ?: return "I couldn't find that contact."
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val i = Intent(
            if (hasPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.parse("tel:$number")
        )
        return try {
            launch(context, i)
            "Calling $number."
        } catch (e: Exception) {
            "I couldn't place the call."
        }
    }

    private fun sendSms(context: Context, a: JSONObject): String {
        val number = resolveNumber(context, a) ?: return "I couldn't find that contact."
        val message = a.optString("message", "")
        if (message.isBlank()) return "What should the message say?"
        return try {
            smsManager(context).sendTextMessage(number, null, message, null, null)
            "Message sent to $number."
        } catch (e: Exception) {
            "I couldn't send the message. Check SMS permission."
        }
    }

    private fun sos(context: Context, prefs: Prefs): String {
        val number = prefs.sosNumber
        if (number.isBlank()) return "Add an SOS contact in Settings first."
        val name = prefs.sosName.ifBlank { "your contact" }
        val loc = LocationHelper.lastLocationLink(context)
        val body = "SOS! I need help. My location: ${loc ?: "location unavailable"}"
        return try {
            smsManager(context).sendTextMessage(number, null, body, null, null)
            "SOS sent to $name with your location."
        } catch (e: Exception) {
            "I couldn't send the SOS. Check SMS permission."
        }
    }

    private fun addCalendar(context: Context, a: JSONObject): String {
        val title = a.optString("title", "Event")
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
        if (a.has("location")) i.putExtra(CalendarContract.Events.EVENT_LOCATION, a.optString("location"))
        return try {
            launch(context, i)
            "Opening your calendar to add: $title."
        } catch (e: Exception) {
            "I couldn't open the calendar."
        }
    }

    private fun search(context: Context, a: JSONObject): String {
        val q = a.optString("query", "")
        if (q.isBlank()) return "What should I search for?"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
        return try {
            launch(context, i)
            "Here's what I found for $q."
        } catch (e: Exception) {
            "I couldn't open the browser."
        }
    }

    private fun smsManager(context: Context): SmsManager {
        return if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun resolveNumber(context: Context, a: JSONObject): String? {
        val direct = a.optString("number", "")
        if (direct.isNotBlank()) return direct
        val contact = a.optString("contact", "")
        if (contact.isBlank()) return null
        return lookupContact(context, contact)
    }

    private fun lookupContact(context: Context, name: String): String? {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val sel = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
        val args = arrayOf("%$name%")
        context.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }
}
