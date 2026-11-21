//! Benchmark da voz Kokoro com o mesmo código do app (webtoon_core::tts).
//! Uso: tts_bench <pasta_da_voz> <pasta_saida_wav> <speed> <frase>...

use std::time::Instant;
use sysinfo::{Pid, ProcessRefreshKind, ProcessesToUpdate, System};
use webtoon_core::{audio::wav_bytes, tts};

fn rss_mb() -> f64 {
    let Ok(mut sys) = System::new() else { return 0.0 };
    let pid = Pid::from_u32(std::process::id());
    sys.refresh_processes_specifics(ProcessesToUpdate::Some(&[pid]), true, ProcessRefreshKind::nothing().with_memory());
    sys.process(pid).map(|p| p.memory() as f64 / 1_048_576.0).unwrap_or(0.0)
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let voice_dir = std::path::PathBuf::from(&args[1]);
    let out_dir = std::path::PathBuf::from(&args[2]);
    let speed: f32 = args[3].parse().unwrap();
    let phrases = &args[4..];
    let lexicon = voice_dir.parent().unwrap().join("lexicon-en-us.txt");

    println!("RAM antes de carregar: {:.0} MB", rss_mb());
    let t = Instant::now();
    let voices_dir = voice_dir.parent().unwrap();
    let engine = tts::create_engine(&webtoon_core::voice::KOKORO, voices_dir, &lexicon).expect("falha ao carregar modelo");
    println!("Carregar modelo: {:.0} ms | RAM depois de carregar: {:.0} MB", t.elapsed().as_secs_f64() * 1000.0, rss_mb());

    for (i, phrase) in phrases.iter().enumerate() {
        for rep in 1..=2 {
            let t = Instant::now();
            let (samples, rate) = tts::synthesize(&engine, phrase, speed, 0).expect("falha ao gerar áudio");
            let ms = t.elapsed().as_secs_f64() * 1000.0;
            let secs = samples.len() as f64 / rate as f64;
            println!(
                "frase {} ({} chars) {}ª vez: {:>5.0} ms pra gerar {:.2}s de áudio (RTF {:.2}) | RAM {:.0} MB",
                i + 1, phrase.chars().count(), rep, ms, secs, ms / 1000.0 / secs, rss_mb()
            );
            if rep == 1 {
                std::fs::write(out_dir.join(format!("frase{}.wav", i + 1)), wav_bytes(&samples, rate)).unwrap();
            }
        }
    }
}
