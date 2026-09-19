package io.github.celeswuff.ktpwarp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

@Composable
fun AppLocalServerCard(
    modifier: Modifier = Modifier,
    configured: Boolean,
    configText: String,
    websocketAddress: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSaveAndStart: (config: String, websocketAddress: String) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var draftConfig by remember(configText) { mutableStateOf(configText) }
    var draftAddress by remember(websocketAddress) { mutableStateOf(websocketAddress) }

    OutlinedCard(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "内置服务器", style = MaterialTheme.typography.headlineSmall)

            Text(
                if (configured) {
                    "已配置。server 会在独立后台进程中运行，Android 客户端直接连接本机。"
                } else {
                    "首次使用请先编辑原版 ktpwarp-server config.ts。"
                }
            )

            Text(
                "远程服务器连接功能仍保留在上面的“状态”卡片中。",
                style = MaterialTheme.typography.bodySmall
            )

            Button(onClick = onStart, enabled = configured) {
                Text("启动并连接内置服务器")
            }

            Button(onClick = {
                draftConfig = configText
                draftAddress = websocketAddress
                showEditor = true
            }) {
                Text(if (configured) "编辑配置" else "首次配置")
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
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("本机 WebSocket 地址")
                    TextField(
                        value = draftAddress,
                        onValueChange = { draftAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text("ws://127.0.0.1:11451/...")
                        }
                    )

                    Text(
                        "config.ts（就是原 ktpwarp-server 的配置文件；所有原功能选项都保留）"
                    )
                    TextField(
                        value = draftConfig,
                        onValueChange = { draftConfig = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        minLines = 18
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditor = false
                    onSaveAndStart(draftConfig, draftAddress)
                }) {
                    Text("保存并启动")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("取消")
                }
            }
        )
    }
}
