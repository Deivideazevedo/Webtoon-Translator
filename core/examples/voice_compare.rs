//! Compara vozes do sherpa-onnx com o mesmo motor e as mesmas threads do app.
//! Uso: voice_compare <tipo> <pasta_do_modelo> <pasta_saida> <sid> <frase>...
//! tipos: kokoro | vits | kitten | pocket | supertonic
//! Imprime uma linha `RESULT {json}` no fim, que o scripts/compare-voices.sh lê.

use sherpa_onnx::*;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::time::Instant;
use sysinfo::{Pid, ProcessRefreshKind, ProcessesToUpdate, System};

fn rss_mb() -> f64 {
    let Ok(mut sys) = System::new() else { return 0.0 };
    let pid = Pid::from_u32(std::process::id());
    sys.refresh_processes_specifics(ProcessesToUpdate::Some(&[pid]), true, ProcessRefreshKind::nothing().with_memory());
    sys.process(pid).map(|p| p.memory() as f64 / 1_048_576.0).unwrap_or(0.0)
}

fn s(p: PathBuf) -> Option<String> {
    Some(p.to_string_lossy().into_owned())
}

/// Primeiro arquivo da pasta que satisfaz o filtro (os nomes mudam entre pacotes).
fn find(dir: &Path, pred: impl Fn(&str) -> bool) -> PathBuf {
    std::fs::read_dir(dir)
        .unwrap()
        .flatten()
        .map(|e| e.path())
        .find(|p| p.file_name().and_then(|n| n.to_str()).is_some_and(&pred))
        .unwrap_or_else(|| panic!("arquivo não encontrado em {dir:?}"))
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let (kind, dir, out) = (args[1].as_str(), PathBuf::from(&args[2]), PathBuf::from(&args[3]));
    let sid: i32 = args[4].parse().unwrap();
    let phrases = &args[5..];
    let espeak = s(dir.join("espeak-ng-data"));

    let mut model = OfflineTtsModelConfig {
        num_threads: std::thread::available_parallelism().map(|n| n.get() as i32).unwrap_or(2),
        debug: std::env::var("SHERPA_DEBUG").is_ok(),
        ..Default::default()
    };
    let mut gen = GenerationConfig { sid, speed: 1.0, ..Default::default() };
    match kind {
        "kokoro" => {
            model.kokoro = OfflineTtsKokoroModelConfig {
                model: s(dir.join("model.onnx")),
                voices: s(dir.join("voices.bin")),
                tokens: s(dir.join("tokens.txt")),
                lexicon: Some(std::env::var("LEXICON").expect("defina LEXICON (lexicon-en-us.txt)")),
                ..Default::default()
            }
        }
        "vits" => {
            model.vits = OfflineTtsVitsModelConfig {
                model: s(find(&dir, |n| n.ends_with(".onnx"))),
                tokens: s(dir.join("tokens.txt")),
                data_dir: espeak.clone(),
                lexicon: std::env::var("LEXICON").ok(),
                ..Default::default()
            }
        }
        "kitten" => {
            model.kitten = OfflineTtsKittenModelConfig {
                model: s(find(&dir, |n| n.ends_with(".onnx"))),
                voices: s(dir.join("voices.bin")),
                tokens: s(dir.join("tokens.txt")),
                data_dir: espeak.clone(),
                lexicon: std::env::var("LEXICON").ok(),
                ..Default::default()
            }
        }
        "pocket" => {
            model.pocket = OfflineTtsPocketModelConfig {
                lm_flow: s(dir.join("lm_flow.onnx")),
                lm_main: s(dir.join("lm_main.onnx")),
                encoder: s(dir.join("encoder.onnx")),
                decoder: s(dir.join("decoder.onnx")),
                text_conditioner: s(dir.join("text_conditioner.onnx")),
                vocab_json: s(dir.join("vocab.json")),
                token_scores_json: s(dir.join("token_scores.json")),
                voice_embedding_cache_capacity: 50,
            };
            // O Pocket imita a voz de um áudio de referência (o pacote traz exemplos).
            let wave = Wave::read(&dir.join("test_wavs/bria.wav").to_string_lossy()).expect("áudio de referência");
            gen.reference_audio = Some(wave.samples().to_vec());
            gen.reference_sample_rate = wave.sample_rate();
            gen.extra = Some(HashMap::from([("seed".to_string(), serde_json::json!(42))]));
        }
        "supertonic" => {
            model.supertonic = OfflineTtsSupertonicModelConfig {
                duration_predictor: s(find(&dir, |n| n.starts_with("duration_predictor"))),
                text_encoder: s(find(&dir, |n| n.starts_with("text_encoder"))),
                vector_estimator: s(find(&dir, |n| n.starts_with("vector_estimator"))),
                vocoder: s(find(&dir, |n| n.starts_with("vocoder"))),
                tts_json: s(dir.join("tts.json")),
                unicode_indexer: s(dir.join("unicode_indexer.bin")),
                voice_style: s(dir.join("voice.bin")),
            };
            gen.num_steps = 8;
            gen.extra = Some(HashMap::from([("lang".to_string(), serde_json::json!("en"))]));
        }
        other => panic!("tipo desconhecido: {other}"),
    }

    let before = rss_mb();
    let t = Instant::now();
    let tts = OfflineTts::create(&OfflineTtsConfig { model, ..Default::default() }).expect("falha ao carregar a voz");
    let load_ms = t.elapsed().as_secs_f64() * 1000.0;
    let after_load = rss_mb();
    println!("carregar: {load_ms:.0} ms | RAM {before:.0} → {after_load:.0} MB");

    let mut results = Vec::new();
    for (i, phrase) in phrases.iter().enumerate() {
        let mut best = f64::MAX;
        let mut secs = 0.0;
        for rep in 0..2 {
            let t = Instant::now();
            let audio = tts
                .generate_with_config(phrase, &gen, None::<fn(&[f32], f32) -> bool>)
                .expect("falha ao gerar");
            let ms = t.elapsed().as_secs_f64() * 1000.0;
            best = best.min(ms);
            secs = audio.samples().len() as f64 / audio.sample_rate() as f64;
            if rep == 0 {
                let wav = webtoon_core::audio::wav_bytes(audio.samples(), audio.sample_rate() as u32);
                std::fs::write(out.join(format!("frase{}.wav", i + 1)), wav).unwrap();
            }
        }
        println!("frase {} ({} chars): {best:.0} ms pra {secs:.2}s de áudio (RTF {:.2})", i + 1, phrase.chars().count(), best / 1000.0 / secs);
        results.push(serde_json::json!({"ms": best.round(), "audio_s": (secs * 100.0).round() / 100.0, "rtf": ((best / 1000.0 / secs) * 100.0).round() / 100.0}));
    }
    println!(
        "RESULT {}",
        serde_json::json!({"load_ms": load_ms.round(), "ram_mb": after_load.round(), "ram_end_mb": rss_mb().round(), "phrases": results})
    );
}
