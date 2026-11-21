
use base64::Engine;
use serde::{Deserialize, Serialize};
use webtoon_core::audio::wav_data_url;
use webtoon_core::ocr::OcrEngine;
use webtoon_core::pronunciation;
use webtoon_core::text::*;
use webtoon_core::translate::*;
use webtoon_core::tts::OfflineTts;
use webtoon_core::voice;
use std::fs;
use tauri::{Emitter, Manager};

const KEYRING_SERVICE: &str = "webtoon-translator";
const KEYRING_USER: &str = "omniroute_api_key";

fn now_ms() -> u128 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
}

fn process_memory_mb() -> f64 {
    use sysinfo::{Pid, ProcessRefreshKind, System};
    let Ok(mut sys) = System::new() else {
        return 0.0;
    };
    let pid = Pid::from_u32(std::process::id());
    sys.refresh_processes_specifics(
        sysinfo::ProcessesToUpdate::Some(&[pid]),
        true,
        ProcessRefreshKind::nothing().with_memory(),
    );
    sys.process(pid)
        .map(|p| p.memory() as f64 / 1_048_576.0)
        .unwrap_or(0.0)
}

fn perf_log_path(app: &tauri::AppHandle) -> Result<std::path::PathBuf, String> {
    let dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("Não achei a pasta de configuração: {e}"))?;
    fs::create_dir_all(&dir).map_err(|e| format!("Não consegui criar a pasta de configuração: {e}"))?;
    Ok(dir.join("perf.log"))
}

/// Registra um evento de performance (OCR, chamada de API, síntese de áudio, etc.) como uma
/// linha JSON em `perf.log`, junto com o consumo de memória do processo naquele instante.
/// É um log de benchmark temporário — não deve virar telemetria permanente nem painel de UI.
fn perf_log(app: &tauri::AppHandle, session: &str, event: &str, extra: serde_json::Value) {
    // Desligado por padrão (o arquivo cresceria pra sempre na máquina de quem usa o app).
    // Pra medir: WEBTOON_PERF_LOG=1 webtoon-translator
    static ENABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    if !*ENABLED.get_or_init(|| std::env::var("WEBTOON_PERF_LOG").is_ok_and(|v| v == "1")) {
        return;
    }
    let mut map = serde_json::Map::new();
    map.insert("ts_ms".into(), serde_json::json!(now_ms()));
    map.insert("session".into(), serde_json::json!(session));
    map.insert("event".into(), serde_json::json!(event));
    map.insert(
        "memory_mb".into(),
        serde_json::json!((process_memory_mb() * 100.0).round() / 100.0),
    );
    if let serde_json::Value::Object(extra_map) = extra {
        map.extend(extra_map);
    }
    let line = serde_json::Value::Object(map).to_string() + "\n";
    eprint!("[perf] {line}");
    // Várias threads logam ao mesmo tempo (TTS em background + streaming): sem o lock e
    // com writeln! (2 escritas: texto e "\n") as linhas se intercalavam no arquivo.
    static WRITE_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
    let _guard = WRITE_LOCK.lock();
    if let Ok(path) = perf_log_path(app) {
        if let Ok(mut f) = std::fs::OpenOptions::new().create(true).append(true).open(path) {
            use std::io::Write;
            let _ = f.write_all(line.as_bytes());
        }
    }
}

struct TtsState(std::sync::Mutex<Option<OfflineTts>>);

/// Pasta onde o instalador coloca os modelos (voz, OCR, dicionário). Definida no setup a
/// partir do resource_dir do Tauri; nos testes, a pasta do código-fonte.
static RESOURCES_DIR: std::sync::OnceLock<std::path::PathBuf> = std::sync::OnceLock::new();

fn resources_dir() -> std::path::PathBuf {
    #[cfg(test)]
    return std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    #[cfg(not(test))]
    RESOURCES_DIR.get().cloned().unwrap_or_default()
}

fn tts_models_dir() -> std::path::PathBuf {
    resources_dir().join("tts-models")
}

/// Áudios já gerados (data URL), em ordem de uso: repetir um clique ou tocar algo que foi
/// pré-gerado não sintetiza de novo. Limitado pra não virar mais um acúmulo de RAM.
struct TtsCache(std::sync::Mutex<std::collections::VecDeque<(String, String)>>);
const TTS_CACHE_MAX: usize = 20;

fn tts_cache_key(text: &str, speed: f32) -> String {
    format!("{speed:.2}|{}", normalize_for_speech(text))
}

fn tts_cache_get(app: &tauri::AppHandle, key: &str) -> Option<String> {
    let state = app.state::<TtsCache>();
    let mut cache = state.0.lock().ok()?;
    let pos = cache.iter().position(|(k, _)| k == key)?;
    let entry = cache.remove(pos)?;
    let url = entry.1.clone();
    cache.push_back(entry);
    Some(url)
}

fn tts_cache_put(app: &tauri::AppHandle, key: String, url: String) {
    let state = app.state::<TtsCache>();
    if let Ok(mut cache) = state.0.lock() {
        cache.retain(|(k, _)| k != &key);
        cache.push_back((key, url));
        while cache.len() > TTS_CACHE_MAX {
            cache.pop_front();
        }
    };
}

/// O Kokoro sempre gera em 1.0 e o balão desacelera no player (playbackRate, mantendo o tom):
/// gerar devagar custa ~35-45% mais CPU porque produz mais segundos de áudio, e no A/B de
/// ouvido a diferença foi mínima. Bônus: mudar a velocidade não invalida o cache.
const TTS_SYNTH_SPEED: f32 = 1.0;

fn load_tts_engine(app: &tauri::AppHandle, guard: &mut Option<OfflineTts>, session: &str) -> Result<(), String> {
    if guard.is_none() {
        let started = std::time::Instant::now();
        *guard = Some(create_tts()?);
        perf_log(
            app,
            session,
            "tts_model_loaded",
            serde_json::json!({"duration_ms": started.elapsed().as_secs_f64() * 1000.0}),
        );
    }
    Ok(())
}

/// Começa a carregar o motor de voz em background (chamado quando o overlay de seleção abre:
/// o usuário ainda está arrastando a área, então os ~2s de carga somem nesse tempo).
fn warm_up_tts(app: &tauri::AppHandle) {
    if !voice_installed() {
        return;
    }
    let app = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<TtsState>();
        if let Ok(mut guard) = state.0.lock() {
            if let Err(e) = load_tts_engine(&app, &mut guard, "tts-warmup") {
                eprintln!("Falha ao carregar o motor de voz: {e}");
            }
        };
    });
}

/// Devolve (data URL, veio_do_cache). O motor é carregado só no primeiro uso (lazy).
fn synthesize_speech(app: &tauri::AppHandle, text: &str, session: &str) -> Result<(String, bool), String> {
    let speed = TTS_SYNTH_SPEED;
    let key = tts_cache_key(text, speed);
    if let Some(url) = tts_cache_get(app, &key) {
        return Ok((url, true));
    }
    // Áudio de favorito sobrevive a fechar o app: lê do disco sem precisar do motor de voz.
    if let Some(url) = read_favorite_audio(app, text) {
        tts_cache_put(app, key, url.clone());
        return Ok((url, true));
    }
    if !voice_installed() {
        return Err(VOICE_MISSING.to_string());
    }

    let state = app.state::<TtsState>();
    let mut guard = state.0.lock().map_err(|_| "Estado do TTS corrompido".to_string())?;
    // Enquanto esperávamos o lock, a pré-geração pode ter acabado de gerar esse mesmo texto.
    if let Some(url) = tts_cache_get(app, &key) {
        return Ok((url, true));
    }
    load_tts_engine(app, &mut guard, session)?;
    let tts = guard.as_ref().unwrap();

    let (samples, sample_rate) = webtoon_core::tts::synthesize(tts, text, speed)?;
    let url = wav_data_url(&samples, sample_rate);
    tts_cache_put(app, key, url.clone());
    if is_favorite_text(app, text) {
        save_favorite_audio(app, text, &url);
    }
    Ok((url, false))
}

// --- Áudio dos favoritos em disco ---
// Um .wav por TEXTO falado (não por favorito): a mesma frase favoritada em dois modelos
// compartilha o arquivo, e ele só é apagado quando nenhum favorito usa mais aquele texto.

fn favorite_audio_dir(app: &tauri::AppHandle) -> Result<std::path::PathBuf, String> {
    let dir = data_file(app, "favorite_audio")?;
    fs::create_dir_all(&dir).map_err(|e| format!("Não consegui criar a pasta de áudios: {e}"))?;
    Ok(dir)
}

fn favorite_audio_name(text: &str) -> String {
    format!("{:016x}.wav", fnv1a64(&tts_cache_key(text, TTS_SYNTH_SPEED)))
}

fn read_favorite_audio(app: &tauri::AppHandle, text: &str) -> Option<String> {
    let bytes = fs::read(favorite_audio_dir(app).ok()?.join(favorite_audio_name(text))).ok()?;
    Some(format!(
        "data:audio/wav;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    ))
}

fn save_favorite_audio(app: &tauri::AppHandle, text: &str, data_url: &str) {
    let Some(b64) = data_url.split_once("base64,").map(|(_, b)| b) else { return };
    let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(b64) else { return };
    if let Ok(dir) = favorite_audio_dir(app) {
        let _ = fs::write(dir.join(favorite_audio_name(text)), bytes);
    }
}

fn is_favorite_text(app: &tauri::AppHandle, text: &str) -> bool {
    let key = normalize_for_speech(text);
    read_translations(app, FAVORITES_FILE)
        .unwrap_or_default()
        .iter()
        .flat_map(speech_texts)
        .any(|t| normalize_for_speech(&t) == key)
}

/// Garante o .wav de cada texto em background (usa o cache se já foi gerado; senão sintetiza).
/// Avisa a tela principal no fim, pra atualizar o espaço ocupado.
fn ensure_favorite_audio(app: tauri::AppHandle, texts: Vec<String>) {
    if !voice_installed() {
        return;
    }
    tauri::async_runtime::spawn_blocking(move || {
        let Ok(dir) = favorite_audio_dir(&app) else { return };
        let mut saved = 0;
        for text in texts {
            if dir.join(favorite_audio_name(&text)).exists() {
                continue;
            }
            if let Ok((url, _)) = synthesize_speech(&app, &text, "favorite-audio") {
                save_favorite_audio(&app, &text, &url);
                saved += 1;
            }
        }
        if saved > 0 {
            let _ = app.emit("favorites-changed", ());
        }
    });
}

/// Apaga os .wav que nenhum favorito usa mais.
fn gc_favorite_audio(app: &tauri::AppHandle) {
    let Ok(dir) = favorite_audio_dir(app) else { return };
    let used: std::collections::HashSet<String> = read_translations(app, FAVORITES_FILE)
        .unwrap_or_default()
        .iter()
        .flat_map(speech_texts)
        .map(|t| favorite_audio_name(&t))
        .collect();
    for entry in fs::read_dir(&dir).into_iter().flatten().flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.ends_with(".wav") && !used.contains(&name) {
            let _ = fs::remove_file(entry.path());
        }
    }
}

/// Gera áudios em segundo plano, em fila (o motor é um só), pra quando o usuário clicar
/// o áudio já estar no cache.
fn pregenerate_speech(app: tauri::AppHandle, texts: Vec<String>, session: String, reason: &'static str) {
    if !voice_installed() {
        return;
    }
    tauri::async_runtime::spawn_blocking(move || {
        for text in texts.into_iter().filter(|t| !t.trim().is_empty()) {
            let started = std::time::Instant::now();
            let result = synthesize_speech(&app, &text, &session);
            perf_log(
                &app,
                &session,
                "tts_pregenerated",
                serde_json::json!({
                    "reason": reason,
                    "chars": text.chars().count(),
                    "cached": matches!(result, Ok((_, true))),
                    "ok": result.is_ok(),
                    "duration_ms": started.elapsed().as_secs_f64() * 1000.0,
                }),
            );
        }
    });
}

#[tauri::command]
async fn speak_text(text: String, app: tauri::AppHandle) -> Result<String, String> {
    let session = format!("audio-{}", now_ms());
    perf_log(
        &app,
        &session,
        "audio_start",
        serde_json::json!({"chars": text.chars().count()}),
    );
    let started = std::time::Instant::now();
    let app_for_tts = app.clone();
    let text_for_tts = text.clone();
    let session_for_tts = session.clone();
    let result = tauri::async_runtime::spawn_blocking(move || {
        synthesize_speech(&app_for_tts, &text_for_tts, &session_for_tts)
    })
    .await
    .map_err(|e| e.to_string())?;
    let duration_ms = started.elapsed().as_secs_f64() * 1000.0;
    let result = match result {
        Ok((url, cached)) => {
            perf_log(
                &app,
                &session,
                "audio_done",
                serde_json::json!({"duration_ms": duration_ms, "cached": cached}),
            );
            Ok(url)
        }
        Err(e) => Err(e),
    };
    match &result {
        Ok(_) => {}
        Err(e) => perf_log(
            &app,
            &session,
            "audio_error",
            serde_json::json!({"duration_ms": duration_ms, "error": e}),
        ),
    }
    result
}

struct OcrState(std::sync::Mutex<Option<OcrEngine>>);

fn ocr_models_dir() -> std::path::PathBuf {
    resources_dir().join("ocr-models")
}

fn create_ocr_engine() -> Result<OcrEngine, String> {
    webtoon_core::ocr::load_engine(&ocr_models_dir())
}

fn create_tts() -> Result<OfflineTts, String> {
    webtoon_core::tts::create_kokoro(&voice::kokoro_dir(&voices_dir()), &lexicon_path())
}

// --- Voz Kokoro opcional: baixada pelo usuário em Configurações → Voz ---

/// Pasta da voz baixada (dados do app, fora do pacote): definida no setup.
static VOICES_DIR: std::sync::OnceLock<std::path::PathBuf> = std::sync::OnceLock::new();

fn voices_dir() -> std::path::PathBuf {
    VOICES_DIR.get().cloned().unwrap_or_default()
}

fn voice_installed() -> bool {
    voice::is_installed(&voices_dir())
}

/// Prefixo que o balão reconhece pra mostrar "baixe a voz" em vez de um erro genérico.
const VOICE_MISSING: &str = "VOZ_NAO_INSTALADA: baixe a voz em Configurações → Voz.";

struct VoiceDownload {
    running: std::sync::atomic::AtomicBool,
    cancel: std::sync::atomic::AtomicBool,
}

#[tauri::command]
fn voice_status() -> serde_json::Value {
    serde_json::json!({
        "installed": voice_installed(),
        "installed_bytes": voice::installed_bytes(&voices_dir()),
        "download_bytes": voice::KOKORO_ARCHIVE_BYTES,
    })
}

/// Baixa (com progresso no evento "voice-progress"), confere o SHA-256 e instala a voz.
#[tauri::command]
async fn download_voice(app: tauri::AppHandle) -> Result<(), String> {
    use std::sync::atomic::Ordering;
    let state = app.state::<VoiceDownload>();
    if state.running.swap(true, Ordering::SeqCst) {
        return Err("O download da voz já está em andamento.".to_string());
    }
    state.cancel.store(false, Ordering::SeqCst);

    let result = async {
        let dir = voices_dir();
        fs::create_dir_all(&dir).map_err(|e| format!("Não consegui criar a pasta da voz: {e}"))?;
        let archive = dir.join("kokoro.tar.bz2");
        let mut last_pct = u64::MAX;
        voice::download_archive(&archive, &state.cancel, |done, total| {
            let pct = done * 100 / total.max(1);
            if pct != last_pct {
                last_pct = pct;
                let _ = app.emit("voice-progress", serde_json::json!({"stage": "download", "done": done, "total": total}));
            }
        })
        .await?;
        let _ = app.emit("voice-progress", serde_json::json!({"stage": "install"}));
        let installed = tauri::async_runtime::spawn_blocking({
            let (archive, dir) = (archive.clone(), dir.clone());
            move || voice::install_archive(&archive, &dir)
        })
        .await
        .map_err(|e| e.to_string())?;
        let _ = fs::remove_file(&archive);
        installed
    }
    .await;

    state.running.store(false, Ordering::SeqCst);
    let _ = app.emit("voice-progress", serde_json::json!({"stage": if result.is_ok() { "done" } else { "error" }}));
    result
}

#[tauri::command]
fn cancel_voice_download(app: tauri::AppHandle) {
    app.state::<VoiceDownload>().cancel.store(true, std::sync::atomic::Ordering::SeqCst);
}

/// Descarrega o motor da memória antes de apagar (no Windows não dá pra apagar arquivo em uso).
#[tauri::command]
fn remove_voice(app: tauri::AppHandle) -> Result<(), String> {
    if let Ok(mut guard) = app.state::<TtsState>().0.lock() {
        *guard = None;
    }
    voice::remove(&voices_dir())
}

fn run_ocr(app: &tauri::AppHandle, image: &image::RgbaImage) -> Result<String, String> {
    let state = app.state::<OcrState>();
    let mut guard = state.0.lock().map_err(|_| "Estado do OCR corrompido".to_string())?;
    if guard.is_none() {
        *guard = Some(create_ocr_engine()?);
    }
    let engine = guard.as_ref().unwrap();

    let rgb = image::DynamicImage::ImageRgba8(image.clone()).into_rgb8();
    webtoon_core::ocr::recognize_text(engine, rgb.as_raw(), rgb.width(), rgb.height())
}

const DEFAULT_SHORTCUT: &str = "CommandOrControl+Shift+T";

#[derive(Serialize, Deserialize, Clone)]
struct Settings {
    base_url: String,
    model: String,
    #[serde(default)]
    always_on_top: bool,
    #[serde(default = "default_shortcut")]
    shortcut: String,
    #[serde(default = "default_speech_speed")]
    speech_speed: f32,
    #[serde(default = "default_provider")]
    provider: String,
    #[serde(default = "default_anthropic_base_url")]
    anthropic_base_url: String,
    #[serde(default)]
    anthropic_model: String,
    #[serde(default = "default_google_base_url")]
    google_base_url: String,
    #[serde(default)]
    google_model: String,
    #[serde(default)]
    favorites: std::collections::HashMap<String, Vec<String>>,
    /// Opacidade do fundo do balão (0.3–1.0): translúcido pra não esconder a arte do webtoon.
    #[serde(default = "default_bubble_opacity")]
    bubble_opacity: f32,
}

impl Settings {
    fn active_base_url(&self) -> &str {
        match self.provider.as_str() {
            "anthropic" => &self.anthropic_base_url,
            "google" => &self.google_base_url,
            _ => &self.base_url,
        }
    }

    fn active_model(&self) -> &str {
        match self.provider.as_str() {
            "anthropic" => &self.anthropic_model,
            "google" => &self.google_model,
            _ => &self.model,
        }
    }
}

fn default_shortcut() -> String {
    DEFAULT_SHORTCUT.to_string()
}

fn default_speech_speed() -> f32 {
    0.9
}

fn default_bubble_opacity() -> f32 {
    0.85
}

/// Instalação nova começa no Google AI Studio: é o único provedor com plano gratuito,
/// então quem baixa o app só precisa criar uma chave pra começar.
fn default_provider() -> String {
    "google".to_string()
}

fn default_anthropic_base_url() -> String {
    "https://api.anthropic.com".to_string()
}

fn default_google_base_url() -> String {
    "https://generativelanguage.googleapis.com".to_string()
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            base_url: "http://localhost".to_string(),
            model: String::new(),
            always_on_top: false,
            shortcut: default_shortcut(),
            speech_speed: default_speech_speed(),
            bubble_opacity: default_bubble_opacity(),
            provider: default_provider(),
            anthropic_base_url: default_anthropic_base_url(),
            anthropic_model: String::new(),
            google_base_url: default_google_base_url(),
            google_model: String::new(),
            favorites: std::collections::HashMap::new(),
        }
    }
}

struct BubbleState(std::sync::Mutex<Option<TranslationResult>>);

#[tauri::command]
fn get_bubble_data(state: tauri::State<BubbleState>) -> Option<TranslationResult> {
    state.0.lock().unwrap().clone()
}

/// Atualiza o balão já aberto sem roubar o foco (diferente de open_bubble_window).
fn update_bubble(app: &tauri::AppHandle, result: &TranslationResult) {
    *app.state::<BubbleState>().0.lock().unwrap() = Some(result.clone());
    let _ = app.emit_to("bubble", "bubble-update", result.clone());
    // Só a versão final tem id: é ela que a lista da janela principal destaca como ativa.
    if !result.pending && !result.id.is_empty() {
        let _ = app.emit_to("main", "bubble-changed", ());
    }
}

fn lexicon_path() -> std::path::PathBuf {
    tts_models_dir().join("lexicon-en-us.txt")
}

fn settings_path(app: &tauri::AppHandle) -> Result<std::path::PathBuf, String> {
    let dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("Não achei a pasta de configuração: {e}"))?;
    fs::create_dir_all(&dir).map_err(|e| format!("Não consegui criar a pasta de configuração: {e}"))?;
    Ok(dir.join("settings.json"))
}

#[tauri::command]
fn load_settings(app: tauri::AppHandle) -> Result<Settings, String> {
    let path = settings_path(&app)?;
    if !path.exists() {
        return Ok(Settings::default());
    }
    let raw = fs::read_to_string(&path).map_err(|e| e.to_string())?;
    serde_json::from_str(&raw).map_err(|e| e.to_string())
}

#[tauri::command]
fn save_settings(app: tauri::AppHandle, settings: Settings) -> Result<(), String> {
    let path = settings_path(&app)?;
    let raw = serde_json::to_string_pretty(&settings).map_err(|e| e.to_string())?;
    fs::write(&path, raw).map_err(|e| e.to_string())
}

fn keyring_user_for(provider: &str) -> &'static str {
    match provider {
        "anthropic" => "anthropic_api_key",
        "google" => "google_api_key",
        _ => KEYRING_USER,
    }
}

#[tauri::command]
fn save_api_key(provider: String, api_key: String) -> Result<(), String> {
    let entry = keyring::Entry::new(KEYRING_SERVICE, keyring_user_for(&provider)).map_err(|e| e.to_string())?;
    entry.set_password(&api_key).map_err(|e| e.to_string())
}

#[tauri::command]
fn get_api_key(provider: String) -> Result<Option<String>, String> {
    let entry = keyring::Entry::new(KEYRING_SERVICE, keyring_user_for(&provider)).map_err(|e| e.to_string())?;
    match entry.get_password() {
        Ok(pass) => Ok(Some(pass)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(e.to_string()),
    }
}

#[tauri::command]
fn delete_api_key(provider: String) -> Result<(), String> {
    let entry = keyring::Entry::new(KEYRING_SERVICE, keyring_user_for(&provider)).map_err(|e| e.to_string())?;
    match entry.delete_password() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}

/// Caixa delimitadora (em coordenadas absolutas da área de trabalho virtual) que cobre todos os monitores.
fn virtual_desktop_bounds() -> Result<(i32, i32, i32, i32), String> {
    let monitors = xcap::Monitor::all().map_err(|e| e.to_string())?;
    if monitors.is_empty() {
        return Err("Nenhum monitor encontrado".to_string());
    }
    let mut min_x = i32::MAX;
    let mut min_y = i32::MAX;
    let mut max_x = i32::MIN;
    let mut max_y = i32::MIN;
    for m in &monitors {
        let x = m.x().map_err(|e| e.to_string())?;
        let y = m.y().map_err(|e| e.to_string())?;
        let w = m.width().map_err(|e| e.to_string())? as i32;
        let h = m.height().map_err(|e| e.to_string())? as i32;
        min_x = min_x.min(x);
        min_y = min_y.min(y);
        max_x = max_x.max(x + w);
        max_y = max_y.max(y + h);
    }
    Ok((min_x, min_y, max_x - min_x, max_y - min_y))
}

/// Dado um ponto em coordenadas absolutas da área de trabalho virtual, acha o monitor que o contém
/// e devolve (monitor, x relativo, y relativo, largura ajustada, altura ajustada) prontos para `capture_region`.
fn resolve_monitor_region(
    abs_x: i32,
    abs_y: i32,
    width: u32,
    height: u32,
) -> Result<(xcap::Monitor, u32, u32, u32, u32), String> {
    let monitors = xcap::Monitor::all().map_err(|e| e.to_string())?;
    for m in monitors {
        let mx = m.x().map_err(|e| e.to_string())?;
        let my = m.y().map_err(|e| e.to_string())?;
        let mw = m.width().map_err(|e| e.to_string())? as i32;
        let mh = m.height().map_err(|e| e.to_string())? as i32;
        if abs_x >= mx && abs_x < mx + mw && abs_y >= my && abs_y < my + mh {
            let rel_x = (abs_x - mx) as u32;
            let rel_y = (abs_y - my) as u32;
            let clamped_w = width.min((mw - (abs_x - mx)).max(1) as u32);
            let clamped_h = height.min((mh - (abs_y - my)).max(1) as u32);
            return Ok((m, rel_x, rel_y, clamped_w, clamped_h));
        }
    }
    Err("Seleção fora de qualquer monitor detectado".to_string())
}

#[tauri::command]
fn close_window(window: tauri::WebviewWindow) -> Result<(), String> {
    window.close().map_err(|e| e.to_string())
}

/// O balão ajusta a própria altura ao conteúdo (modo compacto pequeno, "Aprender" maior).
/// A largura continua sendo a que o usuário escolher.
#[tauri::command]
fn fit_bubble_height(window: tauri::WebviewWindow, height: f64) -> Result<(), String> {
    let scale = window.scale_factor().map_err(|e| e.to_string())?;
    let size = window.inner_size().map_err(|e| e.to_string())?.to_logical::<f64>(scale);
    let height = height.clamp(120.0, 640.0);
    if (size.height - height).abs() < 2.0 {
        return Ok(());
    }
    window
        .set_size(tauri::LogicalSize::new(size.width, height))
        .map_err(|e| e.to_string())
}

#[tauri::command]
fn start_window_drag(window: tauri::WebviewWindow) -> Result<(), String> {
    window.start_dragging().map_err(|e| e.to_string())
}

#[tauri::command]
fn set_always_on_top(app: tauri::AppHandle, enabled: bool) -> Result<(), String> {
    let window = app
        .get_webview_window("main")
        .ok_or("janela principal não encontrada")?;
    window.set_always_on_top(enabled).map_err(|e| e.to_string())
}

fn apply_shortcut(app: &tauri::AppHandle, accelerator: &str) -> Result<(), String> {
    use tauri_plugin_global_shortcut::{GlobalShortcutExt, ShortcutState};
    let gs = app.global_shortcut();
    let _ = gs.unregister_all();
    let app_for_handler = app.clone();
    gs.on_shortcut(accelerator, move |_app, _shortcut, event| {
        if event.state() == ShortcutState::Pressed {
            let _ = begin_capture(app_for_handler.clone());
        }
    })
    .map_err(|e| format!("Atalho inválido ou já em uso: {e}"))
}

#[tauri::command]
fn set_shortcut(app: tauri::AppHandle, accelerator: String) -> Result<(), String> {
    apply_shortcut(&app, &accelerator)?;
    let mut settings = load_settings(app.clone())?;
    settings.shortcut = accelerator;
    save_settings(app, settings)
}

#[tauri::command]
fn begin_capture(app: tauri::AppHandle) -> Result<(), String> {
    if app.get_webview_window("capture-overlay").is_some() {
        return Ok(());
    }
    warm_up_tts(&app);
    tauri::async_runtime::spawn_blocking(|| pronunciation::warm_up(&lexicon_path()));
    let (min_x, min_y, total_w, total_h) = virtual_desktop_bounds()?;

    tauri::WebviewWindowBuilder::new(
        &app,
        "capture-overlay",
        tauri::WebviewUrl::App("overlay.html".into()),
    )
    .title("")
    .decorations(false)
    .transparent(true)
    .always_on_top(true)
    .skip_taskbar(true)
    .resizable(false)
    .position(min_x as f64, min_y as f64)
    .inner_size(total_w as f64, total_h as f64)
    .build()
    .map_err(|e| e.to_string())?;
    Ok(())
}

const BENCH_CONCURRENCY: usize = 4;
const BENCH_FINALISTS: usize = 8;
const BENCH_EXTRA_RUNS: usize = 2;
const BENCH_FAVORITES: usize = 5;

#[tauri::command]
async fn test_model_speed(
    base_url: String,
    api_key: String,
    provider: String,
    model: String,
) -> Result<f64, String> {
    if model.trim().is_empty() {
        return Err("Escolha um modelo antes de testar a velocidade.".to_string());
    }
    timed_translation(&provider, &base_url, &api_key, &model).await
}

#[derive(Serialize, Clone)]
struct BenchmarkEntry {
    model: String,
    /// Mediana dos tempos (finalistas rodam 1 + BENCH_EXTRA_RUNS vezes).
    secs: Option<f64>,
    runs: Vec<f64>,
    error: Option<String>,
    favorite: bool,
}

/// Roda `models` com no máximo BENCH_CONCURRENCY chamadas simultâneas (mais que isso o
/// gateway começa a devolver 429 e o tempo medido deixa de ser do modelo).
async fn bench_round(
    app: &tauri::AppHandle,
    provider: &str,
    base_url: &str,
    api_key: &str,
    models: Vec<String>,
    done: std::sync::Arc<std::sync::atomic::AtomicUsize>,
    total: usize,
) -> Vec<(String, Result<f64, String>)> {
    let semaphore = std::sync::Arc::new(tokio::sync::Semaphore::new(BENCH_CONCURRENCY));
    let mut handles = Vec::new();
    for model in models {
        let permit = semaphore.clone().acquire_owned().await.expect("semáforo fechado");
        let (app, provider, base_url, api_key, done) =
            (app.clone(), provider.to_string(), base_url.to_string(), api_key.to_string(), done.clone());
        handles.push(tauri::async_runtime::spawn(async move {
            let result = timed_translation(&provider, &base_url, &api_key, &model).await;
            drop(permit);
            let n = done.fetch_add(1, std::sync::atomic::Ordering::SeqCst) + 1;
            let _ = app.emit(
                "benchmark-progress",
                serde_json::json!({"done": n, "total": total, "model": model, "ok": result.is_ok()}),
            );
            (model, result)
        }));
    }
    let mut out = Vec::new();
    for h in handles {
        if let Ok(r) = h.await {
            out.push(r);
        }
    }
    out
}

/// Mede todos os modelos do provider com o prompt real e grava os BENCH_FAVORITES mais
/// rápidos (e que nunca falharam) como favoritos desse provider, substituindo os anteriores.
#[tauri::command]
async fn benchmark_models(
    app: tauri::AppHandle,
    base_url: String,
    api_key: String,
    provider: String,
    models: Vec<String>,
) -> Result<Vec<BenchmarkEntry>, String> {
    if models.is_empty() {
        return Err("Nenhum modelo pra testar. Teste a conexão primeiro.".to_string());
    }
    let finalists_n = BENCH_FINALISTS.min(models.len());
    let total = models.len() + finalists_n * BENCH_EXTRA_RUNS;
    let done = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
    let mut runs: std::collections::HashMap<String, Vec<f64>> = std::collections::HashMap::new();
    let mut errors: std::collections::HashMap<String, String> = std::collections::HashMap::new();

    for (model, result) in bench_round(&app, &provider, &base_url, &api_key, models.clone(), done.clone(), total).await {
        match result {
            Ok(secs) => runs.entry(model).or_default().push(secs),
            Err(e) => {
                errors.insert(model, e.chars().take(140).collect());
            }
        }
    }

    let mut ranked: Vec<(String, f64)> = runs.iter().map(|(m, r)| (m.clone(), r[0])).collect();
    ranked.sort_by(|a, b| a.1.total_cmp(&b.1));
    let finalists: Vec<String> = ranked.iter().take(finalists_n).map(|(m, _)| m.clone()).collect();
    for _ in 0..BENCH_EXTRA_RUNS {
        for (model, result) in bench_round(&app, &provider, &base_url, &api_key, finalists.clone(), done.clone(), total).await {
            match result {
                Ok(secs) => runs.entry(model).or_default().push(secs),
                Err(e) => {
                    errors.entry(model).or_insert_with(|| e.chars().take(140).collect());
                }
            }
        }
    }

    let median = |r: &[f64]| {
        let mut v = r.to_vec();
        v.sort_by(|a, b| a.total_cmp(b));
        v[v.len() / 2]
    };
    // Favorito só se foi finalista e respondeu certo em TODAS as rodadas.
    let mut reliable: Vec<(String, f64)> = finalists
        .iter()
        .filter(|m| !errors.contains_key(*m) && runs.get(*m).map(|r| r.len()) == Some(1 + BENCH_EXTRA_RUNS))
        .map(|m| (m.clone(), median(&runs[m])))
        .collect();
    reliable.sort_by(|a, b| a.1.total_cmp(&b.1));
    let favorites: Vec<String> = reliable.iter().take(BENCH_FAVORITES).map(|(m, _)| m.clone()).collect();

    if !favorites.is_empty() {
        let mut settings = load_settings(app.clone())?;
        settings.favorites.insert(provider.clone(), favorites.clone());
        save_settings(app.clone(), settings)?;
    }

    let mut entries: Vec<BenchmarkEntry> = models
        .iter()
        .map(|m| BenchmarkEntry {
            model: m.clone(),
            secs: runs.get(m).map(|r| median(r)),
            runs: runs.get(m).cloned().unwrap_or_default(),
            error: errors.get(m).cloned(),
            favorite: favorites.contains(m),
        })
        .collect();
    entries.sort_by(|a, b| match (a.secs, b.secs) {
        (Some(x), Some(y)) => x.total_cmp(&y),
        (Some(_), None) => std::cmp::Ordering::Less,
        (None, Some(_)) => std::cmp::Ordering::Greater,
        (None, None) => a.model.cmp(&b.model),
    });
    Ok(entries)
}

/// Recentes guardam todas as traduções (inclusive a mesma frase em modelos diferentes, pra
/// comparar); o usuário limpa pela seleção na tela. Favoritos ficam num arquivo à parte, sem limite.
const MAX_HISTORY: usize = 20;

fn data_file(app: &tauri::AppHandle, name: &str) -> Result<std::path::PathBuf, String> {
    let dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("Não achei a pasta de configuração: {e}"))?;
    fs::create_dir_all(&dir).map_err(|e| format!("Não consegui criar a pasta de configuração: {e}"))?;
    Ok(dir.join(name))
}

const HISTORY_FILE: &str = "history.json";
const FAVORITES_FILE: &str = "favorite_translations.json";

fn read_translations(app: &tauri::AppHandle, file: &str) -> Result<Vec<TranslationResult>, String> {
    let path = data_file(app, file)?;
    if !path.exists() {
        return Ok(Vec::new());
    }
    let raw = fs::read_to_string(&path).map_err(|e| e.to_string())?;
    let mut list: Vec<TranslationResult> = serde_json::from_str(&raw).unwrap_or_default();
    // Traduções de antes de existir `id`: ganham um agora e o arquivo é regravado, pra o id
    // ficar estável (favoritar/excluir dependem dele).
    if list.iter().any(|t| t.id.is_empty()) {
        let base = now_ms();
        for (i, t) in list.iter_mut().enumerate().filter(|(_, t)| t.id.is_empty()) {
            t.id = (base - i as u128).to_string();
        }
        write_translations(app, file, &list)?;
    }
    Ok(list)
}

fn write_translations(app: &tauri::AppHandle, file: &str, list: &[TranslationResult]) -> Result<(), String> {
    let raw = serde_json::to_string_pretty(list).map_err(|e| e.to_string())?;
    fs::write(data_file(app, file)?, raw).map_err(|e| e.to_string())
}

#[tauri::command]
fn load_history(app: tauri::AppHandle) -> Result<Vec<TranslationResult>, String> {
    read_translations(&app, HISTORY_FILE)
}

#[tauri::command]
fn load_favorite_translations(app: tauri::AppHandle) -> Result<Vec<TranslationResult>, String> {
    read_translations(&app, FAVORITES_FILE)
}

fn append_history(app: &tauri::AppHandle, entry: TranslationResult) -> Result<(), String> {
    let mut list = read_translations(app, HISTORY_FILE)?;
    list.insert(0, entry);
    list.truncate(MAX_HISTORY);
    write_translations(app, HISTORY_FILE, &list)
}

#[tauri::command]
fn set_translation_favorite(app: tauri::AppHandle, entry: TranslationResult, favorite: bool) -> Result<(), String> {
    let mut list = read_translations(&app, FAVORITES_FILE)?;
    list.retain(|t| t.id != entry.id);
    let texts = speech_texts(&entry);
    if favorite {
        list.insert(0, entry);
    }
    write_translations(&app, FAVORITES_FILE, &list)?;
    if favorite {
        ensure_favorite_audio(app, texts);
    } else {
        gc_favorite_audio(&app);
    }
    Ok(())
}

/// `list`: "recent" ou "favorites".
#[tauri::command]
fn delete_translations(app: tauri::AppHandle, list: String, ids: Vec<String>) -> Result<(), String> {
    let file = if list == "favorites" { FAVORITES_FILE } else { HISTORY_FILE };
    let mut items = read_translations(&app, file)?;
    items.retain(|t| !ids.contains(&t.id));
    write_translations(&app, file, &items)?;
    if file == FAVORITES_FILE {
        gc_favorite_audio(&app);
    }
    Ok(())
}

// --- Backup: exportar/importar tudo num JSON ---

#[tauri::command]
async fn list_models(base_url: String, api_key: String, provider: Option<String>) -> Result<Vec<ModelEntry>, String> {
    fetch_models(base_url, api_key, provider).await
}

const BACKUP_FORMAT: &str = "webtoon-translator-backup";
const PROVIDERS: [&str; 3] = ["openai", "anthropic", "google"];

/// Abre o "Salvar como" do sistema (começando em Downloads) e grava o backup lá.
/// Devolve o caminho escolhido, ou None se o usuário cancelar.
#[tauri::command]
async fn export_data(app: tauri::AppHandle, include_keys: bool) -> Result<Option<String>, String> {
    use tauri_plugin_dialog::DialogExt;

    let mut keys = serde_json::Map::new();
    if include_keys {
        for p in PROVIDERS {
            if let Some(k) = get_api_key(p.to_string())? {
                keys.insert(p.to_string(), serde_json::json!(k));
            }
        }
    }
    let now = now_ms();
    let backup = serde_json::json!({
        "format": BACKUP_FORMAT,
        "version": 1,
        "exported_at_ms": now,
        "settings": load_settings(app.clone())?,
        "api_keys": keys,
        "history": read_translations(&app, HISTORY_FILE)?,
        "favorite_translations": read_translations(&app, FAVORITES_FILE)?,
    });
    let raw = serde_json::to_string_pretty(&backup).map_err(|e| e.to_string())?;

    let mut dialog = app
        .dialog()
        .file()
        .set_title("Salvar backup do Webtoon Translator")
        .set_file_name(format!("webtoon-translator-backup_{}.json", utc_stamp(now)))
        .add_filter("Backup (JSON)", &["json"]);
    if let Ok(dir) = app.path().download_dir() {
        dialog = dialog.set_directory(dir);
    }
    // O diálogo bloqueia até o usuário escolher: roda fora do runtime async.
    let chosen = tauri::async_runtime::spawn_blocking(move || dialog.blocking_save_file())
        .await
        .map_err(|e| e.to_string())?;
    let Some(chosen) = chosen else {
        return Ok(None);
    };
    let path = chosen.into_path().map_err(|e| format!("Caminho inválido: {e}"))?;
    fs::write(&path, raw).map_err(|e| format!("Não consegui salvar o backup: {e}"))?;
    Ok(Some(path.to_string_lossy().into_owned()))
}

/// Quanto os recentes e favoritos ocupam em disco (bytes), pro usuário administrar o espaço.
#[tauri::command]
fn storage_usage(app: tauri::AppHandle) -> Result<serde_json::Value, String> {
    let size = |file: &str| -> Result<u64, String> {
        let path = data_file(&app, file)?;
        Ok(fs::metadata(path).map(|m| m.len()).unwrap_or(0))
    };
    let audio_bytes: u64 = fs::read_dir(favorite_audio_dir(&app)?)
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|e| e.metadata().ok())
        .map(|m| m.len())
        .sum();
    Ok(serde_json::json!({
        "history_bytes": size(HISTORY_FILE)?,
        "favorites_bytes": size(FAVORITES_FILE)?,
        "favorites_audio_bytes": audio_bytes,
        "history_max": MAX_HISTORY,
    }))
}

/// Configurações e chaves do arquivo substituem as atuais; favoritos de modelos, traduções
/// recentes e favoritas são somados (nada que já existe aqui é apagado).
#[tauri::command]
fn import_data(app: tauri::AppHandle, json: String) -> Result<String, String> {
    let backup: serde_json::Value =
        serde_json::from_str(&json).map_err(|_| "Esse arquivo não é um JSON válido.".to_string())?;
    if backup["format"] != BACKUP_FORMAT {
        return Err("Esse arquivo não é um backup do Webtoon Translator.".to_string());
    }

    let mut summary = Vec::new();
    if !backup["settings"].is_null() {
        let mut imported: Settings = serde_json::from_value(backup["settings"].clone())
            .map_err(|e| format!("Configurações do backup inválidas: {e}"))?;
        let local = load_settings(app.clone())?;
        for (provider, favs) in local.favorites {
            let merged = imported.favorites.entry(provider).or_default();
            for m in favs {
                if !merged.contains(&m) {
                    merged.push(m);
                }
            }
        }
        if let Err(e) = apply_shortcut(&app, &imported.shortcut) {
            eprintln!("Atalho do backup não pôde ser registrado: {e}");
        }
        if let Some(w) = app.get_webview_window("main") {
            let _ = w.set_always_on_top(imported.always_on_top);
        }
        save_settings(app.clone(), imported)?;
        summary.push("configurações".to_string());
    }

    let mut keys = 0;
    if let Some(map) = backup["api_keys"].as_object() {
        for (provider, key) in map {
            if let (true, Some(k)) = (PROVIDERS.contains(&provider.as_str()), key.as_str()) {
                save_api_key(provider.clone(), k.to_string())?;
                keys += 1;
            }
        }
    }
    if keys > 0 {
        summary.push(format!("{keys} chave(s) de API"));
    }

    for (field, file, label) in [
        ("history", HISTORY_FILE, "recentes"),
        ("favorite_translations", FAVORITES_FILE, "favoritas"),
    ] {
        let imported: Vec<TranslationResult> = serde_json::from_value(backup[field].clone()).unwrap_or_default();
        if imported.is_empty() {
            continue;
        }
        let n = imported.len();
        let mut merged = merge_translations(read_translations(&app, file)?, imported);
        if file == HISTORY_FILE {
            merged.truncate(MAX_HISTORY);
        }
        write_translations(&app, file, &merged)?;
        summary.push(format!("{n} traduções {label}"));
    }
    let favorite_texts: Vec<String> = read_translations(&app, FAVORITES_FILE)?.iter().flat_map(speech_texts).collect();
    if !favorite_texts.is_empty() {
        ensure_favorite_audio(app.clone(), favorite_texts);
        summary.push("áudios dos favoritos sendo gerados em segundo plano".to_string());
    }
    Ok(format!("Importado: {}.", summary.join(", ")))
}

#[tauri::command]
fn reopen_bubble(app: tauri::AppHandle, result: TranslationResult) -> Result<(), String> {
    open_bubble_window(&app, 120, 120, &result)
}

fn open_bubble_window(
    app: &tauri::AppHandle,
    x: u32,
    y: u32,
    result: &TranslationResult,
) -> Result<(), String> {
    {
        let state = app.state::<BubbleState>();
        *state.0.lock().unwrap() = Some(result.clone());
    }
    let _ = app.emit_to("main", "bubble-changed", ());

    if let Some(win) = app.get_webview_window("bubble") {
        // Reaproveita a janela onde quer que o usuário a tenha deixado — não reposiciona.
        let _ = app.emit_to("bubble", "bubble-update", result.clone());
        let _ = win.show();
        let _ = win.set_focus();
        return Ok(());
    }

    tauri::WebviewWindowBuilder::new(app, "bubble", tauri::WebviewUrl::App("bubble.html".into()))
        .title("")
        .decorations(false)
        .transparent(true)
        .always_on_top(true)
        .skip_taskbar(true)
        .resizable(true)
        .inner_size(380.0, 460.0)
        .position((x + 20) as f64, (y + 20) as f64)
        .build()
        .map_err(|e| e.to_string())?;
    Ok(())
}

fn elapsed_ms(since: std::time::Instant) -> f64 {
    since.elapsed().as_secs_f64() * 1000.0
}

/// Fluxo de uma captura, otimizado pra latência percebida:
/// OCR -> balão abre na hora com o texto do OCR + TTS especulativo do texto do OCR em paralelo
/// -> streaming: original corrigido e tradução aparecem assim que fecham no JSON
/// -> JSON completo: breakdown + pronúncia local -> pré-gera os áudios dos trechos.
async fn do_capture_and_translate(
    app: &tauri::AppHandle,
    client_x: u32,
    client_y: u32,
    width: u32,
    height: u32,
) -> Result<(), String> {
    if width < 4 || height < 4 {
        return Err("Seleção muito pequena.".to_string());
    }

    let settings = load_settings(app.clone())?;
    if settings.active_model().is_empty() {
        return Err("Configure um modelo em Configurações antes de capturar.".to_string());
    }
    let api_key = get_api_key(settings.provider.clone())?
        .ok_or("Configure a chave de API em Configurações antes de capturar.")?;
    let model = settings.active_model().to_string();
    let started_at = std::time::Instant::now();
    let session = format!("capture-{}", now_ms());
    perf_log(app, &session, "capture_start", serde_json::json!({}));

    // O dicionário de pronúncia carrega em paralelo com a captura + OCR (só pesa na 1ª vez).
    let lexicon_warm = tauri::async_runtime::spawn_blocking(|| pronunciation::warm_up(&lexicon_path()));

    let (min_x, min_y, _, _) = virtual_desktop_bounds()?;
    let abs_x = min_x + client_x as i32;
    let abs_y = min_y + client_y as i32;
    let capture_started = std::time::Instant::now();
    // O xcap::Monitor não é Send no Windows (guarda um HMONITOR): ele precisa ser descartado
    // dentro deste bloco, antes de qualquer `.await`, senão o comando não compila lá.
    let captured = {
        let (monitor, rel_x, rel_y, clamped_w, clamped_h) =
            resolve_monitor_region(abs_x, abs_y, width, height)?;
        monitor
            .capture_region(rel_x, rel_y, clamped_w, clamped_h)
            .map_err(|e| e.to_string())?
    };
    perf_log(
        app,
        &session,
        "screen_captured",
        serde_json::json!({"duration_ms": elapsed_ms(capture_started)}),
    );

    let ocr_started = std::time::Instant::now();
    let app_for_ocr = app.clone();
    let extracted_text = tauri::async_runtime::spawn_blocking(move || run_ocr(&app_for_ocr, &captured))
        .await
        .map_err(|e| e.to_string())??;
    perf_log(
        app,
        &session,
        "ocr_done",
        serde_json::json!({
            "duration_ms": elapsed_ms(ocr_started),
            "chars": extracted_text.chars().count(),
        }),
    );
    if extracted_text.trim().is_empty() {
        return Err("Nenhum texto detectado nessa área. Tente selecionar de novo, incluindo o balão inteiro.".to_string());
    }

    // Especulativo: na maioria das vezes o OCR já acerta o texto, então o áudio fica pronto
    // sem esperar o modelo. Se o modelo corrigir o texto, gera de novo com a versão corrigida.
    pregenerate_speech(app.clone(), vec![extracted_text.clone()], session.clone(), "ocr_especulativo");

    let mut partial = TranslationResult {
        original_text: extracted_text.clone(),
        model: model.clone(),
        pending: true,
        ..Default::default()
    };
    open_bubble_window(app, abs_x.max(0) as u32, abs_y.max(0) as u32, &partial)?;
    perf_log(
        app,
        &session,
        "bubble_opened",
        serde_json::json!({"since_capture_ms": elapsed_ms(started_at)}),
    );

    let _ = lexicon_warm.await;
    let api_started = std::time::Instant::now();
    let out_tokens;
    let raw = {
        {
            let app_ev = app.clone();
            let session_ev = session.clone();
            let ocr_text = extracted_text.clone();
            let mut on_event = move |event: StreamEvent| match event {
                StreamEvent::FirstToken => perf_log(
                    &app_ev,
                    &session_ev,
                    "api_first_token",
                    serde_json::json!({
                        "duration_ms": elapsed_ms(api_started),
                        "since_capture_ms": elapsed_ms(started_at),
                    }),
                ),
                StreamEvent::Field(field, value) => {
                    if field == "original_text" {
                        if normalize_for_speech(&value) != normalize_for_speech(&ocr_text) {
                            pregenerate_speech(
                                app_ev.clone(),
                                vec![value.clone()],
                                session_ev.clone(),
                                "original_corrigido",
                            );
                        }
                        if let Some(p) = pronunciation::pronounce(&lexicon_path(), &value) {
                            partial.pronunciation_pt = p.ptbr;
                            partial.pronunciation_ipa = p.ipa;
                        }
                        partial.original_text = value;
                    } else if field == "translation" {
                        partial.translation = value;
                    }
                    update_bubble(&app_ev, &partial);
                    perf_log(
                        &app_ev,
                        &session_ev,
                        &format!("{field}_ready"),
                        serde_json::json!({
                            "duration_ms": elapsed_ms(api_started),
                            "since_capture_ms": elapsed_ms(started_at),
                        }),
                    );
                }
            };
            let (text, tokens) = stream_translation(
                Wire::from_provider(&settings.provider),
                &extracted_text,
                &api_key,
                settings.active_base_url(),
                &model,
                &mut on_event,
            )
            .await?;
            out_tokens = tokens;
            text
        }
    };
    perf_log(
        app,
        &session,
        "api_response",
        serde_json::json!({
            "duration_ms": elapsed_ms(api_started),
            "provider": settings.provider,
            "model": model,
            "out_tokens": out_tokens,
        }),
    );

    let mut result = parse_translation_json(&raw)?;
    fill_pronunciations(&mut result, &lexicon_path());
    result.duration_secs = started_at.elapsed().as_secs_f64();
    result.model = model;
    result.pending = false;
    result.id = session.trim_start_matches("capture-").to_string();
    update_bubble(app, &result);
    perf_log(
        app,
        &session,
        "capture_total",
        serde_json::json!({"duration_ms": result.duration_secs * 1000.0}),
    );

    append_history(app, result.clone())?;
    let _ = app.emit("capture-done", ());

    // Original primeiro (normalmente já está no cache pelo especulativo), depois os trechos.
    let mut to_speak = vec![result.original_text.clone()];
    to_speak.extend(result.breakdown.iter().map(|b| b.segment.clone()));
    pregenerate_speech(app.clone(), to_speak, session, "pos_resposta");
    Ok(())
}

/// Se a captura falhar com o balão já aberto esperando a resposta, mostra o erro nele
/// em vez de deixar o skeleton girando pra sempre.
fn fail_pending_bubble(app: &tauri::AppHandle, msg: &str) {
    let current = app.state::<BubbleState>().0.lock().unwrap().clone();
    if let Some(mut bubble) = current.filter(|b| b.pending) {
        bubble.pending = false;
        bubble.grammar_notes = format!("⚠ {msg}");
        update_bubble(app, &bubble);
    }
}

#[tauri::command]
async fn region_selected(
    app: tauri::AppHandle,
    window: tauri::WebviewWindow,
    x: u32,
    y: u32,
    width: u32,
    height: u32,
) -> Result<(), String> {
    window.close().map_err(|e| e.to_string())?;
    let _ = app.emit("capture-started", ());
    tokio::time::sleep(std::time::Duration::from_millis(120)).await;

    let result = do_capture_and_translate(&app, x, y, width, height).await;
    if let Err(ref msg) = result {
        fail_pending_bubble(&app, msg);
        let _ = app.emit("capture-error", msg.clone());
    }
    result
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(BubbleState(std::sync::Mutex::new(None)))
        .manage(TtsState(std::sync::Mutex::new(None)))
        .manage(VoiceDownload {
            running: std::sync::atomic::AtomicBool::new(false),
            cancel: std::sync::atomic::AtomicBool::new(false),
        })
        .manage(TtsCache(std::sync::Mutex::new(std::collections::VecDeque::new())))
        .manage(OcrState(std::sync::Mutex::new(None)))
        .invoke_handler(tauri::generate_handler![
            load_settings,
            save_settings,
            save_api_key,
            get_api_key,
            delete_api_key,
            list_models,
            test_model_speed,
            benchmark_models,
            close_window,
            start_window_drag,
            fit_bubble_height,
            set_always_on_top,
            begin_capture,
            region_selected,
            load_history,
            load_favorite_translations,
            set_translation_favorite,
            delete_translations,
            export_data,
            storage_usage,
            import_data,
            reopen_bubble,
            get_bubble_data,
            speak_text,
            set_shortcut,
            voice_status,
            download_voice,
            cancel_voice_download,
            remove_voice,
        ])
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .plugin(tauri_plugin_dialog::init())
        .on_window_event(|window, event| {
            // Balão fechado: nenhuma tradução fica marcada como ativa na lista.
            if window.label() == "bubble" && matches!(event, tauri::WindowEvent::Destroyed) {
                let app = window.app_handle();
                *app.state::<BubbleState>().0.lock().unwrap() = None;
                let _ = app.emit_to("main", "bubble-changed", ());
            }
        })
        .setup(|app| {
            if let Ok(dir) = app.path().resource_dir() {
                let _ = RESOURCES_DIR.set(dir);
            }
            if let Ok(dir) = app.path().app_local_data_dir() {
                let _ = VOICES_DIR.set(dir.join("voices"));
            }
            let handle = app.handle().clone();
            if let Ok(settings) = load_settings(handle.clone()) {
                if settings.always_on_top {
                    if let Some(window) = handle.get_webview_window("main") {
                        let _ = window.set_always_on_top(true);
                    }
                }
                if let Err(e) = apply_shortcut(&handle, &settings.shortcut) {
                    eprintln!("Falha ao registrar atalho global: {e}");
                }
            }

            // Só o OCR é pré-carregado (roda em toda captura). O TTS (~500MB de RAM) carrega
            // sob demanda, escondido atrás da chamada da API na primeira captura.
            let ocr_handle = app.handle().clone();
            std::thread::spawn(move || {
                let state = ocr_handle.state::<OcrState>();
                let mut guard = state.0.lock().unwrap();
                if guard.is_none() {
                    match create_ocr_engine() {
                        Ok(engine) => *guard = Some(engine),
                        Err(e) => eprintln!("Falha ao pré-carregar o OCR: {e}"),
                    }
                }
            });

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("erro ao iniciar o Webtoon Translator");
}


#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nome_do_audio_do_favorito_ignora_caixa_e_espacos() {
        assert_eq!(favorite_audio_name("HEAD INSIDE"), favorite_audio_name("head  inside"));
        assert!(favorite_audio_name("head inside").ends_with(".wav"));
    }
}
