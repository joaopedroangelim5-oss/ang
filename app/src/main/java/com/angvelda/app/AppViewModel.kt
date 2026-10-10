package com.angvelda.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.angvelda.app.backup.BackupException
import com.angvelda.app.backup.BackupManager
import com.angvelda.app.backup.FileStore
import com.angvelda.app.backup.ParsedBackup
import com.angvelda.app.backup.RestoreMode
import com.angvelda.app.data.AppDatabase
import com.angvelda.app.data.Attachment
import com.angvelda.app.data.Conn
import com.angvelda.app.data.Entry
import com.angvelda.app.data.Topic
import com.angvelda.app.data.descendantsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx: Context = app.applicationContext
    private val db = AppDatabase.get(appCtx)
    private val dao = db.dao()
    val prefs = Prefs(appCtx)
    private val backup = BackupManager(appCtx, db, prefs)

    val entries: StateFlow<List<Entry>> = dao.observeEntries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val topics: StateFlow<List<Topic>> = dao.observeTopics().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val conns: StateFlow<List<Conn>> = dao.observeConns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val attachments: StateFlow<List<Attachment>> = dao.observeAttachments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Estado da teia (preservado ao navegar entre telas)
    var webScale by mutableFloatStateOf(1f)
    var webOffset by mutableStateOf(Offset.Zero)
    var webFramed by mutableStateOf(false)
    var webSelected by mutableStateOf<String?>(null)
    var focusRequest by mutableStateOf<String?>(null)

    // Estado do Cofre
    var busy by mutableStateOf(false)
    var busyMessage by mutableStateOf("")
    var pendingRestore by mutableStateOf<ParsedBackup?>(null)
    var resultReport by mutableStateOf<String?>(null)

    private fun toast(m: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(appCtx, m, Toast.LENGTH_LONG).show() }
    }

    // ------------------------------------------------------------ Registros

    fun newPosition(): Pair<Float, Float> {
        val n = entries.value.size
        val angle = n * 2.399963f
        val r = 110f * sqrt(n.toFloat() + 0.5f)
        return Pair(r * cos(angle), r * sin(angle))
    }

    fun saveEntry(e: Entry, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try { dao.upsertEntry(e); onDone(true) }
            catch (t: Throwable) { toast("Erro ao salvar: ${t.message}"); onDone(false) }
        }
    }

    fun duplicateEntry(e: Entry) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.upsertEntry(e.copy(id = UUID.randomUUID().toString(), title = e.title + " (cópia)", createdAt = now, modifiedAt = now, posX = e.posX + 60f, posY = e.posY + 60f))
            toast("Registro duplicado (anexos e conexões não são copiados).")
        }
    }

    fun deleteEntries(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val atts = dao.allAttachments().filter { it.entryId in ids }
                ids.chunked(500).forEach { dao.deleteEntries(it) }
                atts.forEach { FileStore.attFile(appCtx, it.id).delete() }
                if (webSelected in ids) webSelected = null
            } catch (t: Throwable) { toast("Erro ao excluir: ${t.message}") }
        }
    }

    fun setArchived(ids: List<String>, archived: Boolean) {
        viewModelScope.launch { ids.chunked(500).forEach { dao.setArchived(it, archived) } }
    }

    fun moveEntries(ids: List<String>, topicId: String?) {
        viewModelScope.launch { ids.chunked(500).forEach { dao.setTopic(it, topicId) } }
    }

    fun updatePos(id: String, x: Float, y: Float) {
        viewModelScope.launch { dao.updatePos(id, x, y) }
    }

    fun updatePositions(map: Map<String, Offset>) {
        viewModelScope.launch { db.withTransaction { map.forEach { (id, p) -> dao.updatePos(id, p.x, p.y) } } }
    }

    // ------------------------------------------------------------ Tópicos

    fun createTopic(name: String, description: String, parentId: String?, color: Int?, icon: String?) {
        viewModelScope.launch { dao.upsertTopic(Topic(UUID.randomUUID().toString(), name.trim(), description.trim(), parentId, color, icon)) }
    }

    fun updateTopic(t: Topic) { viewModelScope.launch { dao.upsertTopic(t) } }

    fun moveTopic(id: String, newParent: String?): Boolean {
        val ts = topics.value
        if (newParent != null && (newParent == id || newParent in descendantsOf(id, ts))) {
            toast("Não é possível mover um tópico para dentro dele mesmo ou de um de seus descendentes.")
            return false
        }
        val t = ts.firstOrNull { it.id == id } ?: return false
        viewModelScope.launch { dao.upsertTopic(t.copy(parentId = newParent)) }
        return true
    }

    /** Subtópicos sobem para o pai do tópico excluído; registros vão para [targetTopic] (ou ficam sem tópico). */
    fun deleteTopic(id: String, targetTopic: String?) {
        viewModelScope.launch {
            db.withTransaction {
                val t = dao.topic(id)
                if (t != null) {
                    dao.moveEntriesTopic(id, targetTopic)
                    dao.reparent(id, t.parentId)
                    dao.deleteTopic(id)
                }
            }
        }
    }

    // ------------------------------------------------------------ Conexões

    fun addConn(from: String, to: String, relation: String, label: String): Boolean {
        if (from == to) { toast("Escolha dois registros diferentes."); return false }
        if (conns.value.any { (it.fromId == from && it.toId == to) || (it.fromId == to && it.toId == from) }) {
            toast("Esses registros já estão conectados."); return false
        }
        viewModelScope.launch { dao.insertConn(Conn(UUID.randomUUID().toString(), from, to, relation, label.trim())) }
        return true
    }

    fun removeConn(id: String) { viewModelScope.launch { dao.deleteConn(id) } }

    // ------------------------------------------------------------ Anexos

    fun addAttachment(entryId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val dest = FileStore.attFile(appCtx, id)
            try {
                val cr = appCtx.contentResolver
                var name = "arquivo"
                cr.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i)?.let { name = it }
                }
                val existing = dao.allAttachments().filter { it.entryId == entryId }.map { it.displayName }.toSet()
                if (name in existing) {
                    val dot = name.lastIndexOf('.')
                    val base = if (dot > 0) name.substring(0, dot) else name
                    val ext = if (dot > 0) name.substring(dot) else ""
                    var n = 2
                    while ("$base ($n)$ext" in existing) n++
                    name = "$base ($n)$ext"
                }
                val mime = cr.getType(uri) ?: "application/octet-stream"
                val md = MessageDigest.getInstance("SHA-256")
                var total = 0L
                val input = cr.openInputStream(uri) ?: throw IOException("Não foi possível abrir o arquivo selecionado.")
                input.use { i ->
                    dest.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = i.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n); md.update(buf, 0, n); total += n
                        }
                    }
                }
                val hash = md.digest().joinToString("") { "%02x".format(it) }
                dao.upsertAttachment(Attachment(id, entryId, name, total, mime, hash, System.currentTimeMillis()))
                toast("Anexo adicionado: $name")
            } catch (t: Throwable) {
                dest.delete()
                toast("Não foi possível anexar o arquivo: ${t.message}")
            }
        }
    }

    fun removeAttachment(a: Attachment) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAttachment(a.id)
            FileStore.attFile(appCtx, a.id).delete()
        }
    }

    fun openAttachment(a: Attachment, share: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = FileStore.attFile(appCtx, a.id)
                if (!src.exists()) { toast("O arquivo deste anexo não foi encontrado."); return@launch }
                val dir = File(appCtx.cacheDir, "share/${a.id}").apply { mkdirs() }
                val safe = a.displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "arquivo" }
                val tmp = File(dir, safe)
                src.copyTo(tmp, overwrite = true)
                val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", tmp)
                val intent = if (share) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = a.mime; putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, a.mime)
                        clipData = ClipData.newRawUri("", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                val launch = if (share) Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                withContext(Dispatchers.Main) {
                    try { appCtx.startActivity(launch) }
                    catch (e: ActivityNotFoundException) { toast("Nenhum aplicativo instalado consegue abrir este tipo de arquivo.") }
                }
            } catch (t: Throwable) {
                toast("Erro ao abrir o anexo: ${t.message}")
            }
        }
    }

    // ------------------------------------------------------------ Cofre

    private fun runBusy(msg: String, block: suspend () -> Unit): Job = viewModelScope.launch {
        busy = true; busyMessage = msg
        try { block() }
        catch (e: BackupException) { resultReport = "Não foi possível concluir.\n${e.message}" }
        catch (t: Throwable) { resultReport = "Erro: ${t.message ?: t.javaClass.simpleName}" }
        finally { busy = false; busyMessage = "" }
    }

    fun exportBackup(dest: Uri, suggestedName: String) {
        runBusy("Gerando backup...") {
            val rec = backup.exportToUri(dest, suggestedName) { m -> busyMessage = m }
            prefs.addBackupRecord(rec)
            resultReport = "Backup gerado, gravado e conferido.\nArquivo: ${rec.file}\nRegistros: ${rec.entries}\nTópicos: ${rec.topics}\nTamanho: ${rec.size} bytes"
        }
    }

    fun prepareRestore(src: Uri) {
        runBusy("Validando backup...") {
            pendingRestore?.let { backup.discard(it) }
            pendingRestore = backup.prepare(src) { m -> busyMessage = m }
        }
    }

    fun cancelRestore() { pendingRestore?.let { backup.discard(it) }; pendingRestore = null }

    fun confirmRestore(mode: RestoreMode) {
        val p = pendingRestore ?: return
        pendingRestore = null
        runBusy("Restaurando...") {
            val report = backup.restore(p, mode) { m -> busyMessage = m }
            webFramed = false; webSelected = null
            resultReport = report
        }
    }

    fun checkIntegrity() {
        runBusy("Verificando integridade...") { resultReport = backup.integrity() }
    }
}
