package com.angvelda.app.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.angvelda.app.AppInfo
import com.angvelda.app.BackupRecord
import com.angvelda.app.Prefs
import com.angvelda.app.data.AppDatabase
import com.angvelda.app.data.Attachment
import com.angvelda.app.data.Conn
import com.angvelda.app.data.Entry
import com.angvelda.app.data.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileStore {
    fun attDir(ctx: Context): File = File(ctx.filesDir, "attachments").apply { mkdirs() }
    fun attFile(ctx: Context, id: String): File = File(attDir(ctx), id)
}

class BackupException(message: String) : Exception(message)

enum class RestoreMode { REPLACE, MERGE }

class ParsedBackup(
    val file: File,
    val formatVersion: Int,
    val createdAt: Long,
    val topics: List<Topic>,
    val entries: List<Entry>,
    val conns: List<Conn>,
    val attachments: List<Attachment>,
    val settings: Map<String, String>
) {
    val attachmentBytes: Long get() = attachments.sumOf { it.size }
}

fun sha256Hex(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun JSONObject.strOrNull(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
private fun JSONObject.intOrNull(k: String): Int? = if (!has(k) || isNull(k)) null else getInt(k)
private fun Float.finiteOr0(): Float = if (this.isNaN() || this.isInfinite()) 0f else this

/**
 * Formato do backup (versão 1):
 *   manifest.json
 *   data/topics.json, data/entries.json, data/connections.json, data/attachments.json, data/settings.json
 *   attachments/<id-do-anexo>
 * Veja docs/FORMATO_BACKUP.md.
 */
class BackupManager(private val ctx: Context, private val db: AppDatabase, private val prefs: Prefs) {
    private val dao = db.dao()

    companion object {
        const val FORMAT = "ang-velda-backup"
        const val MAX_JSON_BYTES = 64 * 1024 * 1024
        const val MAX_ENTRY_BYTES = 4L * 1024 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 16L * 1024 * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 200_000
        private val ID_RE = Regex("^[A-Za-z0-9_-]{1,64}$")
        private val REQUIRED = listOf(
            "data/topics.json", "data/entries.json", "data/connections.json",
            "data/attachments.json", "data/settings.json"
        )
    }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())

    // ---------------------------------------------------------------- JSON

    private fun topicJson(t: Topic) = JSONObject().apply {
        put("id", t.id); put("name", t.name); put("description", t.description)
        put("parentId", t.parentId ?: JSONObject.NULL)
        put("color", t.color ?: JSONObject.NULL)
        put("icon", t.icon ?: JSONObject.NULL)
        put("sortOrder", t.sortOrder)
    }

    private fun entryJson(e: Entry) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("content", e.content); put("type", e.type)
        put("createdAt", e.createdAt); put("modifiedAt", e.modifiedAt)
        put("topicId", e.topicId ?: JSONObject.NULL)
        put("tags", JSONArray(e.tagList()))
        put("color", e.color ?: JSONObject.NULL)
        put("icon", e.icon ?: JSONObject.NULL)
        put("archived", e.archived)
        put("pos", JSONObject().put("x", e.posX.toDouble()).put("y", e.posY.toDouble()))
    }

    private fun connJson(c: Conn) = JSONObject().apply {
        put("id", c.id); put("from", c.fromId); put("to", c.toId)
        put("relation", c.relation); put("label", c.label)
        put("color", c.color ?: JSONObject.NULL)
    }

    private fun attJson(a: Attachment) = JSONObject().apply {
        put("id", a.id); put("entryId", a.entryId); put("name", a.displayName)
        put("mime", a.mime); put("size", a.size); put("sha256", a.sha256)
        put("createdAt", a.createdAt); put("path", "attachments/${a.id}")
    }

    private fun wrap(items: JSONArray) = JSONObject().put("formatVersion", AppInfo.BACKUP_FORMAT_VERSION).put("items", items)

    private fun parseTopic(o: JSONObject) = Topic(
        id = o.getString("id"), name = o.getString("name"),
        description = o.optString("description", ""),
        parentId = o.strOrNull("parentId"), color = o.intOrNull("color"),
        icon = o.strOrNull("icon"), sortOrder = o.optInt("sortOrder", 0)
    )

    private fun parseEntry(o: JSONObject): Entry {
        val pos = o.optJSONObject("pos")
        val tagsArr = o.optJSONArray("tags")
        val tags = if (tagsArr == null) "" else (0 until tagsArr.length()).joinToString(",") { tagsArr.getString(it) }
        val created = o.getLong("createdAt")
        return Entry(
            id = o.getString("id"), title = o.getString("title"),
            content = o.optString("content", ""), type = o.optString("type", "livre"),
            createdAt = created, modifiedAt = o.optLong("modifiedAt", created),
            topicId = o.strOrNull("topicId"), tags = tags,
            color = o.intOrNull("color"), icon = o.strOrNull("icon"),
            archived = o.optBoolean("archived", false),
            posX = (pos?.optDouble("x", 0.0) ?: 0.0).toFloat().finiteOr0(),
            posY = (pos?.optDouble("y", 0.0) ?: 0.0).toFloat().finiteOr0()
        )
    }

    private fun parseConn(o: JSONObject) = Conn(
        id = o.getString("id"), fromId = o.getString("from"), toId = o.getString("to"),
        relation = o.optString("relation", "Relacionado a"), label = o.optString("label", ""),
        color = o.intOrNull("color")
    )

    private fun parseAtt(o: JSONObject) = Attachment(
        id = o.getString("id"), entryId = o.getString("entryId"), displayName = o.getString("name"),
        size = o.getLong("size"), mime = o.optString("mime", "application/octet-stream"),
        sha256 = o.getString("sha256"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    // ---------------------------------------------------------------- Exportar

    private class Snapshot(val topics: List<Topic>, val entries: List<Entry>, val conns: List<Conn>, val atts: List<Attachment>)

    suspend fun writeZip(target: File): BackupRecord = withContext(Dispatchers.IO) {
        val snap = db.withTransaction { Snapshot(dao.allTopics(), dao.allEntries(), dao.allConns(), dao.allAttachments()) }
        val now = System.currentTimeMillis()
        val files = JSONArray()
        fun jsonBytes(o: JSONObject) = o.toString().toByteArray(Charsets.UTF_8)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            fun putData(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
                files.put(JSONObject().put("path", path).put("size", bytes.size).put("sha256", sha256Hex(bytes)))
            }
            putData("data/topics.json", jsonBytes(wrap(JSONArray().also { a -> snap.topics.forEach { a.put(topicJson(it)) } })))
            putData("data/entries.json", jsonBytes(wrap(JSONArray().also { a -> snap.entries.forEach { a.put(entryJson(it)) } })))
            putData("data/connections.json", jsonBytes(wrap(JSONArray().also { a -> snap.conns.forEach { a.put(connJson(it)) } })))
            putData("data/attachments.json", jsonBytes(wrap(JSONArray().also { a -> snap.atts.forEach { a.put(attJson(it)) } })))
            val st = JSONObject(); prefs.exportSettings().forEach { (k, v) -> st.put(k, v) }
            putData("data/settings.json", jsonBytes(JSONObject().put("formatVersion", AppInfo.BACKUP_FORMAT_VERSION).put("settings", st)))

            for (a in snap.atts) {
                val f = FileStore.attFile(ctx, a.id)
                if (!f.exists()) throw IOException("O anexo \"${a.displayName}\" está ausente no armazenamento do app. O backup foi cancelado.")
                zip.putNextEntry(ZipEntry("attachments/${a.id}"))
                f.inputStream().use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }

            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("formatVersion", AppInfo.BACKUP_FORMAT_VERSION)
                put("createdAt", now)
                put("createdAtIso", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date(now)))
                put("app", JSONObject().put("name", AppInfo.NAME).put("version", AppInfo.VERSION))
                put("counts", JSONObject().put("entries", snap.entries.size).put("topics", snap.topics.size)
                    .put("connections", snap.conns.size).put("attachments", snap.atts.size))
                put("encrypted", false)
                put("files", files)
            }
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(jsonBytes(manifest)); zip.closeEntry()
        }
        BackupRecord(target.name, now, now, target.length(), snap.entries.size, snap.topics.size)
    }

    private fun displayName(uri: Uri): String? = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
    } catch (e: Exception) { null }

    suspend fun exportToUri(dest: Uri, suggestedName: String, progress: (String) -> Unit): BackupRecord = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "export_tmp.zip")
        tmp.delete()
        try {
            progress("Reunindo dados e anexos...")
            val rec = writeZip(tmp)
            progress("Validando o arquivo gerado...")
            parseAndValidate(tmp)
            val expected = tmp.inputStream().use { sha256Hex(it) }
            progress("Gravando no destino escolhido...")
            val out = ctx.contentResolver.openOutputStream(dest, "wt")
                ?: throw IOException("Não foi possível abrir o destino para gravação.")
            out.use { o -> tmp.inputStream().use { it.copyTo(o, 64 * 1024) }; o.flush() }
            progress("Conferindo o arquivo gravado...")
            val actual = ctx.contentResolver.openInputStream(dest)?.use { sha256Hex(it) }
                ?: throw IOException("O arquivo foi gravado, mas não foi possível reabri-lo para conferência.")
            if (actual != expected) throw IOException("O arquivo gravado no destino não confere com o backup gerado.")
            rec.copy(file = displayName(dest) ?: suggestedName, savedAt = System.currentTimeMillis())
        } finally {
            tmp.delete()
        }
    }

    // ---------------------------------------------------------------- Ler / validar

    suspend fun prepare(src: Uri, progress: (String) -> Unit): ParsedBackup = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "restore_input.zip")
        tmp.delete()
        try {
            progress("Lendo o arquivo selecionado...")
            val input = ctx.contentResolver.openInputStream(src) ?: throw BackupException("Não foi possível abrir o arquivo selecionado.")
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
            progress("Validando o backup...")
            parseAndValidate(tmp, progress)
        } catch (t: Throwable) {
            tmp.delete(); throw t
        }
    }

    fun discard(p: ParsedBackup) { p.file.delete() }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw BackupException("Um arquivo de dados do backup é grande demais.")
            bos.write(buf, 0, n)
        }
        return bos.toByteArray()
    }

    fun parseAndValidate(file: File, progress: (String) -> Unit = {}): ParsedBackup {
        val zip = try { ZipFile(file) } catch (e: Exception) {
            throw BackupException("Não foi possível abrir o arquivo como ZIP. Ele pode estar corrompido ou não ser um backup.")
        }
        val parsed = zip.use { z ->
            val names = HashSet<String>()
            var total = 0L
            val list = Collections.list(z.entries())
            if (list.size > MAX_ZIP_ENTRIES) throw BackupException("O ZIP contém entradas demais.")
            for (e in list) {
                val n = e.name
                if (n.startsWith("/") || n.contains("..") || n.contains('\\') || n.contains(':') || n.contains('\u0000'))
                    throw BackupException("Caminho inseguro dentro do ZIP: $n")
                if (!names.add(n)) throw BackupException("Entrada duplicada no ZIP: $n")
                if (e.isDirectory) continue
                if (e.size < 0) throw BackupException("Entrada sem tamanho declarado: $n")
                if (e.size > MAX_ENTRY_BYTES) throw BackupException("Entrada grande demais: $n")
                total += e.size
                if (total > MAX_TOTAL_BYTES) throw BackupException("O conteúdo do backup é grande demais.")
            }

            fun bytes(path: String): ByteArray {
                val e = z.getEntry(path) ?: throw BackupException("Arquivo obrigatório ausente no backup: $path")
                if (e.size > MAX_JSON_BYTES) throw BackupException("Arquivo de dados grande demais: $path")
                return z.getInputStream(e).use { readLimited(it, MAX_JSON_BYTES) }
            }
            fun json(path: String, data: ByteArray): JSONObject = try {
                JSONObject(String(data, Charsets.UTF_8))
            } catch (e: JSONException) {
                throw BackupException("JSON malformado em $path.")
            }

            val manifest = json("manifest.json", bytes("manifest.json"))
            if (manifest.optString("format") != FORMAT) throw BackupException("Este arquivo não é um backup do Ang-Velda.")
            val ver = manifest.optInt("formatVersion", -1)
            if (ver < 1) throw BackupException("Versão de formato inválida no manifesto.")
            if (ver > AppInfo.BACKUP_FORMAT_VERSION)
                throw BackupException("Este backup usa a versão $ver do formato, mais nova que a suportada (${AppInfo.BACKUP_FORMAT_VERSION}). Atualize o Ang-Velda. Nada foi alterado.")
            // Migrações futuras entram aqui: converter os dados da versão `ver` para a atual.

            val hashes = HashMap<String, String>()
            val fa = manifest.optJSONArray("files") ?: throw BackupException("Manifesto sem a lista de arquivos.")
            for (i in 0 until fa.length()) {
                val o = fa.getJSONObject(i); hashes[o.getString("path")] = o.getString("sha256")
            }
            val raw = HashMap<String, ByteArray>()
            for (p in REQUIRED) {
                val b = bytes(p)
                val expected = hashes[p] ?: throw BackupException("O manifesto não lista $p.")
                if (sha256Hex(b) != expected) throw BackupException("O arquivo $p está corrompido (hash não confere).")
                raw[p] = b
            }

            try {
                val topics = json("data/topics.json", raw.getValue("data/topics.json")).getJSONArray("items").let { a -> (0 until a.length()).map { parseTopic(a.getJSONObject(it)) } }
                val entries = json("data/entries.json", raw.getValue("data/entries.json")).getJSONArray("items").let { a -> (0 until a.length()).map { parseEntry(a.getJSONObject(it)) } }
                val conns = json("data/connections.json", raw.getValue("data/connections.json")).getJSONArray("items").let { a -> (0 until a.length()).map { parseConn(a.getJSONObject(it)) } }
                val atts = json("data/attachments.json", raw.getValue("data/attachments.json")).getJSONArray("items").let { a -> (0 until a.length()).map { parseAtt(a.getJSONObject(it)) } }
                val stObj = json("data/settings.json", raw.getValue("data/settings.json")).optJSONObject("settings") ?: JSONObject()
                val settings = HashMap<String, String>()
                for (k in stObj.keys()) settings[k] = stObj.getString(k)

                // Identificadores e referências
                fun checkIds(kind: String, ids: List<String>) {
                    val seen = HashSet<String>()
                    for (id in ids) {
                        if (!ID_RE.matches(id)) throw BackupException("Identificador inválido em $kind: $id")
                        if (!seen.add(id)) throw BackupException("Identificador duplicado em $kind: $id")
                    }
                }
                checkIds("tópicos", topics.map { it.id }); checkIds("registros", entries.map { it.id })
                checkIds("conexões", conns.map { it.id }); checkIds("anexos", atts.map { it.id })
                val topicIds = topics.map { it.id }.toSet()
                val entryIds = entries.map { it.id }.toSet()
                for (t in topics) if (t.parentId != null && t.parentId !in topicIds) throw BackupException("O tópico \"${t.name}\" aponta para um tópico pai inexistente.")
                val byId = topics.associateBy { it.id }
                for (t in topics) {
                    var cur: Topic? = t; var steps = 0
                    while (cur?.parentId != null) {
                        cur = cur.parentId?.let { byId[it] }; steps++
                        if (steps > topics.size) throw BackupException("A hierarquia de tópicos contém um ciclo.")
                    }
                }
                for (e in entries) if (e.topicId != null && e.topicId !in topicIds) throw BackupException("O registro \"${e.title}\" aponta para um tópico inexistente.")
                for (c in conns) if (c.fromId !in entryIds || c.toId !in entryIds) throw BackupException("Há conexões apontando para registros inexistentes.")
                for (a in atts) if (a.entryId !in entryIds) throw BackupException("O anexo \"${a.displayName}\" aponta para um registro inexistente.")

                // Anexos: presença, tamanho e hash
                for ((i, a) in atts.withIndex()) {
                    progress("Verificando anexos (${i + 1}/${atts.size})...")
                    val e = z.getEntry("attachments/${a.id}") ?: throw BackupException("Anexo ausente no ZIP: ${a.displayName}")
                    if (e.size != a.size) throw BackupException("O tamanho do anexo \"${a.displayName}\" não confere.")
                    val h = z.getInputStream(e).use { sha256Hex(it) }
                    if (h != a.sha256) throw BackupException("O anexo \"${a.displayName}\" está corrompido (hash não confere).")
                }
                ParsedBackup(file, ver, manifest.optLong("createdAt", 0L), topics, entries, conns, atts, settings)
            } catch (e: BackupException) {
                throw e
            } catch (e: JSONException) {
                throw BackupException("Dados do backup malformados: ${e.message}")
            }
        }
        return parsed
    }

    // ---------------------------------------------------------------- Restaurar

    private class Plan(
        val topics: List<Topic>, val entries: List<Entry>, val conns: List<Conn>, val atts: List<Attachment>,
        val extract: List<Pair<String, Attachment>>, // novo id -> anexo original
        val skippedTopics: Int, val skippedEntries: Int, val skippedConns: Int
    )

    private fun hashOfFile(f: File): String = f.inputStream().use { sha256Hex(it) }

    private fun buildPlan(
        p: ParsedBackup, mode: RestoreMode,
        curTopics: List<Topic>, curEntries: List<Entry>, curConns: List<Conn>, curAtts: List<Attachment>
    ): Plan {
        val topicMap = HashMap<String, String>()
        val entryMap = HashMap<String, String>()
        val reusedTopics = HashSet<String>()
        val reusedEntries = HashSet<String>()

        if (mode == RestoreMode.MERGE) {
            val tById = curTopics.associateBy { it.id }
            for (t in p.topics) {
                val cur = tById[t.id]
                when {
                    cur == null -> topicMap[t.id] = t.id
                    cur.name == t.name && cur.description == t.description -> { topicMap[t.id] = t.id; reusedTopics.add(t.id) }
                    else -> topicMap[t.id] = UUID.randomUUID().toString()
                }
            }
            val eById = curEntries.associateBy { it.id }
            for (e in p.entries) {
                val cur = eById[e.id]
                when {
                    cur == null -> entryMap[e.id] = e.id
                    cur.title == e.title && cur.content == e.content && cur.type == e.type -> { entryMap[e.id] = e.id; reusedEntries.add(e.id) }
                    else -> entryMap[e.id] = UUID.randomUUID().toString()
                }
            }
        } else {
            p.topics.forEach { topicMap[it.id] = it.id }
            p.entries.forEach { entryMap[it.id] = it.id }
        }

        val newTopics = p.topics.filter { it.id !in reusedTopics }.map { t ->
            t.copy(id = topicMap.getValue(t.id), parentId = t.parentId?.let { topicMap[it] })
        }

        // Em mesclagem, os nós importados são colocados à direita da teia atual para não se sobrepor.
        var dx = 0f
        val toInsert = p.entries.filter { it.id !in reusedEntries }
        if (mode == RestoreMode.MERGE && curEntries.isNotEmpty() && toInsert.isNotEmpty()) {
            val curMax = curEntries.maxOf { it.posX }
            val impMin = toInsert.minOf { it.posX }
            dx = curMax + 250f - impMin
        }
        val newEntries = toInsert.map { e ->
            e.copy(id = entryMap.getValue(e.id), topicId = e.topicId?.let { topicMap[it] }, posX = e.posX + dx)
        }

        val existingPairs = HashSet<String>()
        curConns.forEach { existingPairs.add("${it.fromId}|${it.toId}"); existingPairs.add("${it.toId}|${it.fromId}") }
        val curConnIds = curConns.map { it.id }.toSet()
        val newConns = mutableListOf<Conn>()
        var skippedConns = 0
        for (c in p.conns) {
            val f = entryMap[c.fromId]; val t = entryMap[c.toId]
            if (f == null || t == null) { skippedConns++; continue }
            if (mode == RestoreMode.MERGE && (c.fromId in reusedEntries && c.toId in reusedEntries)) { skippedConns++; continue }
            val key = "$f|$t"
            if (mode == RestoreMode.MERGE && key in existingPairs) { skippedConns++; continue }
            existingPairs.add(key); existingPairs.add("$t|$f")
            val id = if (mode == RestoreMode.MERGE && c.id in curConnIds) UUID.randomUUID().toString() else c.id
            newConns.add(c.copy(id = id, fromId = f, toId = t))
        }

        val curAttById = curAtts.associateBy { it.id }
        val newAtts = mutableListOf<Attachment>()
        val extract = mutableListOf<Pair<String, Attachment>>()
        for (a in p.attachments) {
            if (a.entryId in reusedEntries) continue
            val targetEntry = entryMap.getValue(a.entryId)
            var id = a.id
            val f = FileStore.attFile(ctx, id)
            var reuse = false
            if (f.exists()) {
                val row = curAttById[id]
                val sameOwner = mode == RestoreMode.REPLACE || (row != null && row.entryId == targetEntry)
                if (sameOwner && hashOfFile(f) == a.sha256) reuse = true else id = UUID.randomUUID().toString()
            } else if (mode == RestoreMode.MERGE && curAttById.containsKey(id)) {
                id = UUID.randomUUID().toString()
            }
            newAtts.add(a.copy(id = id, entryId = targetEntry))
            if (!reuse) extract.add(id to a)
        }
        return Plan(newTopics, newEntries, newConns, newAtts, extract, reusedTopics.size, reusedEntries.size, skippedConns)
    }

    suspend fun restore(p: ParsedBackup, mode: RestoreMode, progress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val attDir = FileStore.attDir(ctx)
        val moved = mutableListOf<File>()
        var committed = false
        try {
            val needed = p.attachmentBytes + 64L * 1024 * 1024
            if (ctx.filesDir.usableSpace < needed) throw BackupException("Espaço insuficiente no aparelho para restaurar. Nada foi alterado.")

            var safetyName: String? = null
            if (mode == RestoreMode.REPLACE) {
                progress("Criando backup de segurança do estado atual...")
                val dir = File(ctx.filesDir, "safety_backups").apply { mkdirs() }
                val f = File(dir, "pre-restauracao_${stamp()}.zip")
                writeZip(f)
                safetyName = f.name
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
            }

            val plan = buildPlan(p, mode, dao.allTopics(), dao.allEntries(), dao.allConns(), dao.allAttachments())

            ZipFile(p.file).use { z ->
                for ((i, pair) in plan.extract.withIndex()) {
                    val (newId, a) = pair
                    progress("Restaurando anexos (${i + 1}/${plan.extract.size})...")
                    val ze = z.getEntry("attachments/${a.id}") ?: throw BackupException("Anexo ausente no ZIP: ${a.displayName}")
                    val part = File(attDir, "$newId.part")
                    val md = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    z.getInputStream(ze).use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                count += n
                                if (count > ze.size) { part.delete(); throw BackupException("Anexo maior que o declarado: ${a.displayName}") }
                                md.update(buf, 0, n); out.write(buf, 0, n)
                            }
                        }
                    }
                    val h = md.digest().joinToString("") { "%02x".format(it) }
                    if (h != a.sha256) { part.delete(); throw BackupException("Anexo corrompido: ${a.displayName}") }
                    val finalFile = File(attDir, newId)
                    if (!part.renameTo(finalFile)) { part.delete(); throw IOException("Não foi possível gravar o anexo ${a.displayName}") }
                    moved.add(finalFile)
                }
            }

            progress("Aplicando os dados...")
            db.withTransaction {
                if (mode == RestoreMode.REPLACE) { dao.clearEntries(); dao.clearTopics() }
                dao.upsertTopics(plan.topics)
                dao.upsertEntries(plan.entries)
                dao.insertConns(plan.conns)
                dao.upsertAttachments(plan.atts)
            }
            committed = true

            progress("Verificando o resultado...")
            val dbEntries = dao.allEntries().map { it.id }.toSet()
            val dbTopics = dao.allTopics().map { it.id }.toSet()
            val dbConns = dao.allConns().map { it.id }.toSet()
            val dbAtts = dao.allAttachments().map { it.id }.toSet()
            val ok = plan.entries.all { it.id in dbEntries } && plan.topics.all { it.id in dbTopics } &&
                plan.conns.all { it.id in dbConns } && plan.atts.all { it.id in dbAtts && FileStore.attFile(ctx, it.id).exists() } &&
                (mode != RestoreMode.REPLACE || (dbEntries.size == plan.entries.size && dbTopics.size == plan.topics.size && dbAtts.size == plan.atts.size))
            if (!ok) throw BackupException("A verificação final encontrou diferenças. Confira o Cofre > Verificar integridade.")

            if (mode == RestoreMode.REPLACE) {
                val keep = plan.atts.map { it.id }.toSet()
                attDir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
                withContext(Dispatchers.Main) { prefs.importSettings(p.settings) }
            }

            buildString {
                append(if (mode == RestoreMode.REPLACE) "Restauração (substituição) concluída e verificada.\n" else "Mesclagem concluída e verificada.\n")
                append("Registros: ${plan.entries.size}\nTópicos: ${plan.topics.size}\nConexões: ${plan.conns.size}\nAnexos: ${plan.atts.size}\n")
                if (mode == RestoreMode.MERGE) {
                    append("Ignorados por já existirem: ${plan.skippedEntries} registros, ${plan.skippedTopics} tópicos, ${plan.skippedConns} conexões.\n")
                    append("Preferências atuais mantidas.")
                } else {
                    append("Preferências de aparência restauradas.\n")
                    append("Backup de segurança do estado anterior: $safetyName (armazenamento interno do app).")
                }
            }
        } catch (t: Throwable) {
            if (!committed) moved.forEach { it.delete() }
            attDirCleanup(attDir)
            throw t
        } finally {
            p.file.delete()
        }
    }

    private fun attDirCleanup(dir: File) { dir.listFiles()?.forEach { if (it.name.endsWith(".part")) it.delete() } }

    // ---------------------------------------------------------------- Integridade

    suspend fun integrity(): String = withContext(Dispatchers.IO) {
        val issues = mutableListOf<String>()
        val atts = dao.allAttachments()
        val topics = dao.allTopics()
        for (a in atts) {
            val f = FileStore.attFile(ctx, a.id)
            when {
                !f.exists() -> issues.add("Arquivo ausente: ${a.displayName}")
                f.length() != a.size -> issues.add("Tamanho diferente do registrado: ${a.displayName}")
                hashOfFile(f) != a.sha256 -> issues.add("Conteúdo alterado: ${a.displayName}")
            }
        }
        val ids = atts.map { it.id }.toSet()
        FileStore.attDir(ctx).listFiles()?.forEach { if (it.name !in ids) issues.add("Arquivo órfão no armazenamento: ${it.name}") }
        val tIds = topics.map { it.id }.toSet()
        val byId = topics.associateBy { it.id }
        for (t in topics) {
            if (t.parentId != null && t.parentId !in tIds) issues.add("Tópico \"${t.name}\" com pai inexistente")
            var cur: Topic? = t; var steps = 0
            while (cur?.parentId != null && steps <= topics.size) { cur = cur.parentId?.let { byId[it] }; steps++ }
            if (steps > topics.size) issues.add("Ciclo na hierarquia envolvendo \"${t.name}\"")
        }
        if (issues.isEmpty()) "Nenhum problema encontrado.\nAnexos verificados: ${atts.size}\nTópicos verificados: ${topics.size}"
        else "Problemas encontrados (${issues.size}):\n" + issues.take(40).joinToString("\n• ", prefix = "• ")
    }
}
