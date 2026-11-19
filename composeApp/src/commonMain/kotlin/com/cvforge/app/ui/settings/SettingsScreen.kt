package com.cvforge.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cvforge.app.data.AuthRepository
import com.cvforge.app.data.SessionStore
import com.cvforge.app.data.ThemeMode
import com.cvforge.app.data.userMessage
import com.cvforge.app.ui.components.ContentColumn
import com.cvforge.app.ui.components.ErrorBanner
import com.cvforge.app.ui.components.LoadingButton
import com.cvforge.app.ui.components.ScreenHeader
import com.cvforge.app.ui.components.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class SettingsUiState(val deleting: Boolean = false, val error: String? = null)

class SettingsViewModel(private val auth: AuthRepository, val store: SessionStore) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun logout() = auth.logout()

    fun deleteAccount() {
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            auth.deleteAccount().onFailure { e -> _state.update { it.copy(deleting = false, error = e.userMessage()) } }
            // Sucesso: a sessão é limpa e o App volta para a tela de login.
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val session by vm.store.session.collectAsStateWithLifecycle()
    val theme by vm.store.theme.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir conta?") },
            text = { Text("Seu perfil e todos os currículos serão apagados de forma permanente.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteAccount() }) { Text("Excluir tudo", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    Column {
        ScreenHeader("Ajustes")
        ContentColumn {
            s.error?.let { ErrorBanner(it) }

            SectionCard("Conta") {
                Text(session?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(session?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LoadingButton("Sair", vm::logout, outlined = true)
            }

            SectionCard("Aparência") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(theme == m, { vm.store.setTheme(m) }, { Text(m.label) })
                    }
                }
            }

            SectionCard("Privacidade") {
                Text(
                    "Para gerar um currículo, enviamos ao provedor de IA (Groq) o conteúdo do seu perfil — sem nome, e-mail, telefone ou links — e o texto da vaga. " +
                        "Você pode apagar todos os seus dados a qualquer momento.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LoadingButton("Excluir minha conta e dados", { confirmDelete = true }, loading = s.deleting, outlined = true)
            }

            Text(
                "CVForge é gratuito, sem planos pagos e sem limite de currículos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
