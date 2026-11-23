package com.deivide.webtoontranslator.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.ui.connection.ConnectionScreen
import com.deivide.webtoontranslator.ui.home.HomeScreen
import com.deivide.webtoontranslator.ui.more.AboutScreen
import com.deivide.webtoontranslator.ui.more.AppearanceScreen
import com.deivide.webtoontranslator.ui.more.BackupScreen
import com.deivide.webtoontranslator.ui.more.BatteryScreen
import com.deivide.webtoontranslator.ui.more.ErrorsScreen
import com.deivide.webtoontranslator.ui.more.MoreScreen
import com.deivide.webtoontranslator.ui.more.StorageScreen
import com.deivide.webtoontranslator.ui.more.TutorialScreen
import com.deivide.webtoontranslator.ui.theme.AppTheme
import com.deivide.webtoontranslator.ui.translations.TranslationsScreen
import com.deivide.webtoontranslator.ui.voice.VoiceScreen
import kotlinx.coroutines.launch

/**
 * Telas do app. As 3 abas (Inicial, Traduções, Mais) ficam juntas numa tela só, lado a lado, e
 * as de detalhe empilham por cima.
 */
object Routes {
    const val TABS = "tabs"
    const val CONNECTION = "connection"
    const val VOICE = "voice"
    const val APPEARANCE = "appearance"
    const val BACKUP = "backup"
    const val STORAGE = "storage"
    const val ERRORS = "errors"
    const val BATTERY = "battery"
    const val ABOUT = "about"
    const val TUTORIAL = "tutorial"
}

private enum class Tab(val icon: ImageVector, val label: Int) {
    HOME(Icons.Filled.Home, R.string.tab_home),
    TRANSLATIONS(Icons.Filled.Translate, R.string.tab_translations),
    MORE(Icons.Filled.MoreHoriz, R.string.tab_more),
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val openConnection = { navController.navigate(Routes.CONNECTION) }
    val back: () -> Unit = { navController.popBackStack() }
    // As abas: arrastar pros lados troca de aba (e a barra de baixo acompanha).
    val pager = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    // Primeira abertura: o tutorial abre por cima da Inicial (fechar volta pra ela).
    val settings = (LocalContext.current.applicationContext as WebtoonApp).container.settingsRepository
    LaunchedEffect(Unit) {
        if (!settings.tutorialSeen) {
            settings.tutorialSeen = true
            navController.navigate(Routes.TUTORIAL)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // Abrir tela de detalhe desliza pra esquerda (a nova entra pela direita) e voltar desliza
            // pra direita. O gesto de voltar arrastando da borda (voltar preditivo) usa o mesmo
            // deslizar, acompanhando o dedo (o padrão da biblioteca seria encolher a tela).
            NavHost(
                navController,
                startDestination = Routes.TABS,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) },
                predictivePopEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS))
                },
                predictivePopExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS))
                },
            ) {
                composable(Routes.TABS) {
                    // Voltar numa aba que não é a Inicial volta pra Inicial (e só daí sai do app).
                    BackHandler(enabled = pager.currentPage != Tab.HOME.ordinal) {
                        scope.launch { pager.animateScrollToPage(Tab.HOME.ordinal) }
                    }
                    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                        when (Tab.entries[page]) {
                            Tab.HOME -> HomeScreen(onOpenConnection = openConnection)
                            Tab.TRANSLATIONS -> TranslationsScreen()
                            Tab.MORE -> MoreScreen(onOpen = { navController.navigate(it) })
                        }
                    }
                }
                composable(Routes.CONNECTION) { ConnectionScreen(onBack = back) }
                composable(Routes.VOICE) { VoiceScreen(onBack = back) }
                composable(Routes.APPEARANCE) { AppearanceScreen(onBack = back) }
                composable(Routes.BACKUP) { BackupScreen(onBack = back) }
                composable(Routes.STORAGE) { StorageScreen(onBack = back) }
                composable(Routes.ERRORS) { ErrorsScreen(onBack = back) }
                composable(Routes.BATTERY) { BatteryScreen(onBack = back) }
                composable(Routes.ABOUT) { AboutScreen(onBack = back) }
                composable(Routes.TUTORIAL) { TutorialScreen(onDone = back) }
            }
        }
        // A barra de baixo só aparece nas 3 abas; telas de detalhe ocupam a tela toda. Entra e sai
        // deslizando, junto com a transição das telas.
        AnimatedVisibility(
            visible = route == null || route == Routes.TABS,
            enter = expandVertically(tween(SLIDE_MS)) + fadeIn(tween(SLIDE_MS)),
            exit = shrinkVertically(tween(SLIDE_MS)) + fadeOut(tween(SLIDE_MS)),
        ) {
            BottomBar(selected = Tab.entries[pager.targetPage]) { tab ->
                scope.launch { pager.animateScrollToPage(tab.ordinal) }
            }
        }
    }
}

private const val SLIDE_MS = 300

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = AppTheme.colors.navigationBar,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding(),
            windowInsets = WindowInsets(0),
        ) {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
