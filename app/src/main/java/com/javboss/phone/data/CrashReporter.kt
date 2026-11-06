package com.javboss.phone.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录：全局未捕获异常落盘 → 下次冷启动弹窗 → 一键调起手机邮件 App 发给维护者。
 * 只写应用私有目录，不申请权限、不接第三方 SDK、不向任何服务器上传。
 */
object CrashReporter {

    private const val DIR_NAME = "crash_logs"
    private const val MAX_FILES = 20
    private const val REPORT_EMAIL = "jiedi720@foxmail.com"

    data class Report(val file: File, val text: String)

    /** 在 Application.onCreate 里尽早注册；保留系统原 handler，写完日志后照常交给它 */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 崩溃现场写盘必须包在 runCatching 里，绝不能因为记录失败而掩盖原始异常
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(app: Application, thread: Thread, throwable: Throwable) {
        val dir = File(app.filesDir, DIR_NAME).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        file.writeText(
            buildString {
                appendLine("JavBoss Modified 崩溃报告")
                appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("版本: ${versionInfo(app)}")
                appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}")
                appendLine("线程: ${thread.name}")
                appendLine()
                appendLine("堆栈:")
                append(stack)
            },
        )
        trim(dir)
    }

    @Suppress("DEPRECATION")
    private fun versionInfo(context: Context): String = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            pi.versionCode.toLong()
        }
        "${pi.versionName} (versionCode=$code)"
    }.getOrDefault("未知")

    /** 只保留最近 MAX_FILES 个文件，避免长期堆积 */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.name } ?: return
        files.drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }

    /** 待发送的崩溃记录（.txt，按时间倒序）；已发送的重命名为 .txt.sent，不再计入 */
    fun pendingReports(context: Context): List<Report> {
        val dir = File(context.filesDir, DIR_NAME)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?.map { Report(it, runCatching { it.readText() }.getOrDefault("")) }
            ?: emptyList()
    }

    fun reportEmail(): String = REPORT_EMAIL

    private fun subject(): String = "JavBoss Modified 崩溃报告"

    /** 多份记录拼成邮件正文，含每份完整堆栈 */
    fun body(reports: List<Report>): String = buildString {
        appendLine("共 ${reports.size} 份崩溃记录，按时间倒序：")
        appendLine()
        reports.forEachIndexed { index, report ->
            appendLine("===== 记录 ${index + 1}: ${report.file.name} =====")
            appendLine(report.text)
            appendLine()
        }
    }

    /** 调起手机邮件 App，收件人 / 主题 / 正文已预填；没有邮件 App 时给个提示 */
    fun send(context: Context, reports: List<Report>) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject())
            putExtra(Intent.EXTRA_TEXT, body(reports))
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "发送崩溃报告"))
        }.onFailure {
            Toast.makeText(context, "未找到可用的邮件 App", Toast.LENGTH_SHORT).show()
        }
    }

    /** 标记已处理：重命名而非删除，记录仍留在私有目录但不再重复弹窗 */
    fun markSent(reports: List<Report>) {
        reports.forEach { report ->
            runCatching {
                report.file.renameTo(File(report.file.parentFile, "${report.file.name}.sent"))
            }
        }
    }
}
