# Android Xiaohongshu Food Note App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a personal Android app (Kotlin/Jetpack Compose) + Python/FastAPI backend with Gemini AI to generate Xiaohongshu food notes from 5-20 photos and restaurant/dish info, with review/editing and Android share handoff.

**Architecture:** Monorepo with `android/` (Kotlin/Compose/Room/Retrofit) and `backend/` (Python/FastAPI/Gemini). Android app uses MVVM with Repository pattern. Backend is a single `POST /generate` endpoint that calls Gemini 3× in parallel for 2-3 style variants.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Retrofit, Coil, Navigation Compose, Material 3, Coroutines/Flow, Python 3.11+, FastAPI, Google Generative AI SDK, Pydantic

---

## File Structure

```
android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/xiaohan/xhsnotegen/
            ├── XhsNoteGenApp.kt
            ├── MainActivity.kt
            ├── data/
            │   ├── local/
            │   │   ├── AppDatabase.kt
            │   │   ├── dao/{NoteDraftDao.kt, FoodInfoDao.kt, StylePreferenceDao.kt}
            │   │   └── entity/{NoteDraftEntity.kt, FoodInfoEntity.kt, StylePreferenceEntity.kt}
            │   ├── remote/
            │   │   ├── AiGenerationClient.kt
            │   │   └── dto/{GenerateRequest.kt, GenerateResponse.kt}
            │   └── repository/{DraftRepository.kt, StylePreferencesRepository.kt}
            ├── domain/
            │   ├── NoteDraft.kt, FoodInfo.kt, NoteVariant.kt
            │   ├── NoteType.kt, NoteStatus.kt, NoteStyle.kt
            ├── ui/
            │   ├── navigation/AppNavigation.kt
            │   ├── theme/{Theme.kt, Color.kt, Type.kt}
            │   ├── drafts/{DraftListScreen.kt, DraftListViewModel.kt}
            │   ├── create/{CreateFormScreen.kt, CreateFormViewModel.kt}
            │   ├── generate/{GeneratingScreen.kt, GenerationViewModel.kt}
            │   ├── review/{ReviewScreen.kt, ReviewViewModel.kt}
            │   └── publish/{PublishViewModel.kt, XiaohongshuSharePublisher.kt}
            └── util/{ImageCompressor.kt, ExifReader.kt}

backend/
├── requirements.txt
├── main.py
├── prompts/food.py
└── tests/test_generate.py
```

---

## Phase 0: Project Scaffolding

### Task 0.1: Root Gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Update .gitignore**

Current `.gitignore` only has `.superpowers/`. Replace with:

```gitignore
.superpowers/

# Android
android/.gradle/
android/build/
android/app/build/
android/app/release/
android/local.properties
android/*.iml
android/.idea/
android/app/*.iml

# Backend
backend/__pycache__/
backend/*.pyc
backend/.venv/
backend/.env
backend/*.egg-info/

# OS
.DS_Store
Thumbs.db
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore && git commit -m "chore: update .gitignore for Android + backend project"
```

### Task 0.2: Android Gradle Scaffold

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `android/settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "XhsNoteGen"
include(":app")
```

- [ ] **Step 2: Create `android/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Create `android/gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create `android/gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
composeBom = "2024.12.01"
room = "2.6.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
coil = "2.7.0"
navigationCompose = "2.8.5"
lifecycle = "2.8.7"
coroutines = "1.9.0"
gson = "2.11.0"
activityCompose = "1.9.3"
coreKtx = "1.15.0"
ksp = "2.1.0-1.0.29"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 5: Create `android/app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xiaohan.xhsnotegen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaohan.xhsnotegen"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(libs.gson)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 6: Create `android/app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".XhsNoteGenApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.XhsNoteGen"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.XhsNoteGen">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 7: Commit**

```bash
git add android/ && git commit -m "chore: scaffold Android project with Gradle, Compose, Room, Retrofit deps"
```

### Task 0.3: Backend Scaffold

**Files:**
- Create: `backend/requirements.txt`
- Create: `backend/main.py`
- Create: `backend/prompts/__init__.py`
- Create: `backend/prompts/food.py`

- [ ] **Step 1: Create `backend/requirements.txt`**

```
fastapi==0.115.6
uvicorn[standard]==0.34.0
google-genai==1.3.0
pydantic==2.10.3
python-multipart==0.0.18
```

- [ ] **Step 2: Create `backend/main.py`**

```python
"""Xiaohongshu Note Generator Backend — FastAPI + Gemini."""

import asyncio
import json
import logging
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from prompts.food import build_food_prompt, FOOD_STYLES, StyleLabel

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
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/generate", response_model=GenerateResponse)
async def generate(req: GenerateRequest):
    if req.style not in FOOD_STYLES:
        raise HTTPException(status_code=422, detail=f"Unknown style: {req.style}")

    # Build the ordered style list: preferred first, then 2 others
    other_styles = [s for s in FOOD_STYLES if s != req.style]
    ordered_styles = [req.style] + other_styles[:2]  # 3 styles max

    # Parallel Gemini calls
    async def gen_one(style: StyleLabel) -> Optional[NoteVariant]:
        try:
            prompt = build_food_prompt(style, req.metadata)
            # TODO: Gemini call will be wired in Task 0.3 Step 5
            return await _call_gemini(prompt, style)
        except Exception as e:
            logger.warning(f"Gemini call failed for style {style}: {e}")
            return None

    results = await asyncio.gather(*(gen_one(s) for s in ordered_styles))
    variants = [v for v in results if v is not None]

    if not variants:
        raise HTTPException(status_code=502, detail="All Gemini calls failed")

    return GenerateResponse(variants=variants)


# ---------------------------------------------------------------------------
# Gemini client (placeholder — wired with real key in Task 0.3 Step 5)
# ---------------------------------------------------------------------------

_gemini_client = None


def init_gemini(api_key: str):
    """Initialize the Gemini client. Call at startup."""
    global _gemini_client
    from google import genai
    _gemini_client = genai.Client(api_key=api_key)


async def _call_gemini(prompt: str, style: str) -> NoteVariant:
    """Call Gemini and parse the structured response."""
    # Stub — real implementation in Task 0.3 Step 5
    return NoteVariant(
        style_label=style,
        title="Placeholder title",
        body="Placeholder body text.",
        hashtags=["#placeholder"],
        warnings=["Backend not connected to Gemini yet"],
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

- [ ] **Step 3: Create `backend/prompts/__init__.py`**

```python
"""Prompt templates for note generation."""
```

- [ ] **Step 4: Create `backend/prompts/food.py`**

```python
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
```

- [ ] **Step 5: Commit**

```bash
git add backend/ && git commit -m "chore: scaffold FastAPI backend with food prompt templates"
```

---

## Phase 1: Android Data Layer

### Task 1.1: Domain Models

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/domain/NoteType.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/domain/NoteStatus.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/domain/NoteStyle.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/domain/FoodInfo.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/domain/NoteVariant.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/domain/NoteDraft.kt`

- [ ] **Step 1: Create `NoteType.kt`**

```kotlin
package com.xiaohan.xhsnotegen.domain

/** Open-ended note type. v1 only uses FOOD; others are designed for future expansion. */
enum class NoteType(val key: String) {
    FOOD("food");

    companion object {
        fun fromKey(key: String): NoteType =
            entries.firstOrNull { it.key == key } ?: FOOD
    }
}
```

- [ ] **Step 2: Create `NoteStatus.kt`**

```kotlin
package com.xiaohan.xhsnotegen.domain

enum class NoteStatus(val key: String) {
    DRAFT("draft"),
    GENERATED("generated"),
    REVIEWED("reviewed"),
    SHARED("shared");

    companion object {
        fun fromKey(key: String): NoteStatus =
            entries.firstOrNull { it.key == key } ?: DRAFT
    }
}
```

- [ ] **Step 3: Create `NoteStyle.kt`**

```kotlin
package com.xiaohan.xhsnotegen.domain

/** Food note styles. Key values match backend StyleLabel. */
enum class NoteStyle(val key: String, val displayName: String) {
    CASUAL_STORY("casual_story", "Casual Story"),
    PRACTICAL("practical", "Practical"),
    PUNCHY("punchy", "XHS Punchy"),
    CLEAN("clean", "Clean/Minimal");

    companion object {
        val DEFAULT = CASUAL_STORY

        fun fromKey(key: String): NoteStyle =
            entries.firstOrNull { it.key == key } ?: DEFAULT

        /** Resolve preferred style: per-type → global → hardcoded fallback. */
        fun resolve(
            typePreference: NoteStyle?,
            globalPreference: NoteStyle?,
        ): NoteStyle = typePreference ?: globalPreference ?: DEFAULT
    }
}
```

- [ ] **Step 4: Create `FoodInfo.kt`**

```kotlin
package com.xiaohan.xhsnotegen.domain

data class FoodInfo(
    val dishName: String = "",
    val restaurantName: String = "",
    val location: String = "",
    val mealDate: String = "",
    val tasteNotes: String = "",
    val priceOrRating: String = "",
    val vibeNotes: String = "",
    val personalNotes: String = "",
    val sponsored: Boolean = false,
) {
    fun isValid(): Boolean = dishName.isNotBlank() && restaurantName.isNotBlank()
}
```

- [ ] **Step 5: Create `NoteVariant.kt`**

```kotlin
package com.xiaohan.xhsnotegen.domain

data class NoteVariant(
    val styleLabel: String,
    val title: String,
    val body: String,
    val hashtags: List<String>,
    val warnings: List<String> = emptyList(),
)
```

- [ ] **Step 6: Create `NoteDraft.kt`**

```kotlin
package com.xiaohan.xhsnotegen.domain

data class NoteDraft(
    val id: Long = 0,
    val type: NoteType = NoteType.FOOD,
    val status: NoteStatus = NoteStatus.DRAFT,
    val photoUris: List<String> = emptyList(),
    val selectedPublishPhotoUris: List<String> = emptyList(),
    val title: String = "",
    val body: String = "",
    val hashtags: List<String> = emptyList(),
    val variants: List<NoteVariant> = emptyList(),
    val selectedVariantIndex: Int = 0,
    val styleLabel: String = NoteStyle.DEFAULT.key,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val foodInfo: FoodInfo = FoodInfo(),
)
```

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/domain/ && git commit -m "feat: add domain models — NoteDraft, FoodInfo, NoteVariant, enums"
```

### Task 1.2: Room Entities

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/entity/NoteDraftEntity.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/entity/FoodInfoEntity.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/entity/StylePreferenceEntity.kt`

- [ ] **Step 1: Create `NoteDraftEntity.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xiaohan.xhsnotegen.domain.*

@Entity(tableName = "note_drafts")
data class NoteDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: String = NoteType.FOOD.key,
    @ColumnInfo(name = "status") val status: String = NoteStatus.DRAFT.key,
    @ColumnInfo(name = "photo_uris") val photoUris: String = "[]",
    @ColumnInfo(name = "selected_publish_photo_uris") val selectedPublishPhotoUris: String = "[]",
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "body") val body: String? = null,
    @ColumnInfo(name = "hashtags") val hashtags: String? = null,
    @ColumnInfo(name = "variants_json") val variantsJson: String? = null,
    @ColumnInfo(name = "selected_variant_index") val selectedVariantIndex: Int = 0,
    @ColumnInfo(name = "style_label") val styleLabel: String = NoteStyle.DEFAULT.key,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: Create `FoodInfoEntity.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_info",
    foreignKeys = [
        ForeignKey(
            entity = NoteDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draft_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class FoodInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "draft_id") val draftId: Long,
    @ColumnInfo(name = "dish_name") val dishName: String,
    @ColumnInfo(name = "restaurant_name") val restaurantName: String,
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "meal_date") val mealDate: String? = null,
    @ColumnInfo(name = "taste_notes") val tasteNotes: String? = null,
    @ColumnInfo(name = "price_or_rating") val priceOrRating: String? = null,
    @ColumnInfo(name = "vibe_notes") val vibeNotes: String? = null,
    @ColumnInfo(name = "personal_notes") val personalNotes: String? = null,
    @ColumnInfo(name = "sponsored") val sponsored: Boolean = false,
)
```

- [ ] **Step 3: Create `StylePreferenceEntity.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "style_preferences")
data class StylePreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_type") val noteType: String,  // "all" = global, "food" = food-specific
    @ColumnInfo(name = "preferred_style") val preferredStyle: String,
)
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/entity/ && git commit -m "feat: add Room entities — NoteDraft, FoodInfo, StylePreference"
```

### Task 1.3: Room DAOs and Database

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/dao/NoteDraftDao.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/dao/FoodInfoDao.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/dao/StylePreferenceDao.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/AppDatabase.kt`

- [ ] **Step 1: Create `NoteDraftDao.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.NoteDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDraftDao {
    @Query("SELECT * FROM note_drafts ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<NoteDraftEntity>>

    @Query("SELECT * FROM note_drafts WHERE id = :id")
    suspend fun getById(id: Long): NoteDraftEntity?

    @Query("SELECT * FROM note_drafts WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<NoteDraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: NoteDraftEntity): Long

    @Update
    suspend fun update(draft: NoteDraftEntity)

    @Query("UPDATE note_drafts SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(draft: NoteDraftEntity)
}
```

- [ ] **Step 2: Create `FoodInfoDao.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.FoodInfoEntity

@Dao
interface FoodInfoDao {
    @Query("SELECT * FROM food_info WHERE draft_id = :draftId")
    suspend fun getByDraftId(draftId: Long): FoodInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(foodInfo: FoodInfoEntity): Long

    @Update
    suspend fun update(foodInfo: FoodInfoEntity)

    @Query("DELETE FROM food_info WHERE draft_id = :draftId")
    suspend fun deleteByDraftId(draftId: Long)
}
```

- [ ] **Step 3: Create `StylePreferenceDao.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.StylePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StylePreferenceDao {
    @Query("SELECT * FROM style_preferences WHERE note_type = :noteType LIMIT 1")
    suspend fun getByNoteType(noteType: String): StylePreferenceEntity?

    @Query("SELECT * FROM style_preferences")
    fun getAllFlow(): Flow<List<StylePreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pref: StylePreferenceEntity): Long

    @Update
    suspend fun update(pref: StylePreferenceEntity)
}
```

- [ ] **Step 4: Create `AppDatabase.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xiaohan.xhsnotegen.data.local.dao.*
import com.xiaohan.xhsnotegen.data.local.entity.*

@Database(
    entities = [
        NoteDraftEntity::class,
        FoodInfoEntity::class,
        StylePreferenceEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDraftDao(): NoteDraftDao
    abstract fun foodInfoDao(): FoodInfoDao
    abstract fun stylePreferenceDao(): StylePreferenceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xhs_notegen.db"
                ).build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/ && git commit -m "feat: add Room DAOs and AppDatabase"
```

### Task 1.4: Entity ↔ Domain Mappers

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/EntityMappers.kt`

- [ ] **Step 1: Create `EntityMappers.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaohan.xhsnotegen.data.local.entity.*
import com.xiaohan.xhsnotegen.domain.*

private val gson = Gson()

// ---- NoteDraft ----

fun NoteDraftEntity.toDomain(foodInfo: FoodInfo? = null): NoteDraft = NoteDraft(
    id = id,
    type = NoteType.fromKey(type),
    status = NoteStatus.fromKey(status),
    photoUris = gson.fromJson(photoUris, StringListType) ?: emptyList(),
    selectedPublishPhotoUris = gson.fromJson(selectedPublishPhotoUris, StringListType) ?: emptyList(),
    title = title ?: "",
    body = body ?: "",
    hashtags = gson.fromJson(hashtags, StringListType) ?: emptyList(),
    variants = variantsJson?.let { gson.fromJson(it, VariantListType) } ?: emptyList(),
    selectedVariantIndex = selectedVariantIndex,
    styleLabel = styleLabel,
    createdAt = createdAt,
    updatedAt = updatedAt,
    foodInfo = foodInfo ?: FoodInfo(),
)

fun NoteDraft.toEntity(): NoteDraftEntity = NoteDraftEntity(
    id = id,
    type = type.key,
    status = status.key,
    photoUris = gson.toJson(photoUris),
    selectedPublishPhotoUris = gson.toJson(selectedPublishPhotoUris),
    title = title.ifBlank { null },
    body = body.ifBlank { null },
    hashtags = gson.toJson(hashtags),
    variantsJson = if (variants.isEmpty()) null else gson.toJson(variants),
    selectedVariantIndex = selectedVariantIndex,
    styleLabel = styleLabel,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---- FoodInfo ----

fun FoodInfoEntity.toDomain(): FoodInfo = FoodInfo(
    dishName = dishName,
    restaurantName = restaurantName,
    location = location ?: "",
    mealDate = mealDate ?: "",
    tasteNotes = tasteNotes ?: "",
    priceOrRating = priceOrRating ?: "",
    vibeNotes = vibeNotes ?: "",
    personalNotes = personalNotes ?: "",
    sponsored = sponsored,
)

fun FoodInfo.toEntity(draftId: Long): FoodInfoEntity = FoodInfoEntity(
    draftId = draftId,
    dishName = dishName,
    restaurantName = restaurantName,
    location = location.ifBlank { null },
    mealDate = mealDate.ifBlank { null },
    tasteNotes = tasteNotes.ifBlank { null },
    priceOrRating = priceOrRating.ifBlank { null },
    vibeNotes = vibeNotes.ifBlank { null },
    personalNotes = personalNotes.ifBlank { null },
    sponsored = sponsored,
)

// ---- StylePreference ----

fun StylePreferenceEntity.toDomain(): NoteStyle =
    NoteStyle.fromKey(preferredStyle)

// ---- Gson type tokens ----

private val StringListType = object : TypeToken<List<String>>() {}.type
private val VariantListType = object : TypeToken<List<NoteVariant>>() {}.type
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/data/local/EntityMappers.kt && git commit -m "feat: add Room entity ↔ domain mappers with Gson JSON serialization"
```

### Task 1.5: Repositories

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/repository/DraftRepository.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/repository/StylePreferencesRepository.kt`

- [ ] **Step 1: Create `DraftRepository.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.repository

import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.data.local.toEntity
import com.xiaohan.xhsnotegen.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DraftRepository(private val db: AppDatabase) {

    private val draftDao = db.noteDraftDao()
    private val foodDao = db.foodInfoDao()

    fun getAllFlow(): Flow<List<NoteDraft>> = draftDao.getAllFlow().map { entities ->
        entities.map { entity ->
            val foodEntity = foodDao.getByDraftId(entity.id)
            entity.toDomain(foodEntity?.toDomain())
        }
    }

    fun getByIdFlow(id: Long): Flow<NoteDraft?> = draftDao.getByIdFlow(id).map { entity ->
        entity?.let {
            val foodEntity = foodDao.getByDraftId(it.id)
            it.toDomain(foodEntity?.toDomain())
        }
    }

    suspend fun getById(id: Long): NoteDraft? {
        val entity = draftDao.getById(id) ?: return null
        val foodEntity = foodDao.getByDraftId(id)
        return entity.toDomain(foodEntity?.toDomain())
    }

    suspend fun insert(draft: NoteDraft): Long {
        val draftId = draftDao.insert(draft.toEntity())
        foodDao.insert(draft.foodInfo.toEntity(draftId))
        return draftId
    }

    suspend fun update(draft: NoteDraft) {
        draftDao.update(draft.copy(updatedAt = System.currentTimeMillis()).toEntity())
        foodDao.deleteByDraftId(draft.id)
        foodDao.insert(draft.foodInfo.toEntity(draft.id))
    }

    suspend fun updateStatus(id: Long, status: NoteStatus) {
        draftDao.updateStatus(id, status.key)
    }

    suspend fun saveGeneratedVariants(draftId: Long, variants: List<NoteVariant>) {
        val entity = draftDao.getById(draftId) ?: return
        val updated = entity.copy(
            variantsJson = com.google.gson.Gson().toJson(variants),
            status = NoteStatus.GENERATED.key,
            updatedAt = System.currentTimeMillis(),
        )
        draftDao.update(updated)
    }

    suspend fun delete(draft: NoteDraft) {
        draftDao.delete(draft.toEntity())
    }
}
```

- [ ] **Step 2: Create `StylePreferencesRepository.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.repository

import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.local.entity.StylePreferenceEntity
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.domain.NoteStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StylePreferencesRepository(private val db: AppDatabase) {

    private val dao = db.stylePreferenceDao()

    suspend fun getGlobalStyle(): NoteStyle? =
        dao.getByNoteType("all")?.toDomain()

    suspend fun getStyleForType(noteType: String): NoteStyle? =
        dao.getByNoteType(noteType)?.toDomain()

    suspend fun resolveStyle(noteType: String): NoteStyle {
        val typePref = getStyleForType(noteType)
        val globalPref = getGlobalStyle()
        return NoteStyle.resolve(typePref, globalPref)
    }

    suspend fun setGlobalStyle(style: NoteStyle) {
        dao.insert(StylePreferenceEntity(noteType = "all", preferredStyle = style.key))
    }

    suspend fun setStyleForType(noteType: String, style: NoteStyle) {
        dao.insert(StylePreferenceEntity(noteType = noteType, preferredStyle = style.key))
    }

    fun getAllFlow(): Flow<List<StylePreferenceEntity>> = dao.getAllFlow()
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/data/repository/ && git commit -m "feat: add DraftRepository and StylePreferencesRepository"
```

### Task 1.6: Application Class

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/XhsNoteGenApp.kt`

- [ ] **Step 1: Create `XhsNoteGenApp.kt`**

```kotlin
package com.xiaohan.xhsnotegen

import android.app.Application
import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.repository.DraftRepository
import com.xiaohan.xhsnotegen.data.repository.StylePreferencesRepository

class XhsNoteGenApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var draftRepository: DraftRepository
        private set

    lateinit var stylePrefsRepository: StylePreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        draftRepository = DraftRepository(database)
        stylePrefsRepository = StylePreferencesRepository(database)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/XhsNoteGenApp.kt && git commit -m "feat: add Application class with DI-initialized repositories"
```

---

## Phase 2: Backend — Gemini Integration

### Task 2.1: Gemini Client Wiring

**Files:**
- Modify: `backend/main.py`
- Modify: `backend/requirements.txt`

- [ ] **Step 1: Add python-dotenv to `backend/requirements.txt`**

Add `python-dotenv==1.0.1` to requirements.txt.

- [ ] **Step 2: Replace the `_call_gemini` stub in `backend/main.py`**

Replace the stub `_call_gemini` function and `init_gemini` with:

```python
import os
from dotenv import load_dotenv
from google.genai import types as genai_types

load_dotenv()

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


async def _call_gemini(prompt_text: str, style: str) -> NoteVariant | None:
    """Call Gemini with structured output and parse the response."""
    if _gemini_client is None:
        init_gemini()
    if _gemini_client is None:
        raise RuntimeError("Gemini client not initialized — set GEMINI_API_KEY")

    # Run blocking Gemini call in a thread
    loop = asyncio.get_running_loop()
    response = await loop.run_in_executor(
        None,
        lambda: _gemini_client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt_text,
            config=genai_types.GenerateContentConfig(
                temperature=0.8,
                top_p=0.95,
                max_output_tokens=2048,
                response_mime_type="application/json",
            ),
        ),
    )

    raw = response.text.strip()
    data = json.loads(raw)

    # Response has { "variants": [...] }, but each call only generates 1 variant
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
```

Also update the top imports to include `json`, and update the `gen_one` function in the `/generate` endpoint to pass images. Replace the `gen_one` closure in the `/generate` endpoint with:

```python
    async def gen_one(style: StyleLabel) -> NoteVariant | None:
        try:
            prompt_text = build_food_prompt(style, req.metadata)
            # For multimodal: include images in the content parts
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
```

And add `import base64` to the imports. Add the new multimodal function:

```python
async def _call_gemini_with_parts(
    system_prompt: str, parts: list, style: str
) -> NoteVariant | None:
    """Call Gemini with system prompt + multimodal parts."""
    if _gemini_client is None:
        init_gemini()
    if _gemini_client is None:
        raise RuntimeError("Gemini client not initialized")

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
```

- [ ] **Step 3: Create `.env.example` in `backend/`**

```
GEMINI_API_KEY=your-gemini-api-key-here
```

- [ ] **Step 4: Commit**

```bash
git add backend/ && git commit -m "feat: wire Gemini multimodal generation with system prompt and structured output"
```

---

## Phase 3: Android Remote Layer

### Task 3.1: API DTOs and Retrofit Client

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/remote/dto/GenerateRequest.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/remote/dto/GenerateResponse.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/remote/AiGenerationClient.kt`

- [ ] **Step 1: Create `GenerateRequest.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FoodMetadataDto(
    @SerializedName("dish_name") val dishName: String,
    @SerializedName("restaurant_name") val restaurantName: String,
    @SerializedName("location") val location: String? = null,
    @SerializedName("meal_date") val mealDate: String? = null,
    @SerializedName("taste_notes") val tasteNotes: String? = null,
    @SerializedName("price_or_rating") val priceOrRating: String? = null,
    @SerializedName("vibe_notes") val vibeNotes: String? = null,
    @SerializedName("personal_notes") val personalNotes: String? = null,
    @SerializedName("sponsored") val sponsored: Boolean = false,
)

data class GenerateRequestDto(
    @SerializedName("note_type") val noteType: String = "food",
    @SerializedName("style") val style: String,
    @SerializedName("metadata") val metadata: FoodMetadataDto,
    @SerializedName("images") val images: List<String>,  // base64 strings
)
```

- [ ] **Step 2: Create `GenerateResponse.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.xiaohan.xhsnotegen.domain.NoteVariant

data class NoteVariantDto(
    @SerializedName("style_label") val styleLabel: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("hashtags") val hashtags: List<String>,
    @SerializedName("warnings") val warnings: List<String> = emptyList(),
)

data class GenerateResponseDto(
    @SerializedName("variants") val variants: List<NoteVariantDto>,
)

fun GenerateResponseDto.toDomain(): List<NoteVariant> =
    variants.map { NoteVariant(it.styleLabel, it.title, it.body, it.hashtags, it.warnings) }
```

- [ ] **Step 3: Create `AiGenerationClient.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.remote

import com.xiaohan.xhsnotegen.data.remote.dto.GenerateRequestDto
import com.xiaohan.xhsnotegen.data.remote.dto.GenerateResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AiGenerationApi {
    @POST("generate")
    suspend fun generate(@Body request: GenerateRequestDto): GenerateResponseDto
}
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/data/remote/ && git commit -m "feat: add Retrofit API DTOs and AiGenerationApi interface"
```

### Task 3.2: Retrofit Client Builder

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/data/remote/RetrofitClient.kt`

- [ ] **Step 1: Create `RetrofitClient.kt`**

```kotlin
package com.xiaohan.xhsnotegen.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Default to 10.0.2.2 for Android emulator → host localhost.
    // Change to your backend IP for physical device testing.
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    private var baseUrl: String = DEFAULT_BASE_URL
    private var retrofit: Retrofit? = null
    private var api: AiGenerationApi? = null

    fun setBaseUrl(url: String) {
        if (url != baseUrl) {
            baseUrl = url
            retrofit = null
            api = null
        }
    }

    fun getApi(): AiGenerationApi {
        if (api == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            api = retrofit!!.create(AiGenerationApi::class.java)
        }
        return api!!
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/data/remote/RetrofitClient.kt && git commit -m "feat: add Retrofit client builder with 120s timeout for generation"
```

---

## Phase 4: Android Utilities

### Task 4.1: Image Compressor and EXIF Reader

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/util/ImageCompressor.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/util/ExifReader.kt`

- [ ] **Step 1: Create `ImageCompressor.kt`**

```kotlin
package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageCompressor {
    private const val MAX_WIDTH = 1024
    private const val MAX_HEIGHT = 1024
    private const val JPEG_QUALITY = 85

    data class CompressedImage(
        val base64: String,
        val success: Boolean,
        val error: String? = null,
    )

    fun compress(context: Context, uri: Uri): CompressedImage {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return CompressedImage("", false, "Cannot open image: $uri")

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val stream = context.contentResolver.openInputStream(uri)
                ?: return CompressedImage("", false, "Cannot reopen image: $uri")

            val sampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight, MAX_WIDTH, MAX_HEIGHT
            )
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream.close()

            if (bitmap == null) {
                return CompressedImage("", false, "Failed to decode: $uri")
            }

            // Scale down if still too large
            val scaled = if (bitmap.width > MAX_WIDTH || bitmap.height > MAX_HEIGHT) {
                val ratio = minOf(
                    MAX_WIDTH.toFloat() / bitmap.width,
                    MAX_HEIGHT.toFloat() / bitmap.height,
                )
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true,
                )
            } else bitmap

            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            CompressedImage(Base64.encodeToString(bytes, Base64.NO_WRAP), true)
        } catch (e: Exception) {
            CompressedImage("", false, "Compression error: ${e.message}")
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                   (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
```

- [ ] **Step 2: Create `ExifReader.kt`**

```kotlin
package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExifReader {

    data class ExifData(
        val captureDate: String?,   // yyyy-MM-dd format
        val location: String?,      // human-readable neighborhood/city
    )

    /**
     * Read EXIF from a photo URI. Returns null fields if data is unavailable.
     */
    fun read(context: Context, uri: Uri): ExifData {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            inputStream?.close()

            if (exif == null) return ExifData(null, null)

            val date = readDate(exif)
            val location = readLocation(context, exif)
            ExifData(date, location)
        } catch (e: IOException) {
            ExifData(null, null)
        }
    }

    /**
     * Aggregate EXIF from multiple photos:
     * - date → earliest capture timestamp
     * - location → most common location string (simple majority)
     */
    fun aggregate(context: Context, uris: List<Uri>): ExifData {
        val allData = uris.map { read(context, it) }

        val dates = allData.mapNotNull { it.captureDate }.sorted()
        val earliest = dates.firstOrNull()

        val locations = allData.mapNotNull { it.location }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key

        return ExifData(earliest, locations)
    }

    private fun readDate(exif: ExifInterface): String? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null

        return try {
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date: Date = parser.parse(raw) ?: return null
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            formatter.format(date)
        } catch (e: Exception) {
            null
        }
    }

    private fun readLocation(context: Context, exif: ExifInterface): String? {
        val latLong = exif.latLong ?: return null
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLong[0], latLong[1], 1)
            addresses?.firstOrNull()?.let { addr ->
                // Prefer locality (city), fall back to subAdminArea or adminArea
                addr.locality ?: addr.subAdminArea ?: addr.adminArea
            }
        } catch (e: IOException) {
            null
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/util/ && git commit -m "feat: add ImageCompressor and ExifReader utilities"
```

---

## Phase 5: UI Layer

### Task 5.1: Theme

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/theme/Color.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/theme/Type.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/theme/Theme.kt`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/themes.xml`

- [ ] **Step 1: Create `Color.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.ui.graphics.Color

// Dark HUD-inspired palette with neon accents
val NeonRed = Color(0xFFE94560)
val NeonCyan = Color(0xFF4FC3F7)
val NeonGreen = Color(0xFF66BB6A)
val NeonPurple = Color(0xFFA78BFA)
val DarkBg = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkCard = Color(0xFF1A1A2E)
val DarkBorder = Color(0xFF30363D)
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val TextMuted = Color(0xFF484F58)
```

- [ ] **Step 2: Create `Type.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)
```

- [ ] **Step 3: Create `Theme.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonRed,
    onPrimary = TextPrimary,
    primaryContainer = DarkCard,
    secondary = NeonCyan,
    tertiary = NeonPurple,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
)

@Composable
fun XhsNoteGenTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Create `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">XHS NoteGen</string>
</resources>
```

- [ ] **Step 5: Create `res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.XhsNoteGen" parent="android:Theme.Material.NoActionBar" />
</resources>
```

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/ui/theme/ android/app/src/main/res/ && git commit -m "feat: add dark HUD-inspired theme with neon accents"
```

### Task 5.2: Navigation and MainActivity

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/navigation/AppNavigation.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/MainActivity.kt`

- [ ] **Step 1: Create `AppNavigation.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xiaohan.xhsnotegen.ui.drafts.DraftListScreen
import com.xiaohan.xhsnotegen.ui.create.CreateFormScreen
import com.xiaohan.xhsnotegen.ui.generate.GeneratingScreen
import com.xiaohan.xhsnotegen.ui.review.ReviewScreen

object Routes {
    const val DRAFT_LIST = "drafts"
    const val CREATE_FORM = "create"
    const val GENERATING = "generating/{draftId}"
    const val REVIEW = "review/{draftId}"

    fun generating(draftId: Long) = "generating/$draftId"
    fun review(draftId: Long) = "review/$draftId"
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DRAFT_LIST) {
        composable(Routes.DRAFT_LIST) {
            DraftListScreen(
                onCreateClick = { navController.navigate(Routes.CREATE_FORM) },
                onDraftClick = { draftId ->
                    val route = when {
                        // Navigation logic handled per status in the screen itself
                        else -> Routes.review(draftId)
                    }
                    navController.navigate(route)
                },
            )
        }

        composable(Routes.CREATE_FORM) {
            CreateFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onDraftSaved = { draftId ->
                    navController.navigate(Routes.generating(draftId)) {
                        popUpTo(Routes.DRAFT_LIST)
                    }
                },
            )
        }

        composable(
            route = Routes.GENERATING,
            arguments = listOf(navArgument("draftId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val draftId = backStackEntry.arguments?.getLong("draftId") ?: return@composable
            GeneratingScreen(
                draftId = draftId,
                onGenerationComplete = { id ->
                    navController.navigate(Routes.review(id)) {
                        popUpTo(Routes.GENERATING) { inclusive = true }
                    }
                },
                onError = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("draftId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val draftId = backStackEntry.arguments?.getLong("draftId") ?: return@composable
            ReviewScreen(
                draftId = draftId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 2: Create `MainActivity.kt`**

```kotlin
package com.xiaohan.xhsnotegen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.xiaohan.xhsnotegen.ui.navigation.AppNavigation
import com.xiaohan.xhsnotegen.ui.theme.XhsNoteGenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XhsNoteGenTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/ui/navigation/ android/app/src/main/java/com/xiaohan/xhsnotegen/MainActivity.kt && git commit -m "feat: add navigation graph and MainActivity"
```

### Task 5.3: Draft List Screen

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/drafts/DraftListViewModel.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/drafts/DraftListScreen.kt`

- [ ] **Step 1: Create `DraftListViewModel.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.drafts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DraftListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository

    val drafts: StateFlow<List<NoteDraft>> = repo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftCount: StateFlow<Int> = drafts.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun deleteDraft(draft: NoteDraft) {
        viewModelScope.launch {
            repo.delete(draft)
        }
    }

    fun getStatusDisplayName(status: NoteStatus): String = when (status) {
        NoteStatus.DRAFT -> "Draft"
        NoteStatus.GENERATED -> "Generated"
        NoteStatus.REVIEWED -> "Reviewed"
        NoteStatus.SHARED -> "Shared"
    }
}
```

- [ ] **Step 2: Create `DraftListScreen.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.drafts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    onCreateClick: () -> Unit,
    onDraftClick: (Long) -> Unit,
    viewModel: DraftListViewModel = viewModel(),
) {
    val drafts by viewModel.drafts.collectAsState()
    val count by viewModel.draftCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("XHS NoteGen") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No drafts yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to create your first food note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "$count draft${if (count != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(drafts, key = { it.id }) { draft ->
                    DraftCard(draft = draft, onClick = { onDraftClick(draft.id) }, onDelete = {
                        viewModel.deleteDraft(draft)
                    })
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: NoteDraft,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = draft.foodInfo.dishName.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(draft.status)
            }

            if (draft.foodInfo.restaurantName.isNotBlank()) {
                Text(
                    draft.foodInfo.restaurantName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${draft.photoUris.size} photos · ${dateFormat.format(Date(draft.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: NoteStatus) {
    val (label, color) = when (status) {
        NoteStatus.DRAFT -> "Draft" to MaterialTheme.colorScheme.outline
        NoteStatus.GENERATED -> "Generated" to MaterialTheme.colorScheme.tertiary
        NoteStatus.REVIEWED -> "Ready" to MaterialTheme.colorScheme.secondary
        NoteStatus.SHARED -> "Shared" to MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/ui/drafts/ && git commit -m "feat: add draft list screen with status chips and delete"
```

### Task 5.4: Create Form Screen (with Photo Picker + EXIF)

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/create/CreateFormViewModel.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/create/CreateFormScreen.kt`

- [ ] **Step 1: Create `CreateFormViewModel.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.util.ExifReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateFormViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as XhsNoteGenApp
    private val draftRepo = app.draftRepository
    private val styleRepo = app.stylePrefsRepository

    private val _photoUris = MutableStateFlow<List<Uri>>(emptyList())
    val photoUris: StateFlow<List<Uri>> = _photoUris.asStateFlow()

    private val _foodInfo = MutableStateFlow(FoodInfo())
    val foodInfo: StateFlow<FoodInfo> = _foodInfo.asStateFlow()

    private val _selectedStyle = MutableStateFlow(NoteStyle.DEFAULT)
    val selectedStyle: StateFlow<NoteStyle> = _selectedStyle.asStateFlow()

    private val _photoCountError = MutableStateFlow<String?>(null)
    val photoCountError: StateFlow<String?> = _photoCountError.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        viewModelScope.launch {
            val resolved = styleRepo.resolveStyle(NoteType.FOOD.key)
            _selectedStyle.value = resolved
        }
    }

    fun setPhotos(uris: List<Uri>) {
        _photoUris.value = uris
        _photoCountError.value = when {
            uris.isEmpty() -> null
            uris.size < 5 -> "Select at least 5 photos (${uris.size} selected)"
            uris.size > 20 -> "Maximum 20 photos allowed"
            else -> null
        }

        // Auto-extract EXIF data from photos
        if (uris.isNotEmpty()) {
            viewModelScope.launch {
                val exif = withContext(Dispatchers.IO) {
                    ExifReader.aggregate(getApplication(), uris)
                }
                val current = _foodInfo.value
                _foodInfo.value = current.copy(
                    location = exif.location ?: current.location,
                    mealDate = exif.captureDate ?: current.mealDate,
                )
            }
        }
    }

    fun updateFoodInfo(info: FoodInfo) {
        _foodInfo.value = info
    }

    fun setStyle(style: NoteStyle) {
        _selectedStyle.value = style
    }

    fun saveDraft(): Long? {
        if (!_foodInfo.value.isValid()) return null
        val count = _photoUris.value.size
        if (count < 5 || count > 20) {
            _photoCountError.value = "Select 5-20 photos (currently $count)"
            return null
        }

        _isSaving.value = true
        var draftId: Long? = null
        viewModelScope.launch {
            val draft = NoteDraft(
                type = NoteType.FOOD,
                status = NoteStatus.DRAFT,
                photoUris = _photoUris.value.map { it.toString() },
                styleLabel = _selectedStyle.value.key,
                foodInfo = _foodInfo.value,
            )
            draftId = draftRepo.insert(draft)
            _isSaving.value = false
        }
        return draftId  // Returns immediately, draftId populated after launch
    }

    /** Suspend version — use from screen coroutine scope */
    suspend fun saveDraftSuspend(): Long {
        if (!_foodInfo.value.isValid()) throw IllegalStateException("Dish and restaurant name required")
        val count = _photoUris.value.size
        if (count < 5 || count > 20) throw IllegalStateException("Select 5-20 photos")

        val draft = NoteDraft(
            type = NoteType.FOOD,
            status = NoteStatus.DRAFT,
            photoUris = _photoUris.value.map { it.toString() },
            styleLabel = _selectedStyle.value.key,
            foodInfo = _foodInfo.value,
        )
        return draftRepo.insert(draft)
    }
}
```

- [ ] **Step 2: Create `CreateFormScreen.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.FoodInfo
import com.xiaohan.xhsnotegen.domain.NoteStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateFormScreen(
    onNavigateBack: () -> Unit,
    onDraftSaved: (Long) -> Unit,
    viewModel: CreateFormViewModel = viewModel(),
) {
    val photos by viewModel.photoUris.collectAsState()
    val foodInfo by viewModel.foodInfo.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val photoCountError by viewModel.photoCountError.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var styleExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.setPhotos(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Food Note") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---- Photo Section ----
            Text("Photos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Select 5-20 food photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(photos) { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                    )
                }
                item {
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                            .clickable { photoPickerLauncher.launch(PickVisualMedia.ImageOnly) },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add photos",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (photoCountError != null) {
                Text(
                    photoCountError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ---- Food Info Section ----
            Text("Food Info", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = foodInfo.dishName,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(dishName = it)) },
                label = { Text("Dish name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = foodInfo.restaurantName,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(restaurantName = it)) },
                label = { Text("Restaurant name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = foodInfo.location,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(location = it)) },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    supportingText = if (photos.isNotEmpty()) {
                        { Text("From EXIF", style = MaterialTheme.typography.labelSmall) }
                    } else null,
                )
                OutlinedTextField(
                    value = foodInfo.mealDate,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(mealDate = it)) },
                    label = { Text("Date") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    supportingText = if (photos.isNotEmpty()) {
                        { Text("From EXIF", style = MaterialTheme.typography.labelSmall) }
                    } else null,
                )
            }

            OutlinedTextField(
                value = foodInfo.tasteNotes,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(tasteNotes = it)) },
                label = { Text("Taste notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = foodInfo.priceOrRating,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(priceOrRating = it)) },
                label = { Text("Price or rating") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = foodInfo.vibeNotes,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(vibeNotes = it)) },
                label = { Text("Atmosphere / vibe") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = foodInfo.personalNotes,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(personalNotes = it)) },
                label = { Text("Personal notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Style Section ----
            Text("Style", style = MaterialTheme.typography.titleMedium)

            ExposedDropdownMenuBox(
                expanded = styleExpanded,
                onExpandedChange = { styleExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedStyle.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = styleExpanded,
                    onDismissRequest = { styleExpanded = false },
                ) {
                    NoteStyle.entries.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style.displayName) },
                            onClick = {
                                viewModel.setStyle(style)
                                styleExpanded = false
                            },
                        )
                    }
                }
            }

            // ---- Error ----
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // ---- Generate Button ----
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val draftId = viewModel.saveDraftSuspend()
                            onDraftSaved(draftId)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to save draft"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = foodInfo.isValid() && photos.size in 5..20 && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("✨ Generate Note")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/ui/create/ && git commit -m "feat: add create form screen with photo picker, EXIF auto-fill, and style selector"
```

### Task 5.5: Generating Screen (with Backend Call)

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/generate/GenerationViewModel.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/generate/GeneratingScreen.kt`

- [ ] **Step 1: Create `GenerationViewModel.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.generate

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.data.remote.RetrofitClient
import com.xiaohan.xhsnotegen.data.remote.dto.*
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GenerationState(
    val isCompressing: Boolean = true,
    val isGenerating: Boolean = false,
    val progress: String = "Compressing photos...",
    val error: String? = null,
    val variants: List<NoteVariant> = emptyList(),
    val isComplete: Boolean = false,
)

class GenerationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as XhsNoteGenApp
    private val draftRepo = app.draftRepository

    private val _state = MutableStateFlow(GenerationState())
    val state: StateFlow<GenerationState> = _state.asStateFlow()

    fun generate(draftId: Long) {
        viewModelScope.launch {
            try {
                val draft = draftRepo.getById(draftId)
                    ?: throw IllegalStateException("Draft not found")

                // Step 1: Compress images
                _state.value = GenerationState(isCompressing = true, progress = "Compressing photos...")
                val photos = draft.photoUris.map { Uri.parse(it) }
                val compressedImages = withContext(Dispatchers.IO) {
                    photos.mapIndexed { i, uri ->
                        _state.value = _state.value.copy(
                            progress = "Compressing photo ${i + 1}/${photos.size}..."
                        )
                        val result = ImageCompressor.compress(getApplication(), uri)
                        if (!result.success) {
                            throw IllegalStateException(
                                "Failed to compress photo ${i + 1}: ${result.error}"
                            )
                        }
                        result.base64
                    }
                }

                // Step 2: Call backend
                _state.value = GenerationState(
                    isCompressing = false,
                    isGenerating = true,
                    progress = "Generating note variants...",
                )

                val request = GenerateRequestDto(
                    noteType = "food",
                    style = draft.styleLabel,
                    metadata = FoodMetadataDto(
                        dishName = draft.foodInfo.dishName,
                        restaurantName = draft.foodInfo.restaurantName,
                        location = draft.foodInfo.location.ifBlank { null },
                        mealDate = draft.foodInfo.mealDate.ifBlank { null },
                        tasteNotes = draft.foodInfo.tasteNotes.ifBlank { null },
                        priceOrRating = draft.foodInfo.priceOrRating.ifBlank { null },
                        vibeNotes = draft.foodInfo.vibeNotes.ifBlank { null },
                        personalNotes = draft.foodInfo.personalNotes.ifBlank { null },
                    ),
                    images = compressedImages,
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.getApi().generate(request)
                }

                val variants = response.toDomain()

                // Step 3: Save results
                draftRepo.saveGeneratedVariants(draftId, variants)

                _state.value = GenerationState(
                    isCompressing = false,
                    isGenerating = false,
                    progress = "Done!",
                    variants = variants,
                    isComplete = true,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCompressing = false,
                    isGenerating = false,
                    error = e.message ?: "Unknown error",
                )
            }
        }
    }
}
```

- [ ] **Step 2: Create `GeneratingScreen.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.generate

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GeneratingScreen(
    draftId: Long,
    onGenerationComplete: (Long) -> Unit,
    onError: () -> Unit,
    viewModel: GenerationViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(draftId) {
        viewModel.generate(draftId)
    }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onGenerationComplete(draftId)
        }
    }

    // Animated dots for the generating indicator
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "dots",
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.error != null) {
                // Error state
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Generation failed",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onError) { Text("Back") }
                    Button(onClick = { viewModel.generate(draftId) }) { Text("Retry") }
                }
            } else {
                // Loading state
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                )
                Text(
                    when {
                        state.isCompressing -> "📸 ${state.progress}"
                        state.isGenerating -> "🧠 ${state.progress}"
                        else -> state.progress
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    ".".repeat(dotCount.toInt()) + " ".repeat(3 - dotCount.toInt()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Generating 2-3 style variants...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

> **Note:** The `Icons.Default.Warning` reference requires `import androidx.compose.material.icons.filled.Warning`. Add proper imports when writing the file.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/ui/generate/ && git commit -m "feat: add generation screen with image compression and backend call"
```

### Task 5.6: Review Screen

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/review/ReviewViewModel.kt`
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/review/ReviewScreen.kt`

- [ ] **Step 1: Create `ReviewViewModel.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteVariant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository

    private val _draft = MutableStateFlow<NoteDraft?>(null)
    val draft: StateFlow<NoteDraft?> = _draft.asStateFlow()

    private val _selectedVariantIndex = MutableStateFlow(0)
    val selectedVariantIndex: StateFlow<Int> = _selectedVariantIndex.asStateFlow()

    fun load(draftId: Long) {
        viewModelScope.launch {
            val d = repo.getById(draftId)
            _draft.value = d
            _selectedVariantIndex.value = d?.selectedVariantIndex ?: 0
        }
    }

    fun selectVariant(index: Int) {
        _selectedVariantIndex.value = index
        _draft.value?.let { draft ->
            val updated = draft.copy(selectedVariantIndex = index)
            _draft.value = updated
            viewModelScope.launch { repo.update(updated) }
        }
    }

    fun updateVariantTitle(text: String) {
        val index = _selectedVariantIndex.value
        _draft.value?.let { draft ->
            val updatedVariants = draft.variants.toMutableList()
            if (index in updatedVariants.indices) {
                updatedVariants[index] = updatedVariants[index].copy(title = text)
            }
            _draft.value = draft.copy(variants = updatedVariants)
        }
    }

    fun updateVariantBody(text: String) {
        val index = _selectedVariantIndex.value
        _draft.value?.let { draft ->
            val updatedVariants = draft.variants.toMutableList()
            if (index in updatedVariants.indices) {
                updatedVariants[index] = updatedVariants[index].copy(body = text)
            }
            _draft.value = draft.copy(variants = updatedVariants)
        }
    }

    fun updateVariantHashtags(hashtags: List<String>) {
        val index = _selectedVariantIndex.value
        _draft.value?.let { draft ->
            val updatedVariants = draft.variants.toMutableList()
            if (index in updatedVariants.indices) {
                updatedVariants[index] = updatedVariants[index].copy(hashtags = hashtags)
            }
            _draft.value = draft.copy(variants = updatedVariants)
        }
    }

    fun reorderPhotos(fromIndex: Int, toIndex: Int) {
        _draft.value?.let { draft ->
            val uris = draft.photoUris.toMutableList()
            val item = uris.removeAt(fromIndex)
            uris.add(toIndex, item)
            _draft.value = draft.copy(photoUris = uris)
        }
    }

    fun togglePublishPhoto(index: Int) {
        _draft.value?.let { draft ->
            val uri = draft.photoUris.getOrNull(index) ?: return
            val selected = draft.selectedPublishPhotoUris.toMutableList()
            if (uri in selected) selected.remove(uri) else selected.add(uri)
            _draft.value = draft.copy(selectedPublishPhotoUris = selected)
        }
    }

    fun saveChanges() {
        _draft.value?.let { draft ->
            viewModelScope.launch {
                repo.update(draft.copy(status = NoteStatus.REVIEWED))
            }
        }
    }
}
```

- [ ] **Step 2: Create `ReviewScreen.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.review

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.ui.publish.XiaohongshuSharePublisher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    draftId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ReviewViewModel = viewModel(),
) {
    val draft by viewModel.draft.collectAsState()
    val selectedIndex by viewModel.selectedVariantIndex.collectAsState()

    LaunchedEffect(draftId) { viewModel.load(draftId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review & Edit") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.saveChanges() }) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        draft?.let { d ->
            val variants = d.variants
            if (variants.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No variants generated", style = MaterialTheme.typography.bodyLarge)
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Variant tabs
                Text("Variants", style = MaterialTheme.typography.titleMedium)
                ScrollableTabRow(selectedTabIndex = selectedIndex) {
                    variants.forEachIndexed { index, variant ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectVariant(index) },
                            text = {
                                Column {
                                    Text(
                                        variant.styleLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (index == 0) {
                                        Text(
                                            "Preferred",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                val currentVariant = variants[selectedIndex]

                // Warnings
                if (currentVariant.warnings.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                currentVariant.warnings.joinToString("; "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Editable title
                OutlinedTextField(
                    value = currentVariant.title,
                    onValueChange = { viewModel.updateVariantTitle(it) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Editable body
                OutlinedTextField(
                    value = currentVariant.body,
                    onValueChange = { viewModel.updateVariantBody(it) },
                    label = { Text("Body") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Editable hashtags
                OutlinedTextField(
                    value = currentVariant.hashtags.joinToString(" "),
                    onValueChange = { text ->
                        viewModel.updateVariantHashtags(
                            text.split(" ").filter { it.startsWith("#") }
                        )
                    },
                    label = { Text("Hashtags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Photo grid
                Text("Photos (tap to toggle publish selection)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${d.selectedPublishPhotoUris.size}/${d.photoUris.size} selected for publish",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(d.photoUris) { index, uriStr ->
                        val uri = Uri.parse(uriStr)
                        val isSelected = uriStr in d.selectedPublishPhotoUris
                        Box(modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .clickable { viewModel.togglePublishPhoto(index) }
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isSelected) {
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Share button
                Button(
                    onClick = {
                        viewModel.saveChanges()
                        XiaohongshuSharePublisher.publish(d)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("📤 Share to Xiaohongshu")
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/ui/review/ && git commit -m "feat: add review screen with variant switching, inline editing, and photo selection"
```

### Task 5.7: Publisher (Android Share Handoff)

**Files:**
- Create: `android/app/src/main/java/com/xiaohan/xhsnotegen/ui/publish/XiaohongshuSharePublisher.kt`

- [ ] **Step 1: Create `XiaohongshuSharePublisher.kt`**

```kotlin
package com.xiaohan.xhsnotegen.ui.publish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.xiaohan.xhsnotegen.domain.NoteDraft
import java.io.File

object XiaohongshuSharePublisher {

    /**
     * Share the selected variant text + photos to Xiaohongshu via Android share intent.
     * Copies text to clipboard as fallback.
     */
    fun publish(context: Context, draft: NoteDraft) {
        val variant = draft.variants.getOrNull(draft.selectedVariantIndex)
            ?: run {
                Toast.makeText(context, "No variant selected", Toast.LENGTH_SHORT).show()
                return
            }

        val shareText = buildString {
            appendLine(variant.title)
            appendLine()
            appendLine(variant.body)
            appendLine()
            append(variant.hashtags.joinToString(" "))
        }

        // Copy text to clipboard as fallback
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XHS Note", shareText))
        Toast.makeText(context, "Note text copied to clipboard", Toast.LENGTH_SHORT).show()

        // Build share intent with photos
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, variant.title)

            // Attach selected publish photos (or all if none selected)
            val urisToShare = if (draft.selectedPublishPhotoUris.isNotEmpty()) {
                draft.selectedPublishPhotoUris
            } else {
                draft.photoUris
            }

            val imageUris = urisToShare.map { uriStr ->
                val uri = Uri.parse(uriStr)
                // If content:// URI, use directly. If file://, convert via FileProvider.
                if (uri.scheme == "file") {
                    val file = File(uri.path!!)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } else {
                    uri
                }
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Explicitly target Xiaohongshu if installed
        val xhsPackage = "com.xingin.xhs"
        val resolvedInfo = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg"), 0
        )

        val xhsInstalled = resolvedInfo.any { it.activityInfo.packageName == xhsPackage }

        if (xhsInstalled) {
            shareIntent.setPackage(xhsPackage)
            try {
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                // Fallback: show system chooser
                shareIntent.setPackage(null)
                context.startActivity(Intent.createChooser(shareIntent, "Share to Xiaohongshu"))
            }
        } else {
            // Xiaohongshu not installed — show instructions
            shareIntent.setPackage(null)
            context.startActivity(Intent.createChooser(shareIntent, "Share (Xiaohongshu not found)"))
            Toast.makeText(
                context,
                "Xiaohongshu not found. Text copied to clipboard. You can paste it manually.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/xiaohan/xhsnotegen/ui/publish/ && git commit -m "feat: add Xiaohongshu share publisher with clipboard fallback"
```

---

## Phase 6: Backend Tests

### Task 6.1: Backend API Tests

**Files:**
- Create: `backend/tests/__init__.py`
- Create: `backend/tests/test_generate.py`

- [ ] **Step 1: Create `backend/tests/__init__.py`**

Empty file.

- [ ] **Step 2: Create `backend/tests/test_generate.py`**

```python
"""Tests for POST /generate endpoint."""

import base64
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock

from main import app, NoteVariant, GenerateResponse

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
            "dish_name": "红烧肉",
            "restaurant_name": "外婆家",
            "location": "上海",
            "meal_date": "2024-03-15",
            "taste_notes": "肥而不腻",
            "price_or_rating": "人均80",
            "vibe_notes": "家庭聚餐好去处",
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

@pytest.mark.asyncio
async def test_generate_returns_200_with_mocked_gemini():
    """Full request with mocked Gemini returns 2-3 variants."""
    mock_variants = [
        NoteVariant(
            style_label="Casual Story",
            title="测试标题",
            body="测试正文",
            hashtags=["#美食", "#红烧肉"],
            warnings=[],
        ),
        NoteVariant(
            style_label="Practical",
            title="实用推荐",
            body="推荐理由",
            hashtags=["#推荐"],
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
    req["metadata"]["dish_name"] = ""
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
        "dish_name": "火锅",
        "restaurant_name": "海底捞",
        "sponsored": False,
    }
    req["images"] = [DUMMY_JPEG_BASE64] * 5

    with patch("main._call_gemini_with_parts", new_callable=AsyncMock) as mock_gemini:
        mock_gemini.side_effect = [
            NoteVariant(style_label="Casual Story", title="T", body="B", hashtags=["#tag"], warnings=[]),
            NoteVariant(style_label="Practical", title="T", body="B", hashtags=["#tag"], warnings=[]),
        ]
        response = client.post("/generate", json=req)

    assert response.status_code == 200


# ---------------------------------------------------------------------------
# Edge cases
# ---------------------------------------------------------------------------

def test_image_count_boundary_min():
    """Exactly 5 images should be valid."""
    req = _valid_request()
    req["images"] = [DUMMY_JPEG_BASE64] * 5
    with patch("main._call_gemini_with_parts", new_callable=AsyncMock) as mock_gemini:
        mock_gemini.side_effect = [
            NoteVariant(style_label="Casual Story", title="T", body="B", hashtags=["#t"], warnings=[]),
            NoteVariant(style_label="Practical", title="T", body="B", hashtags=["#t"], warnings=[]),
        ]
        response = client.post("/generate", json=req)
    assert response.status_code == 200


def test_image_count_boundary_max():
    """Exactly 20 images should be valid."""
    req = _valid_request()
    req["images"] = [DUMMY_JPEG_BASE64] * 20
    with patch("main._call_gemini_with_parts", new_callable=AsyncMock) as mock_gemini:
        mock_gemini.side_effect = [
            NoteVariant(style_label="Casual Story", title="T", body="B", hashtags=["#t"], warnings=[]),
            NoteVariant(style_label="Practical", title="T", body="B", hashtags=["#t"], warnings=[]),
        ]
        response = client.post("/generate", json=req)
    assert response.status_code == 200


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

def test_health_returns_ok():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
```

- [ ] **Step 3: Install test deps and run tests**

```bash
cd backend && pip install pytest pytest-asyncio httpx && python -m pytest tests/ -v
```

- [ ] **Step 4: Commit**

```bash
git add backend/tests/ && git commit -m "test: add backend API tests for validation, boundaries, and mocked generation"
```

---

## Phase 7: Android Resources and Final Wiring

### Task 7.1: Android Resource Files

**Files:**
- Create: `android/app/src/main/res/xml/file_paths.xml`
- Modify: `android/app/src/main/AndroidManifest.xml` (add FileProvider)

- [ ] **Step 1: Create `res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
    <files-path name="files" path="." />
</paths>
```

- [ ] **Step 2: Add FileProvider to `AndroidManifest.xml`**

Add inside `<application>` block, after `</activity>`:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

Also add the `android:requestLegacyExternalStorage="true"` attribute to the `<application>` block (for Android 10 compat).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/res/xml/ android/app/src/main/AndroidManifest.xml && git commit -m "chore: add FileProvider configuration for share intent URIs"
```

### Task 7.2: Gradle Wrapper

- [ ] **Step 1: Generate Gradle wrapper**

If you have Android Studio or Gradle installed:
```bash
cd android && gradle wrapper --gradle-version 8.9
```

Otherwise, create `android/gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Commit**

```bash
git add android/gradle/ && git commit -m "chore: add Gradle wrapper properties"
```

---

## Implementation Order Summary

| Phase | Tasks | Description | Cumulative |
|---|---|---|---|
| 0 | 0.1–0.3 | Scaffolding: gitignore, Gradle, FastAPI | Project boots |
| 1 | 1.1–1.6 | Data layer: domain models, Room, repos | Local storage works |
| 2 | 2.1 | Gemini wiring in backend | Backend generates |
| 3 | 3.1–3.2 | Retrofit client | App can call backend |
| 4 | 4.1 | Image compressor + EXIF reader | Photo utilities ready |
| 5 | 5.1–5.7 | UI: theme, nav, all 5 screens | Full app functional |
| 6 | 6.1 | Backend tests | Tests pass |
| 7 | 7.1–7.2 | Resources + wrapper | App buildable |

**Total: ~16 commits across 7 phases.**

---

## Post-Implementation Verification

After all tasks complete, verify:

1. **Android builds:** `cd android && ./gradlew assembleDebug` succeeds
2. **Backend starts:** `cd backend && python main.py` starts on port 8000
3. **Backend tests:** `cd backend && python -m pytest tests/ -v` passes
4. **End-to-end:** Launch app on emulator, create a food draft with 5 test photos, generate variants, review, and share
5. **Error paths:** Test with backend down (retry shown), test with <5 photos (error shown)
