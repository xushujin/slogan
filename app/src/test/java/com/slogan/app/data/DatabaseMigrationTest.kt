package com.slogan.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * 用真实 SQLite（sqlite-jdbc，仅测试期依赖）执行迁移语句，
 * 验证三件事：
 * 1. v1 数据在升级后完整保留；
 * 2. 迁移得到的表结构与“全新安装”的结构完全一致；
 * 3. 迁移语句里不存在任何破坏性操作。
 */
class DatabaseMigrationTest {

    private lateinit var migrated: Connection
    private lateinit var fresh: Connection

    @Before
    fun setUp() {
        migrated = DriverManager.getConnection("jdbc:sqlite::memory:")
        fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun tearDown() {
        migrated.close()
        fresh.close()
    }

    @Test
    fun migrationPreservesExistingRowsAndFillsDefaults() {
        migrated.execAll(SloganSchema.createV1)
        migrated.createStatement().use { statement ->
            statement.executeUpdate(
                """
                INSERT INTO presets (title, body, voice_name, interval_ms, repeat_count, volume, created_at, updated_at)
                VALUES ('旧记录', '前方施工，请绕行', 'zh-cn-x-ccc-local', 2000, 5, 0.7, 100, 200)
                """.trimIndent(),
            )
        }

        migrated.execAll(SloganSchema.migrationPath(1, 2))

        migrated.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT title, body, voice_name, interval_ms, repeat_count, volume, created_at, updated_at,
                       speech_rate, pitch, boost
                FROM presets
                """.trimIndent(),
            ).use { cursor ->
                assertTrue("迁移后应仍有一条记录", cursor.next())
                assertEquals("旧记录", cursor.getString(1))
                assertEquals("前方施工，请绕行", cursor.getString(2))
                assertEquals("zh-cn-x-ccc-local", cursor.getString(3))
                assertEquals(2000, cursor.getInt(4))
                assertEquals(5, cursor.getInt(5))
                assertEquals(0.7, cursor.getDouble(6), 1e-6)
                assertEquals(100L, cursor.getLong(7))
                assertEquals(200L, cursor.getLong(8))
                assertEquals(1.0, cursor.getDouble(9), 1e-6)
                assertEquals(1.0, cursor.getDouble(10), 1e-6)
                assertEquals(1, cursor.getInt(11))
                assertFalse("不应出现第二条记录", cursor.next())
            }
        }
    }

    @Test
    fun migratedSchemaMatchesFreshInstallSchema() {
        migrated.execAll(SloganSchema.createV1)
        migrated.execAll(SloganSchema.migrationPath(1, 2))
        fresh.execAll(SloganSchema.createLatest)

        assertEquals(fresh.snapshot(), migrated.snapshot())
    }

    @Test
    fun migratedSchemaAcceptsInsertsWithDefaults() {
        migrated.execAll(SloganSchema.createV1)
        migrated.execAll(SloganSchema.migrationPath(1, 2))

        migrated.createStatement().use { statement ->
            statement.executeUpdate(
                "INSERT INTO presets (title, body, created_at, updated_at) VALUES ('新记录', '测试', 300, 400)",
            )
            statement.executeQuery(
                "SELECT speech_rate, pitch, boost FROM presets WHERE title = '新记录'",
            ).use { cursor ->
                assertTrue(cursor.next())
                assertEquals(1.0, cursor.getDouble(1), 1e-6)
                assertEquals(1.0, cursor.getDouble(2), 1e-6)
                assertEquals(1, cursor.getInt(3))
            }
        }
    }

    @Test
    fun migrationStatementsAreNonDestructive() {
        val forbidden = Regex("(?i)\\b(drop|delete|truncate|vacuum)\\b")
        SloganSchema.migrations.forEach { (fromVersion, statements) ->
            statements.forEach { statement ->
                assertFalse(
                    "迁移 $fromVersion 含破坏性语句: $statement",
                    forbidden.containsMatchIn(statement),
                )
            }
        }
    }

    @Test
    fun rejectsDowngrade() {
        assertThrows(IllegalArgumentException::class.java) {
            SloganSchema.migrationPath(2, 1)
        }
    }

    @Test
    fun rejectsMissingMigrationStep() {
        assertThrows(IllegalStateException::class.java) {
            SloganSchema.migrationPath(1, SloganSchema.VERSION + 1)
        }
    }

    @Test
    fun everyVersionHasMigrationUntilLatest() {
        for (version in 1 until SloganSchema.VERSION) {
            assertTrue("缺少 $version 的迁移", SloganSchema.migrations.containsKey(version))
        }
        assertEquals(SloganSchema.VERSION - 1, SloganSchema.migrations.size)
    }

    private fun Connection.execAll(statements: List<String>) {
        createStatement().use { statement ->
            statements.forEach { sql -> statement.execute(sql) }
        }
    }

    private fun Connection.snapshot(): Map<String, List<String>> {
        val snapshot = linkedMapOf<String, List<String>>()

        val tableNames = mutableListOf<String>()
        createStatement().use { statement ->
            statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            ).use { cursor ->
                while (cursor.next()) tableNames += cursor.getString(1)
            }
        }

        for (table in tableNames) {
            val columns = mutableListOf<String>()
            createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info($table)").use { cursor ->
                    while (cursor.next()) {
                        columns += listOf(
                            cursor.getString("name"),
                            cursor.getString("type"),
                            cursor.getInt("notnull").toString(),
                            cursor.getString("dflt_value").orEmpty(),
                            cursor.getInt("pk").toString(),
                        ).joinToString("|")
                    }
                }
            }
            snapshot["table:$table"] = columns.sorted()
        }

        val indexes = mutableListOf<String>()
        createStatement().use { statement ->
            statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            ).use { cursor ->
                while (cursor.next()) indexes += cursor.getString(1)
            }
        }
        snapshot["indexes"] = indexes

        return snapshot
    }
}
