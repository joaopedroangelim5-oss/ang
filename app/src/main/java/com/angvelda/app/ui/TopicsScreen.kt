@file:OptIn(ExperimentalMaterial3Api::class)

package com.angvelda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.angvelda.app.AppViewModel
import com.angvelda.app.data.Topic
import com.angvelda.app.data.descendantsOf
import com.angvelda.app.data.flattenTopics
import com.angvelda.app.data.topicPath

@Composable
private fun TopicDialog(
    initial: Topic?, title: String,
    onSave: (String, String, Int?, String?) -> Unit, onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var desc by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var color by rememberSaveable { mutableStateOf(initial?.color) }
    var icon by rememberSaveable { mutableStateOf(initial?.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Descrição (opcional)") }, modifier = Modifier.fillMaxWidth())
                Text("Cor", style = MaterialTheme.typography.labelMedium)
                ColorPicker(color, true) { color = it }
                Text("Ícone", style = MaterialTheme.typography.labelMedium)
                ChoiceRow(listOf("Nenhum") + EMOJIS, if (icon == null) 0 else EMOJIS.indexOf(icon) + 1) { i -> icon = if (i == 0) null else EMOJIS[i - 1] }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, desc, color, icon) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MoveTopicDialog(topic: Topic, topics: List<Topic>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val blocked = descendantsOf(topic.id, topics) + topic.id
    val options = flattenTopics(topics).filter { it.first.id !in blocked }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover “${topic.name}” para...") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item { ListItem(headlineContent = { Text("Raiz (sem tópico pai)") }, modifier = Modifier.clickable { onPick(null) }) }
                items(options, key = { it.first.id }) { (t, depth) ->
                    ListItem(
                        headlineContent = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.padding(start = (depth * 16).dp).clickable { onPick(t.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DeleteTopicDialog(topic: Topic, topics: List<Topic>, entryCount: Int, childCount: Int, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var moveToOther by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    val options = flattenTopics(topics).filter { it.first.id != topic.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir “${topic.name}”?") },
        text = {
            Column {
                Text("Os registros nunca são excluídos junto com o tópico. Subtópicos ($childCount) sobem um nível.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(4.dp))
                Text("O que fazer com os $entryCount registro(s) deste tópico?", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { moveToOther = false }) {
                    RadioButton(selected = !moveToOther, onClick = { moveToOther = false }); Text("Deixar sem tópico")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { moveToOther = true }) {
                    RadioButton(selected = moveToOther, onClick = { moveToOther = true }); Text("Mover para outro tópico")
                }
                if (moveToOther) {
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(options, key = { it.first.id }) { (t, depth) ->
                            Row(Modifier.fillMaxWidth().padding(start = (depth * 12).dp).clickable { target = t.id }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = target == t.id, onClick = { target = t.id })
                                Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !moveToOther || target != null, onClick = { onConfirm(if (moveToOther) target else null) }) { Text("Excluir tópico") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun TopicsScreen(vm: AppViewModel, nav: NavController) {
    val topics by vm.topics.collectAsState()
    val entries by vm.entries.collectAsState()
    val prefs = vm.prefs
    val cs = MaterialTheme.colorScheme
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var query by rememberSaveable { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var newParent by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Topic?>(null) }
    var moving by remember { mutableStateOf<Topic?>(null) }
    var deleting by remember { mutableStateOf<Topic?>(null) }

    val counts = remember(entries) { entries.filter { !it.archived }.groupingBy { it.topicId }.eachCount() }
    val children = remember(topics) { topics.groupBy { it.parentId } }
    val rows: List<Pair<Topic, Int>> = remember(topics, query, expanded.toMap()) {
        if (query.isNotBlank()) topics.filter { it.name.contains(query, true) || it.description.contains(query, true) }.map { it to 0 }
        else {
            val out = mutableListOf<Pair<Topic, Int>>()
            fun walk(parent: String?, depth: Int) {
                for (t in flattenTopics(topics).filter { it.first.parentId == parent }.map { it.first }) {
                    out.add(t to depth)
                    if (expanded[t.id] == true) walk(t.id, depth + 1)
                }
            }
            walk(null, 0)
            out
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tópicos") },
                actions = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Configurações") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newParent = null; showNew = true }) { Icon(Icons.Default.CreateNewFolder, contentDescription = "Novo tópico") }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Pesquisar tópicos") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(if (prefs.density == 1) 4.dp else 8.dp)) {
                item(key = "none") {
                    Card(Modifier.fillMaxWidth().clickable { nav.navigate("topic/none") }, colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📥", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(10.dp))
                            Text("Sem tópico", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text("${counts[null] ?: 0}", color = cs.onSurfaceVariant)
                        }
                    }
                }
                if (topics.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nenhum tópico ainda", style = MaterialTheme.typography.titleMedium)
                            Text("Crie tópicos e subtópicos para organizar seus registros em hierarquias.", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                            TextButton(onClick = { newParent = null; showNew = true }) { Text("Criar primeiro tópico") }
                        }
                    }
                } else if (rows.isEmpty()) {
                    item(key = "noresult") { Text("Nenhum tópico encontrado para “$query”.", color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                }
                items(rows, key = { it.first.id }) { (t, depth) ->
                    val hasKids = !children[t.id].isNullOrEmpty()
                    val isOpen = expanded[t.id] == true
                    var menu by remember { mutableStateOf(false) }
                    val dot = t.color?.let { Color(it) } ?: cs.primary
                    val inner: @Composable () -> Unit = {
                        Row(Modifier.clickable { nav.navigate("topic/${t.id}") }.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (hasKids && query.isBlank()) {
                                IconButton(onClick = { expanded[t.id] = !isOpen }) { Icon(if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (isOpen) "Recolher" else "Expandir") }
                            } else Spacer(Modifier.size(48.dp))
                            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                            Spacer(Modifier.width(10.dp))
                            if (t.icon != null) { Text(t.icon, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(6.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${counts[t.id] ?: 0}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mais ações") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Novo subtópico") }, onClick = { menu = false; newParent = t.id; showNew = true })
                                    DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; editing = t })
                                    DropdownMenuItem(text = { Text("Mover") }, onClick = { menu = false; moving = t })
                                    DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; deleting = t })
                                }
                            }
                        }
                    }
                    Box(Modifier.padding(start = (depth * 20).dp)) {
                        if (prefs.topicStyle == 1) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) { inner() }
                        else inner()
                    }
                }
            }
        }
    }

    if (showNew) {
        TopicDialog(null, if (newParent == null) "Novo tópico" else "Novo subtópico",
            onSave = { n, d, c, i -> vm.createTopic(n, d, newParent, c, i); newParent?.let { expanded[it] = true }; showNew = false },
            onDismiss = { showNew = false })
    }
    editing?.let { t ->
        TopicDialog(t, "Editar tópico",
            onSave = { n, d, c, i -> vm.updateTopic(t.copy(name = n.trim(), description = d.trim(), color = c, icon = i)); editing = null },
            onDismiss = { editing = null })
    }
    moving?.let { t -> MoveTopicDialog(t, topics, onPick = { vm.moveTopic(t.id, it); moving = null }, onDismiss = { moving = null }) }
    deleting?.let { t ->
        DeleteTopicDialog(t, topics, counts[t.id] ?: 0, children[t.id]?.size ?: 0,
            onConfirm = { target -> vm.deleteTopic(t.id, target); deleting = null }, onDismiss = { deleting = null })
    }
}

@Composable
fun TopicDetailScreen(vm: AppViewModel, nav: NavController, topicId: String) {
    val topics by vm.topics.collectAsState()
    val entries by vm.entries.collectAsState()
    val atts by vm.attachments.collectAsState()
    val cs = MaterialTheme.colorScheme
    val isNone = topicId == "none"
    val topic = topics.firstOrNull { it.id == topicId }
    val list = remember(entries, topicId) { entries.filter { !it.archived && (if (isNone) it.topicId == null else it.topicId == topicId) } }
    val subs = topics.filter { it.parentId == topicId }
    val title = if (isNone) "Sem tópico" else topic?.name ?: "Tópico"
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, contentDescription = "Novo registro neste tópico") }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (topic != null) {
                item(key = "path") { Text(topicPath(topic.id, topics), color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                if (topic.description.isNotBlank()) item(key = "desc") { Text(topic.description) }
            }
            if (subs.isNotEmpty()) {
                item(key = "subs") {
                    ChoiceRow(subs.map { it.name }, -1) { nav.navigate("topic/${subs[it].id}") }
                }
            }
            if (list.isEmpty()) {
                item(key = "empty") {
                    Text("Nenhum registro aqui ainda. Toque em + para criar o primeiro já associado a este tópico.", color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(list, key = { it.id }) { e ->
                EntryCard(e, atts.count { it.entryId == e.id }, vm.prefs, false, { nav.navigate(editorRoute(e.id)) }, { nav.navigate(editorRoute(e.id)) })
            }
        }
    }
    if (showCreate) CreateMenu(
        onPick = { showCreate = false; nav.navigate(editorRoute(topicId = if (isNone) null else topicId, type = it)) },
        onDismiss = { showCreate = false }
    )
}
