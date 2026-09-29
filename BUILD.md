# Building & Installing Glint (浮生拾遗)

How to build an APK and get it onto an Android phone.

## TL;DR

| Goal | Command (from `android/`) | Output |
|---|---|---|
| Quick test build | `gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` (~60 MB) |
| Build to share / keep on your phone | `gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` (~3 MB) |
| Run unit tests | `gradlew testDebugUnitTest` | `app/build/reports/tests/` |

On macOS / Linux use `bash gradlew …` (or `chmod +x gradlew` once).

**Windows (PowerShell)** — from `android/`, point `JAVA_HOME` at your JDK 17+ install once per session, then call the `.bat` wrapper:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"   # adjust to your JDK path
.\gradlew.bat assembleDebug        # or .gradlew.bat assembleRelease / .\gradlew.bat testDebugUnitTest
```

> ⚠️ **Read [Signing & updates](#signing--updates-read-this-before-installing) before installing** — installing a build signed with a different key than the one on your phone can cost you all your notes.

---

## 1. Prerequisites

- **JDK 17 or newer**
- **Android SDK** with *Android SDK Platform 35* (Android Studio installs it; or use the command-line tools)
- The phone runs **Android 10 or newer** (minSdk 29)

Tell Gradle where the SDK is — either set `ANDROID_HOME`, or put this line in `android/local.properties` (gitignored):

```properties
sdk.dir=/path/to/Android/sdk
```

Typical SDK locations: macOS `~/Library/Android/sdk`, Windows `C:\Users\<you>\AppData\Local\Android\Sdk`.

## 2. Debug build (quick test)

```bash
cd android
bash gradlew assembleDebug        # Windows: gradlew assembleDebug
```

- Output: `android/app/build/outputs/apk/debug/app-debug.apk`
- Signed with the machine's auto-generated debug key. Large (no shrinking) but fine for testing.

## 3. Release build (the one to share)

Release builds are minified with R8 and signed with the project's release key.

### One-time setup

1. The keystore lives at `.wsl-tools/keystore/xhs-release.jks` in the repo root (gitignored — **never commit it**; keep a backup somewhere safe, a lost keystore means future builds can't update installed copies).
2. Add the passwords to `android/local.properties` (gitignored):

   ```properties
   RELEASE_STORE_PASSWORD=...
   RELEASE_KEY_PASSWORD=...
   ```

### Build

```bash
cd android
bash gradlew assembleRelease      # Windows: gradlew assembleRelease
```

- Output: `android/app/build/outputs/apk/release/app-release.apk`
- Optional check that it's signed: `$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs app-release.apk`

If the build fails with a signing error, the keystore file or the password lines are missing.

## 4. Install on the phone

**Over USB (adb)** — enable *Developer options → USB debugging* on the phone, connect it, then:

```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

`-r` updates the existing app and keeps its data.

**By sending the file** — cloud drive, USB file copy, nearby share, etc. Open the APK on the phone and allow *Install unknown apps* for whichever app opened it. WeChat/QQ sometimes rename `.apk` files (e.g. `.apk.1`); rename it back if the phone won't open it.

## Signing & updates (read this before installing)

Android only updates an app if the new APK is signed with **the same key** as the installed one.

| Installed on phone | You install | Result |
|---|---|---|
| release build | release build | ✅ Updates, notes kept |
| debug build (same machine) | debug build | ✅ Updates, notes kept |
| release build | debug build (or vice versa) | ❌ "App not installed" |

The only way past ❌ is uninstalling — which **deletes every note and photo**. The app deliberately disables Android backup (the data includes your XHS login), so nothing comes back.

If you must switch keys:
1. In the app: **⋮ → Export backup** (saves note *text*; photos are not included).
2. Uninstall, install the new APK.
3. **⋮ → Import backup**.

**Rule of thumb:** keep using release builds on your real phone; use debug builds on a test device or emulator.

## 5. Before shipping a new version

1. Bump `versionCode` (must increase) and `versionName` in `android/app/build.gradle.kts`.
2. `bash gradlew testDebugUnitTest assembleRelease`
3. Changed a Room entity? Bump the database `version` and add a `Migration` — there is no destructive fallback. Commit the new file under `android/app/schemas/`.
4. Update `CHANGELOG.md`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `permission denied: ./gradlew` | Run `bash gradlew …` or `chmod +x gradlew` |
| `SDK location not found` | Set `ANDROID_HOME` or `sdk.dir` in `android/local.properties` |
| `Unsupported class file major version` / Java errors | Use JDK 17+: PowerShell `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"` |
| Release build: keystore / password error | See [One-time setup](#one-time-setup) |
| Phone: "App not installed" | Signature mismatch — see [Signing & updates](#signing--updates-read-this-before-installing) |
| Phone: "There was a problem parsing the package" | Phone is older than Android 10, or the file was corrupted/renamed in transfer |
