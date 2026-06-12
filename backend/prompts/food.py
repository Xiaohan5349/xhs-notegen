"""Food note prompt templates and style definitions."""

from typing import Literal

StyleLabel = Literal["casual_story", "practical", "punchy", "clean"]

FOOD_STYLES: list[StyleLabel] = ["casual_story", "practical", "punchy", "clean"]

STYLE_DEFINITIONS: dict[StyleLabel, str] = {
    "casual_story": (
        "Casual: A short personal diary entry. Just record what you ate, "
        "where, when, and simple impressions. Like jotting down notes for yourself."
    ),
    "practical": (
        "Practical: A factual summary. What dishes you ordered, "
        "basic taste notes, price, whether you'd go again. "
        "Straightforward, no embellishment."
    ),
    "punchy": (
        "Punchy: Short, structured format with clear labels for each section "
        "(e.g. 菜品 / 口味 / 环境 / 价格). "
        "Clean and scannable for your own future reference."
    ),
    "clean": (
        "Minimal: Bare essentials. Dish names, brief factual description. "
        "No adjectives unless they describe a specific quality (e.g. 偏咸, 分量大)."
    ),
}

SYSTEM_PROMPT = """CRITICAL: This is a PRIVATE food diary. The user is writing notes for THEMSELVES only — to remember meals they've had. There is NO audience. This is NOT content for followers, NOT a recommendation post, NOT a review for others.

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

Generate exactly 1 variant in the specified style."""


def build_food_prompt(style: StyleLabel, metadata) -> str:
    """Build the user prompt for a food note with given style and metadata."""
    parts = [
        f"Dish: {metadata.dish_name}",
        f"Restaurant: {metadata.restaurant_name}",
    ]
    if metadata.location:
        parts.append(f"Location: {metadata.location}")
    if metadata.meal_date:
        parts.append(f"Date: {metadata.meal_date}")
    if metadata.taste_notes:
        parts.append(f"Taste notes: {metadata.taste_notes}")
    if metadata.price_or_rating:
        parts.append(f"Price/rating: {metadata.price_or_rating}")
    if metadata.vibe_notes:
        parts.append(f"Atmosphere: {metadata.vibe_notes}")
    if metadata.personal_notes:
        parts.append(f"Personal notes: {metadata.personal_notes}")

    context = "\n".join(parts)

    return f"""Style: {STYLE_DEFINITIONS[style]}

Context:
{context}

Write a plain personal food diary entry based on the context above.
This is for private record-keeping only.
No emoji, no hype, no addressing readers.
Just state what you ate, where, when, and what it was like.
Write in Simplified Chinese."""
