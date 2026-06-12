# Android Xiaohongshu Note App Design

## Goal

Build a personal Android app for creating Xiaohongshu notes from photos and basic context. The first complete note type is food. The app should be designed so other note types (e.g., travel, concert, museum, sports matches, and more) can be added later without rebuilding the shared draft, review, and publishing workflow.

The v1 publishing model is review-first handoff: the app generates note drafts, the user reviews and edits one, then the app opens Xiaohongshu through Android sharing with the selected images and text. The final publish confirmation happens inside Xiaohongshu.

## Assumptions

- The app is for personal use on Android phones such as Samsung and Xiaomi.
- The Xiaohongshu app is installed on the same phone and the user is already logged in.
- Cloud AI services are acceptable.
- There is no confirmed public Xiaohongshu personal-account publishing API for v1.
- Android sharing can hand images and text to Xiaohongshu, but Xiaohongshu decides how much of that content it accepts and where it lands in its composer.

## V1 User Flow

1. Add 5-20 food photos from the Android Photo Picker.
2. Enter basic food context: dish name, restaurant name, location, taste notes, price or rating if desired, and atmosphere notes.
3. Generate AI note variants.
4. Review the generated title, body, hashtags, and selected photos.
5. Edit text, reorder photos, and choose the variant to use.
6. Tap publish handoff.
7. The app copies text as a fallback and opens Android sharing to Xiaohongshu with the selected images and generated text.
8. The user performs final confirmation inside Xiaohongshu.

## Architecture

The Android app uses Kotlin and Jetpack Compose. It follows a simple layered structure:

- UI layer: Compose screens and ViewModels for draft list, create form, generation, review editor, style settings, and publish handoff.
- Data layer: Room stores note drafts, selected photo references, style preferences, and generation results.
- AI client: the Android app calls a small backend instead of calling the AI provider directly.
- Publisher interface: a small boundary for publishing behavior. V1 implements `XiaohongshuSharePublisher` using Android sharing. A future `XiaohongshuApiPublisher` can be added if official API access becomes available.

The backend has one main generation endpoint. It receives note type, structured metadata, selected images or image references, and style preferences. It returns structured note variants.

### Backend

- **Technology**: Python/FastAPI
- **AI Provider**: Google Gemini (multimodal, supports base64 images)
- **Project layout**: Monorepo — `android/` and `backend/` folders in this repository

### Backend Generation Flow

1. Validate input (5-20 images, required fields)
2. Resolve styles: preferred first, then 2 others from remaining pool
3. Fire 3 parallel Gemini calls (one per style) with the same prompt template + style-specific instructions
4. Collect results, validate JSON schema, attach warnings
5. Return variants array (2-3). If one fails, return the 2 that succeeded.
6. If all 3 fail → 502 with error details.

## Data Model

`NoteDraft`

- `id`
- `type`: `food` in v1; open-ended — later `travel`, `concert`, `museum`, `sports_match`, or any new category
- `status`: `draft`, `generated`, `reviewed`, `shared`
- `photoUris`
- `selectedPublishPhotoUris`
- `title`
- `body`
- `hashtags`
- `createdAt`
- `updatedAt`

`FoodInfo`

- `dishName`
- `restaurantName`
- `location`
- `tasteNotes`
- `priceOrRating`
- `vibeNotes`
- `personalNotes`

Future note-type extensions (open-ended — these are examples, not an exhaustive list):

- `TravelInfo`: destination, dates, itinerary, cost, tips.
- `ConcertInfo`: artist, venue, seat or view, setlist highlights, mood.
- `MuseumInfo`: museum or exhibition name, city, favorite works, visit tips.
- `SportsMatchInfo`: teams/players, sport type, venue, score, key moments, atmosphere.
- Any other note type can be added by defining its metadata table and AI prompt template.

The draft list, AI generation, review editor, and Xiaohongshu handoff are shared across note types. Only the basic-info form and AI prompt differ by type.

## Style Defaults

The app supports default note styles without forcing the user to choose every time.

- A global preferred style applies to all note types unless overridden.
- Each note type can have its own preferred default style.
- Each draft can override the style before generation.
- Every generation returns 2-3 variants.
- The preferred style appears first and is preselected.
- The user can switch variants, edit, or regenerate before handoff.

Style resolution logic: per-type preference → global default ("all") → hardcoded fallback ("casual_story").

Food v1 styles:

- Casual story: personal and narrative.
- Practical recommendation: taste, value, what to order, and who should go.
- Xiaohongshu punchy: stronger title, short sections, and searchable hashtags. High energy but no unsupported hype.
- Clean/minimal: natural tone with less promotional language.

## AI Generation Behavior

### Gemini System Prompt

```
You are a Xiaohongshu food note writer. Create authentic Chinese social-media style food notes.

Write in Simplified Chinese unless the user asks for another language.

Use only:
- the structured details provided by the user
- safe visual observations from uploaded photos (color, plating, portion appearance, atmosphere shown in the image)

Do NOT invent:
- exact prices, ingredients, awards, opening hours, queue times
- restaurant popularity, sponsorship, service quality
- location details not provided
- anything not visible or provided

If the user did not mark the note as sponsored, avoid promotional/ad-like language.

Return valid JSON only. No markdown. No explanation.
```

### JSON Response Schema

```json
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
```

Generate 2-3 variants. Put the user's preferred style first.

### Style Definitions

- **Casual Story**: Personal narrative. "I went to..." tone. Emoji-friendly. Like telling a friend. Focus on experience and feeling.
- **Practical**: Taste, value, what to order, who to bring. Bullet points are okay. Helpful and informative. Focus on "is it worth it?"
- **XHS Punchy**: Strong hook title. Short sections with emoji headers. Search-optimized hashtags. High energy, but do not exaggerate unsupported facts.
- **Clean/Minimal**: Natural tone. Less promotional. Simple structure. Honest description. "Here's what it is," not "you must go."

The AI must not invent facts such as exact price, ingredients, awards, opening hours, queue time, or sponsorship unless the user provided them. Visual observations from photos (color, plating, portion, atmosphere) are allowed. Sponsored or promotional language should only be generated if the draft is explicitly marked sponsored.

The review step is mandatory before any Xiaohongshu handoff.

## Image Handling

Food v1 supports 5-20 images per note. Later note types may need larger sets:

- Travel: 10-40 images.
- Museum: 20-80 images.
- Concert: 20-100+ images.

The v1 review screen includes a thumbnail grid, photo reorder controls, and a selected-for-publish count. All photos are uploaded in a single batch request (parallel compression, one POST /generate).

Images are compressed on-device to 1024px JPEG quality 85% before upload. The Android Photo Picker is used (no storage permissions needed — system UI component, permissionless read-only access to user-selected photos).

If Android sharing or Xiaohongshu rejects too many images, the app preserves the draft and shows a manual fallback path.

Future note types can add album grouping, best-photo selection, or batching helpers.

## Error Handling

- If AI generation fails, keep the draft and show retry.
- If image compression fails for a single image, skip it and warn the user.
- If Xiaohongshu is not installed or not available as a share target, copy the note text and show manual posting instructions.
- If Android sharing returns without confirmation, keep the note status as reviewed unless the user marks it shared.
- The draft is never lost. Every error path preserves the local draft in Room.

### HTTP Error Codes (Backend)

- 422: Invalid input (missing required fields, wrong image count)
- 413: Images too large or too many
- 429: Gemini rate limited (include retry-after)
- 502: Gemini API error (logged, return error details)
- 200 with warnings: Partial success (2 variants returned, 1 failed)

## Testing

Android:

- Unit tests for draft state changes, style default resolution, and data mapping.
- UI tests for create, generate, review, and publish handoff.
- Manual device testing on at least one Samsung device and one Xiaomi-style Android environment if available.

Backend:

- API tests for valid food input.
- API tests for missing optional fields.
- API tests for food image-count boundaries.
- Prompt tests using sample food notes to verify the AI does not invent unsupported details.

## V1 Scope

Included:

- Personal Android app.
- Food-note creation.
- 5-20 image selection for food drafts.
- Cloud AI generation through a FastAPI backend with Gemini.
- 2-3 generated variants with preferred style first.
- Review and edit before publishing.
- Android share handoff to Xiaohongshu.

Excluded:

- Direct Xiaohongshu API publishing.
- Accessibility automation.
- Public multi-user release.
- Non-food note types (travel, concert, museum, sports, etc. — designed for but not implemented in v1).
- Automatic final publish confirmation inside Xiaohongshu.

## References

- Android app architecture: https://developer.android.com/topic/architecture
- Android Room: https://developer.android.com/training/data-storage/room
- Android Photo Picker: https://developer.android.com/training/data-storage/shared/photo-picker
- Android sharing: https://developer.android.com/training/sharing/send
- Xiaohongshu creator publish page observed during research: https://creator.xiaohongshu.com/publish/publish
