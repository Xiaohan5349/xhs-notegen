"""Tests for POST /generate endpoint."""

import base64
from unittest.mock import patch, AsyncMock

from fastapi.testclient import TestClient
from main import app, NoteVariant

client = TestClient(app)

# A tiny 1x1 white JPEG base64 (valid image data)
DUMMY_JPEG_BASE64 = base64.b64encode(
    base64.b64decode(
        "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDAREAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/xAAUAQEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEQMRAD8AKwA="
    )
).decode()


def _valid_request(**overrides):
    """Build a valid food generation request."""
    data = {
        "note_type": "food",
        "style": "casual_story",
        "metadata": {
            "dish_names": "Hong Shao Rou, Tang Cu Li Ji",
            "restaurant_name": "Waipojia",
            "location": "Shanghai",
            "meal_date": "2024-03-15",
            "taste_notes": "Rich and tender",
            "price_or_rating": "80 RMB per person",
            "vibe_notes": "Great for family dinner",
            "personal_notes": "",
            "sponsored": False,
        },
        "images": [DUMMY_JPEG_BASE64] * 5,
    }
    data.update(overrides)
    return data


# ---------------------------------------------------------------------------
# Valid input tests
# ---------------------------------------------------------------------------

def test_generate_returns_200_with_mocked_gemini():
    """Full request with mocked Gemini returns 2-3 variants."""
    mock_variants = [
        NoteVariant(
            style_label="Casual Story",
            title="Test Title",
            body="Test body text.",
            hashtags=["#food", "#shanghai"],
            warnings=[],
        ),
        NoteVariant(
            style_label="Practical",
            title="Practical Rec",
            body="Why you should go.",
            hashtags=["#recommend"],
            warnings=[],
        ),
    ]

    with patch("main._call_gemini_with_parts", new_callable=AsyncMock) as mock_gemini:
        mock_gemini.side_effect = mock_variants
        response = client.post("/generate", json=_valid_request())

    assert response.status_code == 200
    data = response.json()
    assert len(data["variants"]) >= 2
    assert data["variants"][0]["style_label"] == "Casual Story"


# ---------------------------------------------------------------------------
# Validation error tests
# ---------------------------------------------------------------------------

def test_missing_required_fields_returns_422():
    """Missing dish_name should return 422."""
    req = _valid_request()
    req["metadata"]["dish_names"] = ""
    response = client.post("/generate", json=req)
    assert response.status_code == 422


def test_too_few_images_returns_422():
    """Less than 5 images should return 422."""
    req = _valid_request()
    req["images"] = [DUMMY_JPEG_BASE64] * 3
    response = client.post("/generate", json=req)
    assert response.status_code == 422


def test_too_many_images_returns_422():
    """More than 20 images should return 422."""
    req = _valid_request()
    req["images"] = [DUMMY_JPEG_BASE64] * 21
    response = client.post("/generate", json=req)
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# Optional fields tests
# ---------------------------------------------------------------------------

def test_missing_optional_fields_returns_200():
    """Request with only required fields should succeed."""
    req = _valid_request()
    req["metadata"] = {
        "dish_names": "Hotpot",
        "restaurant_name": "Haidilao",
        "sponsored": False,
    }
    req["images"] = [DUMMY_JPEG_BASE64] * 5

    mock_variants = [
        NoteVariant(style_label="Casual Story", title="T", body="B", hashtags=["#t"], warnings=[]),
        NoteVariant(style_label="Practical", title="T", body="B", hashtags=["#t"], warnings=[]),
    ]
    with patch("main._call_gemini_with_parts", new_callable=AsyncMock) as mock_gemini:
        mock_gemini.side_effect = mock_variants
        response = client.post("/generate", json=req)

    assert response.status_code == 200


# ---------------------------------------------------------------------------
# Image count boundaries
# ---------------------------------------------------------------------------

def test_image_count_boundary_min():
    """Exactly 5 images should be valid."""
    req = _valid_request()
    req["images"] = [DUMMY_JPEG_BASE64] * 5
    mock = NoteVariant(style_label="Casual Story", title="T", body="B", hashtags=["#t"], warnings=[])
    with patch("main._call_gemini_with_parts", new_callable=AsyncMock) as mock_gemini:
        mock_gemini.side_effect = [mock, mock]
        response = client.post("/generate", json=req)
    assert response.status_code == 200


def test_image_count_boundary_max():
    """Exactly 20 images should be valid."""
    req = _valid_request()
    req["images"] = [DUMMY_JPEG_BASE64] * 20
    mock = NoteVariant(style_label="Casual Story", title="T", body="B", hashtags=["#t"], warnings=[])
    with patch("main._call_gemini_with_parts", new_callable=AsyncMock) as mock_gemini:
        mock_gemini.side_effect = [mock, mock]
        response = client.post("/generate", json=req)
    assert response.status_code == 200


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

def test_health_returns_ok():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
