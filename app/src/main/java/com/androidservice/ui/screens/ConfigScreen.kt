package com.androidservice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.ui.FlatPanel
import com.androidservice.ui.PageHeader
import com.androidservice.ui.SectionTitle
import com.androidservice.ui.VisibilityFade
import com.androidservice.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: MainViewModel = viewModel()) {
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val availableNames by viewModel.availableBinaryNames.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var argumentsText by remember(config.argumentsString) { mutableStateOf(config.argumentsString) }
    var restartDelayText by remember(config.restartDelay) { mutableStateOf((config.restartDelay / 1000).toString()) }
    var maxRestartsText by remember(config.maxRestarts) {
        mutableStateOf(config.maxRestarts.takeIf { it >= 0 }?.toString().orEmpty())
    }
    var userEdited by remember { mutableStateOf(false) }
    val latestConfig by rememberUpdatedState(config)

    LaunchedEffect(Unit) {
        viewModel.loadAvailableBinaryNames()
    }

    LaunchedEffect(userEdited, argumentsText, restartDelayText, maxRestartsText) {
        if (!userEdited) return@LaunchedEffect
        delay(800)
        val next = latestConfig.copy(
            argumentsString = argumentsText,
            restartDelay = restartDelayText.toLongOrNull()?.coerceAtLeast(1)?.times(1000) ?: latestConfig.restartDelay,
            maxRestarts = maxRestartsText.toIntOrNull() ?: -1
        )
        if (next != latestConfig) viewModel.updateConfig(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        PageHeader("运行设置", "选择程序、启动参数、重启策略和后台运行选项")

        FlatPanel {
            SectionTitle("程序")
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = config.binaryName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("程序") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (availableNames.isEmpty()) {
                        DropdownMenuItem(text = { Text("未发现可用程序") }, enabled = false, onClick = {})
                    } else {
                        availableNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.updateConfig(config.copy(binaryName = name))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        FlatPanel {
            SectionTitle(
                title = "启动参数",
                trailing = {
                    Row {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.getPlainText()?.let {
                                        userEdited = true
                                        argumentsText = it
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴参数")
                        }
                        IconButton(onClick = { viewModel.updateConfig(config.copy(argumentsString = argumentsText)) }) {
                            Icon(Icons.Filled.Save, contentDescription = "保存参数")
                        }
                    }
                }
            )
            OutlinedTextField(
                value = argumentsText,
                onValueChange = {
                    userEdited = true
                    argumentsText = it
                },
                label = { Text("参数") },
                placeholder = { Text("例如: run -config /path/config.json --debug") },
                modifier = Modifier.fillMaxWidth()
            )
            VisibilityFade(argumentsText.isNotBlank()) {
                Text(
                    text = "执行方式: sh -c \"${config.binaryName.ifBlank { "程序" }} $argumentsText\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FlatPanel {
            SectionTitle("自动重启")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("进程异常退出后自动拉起")
                Switch(
                    checked = config.autoRestart,
                    onCheckedChange = { viewModel.updateConfig(config.copy(autoRestart = it)) }
                )
            }
            VisibilityFade(config.autoRestart) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = restartDelayText,
                        onValueChange = {
                            userEdited = true
                            restartDelayText = it.filter(Char::isDigit)
                        },
                        label = { Text("重启延迟（秒）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxRestartsText,
                        onValueChange = {
                            userEdited = true
                            maxRestartsText = it.filter(Char::isDigit)
                        },
                        label = { Text("最大重启次数（留空为不限）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        FlatPanel {
            SectionTitle("后台运行")
            Text(
                text = "可选。仅在设备长时间运行时频繁停止前台服务的情况下使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = viewModel::requestIgnoreBatteryOptimizations,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.BatterySaver, contentDescription = null)
                Text("允许不受限制地后台运行")
            }
        }

        FlatPanel {
            SectionTitle("备份")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::loadConfig, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text("加载")
                }
                Button(onClick = viewModel::saveConfig, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Upload, contentDescription = null)
                    Text("保存")
                }
            }
        }

        Spacer(Modifier.height(22.dp))
    }
}
