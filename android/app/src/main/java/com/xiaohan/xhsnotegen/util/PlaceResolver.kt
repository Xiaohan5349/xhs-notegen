package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.xiaohan.xhsnotegen.domain.FoodInfo
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.PlaceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Turns coordinates or free text ("老城南面馆, 南京") into a Country → Region → City
 * place using the phone's geocoder. Names are requested in Simplified Chinese
 * (南京市 · 江苏省 · 中国) to match the notes, which are written in Chinese.
 */
object PlaceResolver {

    private val NAMES_LOCALE: Locale = Locale.SIMPLIFIED_CHINESE

    fun isAvailable(): Boolean = Geocoder.isPresent()

    @Suppress("DEPRECATION") // async overloads are API 33+; these run on Dispatchers.IO
    suspend fun fromCoordinates(context: Context, lat: Double, lng: Double): Place? = withContext(Dispatchers.IO) {
        if (!isAvailable() || isNullIsland(lat, lng)) return@withContext null
        runCatching {
            Geocoder(context, NAMES_LOCALE).getFromLocation(lat, lng, 1)?.firstOrNull()
                ?.toPlace(PlaceSource.GPS, lat, lng)
        }.onFailure { Log.d("PlaceResolver", "reverse geocode failed: ${it.javaClass.simpleName} ${it.message}") }
            .getOrNull()
    }

    @Suppress("DEPRECATION")
    suspend fun fromText(context: Context, query: String): Place? = withContext(Dispatchers.IO) {
        if (!isAvailable() || query.isBlank()) return@withContext null
        runCatching {
            Geocoder(context, NAMES_LOCALE).getFromLocationName(query, 1)?.firstOrNull()
                ?.let { it.toPlace(PlaceSource.TEXT, it.latitude, it.longitude) }
        }.getOrNull()
    }

    /**
     * Best place for a note: its GPS if known, else its text — trying the most
     * specific query first ("restaurant, area", then area, then restaurant).
     */
    suspend fun forNote(context: Context, info: FoodInfo): Place? {
        info.place.takeIf { it.hasCoordinates }?.let { p ->
            fromCoordinates(context, p.latitude!!, p.longitude!!)?.let { return it }
        }
        val queries = listOf(
            listOf(info.restaurantName, info.location).filter { it.isNotBlank() }.joinToString(", "),
            info.location,
            info.restaurantName,
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        for (q in queries) fromText(context, q)?.takeIf { it.isKnown }?.let { return it }
        return null
    }

    /** Stripped EXIF often reads as exactly 0,0 — that's "unknown", not the Gulf of Guinea. */
    fun isNullIsland(lat: Double, lng: Double) = lat == 0.0 && lng == 0.0

    private fun Address.toPlace(source: PlaceSource, lat: Double?, lng: Double?): Place? {
        // Municipalities (上海, 北京…) often have no locality; fall back through the admin levels.
        val city = locality ?: subAdminArea ?: adminArea
        val place = Place(
            country = countryName.orEmpty(),
            region = adminArea.orEmpty().takeIf { it != city }.orEmpty(),
            city = city.orEmpty(),
            latitude = lat, longitude = lng,
            source = source,
        )
        return place.takeIf { it.isKnown }
    }
}
