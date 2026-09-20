package io.github.celeswuff.ktpwarp.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.celeswuff.ktpwarp.network.entity.Class_
import io.github.celeswuff.ktpwarp.network.entity.WebsocketMessage
import io.github.celeswuff.ktpwarp.network.entity.签到HistoryData
import io.github.celeswuff.ktpwarp.network.service.KtpwarpService
import io.github.celeswuff.ktpwarp.network.service.KtpwarpService.ConnectionStatus
import io.github.celeswuff.ktpwarp.server.NodeServerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun deriveLocalServerAddress(config: String): String {
    val port = Regex("""WEBSOCKET_SERVER_PORT\s*=\s*(\d+)""")
        .find(config)?.groupValues?.getOrNull(1) ?: "11451"
    val rawPath = Regex("""WEBSOCKET_SERVER_PATH\s*=\s*["']([^"']+)["']""")
        .find(config)?.groupValues?.getOrNull(1) ?: "/kfccrazythursdayvme50"
    val path = if (rawPath.startsWith("/")) rawPath else "/" + rawPath
    val tls = Regex("""WEBSOCKET_ENABLE_TLS\s*=\s*(true|false)""")
        .find(config)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: false

    return (if (tls) "wss" else "ws") + "://127.0.0.1:" + port + path
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("settings", 0)
    private var serverAddress = ""
    private var connectJob: Job? = null
    private var localStartJob: Job? = null

    var localServerConfigured by mutableStateOf(NodeServerService.configFile(application).isFile)
        private set
    var localConfigText by mutableStateOf(loadLocalConfig())
        private set
    var localServerAddress by mutableStateOf(
        if (localConfigText.isNotBlank()) deriveLocalServerAddress(localConfigText)
        else NodeServerService.DEFAULT_LOCAL_ADDRESS
    )
        private set
    var localServerLog by mutableStateOf("")
        private set
    var localServerStatus by mutableStateOf(
        if (localServerConfigured) "等待启动" else "尚未配置"
    )
        private set
    var lastSuccessfulServerAddress by mutableStateOf(
        sharedPreferences.getString("lastSuccessfulServerAddress", null)
            ?: if (localServerConfigured) localServerAddress else ""
    )
        private set

    private var service = KtpwarpService.create(viewModelScope)
    val connectionStatus = service.connectionStatus

    private val _showConnectionFailureDialog = mutableStateOf(false)
    val showConnectionFailureDialog: State<Boolean> = _showConnectionFailureDialog

    var ktpwarpServerVersion: String? = null
    var nodejsVersion: String? = null
    var schedule: List<Class_>? = null
    var currentClass: Class_? = null
    var finished签到s: List<签到HistoryData>? = null
    val messages = mutableStateListOf<WebsocketMessage>()

    init {
        sharedPreferences.edit().putString("localServerAddress", localServerAddress).apply()

        viewModelScope.launch {
            service.connectionStatus.collect {
                if (it == ConnectionStatus.CONNECTED) {
                    ktpwarpServerVersion = service.ktpwarpServerVersion
                    nodejsVersion = service.nodejsVersion
                    schedule = service.schedule
                    currentClass = service.currentClass
                    finished签到s = service.finished签到s
                    updateLastSuccessfulServerAddress()
                    if (isLocalAddress(serverAddress)) localServerStatus = "已连接内置服务器"
                }
            }
        }

        viewModelScope.launch {
            service.messages.collect { messages.add(it) }
        }

        viewModelScope.launch {
            while (true) {
                refreshLocalServerDiagnostics()
                delay(1000)
            }
        }

        if (localServerConfigured) {
            NodeServerService.start(application)
            waitForLocalServerAndConnect()
        }
    }

    private fun loadLocalConfig(): String {
        val application = getApplication<Application>()
        val file = NodeServerService.configFile(application)
        if (file.isFile) return runCatching { file.readText() }.getOrDefault("")
        return runCatching { NodeServerService.defaultConfig(application) }.getOrDefault("")
    }

    private fun updateLastSuccessfulServerAddress() {
        lastSuccessfulServerAddress = serverAddress
        sharedPreferences.edit().putString("lastSuccessfulServerAddress", serverAddress).apply()
    }

    private fun isLocalAddress(value: String): Boolean =
        value.contains("127.0.0.1") || value.contains("localhost")

    private fun refreshLocalServerDiagnostics() {
        val file = NodeServerService.logFile(getApplication<Application>())
        val text = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")
        localServerLog = if (text.length > 16000) text.takeLast(16000) else text

        if (connectionStatus.value == ConnectionStatus.CONNECTED && isLocalAddress(serverAddress)) {
            localServerStatus = "已连接内置服务器"
            return
        }

        localServerStatus = when {
            text.contains("启动失败：config.ts 仍包含上游示例账号") ->
                "配置仍是示例账号，请先填写真实账号"
            text.contains("内置服务器启动失败") ||
                text.contains("Node.js 已退出") ||
                text.contains("[ktpWarp mobile] Failed to load server") ||
                text.contains("[ktpWarp mobile] uncaught exception") ||
                text.contains("[ktpWarp mobile] unhandled rejection") ->
                "内置服务器启动失败，请查看日志"
            text.contains("At least one login failed") ->
                "课堂派登录失败，原 server 正在重试"
            text.contains("Logging in...") ->
                "正在登录课堂派；登录成功后才会开启 WebSocket"
            text.contains("WebSocket server listening on port") ->
                "WebSocket 已启动，正在连接"
            text.contains("Node.js 运行时启动中") ->
                "正在启动 Node.js"
            text.contains("正在解压内置 server") ->
                "首次启动：正在解压内置 server"
            text.contains("正在启动内置 ktpwarp-server") ->
                "正在启动内置 server"
            localServerConfigured -> "等待内置 server"
            else -> "尚未配置"
        }
    }

    private fun hasFatalLocalServerError(): Boolean =
        localServerLog.contains("启动失败：") ||
            localServerLog.contains("内置服务器启动失败") ||
            localServerLog.contains("Node.js 已退出") ||
            localServerLog.contains("[ktpWarp mobile] Failed to load server") ||
            localServerLog.contains("[ktpWarp mobile] uncaught exception") ||
            localServerLog.contains("[ktpWarp mobile] unhandled rejection")

    private fun waitForLocalServerAndConnect() {
        localStartJob?.cancel()
        localStartJob = viewModelScope.launch {
            repeat(180) {
                refreshLocalServerDiagnostics()
                if (localServerLog.contains("WebSocket server listening on port")) {
                    connect(localServerAddress)
                    return@launch
                }
                if (hasFatalLocalServerError()) {
                    _showConnectionFailureDialog.value = true
                    return@launch
                }
                delay(1000)
            }
            refreshLocalServerDiagnostics()
            _showConnectionFailureDialog.value = true
        }
    }

    fun connect(serverAddress: String) {
        if (serverAddress.isBlank()) return
        this.serverAddress = serverAddress
        messages.clear()
        connectJob?.cancel()

        connectJob = viewModelScope.launch {
            val attempts = if (isLocalAddress(serverAddress)) 3 else 1
            var lastFailure: Throwable? = null
            repeat(attempts) { attempt ->
                try {
                    service.connect(serverAddress)
                    return@launch
                } catch (t: Throwable) {
                    lastFailure = t
                    runCatching { service.disconnect() }
                    if (attempt + 1 < attempts) delay(1000)
                }
            }
            lastFailure?.printStackTrace()
            refreshLocalServerDiagnostics()
            _showConnectionFailureDialog.value = true
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        viewModelScope.launch { service.disconnect() }
    }

    fun closeConnectionFailureDialog() {
        _showConnectionFailureDialog.value = false
    }

    fun saveLocalServerConfig(config: String) {
        val application = getApplication<Application>()
        NodeServerService.configFile(application).writeText(config)
        localConfigText = config
        localServerConfigured = true
        localServerAddress = deriveLocalServerAddress(config)
        sharedPreferences.edit().putString("localServerAddress", localServerAddress).apply()
    }

    fun saveAndRestartLocalServer(config: String) {
        saveLocalServerConfig(config)
        disconnect()
        localStartJob?.cancel()
        val application = getApplication<Application>()
        NodeServerService.stop(application)

        viewModelScope.launch {
            delay(700)
            NodeServerService.start(application)
            waitForLocalServerAndConnect()
        }
    }

    fun startLocalServer() {
        if (!localServerConfigured) return
        NodeServerService.start(getApplication<Application>())
        waitForLocalServerAndConnect()
    }

    fun stopLocalServer() {
        localStartJob?.cancel()
        disconnect()
        NodeServerService.stop(getApplication<Application>())
        localServerStatus = "已停止"
    }

    fun skip() { viewModelScope.launch { service.skip() } }
    fun cancel() { viewModelScope.launch { service.cancel() } }
    fun manualCheck() { viewModelScope.launch { service.manualCheck() } }
    fun submitQrcode(urlString: String) {
        viewModelScope.launch { service.submitQrcode(urlString) }
    }

    override fun onCleared() {
        localStartJob?.cancel()
        disconnect()
        super.onCleared()
    }
}
