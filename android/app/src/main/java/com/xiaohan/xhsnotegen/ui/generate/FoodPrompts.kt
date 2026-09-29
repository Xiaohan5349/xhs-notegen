package com.xiaohan.xhsnotegen.ui.generate

import com.xiaohan.xhsnotegen.domain.BuiltInModes
import com.xiaohan.xhsnotegen.domain.FieldSlot
import com.xiaohan.xhsnotegen.domain.FoodInfo
import com.xiaohan.xhsnotegen.domain.WritingMode
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.PromptLanguage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Prompts for the food diary.
 *
 * Why they look like this (the old prompt produced obviously-AI text):
 *  - Written in Chinese: English instructions for Chinese output pull the
 *    model toward translationese.
 *  - Describes what real notes look like instead of a wall of "DO NOT"s —
 *    long ban lists make the model stiff and still prime the banned phrases.
 *  - Names the concrete AI tells (summary endings, parallelism, food-critic
 *    vocabulary, "X年X月X日，我来到了…" openings) so they can be avoided.
 *  - Few-shot examples of the target voice, plus the user's own past notes
 *    when available — the strongest signal for "sounds like me".
 *  - The date is computed here (weekday + time of day) and placed as a
 *    diary-style header line, so it no longer forces a formulaic first sentence.
 */
object FoodPrompts {

    /** The editable part of the system prompt (Settings → Writing prompt). */
    val DEFAULT_SYSTEM_PROMPT = """
你在帮我写我自己的美食日记。写好后我会发到小红书，但它首先是我的日记：写给自己看，顺便给刷到的人看看。写出来要像我本人吃完饭拿手机随手打的字，不能让人一眼看出是AI写的。

## 真人随手记是什么样的
- 不完整。不会把每道菜都评价一遍，常常只记得一两个细节：汤有点咸、排了四十分钟、老板多给了一勺辣椒。
- 具体的小事比笼统的评价真实。"牛肉切得很厚，嚼了半天" 比 "牛肉口感扎实" 像人话。
- 句子长短不一，可以有半句、口语、语气词（挺、蛮、有点、还行、说实话、其实、就是）。
- 评价是个人的，可以含糊、可以矛盾："好吃是好吃，就是有点贵"、"说不上哪里好，但还会想来"。
- 结尾很随意，可以停在一个细节上，不用总结，不用升华。
- 信息少就写短，不要凑字数。

## 一看就是AI写的，不要这样
- 报流水账式开头："X年X月X日，我来到了……"、"今天和朋友来到了……"
- 总结和升华：总的来说、整体而言、总体来说、下次还会再来、值得一试、不虚此行、满满的幸福感
- 排比、对仗、"不仅……而且……"、"既……又……"、"首先……其次……最后"
- 美食评论腔：口感层次丰富、恰到好处、鲜嫩多汁、入口即化、外酥里嫩、回味无穷、唇齿留香、味蕾、烟火气、治愈
- 营销腔：绝了、yyds、天花板、宝藏、必打卡、冲、姐妹们、家人们、强烈推荐、闭眼入
- 每道菜一句评价、每段一样长的整齐感（清单风格除外）
- emoji、连着用感叹号

## 事实
只写我给的信息和照片里确实看得到的东西。没给价格就不提价格，没说排队就不写排队，不要编食材、做法、店的来历和服务。照片里看不清的不要猜。
标着"我的原话"的内容最有我自己的味道：尽量保留我的说法和用词，最多理顺一下，不要改成书面语。

## 标题
严格不超过20个字。像日记的小标题，平实具体，比如"周五的烤肉"、"楼下新开的米粉"、"又来吃这家的鳗鱼饭"。不要用"｜"拼接，不要标题党，不要感叹号。

## 话题标签
3到5个，不带#号。具体实用：店名、菜名、城市或商圈，比如"本帮菜"、"徐汇美食"。少用"美食分享"这种泛标签。

## warnings
如果我给的信息和照片对不上（比如写的是火锅，照片是寿司），或者有你拿不准的事实，在 warnings 里用一句中文说明。没有就返回空数组。

## 语气示例（只学语气和节奏，不要照抄内容）
示例一：
3.8 周六 中午
排了快四十分钟，差点走了。
招牌蟹黄面确实香，拌到后面有点腻，桌上的醋一定要加。
另外点了个狮子头，比想象中松，筷子一夹就散。
两个人一百五出头，在这个位置不算贵。

示例二：
11.2 周四 晚上
下班太累，在公司楼下随便吃了碗牛肉粉。
汤比上次淡，牛肉还是那么几片。
不过热乎乎的，吃完人活过来了。
""".trimIndent()

    /**
     * Output format, always appended and never user-editable: the app parses
     * this JSON, and DeepSeek's JSON mode requires the word "JSON" and an example.
     */
    val OUTPUT_RULES = """
## 输出
只返回JSON，不要任何其他文字。要求的每个风格各写一篇，style 字段填风格的 key。几篇之间要真的不一样：角度、长短、开头都换，不要只替换几个词。
格式：
{"variants":[{"style":"casual_story","title":"标题","body":"正文","hashtags":["标签"],"warnings":[]}]}
""".trimIndent()

    /** The same rules for English notes. */
    val OUTPUT_RULES_EN = """
## Output
Return only JSON, no other text. Write one note for each requested style and put the style's key in the style field. Make the notes genuinely different: change the angle, the length and the opening, not just a few words. Write the title, body, hashtags and warnings in English.
Format:
{"variants":[{"style":"casual_story","title":"Title","body":"Body","hashtags":["tag"],"warnings":[]}]}
""".trimIndent()

    fun outputRules(language: PromptLanguage): String =
        if (language == PromptLanguage.EN) OUTPUT_RULES_EN else OUTPUT_RULES

    /** Final system prompt: the user's (or default) instructions + the fixed output rules. */
    fun systemPrompt(instructions: String = DEFAULT_SYSTEM_PROMPT, language: PromptLanguage = PromptLanguage.ZH): String =
        instructions.trim() + "\n\n" + outputRules(language)

    /** Default per-style instructions, in Chinese to match the system prompt. */
    fun defaultStyleInstruction(style: NoteStyle): String = when (style) {
        NoteStyle.CASUAL_STORY ->
            "随手记：像给朋友发消息那样讲这顿饭——怎么来的、和谁、印象最深的一两件事。80到200字，分成几小段。"
        NoteStyle.PRACTICAL ->
            "实用记录：写给以后的自己查的。点了什么、各自怎么样（一两句大白话）、花了多少、会不会再来。可以分行，但每行都是完整的口语句子，不是标签。100到200字。"
        NoteStyle.PUNCHY ->
            "清单式：几行短句，每行开头一个小标签（比如 点了／味道／价格／环境／下次，只写有信息的项），标签后面是一句大白话，不堆形容词。最后可以加一句自己的碎碎念。"
        NoteStyle.CLEAN ->
            "极简：除日期行外只写两到四句话。吃了什么，加一个最具体的感受。"
    }

    fun buildUserPrompt(
        foodInfo: FoodInfo,
        styles: List<NoteStyle>,
        voiceSamples: List<String> = emptyList(),
        photoCount: Int = 0,
        /** The note has photos but the model can't see images (text-only model). */
        photosHidden: Boolean = false,
        styleInstruction: (NoteStyle) -> String = ::defaultStyleInstruction,
        rating: Int = 0,
        /** Labels for the facts; defaults to the Food mode's (菜 / 店 / 味道 …). */
        mode: WritingMode = BuiltInModes.food,
    ): String = if (mode.language == PromptLanguage.EN) {
        buildUserPromptEn(foodInfo, styles, voiceSamples, photoCount, photosHidden, styleInstruction, rating, mode)
    } else buildString {
        fun key(slot: FieldSlot) = mode.field(slot).promptKey
        if (voiceSamples.isNotEmpty()) {
            appendLine("## 我以前写的几篇（学我的语气和用词习惯，不要抄内容）")
            voiceSamples.forEach { sample ->
                appendLine("---")
                appendLine(sample.take(400).trim())
            }
            appendLine("---")
            appendLine()
        }

        appendLine("## ${mode.promptHeading}")
        appendLine("${key(FieldSlot.SUBJECT)}：${foodInfo.dishNames.trim()}")
        appendLine("${key(FieldSlot.PLACE)}：${foodInfo.restaurantName.trim()}")
        if (foodInfo.location.isNotBlank()) appendLine("地点：${foodInfo.location.trim()}")
        val date = describeMealDate(foodInfo.mealDate)
        if (date != null) appendLine("时间：${date.spoken}")
        if (foodInfo.tasteNotes.isNotBlank()) appendLine("${key(FieldSlot.FEELING)}（我的原话）：${foodInfo.tasteNotes.trim()}")
        if (foodInfo.priceOrRating.isNotBlank()) appendLine("${key(FieldSlot.COST)}（我的原话）：${foodInfo.priceOrRating.trim()}")
        if (rating in 1..5) appendLine("我的打分：$rating/5（语气要和分数一致；正文里不要写出星级或分数）")
        if (foodInfo.vibeNotes.isNotBlank()) appendLine("${key(FieldSlot.SCENE)}（我的原话）：${foodInfo.vibeNotes.trim()}")
        if (foodInfo.personalNotes.isNotBlank()) appendLine("${key(FieldSlot.OTHER)}（我的原话）：${foodInfo.personalNotes.trim()}")
        if (photoCount > 0) appendLine("照片：$photoCount 张，附在后面。")
        if (photosHidden) appendLine("这次看不到照片，只根据上面的文字写，不要描述或猜测照片内容。")
        appendLine()

        appendLine("## 要写的风格（每个写一篇）")
        styles.forEach { appendLine("- ${it.key}：${styleInstruction(it)}") }
        appendLine()

        if (date != null) {
            appendLine("每篇正文第一行固定写「${date.headerLine}」，单独一行。后面不要再重复完整日期。")
        } else {
            appendLine("没有日期信息，正文不要写日期。")
        }
        if (foodInfo.location.isNotBlank()) appendLine("地点在自然的时候提一下就行，不用放在开头。")
    }

    /** The same facts for a mode whose notes are written in English. */
    private fun buildUserPromptEn(
        foodInfo: FoodInfo, styles: List<NoteStyle>, voiceSamples: List<String>, photoCount: Int,
        photosHidden: Boolean, styleInstruction: (NoteStyle) -> String, rating: Int, mode: WritingMode,
    ): String = buildString {
        fun key(slot: FieldSlot) = mode.field(slot).promptKey
        if (voiceSamples.isNotEmpty()) {
            appendLine("## A few notes I wrote before (learn my tone and word choices, don't copy the content)")
            voiceSamples.forEach { sample ->
                appendLine("---")
                appendLine(sample.take(600).trim())
            }
            appendLine("---")
            appendLine()
        }

        appendLine("## ${mode.promptHeading}")
        appendLine("${key(FieldSlot.SUBJECT)}: ${foodInfo.dishNames.trim()}")
        appendLine("${key(FieldSlot.PLACE)}: ${foodInfo.restaurantName.trim()}")
        if (foodInfo.location.isNotBlank()) appendLine("Area: ${foodInfo.location.trim()}")
        val date = describeMealDate(foodInfo.mealDate, language = PromptLanguage.EN)
        if (date != null) appendLine("When: ${date.spoken}")
        if (foodInfo.tasteNotes.isNotBlank()) appendLine("${key(FieldSlot.FEELING)} (my words): ${foodInfo.tasteNotes.trim()}")
        if (foodInfo.priceOrRating.isNotBlank()) appendLine("${key(FieldSlot.COST)} (my words): ${foodInfo.priceOrRating.trim()}")
        if (rating in 1..5) appendLine("My rating: $rating/5 (match the tone to it; don't write stars or a score in the note)")
        if (foodInfo.vibeNotes.isNotBlank()) appendLine("${key(FieldSlot.SCENE)} (my words): ${foodInfo.vibeNotes.trim()}")
        if (foodInfo.personalNotes.isNotBlank()) appendLine("${key(FieldSlot.OTHER)} (my words): ${foodInfo.personalNotes.trim()}")
        if (photoCount > 0) appendLine("Photos: $photoCount, attached below.")
        if (photosHidden) appendLine("You can't see the photos this time. Write only from the text above; don't describe or guess what's in the photos.")
        appendLine()

        appendLine("## Styles to write (one note each)")
        styles.forEach { appendLine("- ${it.key}: ${styleInstruction(it)}") }
        appendLine()

        if (date != null) {
            appendLine("Start every body with the line \"${date.headerLine}\" on its own. Don't repeat the full date after it.")
        } else {
            appendLine("There's no date, so don't write one in the body.")
        }
        if (foodInfo.location.isNotBlank()) appendLine("Mention the area only where it comes up naturally, not at the start.")
        appendLine("Write everything in English. Keep names of places, dishes and products as I wrote them.")
    }

    data class MealDate(val headerLine: String, val spoken: String)

    /**
     * "2026-03-15 19:20" → header "3.15 周日 晚上", spoken "2026年3月15日 周日 晚上".
     * Computed here so the model never does calendar arithmetic. Unparseable
     * input is passed through as-is.
     */
    fun describeMealDate(
        raw: String,
        now: Calendar = Calendar.getInstance(),
        language: PromptLanguage = PromptLanguage.ZH,
    ): MealDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val cal = parse(text) ?: return MealDate(text, text)
        val hasTime = text.contains(':')
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val weekday = WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val part = if (hasTime) " " + timeOfDay(cal.get(Calendar.HOUR_OF_DAY)) else ""

        if (language == PromptLanguage.EN) {
            // "Sat, Mar 8 · lunch" / "Saturday, March 8, 2026, lunch"
            val partEn = if (hasTime) timeOfDayEn(cal.get(Calendar.HOUR_OF_DAY)) else null
            val dayEn = SimpleDateFormat("EEE, MMM d", Locale.US).format(cal.time)
            val yearEn = if (year != now.get(Calendar.YEAR)) ", $year" else ""
            return MealDate(
                headerLine = "$dayEn$yearEn" + (partEn?.let { " · $it" } ?: ""),
                spoken = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(cal.time) + (partEn?.let { ", $it" } ?: ""),
            )
        }

        val yearPrefix = if (year != now.get(Calendar.YEAR)) "$year." else ""
        return MealDate(
            headerLine = "$yearPrefix$month.$day $weekday$part",
            spoken = "${year}年${month}月${day}日 $weekday$part",
        )
    }

    private val WEEKDAYS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    private fun timeOfDay(hour: Int): String = when (hour) {
        in 5..9 -> "早上"
        in 10..13 -> "中午"
        in 14..16 -> "下午"
        in 17..20 -> "晚上"
        else -> "夜宵"
    }

    private fun timeOfDayEn(hour: Int): String = when (hour) {
        in 5..9 -> "morning"
        in 10..13 -> "lunch"
        in 14..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "late night"
    }

    private fun parse(text: String): Calendar? {
        for (pattern in listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd")) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val date = fmt.parse(text) ?: continue
                return Calendar.getInstance().apply { time = date }
            } catch (_: Exception) { }
        }
        return null
    }
}
