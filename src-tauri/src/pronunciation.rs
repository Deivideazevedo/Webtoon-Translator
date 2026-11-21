//! Pronúncia determinística: inglês -> IPA (lexicon do Kokoro, o mesmo que a voz usa)
//! -> grafia aproximada em português brasileiro, com a sílaba tônica em maiúsculas.

use std::path::Path;
use std::sync::OnceLock;

pub struct Pronunciation {
    pub ptbr: String,
    pub ipa: String,
}

/// O arquivo inteiro fica numa String só e o índice guarda só offsets ordenados pela chave,
/// pra busca binária sem alocar 168 mil Strings (~8MB em vez de ~25MB de HashMap).
struct Lexicon {
    data: String,
    index: Vec<(u32, u32, u32, u32)>, // (início chave, tam chave, início valor, tam valor)
}

impl Lexicon {
    fn load(path: &Path) -> Option<Lexicon> {
        let data = std::fs::read_to_string(path).ok()?;
        let mut index = Vec::with_capacity(170_000);
        let mut offset = 0usize;
        for line in data.split_inclusive('\n') {
            let trimmed = line.trim_end_matches(['\n', '\r']);
            if let Some(sep) = trimmed.find(" || ") {
                let value = sep + 4;
                index.push((
                    offset as u32,
                    sep as u32,
                    (offset + value) as u32,
                    (trimmed.len() - value) as u32,
                ));
            }
            offset += line.len();
        }
        index.sort_unstable_by(|a, b| slice(&data, a.0, a.1).cmp(slice(&data, b.0, b.1)));
        Some(Lexicon { data, index })
    }

    fn get(&self, key: &str) -> Option<&str> {
        self.index
            .binary_search_by(|e| slice(&self.data, e.0, e.1).cmp(key))
            .ok()
            .map(|i| {
                let e = self.index[i];
                slice(&self.data, e.2, e.3)
            })
    }
}

fn slice(data: &str, start: u32, len: u32) -> &str {
    &data[start as usize..(start + len) as usize]
}

static LEXICON: OnceLock<Option<Lexicon>> = OnceLock::new();

fn lexicon(path: &Path) -> Option<&'static Lexicon> {
    LEXICON.get_or_init(|| Lexicon::load(path)).as_ref()
}

/// Carrega o dicionário antes de precisar dele (chamar fora da thread de UI).
pub fn warm_up(path: &Path) {
    let _ = lexicon(path);
}

fn diphthong(a: &str, b: &str) -> Option<&'static str> {
    Some(match (a, b) {
        ("e", "ɪ") => "ei",
        ("a", "ɪ") => "ai",
        ("o", "ʊ") => "ou",
        ("a", "ʊ") => "au",
        ("ɔ", "ɪ") => "ói",
        _ => return None,
    })
}

fn vowel(ph: &str) -> Option<&'static str> {
    Some(match ph {
        "iː" | "i" | "ɪ" | "ᵻ" => "i",
        "e" => "ê",
        "ɛ" | "æ" => "é",
        "ɑː" | "ɑ" => "á",
        "ɐ" | "a" => "a",
        "ʌ" => "â",
        "ɔː" | "ɔ" => "ó",
        "o" => "ô",
        "ʊ" | "uː" | "u" => "u",
        "ə" | "ᵊ" => "e",
        "ɚ" => "er",
        "ɜː" | "ɜ" => "êr",
        _ => return None,
    })
}

fn consonant(ph: &str) -> &str {
    match ph {
        "ɡ" => "g",
        "ŋ" => "n",
        // Sem equivalente em português (nem "f" nem "t"): mantém "th" e o balão explica no hover.
        "θ" => "th",
        "ð" => "d",
        "ʃ" => "ch",
        "ʒ" => "j",
        "tʃ" => "tch",
        "dʒ" => "dj",
        "h" | "ɹ" | "ɾ" => "r",
        "w" => "u",
        "j" => "i",
        "ʔ" => "",
        other => other,
    }
}

struct Phone {
    text: String,
    vowel: bool,
    tonic: bool,
}

/// Uma palavra do lexicon ("hˈ æ p ə n d") -> fones já convertidos, ditongos juntos,
/// tônica marcada na vogal que vem depois do ˈ.
fn word_phones(ipa_word: &[&str]) -> Vec<Phone> {
    let mut phones = Vec::new();
    let mut stress_next = false;
    let toks: Vec<(String, bool)> = ipa_word
        .iter()
        .map(|t| (t.replace(['ˈ', 'ˌ'], ""), t.contains('ˈ')))
        .collect();
    let mut i = 0;
    while i < toks.len() {
        let (ph, primary) = (&toks[i].0, toks[i].1);
        if primary {
            stress_next = true;
        }
        if ph.is_empty() {
            i += 1;
            continue;
        }
        let next = toks.get(i + 1).map(|t| t.0.as_str()).unwrap_or("");
        if let Some(d) = diphthong(ph, next) {
            phones.push(Phone { text: d.to_string(), vowel: true, tonic: stress_next });
            stress_next = false;
            i += 2;
            continue;
        }
        if let Some(v) = vowel(ph) {
            // "really" = ɹ iə l i: o schwa colado no "i" some na fala, senão vira "RI-e-li".
            let prev_is_i = phones.last().map(|p| p.vowel && p.text == "i").unwrap_or(false);
            if ph == "ə" && prev_is_i {
                i += 1;
                continue;
            }
            phones.push(Phone { text: v.to_string(), vowel: true, tonic: stress_next });
            stress_next = false;
        } else {
            // "party" = ɑːɹ ɾ i: o r antes do flap não se pronuncia separado.
            if ph == "ɹ" && next == "ɾ" {
                i += 1;
                continue;
            }
            phones.push(Phone { text: consonant(ph).to_string(), vowel: false, tonic: false });
        }
        i += 1;
    }
    phones
}

fn render_word(phones: &[Phone]) -> String {
    let nuclei: Vec<usize> = phones.iter().enumerate().filter(|(_, p)| p.vowel).map(|(i, _)| i).collect();
    let mut cuts = Vec::new();
    for k in 0..nuclei.len().saturating_sub(1) {
        let gap = nuclei[k + 1] - nuclei[k] - 1;
        // uma consoante entre vogais vai pra sílaba seguinte ("ha-ppened" -> "RÉ-pend")
        cuts.push(if gap == 0 { nuclei[k + 1] } else { nuclei[k + 1] - 1 });
    }
    let multi = nuclei.len() > 1;
    let mut out = Vec::new();
    let mut start = 0;
    for end in cuts.into_iter().chain(std::iter::once(phones.len())) {
        let syl = &phones[start..end];
        let text: String = syl.iter().map(|p| p.text.as_str()).collect();
        let tonic = syl.iter().any(|p| p.tonic);
        out.push(if tonic && multi { text.to_uppercase() } else { text });
        start = end;
    }
    out.join("-")
}

enum Token {
    Word(String),
    Punct(String),
    Newline,
}

fn tokenize(text: &str) -> Vec<Token> {
    let mut tokens = Vec::new();
    let mut word = String::new();
    let mut punct = String::new();
    let flush = |word: &mut String, punct: &mut String, tokens: &mut Vec<Token>| {
        if !word.is_empty() {
            tokens.push(Token::Word(std::mem::take(word)));
        }
        if !punct.is_empty() {
            tokens.push(Token::Punct(std::mem::take(punct)));
        }
    };
    for ch in text.chars() {
        let ch = if ch == '’' { '\'' } else { ch };
        if ch.is_ascii_alphabetic() || (ch == '\'' && !word.is_empty()) {
            if !punct.is_empty() {
                tokens.push(Token::Punct(std::mem::take(&mut punct)));
            }
            word.push(ch.to_ascii_lowercase());
        } else if ch == '\n' {
            flush(&mut word, &mut punct, &mut tokens);
            tokens.push(Token::Newline);
        } else if ch.is_whitespace() {
            flush(&mut word, &mut punct, &mut tokens);
        } else {
            if !word.is_empty() {
                tokens.push(Token::Word(std::mem::take(&mut word)));
            }
            punct.push(ch);
        }
    }
    flush(&mut word, &mut punct, &mut tokens);
    tokens
}

/// Converte um texto em inglês. Palavras fora do dicionário (onomatopeias, nomes raros)
/// aparecem entre colchetes, como estão escritas.
pub fn pronounce(lexicon_path: &Path, text: &str) -> Option<Pronunciation> {
    let lex = lexicon(lexicon_path)?;
    let tokens = tokenize(text);
    let mut ptbr = String::new();
    let mut ipa = String::new();
    let push = |out: &mut String, piece: &str, attach: bool| {
        if !out.is_empty() && !attach && !out.ends_with('\n') {
            out.push(' ');
        }
        out.push_str(piece);
    };

    let mut i = 0;
    while i < tokens.len() {
        match &tokens[i] {
            Token::Newline => {
                ptbr.push('\n');
                ipa.push('\n');
                i += 1;
            }
            Token::Punct(p) => {
                push(&mut ptbr, p, true);
                push(&mut ipa, p, true);
                i += 1;
            }
            Token::Word(w) => {
                // tenta a expressão mais longa primeiro (até 3 palavras), pra pegar fala conectada
                let mut matched = false;
                for n in (1..=3).rev() {
                    let words: Vec<&str> = tokens[i..]
                        .iter()
                        .take(n)
                        .map_while(|t| if let Token::Word(w) = t { Some(w.as_str()) } else { None })
                        .collect();
                    if words.len() < n {
                        continue;
                    }
                    if let Some(entry) = lex.get(&words.join(" ")) {
                        let parts: Vec<&str> = entry.split(' ').collect();
                        let rendered: Vec<String> = parts
                            .split(|t| t.is_empty())
                            .filter(|w| !w.is_empty())
                            .map(|w| render_word(&word_phones(w)))
                            .collect();
                        push(&mut ptbr, &rendered.join(" "), false);
                        push(&mut ipa, &entry.split("  ").map(|w| w.replace(' ', "")).collect::<Vec<_>>().join(" "), false);
                        i += n;
                        matched = true;
                        break;
                    }
                }
                if !matched {
                    push(&mut ptbr, &format!("[{w}]"), false);
                    push(&mut ipa, &format!("[{w}]"), false);
                    i += 1;
                }
            }
        }
    }
    Some(Pronunciation { ptbr, ipa })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lex_path() -> std::path::PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR")).join("tts-models").join("lexicon-en-us.txt")
    }

    #[test]
    fn regras_principais() {
        let p = pronounce(&lex_path(), "And what happened to its other arm?").unwrap();
        assert_eq!(p.ptbr, "énd uât RÉ-pend tu its Â-der árm?");
        let p = pronounce(&lex_path(), "I didn't think you'd go that far, kid.").unwrap();
        assert!(p.ptbr.contains("think"), "{}", p.ptbr);
        assert!(p.ptbr.contains("dét"), "{}", p.ptbr);
        let p = pronounce(&lex_path(), "Thank you").unwrap();
        assert_eq!(p.ptbr, "thénk iu");
        let p = pronounce(&lex_path(), "really, the party").unwrap();
        assert!(p.ptbr.starts_with("RÍ-li") || p.ptbr.starts_with("RI-li"), "{}", p.ptbr);
        assert!(p.ptbr.contains("PÁ-ri"), "{}", p.ptbr);
    }

    #[test]
    fn fora_do_dicionario_e_quebra_de_linha() {
        let p = pronounce(&lex_path(), "Grrr...\nYou bastard!").unwrap();
        assert_eq!(p.ptbr.lines().count(), 2, "{}", p.ptbr);
        assert!(p.ptbr.starts_with("[grrr]..."), "{}", p.ptbr);
    }
}
