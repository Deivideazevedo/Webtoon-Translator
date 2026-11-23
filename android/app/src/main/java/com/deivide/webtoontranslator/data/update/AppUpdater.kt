package com.deivide.webtoontranslator.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Atualização pelo próprio app: confere o último release publicado no GitHub, baixa o APK e abre
 * o instalador do Android (que sempre pede a confirmação do usuário; o Android não deixa um app
 * se atualizar sozinho). Releases em rascunho não aparecem pra API, então só vale o que foi publicado.
 */
class AppUpdater(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        /** Já está na versão mais nova (só depois de procurar pelo botão do Sobre). */
        data object UpToDate : State
        data class Available(val version: String, val apkUrl: String) : State
        data class Downloading(val version: String, val progress: Float) : State
        data class Ready(val version: String, val apk: File) : State
        data class Failed(val version: String?, val apkUrl: String?) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null
    private var checked = false

    private val isDevBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    val currentVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** Confere uma vez por abertura do app ([force]: botão "Procurar atualizações"). */
    fun check(force: Boolean = false) {
        // A versão de desenvolvimento é outro app: o APK do GitHub não a atualizaria.
        if (isDevBuild || (checked && !force) || job?.isActive == true) return
        checked = true
        job = scope.launch {
            if (force) _state.value = State.Checking
            _state.value = try {
                latestRelease()?.takeIf { isNewer(it.first, currentVersion) }
                    ?.let { (version, url) -> State.Available(version, url) }
                    ?: if (force) State.UpToDate else State.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (force) State.Failed(null, null) else State.Idle
            }
        }
    }

    /** "Agora não": some até a próxima abertura do app (e o Sobre continua oferecendo). */
    fun dismiss() {
        if (_state.value is State.Available || _state.value is State.Failed) dismissed = true
    }

    /** Aviso da versão nova fechado nesta abertura do app. */
    var dismissed by mutableStateOf(false)
        private set

    /** Baixa o APK da versão nova (com progresso) e já abre o instalador. */
    fun download() {
        val available = when (val s = _state.value) {
            is State.Available -> s.version to s.apkUrl
            is State.Failed -> (s.version ?: return) to (s.apkUrl ?: return)
            else -> return
        }
        val (version, url) = available
        job?.cancel()
        job = scope.launch {
            _state.value = State.Downloading(version, 0f)
            _state.value = try {
                val apk = fetch(url, File(context.cacheDir, "updates/WebtoonTranslator-$version.apk")) { progress ->
                    _state.value = State.Downloading(version, progress)
                }
                State.Ready(version, apk).also { install(it.apk) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed(version, url)
            }
        }
    }

    /**
     * Abre o instalador. Na primeira vez o Android pede pra permitir "instalar apps desta fonte":
     * abre essa tela e, depois de permitir, é só tocar em "Instalar" de novo.
     */
    fun install(apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** (versão sem o "v", link do APK) do último release publicado; null se não tem APK. */
    private suspend fun latestRelease(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null // nenhum release ainda
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            json.getString("tag_name").removePrefix("v") to apk.getString("browser_download_url")
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetch(url: String, target: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        target.parentFile?.listFiles()?.forEach { if (it != target) it.delete() } // APKs de versões antigas
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                    }
                }
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LATEST_RELEASE = "https://api.github.com/repos/Deivideazevedo/Webtoon-Translator/releases/latest"
        const val TIMEOUT_MS = 15_000

        /** "0.2.1" > "0.2.0" (compara número a número; ignora sufixos como "-beta"). */
        fun isNewer(latest: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(latest)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
