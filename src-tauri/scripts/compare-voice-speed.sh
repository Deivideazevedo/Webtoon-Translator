#!/usr/bin/env bash
# A/B da velocidade da voz: Kokoro gerando direto na velocidade configurada (nativo)
# vs Kokoro gerando em 1.0 e o player desacelerando (playbackRate), como o app faria.
set -euo pipefail

ROOT="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
BIN="$ROOT/target/release/examples/tts_bench"
MODEL="$ROOT/tts-models/kokoro-en-v0_19"
OUT="${TMPDIR:-/tmp}/webtoon-voice-speed"
SETTINGS="$HOME/.config/com.deivide.webtoontranslator/settings.json"
SPEED=$(grep -o '"speech_speed": *[0-9.]*' "$SETTINGS" 2>/dev/null | grep -o '[0-9.]*$' || echo 0.9)

PHRASES=(
  "What happened to"
  "And what happened to its other arm?"
  "You really hacked it to pieces. I didn't think you'd go that far, kid."
)

if [[ ! -x "$BIN" ]]; then
  echo "Compilando o benchmark (só na primeira vez)..."
  (cd "$ROOT" && cargo build --release --example tts_bench)
fi

rm -rf "$OUT"; mkdir -p "$OUT/nativo" "$OUT/rapido"
echo "Gerando áudios (nativo em $SPEED e em 1.0)... leva uns 30s"
"$BIN" "$MODEL" "$OUT/nativo" "$SPEED" "${PHRASES[@]}" | tee "$OUT/nativo.txt"
"$BIN" "$MODEL" "$OUT/rapido" 1.0 "${PHRASES[@]}" | tee "$OUT/rapido.txt"

# Tempo da 1ª geração de cada frase, pra mostrar na página.
tempos() { grep "1ª vez" "$1" | grep -oE '[0-9]+ ms' | tr -d ' ms' ; }
mapfile -t TN < <(tempos "$OUT/nativo.txt")
mapfile -t TR < <(tempos "$OUT/rapido.txt")

{
  cat <<EOF
<!doctype html><html lang="pt-br"><head><meta charset="utf-8"><style>
body{font-family:sans-serif;background:#16171d;color:#e6e6e6;padding:16px}
.f{border:1px solid #333;border-radius:10px;padding:12px;margin-bottom:12px}
button{font-size:14px;padding:8px 12px;margin:6px 8px 0 0;border-radius:8px;border:1px solid #555;background:#23252d;color:#eee;cursor:pointer}
small{color:#999}
</style></head><body>
<h3>Velocidade configurada: ${SPEED}x</h3>
<p><small><b>A</b> = Kokoro já gera devagar (como é hoje). <b>B</b> = Kokoro gera em 1.0 (mais rápido de sintetizar) e o player desacelera pra ${SPEED}x mantendo o tom.</small></p>
EOF
  for i in "${!PHRASES[@]}"; do
    n=$((i + 1))
    cat <<EOF
<div class="f"><b>Frase $n:</b> ${PHRASES[$i]}<br>
<button onclick="tocar('nativo/frase$n.wav',1)">▶ A — nativo ${SPEED}x</button><small>gerou em ${TN[$i]} ms</small><br>
<button onclick="tocar('rapido/frase$n.wav',${SPEED})">▶ B — 1.0 + playbackRate ${SPEED}</button><small>gerou em ${TR[$i]} ms</small>
</div>
EOF
  done
  cat <<'EOF'
<script>
const p = new Audio();
function tocar(src, rate) { p.pause(); p.src = src; p.preservesPitch = true; p.defaultPlaybackRate = rate; p.playbackRate = rate; p.play(); }
</script></body></html>
EOF
} > "$OUT/index.html"

python3 "$ROOT/scripts/voice-speed-ab.py" "$OUT/index.html"
