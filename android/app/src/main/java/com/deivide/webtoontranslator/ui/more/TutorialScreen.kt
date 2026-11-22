package com.deivide.webtoontranslator.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.components.IconTile
import com.deivide.webtoontranslator.ui.theme.AppTheme
import kotlinx.coroutines.launch

private class TutorialPage(val icon: ImageVector, val color: Color, val title: Int, val body: Int)

/** Tutorial curto (abre sozinho na primeira vez e por Mais → Tutorial): uma página por passo. */
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val icons = AppTheme.colors.icons
    val pages = listOf(
        TutorialPage(Icons.Filled.Link, icons.blue, R.string.tutorial_1_title, R.string.tutorial_1_body),
        TutorialPage(Icons.Filled.PlayArrow, icons.green, R.string.tutorial_2_title, R.string.tutorial_2_body),
        TutorialPage(Icons.Filled.AutoMode, icons.orange, R.string.tutorial_3_title, R.string.tutorial_3_body),
        TutorialPage(Icons.Filled.School, icons.purple, R.string.tutorial_4_title, R.string.tutorial_4_body),
        TutorialPage(Icons.Filled.History, icons.sky, R.string.tutorial_5_title, R.string.tutorial_5_body),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    // Voltar volta uma página; na primeira, fecha.
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.tutorial_skip)) }
            else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconTile(page.icon, page.color, size = 72.dp)
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(page.title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(page.body),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == pager.currentPage) 10.dp else 8.dp)
                            .background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                CircleShape,
                            )
                    )
                }
            }
            Button(onClick = {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }) { Text(stringResource(if (last) R.string.tutorial_done else R.string.tutorial_next)) }
        }
    }
}
