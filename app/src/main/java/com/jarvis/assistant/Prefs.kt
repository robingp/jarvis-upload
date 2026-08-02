package com.jarvis.assistant

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v).apply()

    var userName: String
        get() = sp.getString("userName", "") ?: ""
        set(v) = sp.edit().putString("userName", v).apply()

    var city: String
        get() = sp.getString("city", "") ?: ""
        set(v) = sp.edit().putString("city", v).apply()

    var sosName: String
        get() = sp.getString("sosName", "") ?: ""
        set(v) = sp.edit().putString("sosName", v).apply()

    var sosNumber: String
        get() = sp.getString("sosNumber", "") ?: ""
        set(v) = sp.edit().putString("sosNumber", v).apply()
}
