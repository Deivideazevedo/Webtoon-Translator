//! OCR local (ocrs + rten, Rust puro: roda igual no desktop e no Android).

use std::path::Path;

pub use ocrs::OcrEngine;

/// Carrega os modelos de detecção e reconhecimento de `models_dir` (text-detection.onnx e
/// text-recognition.onnx).
pub fn load_engine(models_dir: &Path) -> Result<OcrEngine, String> {
    let base = models_dir;
    let detection_model = rten::Model::load_file(base.join("text-detection.onnx"))
        .map_err(|e| format!("Falha ao carregar modelo de detecção de texto: {e}"))?;
    let recognition_model = rten::Model::load_file(base.join("text-recognition.onnx"))
        .map_err(|e| format!("Falha ao carregar modelo de reconhecimento de texto: {e}"))?;
    ocrs::OcrEngine::new(ocrs::OcrEngineParams {
        detection_model: Some(detection_model),
        recognition_model: Some(recognition_model),
        ..Default::default()
    })
    .map_err(|e| format!("Falha ao iniciar o OCR: {e}"))
}

/// Lê o texto de uma imagem RGB (3 bytes por pixel, linha a linha).
pub fn recognize_text(engine: &OcrEngine, rgb: &[u8], width: u32, height: u32) -> Result<String, String> {
    let img_source = ocrs::ImageSource::from_bytes(rgb, (width, height))
        .map_err(|e| format!("Falha ao preparar imagem pro OCR: {e}"))?;
    let ocr_input = engine
        .prepare_input(img_source)
        .map_err(|e| format!("Falha ao processar imagem pro OCR: {e}"))?;
    let text = engine
        .get_text(&ocr_input)
        .map_err(|e| format!("Falha ao extrair texto: {e}"))?;

    // Filtra detecções espúrias (letras soltas nas bordas de balões), igual o
    // próprio exemplo oficial do ocrs recomenda.
    Ok(text
        .lines()
        .filter(|l| l.trim().chars().count() > 1)
        .collect::<Vec<_>>()
        .join("\n"))
}

/// Texto de um recorte com as linhas de cada balão juntas num parágrafo, e balões diferentes
/// separados por "\n". O OCR devolve uma linha por linha do desenho; mandando assim pro modelo,
/// ele às vezes trata cada linha como um balão e devolve a tradução quebrada.
pub fn recognize_paragraphs(engine: &OcrEngine, rgb: &[u8], width: u32, height: u32) -> Result<String, String> {
    let lines = recognize_lines(engine, rgb, width, height)?;
    Ok(group_blocks(&lines).into_iter().map(|b| b.text).collect::<Vec<_>>().join("\n"))
}

/// Uma linha de texto e onde ela está na imagem (pixels).
#[derive(Clone, Debug, PartialEq)]
pub struct OcrLine {
    pub text: String,
    pub left: i32,
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
}

impl OcrLine {
    fn height(&self) -> i32 {
        self.bottom - self.top
    }
}

/// Um balão (ou legenda): linhas próximas juntadas, com a caixa que envolve todas.
#[derive(Clone, Debug, PartialEq)]
pub struct TextBlock {
    /// Linhas do balão separadas por espaço (o OCR quebra onde o quadrinho quebra).
    pub text: String,
    pub left: i32,
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
}

/// Linhas de texto com a posição de cada uma (modo tela cheia), na ordem de leitura.
pub fn recognize_lines(engine: &OcrEngine, rgb: &[u8], width: u32, height: u32) -> Result<Vec<OcrLine>, String> {
    use ocrs::TextItem;
    let img_source = ocrs::ImageSource::from_bytes(rgb, (width, height))
        .map_err(|e| format!("Falha ao preparar imagem pro OCR: {e}"))?;
    let input = engine
        .prepare_input(img_source)
        .map_err(|e| format!("Falha ao processar imagem pro OCR: {e}"))?;
    let words = engine.detect_words(&input).map_err(|e| format!("Falha ao achar texto: {e}"))?;
    let line_rects = engine.find_text_lines(&input, &words);
    let lines = engine
        .recognize_text(&input, &line_rects)
        .map_err(|e| format!("Falha ao extrair texto: {e}"))?;
    Ok(lines
        .into_iter()
        .flatten()
        .filter_map(|line| {
            let text = line.to_string().trim().to_string();
            // Letras soltas e números costumam ser ruído (bordas de balão, relógio, contadores).
            if text.chars().filter(|c| c.is_alphabetic()).count() < 2 {
                return None;
            }
            let r = line.bounding_rect();
            Some(OcrLine { text, left: r.left(), top: r.top(), right: r.right(), bottom: r.bottom() })
        })
        .collect())
}

/// Junta linhas do mesmo balão: uma linha entra num bloco se estiver logo abaixo dele (espaço
/// menor que ~1 altura de linha) ou na mesma altura encostada nele, e alinhada na horizontal
/// (caixas se sobrepõem ou centros perto). Linha quase toda dentro de um bloco é leitura
/// duplicada do OCR e é descartada. Webtoon é uma coluna só, então isso separa bem os balões.
pub fn group_blocks(lines: &[OcrLine]) -> Vec<TextBlock> {
    let mut blocks: Vec<(TextBlock, i32)> = Vec::new(); // (bloco, altura da última linha)
    let mut sorted: Vec<&OcrLine> = lines.iter().collect();
    sorted.sort_by_key(|l| (l.top, l.left));
    for line in sorted {
        let h = line.height().max(1);
        let center = (line.left + line.right) / 2;
        if blocks.iter().any(|(b, _)| inside_ratio(line, b) > 0.6) {
            continue;
        }
        let target = blocks.iter_mut().rev().find(|(b, last_h)| {
            let gap = line.top - b.bottom;
            let max_h = (*last_h).max(h);
            let overlaps = line.left < b.right && line.right > b.left;
            let centered = (center - (b.left + b.right) / 2).abs() < (b.right - b.left).max(line.right - line.left) / 2;
            // gap negativo = a linha começa antes do fim do bloco (mesma altura).
            gap > -(b.bottom - b.top) && gap < max_h * 6 / 5 && (overlaps || centered)
        });
        match target {
            Some((block, last_h)) => {
                // Palavra partida na quebra ("HORSE-" + "MANSHIP"): junta sem espaço.
                if !block.text.ends_with('-') {
                    block.text.push(' ');
                }
                block.text.push_str(&line.text);
                block.left = block.left.min(line.left);
                block.top = block.top.min(line.top);
                block.right = block.right.max(line.right);
                block.bottom = block.bottom.max(line.bottom);
                *last_h = h;
            }
            None => blocks.push((
                TextBlock { text: line.text.clone(), left: line.left, top: line.top, right: line.right, bottom: line.bottom },
                h,
            )),
        }
    }
    blocks.into_iter().map(|(b, _)| b).collect()
}

/// Quanto da área da linha está dentro do bloco (0 a 1).
fn inside_ratio(line: &OcrLine, block: &TextBlock) -> f32 {
    let w = (line.right.min(block.right) - line.left.max(block.left)).max(0);
    let h = (line.bottom.min(block.bottom) - line.top.max(block.top)).max(0);
    let area = ((line.right - line.left) * (line.bottom - line.top)).max(1);
    (w * h) as f32 / area as f32
}
