//! OCR local (ocrs + rten, Rust puro: roda igual no desktop e no Android).

use std::path::Path;

pub use ocrs::OcrEngine;

/// Carrega os modelos de detecção e reconhecimento de `models_dir` (text-detection.onnx e
/// text-recognition.onnx).
pub fn load_engine(models_dir: &Path) -> Result<OcrEngine, String> {
    let base = models_dir;
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

/// Lê o texto de uma imagem RGB (3 bytes por pixel, linha a linha).
pub fn recognize_text(engine: &OcrEngine, rgb: &[u8], width: u32, height: u32) -> Result<String, String> {
    let img_source = ocrs::ImageSource::from_bytes(rgb, (width, height))
        .map_err(|e| format!("Falha ao preparar imagem pro OCR: {e}"))?;
    let ocr_input = engine
        .prepare_input(img_source)
        .map_err(|e| format!("Falha ao processar imagem pro OCR: {e}"))?;
    let text = engine
        .get_text(&ocr_input)
        .map_err(|e| format!("Falha ao extrair texto: {e}"))?;

    // Filtra detecções espúrias (letras soltas nas bordas de balões), igual o
    // próprio exemplo oficial do ocrs recomenda.
    Ok(text
        .lines()
        .filter(|l| l.trim().chars().count() > 1)
        .collect::<Vec<_>>()
        .join("\n"))
}
