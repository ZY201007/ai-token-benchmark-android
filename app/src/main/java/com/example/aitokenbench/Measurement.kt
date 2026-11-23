package com.example.aitokenbench

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 一次「任务组 × 模型」的测评记录 */
data class Measurement(
    val id: Long = 0L,
    val taskIndex: Int,
    val modelId: String,
    val modelName: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val latencyMs: Long,
    val costCny: Double,
    val mock: Boolean,
    val promptChars: Int,
    val timestamp: Long,
    val error: String? = null
)

/** 表结构与 SQL 约定见 README「SQLite schema」 */
class DbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "aitokenbench.db"
        const val DB_VERSION = 1
        const val TABLE = "measurements"

        const val COL_ID = "id"
        const val COL_TASK_INDEX = "task_index"
        const val COL_MODEL_ID = "model_id"
        const val COL_MODEL_NAME = "model_name"
        const val COL_INPUT_TOKENS = "input_tokens"
        const val COL_OUTPUT_TOKENS = "output_tokens"
        const val COL_LATENCY_MS = "latency_ms"
        const val COL_COST_CNY = "cost_cny"
        const val COL_MOCK = "mock"
        const val COL_PROMPT_CHARS = "prompt_chars"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_ERROR = "error"

        private const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TASK_INDEX INTEGER NOT NULL,
                $COL_MODEL_ID TEXT NOT NULL,
                $COL_MODEL_NAME TEXT NOT NULL,
                $COL_INPUT_TOKENS INTEGER NOT NULL,
                $COL_OUTPUT_TOKENS INTEGER NOT NULL,
                $COL_LATENCY_MS INTEGER NOT NULL,
                $COL_COST_CNY REAL NOT NULL,
                $COL_MOCK INTEGER NOT NULL,
                $COL_PROMPT_CHARS INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_ERROR TEXT
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }
}

/** 对 SQLite 的薄封装，全部数据只写入本机数据库，不做任何网络上传 */
class MeasurementRepository(context: Context) {
    private val dbHelper = DbHelper(context)

    fun insert(m: Measurement): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DbHelper.COL_TASK_INDEX, m.taskIndex)
            put(DbHelper.COL_MODEL_ID, m.modelId)
            put(DbHelper.COL_MODEL_NAME, m.modelName)
            put(DbHelper.COL_INPUT_TOKENS, m.inputTokens)
            put(DbHelper.COL_OUTPUT_TOKENS, m.outputTokens)
            put(DbHelper.COL_LATENCY_MS, m.latencyMs)
            put(DbHelper.COL_COST_CNY, m.costCny)
            put(DbHelper.COL_MOCK, if (m.mock) 1 else 0)
            put(DbHelper.COL_PROMPT_CHARS, m.promptChars)
            put(DbHelper.COL_TIMESTAMP, m.timestamp)
            put(DbHelper.COL_ERROR, m.error)
        }
        return db.insert(DbHelper.TABLE, null, values)
    }

    /** 按时间倒序读取，限制条数避免一次性加载过多 */
    fun recent(limit: Int = 200): List<Measurement> {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            DbHelper.TABLE,
            null,
            null,
            null,
            null,
            null,
            "${DbHelper.COL_ID} DESC LIMIT $limit"
        )
        return cursor.use { readAll(it) }
    }

    /** 全量读取（用于 CSV 导出） */
    fun all(): List<Measurement> {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            DbHelper.TABLE, null, null, null, null, null,
            "${DbHelper.COL_ID} ASC"
        )
        return cursor.use { readAll(it) }
    }

    fun clear() {
        dbHelper.writableDatabase.delete(DbHelper.TABLE, null, null)
    }

    private fun readAll(cursor: Cursor): List<Measurement> {
        val result = mutableListOf<Measurement>()
        if (!cursor.moveToFirst()) return result
        do {
            result.add(
                Measurement(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(DbHelper.COL_ID)),
                    taskIndex = cursor.getInt(cursor.getColumnIndexOrThrow(DbHelper.COL_TASK_INDEX)),
                    modelId = cursor.getString(cursor.getColumnIndexOrThrow(DbHelper.COL_MODEL_ID)),
                    modelName = cursor.getString(cursor.getColumnIndexOrThrow(DbHelper.COL_MODEL_NAME)),
                    inputTokens = cursor.getInt(cursor.getColumnIndexOrThrow(DbHelper.COL_INPUT_TOKENS)),
                    outputTokens = cursor.getInt(cursor.getColumnIndexOrThrow(DbHelper.COL_OUTPUT_TOKENS)),
                    latencyMs = cursor.getLong(cursor.getColumnIndexOrThrow(DbHelper.COL_LATENCY_MS)),
                    costCny = cursor.getDouble(cursor.getColumnIndexOrThrow(DbHelper.COL_COST_CNY)),
                    mock = cursor.getInt(cursor.getColumnIndexOrThrow(DbHelper.COL_MOCK)) == 1,
                    promptChars = cursor.getInt(cursor.getColumnIndexOrThrow(DbHelper.COL_PROMPT_CHARS)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DbHelper.COL_TIMESTAMP)),
                    error = cursor.getString(cursor.getColumnIndexOrThrow(DbHelper.COL_ERROR))
                )
            )
        } while (cursor.moveToNext())
        return result
    }
}
