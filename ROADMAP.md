# Roadmap

Pendências do Webtoon Translator, em ordem de prioridade. Ideias marcadas com 💡 vieram da análise do
app *Translate On Screen* (21/11/2026).

Legenda: ⬜ a fazer · 🟨 em andamento · ✅ feito · ➖ descartado ou absorvido por outro item

---

## Android

### Modos de tradução e cartão novo (ver [`android/LAYOUT.md`](android/LAYOUT.md) §5.6–5.7) — ✅

| # | Item | Status |
|---|---|---|
| M1 | **Cartão flutuante novo**: escuro translúcido, arrastável, cabeçalho só com ícones iguais (🔊 🐢 📋 ★ IPA 📖 · ⌗ ✕) | ✅ (opacidade em Mais → Aparência, item 15) |
| M2 | **Seletor de modo** (menu do ícone e tela Inicial) | ✅ |
| M3 | **Região** no cartão novo | ✅ |
| M4 | **Região fixa**: área com alças nos cantos e ✓/✗; toque no ícone traduz de novo; ⌗ redefine (absorve o item 31) | ✅ |
| M5 | **Tela cheia**: OCR com posição de cada bloco + agrupamento em balões; uma chamada à IA pra todos; tradução em cima de cada balão; toque abre o cartão completo | ✅ (traduções acima de cada balão, arrastáveis) |
| M6 | **Região automática**: traduz sozinho quando a área muda (cartão fora da área) | ✅ |
| M7 | **Tela cheia automática**: traduz de novo quando a rolagem para | ✅ |
| M8 | **OCR do Google (ML Kit)** no Android: ~4× mais rápido que o ocrs e sem o ruído das bordas de balão/efeitos sonoros; descarta linhas de confiança baixa, minúsculas ou com endereço de site; ocrs fica de reserva (licença: exceção da GPL no fim do `LICENSE`) | ✅ |
| M9 | **Desempenho dos automáticos**: tela "parada" em 0,5 s, tradução logo após a 1ª leitura (conferência em paralelo), ícone apagado da imagem em vez de escondido; timeouts de rede (núcleo) | ✅ (região: ~4 s → ~1,8 s até a tradução; tela cheia: ~3,5 s → ~1,8 s) |

### Layout novo (ver [`android/LAYOUT.md`](android/LAYOUT.md))

Padrão visual e organização inspirados no Translate On Screen, com o azul do app. Vem antes do
resto: as próximas telas já nascem no padrão novo.

| # | Item | Status |
|---|---|---|
| L1 | **Tema claro e escuro** com as cores do LAYOUT.md (neutros + azul), seguindo o sistema; opção Automático / Claro / Escuro | ✅ |
| L2 | **Componentes padrão**: barra do topo fixa, navegação inferior, cartão de seção, título de seção, `IconTile` (quadrado/redondo, cor em transparência + ícone sólido), linha de configuração, seletor ▾, bottom sheet de opções, abas em pílula | ✅ |
| L3 | **Navegação inferior: Inicial \| Traduções \| Mais** | ✅ |
| L4 | **Inicial**: cartão com botão grande + pílula de status + seletores **Modelo de IA** e **Modo de tradução** (bottom sheets); última tradução; personalizar ícone | ✅ (cartão de controle, seletores de modelo ★ e de modo, última tradução; "personalizar ícone" descartado) |
| L5 | **Traduções**: traduzir texto digitado/colado (absorve o item 37) + Recentes/Favoritos | ✅ |
| L6 | **Mais**: Conexão, Voz, Aparência, Acesso rápido · Dados (Backup, Armazenamento) · Suporte (Tutorial, App fechando sozinho, Sobre e licenças) | ✅ |
| L7 | **Voz**: voz do Android (escolher entre as instaladas), **tom** e velocidade, ouvir exemplo — tudo nativo, sem gerar áudio | ✅ (voz instalada, tom, velocidade, ouvir exemplo) |
| L8 | Cartão de tradução e ícone flutuante seguindo o tema e o padrão de botões redondos | ✅ |
| L9 | **Opção "Pronúncia em IPA"** (em Mais → Aparência ou Voz): desligada por padrão; ligada, mostra o botão IPA no cartão pra alternar PT-BR ⇄ IPA | ✅ (Mais → Aparência) |
| L10 | **Erros recentes** (em Mais → Suporte): lista das últimas falhas (API, OCR) com data e detalhe técnico, pra investigar quando uma tradução falha. O registro já é gravado (`ErrorLog`); falta a tela | ✅ (Mais → Erros recentes; tocar copia o detalhe) |

### Prioridade 1 — base do app (paridade com o desktop)

**Tela inicial**

| # | Item | Status |
|---|---|---|
| 1 | **Botão grande Iniciar/Parar** o tradutor flutuante, com o estado escrito embaixo 💡 | ✅ |
| 2 | **Seletor de modelo** na tela inicial, com ★ favoritos no topo | ✅ |
| 3 | **Recentes (até 100) / Favoritos** (sem limite, mostrando o espaço ocupado por texto + áudio); ★ em cada cartão; o cartão ativo fica destacado | ✅ |
| 4 | **Excluir em lote**: segurar o dedo num cartão entra no modo de seleção (caixas de marcar + "Excluir (n)") | ✅ |
| 5 | Tocar num cartão do histórico **abre a tradução completa** de novo | ✅ |

**Configurações → Conexão**

| # | Item | Status |
|---|---|---|
| 6 | Provedor (Google, Anthropic, compatível OpenAI), URL do gateway e chave de API cifrada | ✅ |
| 7 | Modelo com lista dos modelos da chave e **★ favoritos** | ✅ (★ no seletor de modelo da tela Inicial) |
| 8 | **⏱ Testar velocidade** do modelo escolhido | ✅ (Conexão) |
| 9 | **🏁 Rodar benchmark**: mede todos os modelos e marca os 5 mais rápidos como favoritos (mover a lógica do desktop pro `core/` pra os dois usarem) | ✅ (Conexão; lógica movida pro core, o desktop usa a mesma) |

**Configurações → Voz**

| # | Item | Status |
|---|---|---|
| 10 | **Voz do Android** como padrão (escolher entre as vozes em inglês instaladas, **tom** e velocidade — ver L7) | ✅ |
| 11 | **Velocidade da fala** (padrão 0,70x, dica e "↺ Voltar ao padrão") | ✅ |
| 12 | **🐢 lento** fixo em 0,5x, independente da velocidade configurada | ✅ |
| 13 | **▶ Ouvir exemplo** da voz escolhida | ✅ (Mais → Voz → Ouvir exemplo) |
| 14 | ~~Vozes Kokoro/Piper pra baixar~~ — descartado no Android: a voz do sistema já troca voz, tom e velocidade na hora, sem baixar nada (ver L7) | ➖ |

**Configurações → Geral**

| # | Item | Status |
|---|---|---|
| 15 | **Opacidade do cartão flutuante** (translucidez, igual à do balão do desktop) | ✅ (Mais → Aparência, com prévia) |
| 16 | **Backup: exportar / importar** (configurações, chaves, favoritos de modelo, traduções) no **mesmo formato do desktop**: exporta no PC e importa no celular, e vice-versa | ✅ (Mais → Backup; mesmo arquivo do desktop, guarda os campos só do desktop) |

**Cartão de tradução (o "balão" do celular)**

| # | Item | Status |
|---|---|---|
| 17 | Tradução, original e pronúncia **PT-BR ⇄ IPA** (botão pra alternar) | ✅ (IPA escondido por padrão, ver L9) |
| 18 | 🔊 / 🐢 na frase e em cada trecho | ✅ |
| 19 | **📖 Aprender ▾** recolhível com abas Trechos / Gramática (lembra a escolha) | ✅ |
| 20 | **📋 Copiar** e **★ favoritar** direto do cartão | ✅ |
| 21 | Rodapé com modelo · tempo | ✅ |

**Geral do app**

| # | Item | Status |
|---|---|---|
| 22 | **Ícones de verdade** com área de toque de 48 dp (← e ⚙ estavam pequenos demais) | ✅ |
| 23 | **Tela explicativa antes de cada permissão** do sistema, com imagem e texto de privacidade 💡 | 🟨 (explicação em texto; falta a imagem) |

### Prioridade 2 — botão flutuante

| # | Item | Status |
|---|---|---|
| 24 | Ícone flutuante arrastável que **gruda na borda** (semitransparente quando parado) e volta pro canto ao trocar de app 💡 | 🟨 (fica onde foi solto; volta encostado na borda ao tocar fora ou rolar e fica transparente; falta voltar ao trocar de app) |
| 25 | **Modo região**: tocar no ícone arma a seleção; arrastar o ícone desenha o retângulo a partir daquele ponto; soltar traduz. Fora disso, arrastar só move o ícone e a página embaixo continua rolando 💡 | ✅ |
| 26 | **Toque longo** (ou tocar de novo sem arrastar) abre o menu de baixo: modo, modelo, abrir o app, parar 💡 | 🟨 (modo, abrir o app e parar; falta escolher o modelo) |
| 27 | **Cartão de tradução perto da área selecionada**, com o conteúdo do balão do desktop (PT/IPA, 🔊, 🐢, Aprender, Copiar, ★) | ✅ |
| 28 | **Tocar numa palavra** mostra o significado (a glosa; no celular não existe hover) 💡 | ✅ (pontilhado nas palavras do original e dos trechos; tocar mostra a tradução) |
| 29 | Desligar pelo **"Parar" na notificação** e arrastando o ícone até um ✕ embaixo da tela | ➖ ("Parar" na notificação e no menu do ícone bastam; arrastar até o ✕ descartado) |
| 30 | Tela de ajuda pro **"app fechando sozinho"** (otimização de bateria da Samsung/Xiaomi) 💡 | ✅ (Mais → App fechando sozinho) |

Regra de captura: **esconder tudo que é nosso** (ícone, cartão, retângulo) antes de capturar a tela e
cortar a barra de status. O Translate On Screen não faz isso e traduz o próprio aviso e o próprio cartão.

### Prioridade 3 — depois que o básico estiver bom

| # | Item | Status |
|---|---|---|
| 31 | **Modo região fixa**: desenhar a área uma vez (alças nos cantos, ✓/✗); cada toque no ícone traduz de novo; botão "selecionar de novo" no cartão 💡 | ➖ (virou M4) |
| 32 | **Editar o texto do OCR** e traduzir de novo (quando o OCR erra uma letra) 💡 | ⬜ |
| 33 | Personalizar o ícone flutuante: tamanho, transparência, cor 💡 | ⬜ |
| 34 | Ações configuráveis no toque duplo e no toque longo do ícone 💡 | ⬜ |
| 35 | Tutorial curto na primeira abertura (e no menu "?") 💡 | ✅ (Android: abre na primeira vez e em Mais → Tutorial) |
| 36 | Atalho no painel rápido do Android (Quick Settings tile) pra ligar/desligar 💡 | ✅ (atalho no painel rápido e widget 2×1 na tela inicial) |

### Prioridade 4 — extras

| # | Item | Status |
|---|---|---|
| 37 | Aba **"Traduzir texto"**: digitar ou colar uma frase, com a mesma análise didática 💡 | ➖ (vira parte da aba Traduções, L5) |
| 38 | Traduzir pela **câmera** (livro/mangá físico) 💡 | ⬜ |
| 39 | Capturar **só um app** (Android 14+), por privacidade 💡 | ⬜ |

### Fora do escopo (decidido)

- **Vários motores de tradução** (Yandex, Baidu…): o diferencial é a IA com explicação; o seletor de
  modelo já cobre a escolha.

---

## Desktop

> Plano (21/11/2026, reforçado em 22/11): **migrar tudo do Android pro desktop exatamente como está**
> (abas, cartões, seletores, modos inclusive os automáticos, digitar frase, tutorial, backup etc.). A única
> diferença é o que é próprio do computador: atalhos de teclado, botões, janelas. O balão do desktop já tem
> o layout do cartão do Android (botões redondos, 🔊/🐢 à esquerda, trechos com nota) e o 🐢 em 0,30x.
> OCR: o ML Kit só existe pra celular; no desktop, avaliar o OCR do Windows (Windows.Media.Ocr) e, no
> Linux, PaddleOCR/RapidOCR, medindo contra o ocrs como foi feito no Android.

| # | Item | Status |
|---|---|---|
| D1 | **Repetir a última área** (região fixa) por atalho, ex.: Shift+F9 💡 | ⬜ |
| D2 | **Editar o texto do OCR** no balão e traduzir de novo 💡 | ⬜ |
| D3 | **Traduzir texto digitado/colado** 💡 (como a aba Traduções do Android) | ⬜ |
| D4 | Tutorial curto na primeira abertura 💡 (como o do Android) | ⬜ |
| D10 | Modos automáticos (região e tela cheia), como no Android | ⬜ |
| D11 | Rodapé do modelo no balão troca o modelo e traduz de novo (no Android já faz) | ⬜ |
| D12 | OCR melhor que o ocrs: Windows.Media.Ocr (Windows) e PaddleOCR/RapidOCR (Linux), medindo antes | ⬜ |
| D5 | Garantir que o balão **nunca entra na captura** quando está em cima da área selecionada | ⬜ |
| D6 | "Salvar no vocabulário" (decidir onde ver/revisar as palavras) | ⬜ |
| D7 | Ajustar a espera de 120 ms antes da captura (depois de testar no Windows) | ⬜ |
| D8 | Testar o instalador num PC com Windows de verdade | ⬜ |
| D9 | Aceleração por GPU (opcional) | ⬜ |

### Descartados

Assinatura digital no Windows, Flatpak/Flathub, dicionário offline no hover, atalho Ctrl+Ctrl e
Alt+clique (o sistema não oferece atalho global assim sem ler todas as teclas).
