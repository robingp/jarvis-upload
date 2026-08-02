package com.jarvis.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WeatherClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun forCity(city: String): String = withContext(Dispatchers.IO) {
        if (city.isBlank()) return@withContext "I don't know your city yet. Add it in Settings."
        try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(city, "UTF-8") + "&count=1"
            val geoRaw = get(geoUrl) ?: return@withContext "I couldn't reach the weather service."
            val results = JSONObject(geoRaw).optJSONArray("results")
            if (results == null || results.length() == 0)
                return@withContext "I couldn't find a place called $city."
            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val name = place.optString("name", city)

            val wUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
            val wRaw = get(wUrl) ?: return@withContext "I couldn't reach the weather service."
            val w = JSONObject(wRaw)
            val cur = w.getJSONObject("current")
            val temp = cur.getDouble("temperature_2m").toInt()
            val feels = cur.getDouble("apparent_temperature").toInt()
            val code = cur.getInt("weather_code")
            val daily = w.getJSONObject("daily")
            val max = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
            val min = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()

            "In $name it's $temp degrees, ${desc(code)}, feels like $feels. " +
                "Today's high is $max and low is $min."
        } catch (e: Exception) {
            "Sorry, I couldn't get the weather right now."
        }
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.string()
        }
    }

    private fun desc(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "foggy"
        51, 53, 55 -> "drizzling"
        61, 63, 65 -> "raining"
        66, 67 -> "freezing rain"
        71, 73, 75 -> "snowing"
        77 -> "snow grains"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorms"
        96, 99 -> "thunderstorms with hail"
        else -> "cloudy"
    }
}
