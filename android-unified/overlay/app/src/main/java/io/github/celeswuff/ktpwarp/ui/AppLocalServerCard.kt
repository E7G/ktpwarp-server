package io.github.celeswuff.ktpwarp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLocalServerCard(
    modifier: Modifier = Modifier,
    configured: Boolean,
    configText: String,
    websocketAddress: String,
    status: String,
    serverLog: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSaveAndStart: (config: String) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var draftConfig by remember(configText) { mutableStateOf(configText) }

    OutlinedCard(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "内置服务器", style = MaterialTheme.typography.headlineSmall)

            Text(if (configured) "状态：" + status else "首次使用请先编辑原版 ktpwarp-server config.ts。")

            if (configured) {
                Text(
                    "本机地址：" + websocketAddress,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "地址会根据 config.ts 的端口、路径和 TLS 设置自动生成，不需要重复填写。",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "原 ktpwarp-server 会先登录课堂派，登录成功后才创建 WebSocket。",
                style = MaterialTheme.typography.bodySmall
            )

            Button(onClick = onStart, enabled = configured) {
                Text("启动并连接内置服务器")
            }
            Button(onClick = {
                draftConfig = configText
                showEditor = true
            }) {
                Text(if (configured) "编辑配置" else "首次配置")
            }
            Button(onClick = { showLog = true }, enabled = serverLog.isNotBlank()) {
                Text("查看启动日志")
            }
            Button(onClick = onStop) {
                Text("停止内置服务器")
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            modifier = Modifier.imePadding(),
            onDismissRequest = { showEditor = false },
            title = { Text("内置 ktpwarp-server 配置") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "直接编辑原版 config.ts。App 会自动读取端口、路径和 TLS 设置。"
                    )
                    TextField(
                        value = draftConfig,
                        onValueChange = { draftConfig = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditor = false
                    onSaveAndStart(draftConfig)
                }) { Text("保存并启动") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("取消") }
            }
        )
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("内置 server 启动日志") },
            text = {
                SelectionContainer {
                    Text(
                        text = if (serverLog.isBlank()) "暂无日志" else serverLog,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("关闭") }
            }
        )
    }
}
