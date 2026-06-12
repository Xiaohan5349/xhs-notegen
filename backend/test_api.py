"""Quick test script for the XHS Note Generator backend.

Usage:
    python test_api.py              # test with dummy image (no real photo needed)
    python test_api.py photo.jpg    # test with a real food photo
"""

import base64
import json
import sys
import requests

BACKEND_URL = "http://localhost:8000"

# Tiny 1x1 white JPEG — valid image that Gemini can "see" (just a white pixel)
TINY_JPEG_B64 = (
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/"
    "2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDAREAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EAB"
    "QQAQAAAAAAAAAAAAAAAAAAAAD/xAAUAQEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEQMRAD8AKwA="
)


def get_image_base64(path: str | None = None) -> str:
    """Get a base64 JPEG string from a file or the tiny placeholder."""
    if path:
        with open(path, "rb") as f:
            return base64.b64encode(f.read()).decode()
    # Decode the base64-encoded tiny JPEG back to raw, then re-encode as clean b64 string
    raw = base64.b64decode(TINY_JPEG_B64)
    return base64.b64encode(raw).decode()


def test_health():
    print("=" * 50)
    print(" 1. Health Check")
    print("=" * 50)
    resp = requests.get(f"{BACKEND_URL}/health")
    print(f"  Status: {resp.status_code}")
    print(f"  Body:   {resp.json()}")
    print()


def test_generate(image_b64: str):
    print("=" * 50)
    print(" 2. Generate Food Note")
    print("=" * 50)

    payload = {
        "note_type": "food",
        "style": "casual_story",
        "metadata": {
            "dish_name": "红烧肉",
            "restaurant_name": "外婆家",
            "location": "上海",
            "meal_date": "2024-12-20",
            "taste_notes": "肥而不腻，入口即化，酱汁浓郁",
            "price_or_rating": "人均80元",
            "vibe_notes": "家庭聚餐好去处，装修有老上海风情",
        },
        "images": [image_b64] * 5,
    }

    print(f"  POST /generate (style={payload['style']})")
    print(f"  Dish: {payload['metadata']['dish_name']}")

    resp = requests.post(f"{BACKEND_URL}/generate", json=payload, timeout=180)

    print(f"  Status: {resp.status_code}")

    if resp.status_code == 200:
        data = resp.json()
        print(f"  Variants returned: {len(data['variants'])}")
        print()
        for i, v in enumerate(data["variants"]):
            print(f"  ── Variant {i+1}: {v['style_label']} ──")
            print(f"  Title:    {v['title']}")
            print(f"  Hashtags: {' '.join(v['hashtags'])}")
            if v.get("warnings"):
                print(f"  Warnings: {', '.join(v['warnings'])}")
            body_preview = v["body"][:200].replace("\n", " ")
            print(f"  Body:     {body_preview}...")
            print()
    elif resp.status_code == 503:
        print(f"  ERROR: Gemini API key not configured!")
        print(f"  Fix: Add GEMINI_API_KEY to backend\\.env")
    elif resp.status_code == 422:
        data = resp.json()
        print(f"  VALIDATION ERROR: {data.get('detail', data)}")
    else:
        print(f"  ERROR: {resp.json()}")


if __name__ == "__main__":
    image_path = sys.argv[1] if len(sys.argv) > 1 else None

    if image_path:
        print(f"  Using photo: {image_path}")
    else:
        print("  Using placeholder image (no photo provided)")
    print()

    test_health()
    test_generate(get_image_base64(image_path))
