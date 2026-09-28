package com.xiaohan.xhsnotegen.domain

/** Where a meal happened, in a Country → Region → City hierarchy. */
data class Place(
    val country: String = "",
    val region: String = "",   // province / state
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: PlaceSource? = null,
) {
    val isKnown: Boolean get() = country.isNotBlank() || city.isNotBlank()
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    /** "Nanjing, Jiangsu, China" style label; empty when unknown. */
    val label: String
        get() = listOf(city, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/** How a note's place was found — manual edits are never overwritten by auto-organize. */
enum class PlaceSource(val key: String) {
    GPS("gps"), TEXT("text"), MANUAL("manual");

    companion object {
        fun fromKey(key: String?): PlaceSource? = entries.firstOrNull { it.key == key }
    }
}

/** A user label on notes. */
data class NoteTag(val id: Long = 0, val name: String)
