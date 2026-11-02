package com.slogan.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 原生 SQLite 封装。升级只走 [SloganSchema.migrations] 中的显式语句；
 * 任何未覆盖的版本路径都会抛异常，而不是删库重建。
 */
class SloganDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    SloganSchema.DATABASE_NAME,
    null,
    SloganSchema.VERSION,
) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SloganSchema.createLatest.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        SloganSchema.migrationPath(oldVersion, newVersion).forEach(db::execSQL)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException(
            "检测到数据库降级（$oldVersion -> $newVersion）。为避免数据丢失，应用拒绝自动重建数据库。",
        )
    }
}
