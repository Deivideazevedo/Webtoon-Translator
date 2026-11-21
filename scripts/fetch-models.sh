#!/usr/bin/env bash
# Só pra desenvolvimento (testes de voz e scripts de comparação): o app em si baixa a voz pela
# aba Configurações → Voz. Coloca o Kokoro em src-tauri/tts-models/, fora do pacote e do git.
set -euo pipefail

DEST="$(cd "$(dirname "$0")/.." && pwd)/src-tauri/tts-models"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2"

if [[ -f "$DEST/kokoro-en-v0_19/model.onnx" ]]; then
  echo "Modelo de voz já está em $DEST/kokoro-en-v0_19"
  exit 0
fi

echo "Baixando o modelo de voz (Kokoro, ~305MB compactado)..."
mkdir -p "$DEST"
curl -fL --retry 3 "$URL" | tar xj -C "$DEST"
echo "Pronto: $DEST/kokoro-en-v0_19"
