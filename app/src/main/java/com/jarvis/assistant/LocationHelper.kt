package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

object LocationHelper {

    /** Returns a Google Maps link to the last known location, or null. */
    fun lastLocationLink(context: Context): String? {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return try {
            val providers = lm.getProviders(true)
            var best: android.location.Location? = null
            for (p in providers) {
                @Suppress("MissingPermission")
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.accuracy < best!!.accuracy) best = l
            }
            best?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
