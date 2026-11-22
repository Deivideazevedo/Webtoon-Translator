package com.deivide.webtoontranslator.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.ui.components.SectionCard
import java.text.DateFormat
import java.util.Date

/**
 * Erros recentes (Mais → Erros recentes): as últimas falhas (API, OCR) com data e o detalhe
 * técnico, pra investigar quando uma tradução falha. Tocar num erro copia o detalhe.
 */
@Composable
fun ErrorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val log = remember { (context.applicationContext as WebtoonApp).container.errorLog }
    var entries by remember { mutableStateOf(log.read()) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

    DetailScaffold(stringResource(R.string.more_errors), onBack) {
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.errors_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
            return@DetailScaffold
        }
        Text(
            stringResource(R.string.errors_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        SectionCard(padding = false) {
            entries.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier
                        .clickable {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("erro", "${e.context}: ${e.message}"))
                            Toast.makeText(context, R.string.errors_copied, Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp)
                ) {
                    Text("${format.format(Date(e.timeMs))} · ${e.context}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text(e.message, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 6, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        TextButton(onClick = { log.clear(); entries = emptyList() }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.errors_clear), color = MaterialTheme.colorScheme.error)
        }
    }
}
