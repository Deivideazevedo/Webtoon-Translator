//! O que o app Android enxerga do núcleo: OCR, tradução com streaming, pronúncia e lista de
//! modelos. Os tipos aqui são cópias "planas" dos do core, no formato que o UniFFI exporta.

use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;

use webtoon_core::ocr::{self, OcrEngine};
use webtoon_core::pronunciation;
use webtoon_core::translate::{self, StreamEvent, Wire};

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
        self.with_ocr(|engine| ocr::recognize_text(engine, &rgb, width, height))
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
        Ok(Translation {
            original_text: result.original_text,
            translation: result.translation,
            pronunciation_pt: result.pronunciation_pt,
            pronunciation_ipa: result.pronunciation_ipa,
            segments: result
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
            grammar_notes: result.grammar_notes,
            gloss: result.gloss.into_iter().map(|(word, meaning)| Gloss { word, meaning }).collect(),
            model: request.model,
            duration_secs: started.elapsed().as_secs_f64(),
        })
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
