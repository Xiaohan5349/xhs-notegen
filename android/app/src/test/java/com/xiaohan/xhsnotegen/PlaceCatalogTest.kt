package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.domain.PlaceCatalog
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class PlaceCatalogTest {

    @Before
    fun load() {
        // Unit tests run from the module directory.
        File("src/main/assets/places.txt").inputStream().use { PlaceCatalog.load(it) }
    }

    @Test
    fun `parses the bundled list`() {
        val cn = PlaceCatalog.country("中国")!!
        assertEquals("CN", cn.code)
        assertTrue(cn.regions.size >= 30)
        assertEquals(13, PlaceCatalog.region("中国", "江苏省")!!.cities.size)
        assertTrue(PlaceCatalog.region("中国", "江苏省")!!.cities.any { it.cn == "南京市" && it.en == "Nanjing" })
        // Countries without regions list their cities directly.
        assertTrue(PlaceCatalog.country("新加坡")!!.regions.isEmpty())
        assertTrue(PlaceCatalog.country("新加坡")!!.cities.isNotEmpty())
        // A region has at least one city, everywhere.
        PlaceCatalog.countries(true).forEach { c -> c.regions.forEach { assertTrue("${c.name.cn} ${it.name.cn}", it.cities.isNotEmpty()) } }
    }

    @Test
    fun `lists every country, pinned first, in both languages`() {
        val zh = PlaceCatalog.countries(true)
        assertEquals(listOf("中国", "美国", "日本"), zh.take(3).map { it.name.cn })
        assertTrue(zh.size > 200)                    // all of the world, not just the data file
        assertTrue(zh.any { it.name.cn == "冰岛" })  // has no region data but is still choosable
        assertEquals("China", PlaceCatalog.countries(false).first().name.en)
        assertEquals(zh.size, zh.map { it.code }.distinct().size)
    }

    @Test
    fun `a region that is the city is dropped, so it groups like a geocoded place`() {
        val p = PlaceCatalog.place("中国", "上海市", "上海市")
        assertEquals("", p.region)
        assertEquals("上海市", p.city)
        assertEquals("江苏省", PlaceCatalog.place("中国", "江苏省", "南京市").region)
    }
}
