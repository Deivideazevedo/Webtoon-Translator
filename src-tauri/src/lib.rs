mod pronunciation;

use base64::Engine;
use serde::{Deserialize, Serialize};
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

struct TtsState(std::sync::Mutex<Option<sherpa_onnx::OfflineTts>>);

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

fn create_tts() -> Result<sherpa_onnx::OfflineTts, String> {
    let base = tts_models_dir();
    let voice_dir = base.join("kokoro-en-v0_19");
    let config = sherpa_onnx::OfflineTtsConfig {
        model: sherpa_onnx::OfflineTtsModelConfig {
            kokoro: sherpa_onnx::OfflineTtsKokoroModelConfig {
                model: Some(voice_dir.join("model.onnx").to_string_lossy().into_owned()),
                voices: Some(voice_dir.join("voices.bin").to_string_lossy().into_owned()),
                tokens: Some(voice_dir.join("tokens.txt").to_string_lossy().into_owned()),
                lexicon: Some(base.join("lexicon-en-us.txt").to_string_lossy().into_owned()),
                length_scale: 1.0,
                ..Default::default()
            },
            num_threads: std::thread::available_parallelism()
                .map(|n| n.get() as i32)
                .unwrap_or(2),
            debug: false,
            ..Default::default()
        },
        ..Default::default()
    };
    sherpa_onnx::OfflineTts::create(&config)
        .ok_or_else(|| "Não consegui carregar o modelo de voz (Kokoro).".to_string())
}

/// Áudios já gerados (data URL), em ordem de uso: repetir um clique ou tocar algo que foi
/// pré-gerado não sintetiza de novo. Limitado pra não virar mais um acúmulo de RAM.
struct TtsCache(std::sync::Mutex<std::collections::VecDeque<(String, String)>>);
const TTS_CACHE_MAX: usize = 20;

/// Caixa alta não muda o áudio do Kokoro (medido), então a chave ignora maiúsculas/espaços.
fn normalize_for_speech(text: &str) -> String {
    text.split_whitespace().collect::<Vec<_>>().join(" ").to_lowercase()
}

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

/// WAV PCM 16-bit montado em memória (antes era salvo num arquivo temporário e relido do disco).
fn wav_data_url(samples: &[f32], sample_rate: u32) -> String {
    let data_len = (samples.len() * 2) as u32;
    let mut bytes = Vec::with_capacity(44 + data_len as usize);
    bytes.extend_from_slice(b"RIFF");
    bytes.extend_from_slice(&(36 + data_len).to_le_bytes());
    bytes.extend_from_slice(b"WAVEfmt ");
    bytes.extend_from_slice(&16u32.to_le_bytes());
    bytes.extend_from_slice(&1u16.to_le_bytes()); // PCM
    bytes.extend_from_slice(&1u16.to_le_bytes()); // mono
    bytes.extend_from_slice(&sample_rate.to_le_bytes());
    bytes.extend_from_slice(&(sample_rate * 2).to_le_bytes());
    bytes.extend_from_slice(&2u16.to_le_bytes());
    bytes.extend_from_slice(&16u16.to_le_bytes());
    bytes.extend_from_slice(b"data");
    bytes.extend_from_slice(&data_len.to_le_bytes());
    for s in samples {
        bytes.extend_from_slice(&((s.clamp(-1.0, 1.0) * 32767.0) as i16).to_le_bytes());
    }
    format!(
        "data:audio/wav;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(&bytes)
    )
}

/// O Kokoro sempre gera em 1.0 e o balão desacelera no player (playbackRate, mantendo o tom):
/// gerar devagar custa ~35-45% mais CPU porque produz mais segundos de áudio, e no A/B de
/// ouvido a diferença foi mínima. Bônus: mudar a velocidade não invalida o cache.
const TTS_SYNTH_SPEED: f32 = 1.0;

fn load_tts_engine(app: &tauri::AppHandle, guard: &mut Option<sherpa_onnx::OfflineTts>, session: &str) -> Result<(), String> {
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

    let state = app.state::<TtsState>();
    let mut guard = state.0.lock().map_err(|_| "Estado do TTS corrompido".to_string())?;
    // Enquanto esperávamos o lock, a pré-geração pode ter acabado de gerar esse mesmo texto.
    if let Some(url) = tts_cache_get(app, &key) {
        return Ok((url, true));
    }
    load_tts_engine(app, &mut guard, session)?;
    let tts = guard.as_ref().unwrap();

    let gen_config = sherpa_onnx::GenerationConfig {
        sid: 0,
        speed,
        ..Default::default()
    };
    let audio = tts
        .generate_with_config(text, &gen_config, None::<fn(&[f32], f32) -> bool>)
        .ok_or_else(|| "Falha ao gerar áudio".to_string())?;
    let url = wav_data_url(audio.samples(), audio.sample_rate() as u32);
    tts_cache_put(app, key, url.clone());
    if is_favorite_text(app, text) {
        save_favorite_audio(app, text, &url);
    }
    Ok((url, false))
}

// --- Áudio dos favoritos em disco ---
// Um .wav por TEXTO falado (não por favorito): a mesma frase favoritada em dois modelos
// compartilha o arquivo, e ele só é apagado quando nenhum favorito usa mais aquele texto.

/// FNV-1a 64: hash estável entre versões do Rust (o DefaultHasher não garante isso), pra o
/// nome do arquivo continuar o mesmo depois de atualizar o app.
fn fnv1a64(text: &str) -> u64 {
    text.bytes().fold(0xcbf2_9ce4_8422_2325, |h, b| (h ^ b as u64).wrapping_mul(0x0100_0000_01b3))
}

fn favorite_audio_dir(app: &tauri::AppHandle) -> Result<std::path::PathBuf, String> {
    let dir = data_file(app, "favorite_audio")?;
    fs::create_dir_all(&dir).map_err(|e| format!("Não consegui criar a pasta de áudios: {e}"))?;
    Ok(dir)
}

fn favorite_audio_name(text: &str) -> String {
    format!("{:016x}.wav", fnv1a64(&tts_cache_key(text, TTS_SYNTH_SPEED)))
}

/// Textos que viram áudio numa tradução: a frase original e cada trecho do breakdown.
fn speech_texts(t: &TranslationResult) -> Vec<String> {
    std::iter::once(t.original_text.clone())
        .chain(t.breakdown.iter().map(|b| b.segment.clone()))
        .filter(|s| !s.trim().is_empty())
        .collect()
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

struct OcrState(std::sync::Mutex<Option<ocrs::OcrEngine>>);

fn ocr_models_dir() -> std::path::PathBuf {
    resources_dir().join("ocr-models")
}

fn create_ocr_engine() -> Result<ocrs::OcrEngine, String> {
    let base = ocr_models_dir();
    let detection_model = rten::Model::load_file(base.join("text-detection.onnx"))
        .map_err(|e| format!("Falha ao carregar modelo de detecção de texto: {e}"))?;
    let recognition_model = rten::Model::load_file(base.join("text-recognition.onnx"))
        .map_err(|e| format!("Falha ao carregar modelo de reconhecimento de texto: {e}"))?;
    ocrs::OcrEngine::new(ocrs::OcrEngineParams {
        detection_model: Some(detection_model),
        recognition_model: Some(recognition_model),
        ..Default::default()
    })
    .map_err(|e| format!("Falha ao iniciar o OCR: {e}"))
}

fn run_ocr(app: &tauri::AppHandle, image: &image::RgbaImage) -> Result<String, String> {
    let state = app.state::<OcrState>();
    let mut guard = state.0.lock().map_err(|_| "Estado do OCR corrompido".to_string())?;
    if guard.is_none() {
        *guard = Some(create_ocr_engine()?);
    }
    let engine = guard.as_ref().unwrap();

    let rgb = image::DynamicImage::ImageRgba8(image.clone()).into_rgb8();
    let img_source = ocrs::ImageSource::from_bytes(rgb.as_raw(), rgb.dimensions())
        .map_err(|e| format!("Falha ao preparar imagem pro OCR: {e}"))?;
    let ocr_input = engine
        .prepare_input(img_source)
        .map_err(|e| format!("Falha ao processar imagem pro OCR: {e}"))?;
    let text = engine
        .get_text(&ocr_input)
        .map_err(|e| format!("Falha ao extrair texto: {e}"))?;

    // Filtra detecções espúrias (letras soltas nas bordas de balões), igual o
    // próprio exemplo oficial do ocrs recomenda.
    let cleaned: String = text
        .lines()
        .filter(|l| l.trim().chars().count() > 1)
        .collect::<Vec<_>>()
        .join("\n");
    Ok(cleaned)
}

/// A pronúncia NÃO vem do modelo: é calculada localmente (pronunciation.rs) a partir do
/// dicionário do Kokoro — mais consistente e menos tokens de saída pra esperar.
const SYSTEM_PROMPT: &str = "Você é um professor de inglês para falantes de português brasileiro que leem \
webtoons. Você recebe um texto em inglês extraído por OCR (pode ter ruído: letras soltas, pontuação estranha, \
palavras levemente erradas). Reconstrua o texto mais provável e responda SOMENTE com JSON válido, sem markdown, \
nesta ordem exata de campos:\n\
{\"original_text\": \"...\", \"translation\": \"...\", \"breakdown\": [{\"segment\": \"...\", \"meaning\": \"...\", \
\"note\": \"...\"}], \"grammar_notes\": \"...\", \"gloss\": [[\"palavra\", \"tradução literal\"]]}\n\n\
Regras:\n\
- original_text e translation em sentence case (nunca copie a caixa alta do quadrinho). Separe balões com \"\\n\".\n\
- translation: português brasileiro natural, preservando a nuance (gíria, intensidade, tom).\n\
- breakdown: de 1 a 3 trechos. Priorize phrasal verbs e expressões cujo sentido muda em relação às palavras \
soltas (ex.: \"go down\" é literalmente \"descer/cair\", mas em \"go down without a fight\" é \"se render\"); \
depois gírias, armadilhas gramaticais e falsos cognatos. Nunca repita a frase inteira como trecho. segment deve \
aparecer exatamente assim em original_text.\n\
- meaning: o sentido do trecho neste contexto. note: no máximo 1 frase; quando o sentido não for literal, comece \
com \"Literal: … → aqui: …\"; sem especular sobre história ou personagens.\n\
- grammar_notes: no máximo 1 frase.\n\
- gloss: tradução literal, palavra por palavra, de CADA palavra de original_text na ordem (sem pontuação), com o \
sentido que ela tem nesta frase. Contrações viram 1 item (\"let's\" = \"vamos\"). Pode ficar estranho em português, \
é de propósito.\n\
- Se não houver inglês legível, retorne original_text e translation vazios e explique em grammar_notes.";

/// Teto de segurança contra geração descontrolada. As respostas reais ficam em ~100-700 tokens,
/// então isso nunca corta uma resposta normal (cortar no meio quebraria o JSON).
const MAX_OUTPUT_TOKENS: u32 = 4096;

/// Um client só pro app inteiro: reaproveita conexões (keep-alive/TLS) entre chamadas.
fn http() -> &'static reqwest::Client {
    static CLIENT: std::sync::OnceLock<reqwest::Client> = std::sync::OnceLock::new();
    CLIENT.get_or_init(reqwest::Client::new)
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
            provider: default_provider(),
            anthropic_base_url: default_anthropic_base_url(),
            anthropic_model: String::new(),
            google_base_url: default_google_base_url(),
            google_model: String::new(),
            favorites: std::collections::HashMap::new(),
        }
    }
}

#[derive(Serialize, Deserialize, Clone)]
struct ModelEntry {
    id: String,
    #[serde(default)]
    owned_by: Option<String>,
    #[serde(default)]
    capabilities: Option<serde_json::Value>,
}

#[derive(Deserialize)]
struct ModelsResponse {
    data: Vec<ModelEntry>,
}

#[derive(Serialize, Deserialize, Clone, Default)]
struct BreakdownItem {
    #[serde(default)]
    segment: String,
    #[serde(default)]
    pronunciation_pt: String,
    #[serde(default)]
    pronunciation_ipa: String,
    #[serde(default)]
    meaning: String,
    #[serde(default)]
    note: String,
}

#[derive(Serialize, Deserialize, Clone, Default)]
struct TranslationResult {
    #[serde(default)]
    original_text: String,
    #[serde(default)]
    translation: String,
    #[serde(default)]
    pronunciation_pt: String,
    #[serde(default)]
    pronunciation_ipa: String,
    #[serde(default)]
    breakdown: Vec<BreakdownItem>,
    #[serde(default)]
    grammar_notes: String,
    #[serde(default)]
    duration_secs: f64,
    #[serde(default)]
    model: String,
    /// true enquanto a resposta ainda está chegando (o balão mostra o que já tem + skeleton).
    #[serde(default)]
    pending: bool,
    /// Epoch ms da captura: identifica a tradução nos recentes/favoritos e ordena por data.
    #[serde(default)]
    id: String,
    /// [palavra, tradução literal] na ordem de original_text, pro hover no balão.
    #[serde(default, deserialize_with = "lenient_gloss")]
    gloss: Vec<(String, String)>,
}

/// A glosa é um extra: se o modelo devolver num formato inesperado, ela é descartada
/// em vez de derrubar o parse da tradução inteira.
fn lenient_gloss<'de, D: serde::Deserializer<'de>>(d: D) -> Result<Vec<(String, String)>, D::Error> {
    let value = serde_json::Value::deserialize(d)?;
    Ok(value
        .as_array()
        .map(|items| {
            items
                .iter()
                .filter_map(|pair| {
                    let pair = pair.as_array()?;
                    Some((pair.first()?.as_str()?.to_string(), pair.get(1)?.as_str()?.to_string()))
                })
                .collect()
        })
        .unwrap_or_default())
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

fn fill_pronunciations(result: &mut TranslationResult) {
    let path = lexicon_path();
    if let Some(p) = pronunciation::pronounce(&path, &result.original_text) {
        result.pronunciation_pt = p.ptbr;
        result.pronunciation_ipa = p.ipa;
    }
    for item in &mut result.breakdown {
        if let Some(p) = pronunciation::pronounce(&path, &item.segment) {
            item.pronunciation_pt = p.ptbr;
            item.pronunciation_ipa = p.ipa;
        }
    }
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

const ANTHROPIC_VERSION: &str = "2023-06-01";

#[tauri::command]
async fn list_models(
    base_url: String,
    api_key: String,
    provider: Option<String>,
) -> Result<Vec<ModelEntry>, String> {
    let client = http();

    if provider.as_deref() == Some("google") {
        let url = format!("{}/v1beta/models", base_url.trim_end_matches('/'));
        let response = client
            .get(&url)
            .header("x-goog-api-key", api_key)
            .send()
            .await
            .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            return Err(format!("API do Google respondeu {status}: {body}"));
        }

        let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
        let entries = body["models"]
            .as_array()
            .cloned()
            .unwrap_or_default()
            .into_iter()
            .filter_map(|m| {
                let name = m["name"].as_str()?;
                let id = name.strip_prefix("models/").unwrap_or(name).to_string();
                Some(ModelEntry {
                    id,
                    owned_by: None,
                    capabilities: None,
                })
            })
            .collect();
        return Ok(entries);
    }

    let url = format!("{}/v1/models", base_url.trim_end_matches('/'));
    let is_anthropic = provider.as_deref() == Some("anthropic");

    let request = if is_anthropic {
        client
            .get(&url)
            .header("x-api-key", api_key)
            .header("anthropic-version", ANTHROPIC_VERSION)
    } else {
        client.get(&url).bearer_auth(api_key)
    };

    let response = request
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("Gateway respondeu {status}: {body}"));
    }

    let parsed: ModelsResponse = response
        .json()
        .await
        .map_err(|e| format!("Resposta inesperada do gateway: {e}"))?;
    Ok(parsed.data)
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

fn parse_translation_json(raw: &str) -> Result<TranslationResult, String> {
    if let Ok(v) = serde_json::from_str::<TranslationResult>(raw) {
        return Ok(v);
    }
    if let (Some(start), Some(end)) = (raw.find('{'), raw.rfind('}')) {
        if end > start {
            if let Ok(v) = serde_json::from_str::<TranslationResult>(&raw[start..=end]) {
                return Ok(v);
            }
        }
    }
    Err(format!("Não consegui interpretar a resposta do modelo: {raw}"))
}

async fn call_omniroute_text(
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
) -> Result<String, String> {
    let url = format!("{}/v1/chat/completions", base_url.trim_end_matches('/'));
    let client = http();
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let payload = serde_json::json!({
        "model": model,
        "max_tokens": MAX_OUTPUT_TOKENS,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message}
        ]
    });
    let response = client
        .post(&url)
        .bearer_auth(api_key)
        .json(&payload)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("Gateway respondeu {status}: {body}"));
    }

    let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
    body["choices"][0]["message"]["content"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Resposta do gateway sem conteúdo".to_string())
}

enum StreamEvent {
    FirstToken,
    /// Um campo string do JSON acabou de fechar (valor já sem escapes).
    Field(&'static str, String),
}

/// Valor de `"campo": "..."` se a string já fechou no texto parcial; None se ainda está chegando.
fn closed_string_field(partial: &str, field: &str) -> Option<String> {
    let key = format!("\"{field}\"");
    let after_key = &partial[partial.find(&key)? + key.len()..];
    let after_colon = &after_key[after_key.find(':')? + 1..];
    let open = after_colon.find('"')?;
    if !after_colon[..open].trim().is_empty() {
        return None;
    }
    let mut escaped = false;
    for (i, c) in after_colon[open + 1..].char_indices() {
        if escaped {
            escaped = false;
        } else if c == '\\' {
            escaped = true;
        } else if c == '"' {
            return serde_json::from_str(&after_colon[open..open + 1 + i + 1]).ok();
        }
    }
    None
}

const STREAMED_FIELDS: [&str; 2] = ["original_text", "translation"];

/// Formato da API de cada provedor (o streaming muda só na requisição e em como ler cada pedaço).
#[derive(Clone, Copy, PartialEq, Debug)]
enum Wire {
    OpenAi,
    Anthropic,
    Google,
}

impl Wire {
    fn from_provider(provider: &str) -> Wire {
        match provider {
            "anthropic" => Wire::Anthropic,
            "google" => Wire::Google,
            _ => Wire::OpenAi,
        }
    }
}

/// Texto novo que chegou num evento do streaming (None se o evento não traz texto).
fn stream_delta(wire: Wire, event: &serde_json::Value) -> Option<String> {
    match wire {
        Wire::OpenAi => event["choices"][0]["delta"]["content"].as_str().map(str::to_string),
        // content_block_delta/text_delta; os outros eventos (message_start, ping...) não têm texto.
        Wire::Anthropic => (event["type"] == "content_block_delta" && event["delta"]["type"] == "text_delta")
            .then(|| event["delta"]["text"].as_str().map(str::to_string))
            .flatten(),
        // Partes marcadas como "thought" são o raciocínio interno do Gemini, não a resposta.
        Wire::Google => {
            let parts = event["candidates"][0]["content"]["parts"].as_array()?;
            let text: String = parts
                .iter()
                .filter(|p| p["thought"] != true)
                .filter_map(|p| p["text"].as_str())
                .collect();
            (!text.is_empty()).then_some(text)
        }
    }
}

fn stream_out_tokens(wire: Wire, event: &serde_json::Value) -> Option<u64> {
    match wire {
        Wire::OpenAi => event["usage"]["completion_tokens"].as_u64(),
        Wire::Anthropic => event["usage"]["output_tokens"].as_u64(),
        Wire::Google => event["usageMetadata"]["candidatesTokenCount"].as_u64(),
    }
}

/// Resposta inteira de uma vez (gateway que ignora `stream`).
fn full_response_text(wire: Wire, body: &serde_json::Value) -> Option<String> {
    match wire {
        Wire::OpenAi => body["choices"][0]["message"]["content"].as_str().map(str::to_string),
        Wire::Anthropic => body["content"][0]["text"].as_str().map(str::to_string),
        Wire::Google => stream_delta(Wire::Google, body),
    }
}

/// Tradução com streaming nos 3 provedores: avisa `on_event` quando o primeiro token chega e
/// assim que original_text/translation fecham no JSON; devolve o texto completo + tokens de saída.
async fn stream_translation(
    wire: Wire,
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
    on_event: &mut (dyn FnMut(StreamEvent) + Send),
) -> Result<(String, Option<u64>), String> {
    let base = base_url.trim_end_matches('/');
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let (url, request) = match wire {
        Wire::OpenAi => {
            let url = format!("{base}/v1/chat/completions");
            let req = http().post(&url).bearer_auth(api_key).json(&serde_json::json!({
                "model": model,
                "max_tokens": MAX_OUTPUT_TOKENS,
                "stream": true,
                "stream_options": {"include_usage": true},
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_message}
                ]
            }));
            (url, req)
        }
        Wire::Anthropic => {
            let url = format!("{base}/v1/messages");
            let req = http()
                .post(&url)
                .header("x-api-key", api_key)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .json(&serde_json::json!({
                    "model": model,
                    "max_tokens": MAX_OUTPUT_TOKENS,
                    "stream": true,
                    "system": SYSTEM_PROMPT,
                    "messages": [{"role": "user", "content": user_message}]
                }));
            (url, req)
        }
        Wire::Google => {
            let url = format!("{base}/v1beta/models/{model}:streamGenerateContent?alt=sse");
            let req = http().post(&url).header("x-goog-api-key", api_key).json(&serde_json::json!({
                "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
                "contents": [{"role": "user", "parts": [{"text": user_message}]}],
                "generationConfig": {"maxOutputTokens": MAX_OUTPUT_TOKENS}
            }));
            (url, req)
        }
    };

    let mut response = request
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;
    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("A API respondeu {status}: {body}"));
    }

    let mut pending: Vec<u8> = Vec::new();
    let mut not_sse = String::new();
    let mut content = String::new();
    let mut out_tokens = None;
    let mut sent = [false; STREAMED_FIELDS.len()];

    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|e| format!("Conexão caiu no meio da resposta: {e}"))?
    {
        pending.extend_from_slice(&chunk);
        // Só processa linhas completas: um caractere UTF-8 pode vir partido entre chunks.
        while let Some(nl) = pending.iter().position(|&b| b == b'\n') {
            let line_bytes: Vec<u8> = pending.drain(..=nl).collect();
            let line = String::from_utf8_lossy(&line_bytes);
            let line = line.trim();
            let Some(data) = line.strip_prefix("data:") else {
                // "event: ..." (Anthropic) e ": comentário" fazem parte do SSE; o resto é
                // resposta sem streaming, guardada pro fallback lá embaixo.
                if !line.is_empty() && !line.starts_with("event:") && !line.starts_with(':') {
                    not_sse.push_str(line);
                }
                continue;
            };
            let data = data.trim();
            if data == "[DONE]" {
                continue;
            }
            let Ok(event) = serde_json::from_str::<serde_json::Value>(data) else {
                continue;
            };
            if let Some(err) = event.get("error") {
                return Err(format!("A API respondeu erro no meio da resposta: {err}"));
            }
            if let Some(n) = stream_out_tokens(wire, &event) {
                out_tokens = Some(n);
            }
            if let Some(delta) = stream_delta(wire, &event) {
                if content.is_empty() && !delta.is_empty() {
                    on_event(StreamEvent::FirstToken);
                }
                content.push_str(&delta);
                for (i, field) in STREAMED_FIELDS.iter().enumerate() {
                    if !sent[i] {
                        if let Some(value) = closed_string_field(&content, field) {
                            sent[i] = true;
                            on_event(StreamEvent::Field(field, value));
                        }
                    }
                }
            }
        }
    }

    if content.is_empty() {
        not_sse.push_str(&String::from_utf8_lossy(&pending));
        let body: serde_json::Value = serde_json::from_str(&not_sse)
            .map_err(|_| format!("Resposta inesperada da API: {not_sse}"))?;
        let text = full_response_text(wire, &body).ok_or_else(|| "Resposta da API sem conteúdo".to_string())?;
        return Ok((text, None));
    }
    Ok((content, out_tokens))
}

async fn call_anthropic_text(
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
) -> Result<String, String> {
    let url = format!("{}/v1/messages", base_url.trim_end_matches('/'));
    let client = http();
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let payload = serde_json::json!({
        "model": model,
        "max_tokens": MAX_OUTPUT_TOKENS,
        "system": SYSTEM_PROMPT,
        "messages": [
            {"role": "user", "content": user_message}
        ]
    });
    let response = client
        .post(&url)
        .header("x-api-key", api_key)
        .header("anthropic-version", ANTHROPIC_VERSION)
        .json(&payload)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("API da Anthropic respondeu {status}: {body}"));
    }

    let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
    body["content"][0]["text"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Resposta da Anthropic sem conteúdo".to_string())
}

async fn call_google_text(
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
) -> Result<String, String> {
    let url = format!(
        "{}/v1beta/models/{}:generateContent",
        base_url.trim_end_matches('/'),
        model
    );
    let client = http();
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let payload = serde_json::json!({
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"role": "user", "parts": [{"text": user_message}]}],
        "generationConfig": {"maxOutputTokens": MAX_OUTPUT_TOKENS}
    });
    let response = client
        .post(&url)
        .header("x-goog-api-key", api_key)
        .json(&payload)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("API do Google respondeu {status}: {body}"));
    }

    let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
    body["candidates"][0]["content"]["parts"][0]["text"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Resposta do Google sem conteúdo".to_string())
}

/// Texto de teste com cara de OCR real (caixa alta, 2 balões), pra medir o modelo com a
/// mesma carga de uma captura: prompt real + JSON completo (com breakdown e glosa).
const SPEED_TEST_TEXT: &str = "YOU REALLY HACKED IT TO PIECES.\nI DIDN'T THINK YOU'D GO THAT FAR, KID.";
const BENCH_TIMEOUT_SECS: u64 = 25;
const BENCH_CONCURRENCY: usize = 4;
const BENCH_FINALISTS: usize = 8;
const BENCH_EXTRA_RUNS: usize = 2;
const BENCH_FAVORITES: usize = 5;

/// Alguns modelos (ex.: qwen) às vezes "escapam" pro chinês no meio do português.
fn has_cjk(text: &str) -> bool {
    text.chars().any(|c| matches!(c as u32, 0x3040..=0x30FF | 0x3400..=0x4DBF | 0x4E00..=0x9FFF | 0xAC00..=0xD7AF))
}

/// Tempo até o JSON completo de uma tradução de teste. Conta como falha se estourar o
/// timeout ou se a resposta não vier no formato esperado (rápido mas inútil não serve).
async fn timed_translation(provider: &str, base_url: &str, api_key: &str, model: &str) -> Result<f64, String> {
    let started = std::time::Instant::now();
    let call = async {
        match provider {
            "anthropic" => call_anthropic_text(SPEED_TEST_TEXT, api_key, base_url, model).await,
            "google" => call_google_text(SPEED_TEST_TEXT, api_key, base_url, model).await,
            _ => call_omniroute_text(SPEED_TEST_TEXT, api_key, base_url, model).await,
        }
    };
    let raw = tokio::time::timeout(std::time::Duration::from_secs(BENCH_TIMEOUT_SECS), call)
        .await
        .map_err(|_| format!("sem resposta em {BENCH_TIMEOUT_SECS}s"))??;
    let parsed = parse_translation_json(&raw).map_err(|_| "resposta fora do JSON esperado".to_string())?;
    if parsed.translation.trim().is_empty() {
        return Err("resposta sem tradução".to_string());
    }
    if has_cjk(&parsed.translation) {
        return Err("tradução veio misturada com chinês/japonês".to_string());
    }
    Ok(started.elapsed().as_secs_f64())
}

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

const BACKUP_FORMAT: &str = "webtoon-translator-backup";
const PROVIDERS: [&str; 3] = ["openai", "anthropic", "google"];

/// Data UTC "AAAA-MM-DD_HH-MM" a partir de epoch ms (algoritmo days-from-civil do Howard Hinnant),
/// só pra dar nome legível ao arquivo sem puxar uma crate de datas.
fn utc_stamp(ms: u128) -> String {
    let secs = (ms / 1000) as i64;
    let (days, rem) = (secs.div_euclid(86_400), secs.rem_euclid(86_400));
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + if m <= 2 { 1 } else { 0 };
    format!("{y:04}-{m:02}-{d:02}_{:02}-{:02}", rem / 3600, rem % 3600 / 60)
}

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

/// Junta duas listas sem repetir id, mais recente primeiro (o id é o epoch ms da captura).
fn merge_translations(local: Vec<TranslationResult>, imported: Vec<TranslationResult>) -> Vec<TranslationResult> {
    let mut all = local;
    for t in imported {
        if !all.iter().any(|x| x.id == t.id) {
            all.push(t);
        }
    }
    all.sort_by_key(|t| std::cmp::Reverse(t.id.parse::<u128>().unwrap_or(0)));
    all
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
    fill_pronunciations(&mut result);
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
mod tts_smoke_test {
    use super::*;

    #[test]
    fn kokoro_generates_audio() {
        let tts = create_tts().expect("falha ao criar o motor de TTS");
        let gen_config = sherpa_onnx::GenerationConfig {
            sid: 0,
            speed: 1.0,
            ..Default::default()
        };
        let audio = tts
            .generate_with_config("All right, let's start on this side.", &gen_config, None::<fn(&[f32], f32) -> bool>)
            .expect("falha ao gerar áudio");
        assert!(audio.samples().len() > 1000, "áudio gerado está vazio demais");
        assert!(audio.sample_rate() > 0);
        println!(
            "OK: {} amostras, {} Hz, {:.2}s",
            audio.samples().len(),
            audio.sample_rate(),
            audio.samples().len() as f32 / audio.sample_rate() as f32
        );
    }
}

#[cfg(test)]
mod ocr_smoke_test {
    /// Precisa de um recorte de balão com "All right, let's start on this side":
    /// OCR_TEST_IMAGE=/caminho/balao.png cargo test --release -- --ignored ocrs_reads
    #[test]
    #[ignore]
    fn ocrs_reads_comic_bubble() {
        use ocrs::{ImageSource, OcrEngine, OcrEngineParams};
        use rten::Model;
        use std::time::Instant;

        let base = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("ocr-models");
        let detection_model = Model::load_file(base.join("text-detection.onnx")).unwrap();
        let recognition_model = Model::load_file(base.join("text-recognition.onnx")).unwrap();

        let engine = OcrEngine::new(OcrEngineParams {
            detection_model: Some(detection_model),
            recognition_model: Some(recognition_model),
            ..Default::default()
        })
        .unwrap();

        let img_path = std::env::var("OCR_TEST_IMAGE").expect("defina OCR_TEST_IMAGE");
        let img = image::open(img_path).unwrap().into_rgb8();
        let img_source = ImageSource::from_bytes(img.as_raw(), img.dimensions()).unwrap();

        let start = Instant::now();
        let ocr_input = engine.prepare_input(img_source).unwrap();
        let text = engine.get_text(&ocr_input).unwrap();
        let elapsed = start.elapsed();

        println!("Texto lido: {:?}", text);
        println!("Tempo: {:.3}s", elapsed.as_secs_f32());
        assert!(text.to_uppercase().contains("ALL RIGHT"));
        assert!(text.to_uppercase().contains("THIS SIDE"));
    }
}

#[cfg(test)]
mod streaming_tests {
    use super::*;

    #[test]
    fn campo_so_fecha_quando_a_string_termina() {
        assert_eq!(closed_string_field(r#"{"original_text": "And wha"#, "original_text"), None);
        assert_eq!(
            closed_string_field(r#"{"original_text": "He said \"hi\"\nok", "trans"#, "original_text"),
            Some("He said \"hi\"\nok".to_string())
        );
        assert_eq!(closed_string_field(r#"```json\n{"translation":"Oi""#, "translation"), Some("Oi".to_string()));
    }

    /// Roda contra o omniroute local: OMNI_KEY=... cargo test --release -- --ignored stream_real
    #[tokio::test]
    #[ignore]
    async fn stream_real() {
        let key = std::env::var("OMNI_KEY").expect("defina OMNI_KEY");
        let started = std::time::Instant::now();
        let mut events = Vec::new();
        let mut on_event = |e: StreamEvent| {
            let ms = started.elapsed().as_millis();
            match e {
                StreamEvent::FirstToken => events.push(format!("{ms}ms primeiro token")),
                StreamEvent::Field(f, v) => events.push(format!("{ms}ms {f} = {v:?}")),
            }
        };
        let (text, tokens) = stream_translation(
            Wire::OpenAi,
            "AND WHAT HAPPENED TO ITS OTHER ARM?",
            &key,
            "http://localhost",
            "antigravity/gemini-3-flash",
            &mut on_event,
        )
        .await
        .unwrap();
        for e in &events {
            println!("{e}");
        }
        let mut result = parse_translation_json(&text).unwrap();
        fill_pronunciations(&mut result);
        println!("{}ms total | {:?} tokens", started.elapsed().as_millis(), tokens);
        println!("{}", serde_json::to_string_pretty(&result).unwrap());
        assert_eq!(events.len(), 3);
        assert!(!result.pronunciation_pt.is_empty());
        assert!(result.gloss.len() >= 6, "glosa veio curta: {:?}", result.gloss);
    }

    #[test]
    fn le_o_texto_de_cada_formato_de_streaming() {
        let j = |s: &str| serde_json::from_str::<serde_json::Value>(s).unwrap();
        assert_eq!(stream_delta(Wire::OpenAi, &j(r#"{"choices":[{"delta":{"content":"Oi"}}]}"#)).as_deref(), Some("Oi"));
        assert_eq!(
            stream_delta(Wire::Anthropic, &j(r#"{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Oi"}}"#)).as_deref(),
            Some("Oi")
        );
        assert_eq!(stream_delta(Wire::Anthropic, &j(r#"{"type":"ping"}"#)), None);
        assert_eq!(stream_out_tokens(Wire::Anthropic, &j(r#"{"type":"message_delta","usage":{"output_tokens":42}}"#)), Some(42));
        // Formato de streamGenerateContent?alt=sse; a parte "thought" (raciocínio) fica de fora.
        let g = j(r#"{"candidates":[{"content":{"role":"model","parts":[{"text":"pensando...","thought":true},{"text":"{\"original_text\""}]}}],"usageMetadata":{"candidatesTokenCount":7}}"#);
        assert_eq!(stream_delta(Wire::Google, &g).as_deref(), Some("{\"original_text\""));
        assert_eq!(stream_out_tokens(Wire::Google, &g), Some(7));
    }

    /// Formato nativo da Anthropic, via omniroute: OMNI_KEY=... cargo test --release -- --ignored stream_anthropic
    #[tokio::test]
    #[ignore]
    async fn stream_anthropic_real() {
        let key = std::env::var("OMNI_KEY").expect("defina OMNI_KEY");
        let started = std::time::Instant::now();
        let mut fields = Vec::new();
        let mut on_event = |e: StreamEvent| {
            if let StreamEvent::Field(f, v) = e {
                fields.push(format!("{}ms {f} = {v:?}", started.elapsed().as_millis()));
            }
        };
        let (text, tokens) = stream_translation(
            Wire::Anthropic,
            "HE DIDN'T GO DOWN WITHOUT A FIGHT.",
            &key,
            "http://localhost",
            "claude/claude-haiku-4-5-20251001",
            &mut on_event,
        )
        .await
        .unwrap();
        for f in &fields {
            println!("{f}");
        }
        let r = parse_translation_json(&text).unwrap();
        println!("{}ms total | {tokens:?} tokens | {} | gloss {}", started.elapsed().as_millis(), r.translation, r.gloss.len());
        assert_eq!(fields.len(), 2);
        assert!(tokens.is_some());
    }

    #[test]
    fn glosa_malformada_nao_derruba_a_traducao() {
        let r = parse_translation_json(r#"{"original_text":"Hi","translation":"Oi","gloss":"hi:oi"}"#).unwrap();
        assert_eq!(r.translation, "Oi");
        assert!(r.gloss.is_empty());
        let r = parse_translation_json(r#"{"translation":"Oi","gloss":[["hi","oi"],["x"],[1,2]]}"#).unwrap();
        assert_eq!(r.gloss, vec![("hi".to_string(), "oi".to_string())]);
    }

    /// OMNI_KEY=... cargo test --release -- --ignored benchmark_real
    #[tokio::test]
    #[ignore]
    async fn benchmark_real() {
        let key = std::env::var("OMNI_KEY").expect("defina OMNI_KEY");
        for model in ["antigravity/gemini-3-flash", "modelo/que-nao-existe"] {
            let r = timed_translation("openai", "http://localhost", &key, model).await;
            println!("{model}: {r:?}");
        }
        assert!(timed_translation("openai", "http://localhost", &key, "antigravity/gemini-3-flash").await.is_ok());
        assert!(timed_translation("openai", "http://localhost", &key, "modelo/que-nao-existe").await.is_err());
    }
}

#[cfg(test)]
mod backup_tests {
    use super::*;

    #[test]
    fn nome_do_audio_e_estavel_e_ignora_caixa() {
        // Valores de referência do FNV-1a 64: se mudarem, os .wav já salvos ficariam órfãos.
        assert_eq!(fnv1a64(""), 0xcbf2_9ce4_8422_2325);
        assert_eq!(fnv1a64("a"), 0xaf63_dc4c_8601_ec8c);
        assert_eq!(favorite_audio_name("HEAD INSIDE"), favorite_audio_name("head  inside"));
        let t = TranslationResult {
            original_text: "Let's head inside now.".into(),
            breakdown: vec![BreakdownItem { segment: "head inside".into(), ..Default::default() }],
            ..Default::default()
        };
        assert_eq!(speech_texts(&t), ["Let's head inside now.", "head inside"]);
    }

    #[test]
    fn detecta_chines_no_meio_da_traducao() {
        assert!(has_cjk("Ele não caiu sem dar a挣扎."));
        assert!(!has_cjk("Ele não se rendeu sem lutar — ação, ênfase, à vontade."));
    }

    #[test]
    fn data_utc_do_nome_do_arquivo() {
        assert_eq!(utc_stamp(0), "1970-01-01_00-00");
        assert_eq!(utc_stamp(1_700_000_000_000), "2023-11-14_22-13");
        assert_eq!(utc_stamp(951_782_400_000), "2000-02-29_00-00");
    }

    #[test]
    fn mescla_sem_repetir_e_mais_recente_primeiro() {
        let t = |id: &str, txt: &str| TranslationResult { id: id.into(), translation: txt.into(), ..Default::default() };
        let merged = merge_translations(vec![t("300", "local"), t("100", "local")], vec![t("200", "importada"), t("300", "duplicada")]);
        let ids: Vec<&str> = merged.iter().map(|x| x.id.as_str()).collect();
        assert_eq!(ids, ["300", "200", "100"]);
        assert_eq!(merged[0].translation, "local");
    }
}
