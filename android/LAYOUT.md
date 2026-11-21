# Layout do app Android

Padrão visual do app, inspirado na organização do *Translate On Screen* (21/11/2026): mesma
disposição de conteúdo, cartões, ícones e cores neutras, com o **azul** do Webtoon Translator como
destaque. Não usamos nenhum arquivo deles (ícones, logo, telas são deles); os ícones são do
**Material Symbols** (Google, Apache 2.0), no mesmo estilo.

---

## 1. Estrutura

```
┌──────────────────────────────────┐
│ Webtoon Translator        (?)    │  ← barra do topo FIXA: nome do app + ações redondas
│                                  │
│  ┌────────────────────────────┐  │
│  │  conteúdo em CARTÕES       │  │  ← tudo dentro de cartões sobre o fundo neutro
│  └────────────────────────────┘  │
│  Seção                           │  ← título de seção solto, sobre o fundo
│  ┌────────────────────────────┐  │
│  │ [▣] Item              ›    │  │
│  │ ────────────────────────── │  │
│  │ [▣] Item          [ ●  ]   │  │
│  └────────────────────────────┘  │
├──────────────────────────────────┤
│   ⌂ Inicial   文 Traduções  ⋯ Mais │  ← navegação inferior FIXA (3 abas)
└──────────────────────────────────┘
```

- **Barra do topo**: nome do app à esquerda (negrito), à direita ações em **botões redondos**
  (48 dp, fundo levemente destacado). Não rola junto com o conteúdo.
- **Navegação inferior**: 3 abas — **Inicial**, **Traduções**, **Mais**. Aba ativa = ícone e texto
  no azul; inativas = cinza.
- **Fundo** neutro; **todo conteúdo em cartões** com cantos bem arredondados.
- **Seleções abrem bottom sheets** (painel que sobe de baixo), nunca menus suspensos.

---

## 2. Cores

Neutros medidos nos prints do Translate On Screen; o destaque é o nosso azul. O tema segue o do
sistema por padrão, com opção Automático / Claro / Escuro em **Mais**.

| Papel | Escuro | Claro |
|---|---|---|
| Fundo da tela e barra inferior | `#121212` | `#E8E8E8` (barra inferior `#FFFFFF`) |
| Cartão / bottom sheet | `#242424` | `#FFFFFF` |
| Campo, item de lista, botão secundário dentro do cartão | `#3A3A3A` | `#F2F2F2` |
| Divisor entre linhas | `#333333` | `#DFDFDF` |
| Texto principal | `#F2F2F2` | `#1C1C1C` |
| Texto secundário (legendas) | `#9AA3B2` | `#6B7280` |
| **Destaque (azul)** — botões, aba ativa, sliders, links, ★ | `#6EA8FE` | `#2D5BD4` |
| Texto sobre o azul | `#0D1B33` | `#FFFFFF` |
| Erro / ação destrutiva | `#FF8A80` | `#D93025` |

O azul é mais claro no escuro e mais fechado no claro, pra manter o contraste dos dois lados.

### Cores dos ícones (cada categoria tem a sua)

| Cor | Usada em |
|---|---|
| Azul `#4C7BEA` | modelo de IA, conexão, idioma |
| Roxo `#8E5BD9` | modo de tradução, aparência, tutorial |
| Verde `#2E9E6B` | acesso rápido, compartilhar, backup concluído |
| Laranja `#E8892B` | ícone flutuante, armazenamento |
| Azul-claro `#2BA3D9` | voz |
| Vermelho `#D9534F` | "app fechando sozinho", excluir |

---

## 3. Ícones

Todo ícone de linha/seletor fica num **quadrado de cantos arredondados** com o **fundo na cor do
ícone em transparência** e o **ícone na mesma cor, sólida**:

```
 ┌─────┐
 │ ▓▓▓ │  40 × 40 dp, cantos 10 dp
 │ ▓文▓ │  fundo: cor @ 14% (claro) / 20% (escuro)
 │ ▓▓▓ │  ícone: 22 dp, mesma cor @ 100%
 └─────┘
```

- **Redondo** (cantos 50%) no mesmo esquema pros botões soltos: ações da barra do topo, botão de
  trocar, ações dos cartões de tradução (copiar, ouvir, ★).
- Ícones só do **Material Symbols** (estilo *Rounded*), traço uniforme em todo o app.
- O ícone sempre tem destaque sobre o que está atrás (o fundo translúcido separa ele do cartão).

---

## 4. Formas, tamanhos e espaços

| Elemento | Valor |
|---|---|
| Margem da tela | 16 dp |
| Espaço entre cartões | 16 dp |
| Cartão: cantos / padding interno | 20 dp / 16–20 dp |
| Campo ou item dentro do cartão: cantos | 12 dp |
| Linha de lista (ícone + texto) | altura mín. 64 dp, divisor com recuo (começa depois do ícone) |
| Pílulas (status, botões de ação) | cantos 50% |
| Botões redondos | 48 dp de área de toque |
| Título da barra do topo | 22 sp, negrito |
| Título de seção (fora dos cartões) | 16 sp, semi-negrito |
| Título da linha / legenda | 17 sp médio / 13 sp texto secundário |
| Rótulo pequeno (ex.: "Modelo de IA" acima do valor) | 12 sp texto secundário |

---

## 5. Telas

### 5.1 Inicial

```
┌──────────────────────────────────┐
│ Webtoon Translator          (?)  │
│ ┌──────────────────────────────┐ │
│ │      ( Inglês ⇄ Português )  │ │  ← par de idiomas (fixo por enquanto, só exibição)
│ │                              │ │
│ │            ╭───╮             │ │
│ │           │ ⏻ │              │ │  ← botão grande com anel (cinza = parado, azul = ligado)
│ │            ╰───╯             │ │
│ │   ( ● Iniciar tradutor flutuante )  ← pílula de status
│ │ ┌──────────────────────────┐ │ │
│ │ │[文] Modelo de IA          │ │ │  ← abre bottom sheet com os modelos (★ no topo)
│ │ │     gemini-3-flash     ▾ │ │ │
│ │ └──────────────────────────┘ │ │
│ │ ┌──────────────────────────┐ │ │
│ │ │[◫] Modo de tradução       │ │ │  ← bottom sheet: Região / Região fixa
│ │ │     Região             ▾ │ │ │
│ │ └──────────────────────────┘ │ │
│ └──────────────────────────────┘ │
│ Última tradução                  │
│ ┌──────────────────────────────┐ │
│ │ Among the heroes…        🔊 ★ │ │  ← atalho pra última tradução (toque abre completa)
│ └──────────────────────────────┘ │
│ Ícone flutuante                  │
│ ┌──────────────────────────────┐ │
│ │[◉] Personalizar ícone      › │ │  ← tamanho, transparência
│ └──────────────────────────────┘ │
├──────────────────────────────────┤
│   ⌂ Inicial   文 Traduções  ⋯ Mais │
└──────────────────────────────────┘
```

### 5.2 Traduções

Junta o que hoje está espalhado: traduzir texto digitado e o histórico.

```
┌──────────────────────────────────┐
│ Webtoon Translator               │
│ ┌──────────────────────────────┐ │
│ │ 🇺🇸 Inglês           (📋)(✕) │ │  ← colar / limpar
│ │ Digite ou cole uma frase     │ │
│ │                  ( Traduzir )│ │  ← pílula azul
│ └──────────────────────────────┘ │
│ ( Recentes 12/20 ) ( Favoritos 3 )  ← abas em pílula
│ ┌──────────────────────────────┐ │
│ │ Among the heroes of…       ★ │ │  ← mesmo cartão de hoje: toque abre,
│ │ Entre os heróis daquele…     │ │     segurar entra no modo de seleção
│ │ gemini-3-flash · 2,1 s       │ │
│ └──────────────────────────────┘ │
├──────────────────────────────────┤
│   ⌂ Inicial   文 Traduções  ⋯ Mais │
└──────────────────────────────────┘
```

O resultado de "Traduzir" usa o mesmo cartão de tradução do ícone flutuante (★, copiar, ouvir,
PT⇄IPA, Aprender) e entra nos recentes.

### 5.3 Mais

```
┌──────────────────────────────────┐
│ Webtoon Translator               │
│ ┌──────────────────────────────┐ │
│ │[🔗] Conexão                 › │ │  provedor, chave, modelo ★, testar velocidade, benchmark
│ │[🔊] Voz                     › │ │  voz do Android, tom, velocidade
│ │[🌙] Aparência   Automático  › │ │  automático / claro / escuro + opacidade do cartão
│ │[⊕] Acesso rápido            › │ │  atalho no painel rápido do Android
│ └──────────────────────────────┘ │
│ Dados                            │
│ ┌──────────────────────────────┐ │
│ │[⇅] Backup                   › │ │  exportar / importar (mesmo arquivo do desktop)
│ │[▤] Armazenamento    1,2 MB  › │ │
│ └──────────────────────────────┘ │
│ Suporte                          │
│ ┌──────────────────────────────┐ │
│ │[📖] Tutorial                › │ │
│ │[ⓘ] App fechando sozinho     › │ │  otimização de bateria (Samsung/Xiaomi)
│ │[⚖] Sobre e licenças         › │ │  versão, GPL, créditos
│ └──────────────────────────────┘ │
├──────────────────────────────────┤
│   ⌂ Inicial   文 Traduções  ⋯ Mais │
└──────────────────────────────────┘
```

Cada item abre uma tela própria (com seta de voltar), no mesmo padrão de cartões.

### 5.4 Voz (dentro de Mais)

Voz do próprio Android (`TextToSpeech`): troca de voz, tom e velocidade são nativos e instantâneos,
sem gerar áudio nem baixar modelo.

```
┌──────────────────────────────────┐
│ ←  Voz                           │
│ Inglês                           │
│ ┌──────────────────────────────┐ │
│ │ Voz                        ▾ │ │  ← bottom sheet com as vozes en-US/en-GB instaladas
│ │ Padrão                       │ │
│ │ ──────────────────────────── │ │
│ │ Tom              ───●───  1,0│ │
│ │ ──────────────────────────── │ │
│ │ Velocidade       ──●────  0,70x  ← padrão 0,70x, "↺ Voltar ao padrão"
│ │ ──────────────────────────── │ │
│ │ ( ▶ Ouvir exemplo )          │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

### 5.5 Bottom sheets

```
├──────────────────────────────────┤
│        Escolher modelo        ✕  │  ← título centralizado, negrito; ✕ à direita
│ ┌──────────────────────────────┐ │
│ │ ★ gemini-3-flash          ✓  │ │  ← itens = blocos arredondados (cor de "campo")
│ └──────────────────────────────┘ │     selecionado com ✓ no azul
│ ┌──────────────────────────────┐ │
│ │ ★ claude-haiku               │ │
│ └──────────────────────────────┘ │
│ Todos os modelos                 │
│ ┌──────────────────────────────┐ │
│ │ gpt-5-mini                   │ │
│ └──────────────────────────────┘ │
```

Modo de tradução no mesmo formato, com ícone colorido + descrição em cada item
(ex.: **Região** — "Toque no ícone e arraste sobre o balão"; **Região fixa** — "Marque a área uma
vez e traduza de novo com um toque").

### 5.6 Cartão de tradução flutuante

No estilo do balão da região fixa do Translate On Screen: **fundo escuro translúcido**, texto grande,
**arrastável** pra qualquer lugar da tela (segurando o cabeçalho). O cabeçalho tem **só ícones**,
todos iguais (botão redondo 36 dp, fundo branco @ 12%, ícone branco), pra sobrar espaço pra tradução:

```
┌────────────────────────────────────────────┐
│ (🔊)(🐢)(📋)(★)(IPA)(📖)        (⌗)(✕)      │  ← ações à esquerda; ⌗ selecionar de novo e ✕ à direita
│ E o "Arqueiro Divino", que nunca errava    │  ← tradução (18 sp)
│ um alvo…                                    │
│ And the "Divine Archer," who never…        │  ← original (14 sp, 85%)
│ 🗣 énd de di-VAIN ÁRT-cher…                │  ← pronúncia (PT-BR ou IPA)
│ ────────────────────────────────────────── │
│ [Trechos] [Gramática]                       │  ← só com 📖 ligado
│ gemini-3-flash · 1,7 s                      │
└────────────────────────────────────────────┘
```

- Fundo `#1E1E1E` com a opacidade escolhida em Aparência (padrão 90%), cantos 14 dp.
- Ícone ligado (★ favoritado, IPA ativo, 📖 aberto) = fundo azul @ 30% e ícone azul.
- ⌗ só aparece nos modos de região (redefine a área).
- No app (aba Traduções), o mesmo cartão aparece no tema da tela, sem o ⌗ e sem arrastar.

### 5.7 Modos de tradução

Escolhidos no seletor **Modo de tradução** (tela Inicial e menu do ícone), bottom sheet com ícone
colorido + descrição em cada item:

| Modo | Como funciona | Onde aparece a tradução |
|---|---|---|
| **Região** | Toca no ícone, arrasta a setinha sobre o balão, solta | Cartão perto da área |
| **Região fixa** | Marca a área uma vez (alças nos cantos, ✓/✗); cada toque no ícone traduz de novo a mesma área; ⌗ redefine | Cartão perto da área |
| **Tela cheia** | Um toque traduz todos os balões da tela | Tradução curta **em cima de cada balão**; tocar num deles abre o cartão completo |
| **Região automática** | Área fixa traduzida sozinha quando o conteúdo dela muda | Cartão **fora** da área (pra não cobrir o que está sendo lido) |
| **Tela cheia automática** | Tela cheia de novo sempre que a rolagem para | Em cima de cada balão |

Regras de todos os modos:
- **Tudo que é nosso some antes da captura** (ícone, cartão, retângulo), senão o OCR lê o próprio cartão.
- Automáticos esperam a tela parar ~0,7 s, só chamam a IA se o **texto do OCR mudou** e reaproveitam
  traduções recentes (não pagam duas vezes pelo mesmo balão).

---

## 6. Implementação (Compose)

- `ui/theme/`: `Color.kt` (tokens acima, claro e escuro), `Theme.kt` (`WebtoonTheme(darkMode)`),
  `Shape.kt`, `Type.kt`.
- `ui/components/`: `AppScaffold` (barra do topo + navegação inferior), `SectionCard`,
  `SectionTitle`, `IconTile` (quadrado/redondo, cor @ transparência), `SettingRow`
  (ícone + título + legenda + seta/switch), `SelectorField` (rótulo + valor + ▾),
  `OptionSheet` (bottom sheet de opções), `PillTabs`, `StatusPill`.
- `ui/navigation/`: `NavigationBar` com as 3 abas; telas de detalhe (Conexão, Voz…) empilham por cima.
- Ícones: `material-icons-extended` (o R8 remove os não usados na versão final).
