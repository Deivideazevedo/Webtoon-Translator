//! Traduções salvas (recentes e favoritas) e backup. Desktop e Android usam este mesmo código,
//! então os arquivos e o formato do backup são idênticos: dá pra exportar num e importar no outro.

use std::fs;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::translate::{merge_translations, TranslationResult};

/// Recentes guardam todas as traduções (inclusive a mesma frase em modelos diferentes, pra
/// comparar); o usuário limpa pela seleção na tela. Favoritos não têm limite.
pub const MAX_RECENT: usize = 100;
pub const BACKUP_FORMAT: &str = "webtoon-translator-backup";
const BACKUP_VERSION: u32 = 1;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum List {
    Recent,
    Favorites,
}

impl List {
    /// "favorites" ou qualquer outra coisa (recentes), como as telas mandam.
    pub fn from_name(name: &str) -> List {
        if name == "favorites" {
            List::Favorites
        } else {
            List::Recent
        }
    }

    fn file(self) -> &'static str {
        match self {
            List::Recent => "history.json",
            List::Favorites => "favorite_translations.json",
        }
    }
}

pub fn now_ms() -> u128 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis()).unwrap_or(0)
}

/// Pasta com os arquivos de traduções salvas.
pub struct Library {
    dir: PathBuf,
}

impl Library {
    pub fn new(dir: impl Into<PathBuf>) -> Self {
        Library { dir: dir.into() }
    }

    fn path(&self, list: List) -> PathBuf {
        self.dir.join(list.file())
    }

    pub fn read(&self, list: List) -> Result<Vec<TranslationResult>, String> {
        let path = self.path(list);
        if !path.exists() {
            return Ok(Vec::new());
        }
        let raw = fs::read_to_string(&path).map_err(|e| e.to_string())?;
        let mut items: Vec<TranslationResult> = serde_json::from_str(&raw).unwrap_or_default();
        // Traduções de antes de existir `id`: ganham um agora e o arquivo é regravado, pra o id
        // ficar estável (favoritar/excluir dependem dele).
        if items.iter().any(|t| t.id.is_empty()) {
            let base = now_ms();
            for (i, t) in items.iter_mut().enumerate().filter(|(_, t)| t.id.is_empty()) {
                t.id = (base - i as u128).to_string();
            }
            self.write(list, &items)?;
        }
        Ok(items)
    }

    fn write(&self, list: List, items: &[TranslationResult]) -> Result<(), String> {
        fs::create_dir_all(&self.dir).map_err(|e| format!("Não consegui criar a pasta dos dados: {e}"))?;
        let raw = serde_json::to_string_pretty(items).map_err(|e| e.to_string())?;
        fs::write(self.path(list), raw).map_err(|e| e.to_string())
    }

    /// Põe no topo dos recentes (o mais antigo sai se passar de [MAX_RECENT]).
    pub fn add_recent(&self, entry: TranslationResult) -> Result<(), String> {
        let mut items = self.read(List::Recent)?;
        items.insert(0, entry);
        items.truncate(MAX_RECENT);
        self.write(List::Recent, &items)
    }

    pub fn set_favorite(&self, entry: TranslationResult, favorite: bool) -> Result<(), String> {
        let mut items = self.read(List::Favorites)?;
        items.retain(|t| t.id != entry.id);
        if favorite {
            items.insert(0, entry);
        }
        self.write(List::Favorites, &items)
    }

    pub fn delete(&self, list: List, ids: &[String]) -> Result<(), String> {
        let mut items = self.read(list)?;
        items.retain(|t| !ids.contains(&t.id));
        self.write(list, &items)
    }

    /// Tamanho do arquivo em disco (0 se ainda não existe).
    pub fn bytes(&self, list: List) -> u64 {
        fs::metadata(self.path(list)).map(|m| m.len()).unwrap_or(0)
    }

    /// Soma traduções importadas às que já existem, sem repetir id.
    pub fn merge(&self, list: List, imported: Vec<TranslationResult>) -> Result<(), String> {
        let mut merged = merge_translations(self.read(list)?, imported);
        if list == List::Recent {
            merged.truncate(MAX_RECENT);
        }
        self.write(list, &merged)
    }

    /// Backup completo em JSON. `settings` e `api_keys` vêm de cada app (cada um guarda do seu jeito).
    pub fn export_backup(
        &self,
        settings: serde_json::Value,
        api_keys: serde_json::Map<String, serde_json::Value>,
    ) -> Result<String, String> {
        let backup = serde_json::json!({
            "format": BACKUP_FORMAT,
            "version": BACKUP_VERSION,
            "exported_at_ms": now_ms(),
            "settings": settings,
            "api_keys": api_keys,
            "history": self.read(List::Recent)?,
            "favorite_translations": self.read(List::Favorites)?,
        });
        serde_json::to_string_pretty(&backup).map_err(|e| e.to_string())
    }

    /// Lê um backup e já soma as traduções dele às daqui. Configurações e chaves voltam pro app
    /// aplicar (substituindo as atuais).
    pub fn import_backup(&self, json: &str) -> Result<ImportedBackup, String> {
        let backup: serde_json::Value =
            serde_json::from_str(json).map_err(|_| "Esse arquivo não é um JSON válido.".to_string())?;
        if backup["format"] != BACKUP_FORMAT {
            return Err("Esse arquivo não é um backup do Webtoon Translator.".to_string());
        }
        let mut counts = [0usize; 2];
        for (i, (field, list)) in [("history", List::Recent), ("favorite_translations", List::Favorites)]
            .into_iter()
            .enumerate()
        {
            let imported: Vec<TranslationResult> = serde_json::from_value(backup[field].clone()).unwrap_or_default();
            counts[i] = imported.len();
            if !imported.is_empty() {
                self.merge(list, imported)?;
            }
        }
        let api_keys = backup["api_keys"]
            .as_object()
            .map(|map| {
                map.iter()
                    .filter_map(|(provider, key)| Some((provider.clone(), key.as_str()?.to_string())))
                    .collect()
            })
            .unwrap_or_default();
        Ok(ImportedBackup {
            settings: Some(backup["settings"].clone()).filter(|s| !s.is_null()),
            api_keys,
            recent: counts[0],
            favorites: counts[1],
        })
    }
}

/// O que veio no backup além das traduções (que [Library::import_backup] já salvou).
pub struct ImportedBackup {
    pub settings: Option<serde_json::Value>,
    /// (provedor, chave)
    pub api_keys: Vec<(String, String)>,
    pub recent: usize,
    pub favorites: usize,
}
