package io.github.celeswuff.ktpwarp.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.celeswuff.ktpwarp.R
import java.io.File
import java.io.FileOutputStream

class NodeServerService : Service() {

    companion object {
        const val PROJECT_DIR = "nodejs-project"
        const val CONFIG_FILE = "server-config.ts"
        const val DEFAULT_LOCAL_ADDRESS =
            "ws://127.0.0.1:11451/kfccrazythursdayvme50"

        private const val CHANNEL_ID = "ktpwarp_server"
        private const val NOTIFICATION_ID = 11451

        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("node")
        }

        fun configFile(context: Context): File = File(context.filesDir, CONFIG_FILE)

        fun defaultConfig(context: Context): String =
            context.assets.open("$PROJECT_DIR/config.example.ts")
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

    private external fun startNodeWithArguments(arguments: Array<String>): Int

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("ktpWarp")
                .setContentText("内置 ktpwarp-server 正在运行")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_STICKY

        val userConfig = configFile(this)
        if (!userConfig.isFile) {
            Log.w("KTPWARP-NODE", "No server-config.ts; embedded server will not start.")
            stopSelf()
            return START_NOT_STICKY
        }

        started = true
        Thread({
            try {
                val nodeDir = prepareNodeProject(userConfig)
                val exitCode = startNodeWithArguments(
                    arrayOf(
                        "node",
                        "--experimental-specifier-resolution=node",
                        File(nodeDir, "main.js").absolutePath
                    )
                )
                Log.i("KTPWARP-NODE", "Node exited with code $exitCode")
            } catch (t: Throwable) {
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
            nodeDir.deleteRecursively()
            nodeDir.mkdirs()
            copyAssetFolder(assets, PROJECT_DIR, nodeDir)
            marker.writeText(currentVersion)
        }

        userConfig.copyTo(File(nodeDir, "config.ts"), overwrite = true)
        return nodeDir
    }

    private fun copyAssetFolder(
        assetManager: AssetManager,
        assetPath: String,
        target: File
    ) {
        val entries = assetManager.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            target.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
            return
        }

        target.mkdirs()
        entries.forEach { name ->
            copyAssetFolder(
                assetManager,
                "$assetPath/$name",
                File(target, name)
            )
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
        // Node.js lives in this dedicated Android process. Killing only this process
        // gives us a clean and reliable server restart without affecting the UI process.
        Process.killProcess(Process.myPid())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
