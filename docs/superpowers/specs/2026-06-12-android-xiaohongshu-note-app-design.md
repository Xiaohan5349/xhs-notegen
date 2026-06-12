# Android Xiaohongshu Note App Design

## Goal

Build a personal Android app for creating Xiaohongshu notes from photos and basic context. The first complete note type is food. The app should be designed so travel, concert, and museum notes can be added later without rebuilding the shared draft, review, and publishing workflow.

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

## Data Model

`NoteDraft`

- `id`
- `type`: `food` in v1; later `travel`, `concert`, `museum`
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

Future note-type extensions:

- `TravelInfo`: destination, dates, itinerary, cost, tips.
- `ConcertInfo`: artist, venue, seat or view, setlist highlights, mood.
- `MuseumInfo`: museum or exhibition name, city, favorite works, visit tips.

The draft list, AI generation, review editor, and Xiaohongshu handoff are shared across note types. Only the basic-info form and AI prompt differ by type.

## Style Defaults

The app supports default note styles without forcing the user to choose every time.

- A global preferred style applies to all note types unless overridden.
- Each note type can have its own preferred default style.
- Each draft can override the style before generation.
- Every generation returns 2-3 variants.
- The preferred style appears first and is preselected.
- The user can switch variants, edit, or regenerate before handoff.

Food v1 styles:

- Casual story: personal and narrative.
- Practical recommendation: taste, value, what to order, and who should go.
- Xiaohongshu punchy: stronger title, short sections, and searchable hashtags.
- Clean/minimal: natural tone with less promotional language.

## AI Generation Behavior

The backend returns a structured response:

- `title`
- `body`
- `hashtags`
- `styleLabel`
- `warnings`

The AI must not invent facts such as exact price, ingredients, awards, opening hours, queue time, or sponsorship unless the user provided them. Sponsored or promotional language should only be generated if the draft is explicitly marked sponsored.

The review step is mandatory before any Xiaohongshu handoff.

## Image Handling

Food v1 supports 5-20 images per note. Later note types may need larger sets:

- Travel: 10-40 images.
- Museum: 20-80 images.
- Concert: 20-100+ images.

The v1 review screen includes a thumbnail grid, photo reorder controls, and a selected-for-publish count. If Android sharing or Xiaohongshu rejects too many images, the app preserves the draft and shows a manual fallback path.

Future note types can add album grouping, best-photo selection, or batching helpers.

## Error Handling

- If AI generation fails, keep the draft and show retry.
- If image upload fails, keep local draft state and allow retry.
- If Xiaohongshu is not installed or not available as a share target, copy the note text and show manual posting instructions.
- If Android sharing returns without confirmation, keep the note status as reviewed unless the user marks it shared.

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
- Cloud AI generation through a backend.
- 2-3 generated variants with preferred style first.
- Review and edit before publishing.
- Android share handoff to Xiaohongshu.

Excluded:

- Direct Xiaohongshu API publishing.
- Accessibility automation.
- Public multi-user release.
- Full travel, concert, or museum forms.
- Automatic final publish confirmation inside Xiaohongshu.

## References

- Android app architecture: https://developer.android.com/topic/architecture
- Android Room: https://developer.android.com/training/data-storage/room
- Android Photo Picker: https://developer.android.com/training/data-storage/shared/photo-picker
- Android sharing: https://developer.android.com/training/sharing/send
- Xiaohongshu creator publish page observed during research: https://creator.xiaohongshu.com/publish/publish
