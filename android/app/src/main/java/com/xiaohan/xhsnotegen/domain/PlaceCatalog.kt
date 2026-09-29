package com.xiaohan.xhsnotegen.domain

import android.content.Context
import java.io.InputStream
import java.text.Collator
import java.util.Locale

/** A place name in both languages. Places are stored under the Chinese name, so grouping stays consistent. */
data class PlaceName(val cn: String, val en: String) {
    fun label(zh: Boolean) = if (zh) cn else en
}

data class CatalogRegion(val name: PlaceName, val cities: List<PlaceName>)

data class CatalogCountry(
    val name: PlaceName,
    val code: String,
    val regions: List<CatalogRegion> = emptyList(),
    /** Cities directly under the country (countries without regions in the list). */
    val cities: List<PlaceName> = emptyList(),
)

/**
 * Offline Country → Region → City lists for choosing where a note happened by hand — it
 * needs no internet or Google services, so it works on phones in mainland China too.
 * Countries come from the phone's own locale data (all of them); regions and cities from
 * assets/places.txt (lines `C|中文|English|ISO`, `R|中文|English`, `T|中文|English`).
 */
object PlaceCatalog {

    /** The first countries in the data file are pinned to the top of the list. */
    private const val PINNED = 14

    private var withData: List<CatalogCountry> = emptyList()

    fun init(context: Context) {
        if (withData.isNotEmpty()) return
        context.assets.open("places.txt").use { load(it) }
    }

    /** Reads the list; also used by tests. */
    fun load(input: InputStream) {
        withData = parse(input.bufferedReader(Charsets.UTF_8).readLines())
    }

    internal fun parse(lines: List<String>): List<CatalogCountry> {
        val out = mutableListOf<CatalogCountry>()
        var country: CatalogCountry? = null
        var regionName: PlaceName? = null
        var regionCities = mutableListOf<PlaceName>()
        var countryCities = mutableListOf<PlaceName>()
        var regions = mutableListOf<CatalogRegion>()

        fun flushRegion() { regionName?.let { regions.add(CatalogRegion(it, regionCities)) }; regionName = null; regionCities = mutableListOf() }
        fun flushCountry() {
            flushRegion()
            country?.let { out.add(it.copy(regions = regions, cities = countryCities)) }
            country = null; regions = mutableListOf(); countryCities = mutableListOf()
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val f = line.split('|').map { it.trim() }
            when (f[0]) {
                "C" -> if (f.size >= 4) { flushCountry(); country = CatalogCountry(PlaceName(f[1], f[2]), f[3]) }
                "R" -> if (f.size >= 3 && country != null) { flushRegion(); regionName = PlaceName(f[1], f[2]) }
                "T" -> if (f.size >= 3 && country != null) {
                    val city = PlaceName(f[1], f[2])
                    if (regionName != null) regionCities.add(city) else countryCities.add(city)
                }
            }
        }
        flushCountry()
        return out
    }

    /** Every country: the pinned ones first, then the rest in alphabetical order for the language shown. */
    fun countries(zh: Boolean): List<CatalogCountry> {
        val byCode = withData.associateBy { it.code }
        val pinned = withData.take(PINNED)
        val pinnedCodes = pinned.map { it.code }.toSet()
        val collator = Collator.getInstance(if (zh) Locale.CHINA else Locale.ENGLISH)
        val rest = Locale.getISOCountries().filter { it !in pinnedCodes }.map { code ->
            byCode[code] ?: CatalogCountry(
                PlaceName(Locale("", code).getDisplayCountry(Locale.SIMPLIFIED_CHINESE), Locale("", code).getDisplayCountry(Locale.ENGLISH)),
                code,
            )
        }.filter { it.name.cn.isNotBlank() && it.name.en.isNotBlank() }
            // A few data-file countries (e.g. Taiwan under its own name) aren't ISO-list duplicates.
            .plus(withData.filter { it.code !in pinnedCodes && it.code !in Locale.getISOCountries() })
            .sortedWith { a, b -> collator.compare(a.name.label(zh), b.name.label(zh)) }
        return pinned + rest
    }

    fun country(cn: String): CatalogCountry? =
        withData.firstOrNull { it.name.cn == cn } ?: countries(true).firstOrNull { it.name.cn == cn }

    fun region(countryCn: String, regionCn: String): CatalogRegion? =
        country(countryCn)?.regions?.firstOrNull { it.name.cn == regionCn }

    /**
     * Country → region → city as the Chinese names the rest of the app uses. A region that is
     * really the city (北京市, 香港) is left out, as the phone's geocoder does.
     */
    fun place(country: String, region: String, city: String, address: String = ""): Place {
        val r = region.trim().takeIf { it != city.trim() }.orEmpty()
        return Place(country = country.trim(), region = r, city = city.trim(), address = address.trim(), source = PlaceSource.MANUAL)
    }
}
