package com.example.core

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
        return String.format(
            Locale.US,
            "| Lat: %.6f | Lon: %.6f | sog : %.1f kts | cog %.1f | Heading: %.1f deg | bearing %.1f | Status : %s",
            lat, lon, sog, cog, headingDeg, bearing, navigationStatus
        )
    }
}
