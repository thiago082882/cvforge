package com.cvforge.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cvforge.app.data.ProfileRepository
import com.cvforge.app.data.userMessage
import com.cvforge.app.ui.components.AppField
import com.cvforge.app.ui.components.ContentColumn
import com.cvforge.app.ui.components.ErrorBanner
import com.cvforge.app.ui.components.LoadingButton
import com.cvforge.app.ui.components.ScreenHeader
import com.cvforge.app.ui.components.SectionCard
import com.cvforge.contract.EducationDto
import com.cvforge.contract.ExperienceDto
import com.cvforge.contract.Limits
import com.cvforge.contract.ProfileDto
import com.cvforge.contract.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class ExpForm(
    val key: Long,
    val company: String = "", val role: String = "", val start: String = "", val end: String = "",
    val current: Boolean = false, val description: String = "", val skills: String = "",
)

data class EduForm(
    val key: Long,
    val institution: String = "", val degree: String = "", val field: String = "", val year: String = "",
)

data class ProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val city: String = "", val phone: String = "", val linkedin: String = "", val website: String = "",
    val summary: String = "", val skills: String = "",
    val experiences: List<ExpForm> = emptyList(),
    val education: List<EduForm> = emptyList(),
    val error: String? = null,
    val savedAt: Int = 0, // incrementa a cada salvamento — a UI observa para mostrar o aviso
)

class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private var nextKey = 1L

    init { load() }

    private fun load() {
        viewModelScope.launch {
            repo.get().fold(
                onSuccess = { p ->
                    _state.update {
                        if (p == null) it.copy(loading = false, experiences = listOf(ExpForm(nextKey++)))
                        else it.copy(
                            loading = false, city = p.city, phone = p.phone.orEmpty(), linkedin = p.linkedinUrl.orEmpty(),
                            website = p.website.orEmpty(), summary = p.summary, skills = p.skills.joinToString(", "),
                            experiences = p.experiences.map { e ->
                                ExpForm(nextKey++, e.company, e.role, e.startDate, e.endDate.orEmpty(), e.endDate == null, e.description, e.skills.joinToString(", "))
                            },
                            education = p.education.map { e ->
                                EduForm(nextKey++, e.institution, e.degree, e.fieldOfStudy, e.graduationYear?.toString().orEmpty())
                            },
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    fun edit(block: ProfileUiState.() -> ProfileUiState) = _state.update { it.block().copy(error = null) }
    fun addExperience() = _state.update { it.copy(experiences = it.experiences + ExpForm(nextKey++)) }
    fun removeExperience(key: Long) = _state.update { it.copy(experiences = it.experiences.filterNot { e -> e.key == key }) }
    fun updateExperience(key: Long, f: (ExpForm) -> ExpForm) =
        _state.update { it.copy(experiences = it.experiences.map { e -> if (e.key == key) f(e) else e }, error = null) }
    fun addEducation() = _state.update { it.copy(education = it.education + EduForm(nextKey++)) }
    fun removeEducation(key: Long) = _state.update { it.copy(education = it.education.filterNot { e -> e.key == key }) }
    fun updateEducation(key: Long, f: (EduForm) -> EduForm) =
        _state.update { it.copy(education = it.education.map { e -> if (e.key == key) f(e) else e }, error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val years = s.education.map { it.year.trim() }
        if (years.any { it.isNotEmpty() && it.toIntOrNull() == null }) {
            _state.update { it.copy(error = "O ano de conclusão deve ser um número (ex.: 2024)") }
            return
        }
        val dto = ProfileDto(
            city = s.city.trim(),
            phone = s.phone.trim().ifBlank { null },
            linkedinUrl = s.linkedin.trim().ifBlank { null },
            website = s.website.trim().ifBlank { null },
            summary = s.summary.trim(),
            experiences = s.experiences
                .filter { it.company.isNotBlank() || it.role.isNotBlank() || it.description.isNotBlank() }
                .map { e ->
                    ExperienceDto(
                        company = e.company.trim(), role = e.role.trim(), startDate = e.start.trim(),
                        endDate = if (e.current) null else e.end.trim().ifBlank { null },
                        description = e.description.trim(), skills = splitList(e.skills),
                    )
                },
            education = s.education
                .filter { it.institution.isNotBlank() || it.degree.isNotBlank() }
                .map { EducationDto(it.institution.trim(), it.degree.trim(), it.field.trim(), it.year.trim().toIntOrNull()) },
            skills = splitList(s.skills),
        )
        Validators.profile(dto)?.let { msg -> _state.update { it.copy(error = msg) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repo.save(dto).fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, savedAt = it.savedAt + 1) }
                },
                onFailure = { e -> _state.update { it.copy(saving = false, error = e.userMessage()) } },
            )
        }
    }

    private fun splitList(s: String) = s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
}

@Composable
fun ProfileScreen(onSaved: () -> Unit, firstTime: Boolean, vm: ProfileViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.savedAt) { if (s.savedAt > 0) onSaved() }

    Column {
        ScreenHeader(
            title = if (firstTime) "Vamos montar seu perfil" else "Meu perfil",
            subtitle = "Escreva uma vez, reaproveite em todas as vagas",
        )
        ContentColumn {
            s.error?.let { ErrorBanner(it) }

            SectionCard("Contato") {
                AppField(s.city, { v -> vm.edit { copy(city = v) } }, "Cidade / Estado")
                AppField(s.phone, { v -> vm.edit { copy(phone = v) } }, "Telefone (opcional)", keyboardType = KeyboardType.Phone)
                AppField(s.linkedin, { v -> vm.edit { copy(linkedin = v) } }, "LinkedIn (opcional)", keyboardType = KeyboardType.Uri)
                AppField(s.website, { v -> vm.edit { copy(website = v) } }, "GitHub / Portfólio (opcional)", keyboardType = KeyboardType.Uri)
            }

            SectionCard("Resumo profissional") {
                AppField(
                    s.summary, { v -> vm.edit { copy(summary = v) } }, "Quem é você profissionalmente?",
                    singleLine = false, minLines = 4,
                    hint = "${s.summary.length}/${Limits.SUMMARY} — opcional: a IA escreve um a partir do seu perfil",
                )
            }

            SectionCard("Experiência profissional") {
                s.experiences.forEach { e ->
                    key(e.key) {
                        ExperienceEditor(e, onChange = { f -> vm.updateExperience(e.key, f) }, onRemove = { vm.removeExperience(e.key) })
                    }
                }
                OutlinedButton(vm::addExperience, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Text("  Adicionar experiência")
                }
                Text("Sem experiência ainda? Pode deixar vazio e preencher formação e habilidades.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard("Formação") {
                s.education.forEach { e ->
                    key(e.key) {
                        EducationEditor(e, onChange = { f -> vm.updateEducation(e.key, f) }, onRemove = { vm.removeEducation(e.key) })
                    }
                }
                OutlinedButton(vm::addEducation, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Text("  Adicionar formação")
                }
            }

            SectionCard("Habilidades") {
                AppField(s.skills, { v -> vm.edit { copy(skills = v) } }, "Separe por vírgula", singleLine = false, minLines = 2,
                    hint = "Ex.: Kotlin, SQL, Git, Inglês avançado. Só o que você realmente domina.")
            }

            LoadingButton("Salvar perfil", vm::save, loading = s.saving)
        }
    }
}

@Composable
private fun ExperienceEditor(e: ExpForm, onChange: ((ExpForm) -> ExpForm) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.role.ifBlank { "Nova experiência" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onRemove) { Icon(Icons.Default.Delete, "Remover experiência") }
        }
        AppField(e.company, { v -> onChange { it.copy(company = v) } }, "Empresa")
        AppField(e.role, { v -> onChange { it.copy(role = v) } }, "Cargo")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(e.start, { v -> onChange { it.copy(start = v) } }, "Início", Modifier.weight(1f), hint = "MM/AAAA")
            if (!e.current) AppField(e.end, { v -> onChange { it.copy(end = v) } }, "Fim", Modifier.weight(1f), hint = "MM/AAAA")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(e.current, { v -> onChange { it.copy(current = v) } })
            Text("Trabalho aqui atualmente", Modifier.padding(start = 12.dp))
        }
        AppField(e.description, { v -> onChange { it.copy(description = v) } }, "O que você fez? (conte com seus fatos e números reais)", singleLine = false, minLines = 3)
        AppField(e.skills, { v -> onChange { it.copy(skills = v) } }, "Tecnologias usadas (vírgula)")
    }
}

@Composable
private fun EducationEditor(e: EduForm, onChange: ((EduForm) -> EduForm) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.degree.ifBlank { "Nova formação" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onRemove) { Icon(Icons.Default.Delete, "Remover formação") }
        }
        AppField(e.institution, { v -> onChange { it.copy(institution = v) } }, "Instituição")
        AppField(e.degree, { v -> onChange { it.copy(degree = v) } }, "Curso / Grau", hint = "Ex.: Bacharelado, Técnico, Pós-graduação")
        AppField(e.field, { v -> onChange { it.copy(field = v) } }, "Área (opcional)")
        AppField(e.year, { v -> onChange { it.copy(year = v) } }, "Ano de conclusão", keyboardType = KeyboardType.Number, hint = "Deixe vazio se estiver em andamento")
    }
}
