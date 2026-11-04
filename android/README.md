# Paradox Music (Android) 1.1 (vc2)

A minimal offline music player in the College WiFi / Paradox monochrome style. Package `bot.paradox.music`, minSdk 29, target 35.

## What it does
- First launch: one setup screen (logo, "Choose your music folder", Choose folder via the system folder picker, Start). The folder grant is persisted. Start scans it recursively.
- Library: Songs / Albums (cover grid) / Artists / Playlists, search (case and accent insensitive), sort, Shuffle all.
- Formats: mp3, m4a/aac/mp4, flac, wav, ogg, opus. Tags + embedded covers are read by a small built-in reader (`scan/TagReader.kt`: ID3v2.2-2.4 + ID3v1, MP4 ilst, FLAC, Vorbis/Opus comments incl. METADATA_BLOCK_PICTURE, RIFF INFO). Android's MediaMetadataRetriever is the fallback. No tags -> "Artist - Title" / "07 - Title" from the file name; folder name as album; cover.jpg/folder.jpg as folder art.
- Rescans are incremental (unchanged files are not re-read). Hidden folders and folders with `.nomedia` are skipped. A quick rescan runs on each launch.
- Playback: Media3 ExoPlayer in a MediaSessionService. Background play, media notification, lock screen, Bluetooth/headset buttons, audio focus, pause on unplug, resume from a headset after the app was closed.
- Now playing: cover, seek bar, prev/next, shuffle, repeat off/all/one, Up next, queue (tap to jump, remove, clear). Play next / Add to queue from any song, album, artist or playlist menu.
- Local playlists: create, rename, delete, add, remove.
- Remembers the queue, the song and the position.
- Paradox extras: 2 s intro on first install and once per update (`paradox_intro_vc`), WhatsApp channel popup (once per launch, "Don't show again"), update check against GitHub releases of chinmays-tanx09/paradox-music (every 12 h, needs an `.apk` asset and a `versionCode: N` line in the notes).

## Smart playlists (1.1)
All offline, all on the phone (`data/Smart.kt`, `data/Stats.kt`, `data/Mixer.kt`, `play/PlayCounter.kt`, `ui/SmartScreens.kt`).
- Play counts + last played: `PlayCounter` in the playback service counts a play once a song has actually been heard (playing time, not seek position) for half its length or 30 s, whichever comes first; once per start, a repeat-one loop counts again. Saved in `stats.json` with likes.
- Favorites: heart on the now-playing screen, Like/Unlike in every song's ⋮ menu, and a small filled heart in song rows for liked songs (tap it to unlike).
- Playlists tab → "Smart" section: Favorites, Most played (top 50), Recently played (50), Recently added (50), Never played; "New smart playlist"; your saved smart playlists; one list per Genre (spellings like "Hip-Hop"/"hip hop" merged, multi-genre tags split on ; / , |) and per Decade, built from the tags.
- Rule builder: match All / Any of rules on Genre, Artist, Album (is / is not / contains / doesn't contain, with suggestions from the library), Year (between / from / up to), Added (in the last / not in the last N days), Play count (at least / at most / between), Last played (in the last / not in the last N days / never), Liked (yes / no), Length (longer / shorter / between, minutes); a sort (13 orders incl. Random, stable per list) and a limit; live preview count. Only the rule is saved (`smart.json`); songs are worked out again every time, so lists update as you add, play and like songs. Edit or delete from the list's ⋮ menu or the editor.
- Make a mix: from any song (⋮ → Make a mix) or artist (artist page ⋮): the artist first (up to 12), then the same genre + era (±5 years), then same genre, then same era, 40 songs max, liked songs float up. "New mix" re-rolls; "Save as playlist" turns it (or any smart list) into a normal playlist.

## Permissions
FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK (playback) and INTERNET (only the update check). No storage / READ_MEDIA_AUDIO permission: music is read through the folder the user picks. Media3's ACCESS_NETWORK_STATE is removed. No analytics, no telemetry.

## Status
**Untested on a real device by the author.** `assembleDebug` and `assembleRelease` were built successfully from this exact folder on a Linux build machine (JDK 17), and the JVM unit tests (Robolectric, including screenshot tests) passed during development; the tests were not re-run for this publish. The app has not been installed and used on a physical Android phone by the author yet. Bug reports are welcome.

## Prerequisites
- JDK 17 (the project compiles with Java/Kotlin target 17)
- Android SDK with platform 35 (`compileSdk` 35, `targetSdk` 35, `minSdk` 29 = Android 10+) and recent build-tools
- Android Studio Ladybug (2024.2.1) or newer, or just the command line (the Gradle wrapper, Gradle 8.11.1, downloads itself)
- Versions in use: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose BOM 2024.12.01, Media3 1.5.1
- Point Gradle at your SDK with a `local.properties` file in this folder (`sdk.dir=/path/to/Android/sdk`, Android Studio creates it for you) or the `ANDROID_HOME` environment variable. `local.properties` is never committed.

## Build
Run from this `android/` folder.
```
# debug APK (installable straight away, debug-signed)
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk

# release APK (minified + resource-shrunk)
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk

# unit tests and screenshot tests, lint
./gradlew :app:testReleaseUnitTest -Proborazzi.test.record=true
./gradlew :app:lintRelease
```
On Windows use `gradlew.bat`. In Android Studio: File > Open this folder, let Gradle sync, then Run or Build > Build APK(s).

## Signing
Nothing secret is in this repository. By default, **release builds are signed with the local debug key**, so anyone can build and install them; such an APK is fine for trying the app but is not a distribution key.

To sign a release with your own key, provide the values either way:
- a `keystore.properties` file in this folder (ignored by git):
  ```
  storeFile=/path/to/your.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```
- or the environment variables `PM_STORE_FILE`, `PM_STORE_PASSWORD`, `PM_KEY_ALIAS`, `PM_KEY_PASSWORD`.

Create a key with `keytool -genkeypair -v -keystore my-release.jks -alias paradox -keyalg RSA -keysize 2048 -validity 10000`. An APK signed with a different key cannot update an install signed with another key (uninstall first).

## Tests
Test audio in `app/src/test/resources/audio` was made with ffmpeg (sine tones with tags and covers); sample covers are in `covers/`.

## License
MIT, same as the rest of this repository (see the top-level [LICENSE](../LICENSE)). The bundled Nunito font is under the SIL OFL (`app/src/main/assets/Nunito-OFL.txt`).
