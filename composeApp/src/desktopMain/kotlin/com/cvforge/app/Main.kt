package com.cvforge.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cvforge.app.di.initKoin
import java.awt.Dimension

fun main() {
    // Aponte para o servidor de produção com:  CVFORGE_API_URL=https://api.seudominio.com
    initKoin(System.getenv("CVFORGE_API_URL") ?: "http://localhost:8080")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "CVForge",
            state = rememberWindowState(size = DpSize(1100.dp, 780.dp)),
        ) {
            window.minimumSize = Dimension(420, 640)
            App()
        }
    }
}
