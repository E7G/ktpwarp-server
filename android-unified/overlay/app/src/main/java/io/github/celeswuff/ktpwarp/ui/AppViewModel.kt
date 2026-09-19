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

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("settings", 0)
    private var serverAddress = ""
    private var connectJob: Job? = null

    var localServerConfigured by mutableStateOf(NodeServerService.configFile(application).isFile)
        private set

    var localServerAddress by mutableStateOf(
        sharedPreferences.getString(
            "localServerAddress",
            NodeServerService.DEFAULT_LOCAL_ADDRESS
        ) ?: NodeServerService.DEFAULT_LOCAL_ADDRESS
    )
        private set

    var localConfigText by mutableStateOf(loadLocalConfig())
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
        viewModelScope.launch {
            service.connectionStatus.collect {
                if (it == ConnectionStatus.CONNECTED) {
                    ktpwarpServerVersion = service.ktpwarpServerVersion
                    nodejsVersion = service.nodejsVersion
                    schedule = service.schedule
                    currentClass = service.currentClass
                    finished签到s = service.finished签到s
                    updateLastSuccessfulServerAddress()
                }
            }
        }

        viewModelScope.launch {
            service.messages.collect { messages.add(it) }
        }
    }

    private fun loadLocalConfig(): String {
        val application = getApplication<Application>()
        val file = NodeServerService.configFile(application)
        if (file.isFile) return runCatching { file.readText() }.getOrDefault("")

        return runCatching {
            NodeServerService.defaultConfig(application)
        }.getOrDefault("")
    }

    private fun updateLastSuccessfulServerAddress() {
        lastSuccessfulServerAddress = serverAddress
        sharedPreferences
            .edit()
            .putString("lastSuccessfulServerAddress", serverAddress)
            .apply()
    }

    private fun isLocalAddress(value: String): Boolean =
        value.contains("127.0.0.1") || value.contains("localhost")

    fun connect(serverAddress: String) {
        if (serverAddress.isBlank()) return

        this.serverAddress = serverAddress
        messages.clear()
        connectJob?.cancel()

        connectJob = viewModelScope.launch {
            val attempts = if (isLocalAddress(serverAddress)) 30 else 1
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
            _showConnectionFailureDialog.value = true
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        viewModelScope.launch {
            service.disconnect()
        }
    }

    fun closeConnectionFailureDialog() {
        _showConnectionFailureDialog.value = false
    }

    fun saveLocalServerConfig(config: String, websocketAddress: String) {
        val application = getApplication<Application>()
        NodeServerService.configFile(application).writeText(config)
        localConfigText = config
        localServerConfigured = true
        localServerAddress = websocketAddress.trim().ifBlank {
            NodeServerService.DEFAULT_LOCAL_ADDRESS
        }

        sharedPreferences.edit()
            .putString("localServerAddress", localServerAddress)
            .apply()
    }

    fun saveAndRestartLocalServer(config: String, websocketAddress: String) {
        saveLocalServerConfig(config, websocketAddress)
        disconnect()

        val application = getApplication<Application>()
        NodeServerService.stop(application)

        viewModelScope.launch {
            delay(700)
            NodeServerService.start(application)
            delay(1200)
            connect(localServerAddress)
        }
    }

    fun startLocalServer() {
        if (!localServerConfigured) return

        val application = getApplication<Application>()
        NodeServerService.start(application)

        viewModelScope.launch {
            delay(1200)
            connect(localServerAddress)
        }
    }

    fun stopLocalServer() {
        disconnect()
        NodeServerService.stop(getApplication<Application>())
    }

    fun skip() {
        viewModelScope.launch { service.skip() }
    }

    fun cancel() {
        viewModelScope.launch { service.cancel() }
    }

    fun manualCheck() {
        viewModelScope.launch { service.manualCheck() }
    }

    fun submitQrcode(urlString: String) {
        viewModelScope.launch { service.submitQrcode(urlString) }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
