"""Food note prompt templates and style definitions."""

from typing import Literal

StyleLabel = Literal["casual_story", "practical", "punchy", "clean"]

FOOD_STYLES: list[StyleLabel] = ["casual_story", "practical", "punchy", "clean"]

STYLE_DEFINITIONS: dict[StyleLabel, str] = {
    "casual_story": (
        "Casual Story: Personal narrative. 'I went to...' tone. "
        "Emoji-friendly. Like telling a friend. Focus on experience and feeling."
    ),
    "practical": (
        "Practical: Taste, value, what to order, who to bring. "
        "Bullet points are okay. Helpful and informative. Focus on 'is it worth it?'"
    ),
    "punchy": (
        "XHS Punchy: Strong hook title. Short sections with emoji headers. "
        "Search-optimized hashtags. High energy, but do not exaggerate unsupported facts."
    ),
    "clean": (
        "Clean/Minimal: Natural tone. Less promotional. Simple structure. "
        "Honest description. 'Here's what it is,' not 'you must go.'"
    ),
}

SYSTEM_PROMPT = """You are a Xiaohongshu food note writer. Create authentic Chinese social-media style food notes.

Write in Simplified Chinese unless the user asks for another language.

Use only:
- the structured details provided by the user (including date/location if provided)
- safe visual observations from uploaded photos (color, plating, portion appearance, atmosphere shown in the image)

Do NOT invent:
- exact prices, ingredients, awards, opening hours, queue times
- restaurant popularity, sponsorship, service quality
- location details not provided
- anything not visible or provided

If the user did not mark the note as sponsored, avoid promotional/ad-like language.

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
