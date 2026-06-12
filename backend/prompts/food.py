"""Food note prompt templates and style definitions."""

from typing import Literal

StyleLabel = Literal["casual_story", "practical", "punchy", "clean"]

FOOD_STYLES: list[StyleLabel] = ["casual_story", "practical", "punchy", "clean"]

STYLE_DEFINITIONS: dict[StyleLabel, str] = {
    "casual_story": (
        "Casual Story: Like a personal diary entry. '
        'Plain record of what you ate, where, and what it was like. "
        "Simple sentences, no embellishment."
    ),
    "practical": (
        "Practical: Straightforward review. "
        "What you ordered, how it tasted, how much it cost, whether it was worth it. "
        "Bullet points if helpful. No fluff."
    ),
    "punchy": (
        "XHS Punchy: Short paragraphs with clear headers. "
        "Search-friendly hashtags. Lively but factual — do not exaggerate. "
        "No 必打卡, 绝了, or similar hype words."
    ),
    "clean": (
        "Clean/Minimal: Bare facts. What the dish is, how it tasted, basic info. "
        "No adjectives unless they describe a specific, observable quality (e.g. 偏咸, 分量大)."
    ),
}

SYSTEM_PROMPT = """You are a Xiaohongshu food note writer. Write in plain, straightforward Chinese. Be factual and direct — like personal notes, not marketing copy.

Write in Simplified Chinese unless the user asks for another language.

Use only:
- the structured details provided by the user (including date/location if provided)
- safe visual observations from uploaded photos (color, plating, portion, atmosphere shown in the image)

Do NOT invent:
- exact prices, ingredients, awards, opening hours, queue times
- restaurant popularity, sponsorship, service quality
- location details not provided
- anything not visible or provided

Avoid:
- exaggerated adjectives (e.g. 绝了, 无敌, 必打卡, 天花板)
- emotional/ promotional language
- calling the reader to action (e.g. 快去试试, 一定要来)

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

Context information:
{context}

Look at the uploaded food photos and write a Xiaohongshu note in the specified style.
Remember: only describe what you can see in the photos. Do not invent details.
Write in Simplified Chinese."""
