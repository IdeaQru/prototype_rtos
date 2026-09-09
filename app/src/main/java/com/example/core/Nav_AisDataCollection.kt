package com.example.core


fun collectAisNav(seq: Long, @Suppress("UNUSED_PARAMETER") epoch: Long = System.currentTimeMillis()): NAV_AisDataModel {
    return NAV_AisDataModel(
        lat = -6.175392 + (seq * 0.00001),
        lon = 106.827153 + (seq * 0.00001),
        sog = 12.4 + (Math.sin(seq.toDouble()) * 0.5),
        cog = (45.0 + (seq * 0.2)) % 360.0,
        headingDeg = (45.0 + (seq * 0.2)) % 360.0,
        bearing = (45.0 + (seq * 0.2)) % 360.0,
        navigationStatus = "Underway using engine"
    )
}
