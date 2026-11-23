package com.deivide.webtoontranslator.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.components.rememberAppUpdater
import com.deivide.webtoontranslator.data.update.AppUpdater
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.SystemUpdate
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SectionTitle
import com.deivide.webtoontranslator.ui.components.SettingRow
import com.deivide.webtoontranslator.ui.theme.AppTheme

private const val REPOSITORY = "https://github.com/Deivideazevedo/Webtoon-Translator"

/** Sobre e licenças (Mais → Suporte): versão, licença GPL, código-fonte e créditos. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val icons = AppTheme.colors.icons
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    DetailScaffold(stringResource(R.string.more_about), onBack) {
        SectionCard(padding = false) {
            SettingRow(
                icon = Icons.Filled.Info,
                color = icons.blue,
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.about_version, version),
            )
            UpdateRow()
            SettingRow(
                icon = Icons.Filled.Gavel,
                color = icons.purple,
                title = stringResource(R.string.about_license),
                subtitle = stringResource(R.string.about_license_desc),
                divider = true,
                onClick = { open("https://www.gnu.org/licenses/gpl-3.0.html") },
            )
            SettingRow(
                icon = Icons.Filled.Code,
                color = icons.green,
                title = stringResource(R.string.about_source),
                subtitle = REPOSITORY.removePrefix("https://"),
                divider = true,
                onClick = { open(REPOSITORY) },
            )
        }
        SectionTitle(stringResource(R.string.about_credits))
        SectionCard {
            Text(stringResource(R.string.about_credits_text), fontSize = 13.sp, lineHeight = 19.sp)
        }
        Text(
            stringResource(R.string.about_free),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )
    }
}

/** "Procurar atualizações": confere o GitHub na hora e, se houver versão nova, baixa e instala. */
@Composable
private fun UpdateRow() {
    val updater = rememberAppUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    val subtitle = when (val s = state) {
        AppUpdater.State.Checking -> stringResource(R.string.update_checking)
        AppUpdater.State.UpToDate -> stringResource(R.string.update_up_to_date)
        is AppUpdater.State.Available -> stringResource(R.string.update_available, s.version)
        is AppUpdater.State.Downloading -> stringResource(R.string.update_downloading, (s.progress * 100).toInt())
        is AppUpdater.State.Ready -> stringResource(R.string.update_install)
        is AppUpdater.State.Failed -> stringResource(R.string.update_failed)
        AppUpdater.State.Idle -> stringResource(R.string.update_check_desc)
    }
    SettingRow(
        icon = Icons.Filled.SystemUpdate,
        color = AppTheme.colors.icons.green,
        title = stringResource(R.string.update_check),
        subtitle = subtitle,
        divider = true,
        onClick = {
            when (val s = state) {
                is AppUpdater.State.Available -> updater.download()
                is AppUpdater.State.Ready -> updater.install(s.apk)
                is AppUpdater.State.Failed -> if (s.version != null) updater.download() else updater.check(force = true)
                is AppUpdater.State.Downloading, AppUpdater.State.Checking -> Unit
                else -> updater.check(force = true)
            }
        },
    )
}
