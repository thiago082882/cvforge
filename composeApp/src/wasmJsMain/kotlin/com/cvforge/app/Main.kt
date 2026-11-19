package com.cvforge.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.cvforge.app.di.initKoin
import kotlinx.browser.document

/** URL da API definida em index.html (window.CVFORGE_API_URL) — troca sem recompilar. */
@JsFun("() => (window.CVFORGE_API_URL || 'http://localhost:8080')")
private external fun apiUrl(): String

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin(apiUrl())
    ComposeViewport(document.body!!) { App() }
}
