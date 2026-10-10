package com.angvelda.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class BackupRecord(
    val file: String,
    val createdAt: Long,
    val savedAt: Long,
    val size: Long,
    val entries: Int,
    val topics: Int
)

object AppInfo {
    const val NAME = "Ang-Velda"
    const val VERSION = "1.0.0"
    const val BACKUP_FORMAT_VERSION = 1
}

/** Preferências de aparência. Cada propriedade altera a interface real e entra no backup. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("angvelda_prefs", Context.MODE_PRIVATE)
    private val map = mutableStateMapOf<String, String>()

    init {
        for (k in SETTING_KEYS) sp.getString(k, null)?.let { map[k] = it }
    }

    private fun int(k: String, d: Int) = map[k]?.toIntOrNull() ?: d
    private fun float(k: String, d: Float) = map[k]?.toFloatOrNull() ?: d
    private fun bool(k: String, d: Boolean) = map[k]?.toBooleanStrictOrNull() ?: d
    private fun put(k: String, v: Any) {
        map[k] = v.toString()
        sp.edit().putString(k, v.toString()).apply()
    }

    /** 0 = sistema, 1 = escuro, 2 = claro */
    var themeMode: Int
        get() = int("themeMode", 1)
        set(v) = put("themeMode", v)
    var accent: Int
        get() = int("accent", 0xFF8B5CF6.toInt())
        set(v) = put("accent", v)
    /** 0 = círculo luminoso, 1 = cartão */
    var nodeStyle: Int
        get() = int("nodeStyle", 0)
        set(v) = put("nodeStyle", v)
    var linkColor: Int
        get() = int("linkColor", 0xFFA78BFA.toInt())
        set(v) = put("linkColor", v)
    /** 0 = preenchido, 1 = contorno, 2 = faixa lateral */
    var cardStyle: Int
        get() = int("cardStyle", 0)
        set(v) = put("cardStyle", v)
    /** 0 = confortável, 1 = compacto */
    var density: Int
        get() = int("density", 0)
        set(v) = put("density", v)
    var textScale: Float
        get() = float("textScale", 1f)
        set(v) = put("textScale", v)
    var animations: Boolean
        get() = bool("animations", true)
        set(v) = put("animations", v)
    /** 0 = árvore, 1 = cartões */
    var topicStyle: Int
        get() = int("topicStyle", 0)
        set(v) = put("topicStyle", v)

    fun exportSettings(): Map<String, String> = mapOf(
        "themeMode" to themeMode.toString(),
        "accent" to accent.toString(),
        "nodeStyle" to nodeStyle.toString(),
        "linkColor" to linkColor.toString(),
        "cardStyle" to cardStyle.toString(),
        "density" to density.toString(),
        "textScale" to textScale.toString(),
        "animations" to animations.toString(),
        "topicStyle" to topicStyle.toString()
    )

    fun importSettings(m: Map<String, String>) {
        for ((k, v) in m) {
            if (k !in SETTING_KEYS) continue
            val ok = when (k) {
                "textScale" -> v.toFloatOrNull()?.let { it in 0.5f..2f } == true
                "animations" -> v.toBooleanStrictOrNull() != null
                else -> v.toIntOrNull() != null
            }
            if (ok) put(k, v)
        }
    }

    var history: List<BackupRecord> by mutableStateOf(loadHistory())
        private set

    private fun loadHistory(): List<BackupRecord> {
        val s = sp.getString("backupHistory", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                BackupRecord(o.getString("file"), o.getLong("createdAt"), o.getLong("savedAt"), o.getLong("size"), o.getInt("entries"), o.getInt("topics"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addBackupRecord(r: BackupRecord) {
        val list = (listOf(r) + history).take(10)
        history = list
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("file", it.file).put("createdAt", it.createdAt).put("savedAt", it.savedAt)
                    .put("size", it.size).put("entries", it.entries).put("topics", it.topics)
            )
        }
        sp.edit().putString("backupHistory", arr.toString()).apply()
    }

    companion object {
        val SETTING_KEYS = setOf(
            "themeMode", "accent", "nodeStyle", "linkColor", "cardStyle",
            "density", "textScale", "animations", "topicStyle"
        )
    }
}
