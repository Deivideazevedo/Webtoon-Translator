//! Benchmark do TTS com a mesma configuração do app (lib.rs::create_tts), mas com o
//! diretório do modelo como parâmetro, pra comparar o Kokoro atual com outro.
//! Uso: tts_bench <dir_do_modelo> <arquivo_onnx> <dir_saida_wav> <speed> <frase>...

use std::time::Instant;
use sysinfo::{Pid, ProcessRefreshKind, ProcessesToUpdate, System};

fn rss_mb() -> f64 {
    let Ok(mut sys) = System::new() else { return 0.0 };
    let pid = Pid::from_u32(std::process::id());
    sys.refresh_processes_specifics(ProcessesToUpdate::Some(&[pid]), true, ProcessRefreshKind::nothing().with_memory());
    sys.process(pid).map(|p| p.memory() as f64 / 1_048_576.0).unwrap_or(0.0)
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let model_dir = std::path::PathBuf::from(&args[1]);
    let onnx = &args[2];
    let out_dir = std::path::PathBuf::from(&args[3]);
    let speed: f32 = args[4].parse().unwrap();
    let phrases = &args[5..];
    let lexicon = model_dir.parent().unwrap().join("lexicon-en-us.txt");

    println!("RAM antes de carregar: {:.0} MB", rss_mb());
    let t = Instant::now();
    let config = sherpa_onnx::OfflineTtsConfig {
        model: sherpa_onnx::OfflineTtsModelConfig {
            kokoro: sherpa_onnx::OfflineTtsKokoroModelConfig {
                model: Some(model_dir.join(onnx).to_string_lossy().into_owned()),
                voices: Some(model_dir.join("voices.bin").to_string_lossy().into_owned()),
                tokens: Some(model_dir.join("tokens.txt").to_string_lossy().into_owned()),
                lexicon: Some(lexicon.to_string_lossy().into_owned()),
                length_scale: 1.0,
                ..Default::default()
            },
            num_threads: std::thread::available_parallelism().map(|n| n.get() as i32).unwrap_or(2),
            debug: false,
            ..Default::default()
        },
        ..Default::default()
    };
    let tts = sherpa_onnx::OfflineTts::create(&config).expect("falha ao carregar modelo");
    println!("Carregar modelo: {:.0} ms | RAM depois de carregar: {:.0} MB", t.elapsed().as_secs_f64() * 1000.0, rss_mb());

    let gen = sherpa_onnx::GenerationConfig { sid: 0, speed, ..Default::default() };
    for (i, phrase) in phrases.iter().enumerate() {
        for rep in 1..=2 {
            let t = Instant::now();
            let audio = tts
                .generate_with_config(phrase, &gen, None::<fn(&[f32], f32) -> bool>)
                .expect("falha ao gerar áudio");
            let ms = t.elapsed().as_secs_f64() * 1000.0;
            let secs = audio.samples().len() as f64 / audio.sample_rate() as f64;
            println!(
                "frase {} ({} chars) {}ª vez: {:>5.0} ms pra gerar {:.2}s de áudio (RTF {:.2}) | RAM {:.0} MB",
                i + 1, phrase.chars().count(), rep, ms, secs, ms / 1000.0 / secs, rss_mb()
            );
            if rep == 1 {
                audio.save(&out_dir.join(format!("frase{}.wav", i + 1)).to_string_lossy());
            }
        }
    }
}
