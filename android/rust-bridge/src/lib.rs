//! O que o app Android enxerga do núcleo: OCR, tradução com streaming, pronúncia, lista de
//! modelos e traduções salvas/backup. Os tipos aqui são cópias "planas" dos do core, no formato
//! que o UniFFI exporta.

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;

use webtoon_core::benchmark;
use webtoon_core::library::{self, Library, List};
use webtoon_core::ocr::{self, OcrEngine};
use webtoon_core::pronunciation;
use webtoon_core::translate::{self, BreakdownItem, StreamEvent, TranslationResult, Wire};

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum BridgeError {
    // "reason" e não "message": no Kotlin vira exceção, e `message` já existe em Throwable.
    #[error("{reason}")]
    Failed { reason: String },
}

impl From<String> for BridgeError {
    fn from(reason: String) -> Self {
        BridgeError::Failed { reason }
    }
}

#[derive(uniffi::Record)]
pub struct Pronunciation {
    pub ptbr: String,
    pub ipa: String,
}

#[derive(uniffi::Record)]
pub struct TranslateRequest {
    /// "google", "anthropic" ou "openai" (gateway compatível, ex.: omniroute).
    pub provider: String,
    pub base_url: String,
    pub api_key: String,
    pub model: String,
    pub text: String,
}

#[derive(uniffi::Record)]
pub struct Segment {
    pub text: String,
    pub pronunciation_pt: String,
    pub pronunciation_ipa: String,
    pub meaning: String,
    pub note: String,
}

#[derive(uniffi::Record)]
pub struct Gloss {
    pub word: String,
    pub meaning: String,
}

#[derive(uniffi::Record)]
pub struct Translation {
    /// Epoch ms da tradução: identifica nos recentes/favoritos (igual ao desktop).
    pub id: String,
    pub original_text: String,
    pub translation: String,
    pub pronunciation_pt: String,
    pub pronunciation_ipa: String,
    pub segments: Vec<Segment>,
    pub grammar_notes: String,
    pub gloss: Vec<Gloss>,
    pub model: String,
    pub duration_secs: f64,
}

/// Balão encontrado na tela (modo tela cheia), com a caixa em pixels da imagem.
#[derive(uniffi::Record)]
pub struct TextBlock {
    pub text: String,
    pub left: i32,
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
}

impl From<TranslationResult> for Translation {
    fn from(r: TranslationResult) -> Self {
        Translation {
            id: r.id,
            original_text: r.original_text,
            translation: r.translation,
            pronunciation_pt: r.pronunciation_pt,
            pronunciation_ipa: r.pronunciation_ipa,
            segments: r
                .breakdown
                .into_iter()
                .map(|b| Segment {
                    text: b.segment,
                    pronunciation_pt: b.pronunciation_pt,
                    pronunciation_ipa: b.pronunciation_ipa,
                    meaning: b.meaning,
                    note: b.note,
                })
                .collect(),
            grammar_notes: r.grammar_notes,
            gloss: r.gloss.into_iter().map(|(word, meaning)| Gloss { word, meaning }).collect(),
            model: r.model,
            duration_secs: r.duration_secs,
        }
    }
}

impl From<Translation> for TranslationResult {
    fn from(t: Translation) -> Self {
        TranslationResult {
            id: t.id,
            original_text: t.original_text,
            translation: t.translation,
            pronunciation_pt: t.pronunciation_pt,
            pronunciation_ipa: t.pronunciation_ipa,
            breakdown: t
                .segments
                .into_iter()
                .map(|s| BreakdownItem {
                    segment: s.text,
                    pronunciation_pt: s.pronunciation_pt,
                    pronunciation_ipa: s.pronunciation_ipa,
                    meaning: s.meaning,
                    note: s.note,
                })
                .collect(),
            grammar_notes: t.grammar_notes,
            gloss: t.gloss.into_iter().map(|g| (g.word, g.meaning)).collect(),
            model: t.model,
            duration_secs: t.duration_secs,
            pending: false,
        }
    }
}

/// Resultado de um modelo no benchmark (igual ao do desktop).
#[derive(uniffi::Record)]
pub struct BenchmarkEntry {
    pub model: String,
    /// Mediana dos tempos em segundos; None se falhou.
    pub secs: Option<f64>,
    pub error: Option<String>,
    /// Entrou nos mais rápidos e confiáveis (sugestão de favorito).
    pub favorite: bool,
}

/// Andamento do benchmark, chamado fora da thread de UI.
#[uniffi::export(with_foreign)]
pub trait BenchmarkListener: Send + Sync {
    fn on_progress(&self, done: u32, total: u32);
}

/// Avisos do streaming, chamados fora da thread de UI.
#[uniffi::export(with_foreign)]
pub trait TranslateListener: Send + Sync {
    /// `field` é "original_text" ou "translation"; `value` já é o texto final daquele campo.
    fn on_field(&self, field: String, value: String);
}

#[derive(uniffi::Object)]
pub struct Engine {
    models_dir: PathBuf,
    lexicon: PathBuf,
    ocr: Mutex<Option<OcrEngine>>,
}

impl Engine {
    fn with_ocr<T>(&self, f: impl FnOnce(&OcrEngine) -> Result<T, String>) -> Result<T, BridgeError> {
        let mut guard = self.ocr.lock().map_err(|e| e.to_string())?;
        if guard.is_none() {
            *guard = Some(ocr::load_engine(&self.models_dir)?);
        }
        Ok(f(guard.as_ref().expect("OCR carregado acima"))?)
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl Engine {
    /// `models_dir` tem text-detection.onnx e text-recognition.onnx; `lexicon_path` é o
    /// lexicon-en-us.txt da pronúncia.
    #[uniffi::constructor]
    pub fn new(models_dir: String, lexicon_path: String) -> Arc<Self> {
        Arc::new(Engine { models_dir: models_dir.into(), lexicon: lexicon_path.into(), ocr: Mutex::new(None) })
    }

    /// Carrega OCR e dicionário antes da primeira captura (chamar em background).
    pub fn warm_up(&self) -> Result<(), BridgeError> {
        self.with_ocr(|_| Ok(()))?;
        pronunciation::warm_up(&self.lexicon);
        Ok(())
    }

    /// Texto de uma imagem RGBA (4 bytes por pixel, como `Bitmap.copyPixelsToBuffer` em ARGB_8888).
    pub fn recognize(&self, rgba: Vec<u8>, width: u32, height: u32) -> Result<String, BridgeError> {
        let rgb: Vec<u8> = rgba.chunks_exact(4).flat_map(|p| [p[0], p[1], p[2]]).collect();
        // Linhas do mesmo balão juntas num parágrafo (o modelo traduz a frase, não linha a linha).
        self.with_ocr(|engine| ocr::recognize_paragraphs(engine, &rgb, width, height))
    }

    /// Balões de uma imagem RGBA inteira (linhas do OCR agrupadas por proximidade).
    pub fn recognize_blocks(&self, rgba: Vec<u8>, width: u32, height: u32) -> Result<Vec<TextBlock>, BridgeError> {
        let rgb: Vec<u8> = rgba.chunks_exact(4).flat_map(|p| [p[0], p[1], p[2]]).collect();
        let lines = self.with_ocr(|engine| ocr::recognize_lines(engine, &rgb, width, height))?;
        Ok(ocr::group_blocks(&lines)
            .into_iter()
            .map(|b| TextBlock { text: b.text, left: b.left, top: b.top, right: b.right, bottom: b.bottom })
            .collect())
    }

    /// Junta em balões linhas lidas por outro OCR (o do Google, no Android), com a mesma regra
    /// usada nas linhas do ocrs ([ocr::group_blocks]).
    pub fn group_lines(&self, lines: Vec<TextBlock>) -> Vec<TextBlock> {
        let lines: Vec<ocr::OcrLine> = lines
            .into_iter()
            .map(|l| ocr::OcrLine { text: l.text, left: l.left, top: l.top, right: l.right, bottom: l.bottom })
            .collect();
        ocr::group_blocks(&lines)
            .into_iter()
            .map(|b| TextBlock { text: b.text, left: b.left, top: b.top, right: b.right, bottom: b.bottom })
            .collect()
    }

    /// Tradução curta de vários balões numa chamada só; uma por texto, na mesma ordem.
    pub async fn translate_blocks(
        &self,
        provider: String,
        base_url: String,
        api_key: String,
        model: String,
        texts: Vec<String>,
    ) -> Result<Vec<String>, BridgeError> {
        Ok(translate::translate_blocks(Wire::from_provider(&provider), &texts, &api_key, &base_url, &model).await?)
    }

    pub fn pronounce(&self, text: String) -> Option<Pronunciation> {
        pronunciation::pronounce(&self.lexicon, &text).map(|p| Pronunciation { ptbr: p.ptbr, ipa: p.ipa })
    }

    pub async fn translate(
        &self,
        request: TranslateRequest,
        listener: Arc<dyn TranslateListener>,
    ) -> Result<Translation, BridgeError> {
        let started = Instant::now();
        let mut on_event = |event: StreamEvent| {
            if let StreamEvent::Field(field, value) = event {
                listener.on_field(field.to_string(), value);
            }
        };
        let (raw, _tokens) = translate::stream_translation(
            Wire::from_provider(&request.provider),
            &request.text,
            &request.api_key,
            &request.base_url,
            &request.model,
            &mut on_event,
        )
        .await?;
        let mut result = translate::parse_translation_json(&raw)?;
        translate::fill_pronunciations(&mut result, &self.lexicon);
        result.id = library::now_ms().to_string();
        result.model = request.model;
        result.duration_secs = started.elapsed().as_secs_f64();
        Ok(result.into())
    }

    /// Tempo (segundos) de uma tradução de teste com o prompt real.
    pub async fn test_speed(&self, provider: String, base_url: String, api_key: String, model: String) -> Result<f64, BridgeError> {
        Ok(translate::timed_translation(&provider, &base_url, &api_key, &model).await?)
    }

    /// Mede todos os `models` (core::benchmark, o mesmo do desktop); do mais rápido pro mais lento.
    pub async fn benchmark(
        &self,
        provider: String,
        base_url: String,
        api_key: String,
        models: Vec<String>,
        listener: Arc<dyn BenchmarkListener>,
    ) -> Result<Vec<BenchmarkEntry>, BridgeError> {
        let entries = benchmark::run(&provider, &base_url, &api_key, &models, &|p: benchmark::Progress| {
            listener.on_progress(p.done as u32, p.total as u32)
        })
        .await?;
        Ok(entries
            .into_iter()
            .map(|e| BenchmarkEntry { model: e.model, secs: e.secs, error: e.error, favorite: e.favorite })
            .collect())
    }

    /// IDs dos modelos disponíveis pra essa chave.
    pub async fn list_models(
        &self,
        provider: String,
        base_url: String,
        api_key: String,
    ) -> Result<Vec<String>, BridgeError> {
        let models = translate::fetch_models(base_url, api_key, Some(provider)).await?;
        Ok(models.into_iter().map(|m| m.id).collect())
    }
}

#[derive(uniffi::Record)]
pub struct StorageUsage {
    pub recent_bytes: u64,
    pub favorites_bytes: u64,
    pub recent_max: u32,
}

#[derive(uniffi::Record)]
pub struct ImportSummary {
    /// Configurações do backup (JSON no formato do desktop), pro app aplicar; None se não vieram.
    pub settings_json: Option<String>,
    /// provedor → chave de API
    pub api_keys: HashMap<String, String>,
    pub recent: u32,
    pub favorites: u32,
}

/// Recentes, favoritas e backup: o mesmo código e os mesmos arquivos do desktop.
#[derive(uniffi::Object)]
pub struct TranslationLibrary {
    inner: Library,
}

#[uniffi::export]
impl TranslationLibrary {
    #[uniffi::constructor]
    pub fn new(dir: String) -> Arc<Self> {
        Arc::new(TranslationLibrary { inner: Library::new(dir) })
    }

    pub fn recent(&self) -> Result<Vec<Translation>, BridgeError> {
        Ok(self.inner.read(List::Recent)?.into_iter().map(Into::into).collect())
    }

    pub fn favorites(&self) -> Result<Vec<Translation>, BridgeError> {
        Ok(self.inner.read(List::Favorites)?.into_iter().map(Into::into).collect())
    }

    pub fn add_recent(&self, translation: Translation) -> Result<(), BridgeError> {
        Ok(self.inner.add_recent(translation.into())?)
    }

    pub fn set_favorite(&self, translation: Translation, favorite: bool) -> Result<(), BridgeError> {
        Ok(self.inner.set_favorite(translation.into(), favorite)?)
    }

    /// `list`: "recent" ou "favorites".
    pub fn delete(&self, list: String, ids: Vec<String>) -> Result<(), BridgeError> {
        Ok(self.inner.delete(List::from_name(&list), &ids)?)
    }

    pub fn usage(&self) -> StorageUsage {
        StorageUsage {
            recent_bytes: self.inner.bytes(List::Recent),
            favorites_bytes: self.inner.bytes(List::Favorites),
            recent_max: library::MAX_RECENT as u32,
        }
    }

    /// `settings_json`: configurações no formato do desktop.
    pub fn export_backup(&self, settings_json: String, api_keys: HashMap<String, String>) -> Result<String, BridgeError> {
        let settings = serde_json::from_str(&settings_json).map_err(|e| e.to_string())?;
        let keys = api_keys.into_iter().map(|(k, v)| (k, serde_json::Value::String(v))).collect();
        Ok(self.inner.export_backup(settings, keys)?)
    }

    pub fn import_backup(&self, json: String) -> Result<ImportSummary, BridgeError> {
        let imported = self.inner.import_backup(&json)?;
        Ok(ImportSummary {
            settings_json: imported.settings.map(|s| s.to_string()),
            api_keys: imported.api_keys.into_iter().collect(),
            recent: imported.recent as u32,
            favorites: imported.favorites as u32,
        })
    }
}
