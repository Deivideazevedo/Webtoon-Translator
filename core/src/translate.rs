//! Tradução: prompt, tipos do resultado, streaming dos 3 provedores e parse do JSON.

use serde::{Deserialize, Serialize};

use crate::pronunciation;
use crate::text::has_cjk;

/// Textos que viram áudio numa tradução: a frase original e cada trecho do breakdown.
pub fn speech_texts(t: &TranslationResult) -> Vec<String> {
    std::iter::once(t.original_text.clone())
        .chain(t.breakdown.iter().map(|b| b.segment.clone()))
        .filter(|s| !s.trim().is_empty())
        .collect()
}

/// A pronúncia NÃO vem do modelo: é calculada localmente (pronunciation.rs) a partir do
/// dicionário do Kokoro — mais consistente e menos tokens de saída pra esperar.
pub const SYSTEM_PROMPT: &str = "Você é um professor de inglês para falantes de português brasileiro que leem \
webtoons. Você recebe um texto em inglês extraído por OCR (pode ter ruído: letras soltas, pontuação estranha, \
palavras levemente erradas). Reconstrua o texto mais provável e responda SOMENTE com JSON válido, sem markdown, \
nesta ordem exata de campos:\n\
{\"original_text\": \"...\", \"translation\": \"...\", \"breakdown\": [{\"segment\": \"...\", \"meaning\": \"...\", \
\"note\": \"...\"}], \"grammar_notes\": \"...\", \"gloss\": [[\"palavra\", \"tradução literal\"]]}\n\n\
Regras:\n\
- original_text e translation em sentence case (nunca copie a caixa alta do quadrinho). Separe balões com \"\\n\".\n\
- translation: português brasileiro natural, preservando a nuance (gíria, intensidade, tom).\n\
- breakdown: de 1 a 3 trechos. Priorize phrasal verbs e expressões cujo sentido muda em relação às palavras \
soltas (ex.: \"go down\" é literalmente \"descer/cair\", mas em \"go down without a fight\" é \"se render\"); \
depois gírias, armadilhas gramaticais e falsos cognatos. Nunca repita a frase inteira como trecho. segment deve \
aparecer exatamente assim em original_text.\n\
- meaning: o sentido do trecho neste contexto. note: no máximo 1 frase; quando o sentido não for literal, comece \
com \"Literal: … → aqui: …\"; sem especular sobre história ou personagens.\n\
- grammar_notes: no máximo 1 frase.\n\
- gloss: tradução literal, palavra por palavra, de CADA palavra de original_text na ordem (sem pontuação), com o \
sentido que ela tem nesta frase. Contrações viram 1 item (\"let's\" = \"vamos\"). Pode ficar estranho em português, \
é de propósito.\n\
- Se não houver inglês legível, retorne original_text e translation vazios e explique em grammar_notes.";

/// Teto de segurança contra geração descontrolada. As respostas reais ficam em ~100-700 tokens,
/// então isso nunca corta uma resposta normal (cortar no meio quebraria o JSON).
pub const MAX_OUTPUT_TOKENS: u32 = 4096;

/// Um client só pro app inteiro: reaproveita conexões (keep-alive/TLS) entre chamadas.
pub fn http() -> &'static reqwest::Client {
    static CLIENT: std::sync::OnceLock<reqwest::Client> = std::sync::OnceLock::new();
    CLIENT.get_or_init(reqwest::Client::new)
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ModelEntry {
    pub id: String,
    #[serde(default)]
    pub owned_by: Option<String>,
    #[serde(default)]
    pub capabilities: Option<serde_json::Value>,
}

#[derive(Deserialize)]
pub struct ModelsResponse {
    pub data: Vec<ModelEntry>,
}

#[derive(Serialize, Deserialize, Clone, Default)]
pub struct BreakdownItem {
    #[serde(default)]
    pub segment: String,
    #[serde(default)]
    pub pronunciation_pt: String,
    #[serde(default)]
    pub pronunciation_ipa: String,
    #[serde(default)]
    pub meaning: String,
    #[serde(default)]
    pub note: String,
}

#[derive(Serialize, Deserialize, Clone, Default)]
pub struct TranslationResult {
    #[serde(default)]
    pub original_text: String,
    #[serde(default)]
    pub translation: String,
    #[serde(default)]
    pub pronunciation_pt: String,
    #[serde(default)]
    pub pronunciation_ipa: String,
    #[serde(default)]
    pub breakdown: Vec<BreakdownItem>,
    #[serde(default)]
    pub grammar_notes: String,
    #[serde(default)]
    pub duration_secs: f64,
    #[serde(default)]
    pub model: String,
    /// true enquanto a resposta ainda está chegando (o balão mostra o que já tem + skeleton).
    #[serde(default)]
    pub pending: bool,
    /// Epoch ms da captura: identifica a tradução nos recentes/favoritos e ordena por data.
    #[serde(default)]
    pub id: String,
    /// [palavra, tradução literal] na ordem de original_text, pro hover no balão.
    #[serde(default, deserialize_with = "lenient_gloss")]
    pub gloss: Vec<(String, String)>,
}

/// A glosa é um extra: se o modelo devolver num formato inesperado, ela é descartada
/// em vez de derrubar o parse da tradução inteira.
pub fn lenient_gloss<'de, D: serde::Deserializer<'de>>(d: D) -> Result<Vec<(String, String)>, D::Error> {
    let value = serde_json::Value::deserialize(d)?;
    Ok(value
        .as_array()
        .map(|items| {
            items
                .iter()
                .filter_map(|pair| {
                    let pair = pair.as_array()?;
                    Some((pair.first()?.as_str()?.to_string(), pair.get(1)?.as_str()?.to_string()))
                })
                .collect()
        })
        .unwrap_or_default())
}

pub fn fill_pronunciations(result: &mut TranslationResult, lexicon: &std::path::Path) {
    let path = lexicon;
    if let Some(p) = pronunciation::pronounce(&path, &result.original_text) {
        result.pronunciation_pt = p.ptbr;
        result.pronunciation_ipa = p.ipa;
    }
    for item in &mut result.breakdown {
        if let Some(p) = pronunciation::pronounce(&path, &item.segment) {
            item.pronunciation_pt = p.ptbr;
            item.pronunciation_ipa = p.ipa;
        }
    }
}

pub const ANTHROPIC_VERSION: &str = "2023-06-01";

pub async fn fetch_models(
    base_url: String,
    api_key: String,
    provider: Option<String>,
) -> Result<Vec<ModelEntry>, String> {
    let client = http();

    if provider.as_deref() == Some("google") {
        let url = format!("{}/v1beta/models", base_url.trim_end_matches('/'));
        let response = client
            .get(&url)
            .header("x-goog-api-key", api_key)
            .send()
            .await
            .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            return Err(format!("API do Google respondeu {status}: {body}"));
        }

        let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
        let entries = body["models"]
            .as_array()
            .cloned()
            .unwrap_or_default()
            .into_iter()
            .filter_map(|m| {
                let name = m["name"].as_str()?;
                let id = name.strip_prefix("models/").unwrap_or(name).to_string();
                Some(ModelEntry {
                    id,
                    owned_by: None,
                    capabilities: None,
                })
            })
            .collect();
        return Ok(entries);
    }

    let url = format!("{}/v1/models", base_url.trim_end_matches('/'));
    let is_anthropic = provider.as_deref() == Some("anthropic");

    let request = if is_anthropic {
        client
            .get(&url)
            .header("x-api-key", api_key)
            .header("anthropic-version", ANTHROPIC_VERSION)
    } else {
        client.get(&url).bearer_auth(api_key)
    };

    let response = request
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("Gateway respondeu {status}: {body}"));
    }

    let parsed: ModelsResponse = response
        .json()
        .await
        .map_err(|e| format!("Resposta inesperada do gateway: {e}"))?;
    Ok(parsed.data)
}

pub fn parse_translation_json(raw: &str) -> Result<TranslationResult, String> {
    if let Ok(v) = serde_json::from_str::<TranslationResult>(raw) {
        return Ok(v);
    }
    if let (Some(start), Some(end)) = (raw.find('{'), raw.rfind('}')) {
        if end > start {
            if let Ok(v) = serde_json::from_str::<TranslationResult>(&raw[start..=end]) {
                return Ok(v);
            }
        }
    }
    Err(format!("Não consegui interpretar a resposta do modelo: {raw}"))
}

pub async fn call_omniroute_text(
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
) -> Result<String, String> {
    let url = format!("{}/v1/chat/completions", base_url.trim_end_matches('/'));
    let client = http();
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let payload = serde_json::json!({
        "model": model,
        "max_tokens": MAX_OUTPUT_TOKENS,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message}
        ]
    });
    let response = client
        .post(&url)
        .bearer_auth(api_key)
        .json(&payload)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("Gateway respondeu {status}: {body}"));
    }

    let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
    body["choices"][0]["message"]["content"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Resposta do gateway sem conteúdo".to_string())
}

pub enum StreamEvent {
    FirstToken,
    /// Um campo string do JSON acabou de fechar (valor já sem escapes).
    Field(&'static str, String),
}

/// Valor de `"campo": "..."` se a string já fechou no texto parcial; None se ainda está chegando.
pub fn closed_string_field(partial: &str, field: &str) -> Option<String> {
    let key = format!("\"{field}\"");
    let after_key = &partial[partial.find(&key)? + key.len()..];
    let after_colon = &after_key[after_key.find(':')? + 1..];
    let open = after_colon.find('"')?;
    if !after_colon[..open].trim().is_empty() {
        return None;
    }
    let mut escaped = false;
    for (i, c) in after_colon[open + 1..].char_indices() {
        if escaped {
            escaped = false;
        } else if c == '\\' {
            escaped = true;
        } else if c == '"' {
            return serde_json::from_str(&after_colon[open..open + 1 + i + 1]).ok();
        }
    }
    None
}

pub const STREAMED_FIELDS: [&str; 2] = ["original_text", "translation"];

/// Formato da API de cada provedor (o streaming muda só na requisição e em como ler cada pedaço).
#[derive(Clone, Copy, PartialEq, Debug)]
pub enum Wire {
    OpenAi,
    Anthropic,
    Google,
}

impl Wire {
    pub fn from_provider(provider: &str) -> Wire {
        match provider {
            "anthropic" => Wire::Anthropic,
            "google" => Wire::Google,
            _ => Wire::OpenAi,
        }
    }
}

/// Texto novo que chegou num evento do streaming (None se o evento não traz texto).
pub fn stream_delta(wire: Wire, event: &serde_json::Value) -> Option<String> {
    match wire {
        Wire::OpenAi => event["choices"][0]["delta"]["content"].as_str().map(str::to_string),
        // content_block_delta/text_delta; os outros eventos (message_start, ping...) não têm texto.
        Wire::Anthropic => (event["type"] == "content_block_delta" && event["delta"]["type"] == "text_delta")
            .then(|| event["delta"]["text"].as_str().map(str::to_string))
            .flatten(),
        // Partes marcadas como "thought" são o raciocínio interno do Gemini, não a resposta.
        Wire::Google => {
            let parts = event["candidates"][0]["content"]["parts"].as_array()?;
            let text: String = parts
                .iter()
                .filter(|p| p["thought"] != true)
                .filter_map(|p| p["text"].as_str())
                .collect();
            (!text.is_empty()).then_some(text)
        }
    }
}

pub fn stream_out_tokens(wire: Wire, event: &serde_json::Value) -> Option<u64> {
    match wire {
        Wire::OpenAi => event["usage"]["completion_tokens"].as_u64(),
        Wire::Anthropic => event["usage"]["output_tokens"].as_u64(),
        Wire::Google => event["usageMetadata"]["candidatesTokenCount"].as_u64(),
    }
}

/// Resposta inteira de uma vez (gateway que ignora `stream`).
pub fn full_response_text(wire: Wire, body: &serde_json::Value) -> Option<String> {
    match wire {
        Wire::OpenAi => body["choices"][0]["message"]["content"].as_str().map(str::to_string),
        Wire::Anthropic => body["content"][0]["text"].as_str().map(str::to_string),
        Wire::Google => stream_delta(Wire::Google, body),
    }
}

/// Tradução com streaming nos 3 provedores: avisa `on_event` quando o primeiro token chega e
/// assim que original_text/translation fecham no JSON; devolve o texto completo + tokens de saída.
pub async fn stream_translation(
    wire: Wire,
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
    on_event: &mut (dyn FnMut(StreamEvent) + Send),
) -> Result<(String, Option<u64>), String> {
    let base = base_url.trim_end_matches('/');
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let (url, request) = match wire {
        Wire::OpenAi => {
            let url = format!("{base}/v1/chat/completions");
            let req = http().post(&url).bearer_auth(api_key).json(&serde_json::json!({
                "model": model,
                "max_tokens": MAX_OUTPUT_TOKENS,
                "stream": true,
                "stream_options": {"include_usage": true},
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_message}
                ]
            }));
            (url, req)
        }
        Wire::Anthropic => {
            let url = format!("{base}/v1/messages");
            let req = http()
                .post(&url)
                .header("x-api-key", api_key)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .json(&serde_json::json!({
                    "model": model,
                    "max_tokens": MAX_OUTPUT_TOKENS,
                    "stream": true,
                    "system": SYSTEM_PROMPT,
                    "messages": [{"role": "user", "content": user_message}]
                }));
            (url, req)
        }
        Wire::Google => {
            let url = format!("{base}/v1beta/models/{model}:streamGenerateContent?alt=sse");
            let req = http().post(&url).header("x-goog-api-key", api_key).json(&serde_json::json!({
                "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
                "contents": [{"role": "user", "parts": [{"text": user_message}]}],
                "generationConfig": {"maxOutputTokens": MAX_OUTPUT_TOKENS}
            }));
            (url, req)
        }
    };

    let mut response = request
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;
    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("A API respondeu {status}: {body}"));
    }

    let mut pending: Vec<u8> = Vec::new();
    let mut not_sse = String::new();
    let mut content = String::new();
    let mut out_tokens = None;
    let mut sent = [false; STREAMED_FIELDS.len()];

    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|e| format!("Conexão caiu no meio da resposta: {e}"))?
    {
        pending.extend_from_slice(&chunk);
        // Só processa linhas completas: um caractere UTF-8 pode vir partido entre chunks.
        while let Some(nl) = pending.iter().position(|&b| b == b'\n') {
            let line_bytes: Vec<u8> = pending.drain(..=nl).collect();
            let line = String::from_utf8_lossy(&line_bytes);
            let line = line.trim();
            let Some(data) = line.strip_prefix("data:") else {
                // "event: ..." (Anthropic) e ": comentário" fazem parte do SSE; o resto é
                // resposta sem streaming, guardada pro fallback lá embaixo.
                if !line.is_empty() && !line.starts_with("event:") && !line.starts_with(':') {
                    not_sse.push_str(line);
                }
                continue;
            };
            let data = data.trim();
            if data == "[DONE]" {
                continue;
            }
            let Ok(event) = serde_json::from_str::<serde_json::Value>(data) else {
                continue;
            };
            if let Some(err) = event.get("error") {
                return Err(format!("A API respondeu erro no meio da resposta: {err}"));
            }
            if let Some(n) = stream_out_tokens(wire, &event) {
                out_tokens = Some(n);
            }
            if let Some(delta) = stream_delta(wire, &event) {
                if content.is_empty() && !delta.is_empty() {
                    on_event(StreamEvent::FirstToken);
                }
                content.push_str(&delta);
                for (i, field) in STREAMED_FIELDS.iter().enumerate() {
                    if !sent[i] {
                        if let Some(value) = closed_string_field(&content, field) {
                            sent[i] = true;
                            on_event(StreamEvent::Field(field, value));
                        }
                    }
                }
            }
        }
    }

    if content.is_empty() {
        not_sse.push_str(&String::from_utf8_lossy(&pending));
        let body: serde_json::Value = serde_json::from_str(&not_sse)
            .map_err(|_| format!("Resposta inesperada da API: {not_sse}"))?;
        let text = full_response_text(wire, &body).ok_or_else(|| "Resposta da API sem conteúdo".to_string())?;
        return Ok((text, None));
    }
    Ok((content, out_tokens))
}

pub async fn call_anthropic_text(
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
) -> Result<String, String> {
    let url = format!("{}/v1/messages", base_url.trim_end_matches('/'));
    let client = http();
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let payload = serde_json::json!({
        "model": model,
        "max_tokens": MAX_OUTPUT_TOKENS,
        "system": SYSTEM_PROMPT,
        "messages": [
            {"role": "user", "content": user_message}
        ]
    });
    let response = client
        .post(&url)
        .header("x-api-key", api_key)
        .header("anthropic-version", ANTHROPIC_VERSION)
        .json(&payload)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("API da Anthropic respondeu {status}: {body}"));
    }

    let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
    body["content"][0]["text"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Resposta da Anthropic sem conteúdo".to_string())
}

pub async fn call_google_text(
    extracted_text: &str,
    api_key: &str,
    base_url: &str,
    model: &str,
) -> Result<String, String> {
    let url = format!(
        "{}/v1beta/models/{}:generateContent",
        base_url.trim_end_matches('/'),
        model
    );
    let client = http();
    let user_message = format!(
        "Texto extraído por OCR de uma captura de tela de webtoon:\n\n{extracted_text}"
    );
    let payload = serde_json::json!({
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"role": "user", "parts": [{"text": user_message}]}],
        "generationConfig": {"maxOutputTokens": MAX_OUTPUT_TOKENS}
    });
    let response = client
        .post(&url)
        .header("x-goog-api-key", api_key)
        .json(&payload)
        .send()
        .await
        .map_err(|e| format!("Falha ao conectar em {url}: {e}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("API do Google respondeu {status}: {body}"));
    }

    let body: serde_json::Value = response.json().await.map_err(|e| e.to_string())?;
    body["candidates"][0]["content"]["parts"][0]["text"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| "Resposta do Google sem conteúdo".to_string())
}

/// Texto de teste com cara de OCR real (caixa alta, 2 balões), pra medir o modelo com a
/// mesma carga de uma captura: prompt real + JSON completo (com breakdown e glosa).
pub const SPEED_TEST_TEXT: &str = "YOU REALLY HACKED IT TO PIECES.\nI DIDN'T THINK YOU'D GO THAT FAR, KID.";

pub const BENCH_TIMEOUT_SECS: u64 = 25;

/// Tempo até o JSON completo de uma tradução de teste. Conta como falha se estourar o
/// timeout ou se a resposta não vier no formato esperado (rápido mas inútil não serve).
pub async fn timed_translation(provider: &str, base_url: &str, api_key: &str, model: &str) -> Result<f64, String> {
    let started = std::time::Instant::now();
    let call = async {
        match provider {
            "anthropic" => call_anthropic_text(SPEED_TEST_TEXT, api_key, base_url, model).await,
            "google" => call_google_text(SPEED_TEST_TEXT, api_key, base_url, model).await,
            _ => call_omniroute_text(SPEED_TEST_TEXT, api_key, base_url, model).await,
        }
    };
    let raw = tokio::time::timeout(std::time::Duration::from_secs(BENCH_TIMEOUT_SECS), call)
        .await
        .map_err(|_| format!("sem resposta em {BENCH_TIMEOUT_SECS}s"))??;
    let parsed = parse_translation_json(&raw).map_err(|_| "resposta fora do JSON esperado".to_string())?;
    if parsed.translation.trim().is_empty() {
        return Err("resposta sem tradução".to_string());
    }
    if has_cjk(&parsed.translation) {
        return Err("tradução veio misturada com chinês/japonês".to_string());
    }
    Ok(started.elapsed().as_secs_f64())
}

/// Junta duas listas sem repetir id, mais recente primeiro (o id é o epoch ms da captura).
pub fn merge_translations(local: Vec<TranslationResult>, imported: Vec<TranslationResult>) -> Vec<TranslationResult> {
    let mut all = local;
    for t in imported {
        if !all.iter().any(|x| x.id == t.id) {
            all.push(t);
        }
    }
    all.sort_by_key(|t| std::cmp::Reverse(t.id.parse::<u128>().unwrap_or(0)));
    all
}
