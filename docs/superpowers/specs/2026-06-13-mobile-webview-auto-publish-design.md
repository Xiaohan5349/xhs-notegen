# Mobile WebView Auto-Publish Design

## Goal

Build mobile-only Xiaohongshu auto-publish without a local or cloud automation service.
The Android app will automate the real Xiaohongshu creator web page in a visible WebView so Xiaohongshu's own JavaScript performs request signing and upload.

## Background

The direct API replay approach failed at the upload permit step with HTTP 406 because Xiaohongshu requires per-request signatures such as `X-s`, `X-S-Common`, and `X-t`.
Those values depend on the current URL, body, timestamp, cookies, and browser runtime state.
Copying HAR requests or reusing captured headers is not reliable.

Working public projects use one of two patterns:

- Browser/page automation: open the Xiaohongshu creator page, upload files through the page file input, fill fields, click publish, and wait for the success page.
- Reverse-engineered API signing: use a library or browser helper to generate request signatures for each API call.

For this Android app, browser/page automation is the first mobile-only path because Kotlin WebView can run the real creator page and Android can provide files through `WebChromeClient.onShowFileChooser`.

## Chosen Approach

Use visible WebView automation first.

The app will open:

```text
https://creator.xiaohongshu.com/publish/publish?from=homepage&target=image
```

The user can see the page during automation. This makes login, upload progress, selector failures, and Xiaohongshu UI changes easier to debug.
After this is reliable, a later version may move toward hidden or less-visible automation.

## User Flow

1. User reviews a generated note in the app.
2. User taps publish.
3. If Xiaohongshu creator cookies are missing or expired, the app opens the creator login WebView.
4. The app opens the visible publish WebView.
5. The app injects JavaScript to locate and click the image upload input.
6. Android WebView receives the file chooser request and returns the selected local image `Uri`s.
7. Xiaohongshu's page uploads images using its own JavaScript and signatures.
8. The app injects JavaScript to fill title, body, and hashtags.
9. The app clicks the publish button.
10. The app waits for a success URL or success page text.
11. On confirmed success, the app marks the draft as shared.
12. On failure or timeout, the app shows the visible WebView and a useful error so the user can finish manually.

## Architecture

### Publish Orchestrator

`XiaohongshuSharePublisher.publish(...)` will become the entry point for WebView automation.
It will prepare:

- selected image URIs
- selected variant title
- body
- hashtags

It will call a WebView automation bridge instead of the current manual handoff path.
Manual handoff remains as a fallback.

### WebView Automation Bridge

`XhsPublishBridge` will stop using direct upload-permit and note APIs.
It will own:

- a visible/attached WebView
- publish result state
- pending image URI list for file chooser
- page load detection
- JavaScript injection
- sanitized logging

It will expose a suspend function similar to:

```kotlin
suspend fun publishViaPage(
    context: Context,
    imageUris: List<Uri>,
    title: String,
    body: String,
    hashtags: List<String>,
): BridgeResult
```

### File Chooser Bridge

Android WebView cannot let JavaScript set file inputs directly.
The app will use `WebChromeClient.onShowFileChooser(...)` to respond when Xiaohongshu's page opens the image picker.

The bridge will store pending image URIs before clicking the upload input.
When `onShowFileChooser` fires, it will return those URIs through the `ValueCallback<Array<Uri>>`.

### Visible Publish Screen

A dedicated publish automation screen will host the WebView full-screen or nearly full-screen.
It will show a small progress/status bar above or below the WebView:

- Loading creator page
- Waiting for upload input
- Uploading images
- Filling note
- Publishing
- Success
- Needs login
- Manual finish needed

The WebView remains visible so the user can see and recover from unexpected page states.

## JavaScript Automation Strategy

The injected script will use resilient selector groups, not one brittle selector.

For upload:

- find `input[type="file"][accept*="image"]`
- fallback to creator upload input classes seen in public implementations
- click the input

For title:

- find `input[placeholder*="填写标题"]`
- fallback to visible text input near title area

For body:

- find `p[data-placeholder*="输入正文描述"]`
- fallback to contenteditable editor areas

For publish:

- find visible button text `发布`
- avoid clicking `定时发布` unless explicitly supported later

For success:

- wait for URL containing `/publish/success`
- fallback to success text on page

## Error Handling

The app will not mark a draft as shared unless publish success is confirmed.

If automation cannot find an element, upload does not start, publish times out, or the page redirects to login:

- complete with an error result
- keep the WebView visible
- show the latest status
- keep text copied and image fallback available for manual completion

## Security And Logging

Logs must not contain cookies or signature headers.
The existing log redaction remains.
New automation logs should include selectors and state transitions, not sensitive request values.

HAR files remain ignored by git.

## Scope For This Implementation

Included:

- visible WebView publish screen
- WebChromeClient file chooser bridge
- page-based image upload
- title/body/hashtag fill
- publish click
- success detection
- manual handoff fallback
- build/test verification and reinstall on connected phone

Not included:

- hidden WebView publishing
- scheduled publishing
- location/product tags
- reverse-engineered direct API signing
- backend Playwright service

## Success Criteria

- On a connected Android phone, tapping publish opens the Xiaohongshu creator publish page in-app.
- If logged in, the page receives the selected images through WebView file chooser.
- The title/body/hashtags are filled by the app.
- The app clicks publish.
- The app only marks the draft shared after a success URL or success page is detected.
- If automation fails, the user can see where it stopped and finish manually.
