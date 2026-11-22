package com.deivide.webtoontranslator.floating

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.deivide.webtoontranslator.MainActivity
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.settings.TranslationMode
import com.deivide.webtoontranslator.domain.model.ScreenBlock
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
import kotlinx.coroutines.launch

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
        }, mainHandler)
        capturer = ScreenCapturer(this, projection)
        selection = SelectionOverlay(this, windows)
        screenBlocks = ScreenBlocksOverlay(
            this, windows, onOpen = ::openBlock, onDismiss = { screenBlocks?.remove() }, onBack = ::backClosesBubbles,
        )
        icon = FloatingIcon(this, windows, this).also { it.show() }
        // O modo pode mudar por fora (tela Inicial); o ícone se ajusta sozinho.
        scope.launch { settings.mode.collect(::applyMode) }
        // A opção pode mudar com o cartão aberto (⚙ do cartão ou Mais → Aparência).
        scope.launch { settings.backClosesBubbles.collect { cardOverlay?.setOnBack(backClosesBubbles()) } }
        container.floatingStatus.set(true)
    }

    override fun onDestroy() {
        container.floatingStatus.set(false)
        translationJob?.cancel()
        scope.cancel()
        closeMenu()
        closeEditor()
        closeSheet()
        hintOverlay?.remove()
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
     * Tela cheia: traduz a tela; se as traduções já estão na tela, tira elas.
     */
    override fun onTap() {
        closeMenu()
        when (mode) {
            TranslationMode.FULL_SCREEN -> if (screenBlocks?.isShown == true) screenBlocks?.remove() else translateFullScreen()
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
        if (appliedMode != null && newMode != appliedMode && newMode == TranslationMode.FIXED_REGION) {
            reviewFixedRegion = true
        }
        appliedMode = newMode
        icon?.tapArms = newMode == TranslationMode.REGION
        screenBlocks?.remove()
    }

    /** Troca pelo menu do ícone (o editor já abre aqui, então não precisa revisar no próximo toque). */
    private fun selectMode(newMode: TranslationMode) {
        closeMenu()
        applyMode(newMode)
        settings.translationMode = newMode
        when (newMode) {
            // Sempre mostra a área (a salva, se houver) pra conferir/ajustar antes de traduzir.
            TranslationMode.FIXED_REGION -> openEditor()
            TranslationMode.FULL_SCREEN -> showHint(R.string.full_screen_hint)
            TranslationMode.REGION -> Unit
        }
    }

    // --- Tradução ---

    private fun translateRegion(region: Rect) {
        translationJob?.cancel()
        translationJob = scope.launch {
            val shot = captureWithoutOverlays() ?: return@launch
            val bounds = Rect(0, 0, shot.width, shot.height)
            if (!bounds.intersect(region)) return@launch
            val crop = Bitmap.createBitmap(shot, bounds.left, bounds.top, bounds.width(), bounds.height())
            showCard(region)
            // "Tentar de novo" reaproveita a mesma captura (a página pode já ter rolado).
            lastRun = { runOnCard { container.regionTranslator.translate(crop) } }
            container.regionTranslator.translate(crop).collect { card.value = it }
        }
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

    /** Tela cheia: acha os balões, traduz todos numa chamada e põe cada tradução em cima do seu. */
    private fun translateFullScreen() {
        translationJob?.cancel()
        closeCard()
        screenBlocks?.remove()
        translationJob = scope.launch {
            val shot = captureWithoutOverlays() ?: return@launch
            showHint(R.string.full_screen_reading, autoHide = false)
            val (statusBar, navigationBar) = systemBarHeights(this@FloatingService)
            val result = try {
                container.screenTranslator.translate(shot, statusBar, navigationBar)
            } catch (e: Exception) {
                ScreenTranslator.Result.Failed(e.toUiText())
            } finally {
                hintOverlay?.remove()
                hintOverlay = null
            }
            when (result) {
                is ScreenTranslator.Result.Blocks -> screenBlocks?.show(result.blocks, topLimit = statusBar)
                is ScreenTranslator.Result.Failed -> toast(result.message.resolve(this@FloatingService))
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

    /** Esconde tudo que é nosso, captura e mostra de novo (senão o OCR lê o próprio cartão). */
    private suspend fun captureWithoutOverlays(): Bitmap? {
        val capturer = capturer ?: return null
        icon?.setHidden(true)
        cardOverlay?.setHidden(true)
        return try {
            capturer.capture()
        } catch (e: Exception) {
            card.value = TranslationCardState.Failed(e.toUiText())
            null
        } finally {
            icon?.setHidden(false)
            cardOverlay?.setHidden(false)
        }
    }

    // --- Cartão ---

    /** Cartão logo abaixo da área (ou acima, se não couber embaixo); dá pra arrastar depois. */
    private fun showCard(region: Rect) {
        val screen = screenBounds(this)
        val below = region.bottom < screen.height() * 0.55
        val params = overlayParams(
            screen.width(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.START or if (below) Gravity.TOP else Gravity.BOTTOM,
        ).apply { y = if (below) region.bottom + MARGIN_PX else screen.height() - region.top + MARGIN_PX }
        cardOverlay?.remove()
        cardParams = params
        val controls = FloatingCardControls(
            onDrag = ::dragCard,
            onReselect = if (mode == TranslationMode.FIXED_REGION) ::openEditor else null,
            onOptions = ::openOptions,
            onPickModel = ::openModelPicker,
        )
        cardOverlay = ComposeOverlay(this, windows, backHandler = backClosesBubbles()) {
            FloatingTranslationCard(
                card, container.libraryRepository.library, container.translationActions.options, cardActions, controls,
                opacity = settings.cardOpacity,
                backCloses = settings.backClosesBubbles,
            )
        }.also { it.show(params) }
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
    private fun openSheet(onBack: () -> Unit = ::hideSheet, content: @Composable (bottomInset: Dp) -> Unit) {
        if (sheet != null) return
        val screen = screenBounds(this)
        val bottomInset = (systemBarHeights(this).second / resources.displayMetrics.density).dp
        sheetOpen.value = true
        sheet = ComposeOverlay(this, windows, backHandler = onBack) { content(bottomInset) }
            .also { it.show(overlayParams(screen.width(), screen.height())) }
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
                speed = speed,
                onSelectMode = { newMode ->
                    hideSheet()
                    if (newMode != mode) selectMode(newMode)
                },
                onBackClosesChange = settings::setBackClosesBubbles,
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

    // --- Região fixa ---

    private fun openEditor() {
        reviewFixedRegion = false
        closeMenu()
        closeEditor()
        closeCard()
        editor = FixedRegionEditor(
            this, windows, settings.fixedRegion,
            onConfirm = { region ->
                closeEditor()
                settings.fixedRegion = region
                translateRegion(region)
            },
            onCancel = ::closeEditor,
        ).also { it.show() }
    }

    private fun closeEditor() {
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
        private const val SPEED_SAMPLE = "Hey! Let's read some webtoons together."

        /** Liga o tradutor com a permissão de captura que o usuário acabou de dar. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(
                Intent(context, FloatingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_DATA, data)
            )
        }

        fun stopIntent(context: Context): Intent = Intent(context, FloatingService::class.java).setAction(ACTION_STOP)

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }
    }
}
