package com.slogan.app.data

/**
 * 数据库结构的唯一描述处。
 *
 * 约定：
 * - [createLatest] 只用于全新安装，必须与“v1 + 全部迁移”得到完全一致的列集合。
 * - 每次 schema 变更都要：VERSION +1、追加一条迁移语句列表、同步 createLatest。
 * - 迁移只允许 ALTER/CREATE/UPDATE 等非破坏性语句；禁止 DROP TABLE、删列、重建表。
 */
object SloganSchema {

    const val DATABASE_NAME = "slogan.db"
    const val VERSION = 2

    val createV1: List<String> = listOf(
        """
        CREATE TABLE presets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            body TEXT NOT NULL,
            voice_name TEXT,
            interval_ms INTEGER NOT NULL DEFAULT 1500,
            repeat_count INTEGER NOT NULL DEFAULT 3,
            volume REAL NOT NULL DEFAULT 0.8,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
    )

    /** 1 → 2：新增语速/音调/扩音开关，并引入设置表与合成缓存表。 */
    val migration1To2: List<String> = listOf(
        "ALTER TABLE presets ADD COLUMN speech_rate REAL NOT NULL DEFAULT 1.0",
        "ALTER TABLE presets ADD COLUMN pitch REAL NOT NULL DEFAULT 1.0",
        "ALTER TABLE presets ADD COLUMN boost INTEGER NOT NULL DEFAULT 1",
        """
        CREATE TABLE settings (
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE synthesis_cache (
            cache_key TEXT PRIMARY KEY NOT NULL,
            file_path TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            last_used_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX index_presets_updated_at ON presets(updated_at)",
        "CREATE INDEX index_synthesis_cache_last_used_at ON synthesis_cache(last_used_at)",
    )

    val migrations: Map<Int, List<String>> = mapOf(
        1 to migration1To2,
    )

    val createLatest: List<String> = listOf(
        """
        CREATE TABLE presets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            body TEXT NOT NULL,
            voice_name TEXT,
            interval_ms INTEGER NOT NULL DEFAULT 1500,
            repeat_count INTEGER NOT NULL DEFAULT 3,
            volume REAL NOT NULL DEFAULT 0.8,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            speech_rate REAL NOT NULL DEFAULT 1.0,
            pitch REAL NOT NULL DEFAULT 1.0,
            boost INTEGER NOT NULL DEFAULT 1
        )
        """.trimIndent(),
        """
        CREATE TABLE settings (
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE synthesis_cache (
            cache_key TEXT PRIMARY KEY NOT NULL,
            file_path TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            last_used_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX index_presets_updated_at ON presets(updated_at)",
        "CREATE INDEX index_synthesis_cache_last_used_at ON synthesis_cache(last_used_at)",
    )

    /** 迁移路径缺失时直接抛错，绝不回退到删库重建。 */
    fun migrationPath(fromVersion: Int, toVersion: Int): List<String> {
        if (fromVersion == toVersion) return emptyList()
        require(fromVersion < toVersion) { "不支持降级：$fromVersion -> $toVersion" }
        val statements = mutableListOf<String>()
        for (version in fromVersion until toVersion) {
            val step = migrations[version]
                ?: throw IllegalStateException("缺少 $version -> ${version + 1} 的显式迁移语句")
            statements += step
        }
        return statements
    }
}
