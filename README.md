# Webtoon Translator

App gratuito para quem quer **ler webtoons e quadrinhos em inglês e aprender no caminho**, no **celular
Android** e no **computador** (Linux e Windows). A tradução aparece em cima do próprio app de leitura, com a
pronúncia e a explicação das expressões, e com áudio em inglês.

- **Tradução natural** em português do Brasil.
- **Explicação do que não é literal**: phrasal verbs, gírias e expressões
  (ex.: *go down* é "descer/cair", mas *go down without a fight* é "não se render sem lutar").
- **Pronúncia** escrita do jeito que se lê em português (ou em IPA, se preferir) e **áudio** em inglês,
  normal ou bem devagar (🐢).
- **Toque** (no celular) ou **passe o mouse** (no computador) numa palavra em inglês para ver a tradução literal dela.
- **Recentes e favoritos**, e backup no mesmo formato nos dois: exporte no PC e importe no celular, e vice-versa.

## Como funciona e o que fica no seu aparelho

| Etapa | Onde acontece |
|---|---|
| Leitura do texto da tela (OCR) | No seu aparelho (no Android, pelo OCR do Google, que roda no próprio celular) |
| Voz em inglês | No seu aparelho |
| Tradução e explicações | No serviço de IA que você escolher (só o **texto** é enviado, nunca a imagem) |

O app não tem servidor próprio, não coleta dados e não tem anúncios. Você usa a sua própria chave de API (ou o
seu próprio servidor, como o OmniRoute).

## Android

### Instalação

1. No celular, abra a página [Releases](../../releases) e baixe o arquivo `WebtoonTranslator-vX.Y.Z.apk` da
   versão mais recente.
2. Abra o arquivo baixado. Na primeira vez, o Android pede para **permitir instalar apps desta fonte**
   (o navegador ou o gerenciador de arquivos): permita e toque em **Instalar**.

Requer Android 10 ou mais novo, em celular de 64 bits (praticamente todos os atuais). O app tem ~26 MB.

**Atualizações:** o próprio app avisa quando sai uma versão nova (aviso na tela Inicial, ou em
**Mais → Sobre → Procurar atualizações**): ele baixa e abre o instalador, e você só confirma. Instala por cima,
sem perder nada.

### Primeira configuração

Na primeira abertura, um tutorial curto mostra o caminho. Em resumo:

1. **Mais → Conexão:** escolha o provedor e cole a chave de API (ou o endereço do seu servidor). Os provedores são
   os mesmos do computador (veja [Primeira configuração](#primeira-configuração-2-minutos) abaixo).
2. Na tela **Inicial**, escolha o **Modelo de IA**. Em **Mais → Conexão**, o **benchmark** mostra qual responde
   mais rápido.
3. Toque no botão **⏻** da tela Inicial e permita **aparecer sobre outros apps** e **capturar a tela**. Aparece uma
   bolinha flutuante por cima do app de leitura.

**Servidor em casa (ex.: OmniRoute) fora de casa:** use o [Tailscale](https://tailscale.com) e configure o
endereço com o IP do Tailscale (`http://100.x.x.x:porta`). O túnel já é criptografado, então não precisa de HTTPS.

### Como usar

- **Modos de tradução** (tela Inicial, ou segure a bolinha):
  - **Região:** toque na bolinha e arraste a setinha sobre o balão.
  - **Região fixa:** marque a área uma vez; cada toque na bolinha traduz de novo.
  - **Tela cheia:** um toque traduz todos os balões da tela; tocar numa tradução abre a explicação completa.
  - **Região automática** e **Tela cheia automática:** traduzem sozinhos quando o texto muda ou quando você para
    de rolar a página, sem tocar em nada.
- **No cartão da tradução:** 🔊 lê em inglês e 🐢 lê devagar; toque numa palavra pontilhada para ver a tradução
  dela; 📖 abre os trechos explicados e a gramática; o rodapé do modelo troca a IA e traduz de novo; ⚙ tem o modo,
  a velocidade da voz e a opção de o **Voltar** fechar as traduções.
- **Traduções:** recentes e favoritas (★), e um campo para **digitar ou colar** qualquer frase em inglês.
- **Atalhos:** um **widget** para a tela inicial e um botão no **painel rápido** (puxando a barra de cima; adicione
  pelo lápis ✏️) ligam e desligam o tradutor.
- **Mais:** tema claro/escuro, voz e velocidade, backup, armazenamento, tutorial e o registro de erros.

O Android pede a confirmação de **capturar a tela** toda vez que o tradutor liga; isso é uma regra do sistema.

### Onde ficam seus dados

Tudo fica dentro do app: traduções, configurações e as chaves de API (criptografadas). "Limpar dados" nas
configurações do sistema apaga tudo isso; faça um **backup** antes (Mais → Backup).

## Computador (Linux e Windows)

### Instalação

Baixe a versão mais recente na página [Releases](../../releases).

**Linux (Manjaro, Arch, Ubuntu, Fedora...)** — baixe o `.AppImage`, dê permissão de execução e abra:

```bash
chmod +x Webtoon.Translator_*.AppImage
./Webtoon.Translator_*.AppImage
```

Se não abrir, instale o FUSE 2 (`sudo pacman -S fuse2` no Manjaro/Arch, `sudo apt install libfuse2t64` no Ubuntu).
Também há pacotes `.deb` (Debian/Ubuntu/Mint) e `.rpm` (Fedora/openSUSE), que aparecem no menu de aplicativos e
desinstalam pelo gerenciador de pacotes.

Sistemas suportados: Ubuntu 24.04 ou mais novo, Linux Mint 22+, Pop!_OS 24.04+, Fedora 40+, Debian 13+, Manjaro e
Arch. Versões mais antigas (Ubuntu 22.04, Mint 21, Debian 12) não são suportadas: atualize o sistema, o que também
traz as correções de segurança mais recentes.

**Windows** — baixe e rode o instalador `.exe` (ou o `.msi`). Como o app não tem assinatura digital paga,
o Windows pode mostrar "O Windows protegeu o computador": clique em **Mais informações → Executar assim mesmo**.

A voz em inglês é opcional e baixada dentro do app (veja abaixo), então o instalador é leve.

### Primeira configuração (2 minutos)

1. Abra o app e clique em **⚙ Configurações → Conexão**.
2. Escolha um provedor e cole a chave de API:
   - **Google AI Studio (recomendado para começar — tem plano gratuito):** crie a chave em
     https://aistudio.google.com/apikey
   - **Anthropic (Claude):** chave em https://console.anthropic.com
   - **Qualquer gateway compatível com OpenAI** (OpenRouter, um servidor local, etc.): informe a URL base
     (para o OpenRouter, `https://openrouter.ai/api`) e a chave.
3. Clique em **Testar conexão**, escolha um modelo na lista e **Salvar**.
4. Para ouvir a pronúncia: **Configurações → Voz** e baixe uma voz (uma vez só): **Natural** (Kokoro, ~305 MB,
   mais expressiva) ou **Rápida** (Piper, ~78 MB, gera o áudio ~15x mais rápido e usa metade da memória).
   Dá pra ter as duas e escolher. As vozes rodam no seu computador, sem internet depois de instaladas;
   sem nenhuma o app traduz normalmente, só não fala.
5. Opcional: **🏁 Rodar benchmark** mede todos os modelos com uma tradução de verdade e marca os 5 mais
   rápidos como favoritos. Cada modelo testado conta como uma chamada à API.

Dica: modelos "flash"/"mini"/"haiku" são os mais rápidos para esse uso.

### Como usar

- Clique em **🔲 Capturar área da tela** ou use o atalho **Ctrl+Shift+T** (dá para mudar em Configurações → Atalhos).
- Arraste sobre o balão. A tradução aparece num balão flutuante, que você pode arrastar.
- No topo do balão: **📋** copia, **★** guarda nos Favoritos e **📖** abre os trechos explicados e a gramática.
  **Esc** fecha o balão.
- Na janela principal, **☆** também guarda nos Favoritos; marque as caixas para excluir várias de uma vez.
- **Configurações → Geral → Backup** exporta/importa tudo (configurações, chaves, favoritos e traduções).

### Onde ficam seus dados

| O quê | Linux | Windows |
|---|---|---|
| Configurações, traduções recentes e favoritas, áudios dos favoritos | `~/.config/com.deivide.webtoontranslator/` | `%APPDATA%\com.deivide.webtoontranslator\` |
| Vozes baixadas (Kokoro ~335 MB, Piper ~75 MB) | `~/.local/share/com.deivide.webtoontranslator/voices/` | `%LOCALAPPDATA%\com.deivide.webtoontranslator\voices\` |
| Chaves de API | Cofre de senhas do sistema (KWallet / GNOME Keyring) | Gerenciador de Credenciais do Windows |

As chaves só saem do cofre se você exportar um backup com a opção "Incluir chaves de API" marcada.

## Compilar a partir do código

**Computador.** Requisitos: [Rust](https://rustup.rs) e as dependências do Tauri para o seu sistema
(https://v2.tauri.app/start/prerequisites/). No Linux, a captura de tela também precisa dos pacotes de
desenvolvimento do PipeWire, Wayland e X11 (veja `.github/workflows/release.yml`).

```bash
npx @tauri-apps/cli@2 build        # gera os instaladores em src-tauri/target/release/bundle/
```

**Android.** Requisitos: Android SDK com NDK, JDK 17+, Rust com o alvo `aarch64-linux-android` e o
[`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk). Com o celular conectado pelo `adb`:

```bash
make android                       # compila, instala no celular e abre o app (make apk só gera o APK)
```

O núcleo (tradução, pronúncia, voz, OCR do computador) fica em `core/` e é compartilhado entre o desktop e o
app Android (`android/`, em Kotlin, ligado ao núcleo pelo UniFFI).
`./scripts/fetch-models.sh` baixa a voz para rodar os testes de voz (`cd core && cargo test --release`).

Para medir desempenho, rode com `WEBTOON_PERF_LOG=1`: cada etapa (OCR, API, voz) é registrada em `perf.log`
na pasta de configuração.

## Licença

O Webtoon Translator é software livre, sob a [GNU GPL v3 ou posterior](LICENSE): você pode usar, estudar,
modificar e redistribuir, desde que versões modificadas também sejam distribuídas com o código-fonte sob a GPL.

A licença tem uma permissão adicional (seção 7 da GPL, no fim do [LICENSE](LICENSE)) que autoriza distribuir o
app junto com o Google ML Kit, usado no Android pra ler o texto da tela. O ML Kit é do Google e tem licença
própria; todo o resto continua sob a GPL.

## Créditos

Voz [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx),
OCR [ocrs](https://github.com/robertknight/ocrs) (desktop) e [Google ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) (Android), pronúncia baseada no
[CMU Pronouncing Dictionary](https://github.com/cmusphinx/cmudict). Licenças em
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
