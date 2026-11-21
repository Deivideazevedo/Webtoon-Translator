//! Núcleo do Webtoon Translator, sem dependência do Tauri: o app desktop e o Android usam o
//! mesmo código pra OCR, tradução com streaming, glosa, pronúncia e voz.

pub mod audio;
pub mod ocr;
pub mod pronunciation;
pub mod text;
pub mod translate;
#[cfg(feature = "kokoro")]
pub mod tts;

#[cfg(test)]
mod tests;
