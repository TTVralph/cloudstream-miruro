# AniStream TV and Miruro Cloudstream Extension

This repository contains:

- **AniStream TV**, a standalone Jetpack Compose Android TV app.
- A Cloudstream extension backed by Miruro sources with AniList metadata.

## Features

- Search anime and anime movies with AniList titles and posters.
- Browse trending, currently airing, seasonal, top-rated, and movie rows.
- View anime details, seasons, SUB/DUB episodes, thumbnails, dates, runtimes, and progress.
- Resolve available Miruro-backed playback sources with provider fallback.
- Load subtitles and tracks exposed by the source payload.
- Persist My List, settings, watch progress, and Continue Watching locally.
- Resume unfinished playback and mark episodes watched after 90% completion.

## Android TV development build

On Windows:

```powershell
.\gradlew.bat clean :MiruroApp:assembleDebug
```

The debug APK is written to:

```text
MiruroApp\build\outputs\apk\debug\MiruroApp-debug.apk
```

Install or replace the debug build with ADB:

```powershell
adb install -r .\MiruroApp\build\outputs\apk\debug\MiruroApp-debug.apk
```

## Android TV release signing

Release credentials are read from a local `keystore.properties` file at the repository root. The real file and common keystore formats are ignored by Git.

Create a signing key once:

```powershell
New-Item -ItemType Directory -Force release
keytool -genkeypair -v `
  -keystore release\anistream-release.jks `
  -alias anistream `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

Copy the template:

```powershell
Copy-Item keystore.properties.example keystore.properties
```

Then replace every placeholder in `keystore.properties`:

```properties
storeFile=release/anistream-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=anistream
keyPassword=YOUR_KEY_PASSWORD
```

Never commit `keystore.properties` or the keystore. Keep secure backups of both the keystore and its passwords. Losing the signing key prevents future APKs from updating the installed release.

Build the signed release APK:

```powershell
.\gradlew.bat clean :MiruroApp:assembleRelease
```

With valid signing properties, the APK is written to:

```text
MiruroApp\build\outputs\apk\release\MiruroApp-release.apk
```

Without valid signing properties, Gradle may produce an unsigned release APK instead. Do not distribute an unsigned APK.

Verify the final APK before installation or distribution:

```powershell
apksigner verify --verbose --print-certs .\MiruroApp\build\outputs\apk\release\MiruroApp-release.apk
```

A release signed with a different key cannot replace an existing debug installation. Uninstall the debug app before the first release install, or keep testing on a separate device/profile.

## Cloudstream extension build

```bash
./gradlew :MiruroProvider:make
```

To validate only the extension Kotlin compilation:

```bash
./gradlew :MiruroProvider:compileDebugKotlin
```

## Not Included

Download support is intentionally disabled. The extension exposes playable stream links, but `hasDownloadSupport` remains `false` until download behavior is tested safely against the supported HLS/DASH source types.

## Release notes

- AniStream TV currently uses version `1.0.0` with version code `1`.
- Release shrinking and resource shrinking remain disabled until Jackson and Media3 playback paths are validated with R8 on a physical TV.
- Metadata comes from AniList GraphQL.
- Episodes and stream sources are requested through Miruro's secure pipe endpoint.
- Miruro source payloads can change; if playback stops working, inspect the pipe response shape first.

## Project layout

- `MiruroApp/` contains the standalone Android TV application.
- `MiruroProvider/src/main/kotlin/com/miruro/MiruroPlugin.kt` registers the Cloudstream plugin.
- `MiruroProvider/src/main/kotlin/com/miruro/MiruroProvider.kt` contains extension search, metadata, episode mapping, subtitles, and stream resolution.
- `MiruroProvider/build.gradle.kts` contains the extension metadata shown to Cloudstream.
