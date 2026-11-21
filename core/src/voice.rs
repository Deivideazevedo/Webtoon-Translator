//! Download e instalação da voz Kokoro, que é opcional (desktop e Android): o pacote oficial
//! do sherpa-onnx é baixado, conferido por SHA-256 e só então extraído.

use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::{BufReader, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};

pub const KOKORO_DIR: &str = "kokoro-en-v0_19";
pub const KOKORO_URL: &str =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2";
pub const KOKORO_ARCHIVE_BYTES: u64 = 319_625_534;
const KOKORO_ARCHIVE_SHA256: &str = "912804855a04745fa77a30be545b3f9a5d15c4d66db00b88cbcd4921df605ac7";

/// O pacote também traz espeak-ng-data, que não usamos (a pronúncia vem do nosso dicionário).
const KOKORO_FILES: [&str; 5] = ["model.onnx", "voices.bin", "tokens.txt", "LICENSE", "README.md"];
const KOKORO_REQUIRED: [&str; 3] = ["model.onnx", "voices.bin", "tokens.txt"];

pub fn kokoro_dir(voices_dir: &Path) -> PathBuf {
    voices_dir.join(KOKORO_DIR)
}

pub fn is_installed(voices_dir: &Path) -> bool {
    let dir = kokoro_dir(voices_dir);
    KOKORO_REQUIRED.iter().all(|f| dir.join(f).is_file())
}

/// Espaço ocupado pela voz instalada, em bytes (0 se não instalada).
pub fn installed_bytes(voices_dir: &Path) -> u64 {
    fs::read_dir(kokoro_dir(voices_dir))
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|e| e.metadata().ok())
        .map(|m| m.len())
        .sum()
}

pub fn remove(voices_dir: &Path) -> Result<(), String> {
    let dir = kokoro_dir(voices_dir);
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
    dest: &Path,
    cancel: &AtomicBool,
    mut progress: impl FnMut(u64, u64),
) -> Result<(), String> {
    let part = dest.with_extension("part");
    let mut response = crate::translate::http()
        .get(KOKORO_URL)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar no servidor da voz: {e}"))?;
    if !response.status().is_success() {
        return Err(format!("O servidor da voz respondeu {}", response.status()));
    }
    let total = response.content_length().unwrap_or(KOKORO_ARCHIVE_BYTES);
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
        if sha256_hex(hasher) != KOKORO_ARCHIVE_SHA256 {
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
pub fn install_archive(archive: &Path, voices_dir: &Path) -> Result<(), String> {
    let staging = voices_dir.join(format!("{KOKORO_DIR}.partial"));
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
        let Some(name) = path.strip_prefix(KOKORO_DIR).ok().and_then(|p| p.to_str()) else {
            continue;
        };
        if KOKORO_FILES.contains(&name) {
            let mut out = File::create(staging.join(name)).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out).map_err(|e| format!("Falha ao extrair a voz (disco cheio?): {e}"))?;
        }
    }
    if let Some(missing) = KOKORO_REQUIRED.iter().find(|f| !staging.join(f).is_file()) {
        let _ = fs::remove_dir_all(&staging);
        return Err(format!("Pacote da voz incompleto: faltou {missing}"));
    }

    remove(voices_dir)?;
    fs::rename(&staging, kokoro_dir(voices_dir)).map_err(|e| e.to_string())
}
