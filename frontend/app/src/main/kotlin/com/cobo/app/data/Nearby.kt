package com.cobo.app.data

import android.content.Context
import android.location.LocationManager
import com.cobo.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 「附近」里可选的一座城市：名字给界面看，坐标给同城流。 */
data class NearbyPlace(val name: String, val lat: Double, val lon: Double)

/**
 * 当前该看哪座城。
 *
 * 设了自定义位置就直接用那组坐标，不再碰定位（也就不需要定位权限）；
 * 否则读设备最后一次定位。拿不到返回 null，由调用方决定要不要提示。
 */
suspend fun nearbyCoords(context: Context): Pair<Double, Double>? {
    val settings = App.settings
    if (settings.nearbyCustom) {
        return settings.nearbyPlaceLat.toDouble() to settings.nearbyPlaceLon.toDouble()
    }
    return deviceCoords(context)
}

/** 设备最后一次定位：三个 provider 各取一份，留最近的那条。 */
suspend fun deviceCoords(context: Context): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null
    listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
}
