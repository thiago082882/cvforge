package com.cvforge.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cvforge.app.data.SessionStore
import com.cvforge.app.ui.auth.AuthScreen
import com.cvforge.app.ui.shell.MainShell
import com.cvforge.app.ui.theme.CvForgeTheme
import org.koin.compose.koinInject

/** Raiz compartilhada por Android, iOS, Desktop e Web. */
@Composable
fun App() {
    val store = koinInject<SessionStore>()
    val session by store.session.collectAsStateWithLifecycle()
    val theme by store.theme.collectAsStateWithLifecycle()

    CvForgeTheme(theme) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            // Login/logout (inclusive sessão expirada) trocam de tela só pelo estado da sessão.
            if (session == null) AuthScreen() else MainShell()
        }
    }
}
