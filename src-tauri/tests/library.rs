//! Library scanning and tag reading, against small real audio files made with ffmpeg
//! (tests/fixtures/music: mp3, m4a, flac with embedded 800x800 cover art; ogg, opus with tags;
//! an untagged wav named "07 - Nila Rao - Window Seat.wav" next to a cover.jpg; a notes.txt;
//! and a $RECYCLE.BIN folder that must be skipped).

use paradox_music_lib::library::{self, Covers, Track};
use std::path::{Path, PathBuf};

fn fixture_copy() -> (tempfile::TempDir, PathBuf) {
    let tmp = tempfile::tempdir().unwrap();
    let dst = tmp.path().join("My Music");
    copy_dir(&Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/music"), &dst);
    (tmp, dst)
}

fn copy_dir(src: &Path, dst: &Path) {
    std::fs::create_dir_all(dst).unwrap();
    for e in std::fs::read_dir(src).unwrap().flatten() {
        let to = dst.join(e.file_name());
        if e.file_type().unwrap().is_dir() {
            copy_dir(&e.path(), &to);
        } else {
            std::fs::copy(e.path(), to).unwrap();
        }
    }
}

fn by_title<'a>(ts: &'a [Track], title: &str) -> &'a Track {
    ts.iter().find(|t| t.title == title).unwrap_or_else(|| panic!("no song titled {title}: {:?}", ts.iter().map(|t| &t.title).collect::<Vec<_>>()))
}

fn scan_all(root: &Path, covers: &Covers, prev: &[Track]) -> Vec<Track> {
    library::scan(&[root.to_path_buf()], covers, prev, &|_, _| {})
}

#[test]
fn finds_audio_recursively_and_skips_the_rest() {
    let (_t, root) = fixture_copy();
    let files = library::collect_files(&[root.clone()]);
    let names: Vec<String> = files.iter().map(|p| p.file_name().unwrap().to_string_lossy().to_string()).collect();
    assert_eq!(files.len(), 6, "{names:?}");
    assert!(names.contains(&"deep.ogg".to_string()), "nested folders are walked");
    assert!(!names.iter().any(|n| n == "deleted.mp3"), "$RECYCLE.BIN is skipped");
    assert!(!names.iter().any(|n| n.ends_with(".txt") || n.ends_with(".jpg")));
    // overlapping roots do not duplicate songs
    let again = library::collect_files(&[root.clone(), root.join("Northbound")]);
    assert_eq!(again.len(), 6);
}

#[test]
fn reads_tags_from_every_format() {
    let (t, root) = fixture_copy();
    let covers = Covers::new(t.path().join("covers"));
    let ts = scan_all(&root, &covers, &[]);
    assert_eq!(ts.len(), 6);

    let mp3 = by_title(&ts, "Morning Light");
    assert_eq!((mp3.artist.as_str(), mp3.album.as_str(), mp3.album_artist.as_str()), ("Aria Sen", "Northbound", "Aria Sen"));
    assert_eq!((mp3.track, mp3.year, mp3.format.as_str()), (1, 2021, "mp3"));

    let m4a = by_title(&ts, "City Rain");
    assert_eq!((m4a.artist.as_str(), m4a.album.as_str(), m4a.track, m4a.year), ("Aria Sen", "Northbound", 2, 2021));

    let flac = by_title(&ts, "Glass");
    assert_eq!((flac.artist.as_str(), flac.album.as_str(), flac.track, flac.disc), ("Kavi", "Lossless", 3, 1));

    let ogg = by_title(&ts, "Deep");
    assert_eq!((ogg.artist.as_str(), ogg.album.as_str(), ogg.track), ("Kavi", "Lossless", 4));

    let opus = by_title(&ts, "Voice Memo");
    assert_eq!((opus.artist.as_str(), opus.album.as_str(), opus.format.as_str()), ("Chinmay", "Memos", "opus"));

    for t in &ts {
        assert!((t.duration - 1.0).abs() < 0.2, "{} lasts {}", t.title, t.duration);
        assert!(!t.from_filename || t.format == "wav", "{} should come from tags", t.title);
        assert_eq!(t.id, library::track_id(&t.path));
    }
}

#[test]
fn untagged_file_falls_back_to_its_name_and_folder_cover() {
    let (t, root) = fixture_copy();
    let covers = Covers::new(t.path().join("covers"));
    let ts = scan_all(&root, &covers, &[]);
    let wav = by_title(&ts, "Window Seat");
    assert!(wav.from_filename);
    assert_eq!((wav.artist.as_str(), wav.track), ("Nila Rao", 7));
    assert_eq!(wav.album, "Untagged", "album falls back to the folder name");
    let c = wav.cover.as_ref().expect("cover.jpg next to the song is used");
    let img = image::open(covers.dir().join(c)).unwrap();
    assert_eq!((img.width(), img.height()), (300, 300));
}

#[test]
fn embedded_covers_are_extracted_resized_and_shared() {
    let (t, root) = fixture_copy();
    let covers = Covers::new(t.path().join("covers"));
    let ts = scan_all(&root, &covers, &[]);
    let (mp3, m4a, flac) = (by_title(&ts, "Morning Light"), by_title(&ts, "City Rain"), by_title(&ts, "Glass"));
    let c = mp3.cover.clone().expect("mp3 cover");
    assert_eq!(m4a.cover.as_ref(), Some(&c), "same picture → one cover file");
    assert_eq!(flac.cover.as_ref(), Some(&c));
    let img = image::open(covers.dir().join(&c)).unwrap();
    assert_eq!((img.width(), img.height()), (600, 600), "800px art is stored at 600px");
    assert!(by_title(&ts, "Deep").cover.is_none());
    assert!(by_title(&ts, "Voice Memo").cover.is_none());
    let n = std::fs::read_dir(covers.dir()).unwrap().count();
    assert_eq!(n, 2, "one embedded cover + one folder cover");
}

#[test]
fn rescan_reuses_unchanged_files_and_rereads_changed_ones() {
    let (t, root) = fixture_copy();
    let covers = Covers::new(t.path().join("covers"));
    let first = scan_all(&root, &covers, &[]);
    // mark the previous scan so we can tell reuse from re-reading
    let mut prev = first.clone();
    for p in prev.iter_mut() {
        p.title = format!("cached:{}", p.title);
        p.added = 12345;
    }
    // touch one file: new modified time
    let flac = root.join("Lossless/glass.flac");
    let f = std::fs::OpenOptions::new().write(true).open(&flac).unwrap();
    f.set_modified(std::time::SystemTime::now() + std::time::Duration::from_secs(3600)).unwrap();
    drop(f);
    let second = scan_all(&root, &covers, &prev);
    assert_eq!(second.len(), 6);
    for t in &second {
        if t.path.ends_with("glass.flac") {
            assert_eq!(t.title, "Glass", "changed file is read again");
        } else {
            assert!(t.title.starts_with("cached:"), "unchanged file {} was re-read", t.path);
        }
        assert_eq!(t.added, 12345, "first-seen time survives rescans");
    }
    // a deleted file disappears, a new one appears
    std::fs::remove_file(root.join("Voice/memo.opus")).unwrap();
    std::fs::copy(root.join("Lossless/deep/nested/deep.ogg"), root.join("Voice/copy.ogg")).unwrap();
    let third = scan_all(&root, &covers, &second);
    assert!(third.iter().all(|t| !t.path.ends_with("memo.opus")));
    assert!(third.iter().any(|t| t.path.ends_with("copy.ogg")));
}

#[test]
fn prune_removes_unused_covers() {
    let (t, root) = fixture_copy();
    let covers = Covers::new(t.path().join("covers"));
    let ts = scan_all(&root, &covers, &[]);
    std::fs::write(covers.dir().join("orphan.jpg"), b"x").unwrap();
    assert_eq!(library::prune_covers(&covers, &ts), 1);
    assert_eq!(std::fs::read_dir(covers.dir()).unwrap().count(), 2);
}

#[test]
fn a_broken_file_still_shows_up() {
    let (t, root) = fixture_copy();
    std::fs::write(root.join("Untagged/broken.mp3"), b"this is not an mp3 at all").unwrap();
    let covers = Covers::new(t.path().join("covers"));
    let ts = scan_all(&root, &covers, &[]);
    let b = by_title(&ts, "broken");
    assert!(b.from_filename);
    assert_eq!(b.artist, "Unknown artist");
}

#[test]
fn filename_parsing() {
    use library::parse_filename as p;
    assert_eq!(p("07 - Nila Rao - Window Seat"), (Some(7), Some("Nila Rao".into()), "Window Seat".into()));
    assert_eq!(p("03. Song"), (Some(3), None, "Song".into()));
    assert_eq!(p("Artist - Title"), (None, Some("Artist".into()), "Title".into()));
    assert_eq!(p("1999"), (None, None, "1999".into()), "a bare number is a title");
    assert_eq!(p("2pac_-_Changes"), (None, Some("2pac".into()), "Changes".into()), "underscores read as spaces");
    assert_eq!(p("Just a title"), (None, None, "Just a title".into()));
}

#[test]
fn shared_art_survives_parallel_scans() {
    // 40 copies of a song with embedded art and 40 ogg files sharing a folder cover.jpg,
    // all read at once by the parallel scan: every one must end up with its cover.
    let (t, root) = fixture_copy();
    let many = root.join("Many");
    std::fs::create_dir_all(&many).unwrap();
    for i in 0..40 {
        std::fs::copy(root.join("Northbound/01 Morning Light.mp3"), many.join(format!("{i:02} a.mp3"))).unwrap();
        std::fs::copy(root.join("Lossless/deep/nested/deep.ogg"), many.join(format!("{i:02} b.ogg"))).unwrap();
    }
    std::fs::copy(root.join("Untagged/cover.jpg"), many.join("Cover.JPG")).unwrap();
    for round in 0..3 {
        let covers = Covers::new(t.path().join(format!("covers{round}")));
        let ts = library::scan(&[many.clone()], &covers, &[], &|_, _| {});
        assert_eq!(ts.len(), 80);
        assert!(ts.iter().all(|t| t.cover.is_some()), "round {round}: a song lost its cover");
        let names: std::collections::HashSet<_> = ts.iter().map(|t| t.cover.clone().unwrap()).collect();
        assert_eq!(names.len(), 2);
        assert_eq!(std::fs::read_dir(covers.dir()).unwrap().count(), 2, "no temp files left behind");
    }
}
