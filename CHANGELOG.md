# Changelog

## Unreleased

### Added
- Multiple AI providers: Gemini, ChatGPT (OpenAI), Claude, DeepSeek, and Custom (any OpenAI-compatible API) — each with its own key, built-in model list (verified Sept 2026) and a free-text model id for newer models
- Text-only models are supported: notes are written from the text without sending photos
- Nine color themes and a System / Light / Dark switch, applied instantly and remembered:
  classic Tomato, Matcha, Blueberry, Sakura, Sesame, Latte (beige); anime-inspired Summer Sky (clouds by day, stars and moon by night), City Pop (80s retro sunset) and Matsuri (seigaiha wave pattern) — all artwork is original and drawn in code
- Full Settings screen (replaces the bottom sheet); changes save immediately
- Editable writing prompt (Settings → AI writing → Writing prompt): main instructions and each style's instructions, with per-section and full reset; the JSON output format stays fixed and is appended automatically

### Fixed
- XHS login: the login page now uses the phone's real browser identity (the hard-coded "Chrome/149" user agent contradicted the engine version, which XHS security checks can flag), drops the embedded-WebView "wv" marker, supports popups and JS dialogs, accepts third-party cookies, and shows real loading progress
- XHS login: new "save QR" button — when XHS asks to scan a QR code (QR login or extra verification), save it and scan it in the XHS app from the album (扫一扫 → 相册)

### Changed
- Default Gemini model is now `gemini-3.8-flash` — `gemini-2.5-flash` is limited to past users and unavailable to new API keys; Gemini 3 requests drop the deprecated `temperature` and use `thinkingLevel: low`
- Structured output uses each provider's native JSON-schema support and steps down to JSON mode / prompt-only if a server rejects it
- Longer read timeout for AI calls (reasoning models answer in one response)

## v1.1.0 — 2026-09-27

### Fixed
- x-s-common signature now matches upstream: 10 of the 256 checksum-table entries were mistranscribed, the checksum overflowed 32-bit math, and `x9` was sent as a string instead of a number (locked in by golden tests)
- Manual handoff can find and open the XHS app on Android 11+ (added `<queries>` for package visibility)
- An expired XHS login is detected and the user is asked to log in again, instead of silently falling back to handoff forever; logging in only counts once a real session cookie exists; you can now log out
- Marking a note Ready no longer reverts to Generated on the next edit; the last edits before leaving the screen are no longer lost; regenerate can't be overwritten by a pending save
- Photo selection is explicit: all photos start selected, numbers show post order (#1 = cover), and at least one photo must stay selected
- Rotating the phone during generation no longer starts another (billed) Gemini request
- Gemini output is no longer cut off by the 2048-token limit (2.5 models spend thinking tokens from it); truncation, safety blocks, bad keys, unknown models and rate limits get clear messages, with one automatic retry for transient errors
- Published photos are sent at up to 2160px / JPEG 92 instead of the 1024px AI preview quality
- Photos are kept after publishing (the draft still shows them); they are removed when the draft is deleted
- Importing a backup gives every draft its own photo copies (deleting a duplicate used to delete the original's photos); imports are all-or-nothing and tolerate sparse files
- Missing fields in Gemini's JSON or in stored data can no longer crash the review screen (null-tolerant parsing)
- A draft whose generation failed can be written from the review screen
- Publishing runs in the ViewModel, so leaving the screen mid-upload no longer abandons it
- A photo that can't be read now stops publishing with a clear message instead of being silently dropped
- Abandoned or removed photos in the create form no longer leave orphaned copies in app storage
- Draft + food info writes are transactional
- Android 12+ device-to-device transfer no longer copies XHS session cookies or the API key (data extraction rules)
- Deleting a draft now also removes its local photo copies from app storage
- Direct-publish failures now fall back to manual handoff (clipboard + gallery save + open XHS app), as documented for v1.0
- Upload response bodies are closed, preventing pooled-connection leaks
- Portrait photos no longer get published sideways — ImageCompressor now applies EXIF orientation before compressing
- The draft list and repository tolerate corrupted JSON rows (skipped instead of crashing)
- Review edits (title/body/hashtags/photo selection) auto-save after a debounce instead of being lost on exit
- Titles longer than XHS's 20-character limit are rejected before calling the API
- EXIF capture dates parse as local time (was parsed as UTC, which could be a day off)
- Signing serialization matches upstream Python: `video_info: null` is kept and url-encoding is ASCII-safe

### Changed
- New prompts, written in Chinese, aimed at notes that sound human: concrete details over summaries, the user's own words kept, a diary-style date header, few-shot examples, and the user's recently reviewed/posted notes as voice samples
- All four styles are generated in a single Gemini request (photos uploaded once) using a response schema; styles are labeled by the app, not by the model
- New "Rewrite this one" (single style) next to "Rewrite all"; the Gemini model is configurable in Settings
- Redesigned UI: warm "food journal" theme with light and dark modes, photo-feed home with status filters, grouped create form with cover selection, animated generating screen, paper-style editor with a live 20-character title counter and tag chips, guided login and handoff dialogs, new adaptive app icon
- minSdk raised to 29 (Android 10): the gallery handoff relies on MediaStore relative paths
- Room schema is exported and the destructive migration fallback removed, so a future schema change can't silently wipe drafts
- Unit tests added (signer golden values, prompt building, response and backup parsing)
- Release builds now ship ProGuard keep rules for Gson/Room/OkHttp — R8 no longer risks breaking AI response parsing at runtime
- Removed unused dependencies (Retrofit, converter-gson, logging-interceptor); OkHttp is now declared explicitly
- GeminiClient and XhsApiClient share one OkHttpClient (single connection pool, one tuning point)
- Release builds are signed with a local keystore (config reads passwords from gitignored local.properties; keystore and passwords never enter git)
- Image upload metadata carries real width/height instead of hardcoded 1024x768
- The selected note style now persists as the default for next time

### Removed
- Unused DAO/repository methods and the legacy `WRITE_EXTERNAL_STORAGE` permission
- Dead code: unused `filterStatus` flow, `URLEncoder` import, never-wired `sponsored` field, unused FileProvider declaration

## v1.0 — 2026-06-13

### Added
- Photo picker (1-20 photos) with EXIF auto-fill for date, time, and GPS location
- 4 AI note styles via Google Gemini (Casual Story, Practical, XHS Punchy, Clean/Minimal)
- Direct publishing to Xiaohongshu via Creator API with x-s request signing
- Draft history with filter tabs (All, Drafts, Ready, Shared)
- Import/export all drafts as JSON backup
- Gemini API key management via Settings dialog
- WebView-based XHS login with cookie capture
- Manual handoff fallback (save photos to Pictures, open XHS app)

### Changed
- New prompts, written in Chinese, aimed at notes that sound human: concrete details over summaries, the user's own words kept, a diary-style date header, few-shot examples, and the user's recently reviewed/posted notes as voice samples
- All four styles are generated in a single Gemini request (photos uploaded once) using a response schema; styles are labeled by the app, not by the model
- New "Rewrite this one" (single style) next to "Rewrite all"; the Gemini model is configurable in Settings
- Redesigned UI: warm "food journal" theme with light and dark modes, photo-feed home with status filters, grouped create form with cover selection, animated generating screen, paper-style editor with a live 20-character title counter and tag chips, guided login and handoff dialogs, new adaptive app icon
- minSdk raised to 29 (Android 10): the gallery handoff relies on MediaStore relative paths
- Room schema is exported and the destructive migration fallback removed, so a future schema change can't silently wipe drafts
- Unit tests added (signer golden values, prompt building, response and backup parsing)
- Photo limit lowered from 5-20 to 1-20
- Date format now includes time (yyyy-MM-dd HH:mm)
- All 4 styles generated in parallel (was 3)

### Removed
- Python backend — all logic runs on Android
- Retrofit HTTP client (replaced by OkHttp)
- `usesCleartextTraffic` — all traffic HTTPS only

### Security
- `allowBackup` disabled
- FileProvider paths tightened to specific subdirectories
- Gemini API key passed via header instead of URL query parameter
- Debug log statements removed
