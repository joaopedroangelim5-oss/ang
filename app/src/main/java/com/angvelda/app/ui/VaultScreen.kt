@file:OptIn(ExperimentalMaterial3Api::class)

package com.angvelda.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.angvelda.app.AppInfo
import com.angvelda.app.AppViewModel
import com.angvelda.app.R
import com.angvelda.app.backup.RestoreMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultScreen(vm: AppViewModel, nav: NavController) {
    val entries by vm.entries.collectAsState()
    val topics by vm.topics.collectAsState()
    val conns by vm.conns.collectAsState()
    val atts by vm.attachments.collectAsState()
    val cs = MaterialTheme.colorScheme
    var showAtts by remember { mutableStateOf(false) }
    var removeAtt by remember { mutableStateOf<com.angvelda.app.data.Attachment?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri, "Ang-Velda_Backup_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip")
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cofre") },
                actions = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Configurações") } }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.ang_logo), contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape))
                Column {
                    Text(AppInfo.NAME, style = MaterialTheme.typography.headlineSmall)
                    Text("Seus dados, sob o seu controle.", color = cs.onSurfaceVariant)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Conteúdo guardado", style = MaterialTheme.typography.titleMedium)
                    Text("Registros: ${entries.size}")
                    Text("Tópicos: ${topics.size}")
                    Text("Conexões: ${conns.size}")
                    Text("Anexos: ${atts.size} (${formatSize(atts.sumOf { it.size })})")
                    Text("Formato de backup: v${AppInfo.BACKUP_FORMAT_VERSION}", color = cs.onSurfaceVariant)
                }
            }

            Button(enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                exportLauncher.launch("Ang-Velda_Backup_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip")
            }) { Text("Exportar backup completo") }
            OutlinedButton(enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = { restoreLauncher.launch(arrayOf("*/*")) }) { Text("Restaurar backup") }
            Text("O backup é um arquivo ZIP comum, sem criptografia. Guarde-o em local seguro.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

            if (vm.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(vm.busyMessage, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()
            Text("Manutenção", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = { vm.checkIntegrity() }) { Text("Verificar integridade") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { showAtts = true }) { Text("Gerenciar anexos (${atts.size})") }

            HorizontalDivider()
            Text("Backups realizados", style = MaterialTheme.typography.titleMedium)
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            if (vm.prefs.history.isEmpty()) Text("Nenhum backup exportado por este aparelho ainda.", color = cs.onSurfaceVariant)
            vm.prefs.history.forEach { r ->
                Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.file, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text("Gerado em ${fmt.format(Date(r.createdAt))}", style = MaterialTheme.typography.bodySmall)
                        Text("Gravado e conferido no destino em ${fmt.format(Date(r.savedAt))}", style = MaterialTheme.typography.bodySmall)
                        Text("${r.entries} registros · ${r.topics} tópicos · ${formatSize(r.size)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Resumo antes de restaurar
    vm.pendingRestore?.let { p ->
        var mode by remember { mutableStateOf(RestoreMode.MERGE) }
        var understood by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { vm.cancelRestore() },
            title = { Text("Restaurar backup") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Backup válido (formato v${p.formatVersion}).")
                    if (p.createdAt > 0) Text("Criado em ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(p.createdAt))}")
                    Text("Registros: ${p.entries.size}\nTópicos: ${p.topics.size}\nConexões: ${p.conns.size}\nAnexos: ${p.attachments.size} (${formatSize(p.attachmentBytes)})")
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mode = RestoreMode.MERGE }) {
                        RadioButton(selected = mode == RestoreMode.MERGE, onClick = { mode = RestoreMode.MERGE })
                        Column { Text("Mesclar com o conteúdo atual"); Text("Mantém tudo que você tem e adiciona o do backup.", style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mode = RestoreMode.REPLACE }) {
                        RadioButton(selected = mode == RestoreMode.REPLACE, onClick = { mode = RestoreMode.REPLACE })
                        Column { Text("Substituir o conteúdo atual"); Text("Apaga o que existe agora. Um backup de segurança interno é criado antes.", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (mode == RestoreMode.REPLACE) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { understood = !understood }) {
                            Checkbox(checked = understood, onCheckedChange = { understood = it })
                            Text("Entendo que o conteúdo atual será substituído.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = mode == RestoreMode.MERGE || understood, onClick = { vm.confirmRestore(mode) }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { vm.cancelRestore() }) { Text("Cancelar") } }
        )
    }

    vm.resultReport?.let { InfoDialog("Resultado", it) { vm.resultReport = null } }

    if (showAtts) {
        val titles = entries.associate { it.id to it.title }
        AlertDialog(
            onDismissRequest = { showAtts = false },
            title = { Text("Anexos armazenados") },
            text = {
                if (atts.isEmpty()) Text("Nenhum anexo guardado.")
                else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(atts, key = { it.id }) { a ->
                        ListItem(
                            headlineContent = { Text(a.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${formatSize(a.size)} · ${titles[a.entryId] ?: "?"}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Row {
                                    TextButton(onClick = { vm.openAttachment(a, false) }) { Text("Abrir") }
                                    IconButton(onClick = { vm.openAttachment(a, true) }) { Icon(Icons.Default.Share, contentDescription = "Compartilhar") }
                                    IconButton(onClick = { removeAtt = a }) { Icon(Icons.Default.Delete, contentDescription = "Remover") }
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAtts = false }) { Text("Fechar") } }
        )
    }
    removeAtt?.let { a ->
        ConfirmDialog("Remover anexo?", "“${a.displayName}” será apagado do armazenamento do app. Esta ação não pode ser desfeita.", "Remover", { vm.removeAttachment(a); removeAtt = null }, { removeAtt = null })
    }
}
