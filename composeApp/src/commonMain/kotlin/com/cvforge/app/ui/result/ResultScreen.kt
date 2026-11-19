package com.cvforge.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cvforge.app.data.CurriculumRepository
import com.cvforge.app.data.userMessage
import com.cvforge.app.platform.FileExporter
import com.cvforge.app.ui.components.ErrorBanner
import com.cvforge.app.ui.components.KeywordFlow
import com.cvforge.app.ui.components.LoadingButton
import com.cvforge.app.ui.components.Pill
import com.cvforge.app.ui.components.ScoreRing
import com.cvforge.app.ui.components.ScreenHeader
import com.cvforge.app.ui.components.SectionCard
import com.cvforge.app.ui.components.scoreColor
import com.cvforge.app.ui.components.scoreLabel
import com.cvforge.contract.CurriculumDto
import com.cvforge.contract.CvLanguage
import com.cvforge.contract.CvTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class ResultUiState(
    val loading: Boolean = true,
    val cv: CurriculumDto? = null,
    val template: CvTemplate = CvTemplate.CLASSIC,
    val exporting: Boolean = false,
    val letterLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val deleted: Boolean = false,
)

class ResultViewModel(
    private val id: String,
    private val repo: CurriculumRepository,
    private val exporter: FileExporter,
) : ViewModel() {
    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.get(id).fold(
                onSuccess = { cv -> _state.update { it.copy(loading = false, cv = cv) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    fun setTemplate(t: CvTemplate) = _state.update { it.copy(template = t) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun export(format: String) {
        val cv = _state.value.cv ?: return
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true, error = null) }
        viewModelScope.launch {
            repo.export(id, format, _state.value.template).fold(
                onSuccess = { bytes ->
                    val ext = if (format == "pdf") "pdf" else "txt"
                    val mime = if (format == "pdf") "application/pdf" else "text/plain"
                    runCatching { exporter.export("curriculo-${slug(cv.company)}.$ext", mime, bytes) }.fold(
                        onSuccess = { _state.update { it.copy(exporting = false, message = "Arquivo pronto") } },
                        onFailure = { _state.update { it.copy(exporting = false, error = "Não foi possível salvar o arquivo.") } },
                    )
                },
                onFailure = { e -> _state.update { it.copy(exporting = false, error = e.userMessage()) } },
            )
        }
    }

    fun exportLetter() {
        val cv = _state.value.cv ?: return
        val letter = cv.coverLetter ?: return
        viewModelScope.launch {
            runCatching { exporter.export("carta-${slug(cv.company)}.txt", "text/plain", letter.encodeToByteArray()) }
                .onFailure { _state.update { it.copy(error = "Não foi possível salvar o arquivo.") } }
        }
    }

    fun generateLetter() {
        if (_state.value.letterLoading) return
        _state.update { it.copy(letterLoading = true, error = null) }
        viewModelScope.launch {
            repo.coverLetter(id).fold(
                onSuccess = { cv -> _state.update { it.copy(letterLoading = false, cv = cv) } },
                onFailure = { e -> _state.update { it.copy(letterLoading = false, error = e.userMessage()) } },
            )
        }
    }

    fun delete() {
        viewModelScope.launch {
            repo.delete(id).fold(
                onSuccess = { _state.update { it.copy(deleted = true) } },
                onFailure = { e -> _state.update { it.copy(error = e.userMessage()) } },
            )
        }
    }

    private fun slug(s: String) = s.lowercase().filter { it.isLetterOrDigit() && it.code < 128 }.take(24).ifEmpty { "vaga" }
}

@Composable
fun ResultScreen(id: String, onBack: () -> Unit, vm: ResultViewModel = koinViewModel(parameters = { parametersOf(id) })) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir currículo?") },
            text = { Text("Essa ação não pode ser desfeita.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete() }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = s.cv?.jobTitle ?: "Currículo",
            subtitle = s.cv?.company,
            onBack = onBack,
            actions = { if (s.cv != null) IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "Excluir") } },
        )
        val cv = s.cv
        when {
            s.loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            cv == null -> Column(Modifier.padding(16.dp)) { ErrorBanner(s.error ?: "Não foi possível carregar o currículo.") }
            else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sidePanel = @Composable { SidePanel(cv, s, vm) }
                val paper = @Composable { CvPaper(cv) }
                if (maxWidth >= 900.dp) {
                    // Telas largas (desktop/web/tablet): análise à esquerda, prévia do currículo à direita.
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(0.42f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { sidePanel() }
                        Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) { paper() }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        sidePanel(); paper()
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }
}

@Composable
private fun SidePanel(cv: CurriculumDto, s: ResultUiState, vm: ResultViewModel) {
    s.error?.let { ErrorBanner(it) }

    SectionCard("Aderência à vaga") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(cv.atsScore)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(scoreLabel(cv.atsScore), scoreColor(cv.atsScore).copy(alpha = 0.16f), scoreColor(cv.atsScore))
                Text(
                    "Calculado pelas palavras-chave da vaga que aparecem no seu currículo. É uma estimativa, não a nota de um ATS real.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (cv.keywordsFound.isNotEmpty()) {
            Text("No seu currículo", style = MaterialTheme.typography.labelLarge)
            KeywordFlow(cv.keywordsFound, found = true)
        }
        if (cv.keywordsMissing.isNotEmpty()) {
            HorizontalDivider()
            Text("Pedidas pela vaga e ausentes", style = MaterialTheme.typography.labelLarge)
            KeywordFlow(cv.keywordsMissing, found = false)
            Text(
                "Se você realmente tem alguma dessas habilidades, adicione-a ao seu perfil e gere de novo. Não inclua o que não domina.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionCard("Baixar") {
        Text("Modelo do PDF", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.template == CvTemplate.CLASSIC, { vm.setTemplate(CvTemplate.CLASSIC) }, { Text("Clássico") })
            FilterChip(s.template == CvTemplate.MODERN, { vm.setTemplate(CvTemplate.MODERN) }, { Text("Moderno") })
            FilterChip(s.template == CvTemplate.COMPACT, { vm.setTemplate(CvTemplate.COMPACT) }, { Text("Compacto") })
        }
        LoadingButton("Baixar PDF", { vm.export("pdf") }, loading = s.exporting)
        LoadingButton("Baixar texto (.txt)", { vm.export("txt") }, outlined = true, enabled = !s.exporting)
        Text(
            "PDF em coluna única e texto selecionável — o formato que sistemas de triagem (ATS) leem melhor.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Carta de apresentação") {
        val letter = cv.coverLetter
        if (letter == null) {
            Text("Gere uma carta curta e sincera para esta vaga, baseada no seu currículo.", style = MaterialTheme.typography.bodyMedium)
            LoadingButton("Gerar carta", vm::generateLetter, loading = s.letterLoading)
        } else {
            Text(letter, style = MaterialTheme.typography.bodyMedium)
            LoadingButton("Baixar carta (.txt)", vm::exportLetter, outlined = true)
            LoadingButton("Gerar outra versão", vm::generateLetter, loading = s.letterLoading, outlined = true)
        }
    }
}

/** Prévia em "folha de papel": sempre clara, mesmo no tema escuro — como o PDF final. */
@Composable
private fun CvPaper(cv: CurriculumDto) {
    val ink = Color(0xFF1F2937)
    val muted = Color(0xFF4B5563)
    val accent = Color(0xFF0F7B5F)
    val en = cv.language == CvLanguage.EN
    val c = cv.content

    @Composable
    fun heading(t: String) {
        Text(t.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent, modifier = Modifier.padding(top = 8.dp))
        HorizontalDivider(color = accent.copy(alpha = 0.5f))
    }

    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 3.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineMedium, color = accent)
            Text(
                listOfNotNull(c.email, c.phone, c.city.ifBlank { null }, c.linkedin, c.website).joinToString("  |  "),
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
            if (c.summary.isNotBlank()) { heading(if (en) "Professional Summary" else "Resumo Profissional"); Text(c.summary, style = MaterialTheme.typography.bodyMedium, color = ink) }
            if (c.skills.isNotEmpty()) { heading(if (en) "Skills" else "Habilidades"); Text(c.skills.joinToString("  •  "), style = MaterialTheme.typography.bodyMedium, color = ink) }
            if (c.experiences.isNotEmpty()) {
                heading(if (en) "Experience" else "Experiência Profissional")
                c.experiences.forEach { e ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${e.role} · ${e.company}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ink)
                        Text(e.period, style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    e.bullets.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = ink) }
                }
            }
            if (c.education.isNotEmpty()) {
                heading(if (en) "Education" else "Formação Acadêmica")
                c.education.forEach { ed ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(listOf(ed.degree, ed.fieldOfStudy).filter { it.isNotBlank() }.joinToString(" – ") + " · ${ed.institution}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ink)
                        Text(ed.graduationYear?.toString() ?: if (en) "In progress" else "Em andamento", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        }
    }
}
