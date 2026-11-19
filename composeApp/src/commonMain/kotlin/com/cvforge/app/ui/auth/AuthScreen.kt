package com.cvforge.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cvforge.app.data.AuthRepository
import com.cvforge.app.data.userMessage
import com.cvforge.app.ui.components.AppField
import com.cvforge.app.ui.components.ContentColumn
import com.cvforge.app.ui.components.ErrorBanner
import com.cvforge.app.ui.components.LoadingButton
import com.cvforge.contract.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class AuthUiState(
    val registerMode: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
)

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v, nameError = null, error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, emailError = null, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, passwordError = null, error = null) }
    fun toggleShowPassword() = _state.update { it.copy(showPassword = !it.showPassword) }
    fun toggleMode() = _state.update { it.copy(registerMode = !it.registerMode, error = null, nameError = null, emailError = null, passwordError = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val nameErr = if (s.registerMode) Validators.name(s.name) else null
        val emailErr = Validators.email(s.email)
        val passErr = if (s.registerMode) Validators.password(s.password) else if (s.password.isEmpty()) "Informe a senha" else null
        if (nameErr != null || emailErr != null || passErr != null) {
            _state.update { it.copy(nameError = nameErr, emailError = emailErr, passwordError = passErr) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.registerMode) repo.register(s.name, s.email, s.password) else repo.login(s.email, s.password)
            // Sucesso: o SessionStore emite a nova sessão e o App troca de tela sozinho.
            _state.update { it.copy(loading = false, error = result.exceptionOrNull()?.userMessage()) }
        }
    }
}

@Composable
fun AuthScreen(vm: AuthViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    ContentColumn(maxWidth = 460.dp) {
        Spacer(Modifier.height(32.dp))
        Text("CVForge", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Currículos adaptados a cada vaga, com IA. 100% gratuito.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(if (s.registerMode) "Criar conta" else "Entrar", style = MaterialTheme.typography.titleLarge)

        s.error?.let { ErrorBanner(it) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.registerMode) AppField(s.name, vm::onName, "Nome completo", error = s.nameError)
            AppField(s.email, vm::onEmail, "E-mail", error = s.emailError, keyboardType = KeyboardType.Email)
            AppField(
                value = s.password, onValueChange = vm::onPassword, label = "Senha", error = s.passwordError,
                hint = if (s.registerMode) "Mínimo de 8 caracteres" else null,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (s.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = { TextButton(vm::toggleShowPassword) { Text(if (s.showPassword) "Ocultar" else "Mostrar") } },
            )
        }

        LoadingButton(if (s.registerMode) "Criar conta" else "Entrar", vm::submit, loading = s.loading)
        TextButton(vm::toggleMode) {
            Text(if (s.registerMode) "Já tenho conta — entrar" else "Não tenho conta — cadastrar")
        }
        Text(
            "Ao gerar um currículo, o conteúdo do seu perfil (sem nome, e-mail ou telefone) e o texto da vaga são enviados ao provedor de IA (Groq) para processamento.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
