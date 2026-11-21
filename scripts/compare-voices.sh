#!/usr/bin/env bash
# Compara vozes do sherpa-onnx (as mesmas 3 frases em cada uma): tempo pra gerar, RAM,
# tamanho e licença, com botões pra ouvir lado a lado no mesmo motor web do app.
set -euo pipefail

ROOT="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/webtoon-voice-compare"
OUT="${TMPDIR:-/tmp}/webtoon-voice-compare"
BIN="$ROOT/src-tauri/target/release/examples/voice_compare"
export LEXICON="$ROOT/src-tauri/tts-models/lexicon-en-us.txt"
BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
SETTINGS="$HOME/.config/com.deivide.webtoontranslator/settings.json"
SPEED=$(grep -o '"speech_speed": *[0-9.]*' "$SETTINGS" 2>/dev/null | grep -o '[0-9.]*$' || echo 0.9)
KOKORO_DIR="$HOME/.local/share/com.deivide.webtoontranslator/voices/kokoro-en-v0_19"

PHRASES=(
  "What happened to"
  "And what happened to its other arm?"
  "You really hacked it to pieces. I didn't think you'd go that far, kid."
)

# nome | tipo | pasta (no cache) | sid | download | licença
VOICES=(
  "Kokoro (atual)|kokoro|@kokoro|0|305 MB|Apache-2.0"
  "Piper lessac (medium)|vits|vits-piper-en_US-lessac-medium|0|64 MB|Blizzard 2013 (dados de pesquisa) ⚠"
  "Piper ryan (high)|vits|vits-piper-en_US-ryan-high|0|110 MB|CC BY-NC-SA 4.0 (não comercial) ⚠"
  "Piper libritts_r (medium)|vits|vits-piper-en_US-libritts_r-medium|0|78 MB|CC BY 4.0"
  "Kitten nano v0.8|kitten|kitten-nano-en-v0_8-fp32|0|61 MB|Apache-2.0"
  "Kitten mini v0.8|kitten|kitten-mini-en-v0_8|0|64 MB|Apache-2.0"
  "Supertonic (2026)|supertonic|sherpa-onnx-supertonic-tts-int8-2026-03-06|6|81 MB|MIT"
  "Pocket TTS (2026)|pocket|sherpa-onnx-pocket-tts-2026-01-26|0|160 MB|CC BY 4.0 / ONNX não comercial ⚠"
)

if [[ ! -x "$BIN" ]]; then
  echo "Compilando o comparador (só na primeira vez)..."
  (cd "$ROOT/core" && cargo build --release --example voice_compare)
fi

mkdir -p "$CACHE"
rm -rf "$OUT"; mkdir -p "$OUT"
for v in "${VOICES[@]}"; do
  IFS='|' read -r name kind dir sid size license <<< "$v"
  if [[ "$dir" == "@kokoro" ]]; then
    path="$KOKORO_DIR"
    [[ -f "$path/model.onnx" ]] || { echo "Pulando $name: instale a voz pelo app (Configurações → Voz)."; continue; }
  else
    path="$CACHE/$dir"
    if [[ ! -d "$path" ]]; then
      echo "Baixando $name ($size)..."
      curl -fL --retry 3 "$BASE/$dir.tar.bz2" | tar xj -C "$CACHE"
    fi
  fi
  slug=$(echo "$dir" | tr -c 'a-zA-Z0-9_\n' '-')
  mkdir -p "$OUT/$slug"
  echo "== $name"
  "$BIN" "$kind" "$path" "$OUT/$slug" "$sid" "${PHRASES[@]}" 2>/dev/null | tee "$OUT/$slug/log.txt" | grep -v RESULT
  printf '%s|%s|%s|%s|%s\n' "$name" "$slug" "$size" "$license" \
    "$(grep RESULT "$OUT/$slug/log.txt" | sed 's/^RESULT //')" >> "$OUT/results.txt"
done

python3 - "$OUT" "$SPEED" "${PHRASES[@]}" <<'PYEOF'
import html, json, sys
out, speed, phrases = sys.argv[1], float(sys.argv[2]), sys.argv[3:]
rows = [l.rstrip("\n").split("|", 4) for l in open(f"{out}/results.txt")]
page = [f"""<!doctype html><html lang="pt-br"><head><meta charset="utf-8"><style>
body{{font-family:sans-serif;background:#16171d;color:#e6e6e6;padding:16px;font-size:14px}}
table{{border-collapse:collapse;width:100%;margin-bottom:18px}} td,th{{border-bottom:1px solid #333;padding:6px 8px;text-align:left;vertical-align:middle}}
th{{color:#9aa0b4;font-weight:normal;font-size:12px;text-transform:uppercase}}
button{{font-size:13px;padding:4px 10px;margin:2px;border-radius:6px;border:1px solid #555;background:#23252d;color:#eee;cursor:pointer}}
.fast{{color:#4fd18b}} .slow{{color:#ff6e6e}} small{{color:#9aa0b4}}
</style></head><body>
<h3>Comparativo de vozes</h3>
<p><small>Todas geradas em 1.0 no mesmo motor do app (4 threads). RTF = tempo pra gerar ÷ duração do áudio
(abaixo de 1 = mais rápido que tempo real). Tempos = melhor de 2 rodadas. ⚠ = licença com restrição.</small></p>
<label><input type="checkbox" id="appspeed" checked> tocar na velocidade do app ({speed:.2f}x)</label>
<table><tr><th>Voz</th><th>Download</th><th>Carregar</th><th>RAM</th><th>Frase 1 / 2 / 3 (geração)</th><th>RTF médio</th><th>Licença</th></tr>"""]
for name, slug, size, lic, res in rows:
    r = json.loads(res)
    rtf = sum(p["rtf"] for p in r["phrases"]) / len(r["phrases"])
    cls = "fast" if rtf < 0.5 else "slow" if rtf > 1 else ""
    times = " / ".join(f'{p["ms"]:.0f} ms' for p in r["phrases"])
    page.append(f'<tr><td><b>{html.escape(name)}</b></td><td>{size}</td><td>{r["load_ms"]/1000:.1f} s</td>'
                f'<td>{r["ram_mb"]:.0f} MB</td><td>{times}</td><td class="{cls}">{rtf:.2f}</td><td><small>{html.escape(lic)}</small></td></tr>')
page.append("</table>")
for i, ph in enumerate(phrases, 1):
    page.append(f'<p><b>Frase {i}:</b> {html.escape(ph)}<br>')
    for name, slug, *_ in rows:
        page.append(f'<button onclick="tocar(\'{slug}/frase{i}.wav\')">▶ {html.escape(name)}</button>')
    page.append("</p>")
page.append(f"""<script>
const p = new Audio();
function tocar(src) {{ const r = document.getElementById('appspeed').checked ? {speed} : 1;
  p.pause(); p.src = src; p.preservesPitch = true; p.defaultPlaybackRate = r; p.playbackRate = r; p.play(); }}
</script></body></html>""")
open(f"{out}/index.html", "w").write("\n".join(page))
PYEOF

echo; echo "Abrindo o comparativo..."
[[ -n "${NO_VIEW:-}" ]] || python3 "$ROOT/src-tauri/scripts/voice-speed-ab.py" "$OUT/index.html" "Comparativo de vozes"
