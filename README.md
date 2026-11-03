# Paradox Music

A minimal, fully offline music player for Windows, in the same look as College WiFi.

- **First launch:** choose your music folder, press Start. Everything inside it (subfolders too) becomes your library.
- **Library:** Songs, Albums (cover grid) and Artists, search (Ctrl+F) and sort. Reads title, artist, album, track number, year and embedded cover art; untagged files are named from the file name ("07 - Artist - Title.mp3") and use a `cover.jpg` / `folder.jpg` next to them.
- **Formats:** MP3, M4A/AAC, FLAC, WAV, OGG Vorbis, Opus.
- **Player:** play/pause, previous/next, seek, volume, shuffle, repeat (off / all / one), queue with Play next and Add to queue, local playlists. Near-gapless: the next song is loaded early and starts the moment the current one ends.
- **Windows:** media keys and the Windows media overlay with title, artist and cover. Remembers the last song, position, volume and window size. Drag songs or folders onto the window to play them.
- **Smart playlists (1.1):** a Smart page and a Smart playlists section in the sidebar.
  - Lists that keep themselves up to date: Favorites, Most played, Recently played, Recently added, Never played, plus one per genre and one per decade from the tags ("Hip-Hop" and "hip hop" are one genre; "Pop; Dance" is both).
  - A heart on every song: on the player bar, in the right-click menu, on Now playing, and filled on liked rows.
  - New smart playlist: match all or any of genre, artist, album, year, added, play count, last played, liked and length; a sort order, a limit and a live count. Only the rules are saved, so the list updates by itself. Edit or delete any time.
  - Make a mix from any song or artist (right-click, the artist page or Now playing): the same artist first, then the same genre and era. Save it as a normal playlist.
  - Play counts and last played are kept on this PC. A play counts after half the song or 30 seconds of real listening; pausing and seeking don't add to it.
- **Folders:** Settings → Change folder / Add another folder / Rescan. Folders are watched, so new music appears on its own.
- **Private:** no account, no network calls, no telemetry. The library, covers, playlists, smart playlists (`smart.json`) and play counts and likes (`stats.json`) live in `%APPDATA%\bot.paradox.music`.

Keyboard: Space play/pause · Ctrl+← / Ctrl+→ previous/next · ← / → seek 5 s · Ctrl+F search · Ctrl+B sidebar.

## Build

Tauri 2 (Rust) + plain HTML/CSS/JS, like College WiFi desktop. Pushing a tag `v*` runs `.github/workflows/build.yml`, which tests, builds the x64 and ARM64 setup .exe and .msi, attaches them to the release and lists gofile links with SHA-256 in the release notes.

Local: `npm install`, then `npm test` (queue logic, smart playlist rules, play counting and mixes, and library scan tests) and `npx tauri build`.

The Rust tests use small audio files made with ffmpeg in `src-tauri/tests/fixtures/music` (MP3, M4A and FLAC with embedded 800×800 cover art, OGG and Opus with tags, an untagged WAV next to a cover.jpg).
