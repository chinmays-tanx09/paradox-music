//! Music library: finding audio files, reading their tags and cover art.
//! Pure functions over the filesystem, so the scan logic is unit-tested without a window.

use lofty::picture::PictureType;
use lofty::prelude::*;
use lofty::probe::Probe;
use rayon::prelude::*;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Mutex;
use std::time::UNIX_EPOCH;

/// File extensions Paradox Music plays (lower case).
pub const AUDIO_EXTS: &[&str] = &["mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus"];

/// Image names that count as an album cover when a song has no embedded art.
const FOLDER_ART: &[&str] = &[
    "cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.jpeg", "folder.png",
    "front.jpg", "front.jpeg", "front.png", "album.jpg", "album.png", "albumart.jpg",
];

/// Folder names never worth walking into.
const SKIP_DIRS: &[&str] = &["$recycle.bin", "system volume information", "node_modules", ".git"];

const COVER_MAX: u32 = 600;

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Default)]
#[serde(default)]
pub struct Track {
    pub id: String,
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub track: u32,
    pub disc: u32,
    pub year: u32,
    /// Seconds.
    pub duration: f64,
    /// File name inside the covers folder, if the song has art.
    pub cover: Option<String>,
    pub size: u64,
    /// Modified time, seconds since the epoch.
    pub mtime: u64,
    /// When this song first showed up in the library, seconds since the epoch.
    pub added: u64,
    pub format: String,
    /// True when title/artist came from the file name, not from tags.
    pub from_filename: bool,
}

pub fn is_audio(p: &Path) -> bool {
    p.extension()
        .and_then(|e| e.to_str())
        .map(|e| AUDIO_EXTS.contains(&e.to_ascii_lowercase().as_str()))
        .unwrap_or(false)
}

pub fn hash_hex(bytes: &[u8], n: usize) -> String {
    let d = Sha256::digest(bytes);
    d.iter().take(n / 2).map(|b| format!("{:02x}", b)).collect()
}

pub fn track_id(path: &str) -> String {
    hash_hex(path.as_bytes(), 16)
}

fn now_secs() -> u64 {
    std::time::SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

/// Every audio file under the given roots (files given directly are kept as they are).
/// Sorted, de-duplicated, skips system folders and unreadable entries.
pub fn collect_files(roots: &[PathBuf]) -> Vec<PathBuf> {
    let mut seen = HashSet::new();
    let mut out = Vec::new();
    for root in roots {
        if root.is_file() {
            if is_audio(root) && seen.insert(root.clone()) {
                out.push(root.clone());
            }
            continue;
        }
        let walker = walkdir::WalkDir::new(root).follow_links(true).into_iter().filter_entry(|e| {
            if e.depth() == 0 || !e.file_type().is_dir() {
                return true;
            }
            let n = e.file_name().to_string_lossy().to_ascii_lowercase();
            !SKIP_DIRS.contains(&n.as_str())
        });
        for e in walker.flatten() {
            if e.file_type().is_file() && is_audio(e.path()) {
                let p = e.path().to_path_buf();
                if seen.insert(p.clone()) {
                    out.push(p);
                }
            }
        }
    }
    out.sort();
    out
}

/// "03 - Artist - Title" → (Some(3), Some("Artist"), "Title"); "Title" → (None, None, "Title").
pub fn parse_filename(stem: &str) -> (Option<u32>, Option<String>, String) {
    let s = stem.replace('_', " ");
    let mut s = s.trim();
    let mut track = None;
    // leading track number: "03 ", "03. ", "03 - ", "3-"
    let digits: String = s.chars().take_while(|c| c.is_ascii_digit()).collect();
    if (1..=3).contains(&digits.len()) {
        let after = &s[digits.len()..];
        if after.starts_with(|c: char| c == ' ' || c == '.' || c == '-' || c == ')') {
            let rest = after.trim_start_matches(|c: char| c == ' ' || c == '.' || c == '-' || c == ')');
            if !rest.is_empty() {
                track = digits.parse().ok();
                s = rest;
            }
        }
    }
    if let Some((a, t)) = s.split_once(" - ") {
        let (a, t) = (a.trim(), t.trim());
        if !a.is_empty() && !t.is_empty() {
            return (track, Some(a.to_string()), t.to_string());
        }
    }
    (track, None, s.to_string())
}

fn clean(s: Option<std::borrow::Cow<'_, str>>) -> Option<String> {
    s.map(|v| v.trim().trim_matches('\0').to_string()).filter(|v| !v.is_empty())
}

/// Cover cache: one file per distinct image, so an album's art is stored once.
pub struct Covers {
    dir: PathBuf,
    folder_art: Mutex<HashMap<PathBuf, Option<String>>>,
}

impl Covers {
    pub fn new(dir: PathBuf) -> Self {
        let _ = std::fs::create_dir_all(&dir);
        Covers { dir, folder_art: Mutex::new(HashMap::new()) }
    }
    pub fn dir(&self) -> &Path {
        &self.dir
    }

    /// Stores image bytes (resized to at most 600 px) and returns the file name.
    pub fn store(&self, data: &[u8]) -> Option<String> {
        if data.len() < 16 {
            return None;
        }
        let name = format!("{}.jpg", hash_hex(data, 24));
        let path = self.dir.join(&name);
        if path.exists() {
            return Some(name);
        }
        let img = image::load_from_memory(data).ok()?;
        let img = if img.width() > COVER_MAX || img.height() > COVER_MAX {
            img.resize(COVER_MAX, COVER_MAX, image::imageops::FilterType::Triangle)
        } else {
            img
        };
        let rgb = img.to_rgb8();
        // unique temp name: several scan threads may store the same album art at once
        static SEQ: AtomicUsize = AtomicUsize::new(0);
        let tmp = self.dir.join(format!("{}.{}-{}.tmp", name, std::process::id(), SEQ.fetch_add(1, Ordering::Relaxed)));
        let written = (|| {
            let mut f = std::fs::File::create(&tmp).ok()?;
            let mut enc = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut f, 88);
            enc.encode_image(&rgb).ok()?;
            drop(f);
            std::fs::rename(&tmp, &path).ok()
        })();
        if written.is_none() {
            let _ = std::fs::remove_file(&tmp);
        }
        path.exists().then_some(name)
    }

    /// cover.jpg / folder.jpg next to the song, looked up once per folder.
    pub fn folder_cover(&self, song: &Path) -> Option<String> {
        let dir = song.parent()?.to_path_buf();
        if let Some(v) = self.folder_art.lock().unwrap().get(&dir) {
            return v.clone();
        }
        let mut found = None;
        if let Ok(rd) = std::fs::read_dir(&dir) {
            let mut imgs: Vec<PathBuf> = rd.flatten().map(|e| e.path()).filter(|p| p.is_file()).collect();
            imgs.sort();
            for want in FOLDER_ART {
                if let Some(p) = imgs.iter().find(|p| {
                    p.file_name().map(|n| n.to_string_lossy().to_ascii_lowercase() == *want).unwrap_or(false)
                }) {
                    if let Ok(bytes) = std::fs::read(p) {
                        found = self.store(&bytes);
                        if found.is_some() {
                            break;
                        }
                    }
                }
            }
        }
        self.folder_art.lock().unwrap().insert(dir, found.clone());
        found
    }
}

fn file_stamp(p: &Path) -> (u64, u64) {
    match std::fs::metadata(p) {
        Ok(m) => {
            let mt = m.modified().ok().and_then(|t| t.duration_since(UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0);
            (m.len(), mt)
        }
        Err(_) => (0, 0),
    }
}

/// Reads one file's tags, duration and cover. Never fails: a file lofty can't read
/// still becomes a song named after its file.
pub fn read_track(path: &Path, covers: &Covers) -> Track {
    let ps = path.to_string_lossy().to_string();
    let (size, mtime) = file_stamp(path);
    let format = path.extension().and_then(|e| e.to_str()).unwrap_or("").to_ascii_lowercase();
    let mut t = Track { id: track_id(&ps), path: ps, size, mtime, added: now_secs(), format, ..Default::default() };

    let mut title = None;
    let mut artist = None;
    let mut cover = None;
    if let Ok(tagged) = Probe::open(path).and_then(|p| p.guess_file_type().map_err(Into::into)).and_then(|p| p.read()) {
        t.duration = tagged.properties().duration().as_secs_f64();
        if let Some(tag) = tagged.primary_tag().or_else(|| tagged.first_tag()) {
            title = clean(tag.title());
            artist = clean(tag.artist());
            t.album = clean(tag.album()).unwrap_or_default();
            t.album_artist = tag.get_string(ItemKey::AlbumArtist).map(|s| s.trim().to_string()).unwrap_or_default();
            t.track = tag.track().unwrap_or(0);
            t.disc = tag.disk().unwrap_or(0);
            t.year = tag.date().map(|d| d.year as u32).unwrap_or(0);
            let pics = tag.pictures();
            let pic = pics.iter().find(|p| p.pic_type() == PictureType::CoverFront).or_else(|| pics.first());
            if let Some(p) = pic {
                cover = covers.store(p.data());
            }
        }
        // some files carry art only in a secondary tag
        if cover.is_none() {
            for tag in tagged.tags() {
                if let Some(p) = tag.pictures().first() {
                    cover = covers.store(p.data());
                    if cover.is_some() {
                        break;
                    }
                }
            }
        }
    }
    let stem = path.file_stem().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    let (ftrack, fartist, ftitle) = parse_filename(&stem);
    if title.is_none() {
        t.from_filename = true;
        if t.track == 0 {
            t.track = ftrack.unwrap_or(0);
        }
    }
    t.title = title.unwrap_or(ftitle);
    t.artist = artist.or(fartist).unwrap_or_else(|| "Unknown artist".to_string());
    if t.album.is_empty() {
        t.album = path
            .parent()
            .and_then(|p| p.file_name())
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_else(|| "Unknown album".to_string());
    }
    t.cover = cover.or_else(|| covers.folder_cover(path));
    t
}

/// Scans the roots. Files whose size and modified time match the previous scan are
/// reused as they were, so a rescan of an unchanged library reads no tags.
pub fn scan(roots: &[PathBuf], covers: &Covers, previous: &[Track], progress: &(dyn Fn(usize, usize) + Sync)) -> Vec<Track> {
    let files = collect_files(roots);
    let prev: HashMap<&str, &Track> = previous.iter().map(|t| (t.path.as_str(), t)).collect();
    let total = files.len();
    let done = AtomicUsize::new(0);
    progress(0, total);
    let mut out: Vec<Track> = files
        .par_iter()
        .map(|p| {
            let ps = p.to_string_lossy();
            let (size, mtime) = file_stamp(p);
            let cover_ok = |t: &Track| t.cover.as_ref().map(|c| covers.dir().join(c).exists()).unwrap_or(true);
            let t = match prev.get(ps.as_ref()) {
                Some(old) if old.size == size && old.mtime == mtime && cover_ok(old) => (*old).clone(),
                Some(old) => {
                    let mut t = read_track(p, covers);
                    t.added = old.added;
                    t
                }
                None => read_track(p, covers),
            };
            let n = done.fetch_add(1, Ordering::Relaxed) + 1;
            if n % 25 == 0 || n == total {
                progress(n, total);
            }
            t
        })
        .collect();
    out.sort_by(|a, b| a.path.cmp(&b.path));
    out
}

/// Deletes cover files no song points to any more.
pub fn prune_covers(covers: &Covers, tracks: &[Track]) -> usize {
    let used: HashSet<&str> = tracks.iter().filter_map(|t| t.cover.as_deref()).collect();
    let mut n = 0;
    if let Ok(rd) = std::fs::read_dir(covers.dir()) {
        for e in rd.flatten() {
            let name = e.file_name().to_string_lossy().to_string();
            if !used.contains(name.as_str()) && std::fs::remove_file(e.path()).is_ok() {
                n += 1;
            }
        }
    }
    n
}
