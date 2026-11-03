//! Windows media overlay (System Media Transport Controls): title, artist, cover and the
//! hardware media keys. Elsewhere these are no-ops (the window's own Media Session API
//! covers Linux/macOS when the webview supports it).

use std::path::PathBuf;
use tauri::AppHandle;

#[cfg(target_os = "windows")]
mod imp {
    use super::*;
    use souvlaki::{MediaControlEvent, MediaControls, MediaMetadata, MediaPlayback, MediaPosition, PlatformConfig, SeekDirection};
    use std::cell::RefCell;
    use std::time::Duration;
    use tauri::{Emitter, Manager};

    thread_local! {
        // SMTC objects live on the main (window) thread only.
        static CONTROLS: RefCell<Option<MediaControls>> = const { RefCell::new(None) };
    }

    pub fn init(app: &AppHandle) {
        let Some(w) = app.get_webview_window("main") else { return };
        let Ok(hwnd) = w.hwnd() else { return };
        let cfg = PlatformConfig { display_name: "Paradox Music", dbus_name: "paradox_music", hwnd: Some(hwnd.0 as *mut std::ffi::c_void) };
        let Ok(mut controls) = MediaControls::new(cfg) else { return };
        let h = app.clone();
        let _ = controls.attach(move |ev: MediaControlEvent| {
            let (action, value) = match ev {
                MediaControlEvent::Play => ("play", 0.0),
                MediaControlEvent::Pause => ("pause", 0.0),
                MediaControlEvent::Toggle => ("toggle", 0.0),
                MediaControlEvent::Next => ("next", 0.0),
                MediaControlEvent::Previous => ("prev", 0.0),
                MediaControlEvent::Stop => ("pause", 0.0),
                MediaControlEvent::SetPosition(MediaPosition(d)) => ("seek", d.as_secs_f64()),
                MediaControlEvent::Seek(SeekDirection::Forward) => ("seekby", 10.0),
                MediaControlEvent::Seek(SeekDirection::Backward) => ("seekby", -10.0),
                MediaControlEvent::SeekBy(SeekDirection::Forward, d) => ("seekby", d.as_secs_f64()),
                MediaControlEvent::SeekBy(SeekDirection::Backward, d) => ("seekby", -d.as_secs_f64()),
                _ => return,
            };
            let _ = h.emit("media-key", serde_json::json!({ "action": action, "value": value }));
        });
        let _ = controls.set_playback(MediaPlayback::Stopped);
        CONTROLS.with(|c| *c.borrow_mut() = Some(controls));
    }

    pub fn set_metadata(app: &AppHandle, title: String, artist: String, album: String, cover: Option<PathBuf>, duration: f64) {
        let _ = app.run_on_main_thread(move || {
            CONTROLS.with(|c| {
                if let Some(ctl) = c.borrow_mut().as_mut() {
                    let url = cover.map(|p| format!("file://{}", p.display()));
                    let _ = ctl.set_metadata(MediaMetadata {
                        title: Some(&title),
                        artist: Some(&artist),
                        album: Some(&album),
                        cover_url: url.as_deref(),
                        duration: Some(Duration::from_secs_f64(duration.max(0.0))),
                    });
                }
            })
        });
    }

    pub fn set_playback(app: &AppHandle, playing: bool, position: f64) {
        let _ = app.run_on_main_thread(move || {
            CONTROLS.with(|c| {
                if let Some(ctl) = c.borrow_mut().as_mut() {
                    let progress = Some(MediaPosition(Duration::from_secs_f64(position.max(0.0))));
                    let _ = ctl.set_playback(if playing { MediaPlayback::Playing { progress } } else { MediaPlayback::Paused { progress } });
                }
            })
        });
    }
}

#[cfg(not(target_os = "windows"))]
mod imp {
    use super::*;
    pub fn init(_app: &AppHandle) {}
    pub fn set_metadata(_app: &AppHandle, _t: String, _a: String, _al: String, _c: Option<PathBuf>, _d: f64) {}
    pub fn set_playback(_app: &AppHandle, _playing: bool, _position: f64) {}
}

pub use imp::*;
