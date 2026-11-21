//! Voz Kokoro (sherpa-onnx). Opcional: só existe com a feature `kokoro`.

use std::path::Path;

pub use sherpa_onnx::OfflineTts;

/// Carrega o Kokoro de `voice_dir` (model.onnx, voices.bin, tokens.txt), usando o dicionário
/// de pronúncia `lexicon` (o mesmo da pronúncia escrita, que vem junto com o app).
pub fn create_kokoro(voice_dir: &Path, lexicon: &Path) -> Result<OfflineTts, String> {
    let config = sherpa_onnx::OfflineTtsConfig {
        model: sherpa_onnx::OfflineTtsModelConfig {
            kokoro: sherpa_onnx::OfflineTtsKokoroModelConfig {
                model: Some(voice_dir.join("model.onnx").to_string_lossy().into_owned()),
                voices: Some(voice_dir.join("voices.bin").to_string_lossy().into_owned()),
                tokens: Some(voice_dir.join("tokens.txt").to_string_lossy().into_owned()),
                lexicon: Some(lexicon.to_string_lossy().into_owned()),
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

/// Gera fala em inglês; devolve (amostras em f32, taxa de amostragem).
pub fn synthesize(tts: &OfflineTts, text: &str, speed: f32) -> Result<(Vec<f32>, u32), String> {
    let config = sherpa_onnx::GenerationConfig { sid: 0, speed, ..Default::default() };
    let audio = tts
        .generate_with_config(text, &config, None::<fn(&[f32], f32) -> bool>)
        .ok_or_else(|| "Falha ao gerar áudio".to_string())?;
    Ok((audio.samples().to_vec(), audio.sample_rate() as u32))
}
