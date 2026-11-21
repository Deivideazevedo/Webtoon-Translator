const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

const content = document.getElementById("content");
const meta = document.getElementById("bubble_meta");
const copyBtn = document.getElementById("copy_btn");
const player = new Audio();
let speaking = false;
let current = null;

// Preferências do balão (sobrevivem entre capturas). Se o storage falhar, usa o padrão.
const prefs = {
  learnOpen: false, // fechado = modo compacto
  learnTab: "chunks",
  pronMode: "pt", // "pt" | "ipa"
};
try {
  Object.assign(prefs, JSON.parse(localStorage.getItem("bubble.prefs") || "{}"));
} catch (_) {}
function savePrefs() {
  try {
    localStorage.setItem("bubble.prefs", JSON.stringify(prefs));
  } catch (_) {}
}

// Lento é absoluto (referência 1.0), não relativo à velocidade configurada: com 0.7
// configurado, 0.7 × 0.7 dava 0.49 e ficava arrastado.
const SLOW_RATE = 0.5;
const TH_HINT = "th: ponta da língua entre os dentes, soprando o ar — não é f nem t";
const WORD_RE = /[A-Za-z]+(?:['’][A-Za-z]+)*/g;

function esc(s) {
  return (s || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
function attr(s) {
  return esc(s).replace(/"/g, "&quot;");
}
const norm = (s) => (s || "").toLowerCase().replace(/’/g, "'");
const bare = (s) => norm(s).replace(/[^a-z' ]/g, " ").replace(/\s+/g, " ").trim();

function speakButtons(text) {
  return (
    `<button class="speak" data-text="${attr(text)}" type="button" title="Ouvir">🔊</button>` +
    `<button class="speak" data-text="${attr(text)}" data-slow="1" type="button" title="Ouvir devagar">🐢</button>`
  );
}

// --- Glosa (tradução literal no hover) ---
// O modelo manda [palavra, tradução] na ordem de original_text. Alinha uma vez com as
// posições das palavras no original; o alinhamento tolera o modelo pular ou juntar uma
// palavra (procura nas próximas 3 posições) — palavra sem par fica sem hover.
function alignGloss(text, gloss) {
  const pairs = (gloss || []).map(([w, t]) => [norm(w), t]);
  const spans = [];
  let next = 0;
  for (const m of (text || "").matchAll(WORD_RE)) {
    const key = norm(m[0]);
    for (let k = next; k < Math.min(next + 3, pairs.length); k++) {
      if (pairs[k][0] === key) {
        next = k + 1;
        spans.push({ start: m.index, end: m.index + m[0].length, gloss: pairs[k][1] });
        break;
      }
    }
  }
  return spans;
}

function withGloss(text, spans) {
  let html = "";
  let pos = 0;
  for (const s of spans) {
    html += esc(text.slice(pos, s.start));
    html += `<span class="w" data-gloss="${attr(s.gloss)}">${esc(text.slice(s.start, s.end))}</span>`;
    pos = s.end;
  }
  return html + esc(text.slice(pos));
}

// Trechos do breakdown são pedaços do original: reaproveita a glosa das mesmas posições
// (mantém o sentido daquela ocorrência). Se o trecho não for achado literalmente, cai pra
// busca palavra a palavra.
function segmentSpans(original, aligned, segment) {
  const idx = norm(original).indexOf(norm(segment));
  if (idx >= 0) {
    return aligned
      .filter((s) => s.start >= idx && s.end <= idx + segment.length)
      .map((s) => ({ ...s, start: s.start - idx, end: s.end - idx }));
  }
  const byWord = new Map();
  for (const s of aligned) {
    const w = norm(original.slice(s.start, s.end));
    if (!byWord.has(w)) byWord.set(w, s.gloss);
  }
  const out = [];
  for (const m of segment.matchAll(WORD_RE)) {
    const g = byWord.get(norm(m[0]));
    if (g) out.push({ start: m.index, end: m.index + m[0].length, gloss: g });
  }
  return out;
}

// --- Pronúncia (PT-BR ⇄ IPA, ambas calculadas localmente) ---
function pronHtml(pt, ipa) {
  if (prefs.pronMode === "ipa" && ipa) return `/${esc(ipa)}/`;
  // "th" não existe em português: marca com a dica no hover.
  return esc(pt).replace(/th/g, `<span class="w th" data-gloss="${attr(TH_HINT)}">th</span>`);
}

function render(data) {
  current = data;
  const aligned = alignGloss(data.original_text, data.gloss);
  let html = "";

  // Camada 1: tradução
  if (data.translation) {
    html += `<div class="translation">${esc(data.translation)}</div>`;
  } else if (data.pending) {
    html += `<div class="translation skeleton">⏳ Traduzindo…</div>`;
  } else if (data.grammar_notes) {
    html += `<div class="error-line">${esc(data.grammar_notes)}</div>`;
  }

  // Camada 2: original (com hover) + áudio + pronúncia
  if (data.original_text) {
    html += `<div class="original">${withGloss(data.original_text, aligned)} ${speakButtons(data.original_text)}</div>`;
  }
  if (data.pronunciation_pt) {
    const toggle = data.pronunciation_ipa
      ? `<button class="mini" data-action="pron" type="button" title="Alternar PT-BR / IPA">${prefs.pronMode === "ipa" ? "PT" : "IPA"}</button>`
      : "";
    html += `<div class="pron-row"><span class="pron-text">🗣 ${pronHtml(data.pronunciation_pt, data.pronunciation_ipa)}</span>${toggle}</div>`;
  }

  // Camada 3: aprender (trechos + gramática). Trecho igual à frase inteira (ou a um balão
  // inteiro) só repetiria o que já está em cima, então não aparece.
  const lines = new Set((data.original_text || "").split("\n").map(bare).concat(bare(data.original_text)));
  const chunks = (data.breakdown || []).filter((c) => c.segment && !lines.has(bare(c.segment)));
  const grammar = data.translation ? data.grammar_notes : "";
  if (chunks.length || grammar) {
    const tab = prefs.learnTab === "grammar" && grammar ? "grammar" : chunks.length ? "chunks" : "grammar";
    html += `<div class="learn"><button class="learn-toggle" data-action="learn" type="button">📖 Aprender ${prefs.learnOpen ? "▴" : "▾"}</button>`;
    if (prefs.learnOpen) {
      html += `<div class="learn-tabs">`;
      if (chunks.length)
        html += `<button data-action="tab" data-tab="chunks" class="${tab === "chunks" ? "active" : ""}" type="button">Trechos (${chunks.length})</button>`;
      if (grammar)
        html += `<button data-action="tab" data-tab="grammar" class="${tab === "grammar" ? "active" : ""}" type="button">Gramática</button>`;
      html += `</div>`;
      if (tab === "chunks") {
        for (const c of chunks) {
          html += `
            <div class="chunk">
              <div class="chunk-head">
                <span class="segment">${withGloss(c.segment, segmentSpans(data.original_text, aligned, c.segment))}</span>
                ${speakButtons(c.segment)}
              </div>
              ${c.pronunciation_pt ? `<div class="pron-row">🗣 ${pronHtml(c.pronunciation_pt, c.pronunciation_ipa)}</div>` : ""}
              <div class="meaning">${esc(c.meaning)}</div>
              ${c.note ? `<div class="note">${esc(c.note)}</div>` : ""}
            </div>`;
        }
      } else {
        html += `<div class="note">${esc(grammar)}</div>`;
      }
    }
    html += `</div>`;
  } else if (data.pending) {
    html += `<div class="learn skeleton">⏳ Detalhando…</div>`;
  }

  // O wrapper tem a altura natural do conteúdo; o .bubble-body (flex: 1) sempre ocupa a
  // janela inteira, então medir ele fazia a janela só crescer, nunca encolher.
  content.innerHTML = `<div class="inner">${html}</div>`;
  meta.textContent = [data.model, data.pending ? "…" : data.duration_secs ? `${data.duration_secs.toFixed(1)}s` : ""]
    .filter(Boolean)
    .join(" · ");
  copyBtn.disabled = !data.translation;
  fitHeight();
}

// A janela acompanha a altura do conteúdo (modo compacto fica pequeno, "Aprender" cresce).
function fitHeight() {
  requestAnimationFrame(() => {
    const head = document.querySelector(".bubble-head").offsetHeight;
    const foot = document.querySelector(".bubble-foot").offsetHeight;
    const inner = content.firstElementChild;
    const body = inner ? inner.offsetHeight + 24 : content.scrollHeight; // 24 = padding do .bubble-body
    invoke("fit_bubble_height", { height: head + body + foot + 2 }).catch(() => {});
  });
}

// --- Ações (um listener só: o HTML é recriado a cada atualização do streaming) ---
content.addEventListener("click", async (e) => {
  const btn = e.target.closest("button");
  if (!btn || !current) return;
  const action = btn.dataset.action;
  if (action === "pron") {
    prefs.pronMode = prefs.pronMode === "ipa" ? "pt" : "ipa";
  } else if (action === "learn") {
    prefs.learnOpen = !prefs.learnOpen;
  } else if (action === "tab") {
    prefs.learnTab = btn.dataset.tab;
  } else if (btn.classList.contains("speak")) {
    return speak(btn);
  } else {
    return;
  }
  savePrefs();
  render(current);
});

async function speak(btn) {
  if (speaking) return;
  speaking = true;
  const all = content.querySelectorAll(".speak");
  all.forEach((b) => (b.disabled = true));
  const label = btn.textContent;
  btn.textContent = "⏳";
  try {
    const timeout = new Promise((_, reject) =>
      setTimeout(() => reject(new Error("Demorou demais pra gerar o áudio (>20s).")), 20000)
    );
    const dataUrl = await Promise.race([invoke("speak_text", { text: btn.dataset.text }), timeout]);
    // O áudio é gerado sempre em 1.0; a velocidade (configurada ou a lenta) é aplicada no player.
    const settings = await invoke("load_settings").catch(() => null);
    const rate = btn.dataset.slow ? SLOW_RATE : (settings && settings.speech_speed) || 0.9;
    player.pause();
    player.currentTime = 0;
    player.src = dataUrl;
    player.preservesPitch = true;
    player.defaultPlaybackRate = rate;
    player.playbackRate = rate;
    btn.textContent = label;
    await new Promise((resolve, reject) => {
      player.onended = resolve;
      player.onerror = () => reject(new Error("O áudio gerado não pôde ser reproduzido."));
      player.play().catch(reject);
    });
  } catch (err) {
    if (String(err).includes("VOZ_NAO_INSTALADA")) {
      // Voz é opcional: em vez de erro, diz onde baixar (no rodapé, por alguns segundos).
      const before = meta.textContent;
      meta.textContent = "🔇 Voz não instalada — baixe em Configurações → Voz";
      setTimeout(() => (meta.textContent = before), 5000);
      return;
    }
    console.error("Falha ao gerar fala:", err);
    btn.title = String(err);
    btn.textContent = "⚠";
    setTimeout(() => (btn.textContent = label), 2000);
  } finally {
    speaking = false;
    content.querySelectorAll(".speak").forEach((b) => (b.disabled = false));
    if (btn.textContent === "⏳") btn.textContent = label;
  }
}

copyBtn.addEventListener("click", async () => {
  if (!current || !current.translation) return;
  const text = `${current.original_text}\n${current.translation}`;
  try {
    await navigator.clipboard.writeText(text);
  } catch (_) {
    const ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    ta.remove();
  }
  copyBtn.textContent = "✓ Copiado";
  setTimeout(() => (copyBtn.textContent = "📋 Copiar"), 1500);
});

// O listener entra antes de buscar o estado: atualizações que chegarem enquanto a janela
// carrega não se perdem. Se algum evento já renderizou, o estado buscado (possivelmente
// mais velho) é ignorado.
let renderedFromEvent = false;
(async () => {
  await listen("bubble-update", (event) => {
    renderedFromEvent = true;
    render(event.payload);
  });
  try {
    const data = await invoke("get_bubble_data");
    if (data && !renderedFromEvent) render(data);
  } catch (e) {
    content.textContent = "Erro ao carregar tradução: " + e;
  }
})();

document.getElementById("close_btn").addEventListener("click", () => invoke("close_window"));
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") invoke("close_window");
});

const head = document.querySelector(".bubble-head");
head.addEventListener("mousedown", (e) => {
  if (e.target.closest("#close_btn")) return;
  invoke("start_window_drag").catch((err) => console.error("Falha ao arrastar:", err));
});
