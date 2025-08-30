package com.example.myapp.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.myapp.settings.Settings

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImageSettingsClick: () -> Unit
) {
    val ctx = LocalContext.current

    var baseUrl by remember { mutableStateOf(Settings.read(ctx).baseUrl) }
    var apiKey by remember { mutableStateOf(Settings.read(ctx).apiKey ?: "") }
    var endpointId by remember { mutableStateOf(Settings.read(ctx).endpointId ?: "") }
    var baiduApiKey by remember { mutableStateOf(Settings.read(ctx).baiduApiKey) }
    var baiduSecretKey by remember { mutableStateOf(Settings.read(ctx).baiduSecretKey) }
    var baiduDevPidText by remember { mutableStateOf(Settings.read(ctx).baiduDevPid.toString()) }
    var forwardAsrToDoubao by remember { mutableStateOf(Settings.read(ctx).forwardAsrToDoubao) }

    val onSave: () -> Unit = {
        Settings.write(
            ctx,
            com.example.myapp.settings.SettingsData(
                baseUrl = baseUrl,
                apiKey = apiKey,
                endpointId = endpointId.ifBlank { null },
                model = endpointId.ifBlank { "gpt-4o" },
                baiduApiKey = baiduApiKey,
                baiduSecretKey = baiduSecretKey,
                baiduDevPid = (baiduDevPidText.toIntOrNull() ?: 1537),
                forwardAsrToDoubao = forwardAsrToDoubao
            )
        )
        Toast.makeText(ctx, "设置已保存", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModernSettingsHeader(onBack = onBack, onSave = onSave)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    ModernSettingsSection(title = "AI 配置", icon = Icons.Filled.Android) {
                        ModernSettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = endpointId,
                                    onValueChange = { endpointId = it },
                                    label = { Text("推理接入点 ID (model)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    label = { Text("API Key (Ark/豆包)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = { Text("Base URL") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Divider()
                                Text(
                                    "语音识别 (Baidu)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = baiduApiKey,
                                    onValueChange = { baiduApiKey = it },
                                    label = { Text("Baidu API Key") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = baiduSecretKey,
                                    onValueChange = { baiduSecretKey = it },
                                    label = { Text("Baidu Secret Key") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = baiduDevPidText,
                                    onValueChange = { baiduDevPidText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("DevPid (如 1537/1737)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("识别结果转发到豆包API", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = forwardAsrToDoubao,
                                        onCheckedChange = { forwardAsrToDoubao = it }
                                    )
                                }
                                Text(
                                    "提示: 若填写了接入点 ID，保存时会自动使用该 ID 作为模型；未填写接入点时，可填通用模型名 gpt-4o。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    ModernSettingsSection(title = "输入设置", icon = Icons.Filled.Input) {
                        ModernSettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ModernSettingItem(
                                    icon = Icons.Filled.PhotoCamera,
                                    title = "图片上传",
                                    subtitle = "设置图片上传参数",
                                    trailing = {
                                        IconButton(onClick = onImageSettingsClick) {
                                            Icon(
                                                Icons.Filled.ArrowForward,
                                                contentDescription = "图片设置",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                ModernSettingItem(
                                    icon = Icons.Filled.Mic,
                                    title = "语音输入",
                                    subtitle = "配置语音识别设置",
                                    trailing = {
                                        IconButton(onClick = { /* 打开语音设置 */ }) {
                                            Icon(
                                                Icons.Filled.ArrowForward,
                                                contentDescription = "语音设置",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "保存设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Overload to keep legacy call sites compiling (ignored params)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImageSettingsClick: () -> Unit,
    onCameraModeChange: (Boolean) -> Unit,
    onBleModeChange: (Boolean) -> Unit,
    onBluetoothManagerClick: () -> Unit,
    cameraMode: Boolean,
    bleMode: Boolean,
    bleConnected: Boolean
) {
    SettingsScreen(onBack = onBack, onImageSettingsClick = onImageSettingsClick)
}

@Composable
private fun ModernSettingsHeader(onBack: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("保存", color = Color.White)
        }
    }
}

@Composable
private fun ModernSettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        content()
    }
}

@Composable
private fun ModernSettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ModernSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}