package com.cvforge.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cvforge.app.data.CurriculumRepository
import com.cvforge.app.data.SessionStore
import com.cvforge.app.data.userMessage
import com.cvforge.app.ui.components.AppField
import com.cvforge.app.ui.components.EmptyState
import com.cvforge.app.ui.components.ErrorBanner
import com.cvforge.app.ui.components.ScreenHeader
import com.cvforge.app.ui.components.scoreColor
import com.cvforge.contract.CurriculumSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

data class HomeUiState(
    val loading: Boolean = true,
    val items: List<CurriculumSummaryDto> = emptyList(),
    val query: String = "",
    val error: String? = null,
) {
    val filtered: List<CurriculumSummaryDto>
        get() = if (query.isBlank()) items else items.filter {
            it.jobTitle.contains(query, ignoreCase = true) || it.company.contains(query, ignoreCase = true)
        }
}

class HomeViewModel(private val repo: CurriculumRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun onQuery(q: String) = _state.update { it.copy(query = q) }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.list().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, items = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }
}

@Composable
fun HomeScreen(onNew: () -> Unit, onOpen: (String) -> Unit, vm: HomeViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val session by koinInject<SessionStore>().session.collectAsStateWithLifecycle()
    // Recarrega sempre que a tela volta a ser exibida (ex.: após gerar ou excluir um currículo).
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Olá, ${session?.name?.substringBefore(' ').orEmpty()}",
            subtitle = "Seus currículos adaptados",
            actions = {
                IconButton(vm::refresh) { Icon(Icons.Default.Refresh, "Atualizar") }
            },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        s.error?.let { ErrorBanner(it, actionLabel = "Tentar de novo", onAction = vm::refresh) }
                        if (s.items.size > 3) {
                            AppField(s.query, vm::onQuery, "Buscar por cargo ou empresa", trailing = { Icon(Icons.Default.Search, null) })
                        }
                    }
                }
                when {
                    s.loading && s.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    s.items.isEmpty() && s.error == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(Icons.Default.Add, "Nenhum currículo ainda", "Cole o link ou o texto de uma vaga e gere um currículo sob medida.", "Criar meu primeiro currículo", onNew)
                    }
                    else -> items(s.filtered, key = { it.id }) { cv -> CurriculumCard(cv) { onOpen(cv.id) } }
                }
            }
        }
    }
}

@Composable
private fun CurriculumCard(cv: CurriculumSummaryDto, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val color = scoreColor(cv.atsScore)
            Surface(color = color.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${cv.atsScore}", style = MaterialTheme.typography.titleMedium, color = color)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(cv.jobTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cv.company, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cv.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
