# XHS NoteGen

Standalone Android app that generates Xiaohongshu food notes from photos using Gemini AI and publishes directly to XHS via the Creator API. No backend server required — everything runs on your phone.

## Features

- **Photo picker** (1-20 photos) with EXIF auto-fill (date, time, GPS location)
- **AI generation** via Google Gemini — 4 note styles in Chinese (Casual Story, Practical, Punchy, Minimal)
- **Review & edit** — switch between styles, tweak title, body, and hashtags
- **Direct publishing** to Xiaohongshu via the Creator API
- **Draft management** — history with filter tabs, import/export JSON backups
- **Dark HUD theme** with neon accents

## Setup

1. Install the APK on your Android device (minSdk 28)
2. Open Settings (⚙️) → paste your Gemini API key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
3. Login to XHS (👤) → the WebView opens creator.xiaohongshu.com → log in → tap Done

## Build

```bash
cd android
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
gradlew assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## How It Works

1. Pick 1-20 food photos → EXIF auto-fills date/time/location
2. Fill in dish names, restaurant, and optional notes
3. Gemini generates 4 diary-style note variants in parallel
4. Review, edit, and select which photos to include
5. One-tap publish to your XHS account via the Creator API

## Tech Stack

- Kotlin + Jetpack Compose
- Room database
- OkHttp (Gemini API + XHS Creator API)
- Pure Kotlin x-s signing (ported from [ReaJason/xhs](https://github.com/ReaJason/xhs))

## Limitations

- Food notes only (museum/travel/concert not yet built)
- XHS login expires after several weeks (re-login required)
- No Android unit tests
