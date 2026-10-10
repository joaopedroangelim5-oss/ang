@file:OptIn(ExperimentalMaterial3Api::class)

package com.angvelda.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.angvelda.app.AppViewModel
import com.angvelda.app.data.Entry
import com.angvelda.app.data.EntryTypes
import com.angvelda.app.data.descendantsOf
import com.angvelda.app.data.flattenTopics
import com.angvelda.app.data.topicPath
import com.angvelda.app.navigateTab

@Composable
private fun FilterMenu(label: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 0.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (k, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; onSelect(k) }) }
        }
    }
}

@Composable
fun LibraryScreen(vm: AppViewModel, nav: NavController) {
    val entries by vm.entries.collectAsState()
    val topics by vm.topics.collectAsState()
    val atts by vm.attachments.collectAsState()
    val prefs = vm.prefs
    val cs = MaterialTheme.colorScheme

    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var topicFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var gridView by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var showCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }

    val attCount = remember(atts) { atts.groupingBy { it.entryId }.eachCount() }
    val allTags = remember(entries) { entries.flatMap { it.tagList() }.distinct().sorted() }

    val filtered = remember(entries, atts, query, typeFilter, topicFilter, tagFilter, sort, showArchived, topics) {
        val q = query.trim().lowercase()
        val attNames = atts.groupBy { it.entryId }.mapValues { (_, v) -> v.joinToString(" ") { it.displayName.lowercase() } }
        val topicIds: Set<String>? = topicFilter?.let { if (it == "none") null else descendantsOf(it, topics) + it }
        val list = entries.filter { e ->
            e.archived == showArchived &&
                (typeFilter == null || e.type == typeFilter) &&
                (topicFilter == null || (if (topicFilter == "none") e.topicId == null else e.topicId in (topicIds ?: emptySet()))) &&
                (tagFilter == null || tagFilter in e.tagList()) &&
                (q.isEmpty() || e.title.lowercase().contains(q) || e.content.lowercase().contains(q) || (attNames[e.id]?.contains(q) == true))
        }
        when (sort) {
            1 -> list.sortedByDescending { it.createdAt }
            2 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            else -> list.sortedByDescending { it.modifiedAt }
        }
    }

    val selecting = selected.isNotEmpty()
    fun toggle(id: String) { if (id in selected) selected.remove(id) else selected.add(id) }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selecionado(s)") },
                    navigationIcon = { IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, contentDescription = "Cancelar seleção") } },
                    actions = {
                        if (selected.size == 1) {
                            val e = entries.firstOrNull { it.id == selected[0] }
                            IconButton(onClick = { vm.focusRequest = selected[0]; selected.clear(); navigateTab(nav, "web") }) { Icon(Icons.Default.Hub, contentDescription = "Ver na teia") }
                            IconButton(onClick = { if (e != null) vm.duplicateEntry(e); selected.clear() }) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar") }
                        }
                        IconButton(onClick = { showMove = true }) { Icon(Icons.Default.Folder, contentDescription = "Mover para tópico") }
                        IconButton(onClick = { vm.setArchived(selected.toList(), !showArchived); selected.clear() }) { Icon(Icons.Default.Archive, contentDescription = if (showArchived) "Desarquivar" else "Arquivar") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Excluir") }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Biblioteca") },
                    actions = {
                        IconButton(onClick = { gridView = !gridView }) { Icon(if (gridView) Icons.Default.Menu else Icons.Default.GridView, contentDescription = "Alternar visualização") }
                        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Configurações") }
                    }
                )
            }
        },
        floatingActionButton = { if (!selecting) FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, contentDescription = "Criar") } },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Pesquisar títulos, textos e nomes de arquivos") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("Todos") })
                EntryTypes.all.forEach { t -> FilterChip(selected = typeFilter == t.key, onClick = { typeFilter = if (typeFilter == t.key) null else t.key }, label = { Text(t.label) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterMenu(
                    "Tópico: " + when (topicFilter) { null -> "todos"; "none" -> "sem tópico"; else -> topicPath(topicFilter, topics) },
                    listOf<Pair<String?, String>>(null to "Todos", "none" to "Sem tópico") + flattenTopics(topics).map { (t, d) -> t.id to ("  ".repeat(d) + t.name) }
                ) { topicFilter = it }
                if (allTags.isNotEmpty()) FilterMenu("Etiqueta: ${tagFilter ?: "todas"}", listOf<Pair<String?, String>>(null to "Todas") + allTags.map { it to "#$it" }) { tagFilter = it }
                FilterMenu("Ordem: " + listOf("modificação", "criação", "A–Z")[sort], listOf("0" to "Última modificação", "1" to "Data de criação", "2" to "Alfabética")) { sort = it?.toIntOrNull() ?: 0 }
                FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text("Arquivados") })
            }

            if (filtered.isEmpty()) {
                if (entries.isEmpty()) EmptyState("Nada guardado ainda", "Toque em + para guardar uma frase, pensamento, ideia, nota ou documento. Você não precisa preencher mais nada.", "Criar primeiro registro", { showCreate = true })
                else EmptyState(if (showArchived) "Nenhum registro arquivado" else "Nenhum resultado", "Tente outro termo ou limpe os filtros.", "Limpar filtros", { query = ""; typeFilter = null; topicFilter = null; tagFilter = null; showArchived = false })
            } else if (gridView) {
                LazyVerticalGrid(
                    GridCells.Adaptive(if (prefs.density == 1) 150.dp else 180.dp),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(filtered, key = { it.id }) { e ->
                        EntryCard(e, attCount[e.id] ?: 0, prefs, e.id in selected,
                            onClick = { if (selecting) toggle(e.id) else nav.navigate(editorRoute(e.id)) }, onLongClick = { toggle(e.id) })
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(if (prefs.density == 1) 6.dp else 10.dp)) {
                    items(filtered, key = { it.id }) { e ->
                        EntryCard(e, attCount[e.id] ?: 0, prefs, e.id in selected,
                            onClick = { if (selecting) toggle(e.id) else nav.navigate(editorRoute(e.id)) }, onLongClick = { toggle(e.id) })
                    }
                }
            }
        }
    }

    if (showCreate) CreateMenu(onPick = { showCreate = false; nav.navigate(editorRoute(type = it)) }, onDismiss = { showCreate = false })

    if (confirmDelete) {
        val ids = selected.toList()
        val nAtt = atts.count { it.entryId in ids }
        ConfirmDialog(
            "Excluir ${ids.size} registro(s)?",
            "Isto remove também $nAtt anexo(s) e todas as conexões desses registros. A ação não pode ser desfeita. Se preferir, use Arquivar.",
            "Excluir", { vm.deleteEntries(ids); selected.clear(); confirmDelete = false }, { confirmDelete = false }
        )
    }
    if (showMove) {
        AlertDialog(
            onDismissRequest = { showMove = false },
            title = { Text("Mover para o tópico") },
            text = {
                LazyColumn(Modifier.padding(0.dp)) {
                    item { ListItem(headlineContent = { Text("Sem tópico") }, modifier = Modifier.clickable2 { vm.moveEntries(selected.toList(), null); selected.clear(); showMove = false }) }
                    items(flattenTopics(topics), key = { it.first.id }) { (t, d) ->
                        ListItem(headlineContent = { Text(t.name) }, modifier = Modifier.padding(start = (d * 16).dp).clickable2 { vm.moveEntries(selected.toList(), t.id); selected.clear(); showMove = false })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMove = false }) { Text("Cancelar") } }
        )
    }
}

private fun Modifier.clickable2(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
