//! Núcleo do Webtoon Translator, sem dependência do Tauri: o app desktop e o Android usam o
//! mesmo código pra OCR, tradução com streaming, glosa, pronúncia e voz.

pub mod audio;
pub mod benchmark;
pub mod library;
pub mod ocr;
pub mod pronunciation;
pub mod text;
pub mod translate;
pub mod voice;
#[cfg(feature = "kokoro")]
pub mod tts;

#[cfg(test)]
mod tests;
