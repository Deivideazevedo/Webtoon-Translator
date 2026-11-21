# Webtoon Translator

App gratuito para quem quer **ler webtoons e quadrinhos em inglês e aprender no caminho**.
Selecione um balão na tela e em ~2 segundos aparece a tradução, a pronúncia e a explicação
das expressões — com áudio em inglês.

- **Tradução natural** em português do Brasil.
- **Explicação do que não é literal**: phrasal verbs, gírias e expressões
  (ex.: *go down* é "descer/cair", mas *go down without a fight* é "não se render sem lutar").
- **Pronúncia** escrita do jeito que se lê em português (ou em IPA, se preferir) e **áudio** em inglês,
  normal ou lento.
- **Passe o mouse** sobre qualquer palavra em inglês para ver a tradução literal dela.
- **Recentes e favoritos**, com o áudio dos favoritos salvo para ouvir depois.

## Como funciona e o que fica no seu computador

| Etapa | Onde acontece |
|---|---|
| Leitura do texto da imagem (OCR) | No seu computador |
| Voz em inglês | No seu computador |
| Tradução e explicações | No serviço de IA que você escolher (só o **texto** é enviado, nunca a imagem) |

O app não tem servidor próprio, não coleta dados e não tem anúncios. Você usa a sua própria chave de API.

## Instalação

Baixe a versão mais recente na página [Releases](../../releases).

**Linux (Manjaro, Arch, Ubuntu, Fedora...)** — baixe o `.AppImage`, dê permissão de execução e abra:

```bash
chmod +x Webtoon.Translator_*.AppImage
./Webtoon.Translator_*.AppImage
```

Se não abrir, instale o FUSE 2 (`sudo pacman -S fuse2` no Manjaro/Arch, `sudo apt install libfuse2` no Ubuntu).
Também há pacotes `.deb` (Debian/Ubuntu) e `.rpm` (Fedora).

**Windows** — baixe e rode o instalador `.exe` (ou o `.msi`). Como o app não tem assinatura digital paga,
o Windows pode mostrar "O Windows protegeu o computador": clique em **Mais informações → Executar assim mesmo**.

A voz em inglês é opcional e baixada dentro do app (veja abaixo), então o instalador é leve.

## Primeira configuração (2 minutos)

1. Abra o app e clique em **⚙ Configurações → Conexão**.
2. Escolha um provedor e cole a chave de API:
   - **Google AI Studio (recomendado para começar — tem plano gratuito):** crie a chave em
     https://aistudio.google.com/apikey
   - **Anthropic (Claude):** chave em https://console.anthropic.com
   - **Qualquer gateway compatível com OpenAI** (OpenRouter, um servidor local, etc.): informe a URL base
     (para o OpenRouter, `https://openrouter.ai/api`) e a chave.
3. Clique em **Testar conexão**, escolha um modelo na lista e **Salvar**.
4. Para ouvir a pronúncia: **Configurações → Voz → Baixar voz** (~305 MB, uma vez só). A voz roda no seu
   computador, sem internet depois de instalada. Sem ela o app traduz normalmente, só não fala.
5. Opcional: **🏁 Rodar benchmark** mede todos os modelos com uma tradução de verdade e marca os 5 mais
   rápidos como favoritos. Cada modelo testado conta como uma chamada à API.

Dica: modelos "flash"/"mini"/"haiku" são os mais rápidos para esse uso.

## Como usar

- Clique em **🔲 Capturar área da tela** ou use o atalho **Ctrl+Shift+T** (dá para mudar em Configurações → Atalhos).
- Arraste sobre o balão. A tradução aparece num balão flutuante, que você pode arrastar.
- **📖 Aprender** abre os trechos explicados e a gramática. **Esc** fecha o balão.
- Na janela principal, **☆** guarda a tradução nos Favoritos; marque as caixas para excluir várias de uma vez.
- **Configurações → Geral → Backup** exporta/importa tudo (configurações, chaves, favoritos e traduções).

## Onde ficam seus dados

| O quê | Linux | Windows |
|---|---|---|
| Configurações, traduções recentes e favoritas, áudios dos favoritos | `~/.config/com.deivide.webtoontranslator/` | `%APPDATA%\com.deivide.webtoontranslator\` |
| Voz baixada (~335 MB) | `~/.local/share/com.deivide.webtoontranslator/voices/` | `%LOCALAPPDATA%\com.deivide.webtoontranslator\voices\` |
| Chaves de API | Cofre de senhas do sistema (KWallet / GNOME Keyring) | Gerenciador de Credenciais do Windows |

As chaves só saem do cofre se você exportar um backup com a opção "Incluir chaves de API" marcada.

## Compilar a partir do código

Requisitos: [Rust](https://rustup.rs) e as dependências do Tauri para o seu sistema
(https://v2.tauri.app/start/prerequisites/). No Linux, a captura de tela também precisa dos pacotes de
desenvolvimento do PipeWire, Wayland e X11 (veja `.github/workflows/release.yml`).

```bash
cargo install tauri-cli --version "^2" --locked
cd src-tauri
cargo tauri build                  # gera os instaladores em src-tauri/target/release/bundle/
```

O núcleo (OCR, tradução, pronúncia, voz) fica em `core/` e é compartilhado com o app Android.
`./scripts/fetch-models.sh` baixa a voz para rodar os testes de voz (`cd core && cargo test --release`).

Para medir desempenho, rode com `WEBTOON_PERF_LOG=1`: cada etapa (OCR, API, voz) é registrada em `perf.log`
na pasta de configuração.

## Licença

O Webtoon Translator é software livre, sob a [GNU GPL v3 ou posterior](LICENSE): você pode usar, estudar,
modificar e redistribuir, desde que versões modificadas também sejam distribuídas com o código-fonte sob a GPL.

## Créditos

Voz [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx),
OCR [ocrs](https://github.com/robertknight/ocrs), pronúncia baseada no
[CMU Pronouncing Dictionary](https://github.com/cmusphinx/cmudict). Licenças em
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
