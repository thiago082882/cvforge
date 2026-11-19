package com.cvforge.app.ui.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cvforge.app.data.ApiException
import com.cvforge.app.data.CurriculumRepository
import com.cvforge.app.data.userMessage
import com.cvforge.app.ui.components.AppField
import com.cvforge.app.ui.components.ContentColumn
import com.cvforge.app.ui.components.ErrorBanner
import com.cvforge.app.ui.components.LoadingButton
import com.cvforge.app.ui.components.ScreenHeader
import com.cvforge.app.ui.components.SectionCard
import com.cvforge.contract.CvLanguage
import com.cvforge.contract.ErrorDto
import com.cvforge.contract.GenerateRequest
import com.cvforge.contract.Limits
import com.cvforge.contract.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class GenerateUiState(
    val jobUrl: String = "",
    val jobText: String = "",
    val language: CvLanguage = CvLanguage.PT_BR,
    val loading: Boolean = false,
    val error: String? = null,
    val profileIncomplete: Boolean = false,
    val createdId: String? = null,
)

class GenerateViewModel(private val repo: CurriculumRepository) : ViewModel() {
    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(jobUrl = v, error = null, profileIncomplete = false) }
    fun onText(v: String) = _state.update { it.copy(jobText = v.take(Limits.JOB_TEXT), error = null, profileIncomplete = false) }
    fun onLanguage(l: CvLanguage) = _state.update { it.copy(language = l) }
    fun consumeNavigation() = _state.update { it.copy(createdId = null, jobUrl = "", jobText = "") }

    fun generate() {
        val s = _state.value
        if (s.loading) return
        val url = s.jobUrl.trim()
        val text = s.jobText.trim()
        when {
            url.isEmpty() && text.isEmpty() -> return fail("Cole o link da vaga ou o texto da descrição.")
            text.isEmpty() -> Validators.jobUrl(url)?.let { return fail(it) }
            text.length < 80 -> return fail("A descrição está muito curta. Cole o texto completo da vaga.")
        }
        _state.update { it.copy(loading = true, error = null, profileIncomplete = false) }
        viewModelScope.launch {
            repo.generate(GenerateRequest(url.ifEmpty { null }, text.ifEmpty { null }, s.language)).fold(
                onSuccess = { cv -> _state.update { it.copy(loading = false, createdId = cv.id) } },
                onFailure = { e ->
                    val incomplete = (e as? ApiException)?.code == ErrorDto.PROFILE_INCOMPLETE
                    _state.update { it.copy(loading = false, error = e.userMessage(), profileIncomplete = incomplete) }
                },
            )
        }
    }

    private fun fail(msg: String) = _state.update { it.copy(error = msg) }
}

@Composable
fun GenerateScreen(onCreated: (String) -> Unit, onOpenProfile: () -> Unit, vm: GenerateViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.createdId) {
        s.createdId?.let { id -> vm.consumeNavigation(); onCreated(id) }
    }

    Column {
        ScreenHeader("Novo currículo", subtitle = "Adaptado à vaga em segundos")
        ContentColumn {
            s.error?.let {
                ErrorBanner(it, actionLabel = if (s.profileIncomplete) "Abrir perfil" else null, onAction = if (s.profileIncomplete) onOpenProfile else null)
            }

            SectionCard("Vaga") {
                AppField(s.jobUrl, vm::onUrl, "Link da vaga", keyboardType = KeyboardType.Uri,
                    hint = "Funciona melhor com páginas públicas. LinkedIn e Indeed costumam bloquear — use o texto abaixo.")
                Text("ou", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppField(s.jobText, vm::onText, "Cole a descrição da vaga", singleLine = false, minLines = 6,
                    hint = "Se preencher o texto, ele tem prioridade sobre o link.")
            }

            SectionCard("Idioma do currículo") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.language == CvLanguage.PT_BR, { vm.onLanguage(CvLanguage.PT_BR) }, { Text("Português") })
                    FilterChip(s.language == CvLanguage.EN, { vm.onLanguage(CvLanguage.EN) }, { Text("English") })
                }
            }

            LoadingButton(if (s.loading) "Analisando a vaga e adaptando seu currículo…" else "Gerar currículo", vm::generate, loading = s.loading)
            Text(
                "A IA só reorganiza e reescreve o que está no seu perfil — ela não inventa experiências, datas nem habilidades. Revise antes de enviar.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
