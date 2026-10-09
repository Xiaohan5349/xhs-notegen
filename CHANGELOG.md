# Changelog

## Unreleased

### Added
- **Filter by several tags at once** on the home screen. Tags under the same top-level tag combine as "or", different ones as "and": 京都 + 大阪 + 拉面 shows ramen in Kyoto or Osaka. Picking 京都 also picks 日本; un-picking it goes back to all of 日本
- Tag counts on each tag, and an **All tags** sheet with search, multi-select and Clear

### Changed
- Refreshed look across the app: tiles with hairline edges and tighter corners, one style of filter chip, inset segmented choices, and a 4-column photo grid with "Show all N" on the create and review screens
- Home screen opens with the note counts: a big "All notes" tile plus Ready / Drafts / Posted tiles that filter when tapped
- "Posted" is gold in every theme; star colors follow the theme
- Two accent colors are slightly darker so white text on them is easier to read

## v1.10.0 — 2026-09-30

### Renamed
- The app is now **浮生拾遗 / Glint** (was 食记 / XHS NoteGen) — it is for the whole day, not just food. The debug app is "浮生拾遗 Debug" / "Glint Debug". Only the name changed: same package, your notes and settings are kept

### Added
- New app icon: a gold glint (four-point sparkle) above still water with spreading ripples, on deep teal — drawn in code, with a matching one-colour version for themed icons
- New **Glint · 浮生** theme (Settings → Appearance → Signature), light and dark: misty teal by day, deep teal night with gold accents, and a drawn header of water ripples with drifting glints. Your current theme is unchanged
- **Group by Mode** on the home screen (Food, Travel, Outfit…), next to Place · Tag · Rating
- **Reorder the Group buttons**: Settings → Home screen → move each up or down
- **Write the note in 中文 / English** on the new-note screen, and again on the review screen for the next rewrite. Each note remembers its language (database v5, with a migration — existing notes use their mode's language)

### Changed
- Photo limit is now **40** per note (was 20) and the per-mode slider goes up to 40. A mode you edited earlier that had stored "20" now uses 40 — set it again if you want less. Xiaohongshu's own limit per post may be lower, and more photos means a bigger, slower AI request
- Place and mode names follow the **app** language: in English the home screen shows China › Nanjing · Jiangsu, "Food", and so on (stored places stay Chinese, so groups don't split). Only the prompt and the notes it writes follow the note language
- Form hints (the grey example text) follow the app language too; the names given to the AI still follow the note language
- Modes no longer have a **root tag**, and the mode's tag is no longer added to new notes — that is what the Mode group is for
- The "Organize N without a place" button sits on its own line

### Removed / cleaned up
- One-time cleanup on first launch: the tags modes used to add to every note (美食, 旅行, 穿搭, 美妆护肤, 家居, 运动, 育儿, 书影音, plus any root tag you set yourself) are removed. Tags that were under them (e.g. 京都 under 旅行) move up a level and keep their notes; all your other tags stay

## v1.9.0 — 2026-09-29

### Added
- Choose the date and place from lists instead of typing (create screen; the place list also on the home and review screens)
  - When: year, month and day wheels, plus an optional time. It starts from the photo's date when there is one
  - Place: Country → Province/State → City lists, in your app language with the other name underneath, with search and "Not listed? Type it". Works offline and without Google services, so it works on phones in mainland China. Includes every country; provinces and cities for China (all prefecture-level cities), the US, Japan, Korea, Thailand, the UK, France, Italy, Germany, Spain, Australia, Canada and many more. A place read from the photo still fills it in first
- AI writing page (Settings → AI writing): provider, model, API key and, for Custom, the address and "Model can see photos" — all on their own screen. **Test connection** sends one tiny request and tells you if it worked, how long it took, whether the model takes photos, or exactly why not (address, HTTP code and the server's reply under "Show details"). **Save** saves and runs the same test; leaving with unsaved changes asks first
- Custom models: "Load models from this address" reads the model list from the server (`/models`) so you can pick the id instead of typing it
- Debug builds show the technical details (address, HTTP code, server reply) on the "Couldn't write the note" screen

### Changed
- Custom (OpenAI-compatible) models get a larger output limit (16,000 tokens, stepping down to 4,096 if the server refuses), because reasoning models such as StepFun's spend part of it thinking. If a model still uses it all on thinking, the message says so
- When a model refuses photos the message now tells you to turn off "Model can see photos"
- Error messages for a custom address name the address (api.stepfun.com) instead of "Custom"
- Settings shows the chosen model as one row that opens the AI page

### Notes
- Places you pick are stored under their Chinese names, like places found by the phone, so groups stay together
- StepFun Step Plan: address `https://api.stepfun.com/step_plan/v1` and model `step-5-preview` match StepFun's docs; the test shows the server's real answer

## v1.8.0 — 2026-09-29

### Added
- Chinese app language: every screen now has a 中文 version. Settings → Language: System / English / 中文, switches instantly (no restart)
- Note language per writing mode (Settings → Writing modes → a mode → Notes): 中文 or English. Each mode now has its own Chinese and English prompt, form hints and styles; English notes get English output rules and date lines like "Sun, Mar 8 · lunch". Separate from the app language
- Photo limit per writing mode (1–20, default 20); the create screen and photo picker follow it
- Tags at any depth (旅行 › 日本 › 京都), like Country → City for places: Group by Tag shows the full tree (notes sit under their deepest tag, "General" for notes with only an upper tag), filter chips open one level at a time, Manage tags shows the tree and moves a tag with everything under it. Type "日本/京都" to create two levels at once
- A mode's root tag can be a path too, e.g. 生活/咖啡

### Changed
- Places in the US, Canada and Australia group as Country → State (e.g. 美国 › 加利福尼亚州); other countries stay Country → City
- Rating groups show a level meter in the theme color and words ("Loved it" / 超喜欢) instead of ★★★★★ — stars stay on the note cards
- Tags show without "#" in the app (XHS post hashtags are unchanged)
- Settings left the ⋮ menu; it is the button at the top right
- Deleting a tag moves the tags under it up one level (not to the top)

### Notes
- No database change (still schema v4). Mode edits from v1.7 are kept; built-in names and labels now follow the app language unless you renamed them
- Backups keep the whole tag tree

## v1.7.0 — 2026-09-28

### Added
- Writing modes: pick the kind of note at the top of the new-note screen. Each mode has its own form labels, AI prompt, four style descriptions and a root tag
  - Built in, based on Xiaohongshu's largest content categories: Food (美食), Travel (旅行), Outfit (穿搭), Beauty (美妆护肤), Home (家居), Fitness (运动), Parenting (育儿), Books & Films (书影音). Each prompt keeps the "sound human" rules and names that category's typical AI clichés; Parenting also keeps children's personal details out
  - Create your own modes (Settings → Writing modes → New mode); edit any mode's name, root tag, labels, prompt and styles; reset built-ins, delete your own
- Root tags: every note gets its mode's root tag automatically; existing notes get 美食 once after updating
- Two-level tags: tags can sit under a root tag (旅行 › 京都). New tags default to the notes' root; move tags in Manage tags. Group by Tag shows root → sub-tags (+ General); filtering by a root includes its sub-tags
- Each mode remembers its favorite style; voice samples come from notes of the same mode

### Changed
- The single "Writing prompt" editor became part of each mode; a customized Food prompt is carried over
- Backups carry the tag hierarchy; older backups still import (as Food)
- Database v4 (tag parents) with a migration — verified that existing tag links are kept

## v1.6.0 — 2026-09-28

### Added
- Star ratings (1–5): a row of stars on every note card (filled when rated, outlined when not) — tap a star to rate right from the home screen; also set while creating a note, on the review screen, or for many notes at once (select → ☆). Group the home screen by rating. The AI gets your rating so the note's tone matches it
- Full street address for places (e.g. 中国上海市黄浦区中华路168号), plus the district; shown and editable on the review screen with where it came from (photo / found from text / set by you)

### Changed
- Group control is now toggles — Place · Tag · Rating; tap the active one again for a plain list (no more "None")

### Fixed
- Dark themes: group titles (country, city, tag names) were drawn in black and nearly invisible
- "Re-check all places" no longer relabels places found from text as "from photo"; postal-code suffixes are removed from addresses
- Database v3 (rating, district, address) with a migration from v1 and v2 — verified on a v2 database with tags and places

## v1.5.0 — 2026-09-27

### Added
- Tags: your own labels on notes (many per note), shown on cards and as filter chips next to Drafts / Ready / Posted; rename or delete them under ⋮ → Manage tags
- Multi-select on the home screen: long-press a note (or ⋮ → Select), tap others, then tag, change status (draft / ready / posted), set a place or delete them all at once
- Group the home screen by Place (Country → City, collapsible, with the province shown next to the city) or by Tag
- Organize by place: finds a Country → Province → City for notes without one, from photo GPS when available, otherwise from the area and restaurant name (geocoded; place names in Chinese). Places set by hand are never overwritten; "Re-check all places" redoes the automatic ones
- Read where photos were taken: optional photo permission on the create screen fills in the area from the photo's real GPS
- Backups include each note's place and tags

### Fixed
- Photo location never worked with the Android photo picker: it strips GPS from the copies apps receive (verified — the date is kept, coordinates are zeroed). Locations are now read from the original photo when you allow photo access, and zeroed coordinates are no longer mistaken for a real place

### Changed
- Database schema v2 (place columns, tags) with a migration — existing notes are kept (verified on a v1 database)

## v1.4.0 — 2026-09-27

### Added
- Photos: tap any photo for Set as cover / Move left / Move right / Remove, and a Select mode to remove several or pick a cover at once (create screen). On the review screen, long-press a photo for Set as cover / Move earlier / Move later / Leave out, plus "Include all"
- Model switcher ("Writing with …") on the create and review screens — change provider or model on the spot; each version shows which model wrote it
- Release build guard: refuses to build a release whose versionCode is lower than the last one (recorded in app/released-version-code.txt), since installing it would force an uninstall and wipe notes, API keys and the XHS login

### Fixed
- Tags now post as real XHS topics: each tag is looked up with XHS topic search and written in XHS's own "#话题[话题]#" format with its topic id; tags XHS doesn't know stay plain "#tag" text
- OpenAI "quota" errors are told apart: no API credit (ChatGPT Plus/Pro doesn't include API credit), request too large for the account's rate limit, or a temporary rate limit — with the provider's own message

## v1.3.1 — 2026-09-27

Includes everything since v1.1.0 (1.2.0 and 1.3.0 were built without separate changelog entries).

### Added
- Multiple AI providers: Gemini, ChatGPT (OpenAI), Claude, DeepSeek, and Custom (any OpenAI-compatible API) — each with its own key, built-in model list (verified Sept 2026) and a free-text model id for newer models
- Text-only models are supported: notes are written from the text without sending photos
- Nine color themes and a System / Light / Dark switch, applied instantly and remembered:
  classic Tomato, Matcha, Blueberry, Sakura, Sesame, Latte (beige); anime-inspired Summer Sky (clouds by day, stars and moon by night), City Pop (80s retro sunset) and Matsuri (seigaiha wave pattern) — all artwork is original and drawn in code
- Full Settings screen (replaces the bottom sheet); changes save immediately
- Editable writing prompt (Settings → AI writing → Writing prompt): main instructions and each style's instructions, with per-section and full reset; the JSON output format stays fixed and is appended automatically

### Fixed
- XHS login: the login page now uses the phone's real browser identity (the hard-coded "Chrome/149" user agent contradicted the engine version, which XHS security checks can flag), drops the embedded-WebView "wv" marker, supports popups and JS dialogs, accepts third-party cookies, and shows real loading progress
- XHS login: QR login is now the recommended path — verified on a real phone. SMS login from the in-app browser is answered by XHS risk control (HTTP 471 → a "scan to verify" QR), so it can hang
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
