package com.deivide.webtoontranslator.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.home.HomeScreen
import com.deivide.webtoontranslator.ui.more.MoreScreen
import com.deivide.webtoontranslator.ui.connection.ConnectionScreen
import com.deivide.webtoontranslator.ui.voice.VoiceScreen
import com.deivide.webtoontranslator.ui.theme.AppTheme
import com.deivide.webtoontranslator.ui.translations.TranslationsScreen

/** Telas do app. As 3 abas ficam na navegação de baixo; as de detalhe empilham por cima. */
object Routes {
    const val HOME = "home"
    const val TRANSLATIONS = "translations"
    const val MORE = "more"
    const val CONNECTION = "connection"
    const val VOICE = "voice"
}

private enum class Tab(val route: String, val icon: ImageVector, val label: Int) {
    HOME(Routes.HOME, Icons.Filled.Home, R.string.tab_home),
    TRANSLATIONS(Routes.TRANSLATIONS, Icons.Filled.Translate, R.string.tab_translations),
    MORE(Routes.MORE, Icons.Filled.MoreHoriz, R.string.tab_more),
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val openConnection = { navController.navigate(Routes.CONNECTION) }
    val back: () -> Unit = { navController.popBackStack() }
    val tabs = Tab.entries.map { it.route }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // Abrir tela de detalhe desliza pra esquerda (a nova entra pela direita) e voltar desliza
            // pra direita; trocar de aba só faz um fade, como no padrão do Android. O gesto de
            // voltar arrastando da borda (voltar preditivo) usa o mesmo deslizar, acompanhando o
            // dedo (o padrão da biblioteca seria encolher a tela).
            NavHost(
                navController,
                startDestination = Routes.HOME,
                enterTransition = {
                    if (isTabSwitch(tabs)) fadeIn(tween(TAB_FADE_MS))
                    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS))
                },
                exitTransition = {
                    if (isTabSwitch(tabs)) fadeOut(tween(TAB_FADE_MS))
                    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS))
                },
                popEnterTransition = {
                    if (isTabSwitch(tabs)) fadeIn(tween(TAB_FADE_MS))
                    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS))
                },
                popExitTransition = {
                    if (isTabSwitch(tabs)) fadeOut(tween(TAB_FADE_MS))
                    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS))
                },
                predictivePopEnterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS))
                },
                predictivePopExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS))
                },
            ) {
                composable(Routes.HOME) { HomeScreen(onOpenConnection = openConnection) }
                composable(Routes.TRANSLATIONS) { TranslationsScreen() }
                composable(Routes.MORE) {
                    MoreScreen(onOpenConnection = openConnection, onOpenVoice = { navController.navigate(Routes.VOICE) })
                }
                composable(Routes.CONNECTION) { ConnectionScreen(onBack = back) }
                composable(Routes.VOICE) { VoiceScreen(onBack = back) }
            }
        }
        // A barra de baixo só aparece nas 3 abas; telas de detalhe ocupam a tela toda. Entra e sai
        // deslizando, junto com a transição das telas.
        AnimatedVisibility(
            visible = route == null || route in tabs,
            enter = expandVertically(tween(SLIDE_MS)) + fadeIn(tween(SLIDE_MS)),
            exit = shrinkVertically(tween(SLIDE_MS)) + fadeOut(tween(SLIDE_MS)),
        ) {
            BottomBar(route) { tab ->
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}

private const val SLIDE_MS = 300
private const val TAB_FADE_MS = 180

/** Troca entre duas abas de baixo (não é abrir/voltar de uma tela de detalhe). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(tabs: List<String>) =
    initialState.destination.route in tabs && targetState.destination.route in tabs

@Composable
private fun BottomBar(route: String?, onSelect: (Tab) -> Unit) {
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
                    selected = route == tab.route,
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
