//! Benchmark de modelos (desktop e Android): mede cada modelo com uma tradução real e escolhe os
//! mais rápidos que nunca falharam pra virarem favoritos do provedor.

use futures_util::stream::{self, StreamExt};
use serde::Serialize;
use std::collections::HashMap;
use std::sync::atomic::{AtomicUsize, Ordering};

use crate::translate::timed_translation;

/// Chamadas simultâneas: mais que isso o gateway começa a devolver 429 e o tempo medido deixa de
/// ser do modelo.
pub const CONCURRENCY: usize = 4;
/// Os mais rápidos da 1ª rodada rodam de novo, pra mediana não depender de um tiro de sorte.
pub const FINALISTS: usize = 8;
pub const EXTRA_RUNS: usize = 2;
pub const FAVORITES: usize = 5;

#[derive(Serialize, Clone, Debug)]
pub struct BenchmarkEntry {
    pub model: String,
    /// Mediana dos tempos (finalistas rodam 1 + EXTRA_RUNS vezes).
    pub secs: Option<f64>,
    pub runs: Vec<f64>,
    pub error: Option<String>,
    /// Entrou nos [FAVORITES] mais rápidos e confiáveis.
    pub favorite: bool,
}

/// Andamento: `done` de `total` chamadas; `model` acabou de responder (ou falhar).
pub struct Progress<'a> {
    pub done: usize,
    pub total: usize,
    pub model: &'a str,
    pub ok: bool,
}

/// Mede todos os `models` e devolve a tabela, do mais rápido pro mais lento (os que falharam no
/// fim). Os favoritos sugeridos vêm com `favorite = true`; quem chama decide se grava.
pub async fn run(
    provider: &str,
    base_url: &str,
    api_key: &str,
    models: &[String],
    on_progress: &(dyn Fn(Progress) + Sync),
) -> Result<Vec<BenchmarkEntry>, String> {
    if models.is_empty() {
        return Err("Nenhum modelo pra testar. Teste a conexão primeiro.".to_string());
    }
    let finalists_n = FINALISTS.min(models.len());
    let total = models.len() + finalists_n * EXTRA_RUNS;
    let done = AtomicUsize::new(0);
    let mut runs: HashMap<String, Vec<f64>> = HashMap::new();
    let mut errors: HashMap<String, String> = HashMap::new();

    let round = |list: Vec<String>| {
        let done = &done;
        stream::iter(list)
            .map(move |model| async move {
                let result = timed_translation(provider, base_url, api_key, &model).await;
                let n = done.fetch_add(1, Ordering::SeqCst) + 1;
                on_progress(Progress { done: n, total, model: &model, ok: result.is_ok() });
                (model, result)
            })
            .buffer_unordered(CONCURRENCY)
            .collect::<Vec<_>>()
    };

    for (model, result) in round(models.to_vec()).await {
        match result {
            Ok(secs) => runs.entry(model).or_default().push(secs),
            Err(e) => {
                errors.insert(model, e.chars().take(140).collect());
            }
        }
    }

    let mut ranked: Vec<(String, f64)> = runs.iter().map(|(m, r)| (m.clone(), r[0])).collect();
    ranked.sort_by(|a, b| a.1.total_cmp(&b.1));
    let finalists: Vec<String> = ranked.iter().take(finalists_n).map(|(m, _)| m.clone()).collect();
    for _ in 0..EXTRA_RUNS {
        for (model, result) in round(finalists.clone()).await {
            match result {
                Ok(secs) => runs.entry(model).or_default().push(secs),
                Err(e) => {
                    errors.entry(model).or_insert_with(|| e.chars().take(140).collect());
                }
            }
        }
    }

    // Favorito só se foi finalista e respondeu certo em TODAS as rodadas.
    let mut reliable: Vec<(String, f64)> = finalists
        .iter()
        .filter(|m| !errors.contains_key(*m) && runs.get(*m).map(|r| r.len()) == Some(1 + EXTRA_RUNS))
        .map(|m| (m.clone(), median(&runs[m])))
        .collect();
    reliable.sort_by(|a, b| a.1.total_cmp(&b.1));
    let favorites: Vec<String> = reliable.iter().take(FAVORITES).map(|(m, _)| m.clone()).collect();

    let mut entries: Vec<BenchmarkEntry> = models
        .iter()
        .map(|m| BenchmarkEntry {
            model: m.clone(),
            secs: runs.get(m).map(|r| median(r)),
            runs: runs.get(m).cloned().unwrap_or_default(),
            error: errors.get(m).cloned(),
            favorite: favorites.contains(m),
        })
        .collect();
    entries.sort_by(|a, b| match (a.secs, b.secs) {
        (Some(x), Some(y)) => x.total_cmp(&y),
        (Some(_), None) => std::cmp::Ordering::Less,
        (None, Some(_)) => std::cmp::Ordering::Greater,
        (None, None) => a.model.cmp(&b.model),
    });
    Ok(entries)
}

/// Favoritos sugeridos, do mais rápido pro mais lento.
pub fn favorites(entries: &[BenchmarkEntry]) -> Vec<String> {
    entries.iter().filter(|e| e.favorite).map(|e| e.model.clone()).collect()
}

fn median(runs: &[f64]) -> f64 {
    let mut v = runs.to_vec();
    v.sort_by(|a, b| a.total_cmp(b));
    v[v.len() / 2]
}
