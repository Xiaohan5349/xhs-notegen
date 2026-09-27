package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.json.normalizeHashtags
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.ai.AiWriter
import org.junit.Assert.*
import org.junit.Test

class ParsingTest {

    @Test
    fun `model answer is matched to requested styles by key, not position`() {
        val json = """{"variants":[
            {"style":"clean","title":"极简","body":"b1","hashtags":["#上海美食"," 面 "],"warnings":[]},
            {"style":"casual_story","title":"随手","body":"b2","hashtags":[]}
        ]}"""
        val variants = AiWriter.parseVariants(json, listOf(NoteStyle.CASUAL_STORY, NoteStyle.CLEAN))
        assertEquals(listOf("casual_story", "clean"), variants.map { it.styleLabel })
        assertEquals("随手", variants[0].title)
        assertEquals(listOf("上海美食", "面"), variants[1].hashtags)
    }

    @Test
    fun `missing fields never become nulls in non-null properties`() {
        // No warnings / hashtags keys at all — used to crash the review screen.
        val v = AiWriter.parseVariants("""{"variants":[{"style":"practical","title":"t","body":"b"}]}""",
            listOf(NoteStyle.PRACTICAL)).single()
        assertEquals(emptyList<String>(), v.warnings)
        assertEquals(emptyList<String>(), v.hashtags)
    }

    @Test
    fun `code fences are tolerated`() {
        val v = AiWriter.parseVariants("```json\n{\"variants\":[{\"style\":\"punchy\",\"title\":\"t\",\"body\":\"b\"}]}\n```",
            listOf(NoteStyle.PUNCHY))
        assertEquals(1, v.size)
    }

    @Test
    fun `legacy stored variants with display-name labels still load`() {
        val stored = """[{"styleLabel":"XHS Punchy","title":"t","body":"b","hashtags":["a"]}]"""
        assertEquals("punchy", JsonCodec.parseVariants(stored).single().styleLabel)
    }

    @Test
    fun `import tolerates sparse drafts`() {
        val data = JsonCodec.parseImport("""{"drafts":[{"status":"SHARED","foodInfo":{"dishNames":"面"}}, null]}""")
        val draft = data.drafts!!.filterNotNull().single().toDomain()
        assertEquals(NoteStatus.SHARED, draft.status)
        assertEquals("面", draft.foodInfo.dishNames)
        assertEquals("", draft.foodInfo.restaurantName)
        assertTrue(draft.variants.isEmpty())
    }

    @Test
    fun `hashtags are split, stripped and deduplicated`() {
        assertEquals(listOf("烤肉", "上海"), normalizeHashtags(listOf("#烤肉 #上海", "＃烤肉", " ")))
    }

    @Test
    fun `style lookup accepts keys, display names and enum names`() {
        assertEquals(NoteStyle.CLEAN, NoteStyle.fromLabel("Clean/Minimal"))
        assertEquals(NoteStyle.PRACTICAL, NoteStyle.fromLabel("practical"))
        assertEquals(NoteStyle.CASUAL_STORY, NoteStyle.fromLabel("CASUAL_STORY"))
        assertNull(NoteStyle.fromLabelOrNull("nonsense"))
    }
}
