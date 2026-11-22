# Componentes de terceiros

O Webtoon Translator inclui os modelos e dados abaixo. Cada um continua sob a própria licença.

## Voz: Kokoro-82M (opcional, baixada pelo app)

- Origem: https://huggingface.co/hexgrad/Kokoro-82M, empacotado pelo projeto sherpa-onnx
  (https://github.com/k2-fsa/sherpa-onnx). Não vem no instalador: o app baixa o pacote oficial do
  sherpa-onnx quando o usuário escolhe "Baixar voz" (Configurações → Voz).
- Licença: Apache License 2.0 — o texto completo vem junto com a voz baixada (`voices/kokoro-en-v0_19/LICENSE`).

## Voz: Piper libritts_r medium (opcional, baixada pelo app)

- Origem: modelo Piper (https://github.com/rhasspy/piper) convertido pelo sherpa-onnx
  (`vits-piper-en_US-libritts_r-medium`). Não vem no instalador: o app baixa quando o usuário escolhe.
- Dados de treino: LibriTTS-R (http://www.openslr.org/141/), licença CC BY 4.0. Segundo o cartão do modelo
  (`MODEL_CARD`, baixado junto), foi ajustado a partir da voz Piper "lessac medium".

## Motor de voz: sherpa-onnx

- Origem: https://github.com/k2-fsa/sherpa-onnx
- Licença: Apache License 2.0.

## Dicionário de pronúncia (`tts-models/lexicon-en-us.txt`)

Distribuído pelo sherpa-onnx (Apache License 2.0), gerado a partir do CMU Pronouncing Dictionary
(https://github.com/cmusphinx/cmudict), cuja licença é reproduzida abaixo:

```
Copyright (C) 1993-2015 Carnegie Mellon University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
   The contents of this file are deemed to be source code.

2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in
   the documentation and/or other materials provided with the
   distribution.

This work was supported in part by funding from the Defense Advanced
Research Projects Agency, the Office of Naval Research and the National
Science Foundation of the United States of America, and by member
companies of the Carnegie Mellon Sphinx Speech Consortium. We acknowledge
the contributions of many volunteers to the expansion and improvement of
this dictionary.

THIS SOFTWARE IS PROVIDED BY CARNEGIE MELLON UNIVERSITY ``AS IS'' AND
ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## OCR: modelos do ocrs (`ocr-models/`)

- Origem: https://github.com/robertknight/ocrs-models (modelos em https://huggingface.co/robertknight/ocrs),
  treinados no conjunto de dados HierText.
- Licença: Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0) —
  https://creativecommons.org/licenses/by-sa/4.0/

## OCR no Android: Google ML Kit Text Recognition

- Biblioteca do Google (`com.google.mlkit:text-recognition`), com o modelo embutido no app; lê o texto da
  tela no próprio aparelho, sem enviar imagens pra fora.
- Licença: não é software livre; uso sob os termos do Google (ML Kit Terms of Service:
  https://developers.google.com/ml-kit/terms). A distribuição junto com o app é autorizada pela permissão
  adicional no fim do [LICENSE](LICENSE).
- O ocrs (acima) continua no app Android como reserva, caso o ML Kit falhe.

## Bibliotecas

O app também usa bibliotecas Rust de código aberto (Tauri, ocrs, rten, reqwest, entre outras), sob licenças
MIT e/ou Apache 2.0. A lista completa está em `src-tauri/Cargo.lock`.

O app Android usa ainda Jetpack Compose e AndroidX (Apache 2.0), os ícones Material Symbols do Google
(Apache 2.0), JNA (Apache 2.0) e UniFFI (MPL 2.0); a lista completa está em
`android/gradle/libs.versions.toml` e `android/rust-bridge/Cargo.lock`.
