# Glint (浮生拾遗)

Standalone Android app that generates Xiaohongshu diary-style notes (food, travel, outfits, home… ) from photos using AI (Gemini, ChatGPT, Claude, DeepSeek or any OpenAI-compatible model) and publishes directly to XHS via the Creator API. No backend server required — everything runs on your phone.

## Features

- **Photo picker** (up to 40 photos per note, set per mode) with EXIF auto-fill (date, time, GPS location); pick a cover, add or remove photos
- **Writing modes** — Food, Travel, Outfit, Beauty, Home, Fitness, Parenting, Books & Films, or your own; each with its own form, prompt, note language and photo limit
- **AI generation** — pick Gemini, ChatGPT, Claude, DeepSeek or a custom OpenAI-compatible model; 4 note styles in Chinese (Casual Story, Practical, Punchy, Minimal), written in one request
- **Sounds like you** — prompts are tuned against typical "AI voice" tells, keep your own words, and learn from notes you've already reviewed or posted
- **Review & edit** — switch styles, edit title (with the 20-character XHS limit), body and tags, rewrite one style or all four
- **Publishing** straight to Xiaohongshu via the Creator API, or a guided manual handoff (text copied, photos saved in order, XHS opened)
- **Life diary** — photo-feed home with filters (Drafts / Ready / Posted), your own tags, multi-select actions, and grouping by place, tag, rating or writing mode (order set in Settings); import/export JSON backups
- **Organize by place** — each note gets a place from its photo's GPS (with your permission; the Android photo picker otherwise strips it) or from its area / restaurant name
- **Themes** — six classic palettes (Tomato, Matcha, Blueberry, Sakura, Sesame, Latte) the signature Glint theme (water ripples and drifting glints), and three anime-inspired ones with drawn backdrops (Summer Sky, City Pop, Matsuri), each with System / Light / Dark mode

## Setup

1. Install the APK on your Android device (Android 10+, minSdk 29)
2. Open Settings (account icon, top right) → **AI writing** → pick a provider and model, paste that provider's API key:

   | Provider | Built-in models (Sept 2026) | Key from |
   |---|---|---|
   | Gemini | `gemini-3.8-flash` (default), `gemini-3.5-flash-lite`, `gemini-3.1-pro-preview` | aistudio.google.com/apikey |
   | ChatGPT | `gpt-6-luna` (default), `gpt-6-sol`, `gpt-6-astra` | platform.openai.com/api-keys |
   | Claude | `claude-sonnet-5` (default), `claude-haiku-4-5`, `claude-opus-5-5` | platform.claude.com |
   | DeepSeek | `deepseek-flash` (sees photos), `deepseek-v4-pro` (text only) | platform.deepseek.com/api_keys |
   | Custom | any OpenAI-compatible endpoint (Qwen, Kimi, Doubao, OpenRouter, local…) | your provider |

   Any newer model id can be typed into "Other model", so new releases don't need an app update. Text-only models get your notes without the photos.
3. In Settings → Xiaohongshu, log in → log in on creator.xiaohongshu.com → tap Done

## Build

Full guide — release signing, installing on a phone, troubleshooting: **[BUILD.md](BUILD.md)**.

Needs JDK 17+ and the Android SDK (platform 35). Point `ANDROID_HOME` at the SDK (or put `sdk.dir` in `android/local.properties`).

macOS / Linux:

```bash
cd android
bash gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows (PowerShell):

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
.\gradlew.bat assembleDebug testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

(CMD: same steps with `set JAVA_HOME=...` and `gradlew.bat`.)

Release builds are signed with the keystore in `.wsl-tools/keystore/` (gitignored) using passwords from `local.properties`.
Room schemas are exported to `android/app/schemas/` — commit them, and add a `Migration` for every schema change (there is no destructive fallback).

## How It Works

1. Pick 1-20 food photos → EXIF fills the date/time; allow photo access to also fill the place from GPS
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
