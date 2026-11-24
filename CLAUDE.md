# Webtoon Translator — guia pro Claude

Traduz webtoons/textos em inglês com IA, com foco didático (pronúncia, trechos, gramática).
Grátis, GPL-3.0-or-later. Repo: `github.com/Deivideazevedo/Webtoon-Translator`.

## Como trabalhar com o usuário (Deivide)

- **Responder sempre em pt-BR**, curto e direto. Quando ele precisar agir, explicar passo a passo.
- **Commit/push/tag só quando ele pedir**, e só do que foi pedido naquela vez (a autorização não
  vale pro trabalho seguinte). Nunca force-push nem reescrever histórico já enviado; pra desfazer,
  oferecer `git revert`.
- **Antes de commitar, conferir que não vai chave de API** (`git diff --cached | grep` por chaves).
  Não sair procurando segredos; nunca lidar com senhas do keystore (ficam nos secrets do GitHub).
- **Não mexer em código existente fora do escopo sem um "sim" explícito**, principalmente o
  streaming e o `SYSTEM_PROMPT` de `core/src/translate.rs`. **O prompt é sagrado**: mudança mínima,
  só com aprovação, e ele não pode crescer. Preferir adicionar código ao lado do existente.
- **Fazer exatamente o que foi pedido.** Se ele aprovar parte de uma proposta com vários itens,
  fazer tudo junto ou perguntar antes (nunca só o pedaço citado).
- **Investigar com logs, não deduzir**: na dúvida sobre a causa, pôr log (versão dev) e pedir pra
  ele reproduzir.
- **Celular via adb**: pode ler logcat, screenshots (`/sdcard/DCIM/Screenshots`) e instalar APK.
  Não controlar a tela do celular dele. Às vezes ele pede "só instalar, sem abrir".
- Ele testa a UI sozinho; não montar harness visual. Desktop: sem janelas de SO extras pra telas
  simples (trocar a view na mesma janela, com "← Voltar").

## Estrutura

- `core/` — núcleo Rust compartilhado (sem Tauri): tradução (OpenAI-compat/omniroute, Anthropic,
  Google, com streaming), `SYSTEM_PROMPT`, benchmark de modelos (`benchmark.rs`), pronúncia.
- `src-tauri/` + `src/` — desktop Tauri v2 (frontend HTML/CSS/JS puro, sem npm/bundler). OCR local
  (ocrs), TTS Kokoro (sherpa-onnx; `scripts/fetch-models.sh` baixa o modelo pra dev).
- `android/` — Kotlin + Jetpack Compose, minSdk 29. Núcleo via UniFFI (`android/rust-bridge`).
  OCR = ML Kit. Tradutor flutuante em `floating/` (`FloatingService`, `ComposeOverlay`,
  cartão `ui/translation/TranslationCard.kt`). Layout documentado em `android/LAYOUT.md`.
- `ROADMAP.md` — backlog (Android e Desktop, com ✅/⬜).

## Comandos

`make` lista tudo. No Windows o `make` não existe nativo: usar Git Bash/WSL ou os equivalentes:

- `make dev` → `cd src-tauri && cargo tauri dev`
- `make test` → `cargo test --release --lib` em `core/` e `src-tauri/`
- `make apk` → `cd android && ./gradlew assembleDebug` (no Windows: `gradlew.bat assembleDebug`).
  APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Precisa do JDK do Android Studio
  (`JAVA_HOME`), Android SDK/NDK e Rust com os targets do Android.
- `make android` → apk + `adb install -r` + abre o app.
- Celular do usuário: `adb connect 192.168.1.13:5555` (IP pode mudar).

**Versão dev**: o build debug é outro app, `com.deivide.webtoontranslator.dev`
("Webtoon Translator (dev)"), ao lado do oficial. Só nele: logs com tag `WT-Dev`
(`adb logcat -s WT-Dev`; OCR linha a linha com confiança e motivo do descarte) e a última
captura do OCR em `/sdcard/Android/data/com.deivide.webtoontranslator.dev/files/debug/ocr.png`.

## Release

1. Subir a versão: `android/app/build.gradle.kts` (`versionCode` +1 e `versionName`),
   `src-tauri/Cargo.toml`, `src-tauri/tauri.conf.json` e o pacote `webtoon-translator` no
   `src-tauri/Cargo.lock`.
2. Commit `feat: vX.Y.Z — …` (lista do que mudou), tag `vX.Y.Z`, push do main e da tag
   (tudo só com o pedido dele).
3. A tag dispara `.github/workflows/release.yml`: Linux + Windows + APK assinado (secrets).
   O app Android se atualiza pelo próprio app (`AppUpdater`).

## Estado (24/11/2026, v0.2.2)

Feito recentemente (Android): aviso de fonte estilizada com "Toque aqui para digitar o texto";
✏️ no cartão pra editar o inglês (folha presa embaixo, sobe com o teclado via janela-sonda de
insets); rodapé do modelo também nas falhas; captura de tela inteira ou de um app só (Android 14+);
região fixa automática abre/fecha o editor pelo flutuante; favoritos ordenados por velocidade;
benchmark com timeout de 15s.

Pendências / ideias:
- Desktop: mesma ordenação dos favoritos por velocidade; D10–D12 do `ROADMAP.md`
  (modos automáticos, rodapé que troca o modelo, OCR melhor no Windows/Linux).
- Benchmark: talvez marcar modelos que falharam numa das rodadas extras (hoje só ficam fora dos
  favoritos, o que confunde: favoritos podem sair 1,3,6,7,8 em vez dos 5 mais rápidos).
- O filtro de confiança do OCR (0.45) foi mantido de propósito; não mudar sem pedido.
