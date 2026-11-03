# Paradox Music

A minimal, fully offline music player for Windows, in the same look as College WiFi.

- **First launch:** choose your music folder, press Start. Everything inside it (subfolders too) becomes your library.
- **Library:** Songs, Albums (cover grid) and Artists, search (Ctrl+F) and sort. Reads title, artist, album, track number, year and embedded cover art; untagged files are named from the file name ("07 - Artist - Title.mp3") and use a `cover.jpg` / `folder.jpg` next to them.
- **Formats:** MP3, M4A/AAC, FLAC, WAV, OGG Vorbis, Opus.
- **Player:** play/pause, previous/next, seek, volume, shuffle, repeat (off / all / one), queue with Play next and Add to queue, local playlists. Near-gapless: the next song is loaded early and starts the moment the current one ends.
- **Windows:** media keys and the Windows media overlay with title, artist and cover. Remembers the last song, position, volume and window size. Drag songs or folders onto the window to play them.
- **Folders:** Settings → Change folder / Add another folder / Rescan. Folders are watched, so new music appears on its own.
- **Private:** no account, no network calls, no telemetry. The library, covers and playlists live in `%APPDATA%\bot.paradox.music`.

Keyboard: Space play/pause · Ctrl+← / Ctrl+→ previous/next · ← / → seek 5 s · Ctrl+F search · Ctrl+B sidebar.

## Build

Tauri 2 (Rust) + plain HTML/CSS/JS, like College WiFi desktop. Pushing a tag `v*` runs `.github/workflows/build.yml`, which tests, builds the x64 and ARM64 setup .exe and .msi, attaches them to the release and lists gofile links with SHA-256 in the release notes.

Local: `npm install`, then `npm test` (queue logic + library scan tests) and `npx tauri build`.

The Rust tests use small audio files made with ffmpeg in `src-tauri/tests/fixtures/music` (MP3, M4A and FLAC with embedded 800×800 cover art, OGG and Opus with tags, an untagged WAV next to a cover.jpg).
