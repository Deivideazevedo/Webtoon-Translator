const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

const player = new Audio();
let speaking = false;

function esc(s) {
  return (s || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function speakBtn(text) {
  const safe = esc(text).replace(/"/g, "&quot;");
  return `<button class="speak" data-text="${safe}" type="button" title="Ouvir em inglês">🔊</button>`;
}

// Cada palavra do original vira um span com a tradução literal no hover. A glosa vem do
// modelo como [palavra, tradução] na ordem do texto; o alinhamento tolera o modelo pular
// ou juntar uma palavra (procura nas próximas 3 posições) — palavra sem par fica sem hover.
function glossedText(text, gloss) {
  const pairs = (gloss || []).map(([w, t]) => [w.toLowerCase().replace(/’/g, "'"), t]);
  let next = 0;
  return text
    .split(/([A-Za-z]+(?:['’][A-Za-z]+)*)/)
    .map((part, i) => {
      if (i % 2 === 0) return esc(part);
      const key = part.toLowerCase().replace(/’/g, "'");
      for (let k = next; k < Math.min(next + 3, pairs.length); k++) {
        if (pairs[k][0] === key) {
          next = k + 1;
          return `<span class="w" data-gloss="${esc(pairs[k][1]).replace(/"/g, "&quot;")}">${esc(part)}</span>`;
        }
      }
      return esc(part);
    })
    .join("");
}

function render(data) {
  document.getElementById("bubble_model").textContent = data.model
    ? `Tradução · ${data.model}`
    : "Tradução";
  const content = document.getElementById("content");
  // Com streaming o balão abre logo após o OCR e vai sendo preenchido: o que ainda não
  // chegou aparece como skeleton enquanto `pending` for true.
  let html = "";
  if (data.translation) {
    html += `<div class="translation">${esc(data.translation)}</div>`;
  } else if (data.pending) {
    html += `<div class="translation skeleton">⏳ Traduzindo…</div>`;
  }
  if (data.original_text) {
    html += `<div class="original">${glossedText(data.original_text, data.gloss)}${speakBtn(data.original_text)}</div>`;
  }
  if (data.pronunciation_pt) {
    html += `<div class="original-pron">🗣 ${esc(data.pronunciation_pt)}</div>`;
  }
  if (data.pending && !(data.breakdown || []).length) {
    html += `<div class="note skeleton">⏳ Detalhando…</div>`;
  }
  for (const item of data.breakdown || []) {
    html += `
      <div class="chunk">
        <div class="chunk-head">
          <span class="segment">${esc(item.segment)}</span>
          ${speakBtn(item.segment)}
        </div>
        ${item.pronunciation_pt ? `<div class="pron">🗣 ${esc(item.pronunciation_pt)}</div>` : ""}
        <div>${esc(item.meaning)}</div>
        ${item.note ? `<div class="note">${esc(item.note)}</div>` : ""}
      </div>`;
  }
  if (data.grammar_notes) {
    html += `<div class="note" style="margin-top:10px;border-top:1px solid var(--border);padding-top:8px;">${esc(data.grammar_notes)}</div>`;
  }
  content.innerHTML = html;

  const speakButtons = content.querySelectorAll(".speak");
  speakButtons.forEach((btn) => {
    btn.addEventListener("click", async () => {
      if (speaking) return;
      speaking = true;
      const original = btn.textContent;
      speakButtons.forEach((b) => (b.disabled = true));
      btn.textContent = "⏳";
      try {
        const timeout = new Promise((_, reject) =>
          setTimeout(() => reject(new Error("Demorou demais pra gerar o áudio (>20s).")), 20000)
        );
        const dataUrl = await Promise.race([invoke("speak_text", { text: btn.dataset.text }), timeout]);

        // O áudio é gerado sempre em 1.0; a velocidade configurada é aplicada aqui no player.
        const settings = await invoke("load_settings").catch(() => null);
        const rate = (settings && settings.speech_speed) || 0.9;
        player.pause();
        player.currentTime = 0;
        player.src = dataUrl;
        player.preservesPitch = true;
        player.defaultPlaybackRate = rate;
        player.playbackRate = rate;
        await new Promise((resolve, reject) => {
          player.onended = resolve;
          player.onerror = () => reject(new Error("O áudio gerado não pôde ser reproduzido."));
          player.play().catch(reject);
        });
      } catch (e) {
        console.error("Falha ao gerar fala:", e);
        btn.title = String(e);
        btn.textContent = "⚠";
        setTimeout(() => {
          btn.textContent = original;
        }, 2000);
      } finally {
        speaking = false;
        speakButtons.forEach((b) => (b.disabled = false));
        if (btn.textContent === "⏳") btn.textContent = original;
      }
    });
  });
}

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
    document.getElementById("content").textContent = "Erro ao carregar tradução: " + e;
  }
})();

document.getElementById("close_btn").addEventListener("click", () => invoke("close_window"));

const head = document.querySelector(".bubble-head");
head.addEventListener("mousedown", (e) => {
  if (e.target.closest("#close_btn")) return;
  invoke("start_window_drag").catch((err) => console.error("Falha ao arrastar:", err));
});
