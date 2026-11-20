const { invoke } = window.__TAURI__.core;

const viewHome = document.getElementById("view-home");
const viewSettings = document.getElementById("view-settings");
const list = document.getElementById("list");

const providerSelect = document.getElementById("provider");
const providerDetails = document.getElementById("provider_details");
const providerSummaryValue = document.getElementById("provider_summary_value");
const baseUrlInput = document.getElementById("base_url");
const apiKeyInput = document.getElementById("api_key");
const modelInput = document.getElementById("model_input");
const modelListbox = document.getElementById("model_listbox");
const modelClearBtn = document.getElementById("model_clear");
const modelToggleBtn = document.getElementById("model_toggle");
const benchBtn = document.getElementById("bench_btn");
const benchStatus = document.getElementById("bench_status");
const benchResults = document.getElementById("bench_results");
const favoriteBtn = document.getElementById("favorite_btn");
const testBtn = document.getElementById("test_btn");
const saveBtn = document.getElementById("save_btn");
const speedTestBtn = document.getElementById("speed_test_btn");
const testStatus = document.getElementById("test_status");
const saveStatus = document.getElementById("save_status");
const speedStatus = document.getElementById("speed_status");
const alwaysOnTopInput = document.getElementById("always_on_top");
const shortcutBtn = document.getElementById("shortcut_btn");
const shortcutStatus = document.getElementById("shortcut_status");
const speechSpeedInput = document.getElementById("speech_speed");
const speechSpeedValue = document.getElementById("speech_speed_value");

const PROVIDER_LABELS = {
  openai: "Gateway OpenAI",
  anthropic: "Anthropic direto",
  google: "Google AI Studio",
};

// Campos de settings.json (base_url/model) usados por cada provedor.
const PROVIDER_FIELDS = {
  openai: { baseUrl: "base_url", model: "model", defaultBaseUrl: "http://localhost" },
  anthropic: {
    baseUrl: "anthropic_base_url",
    model: "anthropic_model",
    defaultBaseUrl: "https://api.anthropic.com",
  },
  google: {
    baseUrl: "google_base_url",
    model: "google_model",
    defaultBaseUrl: "https://generativelanguage.googleapis.com",
  },
};

alwaysOnTopInput.addEventListener("change", async () => {
  const enabled = alwaysOnTopInput.checked;
  try {
    await invoke("set_always_on_top", { enabled });
    const current = await invoke("load_settings");
    await invoke("save_settings", { settings: { ...current, always_on_top: enabled } });
  } catch (e) {
    console.error("Falha ao salvar preferência de fixar janela:", e);
  }
});

speechSpeedInput.addEventListener("input", () => {
  speechSpeedValue.textContent = `${Number(speechSpeedInput.value).toFixed(2)}x`;
});

speechSpeedInput.addEventListener("change", async () => {
  try {
    const current = await invoke("load_settings");
    await invoke("save_settings", {
      settings: { ...current, speech_speed: Number(speechSpeedInput.value) },
    });
  } catch (e) {
    console.error("Falha ao salvar velocidade da voz:", e);
  }
});

let recordingShortcut = false;

shortcutBtn.addEventListener("click", () => {
  recordingShortcut = true;
  shortcutBtn.textContent = "Pressione a combinação...";
  shortcutBtn.disabled = true;
});

const SHORTCUT_KEY_MAP = {
  " ": "Space",
  ArrowUp: "Up",
  ArrowDown: "Down",
  ArrowLeft: "Left",
  ArrowRight: "Right",
  Escape: "Escape",
};

document.addEventListener("keydown", async (e) => {
  if (!recordingShortcut) return;
  if (["Control", "Shift", "Alt", "Meta"].includes(e.key)) return;
  e.preventDefault();

  const parts = [];
  if (e.ctrlKey || e.metaKey) parts.push("CommandOrControl");
  if (e.shiftKey) parts.push("Shift");
  if (e.altKey) parts.push("Alt");
  const key = SHORTCUT_KEY_MAP[e.key] || (e.key.length === 1 ? e.key.toUpperCase() : e.key);
  parts.push(key);
  const accelerator = parts.join("+");

  recordingShortcut = false;
  shortcutBtn.disabled = false;

  try {
    await invoke("set_shortcut", { accelerator });
    shortcutBtn.textContent = accelerator;
    setStatus(shortcutStatus, "Atalho salvo.", "ok");
  } catch (err) {
    setStatus(shortcutStatus, friendlyError(err), "error");
    const settings = await invoke("load_settings").catch(() => null);
    shortcutBtn.textContent = (settings && settings.shortcut) || "CommandOrControl+Shift+T";
  }
});

function setStatus(el, message, kind) {
  el.textContent = message;
  el.className = "status" + (kind ? " " + kind : "");
}

// As respostas de erro dos gateways (Anthropic, OpenAI, etc.) vêm como um corpo
// JSON cru. Em vez de jogar isso tudo na tela, extrai só a mensagem legível.
function friendlyError(raw) {
  const text = String(raw || "");
  const match = text.match(/"message"\s*:\s*"([^"]+)"/);
  return match ? match[1] : text;
}

function showView(name) {
  viewHome.style.display = name === "home" ? "" : "none";
  viewSettings.style.display = name === "settings" ? "" : "none";
  if (name === "home") refreshHome();
}

function esc(s) {
  return (s || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function truncate(s, n) {
  s = s || "";
  return s.length > n ? s.slice(0, n) + "…" : s;
}

function activeModel(settings) {
  if (!settings) return "";
  const fields = PROVIDER_FIELDS[settings.provider] || PROVIDER_FIELDS.openai;
  return settings[fields.model];
}

function favoritesFor(settings, provider) {
  return (settings && settings.favorites && settings.favorites[provider]) || [];
}

function renderQuickModelSelect(settings, model) {
  const select = document.getElementById("quick_model_select");
  if (!settings || !model) {
    select.style.display = "none";
    return;
  }
  const provider = settings.provider || "openai";
  const favorites = favoritesFor(settings, provider);
  const options = new Set(favorites);
  options.add(model); // o modelo atual sempre aparece, mesmo que não esteja favoritado
  if (options.size <= 1) {
    // sem favoritos cadastrados ainda — não faz sentido mostrar um select de 1 item só
    select.style.display = "none";
    return;
  }
  select.innerHTML = Array.from(options)
    .map((id) => `<option value="${esc(id)}" ${id === model ? "selected" : ""}>${esc(id)}</option>`)
    .join("");
  select.style.display = "";
}

document.getElementById("quick_model_select").addEventListener("change", async (e) => {
  const id = e.target.value;
  const current = await invoke("load_settings").catch(() => ({}));
  const provider = current.provider || "openai";
  const fields = PROVIDER_FIELDS[provider] || PROVIDER_FIELDS.openai;
  const updated = { ...current, [fields.model]: id };
  await invoke("save_settings", { settings: updated }).catch((err) => console.error(err));
  refreshHome();
});

async function refreshHome() {
  const settings = await invoke("load_settings").catch(() => null);
  const model = activeModel(settings);
  const key = settings
    ? await invoke("get_api_key", { provider: settings.provider || "openai" }).catch(() => null)
    : null;

  renderQuickModelSelect(settings, model);

  if (!settings || !model || !key) {
    list.innerHTML = `
      <div class="card empty">
        Nenhuma tradução ainda.<br>
        Abra as Configurações, cole a chave e a URL do seu gateway, teste a conexão e escolha um modelo.
      </div>`;
    return;
  }

  const history = await invoke("load_history").catch(() => []);
  if (!history || history.length === 0) {
    list.innerHTML = `
      <div class="card empty">
        Configurado com <b>${esc(model)}</b>.<br>
        Capture uma área da tela pra começar a traduzir.
      </div>`;
    return;
  }

  list.innerHTML = history
    .map(
      (item, i) => `
      <div class="card history-item" data-index="${i}" style="cursor:pointer; position:relative;">
        <div class="status" style="position:absolute; top:12px; right:16px; margin:0;">
          🕐 ${(item.duration_secs || 0).toFixed(1)}s
        </div>
        <div class="original" style="margin:0 0 4px; padding-right:60px;">${esc(truncate(item.original_text, 60))}</div>
        <div style="margin:0;">${esc(truncate(item.translation, 80))}</div>
        ${item.model ? `<div class="status" style="margin:6px 0 0;">via ${esc(item.model)}</div>` : ""}
      </div>`
    )
    .join("");

  list.querySelectorAll(".history-item").forEach((el) => {
    el.addEventListener("click", () => {
      const item = history[Number(el.dataset.index)];
      invoke("reopen_bubble", { result: item }).catch((e) => setStatus(captureStatus, friendlyError(e), "error"));
    });
  });
}

let currentSettings = null;
const fetchedModelsByProvider = {};

function fieldsForProvider(provider, settings) {
  const fields = PROVIDER_FIELDS[provider] || PROVIDER_FIELDS.openai;
  return {
    baseUrl: (settings && settings[fields.baseUrl]) || fields.defaultBaseUrl,
    model: (settings && settings[fields.model]) || "",
  };
}

// --- Campo de modelo: combobox próprio no estilo do Autocomplete do MUI. ---
// O <datalist> nativo filtrava pelo texto do input, então com um modelo já escolhido
// só sobrava ele mesmo na lista. Aqui abrir sempre mostra tudo; só filtra enquanto se digita.

const combo = { models: [], filtering: false, active: -1, visible: [] };

function comboOptions() {
  const provider = providerSelect.value;
  const favs = new Set(favoritesFor(currentSettings, provider));
  const query = combo.filtering ? modelInput.value.trim().toLowerCase() : "";
  return sortModelsWithFavoritesFirst(combo.models, provider)
    .filter((m) => !query || m.id.toLowerCase().includes(query))
    .map((m) => ({
      id: m.id,
      label: `${favs.has(m.id) ? "★ " : ""}${m.id}${m.capabilities && m.capabilities.vision ? " (visão)" : ""}`,
    }));
}

function renderCombo() {
  combo.visible = comboOptions();
  const current = modelInput.value.trim();
  if (combo.visible.length === 0) {
    modelListbox.innerHTML = `<li class="empty">Nenhum modelo contém "${esc(current)}"</li>`;
    return;
  }
  modelListbox.innerHTML = combo.visible
    .map((o, i) => {
      const cls = [i === combo.active ? "active" : "", o.id === current ? "selected" : ""].join(" ");
      return `<li role="option" data-index="${i}" class="${cls}" aria-selected="${o.id === current}">${esc(o.label)}</li>`;
    })
    .join("");
  const activeEl = modelListbox.querySelector("li.active");
  if (activeEl) activeEl.scrollIntoView({ block: "nearest" });
}

function openCombo() {
  if (modelInput.disabled || combo.models.length === 0) return;
  if (modelListbox.hidden) {
    combo.filtering = false;
    combo.active = comboOptions().findIndex((o) => o.id === modelInput.value.trim());
    modelListbox.hidden = false;
    modelInput.setAttribute("aria-expanded", "true");
  }
  renderCombo();
}

function closeCombo() {
  modelListbox.hidden = true;
  modelInput.setAttribute("aria-expanded", "false");
  combo.filtering = false;
  combo.active = -1;
}

function updateComboButtons() {
  modelClearBtn.hidden = modelInput.disabled || !modelInput.value;
  modelToggleBtn.hidden = modelInput.disabled;
}

function selectModel(id) {
  modelInput.value = id;
  closeCombo();
  updateComboButtons();
  updateFavoriteBtn();
}

modelInput.addEventListener("focus", openCombo);
modelInput.addEventListener("click", openCombo);
modelInput.addEventListener("input", () => {
  combo.filtering = true;
  combo.active = 0;
  if (modelListbox.hidden && combo.models.length) {
    modelListbox.hidden = false;
    modelInput.setAttribute("aria-expanded", "true");
  }
  renderCombo();
  updateComboButtons();
  updateFavoriteBtn();
});
modelInput.addEventListener("keydown", (e) => {
  if (e.key === "ArrowDown" || e.key === "ArrowUp") {
    e.preventDefault();
    if (modelListbox.hidden) return openCombo();
    const n = combo.visible.length;
    if (!n) return;
    combo.active = e.key === "ArrowDown" ? (combo.active + 1) % n : (combo.active - 1 + n) % n;
    renderCombo();
  } else if (e.key === "Enter" && !modelListbox.hidden && combo.visible[combo.active]) {
    e.preventDefault();
    selectModel(combo.visible[combo.active].id);
  } else if (e.key === "Escape" && !modelListbox.hidden) {
    e.preventDefault();
    closeCombo();
  }
});
// Texto livre continua valendo (dá pra digitar um id que não está na lista).
modelInput.addEventListener("blur", closeCombo);
// mousedown + preventDefault: escolher um item não tira o foco do input antes do clique contar.
modelListbox.addEventListener("mousedown", (e) => {
  e.preventDefault();
  const li = e.target.closest("li[data-index]");
  if (li) selectModel(combo.visible[Number(li.dataset.index)].id);
});
modelToggleBtn.addEventListener("mousedown", (e) => {
  e.preventDefault();
  if (modelListbox.hidden) {
    modelInput.focus();
    openCombo();
  } else {
    closeCombo();
  }
});
modelClearBtn.addEventListener("mousedown", (e) => {
  e.preventDefault();
  modelInput.value = "";
  updateComboButtons();
  updateFavoriteBtn();
  closeCombo();
  modelInput.focus();
  openCombo();
});

function updateFavoriteBtn() {
  const provider = providerSelect.value;
  const id = modelInput.value.trim();
  const favs = favoritesFor(currentSettings, provider);
  const isFav = !!id && favs.includes(id);
  favoriteBtn.textContent = isFav ? "★" : "☆";
  favoriteBtn.title = isFav ? "Remover dos favoritos" : "Favoritar este modelo";
  favoriteBtn.disabled = !id;
}

favoriteBtn.addEventListener("click", async () => {
  const provider = providerSelect.value;
  const id = modelInput.value.trim();
  if (!id) return;
  const current = await invoke("load_settings").catch(() => currentSettings || {});
  const favorites = { ...(current.favorites || {}) };
  const set = new Set(favorites[provider] || []);
  if (set.has(id)) {
    set.delete(id);
  } else {
    set.add(id);
  }
  favorites[provider] = Array.from(set);
  const updated = { ...current, favorites };
  await invoke("save_settings", { settings: updated });
  currentSettings = updated;
  updateFavoriteBtn();
  // Reordena a lista existente pra refletir o novo favorito no topo.
  const cached = fetchedModelsByProvider[provider];
  if (cached) fillModelSelect(cached, modelInput.value.trim());
});

function sortModelsWithFavoritesFirst(models, provider) {
  const favs = new Set(favoritesFor(currentSettings, provider));
  return [...models].sort((a, b) => (favs.has(a.id) ? 0 : 1) - (favs.has(b.id) ? 0 : 1));
}

function fillModelSelect(models, previousSelected) {
  combo.models = models;
  if (models.length === 0) {
    modelInput.disabled = true;
    modelInput.placeholder = "Nenhum modelo encontrado";
    modelInput.value = "";
  } else {
    modelInput.disabled = false;
    modelInput.placeholder = "Clique pra ver todos ou digite pra filtrar...";
    if (previousSelected && models.some((m) => m.id === previousSelected)) {
      modelInput.value = previousSelected;
    }
  }
  if (!modelListbox.hidden) renderCombo();
  updateComboButtons();
  updateFavoriteBtn();
}

function updateProviderSummary() {
  providerSummaryValue.textContent = PROVIDER_LABELS[providerSelect.value] || providerSelect.value;
}

async function applyProviderToUI(provider) {
  const { baseUrl, model } = fieldsForProvider(provider, currentSettings);
  baseUrlInput.value = baseUrl;
  const cached = fetchedModelsByProvider[provider];
  if (cached) {
    // Já testamos esse provedor nesta sessão — reaproveita a lista inteira
    // em vez de voltar a mostrar só o modelo salvo.
    fillModelSelect(cached, model);
  } else if (model) {
    combo.models = [{ id: model }];
    modelInput.disabled = false;
    modelInput.value = model;
  } else {
    combo.models = [];
    modelInput.disabled = true;
    modelInput.placeholder = "Teste a conexão para listar os modelos";
    modelInput.value = "";
  }
  closeCombo();
  updateComboButtons();
  updateFavoriteBtn();
  try {
    const key = await invoke("get_api_key", { provider });
    apiKeyInput.value = key || "";
  } catch (e) {
    apiKeyInput.value = "";
  }
  updateProviderSummary();
}

async function openSettings() {
  showView("settings");
  document.querySelectorAll(".tab").forEach((b) => b.classList.remove("active"));
  document.querySelectorAll(".tab-panel").forEach((p) => (p.style.display = "none"));
  document.querySelector('.tab[data-tab="connection"]').classList.add("active");
  document.getElementById("tab-connection").style.display = "";
  setStatus(testStatus, "", "");
  setStatus(saveStatus, "", "");
  setStatus(speedStatus, "", "");
  setStatus(benchStatus, "", "");
  benchResults.innerHTML = "";
  providerDetails.open = false; // acordeão recolhido por padrão, pra ganhar espaço

  try {
    currentSettings = await invoke("load_settings");
  } catch (e) {
    currentSettings = null;
  }

  providerSelect.value = (currentSettings && currentSettings.provider) || "openai";
  alwaysOnTopInput.checked = !!(currentSettings && currentSettings.always_on_top);
  shortcutBtn.textContent = (currentSettings && currentSettings.shortcut) || "CommandOrControl+Shift+T";
  const speed = (currentSettings && currentSettings.speech_speed) || 0.9;
  speechSpeedInput.value = speed;
  speechSpeedValue.textContent = `${Number(speed).toFixed(2)}x`;

  await applyProviderToUI(providerSelect.value);
}

providerSelect.addEventListener("change", () => {
  applyProviderToUI(providerSelect.value);
});

document.getElementById("open_settings_btn").addEventListener("click", openSettings);
document.getElementById("back_btn").addEventListener("click", () => showView("home"));

const captureBtn = document.getElementById("capture_btn");
const captureStatus = document.getElementById("capture_status");

captureBtn.addEventListener("click", async () => {
  setStatus(captureStatus, "", "");
  try {
    await invoke("begin_capture");
  } catch (e) {
    setStatus(captureStatus, friendlyError(e), "error");
  }
});

window.__TAURI__.event.listen("capture-started", () => {
  const empty = list.querySelector(".card.empty:only-child");
  if (empty) empty.remove();
  list.insertAdjacentHTML(
    "afterbegin",
    `<div class="card empty" id="pending-capture">⏳ Processando captura...</div>`
  );
});

window.__TAURI__.event.listen("capture-error", (event) => {
  setStatus(captureStatus, friendlyError(event.payload), "error");
  refreshHome();
});

window.__TAURI__.event.listen("capture-done", () => {
  refreshHome();
});

document.querySelectorAll(".tab").forEach((tabBtn) => {
  tabBtn.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".tab-panel").forEach((p) => (p.style.display = "none"));
    tabBtn.classList.add("active");
    document.getElementById(`tab-${tabBtn.dataset.tab}`).style.display = "";
  });
});

testBtn.addEventListener("click", async () => {
  setStatus(testStatus, "Conectando...", "");
  testBtn.disabled = true;
  try {
    const provider = providerSelect.value;
    const models = await invoke("list_models", {
      baseUrl: baseUrlInput.value.trim(),
      apiKey: apiKeyInput.value.trim(),
      provider,
    });
    fetchedModelsByProvider[provider] = models;
    const previous = fieldsForProvider(provider, currentSettings).model;
    fillModelSelect(models, previous);
    setStatus(testStatus, `Conectado! ${models.length} modelos encontrados.`, "ok");
  } catch (e) {
    setStatus(testStatus, friendlyError(e), "error");
  } finally {
    testBtn.disabled = false;
  }
});

saveBtn.addEventListener("click", async () => {
  setStatus(saveStatus, "Salvando...", "");
  try {
    const provider = providerSelect.value;
    await invoke("save_api_key", { provider, apiKey: apiKeyInput.value.trim() });
    const current = await invoke("load_settings").catch(() => ({}));
    const updated = {
      ...current,
      provider,
      always_on_top: alwaysOnTopInput.checked,
    };
    const fields = PROVIDER_FIELDS[provider] || PROVIDER_FIELDS.openai;
    updated[fields.baseUrl] = baseUrlInput.value.trim();
    updated[fields.model] = modelInput.value.trim();
    await invoke("save_settings", { settings: updated });
    currentSettings = updated;
    setStatus(saveStatus, "Configurações salvas.", "ok");
    setTimeout(() => showView("home"), 500);
  } catch (e) {
    setStatus(saveStatus, friendlyError(e), "error");
  }
});

speedTestBtn.addEventListener("click", async () => {
  setStatus(speedStatus, "Enviando frase de teste em inglês...", "");
  speedTestBtn.disabled = true;
  try {
    const seconds = await invoke("test_model_speed", {
      baseUrl: baseUrlInput.value.trim(),
      apiKey: apiKeyInput.value.trim(),
      provider: providerSelect.value,
      model: modelInput.value.trim(),
    });
    setStatus(speedStatus, `Respondeu em ${seconds.toFixed(2)}s`, "ok");
  } catch (e) {
    setStatus(speedStatus, friendlyError(e), "error");
  } finally {
    speedTestBtn.disabled = false;
  }
});

const BENCH_LABEL = "🏁 Rodar benchmark";
const BENCH_EXTRA_CALLS = 16; // 8 finalistas × 2 rodadas extras (BENCH_FINALISTS/BENCH_EXTRA_RUNS no lib.rs)
let benchArmed = false;

benchBtn.addEventListener("click", async () => {
  const provider = providerSelect.value;
  const models = (fetchedModelsByProvider[provider] || []).map((m) => m.id);
  if (models.length === 0) {
    setStatus(benchStatus, "Clique em “Testar conexão” primeiro, pra listar os modelos.", "error");
    return;
  }
  // Dispara dezenas de chamadas pagas: o primeiro clique só arma, o segundo confirma.
  if (!benchArmed) {
    benchArmed = true;
    benchBtn.textContent = `Confirmar: testar ${models.length} modelos (~${models.length + BENCH_EXTRA_CALLS} chamadas)`;
    setTimeout(() => {
      if (benchArmed) {
        benchArmed = false;
        benchBtn.textContent = BENCH_LABEL;
      }
    }, 6000);
    return;
  }
  benchArmed = false;
  benchBtn.disabled = true;
  benchBtn.textContent = "⏳ Rodando…";
  benchResults.innerHTML = "";
  setStatus(benchStatus, "Começando…", "");
  const unlisten = await window.__TAURI__.event.listen("benchmark-progress", (e) => {
    const p = e.payload;
    setStatus(benchStatus, `${p.done}/${p.total} chamadas · ${p.model} ${p.ok ? "✓" : "✗"}`, "");
  });
  try {
    const entries = await invoke("benchmark_models", {
      baseUrl: baseUrlInput.value.trim(),
      apiKey: apiKeyInput.value.trim(),
      provider,
      models,
    });
    currentSettings = await invoke("load_settings").catch(() => currentSettings);
    const favCount = entries.filter((e) => e.favorite).length;
    const failCount = entries.filter((e) => e.secs == null).length;
    setStatus(
      benchStatus,
      favCount
        ? `Pronto! ${favCount} favoritos atualizados. ${failCount} modelos não responderam direito.`
        : "Nenhum modelo respondeu de forma confiável — favoritos mantidos.",
      favCount ? "ok" : "error"
    );
    benchResults.innerHTML = entries
      .map((e) => {
        if (e.secs == null) return `<li class="fail">${esc(e.model)} — ✗ ${esc(friendlyError(e.error))}</li>`;
        const runs = e.runs.length > 1 ? ` <span class="muted">(mediana de ${e.runs.length})</span>` : "";
        const flaky = e.error ? ` <span class="fail">· falhou em alguma rodada</span>` : "";
        return `<li>${e.favorite ? "★ " : ""}<b>${esc(e.model)}</b> — ${e.secs.toFixed(2)}s${runs}${flaky}</li>`;
      })
      .join("");
    const cached = fetchedModelsByProvider[provider];
    if (cached) fillModelSelect(cached, modelInput.value.trim());
  } catch (err) {
    setStatus(benchStatus, friendlyError(err), "error");
  } finally {
    unlisten();
    benchBtn.disabled = false;
    benchBtn.textContent = BENCH_LABEL;
  }
});

showView("home");
