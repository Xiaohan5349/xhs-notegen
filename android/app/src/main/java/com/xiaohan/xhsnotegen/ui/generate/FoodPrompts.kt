package com.xiaohan.xhsnotegen.ui.generate

import com.xiaohan.xhsnotegen.domain.FoodInfo
import com.xiaohan.xhsnotegen.domain.NoteStyle

object FoodPrompts {

    val SYSTEM_PROMPT = """
CRITICAL: This is a PRIVATE food diary. The user is writing notes for THEMSELVES only — to remember meals they've had. There is NO audience. This is NOT content for followers, NOT a recommendation post, NOT a review for others.

TONE RULES (violating any of these is an error):

DO NOT use these words or their variants:
绝了, 无敌, 神仙, 天花板, 必打卡, 冲鸭, 安排, 宝藏, 隐藏, 惊艳, 一绝
一口入魂, 名不虚传, 果然名不虚传, 恨不得, 舔盘子

DO NOT address readers: 大家, 你们, 姐妹们, 宝子们, 吃货们
DO NOT give recommendations or calls to action: 快去, 一定要来, 推荐, 值得去
DO NOT use emoji (🚀, 🤤, 😍, ✨, 💡, 🍚, etc.)
DO NOT exaggerate or use emotional language
DO NOT try to be entertaining
DO NOT use promotional phrases like 性价比超高, 强烈推荐

WHAT TO DO:
- Write like jotting a note in a personal notebook
- State facts plainly: dish name, taste, price paid, date, location
- Keep sentences short and direct
- Write in Simplified Chinese

CONTENT RULES:
Use only details the user provided + what is visible in photos.
Do NOT invent prices, ingredients, awards, hours, queue times, popularity, or service details.

Return valid JSON only. No markdown. No explanation.

JSON schema:
{
  "variants": [
    {
      "styleLabel": "Casual Story | Practical | XHS Punchy | Clean/Minimal",
      "title": "string",
      "body": "string",
      "hashtags": ["string"],
      "warnings": ["string"]
    }
  ]
}

Generate exactly 1 variant in the specified style.
""".trimIndent()

    private val STYLE_DEFINITIONS = mapOf(
        "casual_story" to (
            "Casual: A short personal diary entry. Just record what you ate, " +
            "where, when, and simple impressions. Like jotting down notes for yourself."
        ),
        "practical" to (
            "Practical: A factual summary. What dishes you ordered, " +
            "basic taste notes, price, whether you'd go again. " +
            "Straightforward, no embellishment."
        ),
        "punchy" to (
            "Punchy: Short, structured format with clear labels for each section " +
            "(e.g. 菜品 / 口味 / 环境 / 价格). " +
            "Clean and scannable for your own future reference."
        ),
        "clean" to (
            "Minimal: Bare essentials. Dish names, brief factual description. " +
            "No adjectives unless they describe a specific quality (e.g. 偏咸, 分量大)."
        ),
    )

    fun buildUserPrompt(style: NoteStyle, foodInfo: FoodInfo): String {
        val parts = mutableListOf<String>()
        parts.add("Dishes: ${foodInfo.dishNames}")
        parts.add("Restaurant: ${foodInfo.restaurantName}")
        if (foodInfo.location.isNotBlank()) parts.add("Location: ${foodInfo.location}")
        if (foodInfo.mealDate.isNotBlank()) parts.add("Date: ${foodInfo.mealDate}")
        if (foodInfo.tasteNotes.isNotBlank()) parts.add("Taste notes: ${foodInfo.tasteNotes}")
        if (foodInfo.priceOrRating.isNotBlank()) parts.add("Price/rating: ${foodInfo.priceOrRating}")
        if (foodInfo.vibeNotes.isNotBlank()) parts.add("Atmosphere: ${foodInfo.vibeNotes}")
        if (foodInfo.personalNotes.isNotBlank()) parts.add("Personal notes: ${foodInfo.personalNotes}")

        val context = parts.joinToString("\n")
        val styleDef = STYLE_DEFINITIONS[style.key] ?: STYLE_DEFINITIONS["casual_story"]!!

        return """
Style: $styleDef

Context:
$context

Write a plain personal food diary entry based on the context above.
This is for private record-keeping only.
No emoji, no hype, no addressing readers.
Just state what you ate, where, when, and what it was like.

REQUIRED: The first sentence MUST include the date from the context above (e.g. "3月15日晚上..." or "2024年3月15日..."). Do NOT omit the date.
If location is provided, include it near the date (e.g. "在上海外滩...").
Write in Simplified Chinese.
""".trimIndent()
    }
}
