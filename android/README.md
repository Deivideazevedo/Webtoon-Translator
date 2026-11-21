# Webtoon Translator — Android

App Android nativo (Kotlin + Jetpack Compose) que usa o **mesmo núcleo Rust do desktop** (`../core`)
pra OCR, tradução com streaming e pronúncia. Android 10+ (API 29), arm64.

## Compilar e instalar

Pré-requisitos: Android SDK (com NDK), Rust com o alvo `aarch64-linux-android` e
[`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk).

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk

# Na raiz do repositório:
make apk        # gera o APK (android/app/build/outputs/apk/debug/app-debug.apk)
make android    # gera, instala no celular conectado pelo adb e abre o app
```

O `make` já aponta o `JAVA_HOME` pro JDK do Android Studio (o Java do sistema costuma ser só o
JRE); dá pra trocar com `make apk JAVA_HOME=/outro/jdk`. Com mais de um celular conectado:
`make android ADB_SERIAL=<id do adb devices>`.

O Gradle roda o `build-rust.sh` antes de cada build: ele compila a ponte Rust pra `.so` e gera os
bindings Kotlin (UniFFI) em `app/build/rust/`. Os modelos do OCR e o dicionário de pronúncia são
copiados de `../src-tauri` pros assets do APK.

## Arquitetura

Camadas no estilo recomendado pelo Google (*Guide to app architecture*): a interface só conversa com
ViewModels, que só conversam com repositórios. Nenhuma tela sabe que existe Rust por baixo.

```
rust-bridge/                    Ponte Rust → Kotlin (UniFFI): expõe OCR, tradução, pronúncia
app/src/main/java/com/deivide/webtoontranslator/
├── WebtoonApp.kt               Application: cria o AppContainer
├── MainActivity.kt             Única Activity: tema + navegação
├── di/
│   └── AppContainer.kt         Injeção de dependências manual (uma instância de cada serviço)
├── domain/model/               Tipos do app (Translation, Segment, TranslationEvent…), sem Android
├── data/                       De onde vêm os dados
│   ├── engine/                 EngineProvider: instala os modelos e cria o motor Rust (lazy)
│   ├── translation/            TranslationRepository: OCR + tradução em streaming (Flow)
│   ├── settings/               SettingsRepository + SecretCipher (chave de API no Keystore)
│   ├── image/                  PageLoader + Page: abre a imagem e fatia tiras longas
│   └── speech/                 SpeechPlayer: voz do próprio Android
└── ui/
    ├── theme/                  Cores e tema (mesma paleta do desktop)
    ├── navigation/             AppNavHost: rotas das telas
    ├── common/                 UiText (texto de ViewModel sem Context) e mensagens de erro
    ├── components/             Componentes reaproveitados entre telas (AppTopBar, LoadingRow)
    ├── home/                   Tela inicial
    │   ├── HomeScreen.kt       Liga a tela ao ViewModel; desenho em HomeContent
    │   ├── HomeViewModel.kt    Estado + ações (abrir imagem, traduzir área, falar)
    │   ├── HomeUiState.kt      Estado da tela (imutável)
    │   └── components/         Partes da tela (PageView, ResultCard, EmptyState)
    └── settings/               Configurações (mesma divisão: Screen, ViewModel, UiState, components)
```

### Convenções

- **Estado de tela**: cada ViewModel expõe um único `StateFlow<XUiState>` imutável; a tela lê com
  `collectAsStateWithLifecycle()` e chama funções do ViewModel (fluxo de dados em uma direção).
- **Telas sem lógica**: o `XScreen` só conecta o ViewModel; o desenho fica em composables que recebem
  estado e callbacks, fáceis de testar e de visualizar em preview.
- **Textos**: todos em `res/values/strings.xml`. ViewModels devolvem `UiText` (recurso ou texto pronto),
  nunca `String` montada com `Context`.
- **Dependências**: criadas no `AppContainer`; cada ViewModel recebe só o que usa pela sua `Factory`.
- **Tipos do Rust** (`uniffi.webtoon_android.*`) ficam presos à camada `data`; o resto do app usa os
  tipos de `domain.model`.
- **Threads**: trabalho pesado (OCR, decodificar imagem, copiar modelos) roda em `Dispatchers.Default/IO`
  dentro dos repositórios; ViewModels e telas nunca bloqueiam a thread principal.

O padrão visual (cores, ícones, cartões, telas) está no [`LAYOUT.md`](LAYOUT.md); o que falta
fazer, no [`ROADMAP.md`](../ROADMAP.md) da raiz.
