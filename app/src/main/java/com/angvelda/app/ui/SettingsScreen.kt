@file:OptIn(ExperimentalMaterial3Api::class)

package com.angvelda.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.angvelda.app.AppInfo
import com.angvelda.app.AppViewModel
import com.angvelda.app.R

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val p = vm.prefs
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aparência") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Section("Tema") { ChoiceRow(listOf("Seguir o sistema", "Escuro", "Claro"), p.themeMode) { p.themeMode = it } }
            Section("Cor de destaque") { ColorPicker(p.accent, false) { c -> if (c != null) p.accent = c } }
            HorizontalDivider()
            Section("Teia: estilo dos nós") { ChoiceRow(listOf("Círculo luminoso", "Cartão"), p.nodeStyle) { p.nodeStyle = it } }
            Section("Teia: cor das conexões") { ColorPicker(p.linkColor, false) { c -> if (c != null) p.linkColor = c } }
            HorizontalDivider()
            Section("Aparência dos cartões") { ChoiceRow(listOf("Preenchido", "Contorno", "Faixa lateral"), p.cardStyle) { p.cardStyle = it } }
            Section("Densidade da biblioteca") { ChoiceRow(listOf("Confortável", "Compacto"), p.density) { p.density = it } }
            Section("Estilo dos tópicos") { ChoiceRow(listOf("Árvore", "Cartões"), p.topicStyle) { p.topicStyle = it } }
            HorizontalDivider()
            Section("Tamanho do texto (${(p.textScale * 100).toInt()}%)") {
                Slider(value = p.textScale, onValueChange = { p.textScale = (Math.round(it * 20) / 20f) }, valueRange = 0.85f..1.4f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Animações", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = p.animations, onCheckedChange = { p.animations = it })
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ang_logo), contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
                Column {
                    Text(AppInfo.NAME, style = MaterialTheme.typography.titleMedium)
                    Text("Versão ${AppInfo.VERSION} · formato de backup v${AppInfo.BACKUP_FORMAT_VERSION}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tudo é guardado apenas neste aparelho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
