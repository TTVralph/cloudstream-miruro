# Miruro Cloudstream Extension

A Cloudstream extension for browsing and watching anime through Miruro-backed sources with AniList metadata.

## Features

- Search anime and anime movies with AniList titles and posters.
- Quick search support inside Cloudstream.
- Browse homepage rows for trending, popular, top-rated, recently added, and fan-favorite anime.
- Load anime details with posters, descriptions, status/year/score/genre metadata, sub/dub episode lists, episode thumbnails, and runtimes when available.
- Resolve Miruro pipe sources into Cloudstream HLS/DASH links.
- Load subtitles/tracks exposed by the source payload.
- Try equivalent episodes across available providers, with a preferred provider order to reduce broken-link fallbacks.

## Not Included

Download support is intentionally disabled for now. The extension exposes playable stream links, but `hasDownloadSupport` remains `false` until download behavior is tested safely against the supported HLS/DASH source types.

## Build

```bash
./gradlew :MiruroProvider:make
```

If you only want to validate Kotlin compilation during development, run:

```bash
./gradlew :MiruroProvider:compileDebugKotlin
```

## Install

1. Build the plugin with Gradle.
2. Copy or publish the generated Cloudstream plugin artifact from the module build output.
3. Add it to Cloudstream as a third-party extension/repository build.

## Known Notes

- Metadata comes from AniList GraphQL.
- Episodes and stream sources are requested through Miruro's secure pipe endpoint.
- Miruro source payloads can change; if playback stops working, inspect the pipe response shape first.
- Provider fallback is sorted by the hardcoded priority in `MiruroProvider.kt`, then by any remaining providers returned by Miruro.

## Project Layout

- `MiruroProvider/src/main/kotlin/com/miruro/MiruroPlugin.kt` registers the Cloudstream plugin.
- `MiruroProvider/src/main/kotlin/com/miruro/MiruroProvider.kt` contains search, homepage, metadata loading, episode mapping, subtitle loading, and stream resolving.
- `MiruroProvider/build.gradle.kts` contains the extension metadata shown to Cloudstream.
