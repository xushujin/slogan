package com.slogan.app.core.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 极简崩溃记录器。
 *
 * 只做一件事：把 Java 层未捕获异常的堆栈写进应用私有目录，然后交回系统默认处理器
 * （应用照常崩溃，行为不变）。目的是让没有 adb 的用户也能在「关于」页把堆栈读出来。
 *
 * 局限要说清楚：原生崩溃（SIGSEGV 等）不经过这里，抓不到。
 */
object CrashRecorder {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 12_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return null
        return runCatching { file.readText().take(MAX_CHARS) }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val content = buildString {
            append("时间：").append(timestamp).append('\n')
            append("线程：").append(thread.name).append('\n')
            append("异常：").append(error.javaClass.name).append('\n')
            append("版本：").append(context.packageName).append('\n')
            append("---- 堆栈 ----\n")
            append(stack)
        }
        runCatching { File(context.filesDir, FILE_NAME).writeText(content.take(MAX_CHARS)) }
    }
}
