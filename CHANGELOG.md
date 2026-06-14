# Changelog

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
