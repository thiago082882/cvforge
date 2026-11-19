package com.cvforge.app.ui.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.cvforge.app.data.SessionStore
import com.cvforge.app.ui.generate.GenerateScreen
import com.cvforge.app.ui.home.HomeScreen
import com.cvforge.app.ui.profile.ProfileScreen
import com.cvforge.app.ui.result.ResultScreen
import com.cvforge.app.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.reflect.KClass

@Serializable sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Generate : Route
    @Serializable data object Profile : Route
    @Serializable data object Settings : Route
    @Serializable data class Result(val id: String) : Route
}

private class TopLevel(val route: Route, val type: KClass<out Route>, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Route.Home, Route.Home::class, "Currículos", Icons.Default.Home),
    TopLevel(Route.Generate, Route.Generate::class, "Novo", Icons.Default.Add),
    TopLevel(Route.Profile, Route.Profile::class, "Perfil", Icons.Default.Person),
    TopLevel(Route.Settings, Route.Settings::class, "Ajustes", Icons.Default.Settings),
)

private fun NavHostController.goTopLevel(route: Route) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * Navegação adaptativa: barra inferior em telas estreitas (celular),
 * trilho lateral a partir de 600dp (tablet, desktop, web).
 */
@Composable
fun MainShell() {
    val nav = rememberNavController()
    val store = koinInject<SessionStore>()
    val onboarding by store.onboarding.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showNav = topLevel.any { t -> destination?.hierarchy?.any { it.hasRoute(t.type) } == true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (useRail && showNav) {
                NavigationRail {
                    topLevel.forEach { t ->
                        NavigationRailItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(t.type) } == true,
                            onClick = { nav.goTopLevel(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = nav,
                    // Usuário novo cai direto no perfil.
                    startDestination = if (onboarding) Route.Profile else Route.Home,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<Route.Home> {
                        HomeScreen(onNew = { nav.goTopLevel(Route.Generate) }, onOpen = { nav.navigate(Route.Result(it)) })
                    }
                    composable<Route.Generate> {
                        GenerateScreen(
                            onCreated = { id -> nav.navigate(Route.Result(id)) },
                            onOpenProfile = { nav.goTopLevel(Route.Profile) },
                        )
                    }
                    composable<Route.Profile> {
                        ProfileScreen(
                            firstTime = onboarding,
                            onSaved = {
                                // Lê o valor atual no momento do salvamento (evita corrida com a recomposição).
                                if (store.onboarding.value) {
                                    store.finishOnboarding()
                                    nav.goTopLevel(Route.Generate)
                                }
                            },
                        )
                    }
                    composable<Route.Settings> { SettingsScreen() }
                    composable<Route.Result> { entry ->
                        val route = entry.toRoute<Route.Result>()
                        ResultScreen(id = route.id, onBack = { nav.popBackStack() })
                    }
                }
                if (!useRail && showNav) {
                    NavigationBar {
                        topLevel.forEach { t ->
                            NavigationBarItem(
                                selected = destination?.hierarchy?.any { it.hasRoute(t.type) } == true,
                                onClick = { nav.goTopLevel(t.route) },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}
