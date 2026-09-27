# XHS NoteGen

Standalone Android app that generates Xiaohongshu food notes from photos using Gemini AI and publishes directly to XHS via the Creator API. No backend server required — everything runs on your phone.

## Features

- **Photo picker** (1-20 photos) with EXIF auto-fill (date, time, GPS location); pick a cover, add or remove photos
- **AI generation** via Google Gemini — 4 note styles in Chinese (Casual Story, Practical, Punchy, Minimal), written in one request
- **Sounds like you** — prompts are tuned against typical "AI voice" tells, keep your own words, and learn from notes you've already reviewed or posted
- **Review & edit** — switch styles, edit title (with the 20-character XHS limit), body and tags, rewrite one style or all four
- **Publishing** straight to Xiaohongshu via the Creator API, or a guided manual handoff (text copied, photos saved in order, XHS opened)
- **Food diary** — photo-feed home with filters (Drafts / Ready / Posted), import/export JSON backups
- **Warm "food journal" design** with light and dark themes

## Setup

1. Install the APK on your Android device (Android 10+, minSdk 29)
2. Open Settings (account icon, top right) → paste your Gemini API key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey). The model defaults to `gemini-2.5-flash`; any Gemini model id that accepts images can be entered there.
3. In the same sheet, Log in to Xiaohongshu → log in on creator.xiaohongshu.com → tap Done

## Build

Full guide — release signing, installing on a phone, troubleshooting: **[BUILD.md](BUILD.md)**.

Needs JDK 17+ and the Android SDK (platform 35). Point `ANDROID_HOME` at the SDK (or put `sdk.dir` in `android/local.properties`).

macOS / Linux:

```bash
cd android
bash gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows:

```bat
cd android
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
gradlew assembleDebug testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Release builds are signed with the keystore in `.wsl-tools/keystore/` (gitignored) using passwords from `local.properties`.
Room schemas are exported to `android/app/schemas/` — commit them, and add a `Migration` for every schema change (there is no destructive fallback).

## How It Works

1. Pick 1-20 food photos → EXIF auto-fills date/time/location
2. Fill in dish names, restaurant, and — ideally — a few words of your own
3. Gemini writes 4 diary-style variants in one request (photos are sent once)
4. Review, edit, and choose which photos to post and in what order
5. One-tap publish to your XHS account via the Creator API; if that fails, a guided manual handoff

### Writing quality

The prompts live in `ui/generate/FoodPrompts.kt` and are written in Chinese. They describe what real quick notes look like, name the common AI tells to avoid (summary endings, parallelism, food-critic vocabulary, date-first openings), keep your own phrasing, and include a couple of short example notes. Once you've marked notes as ready or posted, the three most recent are sent along as examples of your voice — the more you edit and post, the more the drafts sound like you.

## Tech Stack

- Kotlin + Jetpack Compose
- Room database
- OkHttp (Gemini API + XHS Creator API)
- Pure Kotlin x-s signing (ported from [ReaJason/xhs](https://github.com/ReaJason/xhs))

## Limitations

- Food notes only (museum/travel/concert not yet built)
- XHS login expires after several weeks — the app detects this and asks you to log in again
- The Creator API and its x-s signing are unofficial and can change without notice (signing is covered by golden tests against the upstream Python implementation)
- Backups contain note text only, not photos
