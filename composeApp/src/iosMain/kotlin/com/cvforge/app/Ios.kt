package com.cvforge.app

import androidx.compose.ui.window.ComposeUIViewController
import com.cvforge.app.di.initKoin
import platform.UIKit.UIViewController

private var started = false

/** Chamado pelo Swift (iOSApp.init) com a URL da API vinda do Info.plist. */
fun initKoinIos(apiBaseUrl: String) {
    if (started) return
    started = true
    initKoin(apiBaseUrl)
}

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
