package com.deivide.webtoontranslator

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.data.settings.ThemeMode
import com.deivide.webtoontranslator.ui.navigation.AppNavHost
import com.deivide.webtoontranslator.ui.theme.WebtoonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as WebtoonApp).container.settingsRepository
        setContent {
            val mode by settings.theme.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Ícones da barra de status/navegação claros no tema escuro e escuros no claro.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            WebtoonTheme(mode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavHost()
                }
            }
        }
    }
}
