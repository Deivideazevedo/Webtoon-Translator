package com.deivide.webtoontranslator.floating

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.deivide.webtoontranslator.BuildConfig
import com.deivide.webtoontranslator.MainActivity
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.settings.TranslationMode
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.domain.model.ScreenBlock
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.ModelListLoader
import com.deivide.webtoontranslator.ui.common.resolve
import com.deivide.webtoontranslator.ui.common.toUiText
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions
import com.deivide.webtoontranslator.ui.translation.TranslationCardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Tradutor flutuante: serviço em primeiro plano que mantém a captura de tela e as janelas por
 * cima dos outros apps (ícone, seleção, editor da região fixa, cartão de tradução e menu).
 * Os modos estão descritos no LAYOUT.md §5.7.
 */
class FloatingService : Service(), FloatingIcon.Listener {

    private val container by lazy { (application as WebtoonApp).container }
    private val settings by lazy { container.settingsRepository }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager

    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var icon: FloatingIcon? = null
    private var selection: SelectionOverlay? = null
    private var editor: FixedRegionEditor? = null
    private var cardOverlay: ComposeOverlay? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private var hintOverlay: ComposeOverlay? = null
    private var screenBlocks: ScreenBlocksOverlay? = null
    private var sheet: ComposeOverlay? = null
    private val sheetOpen = MutableStateFlow(false)
    private val modelLoader by lazy { ModelListLoader(container.translationRepository, scope) }

    private val card = MutableStateFlow<TranslationCardState?>(null)
    private var translationJob: Job? = null
    /** Refaz a última tradução do cartão ("Tentar de novo"), com a mesma captura. */
    private var lastRun: (() -> Unit)? = null

    /** Modo atual; muda pelo menu do ícone ou pela tela Inicial (ver [applyMode]). */
    private val mode: TranslationMode get() = settings.translationMode

    private val cardActions by lazy {
        val actions = container.translationActions
        TranslationCardActions(
            onSpeak = { text, slow -> actions.speak(text, slow)?.let { toast(it.resolve(this)) } },
            onToggleFavorite = { scope.launch { actions.toggleFavorite(it) } },
            onOptionsChange = actions::updateOptions,
            onClose = ::closeCard,
            onRetry = { lastRun?.invoke() },
            onPickModel = ::openModelPicker,
            onTypeText = ::openTypeText,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> if (projection == null) start(intent)
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopSelf()
            return
        }
        // O Android exige o serviço em primeiro plano (tipo "captura de tela") antes de pegar a captura.
        startForeground(
            FloatingNotification.ID,
            FloatingNotification.build(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        windows = getSystemService(WindowManager::class.java)
        val projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
            ?: return stopSelf()
        this.projection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            // Parou pelo sistema (ex.: "parar compartilhamento" na barra de status).
            override fun onStop() = stopSelf()

            // Diagnóstico (dev): no modo "um app só", o tamanho e a visibilidade do que é capturado.
            override fun onCapturedContentResize(width: Int, height: Int) {
                if (BuildConfig.DEBUG) Log.d(DEV_TAG, "captura: conteúdo ${width}x$height")
            }

            override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                if (BuildConfig.DEBUG) Log.d(DEV_TAG, "captura: conteúdo visível=$isVisible")
            }
        }, mainHandler)
        capturer = ScreenCapturer(this, projection)
        selection = SelectionOverlay(this, windows)
        screenBlocks = ScreenBlocksOverlay(
            this, windows, onOpen = ::openBlock, onDismiss = { screenBlocks?.remove() }, onBack = ::backClosesBubbles,
        )
        icon = FloatingIcon(this, windows, this).also { it.show() }
        // O modo pode mudar por fora (tela Inicial); o ícone se ajusta sozinho.
        scope.launch { settings.mode.collect(::applyMode) }
        // Carrega o modelo do OCR já (a 1ª leitura não paga isso).
        scope.launch { container.translationRepository.warmUpOcr() }
        // A opção pode mudar com o cartão aberto (⚙ do cartão ou Mais → Aparência).
        scope.launch { settings.backClosesBubbles.collect { cardOverlay?.setOnBack(backClosesBubbles()) } }
        container.floatingStatus.set(true)
        FloatingWidget.refresh(this, running = true)
    }

    override fun onDestroy() {
        container.floatingStatus.set(false)
        FloatingWidget.refresh(this, running = false)
        translationJob?.cancel()
        scope.cancel()
        closeMenu()
        closeEditor()
        closeSheet()
        hintOverlay?.remove()
        saveCardPosition()
        cardOverlay?.remove()
        screenBlocks?.remove()
        selection?.remove()
        icon?.remove()
        capturer?.release()
        projection?.stop()
        super.onDestroy()
    }

    // --- Ícone ---

    /**
     * Região fixa: traduz a área marcada (ou abre o editor se ainda não há área).
     * Região automática: mostra a área (o ✓ traduz); tocar de novo fecha, como o ✕.
     * Tela cheia: traduz a tela; se as traduções já estão na tela, tira elas.
     */
    override fun onTap() {
        closeMenu()
        when (mode) {
            TranslationMode.FULL_SCREEN, TranslationMode.AUTO_FULL_SCREEN ->
                if (screenBlocks?.isShown == true) screenBlocks?.remove() else translateFullScreen()
            TranslationMode.AUTO_REGION -> if (editor != null) closeEditor() else openEditor()
            else -> {
                val region = settings.fixedRegion
                if (region == null || reviewFixedRegion) openEditor() else translateRegion(region)
            }
        }
    }

    override fun onArmed() {
        closeMenu()
        showHint(R.string.floating_armed_hint)
    }

    override fun onMenu() {
        hintOverlay?.remove()
        if (sheet != null) return closeMenu()
        openSheet { bottomInset ->
            FloatingIconMenu(
                open = sheetOpen,
                bottomInset = bottomInset,
                mode = mode,
                onSelectMode = ::selectMode,
                onOpenApp = ::openApp,
                onStop = ::stopSelf,
                onClose = ::hideSheet,
            )
        }
    }

    override fun onRegionChanged(region: Rect) {
        hintOverlay?.remove()
        selection?.update(region)
    }

    override fun onRegionDone(region: Rect) {
        selection?.remove()
        if (region.width() < MIN_REGION_PX || region.height() < MIN_REGION_PX / 2) return
        translateRegion(region)
    }

    /** Região fixa escolhida pela tela Inicial: o próximo toque mostra a área antes de traduzir. */
    private var reviewFixedRegion = false
    private var appliedMode: TranslationMode? = null

    private fun applyMode(newMode: TranslationMode) {
        if (appliedMode != null && newMode != appliedMode && newMode.usesFixedRegion) {
            reviewFixedRegion = true
        }
        appliedMode = newMode
        icon?.tapArms = newMode == TranslationMode.REGION
        screenBlocks?.remove()
        updateWatcher(newMode)
    }

    /** Troca pelo menu do ícone (o editor já abre aqui, então não precisa revisar no próximo toque). */
    private fun selectMode(newMode: TranslationMode) {
        closeMenu()
        applyMode(newMode)
        settings.translationMode = newMode
        when (newMode) {
            // Sempre mostra a área (a salva, se houver) pra conferir/ajustar antes de traduzir.
            TranslationMode.FIXED_REGION, TranslationMode.AUTO_REGION -> openEditor()
            TranslationMode.FULL_SCREEN -> showHint(R.string.full_screen_hint)
            TranslationMode.AUTO_FULL_SCREEN -> showHint(R.string.auto_full_screen_hint)
            TranslationMode.REGION -> Unit
        }
    }

    // --- Tradução ---

    private fun translateRegion(region: Rect) {
        translationJob?.cancel()
        lastAutoText = ""
        translationJob = scope.launch {
            val shot = captureWithoutOverlays() ?: return@launch
            val crop = crop(shot, region) ?: return@launch
            if (BuildConfig.DEBUG) Log.d(DEV_TAG, "região $region de uma tela ${shot.width}x${shot.height}")
            showCard(region)
            // "Tentar de novo" reaproveita a mesma captura (a página pode já ter rolado).
            lastRun = { runOnCard { container.regionTranslator.translate(crop) } }
            container.regionTranslator.translate(crop).collect { card.value = it }
        }
    }

    private fun crop(shot: Bitmap, region: Rect): Bitmap? {
        val bounds = Rect(0, 0, shot.width, shot.height)
        if (!bounds.intersect(region)) return null
        return Bitmap.createBitmap(shot, bounds.left, bounds.top, bounds.width(), bounds.height())
    }

    /** Roda uma tradução no cartão que já está aberto (sem mexer na posição dele). */
    private fun runOnCard(translation: () -> kotlinx.coroutines.flow.Flow<TranslationCardState>) {
        translationJob?.cancel()
        translationJob = scope.launch { translation().collect { card.value = it } }
    }

    /** Inglês que está no cartão ("" se ainda não foi lido). */
    private fun cardOriginal() = when (val state = card.value) {
        is TranslationCardState.Done -> state.translation.originalText
        is TranslationCardState.Streaming -> state.original
        else -> ""
    }

    /**
     * Troca de modelo pelo rodapé: traduz de novo o mesmo texto já lido (sem nova captura nem
     * OCR), com o modelo atual. Se o texto nem chegou a ser lido, refaz a última tradução (mesma captura).
     */
    private fun retranslate() {
        val text = cardOriginal()
        if (text.isNotBlank()) runOnCard { container.regionTranslator.translateText(text) } else lastRun?.invoke()
    }

    /**
     * Tela cheia: acha os balões, traduz todos numa chamada e põe cada tradução em cima do seu.
     * [auto] (tela cheia automática): sem aviso de "lendo" nem de erro, e só os balões novos vão pra IA.
     */
    private fun translateFullScreen(auto: Boolean = false) {
        translationJob?.cancel()
        closeCard()
        screenBlocks?.remove()
        translationJob = scope.launch {
            val shot = captureWithoutOverlays() ?: return@launch
            if (!auto) showHint(R.string.full_screen_reading, autoHide = false)
            val (statusBar, navigationBar) = systemBarHeights(this@FloatingService)
            val result = try {
                container.screenTranslator.translate(
                    shot, statusBar, navigationBar, cache = autoBlocks.takeIf { mode.isAutomatic },
                )
            } catch (e: Exception) {
                ScreenTranslator.Result.Failed(e.toUiText())
            } finally {
                hintOverlay?.remove()
                hintOverlay = null
            }
            when (result) {
                is ScreenTranslator.Result.Blocks -> screenBlocks?.show(result.blocks, topLimit = statusBar)
                is ScreenTranslator.Result.Failed -> if (!auto) toast(result.message.resolve(this@FloatingService))
            }
        }
    }

    /** Toque numa tradução da tela cheia: cartão completo (didático) daquele balão. */
    private fun openBlock(block: ScreenBlock) {
        lastRun = { runOnCard { container.regionTranslator.translateText(block.text) } }
        translationJob?.cancel()
        translationJob = scope.launch {
            showCard(Rect(block.left, block.top, block.right, block.bottom))
            container.regionTranslator.translateText(block.text).collect { card.value = it }
        }
    }

    /**
     * Captura sem o que é nosso (senão o OCR lê o próprio cartão): o cartão é escondido; o ícone e
     * a dica ficam na tela e são apagados da imagem (pintados por cima), o que dispensa esperar um
     * quadro novo e garante que o "—" do ícone nunca vire um traço no texto lido.
     */
    private suspend fun captureWithoutOverlays(): Bitmap? =
        captureWithout(listOfNotNull(cardOverlay?.let { it::setHidden }), maskedWindows())

    /**
     * Só a [area]: esconde apenas o que está em cima dela (região automática: o cartão fica fora
     * da área, então não pisca a cada leitura).
     */
    private suspend fun captureArea(area: Rect): Bitmap? = captureWithout(
        listOfNotNull(cardOverlay?.takeIf { it.screenRect()?.let { r -> Rect.intersects(r, area) } == true }?.let { it::setHidden }),
        maskedWindows().filter { Rect.intersects(it, area) },
    )

    /** Janelas nossas que ficam na tela na captura e são apagadas da imagem: o ícone e a dica. */
    private fun maskedWindows(): List<Rect> = listOfNotNull(icon?.position, hintOverlay?.screenRect())

    /** Esconde as janelas de [hide], captura (apagando as áreas de [mask]) e mostra de novo. */
    private suspend fun captureWithout(hide: List<(Boolean) -> Unit>, mask: List<Rect>): Bitmap? {
        val capturer = capturer ?: return null
        hide.forEach { it(true) }
        // Logo depois de fechar o editor, o quadro guardado ainda tem ele por cima do balão.
        val editorJustClosed = SystemClock.uptimeMillis() - editorClosedAt < FRESH_FRAME_MS
        if (BuildConfig.DEBUG) {
            Log.d(DEV_TAG, "captura: esconde=${hide.size} editorRecemFechado=$editorJustClosed máscaras=$mask")
        }
        return try {
            capturer.capture(waitForNewFrame = hide.isNotEmpty() || editorJustClosed, mask = mask)
        } catch (e: Exception) {
            card.value = TranslationCardState.Failed(e.toUiText())
            null
        } finally {
            hide.forEach { it(false) }
        }
    }

    // --- Cartão ---

    /**
     * Cartão logo abaixo da área (ou acima, se não couber embaixo); dá pra arrastar depois. Com o
     * "Balão principal fixo", o cartão aberto fica onde está (só o conteúdo muda) e um cartão novo
     * abre onde o último ficou.
     */
    private fun showCard(region: Rect) {
        val fixed = settings.fixedCard.value
        if (fixed && cardOverlay != null) return
        val screen = screenBounds(this)
        val saved = settings.cardPosition.takeIf { fixed }
        val below = saved?.let { !it.third } ?: (region.bottom < screen.height() * 0.55)
        val params = overlayParams(
            screen.width(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.START or if (below) Gravity.TOP else Gravity.BOTTOM,
        ).apply {
            if (saved != null) {
                x = saved.first
                y = saved.second
            } else {
                y = if (below) region.bottom + MARGIN_PX else screen.height() - region.top + MARGIN_PX
            }
        }
        cardOverlay?.remove()
        cardParams = params
        val controls = FloatingCardControls(
            onDrag = ::dragCard,
            onDragEnd = ::anchorCard,
            onReselect = if (mode.usesFixedRegion) ::openEditor else null,
            onOptions = ::openOptions,
        )
        cardOverlay = ComposeOverlay(this, windows, backHandler = backClosesBubbles()) {
            FloatingTranslationCard(
                card, container.libraryRepository.library, container.translationActions.options, cardActions, controls,
                opacity = settings.cardOpacity,
                backCloses = settings.backClosesBubbles,
            )
        }.also { it.show(params) }
    }

    /**
     * Soltou o cartão: prende ele na borda mais perto (metade de cima → topo, cresce pra baixo;
     * metade de baixo → base, cresce pra cima). Com a altura máxima de meia tela, o conteúdo novo
     * nunca empurra o cartão pra fora da tela.
     */
    private fun anchorCard() {
        val params = cardParams ?: return
        val rect = cardOverlay?.screenRect() ?: return
        val screenHeight = screenBounds(this).height()
        val atBottom = params.gravity and Gravity.BOTTOM == Gravity.BOTTOM
        val wantBottom = rect.centerY() > screenHeight / 2
        if (atBottom != wantBottom) {
            // Mesmo lugar, medido da outra borda.
            params.y = (screenHeight - params.y - rect.height()).coerceAtLeast(0)
            params.gravity = Gravity.START or if (wantBottom) Gravity.BOTTOM else Gravity.TOP
            cardOverlay?.show(params)
        }
        saveCardPosition()
    }

    /** Guarda onde o cartão está, pro "Balão principal fixo" abrir o próximo no mesmo lugar. */
    private fun saveCardPosition() {
        val params = cardParams ?: return
        if (!settings.fixedCard.value) return
        settings.cardPosition = Triple(params.x, params.y, params.gravity and Gravity.BOTTOM == Gravity.BOTTOM)
    }

    private fun dragCard(dx: Float, dy: Float) {
        val params = cardParams ?: return
        params.x += dx.toInt()
        // Com a janela presa embaixo (gravity BOTTOM), y conta de baixo pra cima.
        params.y += if (params.gravity and Gravity.BOTTOM == Gravity.BOTTOM) -dy.toInt() else dy.toInt()
        cardOverlay?.show(params)
    }

    /** Opção "Voltar fecha as traduções": o voltar fecha o cartão e as da tela cheia. */
    private fun backClosesBubbles(): (() -> Unit)? = if (settings.backClosesBubbles.value) ::closeBubbles else null

    private fun closeBubbles() {
        closeCard()
        screenBlocks?.remove()
    }

    private fun closeCard() {
        saveCardPosition()
        closeSheet()
        translationJob?.cancel()
        card.value = null
        cardOverlay?.remove()
        cardOverlay = null
        cardParams = null
    }

    // --- Bottom sheets do cartão (modelo e ⚙ opções) ---

    /**
     * Abre um bottom sheet (janela da tela toda, por cima de tudo); só um por vez. [onBack]: o
     * que o voltar faz (padrão: fechar o sheet).
     */
    private fun openSheet(
        onBack: () -> Unit = ::hideSheet,
        keyboard: Boolean = false,
        /** Só a faixa de baixo (sheet não modal); senão a tela inteira. */
        bottomOnly: Boolean = false,
        content: @Composable (bottomInset: Dp) -> Unit,
    ) {
        if (sheet != null) return
        val screen = screenBounds(this)
        val params = if (bottomOnly) {
            overlayParams(screen.width(), WindowManager.LayoutParams.WRAP_CONTENT, gravity = Gravity.BOTTOM)
        } else {
            overlayParams(screen.width(), screen.height())
        }
        val bottomInset = (systemBarHeights(this).second / resources.displayMetrics.density).dp
        sheetOpen.value = true
        sheet = ComposeOverlay(this, windows, backHandler = onBack, keyboard = keyboard) { content(bottomInset) }
            .also { it.show(params) }
    }

    /** Desce o sheet (animação) e tira a janela em seguida. */
    private fun hideSheet() {
        val current = sheet ?: return
        sheetOpen.value = false
        scope.launch {
            delay(SHEET_EXIT_MS)
            if (sheet === current) closeSheet()
        }
    }

    private fun closeSheet() {
        sheet?.remove()
        sheet = null
    }

    /** Escolher um modelo já vira o padrão do app e retraduz o cartão com ele. */
    private fun openModelPicker() {
        val current = settings.current
        val model = MutableStateFlow(current.value.model)
        val speeds = MutableStateFlow(settings.benchmarks.value[current.value.provider]?.speeds.orEmpty())
        openSheet { bottomInset ->
            FloatingModelPicker(
                open = sheetOpen,
                bottomInset = bottomInset,
                current = model,
                favorites = settings.favoriteModels,
                speeds = speeds,
                models = modelLoader.list,
                hasApiKey = current.value.apiKey.isNotBlank(),
                onLoad = { modelLoader.load(current.value) },
                onSelect = { id ->
                    hideSheet()
                    settings.selectModel(id)
                    retranslate()
                },
                onToggleFavorite = { settings.toggleFavoriteModel(it) },
                onClose = ::hideSheet,
            )
        }
    }

    /** "Digitar o texto" (OCR inseguro): o texto corrigido é traduzido no mesmo cartão. */
    private fun openTypeText(draft: String) {
        openSheet(keyboard = true, bottomOnly = true) { bottomInset ->
            FloatingTypeSheet(
                open = sheetOpen,
                bottomInset = bottomInset,
                draft = draft,
                onTranslate = { text ->
                    hideSheet()
                    lastRun = { runOnCard { container.regionTranslator.translateText(text) } }
                    runOnCard { container.regionTranslator.translateText(text) }
                },
                onClose = ::hideSheet,
            )
        }
    }

    /** ⚙ do cartão: modo, "voltar fecha as traduções" e velocidade da voz (subpágina). */
    private fun openOptions() {
        val page = MutableStateFlow(OptionsPage.MAIN)
        val speed = MutableStateFlow(settings.load().speechSpeed)
        openSheet(onBack = { if (page.value == OptionsPage.SPEED) page.value = OptionsPage.MAIN else hideSheet() }) { bottomInset ->
            FloatingOptionsSheet(
                open = sheetOpen,
                bottomInset = bottomInset,
                page = page,
                mode = settings.mode,
                backCloses = settings.backClosesBubbles,
                fixedCard = settings.fixedCard,
                speed = speed,
                onSelectMode = { newMode ->
                    hideSheet()
                    if (newMode != mode) selectMode(newMode)
                },
                onBackClosesChange = settings::setBackClosesBubbles,
                onFixedCardChange = settings::setFixedCard,
                onPage = { page.value = it },
                onSpeedChange = {
                    speed.value = it
                    settings.setSpeechSpeed(it)
                },
                // Ouve a velocidade na própria frase do cartão.
                onSpeedPreview = { cardActions.onSpeak(cardOriginal().ifBlank { SPEED_SAMPLE }, false) },
                onClose = ::hideSheet,
            )
        }
    }

    // --- Modos automáticos (LAYOUT.md §5.7) ---

    private var watcher: Job? = null
    private var watcherMode: TranslationMode? = null
    /**
     * Desde quando a tela está mudando sem ter sido traduzida (0 = nada pendente). A vigia traduz
     * quando ela fica parada [SETTLE_MS]; confirmar a área no editor também marca uma pendência.
     */
    private var autoPendingSince = 0L
    /** Leitura da área na região automática (separada da tradução, que pode continuar chegando). */
    private var autoJob: Job? = null
    /** Texto do OCR da última tradução automática da área (o mesmo texto não é traduzido de novo). */
    private var lastAutoText = ""
    /** Traduções feitas nos modos automáticos (texto do OCR → resultado): não paga duas vezes. */
    private val autoCards = lruMap<String, Translation>(AUTO_CACHE_SIZE)
    private val autoBlocks = lruMap<String, String>(AUTO_CACHE_SIZE)

    /**
     * Liga a vigia da tela nos modos automáticos e desliga nos outros. Usa o [newMode] recebido (a
     * preferência pode ainda não ter sido gravada) e não reinicia se já está vigiando esse modo
     * (a troca chega aqui duas vezes: pelo menu e pela preferência).
     */
    private fun updateWatcher(newMode: TranslationMode) {
        if (newMode == watcherMode && watcher?.isActive == true) return
        watcher?.cancel()
        watcher = null
        watcherMode = null
        autoJob?.cancel()
        lastAutoText = ""
        if (newMode.isAutomatic) {
            watcherMode = newMode
            watcher = scope.launch { watch() }
        }
    }

    /**
     * Compara a tela de tempos em tempos (uma grade de pontos, fora das nossas janelas). Quando ela
     * muda e depois fica parada por [SETTLE_MS], traduz sozinho. Não roda com sheet, editor ou (na
     * tela cheia) o cartão aberto. Ao ligar, traduz o que já está na tela.
     */
    private suspend fun watch() {
        var previous: IntArray? = null
        autoPendingSince = SystemClock.uptimeMillis()
        while (true) {
            delay(WATCH_INTERVAL_MS)
            val capturer = capturer ?: continue
            val area = autoArea()
            if (area == null || sheet != null || editor != null || (mode == TranslationMode.AUTO_FULL_SCREEN && cardOverlay != null)) {
                previous = null
                continue
            }
            val exclude = ownWindows()
            val current = withContext(Dispatchers.Default) { capturer.signature(area, exclude) } ?: continue
            val before = previous
            previous = current
            val now = SystemClock.uptimeMillis()
            if (before != null && screenChanged(before, current)) {
                if (autoPendingSince == 0L) onAutoChange()
                autoPendingSince = now
            } else if (autoPendingSince != 0L && now - autoPendingSince >= SETTLE_MS && !autoBusy()) {
                autoPendingSince = 0L
                autoTranslate()
            }
        }
    }

    /**
     * Ainda trabalhando na última mudança. Na região automática só a leitura conta: uma tradução
     * ainda chegando pode ser trocada por um texto novo.
     */
    private fun autoBusy() = when (mode) {
        TranslationMode.AUTO_REGION -> autoJob?.isActive == true
        else -> translationJob?.isActive == true
    }

    private fun autoArea(): Rect? = when (mode) {
        TranslationMode.AUTO_REGION -> settings.fixedRegion
        TranslationMode.AUTO_FULL_SCREEN -> {
            val (statusBar, navigationBar) = systemBarHeights(this)
            screenBounds(this).apply { top += statusBar; bottom -= navigationBar }
        }
        else -> null
    }

    /** As nossas janelas na tela: não contam como mudança (nem quando somem pra captura). */
    private fun ownWindows(): List<Rect> = buildList {
        icon?.position?.let(::add)
        cardOverlay?.screenRect()?.let(::add)
        hintOverlay?.screenRect()?.let(::add)
        screenBlocks?.rects()?.let(::addAll)
    }

    /** A tela começou a mudar (rolagem): na tela cheia, as traduções saem (ficariam fora do lugar). */
    private fun onAutoChange() {
        if (mode != TranslationMode.AUTO_FULL_SCREEN) return
        translationJob?.cancel()
        screenBlocks?.remove()
    }

    private fun autoTranslate() {
        when (mode) {
            TranslationMode.AUTO_REGION -> settings.fixedRegion?.let(::autoTranslateRegion)
            TranslationMode.AUTO_FULL_SCREEN -> if (screenBlocks?.isShown != true) translateFullScreen(auto = true)
            else -> Unit
        }
    }

    /**
     * Região automática: lê a área e só traduz (e só mexe no cartão) se o texto mudou de verdade
     * ([AutoText.same]: rolagem leve ou uma letra lida diferente não contam). Traduz logo após a
     * leitura e confere [STABLE_RECHECK_MS] depois: se o texto ainda estava crescendo (texto
     * "digitado" em jogos, quadrinho animado) e a tela continua mudando, a tradução parcial é
     * cancelada e a vigia traduz o texto completo quando a tela parar.
     */
    private fun autoTranslateRegion(region: Rect) {
        autoJob = scope.launch {
            val first = readArea(region) ?: return@launch
            if (AutoText.same(AutoText.normalize(lastAutoText), AutoText.normalize(first.text))) return@launch
            translateAuto(region, first.text, first.unsureDraft)
            delay(STABLE_RECHECK_MS)
            val again = readArea(region) ?: return@launch
            if (AutoText.same(AutoText.normalize(first.text), AutoText.normalize(again.text))) return@launch
            // Só cancela se a tela ainda está mudando (aí a vigia traduz o texto completo); com a
            // tela parada, a diferença é variação do OCR e a tradução continua.
            if (autoPendingSince != 0L) {
                translationJob?.cancel()
                lastAutoText = ""
            }
        }
    }

    /** Mostra o texto lido no cartão: do cache ou pedindo à IA. */
    private fun translateAuto(region: Rect, text: String, unsureDraft: String?) {
        lastAutoText = text
        if (cardOverlay == null) showCard(region)
        lastRun = { runOnCard { container.regionTranslator.translateText(text) } }
        val key = AutoText.normalize(text)
        AutoText.find(autoCards, key)?.let {
            translationJob?.cancel()
            card.value = TranslationCardState.Done(it, unsureDraft)
            return
        }
        runOnCard {
            container.regionTranslator.translateText(text, unsureDraft).onEach {
                if (it is TranslationCardState.Done) autoCards[key] = it.translation
            }
        }
    }

    /** Texto da área agora (vazio ou erro = null). */
    private suspend fun readArea(region: Rect): TranslationRepository.AreaText? {
        val shot = captureArea(region) ?: return null
        val crop = crop(shot, region) ?: return null
        val read = runCatching { container.translationRepository.recognizeArea(crop) }.getOrNull() ?: return null
        return read.copy(text = read.text.trim()).takeIf { it.text.isNotEmpty() }
    }

    // --- Região fixa ---

    private fun openEditor() {
        reviewFixedRegion = false
        closeMenu()
        closeEditor()
        closeCard()
        editor = FixedRegionEditor(
            this, windows, settings.fixedRegion,
            // O editor cobre a tela toda: o toque na bolinha chega nele e fecha, como o ✕.
            closeArea = icon?.position?.takeIf { mode == TranslationMode.AUTO_REGION },
            onConfirm = { region ->
                closeEditor()
                settings.fixedRegion = region
                if (mode == TranslationMode.AUTO_REGION) {
                    // A vigia traduz assim que a tela (já sem o editor) ficar parada: uma leitura só.
                    lastAutoText = ""
                    autoPendingSince = SystemClock.uptimeMillis()
                } else {
                    translateRegion(region)
                }
            },
            onCancel = ::closeEditor,
        ).also { it.show() }
        // Região automática: a bolinha fica armada por cima do editor; tocar de novo fecha.
        if (mode == TranslationMode.AUTO_REGION) icon?.showArmedOnTop(true)
    }

    private var editorClosedAt = 0L

    private fun closeEditor() {
        if (editor != null) {
            editorClosedAt = SystemClock.uptimeMillis()
            icon?.showArmedOnTop(false)
        }
        editor?.remove()
        editor = null
    }

    // --- Menu, dica e atalhos ---

    /** Menu do ícone (e qualquer sheet aberto): desce e some. */
    private fun closeMenu() = hideSheet()

    private fun showHint(@StringRes text: Int, autoHide: Boolean = true) {
        val anchor = icon?.position ?: return
        hintOverlay?.remove()
        val params = overlayParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            touchable = false,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply { y = (anchor.top - HINT_OFFSET_PX).coerceAtLeast(0) }
        val hint = ComposeOverlay(this, windows) { FloatingHint(getString(text)) }.also { it.show(params) }
        hintOverlay = hint
        if (autoHide) scope.launch {
            delay(HINT_MS)
            if (hintOverlay === hint) {
                hint.remove()
                hintOverlay = null
            }
        }
    }

    private fun openApp() {
        closeMenu()
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val MARGIN_PX = 16
        private const val MIN_REGION_PX = 24
        private const val HINT_OFFSET_PX = 140
        private const val HINT_MS = 2_500L
        private const val SHEET_EXIT_MS = 300L
        /** Diagnóstico da versão (dev): `adb logcat -s WT-Dev`. */
        private const val DEV_TAG = "WT-Dev"
        /** Até esse tempo depois de fechar o editor, a captura espera um quadro novo. */
        private const val FRESH_FRAME_MS = 1_500L
        private const val SPEED_SAMPLE = "Hey! Let's read some webtoons together."
        private const val WATCH_INTERVAL_MS = 100L
        /** A tela precisa ficar parada esse tempo pra contar como "parou de rolar". */
        private const val SETTLE_MS = 500L
        /** Texto novo é conferido de novo depois desse tempo (texto "digitado" que ainda crescia). */
        private const val STABLE_RECHECK_MS = 600L
        private const val AUTO_CACHE_SIZE = 200
        /** Um ponto mudou se o brilho variou mais que isso (0–255)… */
        private const val POINT_DELTA = 24
        /** …e a tela mudou se ao menos essa fração dos pontos mudou (ignora cursor piscando, relógio). */
        private const val CHANGED_FRACTION = 0.01

        /** Dois quadros diferem de verdade (pontos fora das nossas janelas nos dois). */
        private fun screenChanged(a: IntArray, b: IntArray): Boolean {
            var valid = 0
            var changed = 0
            for (i in a.indices) {
                if (a[i] < 0 || b[i] < 0) continue
                valid++
                if (abs(a[i] - b[i]) > POINT_DELTA) changed++
            }
            return valid > 0 && changed >= maxOf(4, (valid * CHANGED_FRACTION).toInt())
        }

        /** Mapa que esquece o menos usado quando passa de [max] itens. */
        private fun <K, V> lruMap(max: Int): MutableMap<K, V> = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
        }

        /** Liga o tradutor com a permissão de captura que o usuário acabou de dar. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(
                Intent(context, FloatingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_DATA, data)
            )
        }

        /**
         * Pedido de captura. No Android 14+ o usuário escolhe entre a tela inteira e um app só (aí
         * só esse app entra na captura; fora dele o tradutor não enxerga nada).
         */
        fun captureIntent(context: Context): Intent {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
            } else {
                manager.createScreenCaptureIntent()
            }
        }

        fun stopIntent(context: Context): Intent = Intent(context, FloatingService::class.java).setAction(ACTION_STOP)

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }
    }
}
