package com.cobo.app.data

import com.cobo.app.App
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.platformDeviceCoords

data class NearbyPlace(val name: String, val lat: Double, val lon: Double)

suspend fun nearbyCoords(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()): Pair<Double, Double>? {
    val settings = App.settings
    if (settings.nearbyCustom) {
        return settings.nearbyPlaceLat.toDouble() to settings.nearbyPlaceLon.toDouble()
    }
    return deviceCoords(context)
}

suspend fun deviceCoords(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()): Pair<Double, Double>? =
    platformDeviceCoords()
