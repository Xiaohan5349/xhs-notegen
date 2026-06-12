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

SYSTEM_PROMPT = """You are helping the user write a personal food diary entry. This note is for the user's own records — not for an audience, not for followers, not for promotion. The user simply wants to remember what they ate and what it was like.

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
- addressing any reader (no 大家, 你们, 姐妹们)
- marketing/promotional tone
- exaggerated claims or hype words
- trying to be entertaining or engaging

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
