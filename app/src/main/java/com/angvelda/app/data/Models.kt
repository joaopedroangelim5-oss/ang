package com.angvelda.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "topics", indices = [Index("parentId")])
data class Topic(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val parentId: String? = null,
    val color: Int? = null,
    val icon: String? = null,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(
        entity = Topic::class,
        parentColumns = ["id"],
        childColumns = ["topicId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("topicId")]
)
data class Entry(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val type: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val topicId: String? = null,
    val tags: String = "",
    val color: Int? = null,
    val icon: String? = null,
    val archived: Boolean = false,
    val posX: Float = 0f,
    val posY: Float = 0f
) {
    fun tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "connections",
    foreignKeys = [
        ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["fromId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["toId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("fromId"), Index("toId")]
)
data class Conn(
    @PrimaryKey val id: String,
    val fromId: String,
    val toId: String,
    val relation: String = "Relacionado a",
    val label: String = "",
    val color: Int? = null
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")]
)
data class Attachment(
    @PrimaryKey val id: String,
    val entryId: String,
    val displayName: String,
    val size: Long,
    val mime: String,
    val sha256: String,
    val createdAt: Long
)

data class TypeInfo(val key: String, val label: String, val emoji: String)

object EntryTypes {
    val all = listOf(
        TypeInfo("pensamento", "Pensamento", "💭"),
        TypeInfo("frase", "Frase", "❝"),
        TypeInfo("ideia", "Ideia", "💡"),
        TypeInfo("nota", "Nota", "📝"),
        TypeInfo("texto_longo", "Texto longo", "📄"),
        TypeInfo("documento", "Documento", "📎"),
        TypeInfo("livre", "Registro livre", "✦")
    )
    fun label(key: String) = all.firstOrNull { it.key == key }?.label ?: "Registro livre"
    fun emoji(key: String) = all.firstOrNull { it.key == key }?.emoji ?: "✦"
}

fun descendantsOf(id: String, topics: List<Topic>): Set<String> {
    val children = topics.groupBy { it.parentId }
    val out = mutableSetOf<String>()
    val stack = mutableListOf(id)
    while (stack.isNotEmpty()) {
        val cur = stack.removeAt(stack.lastIndex)
        for (c in children[cur].orEmpty()) if (out.add(c.id)) stack.add(c.id)
    }
    return out
}

/** Tópicos em ordem de árvore (profundidade primeiro), com o nível de cada um. */
fun flattenTopics(topics: List<Topic>): List<Pair<Topic, Int>> {
    val children = topics.groupBy { it.parentId }
    val ids = topics.map { it.id }.toSet()
    val out = mutableListOf<Pair<Topic, Int>>()
    val visited = mutableSetOf<String>()
    fun walk(parent: String?, depth: Int) {
        for (t in children[parent].orEmpty()) {
            if (!visited.add(t.id)) continue
            out.add(t to depth)
            walk(t.id, depth + 1)
        }
    }
    walk(null, 0)
    // tópicos cujo pai não existe são tratados como raiz
    for (t in topics) if (t.parentId != null && t.parentId !in ids && visited.add(t.id)) {
        out.add(t to 0); walk(t.id, 1)
    }
    return out
}

fun topicPath(id: String?, topics: List<Topic>): String {
    if (id == null) return "Sem tópico"
    val byId = topics.associateBy { it.id }
    val parts = mutableListOf<String>()
    var cur = byId[id]
    var guard = 0
    while (cur != null && guard++ < 64) {
        parts.add(0, cur.name)
        cur = cur.parentId?.let { byId[it] }
    }
    return if (parts.isEmpty()) "Sem tópico" else parts.joinToString(" › ")
}

@Dao
interface AppDao {
    @Query("SELECT * FROM entries ORDER BY modifiedAt DESC") fun observeEntries(): Flow<List<Entry>>
    @Query("SELECT * FROM topics ORDER BY sortOrder, name COLLATE NOCASE") fun observeTopics(): Flow<List<Topic>>
    @Query("SELECT * FROM connections") fun observeConns(): Flow<List<Conn>>
    @Query("SELECT * FROM attachments ORDER BY createdAt DESC") fun observeAttachments(): Flow<List<Attachment>>

    @Query("SELECT * FROM entries") suspend fun allEntries(): List<Entry>
    @Query("SELECT * FROM topics") suspend fun allTopics(): List<Topic>
    @Query("SELECT * FROM connections") suspend fun allConns(): List<Conn>
    @Query("SELECT * FROM attachments") suspend fun allAttachments(): List<Attachment>
    @Query("SELECT * FROM topics WHERE id = :id") suspend fun topic(id: String): Topic?

    @Upsert suspend fun upsertEntry(e: Entry)
    @Upsert suspend fun upsertEntries(list: List<Entry>)
    @Upsert suspend fun upsertTopic(t: Topic)
    @Upsert suspend fun upsertTopics(list: List<Topic>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertConn(c: Conn)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertConns(list: List<Conn>)
    @Upsert suspend fun upsertAttachment(a: Attachment)
    @Upsert suspend fun upsertAttachments(list: List<Attachment>)

    @Query("DELETE FROM entries WHERE id IN (:ids)") suspend fun deleteEntries(ids: List<String>)
    @Query("DELETE FROM connections WHERE id = :id") suspend fun deleteConn(id: String)
    @Query("DELETE FROM attachments WHERE id = :id") suspend fun deleteAttachment(id: String)
    @Query("DELETE FROM topics WHERE id = :id") suspend fun deleteTopic(id: String)
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM topics") suspend fun clearTopics()

    @Query("UPDATE entries SET posX = :x, posY = :y WHERE id = :id") suspend fun updatePos(id: String, x: Float, y: Float)
    @Query("UPDATE entries SET topicId = :newId WHERE topicId = :old") suspend fun moveEntriesTopic(old: String, newId: String?)
    @Query("UPDATE topics SET parentId = :newId WHERE parentId = :old") suspend fun reparent(old: String, newId: String?)
    @Query("UPDATE entries SET topicId = :topic WHERE id IN (:ids)") suspend fun setTopic(ids: List<String>, topic: String?)
    @Query("UPDATE entries SET archived = :archived WHERE id IN (:ids)") suspend fun setArchived(ids: List<String>, archived: Boolean)
}

@Database(
    entities = [Topic::class, Entry::class, Conn::class, Attachment::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "angvelda.db")
                .build().also { instance = it }
        }
    }
}
