//! Testes do núcleo. Os modelos de OCR e o dicionário ficam na pasta do app desktop
//! (src-tauri), que é de onde o instalador os empacota.

#![allow(unused_imports)]

use std::path::{Path, PathBuf};

use crate::text::*;
use crate::translate::*;

fn models_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../src-tauri")
}

fn lexicon() -> PathBuf {
    models_root().join("tts-models/lexicon-en-us.txt")
}

#[cfg(all(test, feature = "kokoro"))]
mod tts_smoke_test {
    use super::*;
    use crate::tts::{create_kokoro, synthesize};

    /// A voz é opcional (baixada pelo app); sem ela o teste só avisa e passa.
    #[test]
    fn kokoro_generates_audio() {
        let voice = models_root().join("tts-models/kokoro-en-v0_19");
        if !voice.join("model.onnx").exists() {
            eprintln!("voz Kokoro não instalada em {voice:?}; rode scripts/fetch-models.sh pra testar");
            return;
        }
        let tts = create_kokoro(&voice, &lexicon()).expect("falha ao criar o motor de TTS");
        let (samples, rate) = synthesize(&tts, "All right, let's start on this side.", 1.0).expect("falha ao gerar áudio");
        assert!(samples.len() > 1000, "áudio gerado está vazio demais");
        assert!(rate > 0);
        println!("OK: {} amostras, {rate} Hz, {:.2}s", samples.len(), samples.len() as f32 / rate as f32);
    }
}

#[cfg(test)]
mod ocr_smoke_test {
    /// Precisa de um recorte de balão com "All right, let's start on this side":
    /// OCR_TEST_IMAGE=/caminho/balao.png cargo test --release -- --ignored ocrs_reads
    #[test]
    #[ignore]
    fn ocrs_reads_comic_bubble() {
        use ocrs::{ImageSource, OcrEngine, OcrEngineParams};
        use rten::Model;
        use std::time::Instant;

        let base = super::models_root().join("ocr-models");
        let detection_model = Model::load_file(base.join("text-detection.onnx")).unwrap();
        let recognition_model = Model::load_file(base.join("text-recognition.onnx")).unwrap();

        let engine = OcrEngine::new(OcrEngineParams {
            detection_model: Some(detection_model),
            recognition_model: Some(recognition_model),
            ..Default::default()
        })
        .unwrap();

        let img_path = std::env::var("OCR_TEST_IMAGE").expect("defina OCR_TEST_IMAGE");
        let img = image::open(img_path).unwrap().into_rgb8();
        let img_source = ImageSource::from_bytes(img.as_raw(), img.dimensions()).unwrap();

        let start = Instant::now();
        let ocr_input = engine.prepare_input(img_source).unwrap();
        let text = engine.get_text(&ocr_input).unwrap();
        let elapsed = start.elapsed();

        println!("Texto lido: {:?}", text);
        println!("Tempo: {:.3}s", elapsed.as_secs_f32());
        assert!(text.to_uppercase().contains("ALL RIGHT"));
        assert!(text.to_uppercase().contains("THIS SIDE"));
    }
}

#[cfg(test)]
mod streaming_tests {
    use super::*;

    #[test]
    fn campo_so_fecha_quando_a_string_termina() {
        assert_eq!(closed_string_field(r#"{"original_text": "And wha"#, "original_text"), None);
        assert_eq!(
            closed_string_field(r#"{"original_text": "He said \"hi\"\nok", "trans"#, "original_text"),
            Some("He said \"hi\"\nok".to_string())
        );
        assert_eq!(closed_string_field(r#"```json\n{"translation":"Oi""#, "translation"), Some("Oi".to_string()));
    }

    /// Roda contra o omniroute local: OMNI_KEY=... cargo test --release -- --ignored stream_real
    #[tokio::test]
    #[ignore]
    async fn stream_real() {
        let key = std::env::var("OMNI_KEY").expect("defina OMNI_KEY");
        let started = std::time::Instant::now();
        let mut events = Vec::new();
        let mut on_event = |e: StreamEvent| {
            let ms = started.elapsed().as_millis();
            match e {
                StreamEvent::FirstToken => events.push(format!("{ms}ms primeiro token")),
                StreamEvent::Field(f, v) => events.push(format!("{ms}ms {f} = {v:?}")),
            }
        };
        let (text, tokens) = stream_translation(
            Wire::OpenAi,
            "AND WHAT HAPPENED TO ITS OTHER ARM?",
            &key,
            "http://localhost",
            "antigravity/gemini-3-flash",
            &mut on_event,
        )
        .await
        .unwrap();
        for e in &events {
            println!("{e}");
        }
        let mut result = parse_translation_json(&text).unwrap();
        fill_pronunciations(&mut result, &lexicon());
        println!("{}ms total | {:?} tokens", started.elapsed().as_millis(), tokens);
        println!("{}", serde_json::to_string_pretty(&result).unwrap());
        assert_eq!(events.len(), 3);
        assert!(!result.pronunciation_pt.is_empty());
        assert!(result.gloss.len() >= 6, "glosa veio curta: {:?}", result.gloss);
    }

    #[test]
    fn le_o_texto_de_cada_formato_de_streaming() {
        let j = |s: &str| serde_json::from_str::<serde_json::Value>(s).unwrap();
        assert_eq!(stream_delta(Wire::OpenAi, &j(r#"{"choices":[{"delta":{"content":"Oi"}}]}"#)).as_deref(), Some("Oi"));
        assert_eq!(
            stream_delta(Wire::Anthropic, &j(r#"{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Oi"}}"#)).as_deref(),
            Some("Oi")
        );
        assert_eq!(stream_delta(Wire::Anthropic, &j(r#"{"type":"ping"}"#)), None);
        assert_eq!(stream_out_tokens(Wire::Anthropic, &j(r#"{"type":"message_delta","usage":{"output_tokens":42}}"#)), Some(42));
        // Formato de streamGenerateContent?alt=sse; a parte "thought" (raciocínio) fica de fora.
        let g = j(r#"{"candidates":[{"content":{"role":"model","parts":[{"text":"pensando...","thought":true},{"text":"{\"original_text\""}]}}],"usageMetadata":{"candidatesTokenCount":7}}"#);
        assert_eq!(stream_delta(Wire::Google, &g).as_deref(), Some("{\"original_text\""));
        assert_eq!(stream_out_tokens(Wire::Google, &g), Some(7));
    }

    /// Formato nativo da Anthropic, via omniroute: OMNI_KEY=... cargo test --release -- --ignored stream_anthropic
    #[tokio::test]
    #[ignore]
    async fn stream_anthropic_real() {
        let key = std::env::var("OMNI_KEY").expect("defina OMNI_KEY");
        let started = std::time::Instant::now();
        let mut fields = Vec::new();
        let mut on_event = |e: StreamEvent| {
            if let StreamEvent::Field(f, v) = e {
                fields.push(format!("{}ms {f} = {v:?}", started.elapsed().as_millis()));
            }
        };
        let (text, tokens) = stream_translation(
            Wire::Anthropic,
            "HE DIDN'T GO DOWN WITHOUT A FIGHT.",
            &key,
            "http://localhost",
            "claude/claude-haiku-4-5-20251001",
            &mut on_event,
        )
        .await
        .unwrap();
        for f in &fields {
            println!("{f}");
        }
        let r = parse_translation_json(&text).unwrap();
        println!("{}ms total | {tokens:?} tokens | {} | gloss {}", started.elapsed().as_millis(), r.translation, r.gloss.len());
        assert_eq!(fields.len(), 2);
        assert!(tokens.is_some());
    }

    /// HTTPS de verdade (rustls + certificados embutidos): com chave inválida o Google tem que
    /// responder um erro HTTP — se o TLS falhasse, o erro seria de conexão.
    /// cargo test --release -- --ignored https_funciona
    #[tokio::test]
    #[ignore]
    async fn https_funciona_com_rustls() {
        let err = fetch_models(
            "https://generativelanguage.googleapis.com".into(),
            "chave-invalida".into(),
            Some("google".into()),
        )
        .await
        .unwrap_err();
        println!("{err}");
        assert!(err.contains("respondeu"), "falhou antes de falar com o Google: {err}");
    }

    #[test]
    fn glosa_malformada_nao_derruba_a_traducao() {
        let r = parse_translation_json(r#"{"original_text":"Hi","translation":"Oi","gloss":"hi:oi"}"#).unwrap();
        assert_eq!(r.translation, "Oi");
        assert!(r.gloss.is_empty());
        let r = parse_translation_json(r#"{"translation":"Oi","gloss":[["hi","oi"],["x"],[1,2]]}"#).unwrap();
        assert_eq!(r.gloss, vec![("hi".to_string(), "oi".to_string())]);
    }

    /// OMNI_KEY=... cargo test --release -- --ignored benchmark_real
    #[tokio::test]
    #[ignore]
    async fn benchmark_real() {
        let key = std::env::var("OMNI_KEY").expect("defina OMNI_KEY");
        for model in ["antigravity/gemini-3-flash", "modelo/que-nao-existe"] {
            let r = timed_translation("openai", "http://localhost", &key, model).await;
            println!("{model}: {r:?}");
        }
        assert!(timed_translation("openai", "http://localhost", &key, "antigravity/gemini-3-flash").await.is_ok());
        assert!(timed_translation("openai", "http://localhost", &key, "modelo/que-nao-existe").await.is_err());
    }
}

#[cfg(test)]
mod backup_tests {
    use super::*;

    #[test]
    fn nome_do_audio_e_estavel_e_ignora_caixa() {
        // Valores de referência do FNV-1a 64: se mudarem, os .wav já salvos ficariam órfãos.
        assert_eq!(fnv1a64(""), 0xcbf2_9ce4_8422_2325);
        assert_eq!(fnv1a64("a"), 0xaf63_dc4c_8601_ec8c);
        let t = TranslationResult {
            original_text: "Let's head inside now.".into(),
            breakdown: vec![BreakdownItem { segment: "head inside".into(), ..Default::default() }],
            ..Default::default()
        };
        assert_eq!(speech_texts(&t), ["Let's head inside now.", "head inside"]);
    }

    #[test]
    fn detecta_chines_no_meio_da_traducao() {
        assert!(has_cjk("Ele não caiu sem dar a挣扎."));
        assert!(!has_cjk("Ele não se rendeu sem lutar — ação, ênfase, à vontade."));
    }

    #[test]
    fn data_utc_do_nome_do_arquivo() {
        assert_eq!(utc_stamp(0), "1970-01-01_00-00");
        assert_eq!(utc_stamp(1_700_000_000_000), "2023-11-14_22-13");
        assert_eq!(utc_stamp(951_782_400_000), "2000-02-29_00-00");
    }

    #[test]
    fn mescla_sem_repetir_e_mais_recente_primeiro() {
        let t = |id: &str, txt: &str| TranslationResult { id: id.into(), translation: txt.into(), ..Default::default() };
        let merged = merge_translations(vec![t("300", "local"), t("100", "local")], vec![t("200", "importada"), t("300", "duplicada")]);
        let ids: Vec<&str> = merged.iter().map(|x| x.id.as_str()).collect();
        assert_eq!(ids, ["300", "200", "100"]);
        assert_eq!(merged[0].translation, "local");
    }
}

#[cfg(test)]
mod voice_tests {
    use crate::voice::*;
    use std::sync::atomic::AtomicBool;

    fn temp_dir(name: &str) -> std::path::PathBuf {
        let d = std::env::temp_dir().join(format!("webtoon-voice-test-{name}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&d);
        std::fs::create_dir_all(&d).unwrap();
        d
    }

    /// KOKORO_ARCHIVE=/caminho/kokoro-en-v0_19.tar.bz2 cargo test --release -- --ignored instala_do_pacote
    #[test]
    #[ignore]
    fn instala_do_pacote_so_os_arquivos_necessarios() {
        let archive = std::env::var("KOKORO_ARCHIVE").expect("defina KOKORO_ARCHIVE");
        let voices = temp_dir("install");
        assert!(!is_installed(&voices));
        install_archive(std::path::Path::new(&archive), &voices).unwrap();
        assert!(is_installed(&voices));
        let mut names: Vec<String> = std::fs::read_dir(kokoro_dir(&voices))
            .unwrap()
            .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
            .collect();
        names.sort();
        assert_eq!(names, ["LICENSE", "README.md", "model.onnx", "tokens.txt", "voices.bin"]);
        println!("instalada: {} MB", installed_bytes(&voices) / 1_048_576);
        remove(&voices).unwrap();
        assert!(!is_installed(&voices));
        let _ = std::fs::remove_dir_all(&voices);
    }

    #[tokio::test]
    #[ignore]
    async fn cancelar_nao_deixa_lixo() {
        let dir = temp_dir("cancel");
        let dest = dir.join("kokoro.tar.bz2");
        let cancel = AtomicBool::new(true);
        let err = download_archive(&dest, &cancel, |_, _| {}).await.unwrap_err();
        assert!(err.contains("cancelado"), "{err}");
        assert_eq!(std::fs::read_dir(&dir).unwrap().count(), 0);
        let _ = std::fs::remove_dir_all(&dir);
    }

    /// Baixa os ~305MB de verdade e confere o SHA-256: cargo test --release -- --ignored download_real
    #[tokio::test]
    #[ignore]
    async fn download_real_confere_o_hash() {
        let dir = temp_dir("download");
        let dest = dir.join("kokoro.tar.bz2");
        let cancel = AtomicBool::new(false);
        let mut last = 0;
        download_archive(&dest, &cancel, |done, total| {
            let pct = done * 100 / total;
            if pct >= last + 25 {
                last = pct;
                println!("{pct}% ({} MB)", done / 1_048_576);
            }
        })
        .await
        .unwrap();
        assert_eq!(std::fs::metadata(&dest).unwrap().len(), KOKORO_ARCHIVE_BYTES);
        install_archive(&dest, &dir.join("voices")).unwrap();
        assert!(is_installed(&dir.join("voices")));
        let _ = std::fs::remove_dir_all(&dir);
    }
}
