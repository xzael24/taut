package com.taut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.taut.app.ui.navigation.TautNavHost
import com.taut.app.ui.theme.TautTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity architecture.
 * All navigation handled by Jetpack Compose Navigation.
 *
 * Architecture follows §9 module structure from architecture.md:
 * - Single Activity (Compose)
 * - NavHost for all screen navigation
 * - Hilt for DI
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TautTheme {
                TautApp()
            }
        }
    }
}

@Composable
fun TautApp() {
    TautNavHost(modifier = Modifier.fillMaxSize())
}
