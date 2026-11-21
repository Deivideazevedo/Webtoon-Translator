//! Utilitários de texto sem dependência de plataforma.

/// Caixa alta não muda o áudio do Kokoro (medido), então a chave ignora maiúsculas/espaços.
pub fn normalize_for_speech(text: &str) -> String {
    text.split_whitespace().collect::<Vec<_>>().join(" ").to_lowercase()
}

/// FNV-1a 64: hash estável entre versões do Rust (o DefaultHasher não garante isso), pra o
/// nome do arquivo continuar o mesmo depois de atualizar o app.
pub fn fnv1a64(text: &str) -> u64 {
    text.bytes().fold(0xcbf2_9ce4_8422_2325, |h, b| (h ^ b as u64).wrapping_mul(0x0100_0000_01b3))
}

/// Alguns modelos (ex.: qwen) às vezes "escapam" pro chinês no meio do português.
pub fn has_cjk(text: &str) -> bool {
    text.chars().any(|c| matches!(c as u32, 0x3040..=0x30FF | 0x3400..=0x4DBF | 0x4E00..=0x9FFF | 0xAC00..=0xD7AF))
}

/// Data UTC "AAAA-MM-DD_HH-MM" a partir de epoch ms (algoritmo days-from-civil do Howard Hinnant),
/// só pra dar nome legível ao arquivo sem puxar uma crate de datas.
pub fn utc_stamp(ms: u128) -> String {
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
