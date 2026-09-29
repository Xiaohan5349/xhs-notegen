package com.xiaohan.xhsnotegen.domain

/** Where a meal happened, in a Country → Region → City hierarchy. */
data class Place(
    val country: String = "",
    val region: String = "",   // province / state
    val city: String = "",
    val district: String = "", // 区 / neighborhood
    /** Full street address as the geocoder formats it, e.g. "中国上海市黄浦区南京东路100号". */
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: PlaceSource? = null,
) {
    val isKnown: Boolean get() = country.isNotBlank() || city.isNotBlank()
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    /** "Nanjing, Jiangsu, China" style label; empty when unknown. */
    val label: String
        get() = listOf(city, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")

    /** For showing: "南京市 · 江苏省 · 中国" (each level once). */
    val display: String
        get() = listOf(city, region, country).filter { it.isNotBlank() }.distinct().joinToString(" · ")

    /** Most detailed text available: the street address, else "city, region, country". */
    val fullAddress: String get() = address.ifBlank { label }
}

/** How a note's place was found — manual edits are never overwritten by auto-organize. */
enum class PlaceSource(val key: String) {
    GPS("gps"), TEXT("text"), MANUAL("manual");

    companion object {
        fun fromKey(key: String?): PlaceSource? = entries.firstOrNull { it.key == key }
    }
}

/** A user label on notes. [parentId] set = a sub-tag under a root (upper) tag. */
data class NoteTag(val id: Long = 0, val name: String, val parentId: Long? = null)
