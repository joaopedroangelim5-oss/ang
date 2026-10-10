@file:OptIn(ExperimentalMaterial3Api::class)

package com.angvelda.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.angvelda.app.data.Attachment
import com.angvelda.app.data.Entry
import com.angvelda.app.data.EntryTypes
import com.angvelda.app.data.flattenTopics
import com.angvelda.app.data.topicPath
import com.angvelda.app.navigateTab
import java.util.UUID

@Composable
fun EditorScreen(vm: AppViewModel, nav: NavController, entryIdArg: String, topicArg: String, typeArg: String) {
    val entries by vm.entries.collectAsState()
    val topics by vm.topics.collectAsState()
    val allAtts by vm.attachments.collectAsState()
    val conns by vm.conns.collectAsState()
    val cs = MaterialTheme.colorScheme

    val isNew = entryIdArg == "new"
    val id = rememberSaveable { if (isNew) UUID.randomUUID().toString() else entryIdArg }
    val existing = entries.firstOrNull { it.id == id }
    var persisted by rememberSaveable { mutableStateOf(!isNew) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(if (typeArg.isNotEmpty()) typeArg else "pensamento") }
    var topicId by rememberSaveable { mutableStateOf(if (topicArg.isNotEmpty()) topicArg else null) }
    var tags by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf<Int?>(null) }
    var icon by rememberSaveable { mutableStateOf<String?>(null) }
    var baseline by rememberSaveable { mutableStateOf("") }

    fun snapshot() = listOf(title, content, type, topicId ?: "", tags, color?.toString() ?: "", icon ?: "").joinToString("\u0001")

    LaunchedEffect(existing?.id, loaded) {
        if (!loaded) {
            if (isNew) { baseline = snapshot(); loaded = true }
            else if (existing != null) {
                title = existing.title; content = existing.content; type = existing.type; topicId = existing.topicId
                tags = existing.tags; color = existing.color; icon = existing.icon
                baseline = snapshot(); loaded = true
            }
        }
    }

    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<Entry?>(null) }
    var removeAtt by remember { mutableStateOf<Attachment?>(null) }
    var menu by remember { mutableStateOf(false) }
    var topicMenu by remember { mutableStateOf(false) }

    val dirty = loaded && snapshot() != baseline
    val myAtts = allAtts.filter { it.entryId == id }
    val myConns = conns.filter { it.fromId == id || it.toId == id }

    fun save(onDone: () -> Unit = {}) {
        if (title.isBlank() && content.isBlank()) { android.widget.Toast.makeText(nav.context, "Escreva um título ou conteúdo para salvar.", android.widget.Toast.LENGTH_SHORT).show(); return }
        val now = System.currentTimeMillis()
        val finalTitle = title.trim().ifEmpty { content.trim().lines().first().take(60).ifEmpty { "Sem título" } }
        val cleanTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        val pos = if (existing != null) existing.posX to existing.posY else vm.newPosition()
        val e = Entry(
            id = id, title = finalTitle, content = content, type = type,
            createdAt = existing?.createdAt ?: now, modifiedAt = now,
            topicId = topicId, tags = cleanTags, color = color, icon = icon,
            archived = existing?.archived ?: false, posX = pos.first, posY = pos.second
        )
        vm.saveEntry(e) { ok ->
            if (ok) { title = finalTitle; tags = cleanTags; persisted = true; baseline = snapshot(); onDone() }
        }
    }

    fun ensureSaved(then: () -> Unit) { if (dirty || !persisted) save(then) else then() }
    fun tryBack() { if (dirty) showDiscard = true else nav.popBackStack() }
    BackHandler(enabled = dirty) { showDiscard = true }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.forEach { vm.addAttachment(id, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(if (persisted) "Editar registro" else "Novo registro"); Text(if (dirty) "Alterações não salvas" else if (persisted) "Salvo" else "", style = MaterialTheme.typography.labelSmall, color = if (dirty) cs.primary else cs.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = { tryBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = {
                    IconButton(enabled = dirty || !persisted, onClick = { save() }) { Icon(Icons.Default.Check, contentDescription = "Salvar") }
                    if (persisted && existing != null) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mais ações") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Ver na teia") }, onClick = { menu = false; vm.focusRequest = id; navigateTab(nav, "web") })
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; vm.duplicateEntry(existing) })
                            DropdownMenuItem(text = { Text(if (existing.archived) "Desarquivar" else "Arquivar") }, onClick = { menu = false; vm.setArchived(listOf(id), !existing.archived) })
                            DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; showDelete = true })
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        if (!loaded) {
            EmptyState("Carregando...", "Se o registro não abrir, ele pode ter sido excluído.", "Voltar", { nav.popBackStack() }, Modifier.padding(pad))
        } else Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { content = it }, label = { Text("Conteúdo") }, minLines = 8, modifier = Modifier.fillMaxWidth())

            Text("Tipo", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(EntryTypes.all.map { it.label }, EntryTypes.all.indexOfFirst { it.key == type }) { type = EntryTypes.all[it].key }

            Text("Tópico (opcional)", style = MaterialTheme.typography.labelLarge)
            Column {
                OutlinedButton(onClick = { topicMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(topicPath(topicId, topics), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = topicMenu, onDismissRequest = { topicMenu = false }) {
                    DropdownMenuItem(text = { Text("Sem tópico") }, onClick = { topicId = null; topicMenu = false })
                    flattenTopics(topics).forEach { (t, depth) ->
                        DropdownMenuItem(text = { Text("  ".repeat(depth) + t.name) }, onClick = { topicId = t.id; topicMenu = false })
                    }
                }
            }

            OutlinedTextField(tags, { tags = it }, label = { Text("Etiquetas (separadas por vírgula)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Cor", style = MaterialTheme.typography.labelLarge)
            ColorPicker(color, true) { color = it }

            Text("Ícone", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(listOf("Padrão do tipo") + EMOJIS, if (icon == null) 0 else EMOJIS.indexOf(icon) + 1) { i -> icon = if (i == 0) null else EMOJIS[i - 1] }

            HorizontalDivider()
            Text("Anexos", style = MaterialTheme.typography.titleMedium)
            if (myAtts.isEmpty()) Text("Nenhum arquivo anexado.", color = cs.onSurfaceVariant)
            myAtts.forEach { a ->
                ListItem(
                    headlineContent = { Text(a.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(formatSize(a.size)) },
                    leadingContent = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { vm.openAttachment(a, false) }) { Text("Abrir") }
                            IconButton(onClick = { vm.openAttachment(a, true) }) { Icon(Icons.Default.Share, contentDescription = "Compartilhar") }
                            IconButton(onClick = { removeAtt = a }) { Icon(Icons.Default.Delete, contentDescription = "Remover anexo") }
                        }
                    }
                )
            }
            OutlinedButton(onClick = { ensureSaved { filePicker.launch(arrayOf("*/*")) } }) { Text("Adicionar arquivo") }

            HorizontalDivider()
            Text("Conexões", style = MaterialTheme.typography.titleMedium)
            if (myConns.isEmpty()) Text("Nenhuma conexão. Conecte este registro a outros para formar a teia.", color = cs.onSurfaceVariant)
            myConns.forEach { c ->
                val otherId = if (c.fromId == id) c.toId else c.fromId
                val other = entries.firstOrNull { it.id == otherId }
                ListItem(
                    headlineContent = { Text((if (c.fromId == id) "→ " else "← ") + (other?.title ?: "?"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(c.relation + if (c.label.isNotBlank()) " · ${c.label}" else "") },
                    trailingContent = { TextButton(onClick = { vm.removeConn(c.id) }) { Text("Remover") } }
                )
            }
            OutlinedButton(onClick = { ensureSaved { showPicker = true } }) { Text("Conectar a outro registro") }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showPicker) EntryPickerDialog(entries.filter { !it.archived }, setOf(id), onPick = { pendingTarget = it; showPicker = false }, onDismiss = { showPicker = false })
    pendingTarget?.let { t ->
        LinkDialog(title.ifBlank { "Este registro" }, t.title, onConfirm = { rel, label -> vm.addConn(id, t.id, rel, label); pendingTarget = null }, onDismiss = { pendingTarget = null })
    }
    removeAtt?.let { a ->
        ConfirmDialog("Remover anexo?", "“${a.displayName}” será removido deste registro e do armazenamento do app.", "Remover", { vm.removeAttachment(a); removeAtt = null }, { removeAtt = null })
    }
    if (showDelete) {
        ConfirmDialog(
            "Excluir registro?",
            "Serão removidos também ${myAtts.size} anexo(s) e ${myConns.size} conexão(ões) deste registro. Se quiser apenas esconder, use Arquivar.",
            "Excluir", { showDelete = false; vm.deleteEntries(listOf(id)); nav.popBackStack() }, { showDelete = false }
        )
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Alterações não salvas") },
            text = { Text("Deseja salvar antes de sair?") },
            confirmButton = { TextButton(onClick = { showDiscard = false; save { nav.popBackStack() } }) { Text("Salvar e sair") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscard = false }) { Text("Continuar editando") }
                    TextButton(onClick = { showDiscard = false; baseline = snapshot(); nav.popBackStack() }) { Text("Descartar") }
                }
            }
        )
    }
}
