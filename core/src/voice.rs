//! Catálogo das vozes opcionais (desktop e Android): cada pacote oficial do sherpa-onnx é
//! baixado, conferido por SHA-256 e só então extraído. Mais de uma pode ficar instalada;
//! o app carrega só a escolhida.

use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::{BufReader, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};

#[derive(Clone, Copy, PartialEq, Eq, Debug, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum VoiceKind {
    Kokoro,
    /// Piper (VITS).
    Vits,
}

/// Um dos tons de voz de um modelo (`sid` = número da voz dentro do modelo).
#[derive(Debug, Serialize)]
pub struct Speaker {
    pub sid: i32,
    pub label: &'static str,
}

#[derive(Debug, Serialize)]
pub struct VoicePackage {
    pub id: &'static str,
    pub name: &'static str,
    /// Resumo pra tela de escolha ("mais natural", "mais leve").
    pub label: &'static str,
    pub kind: VoiceKind,
    #[serde(skip)]
    pub url: &'static str,
    pub archive_bytes: u64,
    #[serde(skip)]
    sha256: &'static str,
    /// Pasta de dentro do pacote (e nome da pasta instalada).
    #[serde(skip)]
    pub dir: &'static str,
    /// Arquivos extraídos; os pacotes trazem mais coisa (ex.: espeak-ng-data) que não usamos,
    /// porque a pronúncia vem do nosso dicionário.
    #[serde(skip)]
    files: &'static [&'static str],
    #[serde(skip)]
    required: &'static [&'static str],
    /// Arquivo .onnx principal.
    #[serde(skip)]
    pub model_file: &'static str,
    /// Tons de voz oferecidos (o primeiro é o padrão).
    pub speakers: &'static [Speaker],
}

impl VoicePackage {
    /// O `sid` se ele estiver na lista deste modelo; senão o tom padrão.
    pub fn speaker_or_default(&self, sid: Option<i32>) -> i32 {
        sid.filter(|s| self.speakers.iter().any(|sp| sp.sid == *s))
            .unwrap_or(self.speakers[0].sid)
    }
}

pub const KOKORO: VoicePackage = VoicePackage {
    id: "kokoro",
    name: "Kokoro",
    label: "mais natural",
    kind: VoiceKind::Kokoro,
    url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
    archive_bytes: 319_625_534,
    sha256: "912804855a04745fa77a30be545b3f9a5d15c4d66db00b88cbcd4921df605ac7",
    dir: "kokoro-en-v0_19",
    files: &["model.onnx", "voices.bin", "tokens.txt", "LICENSE", "README.md"],
    required: &["model.onnx", "voices.bin", "tokens.txt"],
    model_file: "model.onnx",
    // Ordem oficial do kokoro-en-v0_19 no sherpa-onnx (af, af_bella, …, bm_lewis).
    speakers: &[
        Speaker { sid: 0, label: "Padrão (feminina, EUA)" },
        Speaker { sid: 1, label: "Bella (feminina, EUA)" },
        Speaker { sid: 2, label: "Nicole (feminina, EUA)" },
        Speaker { sid: 3, label: "Sarah (feminina, EUA)" },
        Speaker { sid: 4, label: "Sky (feminina, EUA)" },
        Speaker { sid: 5, label: "Adam (masculina, EUA)" },
        Speaker { sid: 6, label: "Michael (masculina, EUA)" },
        Speaker { sid: 7, label: "Emma (feminina, Reino Unido)" },
        Speaker { sid: 8, label: "Isabella (feminina, Reino Unido)" },
        Speaker { sid: 9, label: "George (masculina, Reino Unido)" },
        Speaker { sid: 10, label: "Lewis (masculina, Reino Unido)" },
    ],
};

/// Um único modelo com 904 falantes (LibriTTS-R, CC BY 4.0); `sid` escolhe a voz.
pub const PIPER_LIBRITTS: VoicePackage = VoicePackage {
    id: "piper-libritts",
    name: "Piper",
    label: "mais leve",
    kind: VoiceKind::Vits,
    url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-libritts_r-medium.tar.bz2",
    archive_bytes: 82_038_311,
    sha256: "10dc268f3e371696d721486123e2705a9fc1faa113491979fde4d88dba1f1b1c",
    dir: "vits-piper-en_US-libritts_r-medium",
    files: &["en_US-libritts_r-medium.onnx", "en_US-libritts_r-medium.onnx.json", "tokens.txt", "MODEL_CARD"],
    required: &["en_US-libritts_r-medium.onnx", "tokens.txt"],
    model_file: "en_US-libritts_r-medium.onnx",
    // 11 das 904 vozes, espalhadas pelo modelo e alternando gênero. O modelo não informa o gênero:
    // ele foi medido pelo tom fundamental de cada voz (feminina ≥ 185 Hz, masculina ≤ 140 Hz).
    speakers: &[
        Speaker { sid: 0, label: "Voz 1 · feminina (padrão)" },
        Speaker { sid: 90, label: "Voz 2 · masculina" },
        Speaker { sid: 180, label: "Voz 3 · feminina" },
        Speaker { sid: 330, label: "Voz 4 · masculina" },
        Speaker { sid: 420, label: "Voz 5 · feminina" },
        Speaker { sid: 495, label: "Voz 6 · masculina" },
        Speaker { sid: 555, label: "Voz 7 · feminina" },
        Speaker { sid: 660, label: "Voz 8 · masculina" },
        Speaker { sid: 765, label: "Voz 9 · feminina" },
        Speaker { sid: 825, label: "Voz 10 · masculina" },
        Speaker { sid: 840, label: "Voz 11 · feminina" },
    ],
};

pub const VOICES: &[&VoicePackage] = &[&KOKORO, &PIPER_LIBRITTS];

pub fn find(id: &str) -> Option<&'static VoicePackage> {
    VOICES.iter().copied().find(|v| v.id == id)
}

pub fn voice_dir(voices_dir: &Path, voice: &VoicePackage) -> PathBuf {
    voices_dir.join(voice.dir)
}

pub fn is_installed(voices_dir: &Path, voice: &VoicePackage) -> bool {
    let dir = voice_dir(voices_dir, voice);
    voice.required.iter().all(|f| dir.join(f).is_file())
}

/// Espaço ocupado pela voz instalada, em bytes (0 se não instalada).
pub fn installed_bytes(voices_dir: &Path, voice: &VoicePackage) -> u64 {
    fs::read_dir(voice_dir(voices_dir, voice))
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|e| e.metadata().ok())
        .map(|m| m.len())
        .sum()
}

pub fn remove(voices_dir: &Path, voice: &VoicePackage) -> Result<(), String> {
    let dir = voice_dir(voices_dir, voice);
    if dir.exists() {
        fs::remove_dir_all(&dir).map_err(|e| format!("Não consegui remover a voz: {e}"))?;
    }
    Ok(())
}

fn sha256_hex(hasher: Sha256) -> String {
    hasher.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// Baixa o pacote da voz pra `dest`, chamando `progress(baixados, total)` a cada pedaço.
/// Grava num `.part` e só renomeia pra `dest` se o SHA-256 bater; `cancel` interrompe.
pub async fn download_archive(
    voice: &VoicePackage,
    dest: &Path,
    cancel: &AtomicBool,
    mut progress: impl FnMut(u64, u64),
) -> Result<(), String> {
    let part = dest.with_extension("part");
    let mut response = crate::translate::http()
        .get(voice.url)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar no servidor da voz: {e}"))?;
    if !response.status().is_success() {
        return Err(format!("O servidor da voz respondeu {}", response.status()));
    }
    let total = response.content_length().unwrap_or(voice.archive_bytes);
    let mut file = File::create(&part).map_err(|e| format!("Não consegui criar o arquivo da voz: {e}"))?;
    let mut hasher = Sha256::new();
    let mut done = 0u64;
    let result = async {
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|e| format!("A conexão caiu durante o download: {e}"))?
        {
            if cancel.load(Ordering::Relaxed) {
                return Err("Download cancelado.".to_string());
            }
            file.write_all(&chunk).map_err(|e| format!("Falha ao gravar a voz (disco cheio?): {e}"))?;
            hasher.update(&chunk);
            done += chunk.len() as u64;
            progress(done, total);
        }
        file.flush().map_err(|e| e.to_string())?;
        if sha256_hex(hasher) != voice.sha256 {
            return Err("O arquivo baixado veio diferente do esperado (corrompido ou alterado). Tente de novo.".to_string());
        }
        Ok(())
    }
    .await;
    drop(file);
    match result {
        Ok(()) => fs::rename(&part, dest).map_err(|e| e.to_string()),
        Err(e) => {
            let _ = fs::remove_file(&part);
            Err(e)
        }
    }
}

/// Extrai só os arquivos da voz pra uma pasta temporária e então a renomeia pra pasta final,
/// pra nunca ficar uma voz pela metade "instalada". Só nomes da lista são extraídos, então um
/// pacote adulterado não consegue escrever fora da pasta.
pub fn install_archive(voice: &VoicePackage, archive: &Path, voices_dir: &Path) -> Result<(), String> {
    let staging = voices_dir.join(format!("{}.partial", voice.dir));
    if staging.exists() {
        fs::remove_dir_all(&staging).map_err(|e| e.to_string())?;
    }
    fs::create_dir_all(&staging).map_err(|e| format!("Não consegui criar a pasta da voz: {e}"))?;

    let file = File::open(archive).map_err(|e| e.to_string())?;
    let mut tar = tar::Archive::new(bzip2::read::BzDecoder::new(BufReader::new(file)));
    let entries = tar.entries().map_err(|e| format!("Pacote da voz inválido: {e}"))?;
    for entry in entries {
        let mut entry = entry.map_err(|e| format!("Pacote da voz inválido: {e}"))?;
        let path = entry.path().map_err(|e| e.to_string())?.into_owned();
        let Some(name) = path.strip_prefix(voice.dir).ok().and_then(|p| p.to_str()) else {
            continue;
        };
        if voice.files.contains(&name) {
            let mut out = File::create(staging.join(name)).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out).map_err(|e| format!("Falha ao extrair a voz (disco cheio?): {e}"))?;
        }
    }
    if let Some(missing) = voice.required.iter().find(|f| !staging.join(f).is_file()) {
        let _ = fs::remove_dir_all(&staging);
        return Err(format!("Pacote da voz incompleto: faltou {missing}"));
    }

    remove(voices_dir, voice)?;
    fs::rename(&staging, voice_dir(voices_dir, voice)).map_err(|e| e.to_string())
}
