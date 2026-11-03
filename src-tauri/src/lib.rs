//! Paradox Music: a minimal offline music player.
//! Rust side: library scanning, cover cache, settings files, folder watching and the
//! Windows media overlay (SMTC). Playback itself happens in the window's <audio>.
//! No network code anywhere.

pub mod library;
mod media;

use library::{Covers, Track};
use serde_json::{json, Value};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, State};

pub struct AppState {
    data_dir: PathBuf,
    covers: Arc<Covers>,
    library: Mutex<Vec<Track>>,
    scanning: AtomicBool,
    watcher: Mutex<Option<notify::RecommendedWatcher>>,
    pending_open: Mutex<Vec<String>>,
}

const JSON_NAMES: &[&str] = &["settings", "playlists", "state", "smart", "stats"];

fn read_json(dir: &Path, name: &str) -> Value {
    std::fs::read_to_string(dir.join(format!("{name}.json")))
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or(Value::Null)
}

fn write_json<T: serde::Serialize>(dir: &Path, name: &str, v: &T) -> Result<(), String> {
    let _ = std::fs::create_dir_all(dir);
    let tmp = dir.join(format!("{name}.json.tmp"));
    let body = serde_json::to_vec(v).map_err(|e| e.to_string())?;
    std::fs::write(&tmp, body).map_err(|e| e.to_string())?;
    std::fs::rename(&tmp, dir.join(format!("{name}.json"))).map_err(|e| e.to_string())
}

fn folders_of(settings: &Value) -> Vec<PathBuf> {
    settings
        .get("folders")
        .and_then(|f| f.as_array())
        .map(|a| a.iter().filter_map(|v| v.as_str()).map(PathBuf::from).collect())
        .unwrap_or_default()
}

/// Lets the window read files in the music folders (and the cover cache), nothing else.
fn allow_paths(app: &AppHandle, paths: &[PathBuf]) {
    let scope = app.asset_protocol_scope();
    for p in paths {
        if p.is_dir() {
            let _ = scope.allow_directory(p, true);
        } else {
            let _ = scope.allow_file(p);
        }
    }
}

/// Watches the music folders; after 2 s of quiet following a change, tells the window
/// to rescan (an incremental scan, which only re-reads changed files).
fn start_watcher(app: &AppHandle, folders: &[PathBuf]) {
    use notify::{EventKind, RecursiveMode, Watcher};
    let state = app.state::<AppState>();
    let mut slot = state.watcher.lock().unwrap();
    *slot = None;
    if folders.is_empty() {
        return;
    }
    let (tx, rx) = mpsc::channel::<()>();
    let watcher = notify::recommended_watcher(move |res: notify::Result<notify::Event>| {
        if let Ok(ev) = res {
            let relevant = match ev.kind {
                EventKind::Access(_) => false,
                EventKind::Create(_) | EventKind::Remove(_) => true,
                EventKind::Modify(notify::event::ModifyKind::Name(_)) => true,
                _ => ev.paths.iter().any(|p| library::is_audio(p)),
            };
            if relevant {
                let _ = tx.send(());
            }
        }
    });
    let Ok(mut watcher) = watcher else { return };
    for f in folders {
        let _ = watcher.watch(f, RecursiveMode::Recursive);
    }
    let handle = app.clone();
    std::thread::spawn(move || {
        while rx.recv().is_ok() {
            while rx.recv_timeout(Duration::from_secs(2)).is_ok() {}
            let _ = handle.emit("library-dirty", ());
        }
    });
    *slot = Some(watcher);
}

#[tauri::command]
fn boot(app: AppHandle, state: State<AppState>) -> Value {
    let settings = read_json(&state.data_dir, "settings");
    let lib = state.library.lock().unwrap().clone();
    json!({
        "version": app.package_info().version.to_string(),
        "settings": settings,
        "library": lib,
        "playlists": read_json(&state.data_dir, "playlists"),
        "smart": read_json(&state.data_dir, "smart"),
        "stats": read_json(&state.data_dir, "stats"),
        "state": read_json(&state.data_dir, "state"),
        "coversDir": state.covers.dir().to_string_lossy(),
        "sep": std::path::MAIN_SEPARATOR.to_string(),
        "os": std::env::consts::OS,
        "demo": std::env::var("PARADOX_MUSIC_DEMO").ok().and_then(|s| serde_json::from_str::<Value>(&s).ok()),
    })
}

#[tauri::command]
fn ready(app: AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.set_focus();
    }
}

#[tauri::command]
async fn pick_folder(app: AppHandle) -> Option<String> {
    use tauri_plugin_dialog::DialogExt;
    let mut d = app.dialog().file().set_title("Choose your music folder");
    if let Some(w) = app.get_webview_window("main") {
        d = d.set_parent(&w);
    }
    d.blocking_pick_folder().and_then(|p| p.into_path().ok()).map(|p| p.to_string_lossy().to_string())
}

#[tauri::command]
fn save_json(app: AppHandle, state: State<AppState>, name: String, value: Value) -> Result<(), String> {
    if !JSON_NAMES.contains(&name.as_str()) {
        return Err("unknown file".into());
    }
    write_json(&state.data_dir, &name, &value)?;
    if name == "settings" {
        let folders = folders_of(&value);
        allow_paths(&app, &folders);
        start_watcher(&app, &folders);
    }
    Ok(())
}

/// Scans the saved folders (incrementally), stores and returns the library.
#[tauri::command]
async fn scan(app: AppHandle) -> Result<Vec<Track>, String> {
    let state = app.state::<AppState>();
    if state.scanning.swap(true, Ordering::SeqCst) {
        return Err("busy".into());
    }
    let folders = folders_of(&read_json(&state.data_dir, "settings"));
    let previous = state.library.lock().unwrap().clone();
    let covers = state.covers.clone();
    let h = app.clone();
    let res = tauri::async_runtime::spawn_blocking(move || {
        let progress = move |done: usize, total: usize| {
            let _ = h.emit("scan-progress", json!({ "done": done, "total": total }));
        };
        let tracks = library::scan(&folders, &covers, &previous, &progress);
        library::prune_covers(&covers, &tracks);
        tracks
    })
    .await
    .map_err(|e| e.to_string());
    state.scanning.store(false, Ordering::SeqCst);
    let tracks = res?;
    *state.library.lock().unwrap() = tracks.clone();
    write_json(&state.data_dir, "library", &tracks)?;
    Ok(tracks)
}

/// Dropped or "Open with" files and folders: returns their songs, ready to play.
#[tauri::command]
async fn open_paths(app: AppHandle, paths: Vec<String>) -> Vec<Track> {
    let state = app.state::<AppState>();
    let roots: Vec<PathBuf> = paths.iter().map(PathBuf::from).filter(|p| p.exists()).collect();
    allow_paths(&app, &roots);
    let known: std::collections::HashMap<String, Track> =
        state.library.lock().unwrap().iter().map(|t| (t.path.clone(), t.clone())).collect();
    let covers = state.covers.clone();
    tauri::async_runtime::spawn_blocking(move || {
        use rayon::prelude::*;
        let files = library::collect_files(&roots);
        files
            .par_iter()
            .map(|p| known.get(p.to_string_lossy().as_ref()).cloned().unwrap_or_else(|| library::read_track(p, &covers)))
            .collect()
    })
    .await
    .unwrap_or_default()
}

#[tauri::command]
fn take_pending_open(state: State<AppState>) -> Vec<String> {
    std::mem::take(&mut *state.pending_open.lock().unwrap())
}

#[tauri::command]
fn media_update(app: AppHandle, title: String, artist: String, album: String, cover: Option<String>, duration: f64) {
    let state = app.state::<AppState>();
    let cover_path = cover.map(|c| state.covers.dir().join(c)).filter(|p| p.exists());
    media::set_metadata(&app, title, artist, album, cover_path, duration);
}

#[tauri::command]
fn media_playback(app: AppHandle, playing: bool, position: f64) {
    media::set_playback(&app, playing, position);
}

fn existing_args(args: &[String]) -> Vec<String> {
    args.iter().skip(1).filter(|a| !a.starts_with('-') && Path::new(a).exists()).cloned().collect()
}

pub fn run() {
    let initial = existing_args(&std::env::args().collect::<Vec<_>>());
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, args, _cwd| {
            let paths = existing_args(&args);
            if !paths.is_empty() {
                let _ = app.emit("open-paths", paths);
            }
            if let Some(w) = app.get_webview_window("main") {
                let _ = w.unminimize();
                let _ = w.show();
                let _ = w.set_focus();
            }
        }))
        .plugin(tauri_plugin_dialog::init())
        .plugin(
            tauri_plugin_window_state::Builder::default()
                .with_state_flags(
                    tauri_plugin_window_state::StateFlags::SIZE
                        | tauri_plugin_window_state::StateFlags::POSITION
                        | tauri_plugin_window_state::StateFlags::MAXIMIZED,
                )
                .build(),
        )
        .setup(move |app| {
            let data_dir = std::env::var("PARADOX_MUSIC_DATA")
                .map(PathBuf::from)
                .unwrap_or_else(|_| app.path().app_data_dir().expect("data dir"));
            let _ = std::fs::create_dir_all(&data_dir);
            let covers = Arc::new(Covers::new(data_dir.join("covers")));
            let library: Vec<Track> = std::fs::read_to_string(data_dir.join("library.json"))
                .ok()
                .and_then(|s| serde_json::from_str(&s).ok())
                .unwrap_or_default();
            let folders = folders_of(&read_json(&data_dir, "settings"));
            let handle = app.handle().clone();
            allow_paths(&handle, &[covers.dir().to_path_buf()]);
            allow_paths(&handle, &folders);
            app.manage(AppState {
                data_dir,
                covers,
                library: Mutex::new(library),
                scanning: AtomicBool::new(false),
                watcher: Mutex::new(None),
                pending_open: Mutex::new(initial.clone()),
            });
            start_watcher(&handle, &folders);
            media::init(&handle);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            boot,
            ready,
            pick_folder,
            save_json,
            scan,
            open_paths,
            take_pending_open,
            media_update,
            media_playback
        ])
        .run(tauri::generate_context!())
        .expect("error while running Paradox Music");
}
