@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.angvelda.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.angvelda.app.Prefs
import com.angvelda.app.data.Entry
import com.angvelda.app.data.EntryTypes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Palette {
    val colors: List<Int> = listOf(
        0xFF8B5CF6, 0xFFA78BFA, 0xFFEC4899, 0xFF3B82F6,
        0xFF06B6D4, 0xFF10B981, 0xFFF59E0B, 0xFFEF4444
    ).map { it.toInt() }
}

val EMOJIS = listOf("💡", "💭", "📝", "📌", "📚", "⭐", "🔬", "🎯", "🔧", "❤️", "🌱", "🔥")
val RELATIONS = listOf("Relacionado a", "Inspirou", "Explica", "Contradiz", "Faz parte de")

fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))

fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(Locale.US, b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(Locale.US, b / 1048576.0)
    else -> "%.2f GB".format(Locale.US, b / 1073741824.0)
}

fun editorRoute(id: String = "new", topicId: String? = null, type: String? = null): String {
    val q = buildList {
        if (!topicId.isNullOrEmpty()) add("topicId=$topicId")
        if (!type.isNullOrEmpty()) add("type=$type")
    }
    return "editor/$id" + if (q.isEmpty()) "" else "?" + q.joinToString("&")
}

@Composable
fun ColorDot(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(color?.let { Color(it) } ?: cs.surfaceVariant)
            .then(if (selected) Modifier.border(2.5.dp, cs.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { if (color == null) Text("∅", color = cs.onSurfaceVariant) }
}

@Composable
fun ColorPicker(selected: Int?, allowNone: Boolean, onSelect: (Int?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowNone) ColorDot(null, selected == null) { onSelect(null) }
        Palette.colors.forEach { c -> ColorDot(c, selected == c) { onSelect(c) } }
    }
}

@Composable
fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun EmptyState(title: String, text: String, actionLabel: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Text(text) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
fun EntryCard(
    entry: Entry, attCount: Int, prefs: Prefs, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val accent = entry.color?.let { Color(it) } ?: cs.primary
    val compact = prefs.density == 1
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        selected -> cs.primaryContainer
        prefs.cardStyle == 1 -> Color.Transparent
        else -> cs.surfaceVariant
    }
    val borderMod = when {
        selected -> Modifier.border(2.dp, cs.primary, shape)
        prefs.cardStyle == 1 -> Modifier.border(1.dp, accent.copy(alpha = 0.7f), shape)
        else -> Modifier
    }
    Row(
        modifier.fillMaxWidth()
            .then(if (prefs.animations) Modifier.animateContentSize() else Modifier)
            .clip(shape).background(bg).then(borderMod)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .height(IntrinsicSize.Min)
    ) {
        if (prefs.cardStyle == 2) Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(if (compact) 10.dp else 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.icon ?: EntryTypes.emoji(entry.type), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (attCount > 0) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Possui anexos", modifier = Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                    Text("$attCount", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            if (entry.content.isNotBlank()) {
                Text(entry.content, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis)
            }
            val tags = entry.tagList()
            Text(
                EntryTypes.label(entry.type) + " · " + formatDate(entry.modifiedAt) + (if (tags.isNotEmpty()) " · " + tags.joinToString(" ") { "#$it" } else ""),
                style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CreateMenu(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Criar", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            EntryTypes.all.filter { it.key != "livre" }.forEach { t ->
                val label = when (t.key) {
                    "documento" -> "Adicionar documento ou arquivo"
                    "texto_longo" -> "Novo texto longo"
                    else -> "Nova ${t.label.lowercase()}"
                }
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Text(t.emoji, style = MaterialTheme.typography.titleLarge) },
                    modifier = Modifier.clickable { onPick(t.key) }
                )
            }
        }
    }
}

@Composable
fun LinkDialog(fromTitle: String, toTitle: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var relation by remember { mutableStateOf(RELATIONS[0]) }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar conexão") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("“$fromTitle” → “$toTitle”", style = MaterialTheme.typography.bodyMedium)
                ChoiceRow(RELATIONS, RELATIONS.indexOf(relation)) { relation = RELATIONS[it] }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Rótulo (opcional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(relation, label) }) { Text("Conectar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun EntryPickerDialog(entries: List<Entry>, exclude: Set<String>, onPick: (Entry) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = entries.filter { it.id !in exclude && (q.isBlank() || it.title.contains(q, true) || it.content.contains(q, true)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conectar a...") },
        text = {
            Column {
                OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Pesquisar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(list, key = { it.id }) { e ->
                        ListItem(
                            headlineContent = { Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Text(e.icon ?: EntryTypes.emoji(e.type)) },
                            modifier = Modifier.clickable { onPick(e) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
