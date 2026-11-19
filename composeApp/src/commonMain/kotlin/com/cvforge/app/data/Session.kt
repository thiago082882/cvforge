package com.cvforge.app.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppConfig(val apiBaseUrl: String)

data class Session(val token: String, val userId: String, val name: String, val email: String)

enum class ThemeMode(val label: String) { System("Sistema"), Light("Claro"), Dark("Escuro") }

/**
 * Sessão e preferências locais. Usa multiplatform-settings (SharedPreferences / NSUserDefaults /
 * Preferences / localStorage). Observação: o token fica em armazenamento comum da plataforma;
 * para endurecer, troque por Keystore (Android) / Keychain (iOS).
 */
class SessionStore(private val settings: Settings) {
    private val _session = MutableStateFlow(read())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _theme = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == settings.getStringOrNull(K_THEME) } ?: ThemeMode.System,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _onboarding = MutableStateFlow(settings.getBoolean(K_ONBOARDING, false))
    val onboarding: StateFlow<Boolean> = _onboarding.asStateFlow()

    val token: String? get() = _session.value?.token

    fun save(session: Session, isNewUser: Boolean) {
        settings.putString(K_TOKEN, session.token)
        settings.putString(K_ID, session.userId)
        settings.putString(K_NAME, session.name)
        settings.putString(K_EMAIL, session.email)
        settings.putBoolean(K_ONBOARDING, isNewUser)
        _onboarding.value = isNewUser
        _session.value = session
    }

    fun finishOnboarding() {
        settings.putBoolean(K_ONBOARDING, false)
        _onboarding.value = false
    }

    fun setTheme(mode: ThemeMode) {
        settings.putString(K_THEME, mode.name)
        _theme.value = mode
    }

    fun clear() {
        listOf(K_TOKEN, K_ID, K_NAME, K_EMAIL, K_ONBOARDING).forEach(settings::remove)
        _onboarding.value = false
        _session.value = null
    }

    private fun read(): Session? {
        val token = settings.getStringOrNull(K_TOKEN) ?: return null
        return Session(
            token = token,
            userId = settings.getStringOrNull(K_ID) ?: return null,
            name = settings.getStringOrNull(K_NAME) ?: return null,
            email = settings.getStringOrNull(K_EMAIL) ?: return null,
        )
    }

    private companion object {
        const val K_TOKEN = "token"
        const val K_ID = "user_id"
        const val K_NAME = "user_name"
        const val K_EMAIL = "user_email"
        const val K_THEME = "theme"
        const val K_ONBOARDING = "onboarding"
    }
}
