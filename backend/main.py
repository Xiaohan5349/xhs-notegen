"""Xiaohongshu Note Generator Backend — FastAPI + Gemini."""

import asyncio
import base64
import json
import logging
import os
from typing import Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from google.genai import types as genai_types
from pydantic import BaseModel, Field

from prompts.food import build_food_prompt, FOOD_STYLES, SYSTEM_PROMPT, StyleLabel

load_dotenv()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="XHS Note Generator", version="1.0.0")

# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------

class FoodMetadata(BaseModel):
    dish_name: str = Field(..., min_length=1, max_length=100)
    restaurant_name: str = Field(..., min_length=1, max_length=100)
    location: Optional[str] = Field(None, max_length=200)
    meal_date: Optional[str] = Field(None, max_length=50)
    taste_notes: Optional[str] = Field(None, max_length=500)
    price_or_rating: Optional[str] = Field(None, max_length=100)
    vibe_notes: Optional[str] = Field(None, max_length=500)
    personal_notes: Optional[str] = Field(None, max_length=1000)
    sponsored: bool = False


class GenerateRequest(BaseModel):
    note_type: str = Field(default="food", pattern=r"^food$")
    style: str = Field(default="casual_story")
    metadata: FoodMetadata
    images: list[str] = Field(..., min_length=5, max_length=20)


class NoteVariant(BaseModel):
    style_label: str
    title: str
    body: str
    hashtags: list[str]
    warnings: list[str] = []


class GenerateResponse(BaseModel):
    variants: list[NoteVariant]


# ---------------------------------------------------------------------------
# Gemini client
# ---------------------------------------------------------------------------

_gemini_client = None
GEMINI_MODEL = "gemini-2.5-flash"


def init_gemini(api_key: str | None = None):
    """Initialize the Gemini client. Call at startup or when key is available."""
    global _gemini_client
    from google import genai
    key = api_key or os.getenv("GEMINI_API_KEY", "")
    if not key:
        logger.warning("GEMINI_API_KEY not set — generation will fail")
        return
    _gemini_client = genai.Client(api_key=key)


async def _call_gemini_with_parts(
    system_prompt: str, parts: list, style: str
) -> NoteVariant | None:
    """Call Gemini with system prompt + multimodal parts, return structured variant."""
    if _gemini_client is None:
        init_gemini()
    if _gemini_client is None:
        raise RuntimeError("Gemini client not initialized — set GEMINI_API_KEY")

    loop = asyncio.get_running_loop()
    response = await loop.run_in_executor(
        None,
        lambda: _gemini_client.models.generate_content(
            model=GEMINI_MODEL,
            contents=[
                genai_types.Content(
                    role="user",
                    parts=parts,
                )
            ],
            config=genai_types.GenerateContentConfig(
                system_instruction=genai_types.Content(
                    role="user",
                    parts=[genai_types.Part.from_text(text=system_prompt)],
                ),
                temperature=0.8,
                top_p=0.95,
                max_output_tokens=2048,
                response_mime_type="application/json",
            ),
        ),
    )

    raw = response.text.strip()
    data = json.loads(raw)
    variants = data.get("variants", [])
    if not variants:
        raise ValueError("Gemini returned empty variants array")

    v = variants[0]
    return NoteVariant(
        style_label=v.get("styleLabel", style),
        title=v.get("title", ""),
        body=v.get("body", ""),
        hashtags=v.get("hashtags", []),
        warnings=v.get("warnings", []),
    )


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/")
async def root():
    """Friendly status page for browser access."""
    return {
        "app": "XHS Note Generator",
        "version": "1.0.0",
        "status": "running",
        "endpoints": {
            "health": "/health",
            "generate": "/generate  (POST — see /docs for schema)",
            "docs": "/docs  (Swagger UI)",
        },
        "tip": "Visit /docs to test the API interactively",
    }


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/generate", response_model=GenerateResponse)
async def generate(req: GenerateRequest):
    if _gemini_client is None:
        raise HTTPException(
            status_code=503,
            detail="Gemini API key not configured. See server console for instructions."
        )
    if req.style not in FOOD_STYLES:
        raise HTTPException(status_code=422, detail=f"Unknown style: {req.style}")

    # Validate base64 images before expensive Gemini calls
    for i, img_b64 in enumerate(req.images):
        try:
            base64.b64decode(img_b64, validate=True)
        except Exception:
            raise HTTPException(
                status_code=422,
                detail=f"Image {i} is not valid base64. When testing from /docs, "
                       f"use a real base64-encoded JPEG string, not placeholder text."
            )

    # Build the ordered style list: preferred first, then 2 others
    other_styles = [s for s in FOOD_STYLES if s != req.style]
    ordered_styles = [req.style] + other_styles[:2]

    # Parallel Gemini calls
    async def gen_one(style: StyleLabel) -> NoteVariant | None:
        try:
            prompt_text = build_food_prompt(style, req.metadata)
            parts = [genai_types.Part.from_text(text=prompt_text)]
            for img_b64 in req.images:
                parts.append(
                    genai_types.Part.from_bytes(
                        data=base64.b64decode(img_b64),
                        mime_type="image/jpeg",
                    )
                )
            return await _call_gemini_with_parts(SYSTEM_PROMPT, parts, style)
        except Exception as e:
            logger.warning(f"Gemini call failed for style {style}: {e}")
            return None

    results = await asyncio.gather(*(gen_one(s) for s in ordered_styles))
    variants = [v for v in results if v is not None]

    if not variants:
        raise HTTPException(status_code=502, detail="All Gemini calls failed")

    return GenerateResponse(variants=variants)


if __name__ == "__main__":
    init_gemini()
    key = os.getenv("GEMINI_API_KEY", "")
    if not key:
        print("=" * 60)
        print("WARNING: GEMINI_API_KEY not set!")
        print("1. Get a free key at https://aistudio.google.com/apikey")
        print('2. Create backend\\.env with: GEMINI_API_KEY=your-key')
        print("3. Restart the server")
        print("=" * 60)
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
