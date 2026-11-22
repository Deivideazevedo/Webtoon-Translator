const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

const content = document.getElementById("content");
const copyBtn = document.getElementById("copy_btn");
const favBtn = document.getElementById("fav_btn");
const ipaBtn = document.getElementById("ipa_btn");
const learnBtn = document.getElementById("learn_btn");
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

// Lento é absoluto (referência 1.0), não relativo à velocidade configurada. 0,21 = o 🐢 do
// Android (0,30x da fala normal, que é 0,7 aqui), bem devagar pra ouvir a pronúncia.
const SLOW_RATE = 0.21;
// Base de geração do modo "som mais limpo" (CLEAN_SYNTH_SPEED no lib.rs).
const CLEAN_SYNTH_SPEED = 0.7;
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

// 🔊/🐢 empilhados: a coluna estreita à esquerda do texto (como no cartão do Android).
function speakRail(text) {
  return (
    `<div class="rail">` +
    `<button class="circle speak" data-text="${attr(text)}" type="button" title="Ouvir">🔊</button>` +
    `<button class="circle speak" data-text="${attr(text)}" data-slow="1" type="button" title="Ouvir devagar">🐢</button>` +
    `</div>`
  );
}

// Nota do trecho: "Literal:" e "aqui:" em destaque quando o modelo usa esse formato.
function noteHtml(note) {
  return esc(note).replace(/\b(Literal|[Aa]qui):/g, "<b>$1:</b>");
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

  // Tradução, original (com hover) e pronúncia, com 🔊/🐢 à esquerda.
  let main = "";
  if (data.translation) {
    main += `<div class="translation">${esc(data.translation)}</div>`;
  } else if (data.pending) {
    main += `<div class="translation skeleton">⏳ Traduzindo…</div>`;
  } else if (data.grammar_notes) {
    main += `<div class="error-line">${esc(data.grammar_notes)}</div>`;
  }
  if (data.original_text) {
    main += `<div class="original">${withGloss(data.original_text, aligned)}</div>`;
  }
  if (data.pronunciation_pt) {
    main += `<div class="pron">🗣 ${pronHtml(data.pronunciation_pt, data.pronunciation_ipa)}</div>`;
  }
  html += data.original_text
    ? `<div class="block">${speakRail(data.original_text)}<div class="block-text">${main}</div></div>`
    : main;

  // Aprender (aberto pelo 📖 do cabeçalho): trechos + gramática. Trecho igual à frase inteira
  // (ou a um balão inteiro) só repetiria o que já está em cima, então não aparece.
  const lines = new Set((data.original_text || "").split("\n").map(bare).concat(bare(data.original_text)));
  const chunks = (data.breakdown || []).filter((c) => c.segment && !lines.has(bare(c.segment)));
  const grammar = data.translation ? data.grammar_notes : "";
  const canLearn = !data.pending && (chunks.length > 0 || !!grammar);
  if (canLearn && prefs.learnOpen) {
    const tab = prefs.learnTab === "grammar" && grammar ? "grammar" : chunks.length ? "chunks" : "grammar";
    html += `<div class="learn">`;
    if (chunks.length && grammar) {
      html += `<div class="learn-tabs">`;
      html += `<button data-action="tab" data-tab="chunks" class="${tab === "chunks" ? "active" : ""}" type="button">📖 Trechos</button>`;
      html += `<button data-action="tab" data-tab="grammar" class="${tab === "grammar" ? "active" : ""}" type="button">Gramática</button>`;
      html += `</div>`;
    }
    if (tab === "chunks") {
      for (const c of chunks) {
        html += `
          <div class="chunk block">
            ${speakRail(c.segment)}
            <div class="block-text">
              <div class="segment">${withGloss(c.segment, segmentSpans(data.original_text, aligned, c.segment))}</div>
              ${c.pronunciation_pt ? `<div class="pron">🗣 ${pronHtml(c.pronunciation_pt, c.pronunciation_ipa)}</div>` : ""}
              <div class="meaning">${esc(c.meaning)}</div>
              ${c.note ? `<div class="note">${noteHtml(c.note)}</div>` : ""}
            </div>
          </div>`;
      }
    } else {
      html += `<div class="grammar">${esc(grammar)}</div>`;
    }
    html += `</div>`;
  }

  const model = [data.model, data.pending ? "…" : data.duration_secs ? `${data.duration_secs.toFixed(1)}s` : ""]
    .filter(Boolean)
    .join(" · ");
  if (model) html += `<div class="model-chip">${esc(model)}</div>`;

  // O wrapper tem a altura natural do conteúdo; o .bubble-body (flex: 1) sempre ocupa a
  // janela inteira, então medir ele fazia a janela só crescer, nunca encolher.
  content.innerHTML = `<div class="inner">${html}</div>`;

  // Cabeçalho: todos os botões desde o início; os que precisam da tradução pronta ficam apagados.
  const ready = !data.pending && !!data.translation;
  copyBtn.disabled = !ready;
  favBtn.disabled = !ready || !data.id;
  learnBtn.disabled = !canLearn;
  learnBtn.classList.toggle("active", prefs.learnOpen && canLearn);
  ipaBtn.hidden = !data.pronunciation_ipa;
  ipaBtn.classList.toggle("active", prefs.pronMode === "ipa");
  renderFavorite();
  fitHeight();
}

// --- ★ (mesma biblioteca da janela principal e do Android) ---
let favoriteIds = new Set();
function renderFavorite() {
  const on = !!(current && current.id && favoriteIds.has(current.id));
  favBtn.textContent = on ? "★" : "☆";
  favBtn.title = on ? "Tirar dos favoritos" : "Favoritar";
  favBtn.classList.toggle("active", on);
}
async function refreshFavorites() {
  try {
    const list = await invoke("load_favorite_translations");
    favoriteIds = new Set((list || []).map((t) => t.id));
  } catch (_) {}
  renderFavorite();
}
favBtn.addEventListener("click", async () => {
  if (!current || !current.id) return;
  const favorite = !favoriteIds.has(current.id);
  try {
    await invoke("set_translation_favorite", { entry: current, favorite });
    if (favorite) favoriteIds.add(current.id);
    else favoriteIds.delete(current.id);
    renderFavorite();
  } catch (err) {
    console.error("Falha ao favoritar:", err);
  }
});
listen("favorites-changed", refreshFavorites);
refreshFavorites();

ipaBtn.addEventListener("click", () => {
  prefs.pronMode = prefs.pronMode === "ipa" ? "pt" : "ipa";
  savePrefs();
  if (current) render(current);
});
learnBtn.addEventListener("click", () => {
  prefs.learnOpen = !prefs.learnOpen;
  savePrefs();
  if (current) render(current);
});

// A janela acompanha a altura do conteúdo (modo compacto fica pequeno, "Aprender" cresce).
function fitHeight() {
  requestAnimationFrame(() => {
    const head = document.querySelector(".bubble-head").offsetHeight;
    const inner = content.firstElementChild;
    const body = inner ? inner.offsetHeight + 24 : content.scrollHeight; // 24 = padding do .bubble-body
    invoke("fit_bubble_height", { height: head + body + 2 }).catch(() => {});
  });
}

// --- Ações (um listener só: o HTML é recriado a cada atualização do streaming) ---
content.addEventListener("click", async (e) => {
  const btn = e.target.closest("button");
  if (!btn || !current) return;
  const action = btn.dataset.action;
  if (action === "tab") {
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
    // O áudio vem gerado na base do modo (1.0 ou 0,7 nativo); o player aplica só o que falta pra
    // chegar na velocidade desejada (a configurada ou a lenta).
    const settings = await invoke("load_settings").catch(() => null);
    const desired = btn.dataset.slow ? SLOW_RATE : (settings && settings.speech_speed) || 0.7;
    const base = settings && settings.speech_mode === "clean" ? CLEAN_SYNTH_SPEED : 1;
    const rate = desired / base;
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
      // Voz é opcional: em vez de erro, diz onde baixar (no rodapé do modelo, por alguns segundos).
      const chip = content.querySelector(".model-chip");
      if (chip) {
        const before = chip.textContent;
        chip.textContent = "🔇 Voz não instalada — baixe em Configurações → Voz";
        setTimeout(() => (chip.textContent = before), 5000);
      }
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
  copyBtn.textContent = "✓";
  setTimeout(() => (copyBtn.textContent = "📋"), 1500);
});

// --- Opacidade do fundo (Configurações → Geral). Muda ao vivo enquanto o slider é arrastado. ---
const card = document.querySelector(".bubble-card");
function applyOpacity(value) {
  const alpha = Math.min(1, Math.max(0.3, Number(value) || 0.85));
  document.documentElement.style.setProperty("--bubble-alpha", alpha);
  card.classList.toggle("translucent", alpha < 0.95);
}
invoke("load_settings")
  .then((s) => applyOpacity(s && s.bubble_opacity))
  .catch(() => applyOpacity(0.85));
listen("bubble-style", (e) => applyOpacity(e.payload.opacity));

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
  if (e.target.closest("button")) return;
  invoke("start_window_drag").catch((err) => console.error("Falha ao arrastar:", err));
});
