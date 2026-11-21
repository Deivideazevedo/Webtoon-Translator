# Roadmap

Pendências do Webtoon Translator, em ordem de prioridade. Ideias marcadas com 💡 vieram da análise do
app *Translate On Screen* (21/11/2026).

Legenda: ⬜ a fazer · 🟨 em andamento · ✅ feito

---

## Android

### Prioridade 1 — base do app (paridade com o desktop)

**Tela inicial**

| # | Item | Status |
|---|---|---|
| 1 | **Botão grande Iniciar/Parar** o tradutor flutuante, com o estado escrito embaixo 💡 | ⬜ |
| 2 | **Seletor de modelo** na tela inicial, com ★ favoritos no topo | ⬜ |
| 3 | **Recentes (até 20) / Favoritos** (sem limite, mostrando o espaço ocupado por texto + áudio); ★ em cada cartão; o cartão ativo fica destacado | ⬜ |
| 4 | **Excluir em lote**: segurar o dedo num cartão entra no modo de seleção (caixas de marcar + "Excluir (n)") | ⬜ |
| 5 | Tocar num cartão do histórico **abre a tradução completa** de novo | ⬜ |

**Configurações → Conexão**

| # | Item | Status |
|---|---|---|
| 6 | Provedor (Google, Anthropic, compatível OpenAI), URL do gateway e chave de API cifrada | ✅ |
| 7 | Modelo com lista dos modelos da chave e **★ favoritos** | 🟨 (lista feita, falta ★) |
| 8 | **⏱ Testar velocidade** do modelo escolhido | ⬜ |
| 9 | **🏁 Rodar benchmark**: mede todos os modelos e marca os 5 mais rápidos como favoritos (mover a lógica do desktop pro `core/` pra os dois usarem) | ⬜ |

**Configurações → Voz**

| # | Item | Status |
|---|---|---|
| 10 | **Voz do Android** como padrão (escolher entre as vozes em inglês instaladas no celular) | 🟨 (já fala, falta escolher a voz) |
| 11 | **Velocidade da fala** (padrão 0,70x, dica e "↺ Voltar ao padrão") | ✅ |
| 12 | **🐢 lento** fixo em 0,5x, independente da velocidade configurada | ✅ |
| 13 | **▶ Ouvir exemplo** da voz escolhida | ⬜ |
| 14 | **Vozes Kokoro (mais natural) / Piper (mais leve) pra baixar**, com os 11 tons de cada e a "Prioridade do áudio" (mais rápido / melhor qualidade), igual ao desktop — etapa seguinte à voz do Android | ⬜ |

**Configurações → Geral**

| # | Item | Status |
|---|---|---|
| 15 | **Opacidade do cartão flutuante** (translucidez, igual à do balão do desktop) | ⬜ |
| 16 | **Backup: exportar / importar** (configurações, chaves, favoritos de modelo, traduções) no **mesmo formato do desktop**: exporta no PC e importa no celular, e vice-versa | ⬜ |

**Cartão de tradução (o "balão" do celular)**

| # | Item | Status |
|---|---|---|
| 17 | Tradução, original e pronúncia **PT-BR ⇄ IPA** (botão pra alternar) | 🟨 (falta o IPA) |
| 18 | 🔊 / 🐢 na frase e em cada trecho | ✅ |
| 19 | **📖 Aprender ▾** recolhível com abas Trechos / Gramática (lembra a escolha) | 🟨 (mostra tudo aberto) |
| 20 | **📋 Copiar** e **★ favoritar** direto do cartão | ⬜ |
| 21 | Rodapé com modelo · tempo | ✅ |

**Geral do app**

| # | Item | Status |
|---|---|---|
| 22 | **Ícones de verdade** com área de toque de 48 dp (← e ⚙ estavam pequenos demais) | ✅ |
| 23 | **Tela explicativa antes de cada permissão** do sistema, com imagem e texto de privacidade 💡 | ⬜ |

### Prioridade 2 — botão flutuante

| # | Item | Status |
|---|---|---|
| 24 | Ícone flutuante arrastável que **gruda na borda** (meio escondido e semitransparente quando parado) e volta pro canto ao trocar de app 💡 | ⬜ |
| 25 | **Modo região**: tocar no ícone arma a seleção; arrastar o ícone desenha o retângulo a partir daquele ponto; soltar traduz. Fora disso, arrastar só move o ícone e a página embaixo continua rolando 💡 | ⬜ |
| 26 | **Toque longo** (ou tocar de novo sem arrastar) abre o menu de baixo: modo, modelo, abrir o app, parar 💡 | ⬜ |
| 27 | **Cartão de tradução perto da área selecionada**, com o conteúdo do balão do desktop (PT/IPA, 🔊, 🐢, Aprender, Copiar, ★) | ⬜ |
| 28 | **Tocar numa palavra** mostra o significado (a glosa; no celular não existe hover) 💡 | ⬜ |
| 29 | Desligar pelo **"Parar" na notificação** e arrastando o ícone até um ✕ embaixo da tela | ⬜ |
| 30 | Tela de ajuda pro **"app fechando sozinho"** (otimização de bateria da Samsung/Xiaomi) 💡 | ⬜ |

Regra de captura: **esconder tudo que é nosso** (ícone, cartão, retângulo) antes de capturar a tela e
cortar a barra de status. O Translate On Screen não faz isso e traduz o próprio aviso e o próprio cartão.

### Prioridade 3 — depois que o básico estiver bom

| # | Item | Status |
|---|---|---|
| 31 | **Modo região fixa**: desenhar a área uma vez (alças nos cantos, ✓/✗); cada toque no ícone traduz de novo; botão "selecionar de novo" no cartão 💡 | ⬜ |
| 32 | **Editar o texto do OCR** e traduzir de novo (quando o OCR erra uma letra) 💡 | ⬜ |
| 33 | Personalizar o ícone flutuante: tamanho, transparência, cor 💡 | ⬜ |
| 34 | Ações configuráveis no toque duplo e no toque longo do ícone 💡 | ⬜ |
| 35 | Tutorial curto na primeira abertura (e no menu "?") 💡 | ⬜ |
| 36 | Atalho no painel rápido do Android (Quick Settings tile) pra ligar/desligar 💡 | ⬜ |

### Prioridade 4 — extras

| # | Item | Status |
|---|---|---|
| 37 | Aba **"Traduzir texto"**: digitar ou colar uma frase, com a mesma análise didática 💡 | ⬜ |
| 38 | Traduzir pela **câmera** (livro/mangá físico) 💡 | ⬜ |
| 39 | Capturar **só um app** (Android 14+), por privacidade 💡 | ⬜ |

### Fora do escopo (decidido)

- **Modo tela cheia e modos automáticos**: no teste traduziram a barra de status, a URL do navegador e o
  próprio cartão; o foco do app é entender um balão por vez.
- **Vários motores de tradução** (Yandex, Baidu…): o diferencial é a IA com explicação; o seletor de
  modelo já cobre a escolha.

---

## Desktop

| # | Item | Status |
|---|---|---|
| D1 | **Repetir a última área** (região fixa) por atalho, ex.: Shift+F9 💡 | ⬜ |
| D2 | **Editar o texto do OCR** no balão e traduzir de novo 💡 | ⬜ |
| D3 | **Traduzir texto digitado/colado** 💡 | ⬜ |
| D4 | Tutorial curto na primeira abertura 💡 | ⬜ |
| D5 | Garantir que o balão **nunca entra na captura** quando está em cima da área selecionada | ⬜ |
| D6 | "Salvar no vocabulário" (decidir onde ver/revisar as palavras) | ⬜ |
| D7 | Ajustar a espera de 120 ms antes da captura (depois de testar no Windows) | ⬜ |
| D8 | Testar o instalador num PC com Windows de verdade | ⬜ |
| D9 | Aceleração por GPU (opcional) | ⬜ |

### Descartados

Assinatura digital no Windows, Flatpak/Flathub, dicionário offline no hover, atalho Ctrl+Ctrl e
Alt+clique (o sistema não oferece atalho global assim sem ler todas as teclas).
