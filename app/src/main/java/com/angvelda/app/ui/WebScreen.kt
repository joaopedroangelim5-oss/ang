@file:OptIn(ExperimentalMaterial3Api::class)

package com.angvelda.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.navigation.NavController
import com.angvelda.app.AppViewModel
import com.angvelda.app.data.Conn
import com.angvelda.app.data.Entry
import com.angvelda.app.data.EntryTypes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val R_NODE = 26f
private const val RECT_W = 130f
private const val RECT_H = 46f

fun autoLayout(es: List<Entry>, ls: List<Conn>): Map<String, Offset> {
    val n = es.size
    if (n == 0) return emptyMap()
    val idx = es.withIndex().associate { it.value.id to it.index }
    val rad = 120f + n * 8f
    val x = FloatArray(n) { (cos(it * 2.0 * PI / n) * rad).toFloat() }
    val y = FloatArray(n) { (sin(it * 2.0 * PI / n) * rad).toFloat() }
    val iters = if (n > 400) 60 else 150
    for (iter in 0 until iters) {
        val t = 1f - iter / iters.toFloat()
        val dx = FloatArray(n); val dy = FloatArray(n)
        for (i in 0 until n) for (j in i + 1 until n) {
            var ddx = x[i] - x[j]; var ddy = y[i] - y[j]
            var d2 = ddx * ddx + ddy * ddy
            if (d2 < 1f) { ddx = 1f; ddy = 0f; d2 = 1f }
            val d = sqrt(d2)
            val f = 9000f / d2
            dx[i] += ddx / d * f; dy[i] += ddy / d * f
            dx[j] -= ddx / d * f; dy[j] -= ddy / d * f
        }
        for (l in ls) {
            val a = idx[l.fromId] ?: continue
            val b = idx[l.toId] ?: continue
            val ddx = x[b] - x[a]; val ddy = y[b] - y[a]
            val d = max(sqrt(ddx * ddx + ddy * ddy), 1f)
            val f = (d - 180f) * 0.05f
            dx[a] += ddx / d * f; dy[a] += ddy / d * f
            dx[b] -= ddx / d * f; dy[b] -= ddy / d * f
        }
        val lim = 40f * t + 1f
        for (i in 0 until n) {
            x[i] += (dx[i] - x[i] * 0.01f).coerceIn(-lim, lim)
            y[i] += (dy[i] - y[i] * 0.01f).coerceIn(-lim, lim)
        }
    }
    return es.withIndex().associate { it.value.id to Offset(x[it.index], y[it.index]) }
}

@Composable
fun WebScreen(vm: AppViewModel, nav: NavController) {
    val allEntries by vm.entries.collectAsState()
    val links by vm.conns.collectAsState()
    val atts by vm.attachments.collectAsState()
    val prefs = vm.prefs
    val cs = MaterialTheme.colorScheme

    val entries = remember(allEntries) { allEntries.filter { !it.archived } }
    val byId = remember(entries) { entries.associateBy { it.id } }
    val visLinks = remember(links, byId) { links.filter { byId.containsKey(it.fromId) && byId.containsKey(it.toId) } }
    val attIds = remember(atts) { atts.map { it.entryId }.toSet() }
    val dragPos = remember { mutableStateMapOf<String, Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var query by remember { mutableStateOf("") }
    var connectFrom by remember { mutableStateOf<String?>(null) }
    var pendingLink by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLinks by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var confirmLayout by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(allEntries) { dragPos.clear() }

    fun worldPos(e: Entry): Offset = dragPos[e.id] ?: Offset(e.posX, e.posY)

    fun centerOn(id: String) {
        val e = byId[id] ?: return
        vm.webOffset = Offset(canvasSize.width / 2f, canvasSize.height / 2f) - worldPos(e) * vm.webScale
    }

    fun frameAll() {
        if (entries.isEmpty() || canvasSize == IntSize.Zero) return
        val ps = entries.map { worldPos(it) }
        val minX = ps.minOf { it.x }; val maxX = ps.maxOf { it.x }
        val minY = ps.minOf { it.y }; val maxY = ps.maxOf { it.y }
        val w = canvasSize.width.toFloat(); val h = canvasSize.height.toFloat()
        val s = min((w - 160f) / (maxX - minX + 200f), (h - 320f) / (maxY - minY + 200f)).coerceIn(0.2f, 1.2f)
        vm.webScale = s
        vm.webOffset = Offset(w / 2f, h / 2f) - Offset((minX + maxX) / 2f, (minY + maxY) / 2f) * s
    }

    LaunchedEffect(canvasSize, entries.size, vm.webFramed) {
        if (!vm.webFramed && canvasSize != IntSize.Zero && entries.isNotEmpty()) { frameAll(); vm.webFramed = true }
    }
    LaunchedEffect(vm.focusRequest, canvasSize, byId) {
        val id = vm.focusRequest
        if (id != null && canvasSize != IntSize.Zero && byId.containsKey(id)) {
            vm.webScale = max(vm.webScale, 1f)
            centerOn(id); vm.webSelected = id; vm.focusRequest = null
        }
    }

    val curEntries by rememberUpdatedState(entries)
    val curStyle by rememberUpdatedState(prefs.nodeStyle)

    val tapNodeAction: (String) -> Unit = { id ->
        val from = connectFrom
        if (from != null) {
            if (id != from) pendingLink = from to id
            connectFrom = null
        } else if (vm.webSelected == id) {
            nav.navigate(editorRoute(id))
        } else vm.webSelected = id
    }
    val tapNode by rememberUpdatedState(tapNodeAction)
    val tapEmptyAction: () -> Unit = { if (connectFrom == null) vm.webSelected = null }
    val tapEmpty by rememberUpdatedState(tapEmptyAction)

    fun hit(screen: Offset): String? {
        val s = vm.webScale
        val w = (screen - vm.webOffset) / s
        for (e in curEntries.asReversed()) {
            val p = worldPos(e)
            val inside = if (curStyle == 1) abs(w.x - p.x) <= RECT_W / 2 + 6 / s && abs(w.y - p.y) <= RECT_H / 2 + 6 / s
            else (w - p).getDistance() <= R_NODE + 8 / s
            if (inside) return e.id
        }
        return null
    }

    val sel = vm.webSelected
    val related = remember(sel, visLinks) {
        if (sel == null) emptySet() else visLinks.filter { it.fromId == sel || it.toId == sel }.flatMap { listOf(it.fromId, it.toId) }.toSet()
    }
    val searching = query.isNotBlank()
    val matches = remember(query, entries) {
        if (query.isBlank()) emptySet() else entries.filter { it.title.contains(query, true) || it.content.contains(query, true) }.map { it.id }.toSet()
    }
    val accent = cs.primary
    val linkBase = Color(prefs.linkColor)
    val textColor = cs.onBackground
    val style = prefs.nodeStyle

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { canvasSize = it }.pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var nodeId = hit(down.position)
                    var moved = false
                    var multi = false
                    var total = Offset.Zero
                    var draggedId: String? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            multi = true; moved = true; nodeId = null
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val c = event.calculateCentroid()
                            val old = vm.webScale
                            val ns = (old * zoom).coerceIn(0.15f, 4f)
                            val worldC = (c - vm.webOffset) / old
                            vm.webScale = ns
                            vm.webOffset = c - worldC * ns + pan
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!multi) {
                            val ch = event.changes.firstOrNull { it.pressed } ?: break
                            val d = ch.positionChange()
                            total += d
                            if (!moved && total.getDistance() > slop) moved = true
                            if (moved) {
                                val nid = nodeId
                                if (nid != null) {
                                    val e = curEntries.firstOrNull { it.id == nid }
                                    if (e != null) {
                                        val cur = dragPos[nid] ?: Offset(e.posX, e.posY)
                                        dragPos[nid] = cur + d / vm.webScale
                                        draggedId = nid
                                    }
                                } else {
                                    vm.webOffset += d
                                }
                                ch.consume()
                            }
                        }
                    }
                    if (!moved) {
                        val nid = nodeId
                        if (nid != null) tapNode(nid) else tapEmpty()
                    } else {
                        draggedId?.let { id -> dragPos[id]?.let { p -> vm.updatePos(id, p.x, p.y) } }
                    }
                }
            }
        ) {
            val s = vm.webScale
            val off = vm.webOffset
            val dp = density
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = size.maxDimension * 0.7f))

            fun screenPos(e: Entry): Offset = (dragPos[e.id] ?: Offset(e.posX, e.posY)) * s + off

            for (l in visLinks) {
                val a = byId[l.fromId] ?: continue
                val b = byId[l.toId] ?: continue
                val pa = screenPos(a); val pb = screenPos(b)
                val isRel = sel != null && (l.fromId == sel || l.toId == sel)
                val alpha = when {
                    sel != null -> if (isRel) 1f else 0.15f
                    searching -> if (a.id in matches && b.id in matches) 1f else 0.12f
                    else -> 0.6f
                }
                val col = l.color?.let { Color(it) } ?: linkBase
                val sw = (if (isRel) 3f else 1.8f) * dp * s.coerceIn(0.6f, 1.6f)
                if (isRel) drawLine(col.copy(alpha = 0.22f), pa, pb, strokeWidth = sw * 3f)
                drawLine(col.copy(alpha = alpha), pa, pb, strokeWidth = sw)
                if (l.label.isNotBlank() && s > 0.7f) {
                    val lay = textMeasurer.measure(l.label, TextStyle(fontSize = (10f * s).coerceIn(8f, 16f).sp), maxLines = 1)
                    val mid = (pa + pb) / 2f
                    drawText(lay, color = textColor.copy(alpha = alpha), topLeft = Offset(mid.x - lay.size.width / 2f, mid.y - lay.size.height / 2f))
                }
            }

            for (e in entries) {
                val c = screenPos(e)
                if (c.x < -220 || c.x > size.width + 220 || c.y < -220 || c.y > size.height + 220) continue
                val color = e.color?.let { Color(it) } ?: accent
                var alpha = 1f
                if (sel != null) alpha = if (e.id == sel || e.id in related) 1f else 0.3f
                if (searching) alpha = if (e.id in matches) 1f else 0.2f
                val r = R_NODE * s
                val icon = e.icon ?: EntryTypes.emoji(e.type)
                val titleSize = (11f * s).coerceIn(9f, 16f).sp
                if (style == 0) {
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f * alpha), Color.Transparent), center = c, radius = r * 2.4f), radius = r * 2.4f, center = c)
                    drawCircle(color.copy(alpha = alpha), r, c)
                    drawCircle(Color.White.copy(alpha = 0.22f * alpha), r * 0.55f, c - Offset(r * 0.2f, r * 0.2f))
                    if (e.id == sel) drawCircle(Color.White, r + 6f * dp, c, style = Stroke(2.5f * dp))
                    else if (searching && e.id in matches) drawCircle(accent, r + 6f * dp, c, style = Stroke(2.5f * dp))
                    if (s > 0.45f) {
                        val il = textMeasurer.measure(icon, TextStyle(fontSize = (15f * s).coerceIn(9f, 30f).sp), maxLines = 1)
                        drawText(il, topLeft = Offset(c.x - il.size.width / 2f, c.y - il.size.height / 2f))
                        val tl = textMeasurer.measure(e.title, TextStyle(fontSize = titleSize), overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = (150f * s).toInt().coerceAtLeast(1)))
                        drawText(tl, color = textColor.copy(alpha = alpha), topLeft = Offset(c.x - tl.size.width / 2f, c.y + r + 4f * dp))
                    }
                } else {
                    val w = RECT_W * s; val h = RECT_H * s
                    val tl0 = Offset(c.x - w / 2f, c.y - h / 2f)
                    drawRoundRect(color.copy(alpha = 0.28f * alpha), tl0 - Offset(6f, 6f), Size(w + 12f, h + 12f), CornerRadius(16f * s))
                    drawRoundRect(color.copy(alpha = 0.92f * alpha), tl0, Size(w, h), CornerRadius(12f * s))
                    if (e.id == sel) drawRoundRect(Color.White, tl0 - Offset(4f, 4f), Size(w + 8f, h + 8f), CornerRadius(14f * s), style = Stroke(2.5f * dp))
                    else if (searching && e.id in matches) drawRoundRect(accent, tl0 - Offset(4f, 4f), Size(w + 8f, h + 8f), CornerRadius(14f * s), style = Stroke(2.5f * dp))
                    if (s > 0.45f) {
                        val onNode = if (color.luminance() > 0.55f) Color.Black else Color.White
                        val il = textMeasurer.measure(icon, TextStyle(fontSize = (14f * s).coerceIn(9f, 28f).sp), maxLines = 1)
                        drawText(il, topLeft = Offset(tl0.x + 6f * s, c.y - il.size.height / 2f))
                        val tl = textMeasurer.measure(e.title, TextStyle(fontSize = titleSize), overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = ((RECT_W - 38f) * s).toInt().coerceAtLeast(1)))
                        drawText(tl, color = onNode.copy(alpha = alpha), topLeft = Offset(tl0.x + 30f * s, c.y - tl.size.height / 2f))
                    }
                }
                if (e.id in attIds && s > 0.5f) {
                    val bp = if (style == 0) c + Offset(r * 0.8f, -r * 0.8f) else c + Offset(RECT_W * s / 2f, -RECT_H * s / 2f)
                    drawCircle(Color.White.copy(alpha = alpha), 8f * dp * s.coerceIn(0.7f, 1.3f), bp)
                    val bl = textMeasurer.measure("📎", TextStyle(fontSize = (9f * s).coerceIn(7f, 14f).sp), maxLines = 1)
                    drawText(bl, topLeft = Offset(bp.x - bl.size.width / 2f, bp.y - bl.size.height / 2f))
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                "Sua teia está vazia",
                "Cada registro vira um nó. Crie o primeiro e depois conecte ideias entre si.",
                "Criar primeiro registro", { showCreate = true }
            )
        }

        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = cs.surface.copy(alpha = 0.94f), tonalElevation = 3.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Pesquisar na teia") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            entries.firstOrNull { it.id in matches }?.let { centerOn(it.id); vm.webSelected = it.id }
                            keyboard?.hide()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { frameAll() }) { Icon(Icons.Default.CenterFocusStrong, contentDescription = "Enquadrar todos os nós") }
                    IconButton(onClick = { confirmLayout = true }) { Icon(Icons.Default.AutoFixHigh, contentDescription = "Reorganizar automaticamente") }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Configurações") }
                }
            }
            if (connectFrom != null) {
                Spacer(Modifier.padding(top = 8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = cs.primaryContainer)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Toque no registro de destino da conexão", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { connectFrom = null }) { Text("Cancelar") }
                    }
                }
            }
        }

        val selEntry = sel?.let { byId[it] }
        if (selEntry != null && connectFrom == null) {
            val relCount = visLinks.count { it.fromId == selEntry.id || it.toId == selEntry.id }
            Card(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selEntry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.webSelected = null }) { Icon(Icons.Default.Close, contentDescription = "Fechar") }
                    }
                    if (selEntry.content.isNotBlank()) Text(selEntry.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { nav.navigate(editorRoute(selEntry.id)) }) { Text("Abrir") }
                        FilledTonalButton(onClick = { connectFrom = selEntry.id }) { Text("Conectar") }
                        FilledTonalButton(onClick = { showLinks = true }) { Text("Conexões ($relCount)") }
                        FilledTonalButton(onClick = { centerOn(selEntry.id) }) { Text("Centralizar") }
                    }
                }
            }
        } else if (connectFrom == null) {
            FloatingActionButton(onClick = { showCreate = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Criar")
            }
        }
    }

    if (showCreate) CreateMenu(onPick = { showCreate = false; nav.navigate(editorRoute(type = it)) }, onDismiss = { showCreate = false })

    pendingLink?.let { (from, to) ->
        LinkDialog(
            byId[from]?.title ?: "", byId[to]?.title ?: "",
            onConfirm = { rel, label -> vm.addConn(from, to, rel, label); pendingLink = null },
            onDismiss = { pendingLink = null }
        )
    }

    if (showLinks && sel != null) {
        val mine = visLinks.filter { it.fromId == sel || it.toId == sel }
        AlertDialog(
            onDismissRequest = { showLinks = false },
            title = { Text("Conexões") },
            text = {
                if (mine.isEmpty()) Text("Este registro ainda não tem conexões.")
                else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(mine, key = { it.id }) { l ->
                        val otherId = if (l.fromId == sel) l.toId else l.fromId
                        val dir = if (l.fromId == sel) "→" else "←"
                        ListItem(
                            headlineContent = { Text("$dir ${byId[otherId]?.title ?: "?"}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(l.relation + if (l.label.isNotBlank()) " · ${l.label}" else "") },
                            trailingContent = { TextButton(onClick = { vm.removeConn(l.id) }) { Text("Remover") } }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLinks = false }) { Text("Fechar") } }
        )
    }

    if (confirmLayout) {
        ConfirmDialog(
            "Reorganizar automaticamente?",
            "As posições atuais dos nós serão substituídas por uma disposição automática baseada nas conexões. Você poderá voltar a arrastar os nós normalmente.",
            "Reorganizar",
            onConfirm = {
                confirmLayout = false
                val pos = autoLayout(entries, visLinks)
                pos.forEach { (id, p) -> dragPos[id] = p }
                frameAll()
                vm.updatePositions(pos)
            },
            onDismiss = { confirmLayout = false }
        )
    }
}
