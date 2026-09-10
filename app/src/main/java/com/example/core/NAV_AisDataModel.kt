package com.example.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NAV_AisDataModel(
    val lat: Double,
    val lon: Double,
    val sog: Double,
    val cog: Double,
    val headingDeg: Double,
    val bearing: Double,
    val navigationStatus: String
) {
    fun formatDisplay(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        val now = sdf.format(Date())
        return String.format(
            Locale.US,
            "Time: %s | Lat: %.6f | Lon: %.6f | SOG: %.1f kts | COG: %.1f | Heading: %.1f deg | Bearing: %.1f | Status: %s",
            now, lat, lon, sog, cog, headingDeg, bearing, navigationStatus
        )
    }
}
