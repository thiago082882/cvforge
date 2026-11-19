package com.cvforge.app.di

import com.cvforge.app.data.ApiClient
import com.cvforge.app.data.AppConfig
import com.cvforge.app.data.AuthRepository
import com.cvforge.app.data.CurriculumRepository
import com.cvforge.app.data.ProfileRepository
import com.cvforge.app.data.SessionStore
import com.cvforge.app.platform.platformModule
import com.cvforge.app.ui.auth.AuthViewModel
import com.cvforge.app.ui.generate.GenerateViewModel
import com.cvforge.app.ui.home.HomeViewModel
import com.cvforge.app.ui.profile.ProfileViewModel
import com.cvforge.app.ui.result.ResultViewModel
import com.cvforge.app.ui.settings.SettingsViewModel
import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val appModule = module {
    single<Settings> { Settings() }
    single { SessionStore(get()) }
    single { ApiClient(get(), get()) }
    single { AuthRepository(get(), get()) }
    single { ProfileRepository(get()) }
    single { CurriculumRepository(get()) }

    viewModelOf(::AuthViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::GenerateViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { params -> ResultViewModel(params.get(), get(), get()) }
}

/** Chamado uma vez por plataforma, antes de abrir a UI. [apiBaseUrl] vem de cada entrypoint. */
fun initKoin(apiBaseUrl: String, extra: KoinApplication.() -> Unit = {}) {
    startKoin {
        extra()
        modules(module { single { AppConfig(apiBaseUrl) } }, platformModule, appModule)
    }
}
