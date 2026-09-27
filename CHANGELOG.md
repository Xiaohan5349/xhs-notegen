# Changelog

## Unreleased

### Fixed
- Publishing now deletes only the photos that were actually published; unselected photos are preserved
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
- Release builds now ship ProGuard keep rules for Gson/Room/OkHttp — R8 no longer risks breaking AI response parsing at runtime
- Removed unused dependencies (Retrofit, converter-gson, logging-interceptor); OkHttp is now declared explicitly
- GeminiClient and XhsApiClient share one OkHttpClient (single connection pool, one tuning point)
- Release builds are signed with a local keystore (config reads passwords from gitignored local.properties; keystore and passwords never enter git)
- Image upload metadata carries real width/height instead of hardcoded 1024x768
- The selected note style now persists as the default for next time

### Removed
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
