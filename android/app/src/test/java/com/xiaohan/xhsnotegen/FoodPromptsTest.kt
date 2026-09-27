package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.domain.FoodInfo
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.ui.generate.FoodPrompts
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class FoodPromptsTest {

    private val in2026 = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 27) }

    @Test
    fun `meal date becomes a diary header with weekday and time of day`() {
        val d = FoodPrompts.describeMealDate("2026-03-15 19:20", in2026)!!
        assertEquals("3.15 周日 晚上", d.headerLine)
        assertEquals("2026年3月15日 周日 晚上", d.spoken)
    }

    @Test
    fun `date-only input has no time of day, other years are spelled out`() {
        val d = FoodPrompts.describeMealDate("2025-11-02", in2026)!!
        assertEquals("2025.11.2 周日", d.headerLine)
    }

    @Test
    fun `late night counts as 夜宵 and garbage passes through`() {
        assertTrue(FoodPrompts.describeMealDate("2026-03-15 23:40", in2026)!!.headerLine.endsWith("夜宵"))
        assertEquals("上周末", FoodPrompts.describeMealDate("上周末", in2026)!!.headerLine)
        assertNull(FoodPrompts.describeMealDate("  ", in2026))
    }

    @Test
    fun `user prompt carries own words, styles and voice samples`() {
        val prompt = FoodPrompts.buildUserPrompt(
            FoodInfo(dishNames = "牛肉粉", restaurantName = "楼下粉店", tasteNotes = "汤有点淡", mealDate = "2026-03-15 19:20"),
            styles = listOf(NoteStyle.PRACTICAL, NoteStyle.CLEAN),
            voiceSamples = listOf("周五的烤肉\n排了很久，还行"),
            photoCount = 2,
        )
        assertTrue(prompt.contains("味道（我的原话）：汤有点淡"))
        assertTrue(prompt.contains("- practical："))
        assertTrue(prompt.contains("- clean："))
        assertFalse(prompt.contains("- punchy："))
        assertTrue(prompt.contains("排了很久，还行"))
        assertTrue(prompt.contains("「3.15 周日 晚上」"))
    }

    @Test
    fun `custom instructions never lose the fixed output rules`() {
        val sys = FoodPrompts.systemPrompt("只写一句话。")
        assertTrue(sys.startsWith("只写一句话。"))
        assertTrue(sys.contains("\"variants\""))
        assertTrue(sys.contains("JSON"))
        assertFalse(FoodPrompts.DEFAULT_SYSTEM_PROMPT.contains("## 输出"))
    }

    @Test
    fun `custom style instructions reach the user prompt`() {
        val prompt = FoodPrompts.buildUserPrompt(
            FoodInfo(dishNames = "面", restaurantName = "店"),
            styles = listOf(NoteStyle.CLEAN),
            styleInstruction = { "只写三句" },
        )
        assertTrue(prompt.contains("- clean：只写三句"))
    }
}
