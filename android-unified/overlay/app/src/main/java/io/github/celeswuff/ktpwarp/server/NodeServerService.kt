package io.github.celeswuff.ktpwarp.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.celeswuff.ktpwarp.R
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class NodeServerService : Service() {

    companion object {
        const val PROJECT_DIR = "nodejs-project"
        const val PROJECT_ZIP = "nodejs-project.zip"
        const val DEFAULT_CONFIG_ASSET = "nodejs-config.example.ts"
        const val CONFIG_FILE = "server-config.ts"
        const val LOG_FILE = "ktpwarp-server.log"
        const val DEFAULT_LOCAL_ADDRESS =
            "ws://127.0.0.1:11451/kfccrazythursdayvme50"

        private const val CHANNEL_ID = "ktpwarp_server"
        private const val NOTIFICATION_ID = 11451

        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("node")
        }

        fun configFile(context: Context): File = File(context.filesDir, CONFIG_FILE)
        fun logFile(context: Context): File = File(context.filesDir, LOG_FILE)

        fun defaultConfig(context: Context): String =
            context.assets.open(DEFAULT_CONFIG_ASSET)
                .bufferedReader()
                .use { it.readText() }

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NodeServerService::class.java)
            )
        }

        fun startIfConfigured(context: Context) {
            if (configFile(context).isFile) start(context)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NodeServerService::class.java))
        }
    }

    @Volatile
    private var started = false

    private external fun startNodeWithArguments(
        arguments: Array<String>,
        logPath: String
    ): Int

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("ktpWarp")
                .setContentText("内置 ktpwarp-server 正在启动/运行")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_STICKY

        val userConfig = configFile(this)
        val logFile = logFile(this)

        runCatching {
            logFile.writeText("[Android] 正在启动内置 ktpwarp-server…\n")
        }

        if (!userConfig.isFile) {
            appendServiceLog("[Android] 启动失败：找不到 server-config.ts。")
            stopSelf()
            return START_NOT_STICKY
        }

        val configText = runCatching { userConfig.readText() }.getOrDefault("")
        if (
            configText.contains("ktp0123456789") ||
            configText.contains("password123")
        ) {
            appendServiceLog(
                "[Android] 启动失败：config.ts 仍包含上游示例账号/密码。请先填写真实课堂派账号。"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        started = true
        Thread({
            try {
                val nodeDir = prepareNodeProject(userConfig)
                appendServiceLog("[Android] Node.js 运行时启动中…")

                val exitCode = startNodeWithArguments(
                    arrayOf(
                        "node",
                        "--experimental-specifier-resolution=node",
                        File(nodeDir, "main.js").absolutePath
                    ),
                    logFile.absolutePath
                )
                appendServiceLog("[Android] Node.js 已退出，exit code=$exitCode")
            } catch (t: Throwable) {
                val stack = Log.getStackTraceString(t)
                appendServiceLog("[Android] 内置服务器启动失败：\n$stack")
                Log.e("KTPWARP-NODE", "Embedded server failed", t)
            } finally {
                stopSelf()
            }
        }, "ktpwarp-node").start()

        return START_STICKY
    }

    private fun prepareNodeProject(userConfig: File): File {
        val nodeDir = File(filesDir, PROJECT_DIR)
        val marker = File(filesDir, "$PROJECT_DIR.version")
        val currentVersion = packageManager
            .getPackageInfo(packageName, 0)
            .lastUpdateTime
            .toString()

        if (!nodeDir.isDirectory || marker.readTextOrNull() != currentVersion) {
            appendServiceLog("[Android] 首次启动：正在解压内置 server（只需执行一次）…")
            nodeDir.deleteRecursively()
            nodeDir.mkdirs()
            extractProjectZip(nodeDir)
            marker.writeText(currentVersion)
            appendServiceLog("[Android] 内置 server 解压完成。")
        }

        userConfig.copyTo(File(nodeDir, "config.ts"), overwrite = true)
        return nodeDir
    }

    private fun extractProjectZip(targetDir: File) {
        val targetCanonical = targetDir.canonicalFile
        val targetPrefix = targetCanonical.path + File.separator

        ZipInputStream(
            BufferedInputStream(assets.open(PROJECT_ZIP), 128 * 1024)
        ).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(targetDir, entry.name).canonicalFile

                if (
                    output.path != targetCanonical.path &&
                    !output.path.startsWith(targetPrefix)
                ) {
                    throw SecurityException("Unsafe zip entry: " + entry.name)
                }

                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { out ->
                        zip.copyTo(out, 128 * 1024)
                    }
                }

                zip.closeEntry()
            }
        }
    }

    private fun appendServiceLog(message: String) {
        runCatching {
            logFile(this).appendText(message + "\n")
        }
    }

    private fun File.readTextOrNull(): String? =
        runCatching { if (isFile) readText() else null }.getOrNull()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ktpWarp 内置服务器",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
