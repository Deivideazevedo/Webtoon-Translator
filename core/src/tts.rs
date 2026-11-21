//! Motor de voz (sherpa-onnx) pras vozes do catálogo em `voice`. Opcional: só existe com a
//! feature `kokoro` (nome histórico; inclui todas as vozes do sherpa-onnx).

use std::path::Path;

use crate::voice::{voice_dir, VoiceKind, VoicePackage};

pub use sherpa_onnx::OfflineTts;

/// Carrega a voz instalada em `voices_dir`. A pronúncia vem do nosso dicionário (`lexicon`),
/// o mesmo da pronúncia escrita: o sherpa-onnx 2.x não traz mais o espeak-ng embutido.
pub fn create_engine(voice: &VoicePackage, voices_dir: &Path, lexicon: &Path) -> Result<OfflineTts, String> {
    let dir = voice_dir(voices_dir, voice);
    let path = |name: &str| Some(dir.join(name).to_string_lossy().into_owned());
    let lexicon = Some(lexicon.to_string_lossy().into_owned());
    let mut model = sherpa_onnx::OfflineTtsModelConfig {
        num_threads: std::thread::available_parallelism().map(|n| n.get() as i32).unwrap_or(2),
        ..Default::default()
    };
    match voice.kind {
        VoiceKind::Kokoro => {
            model.kokoro = sherpa_onnx::OfflineTtsKokoroModelConfig {
                model: path(voice.model_file),
                voices: path("voices.bin"),
                tokens: path("tokens.txt"),
                lexicon,
                length_scale: 1.0,
                ..Default::default()
            }
        }
        VoiceKind::Vits => {
            model.vits = sherpa_onnx::OfflineTtsVitsModelConfig {
                model: path(voice.model_file),
                tokens: path("tokens.txt"),
                lexicon,
                ..Default::default()
            }
        }
    }
    sherpa_onnx::OfflineTts::create(&sherpa_onnx::OfflineTtsConfig { model, ..Default::default() })
        .ok_or_else(|| format!("Não consegui carregar a voz {}.", voice.name))
}

/// Gera fala em inglês com a voz `sid`; devolve (amostras em f32, taxa de amostragem).
pub fn synthesize(tts: &OfflineTts, text: &str, speed: f32, sid: i32) -> Result<(Vec<f32>, u32), String> {
    let config = sherpa_onnx::GenerationConfig { sid, speed, ..Default::default() };
    let audio = tts
        .generate_with_config(text, &config, None::<fn(&[f32], f32) -> bool>)
        .ok_or_else(|| "Falha ao gerar áudio".to_string())?;
    Ok((audio.samples().to_vec(), audio.sample_rate() as u32))
}
