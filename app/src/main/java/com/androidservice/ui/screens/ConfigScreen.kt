package com.androidservice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.data.BinaryConfig
import com.androidservice.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel = viewModel()
) {
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val availableBinaryNames by viewModel.availableBinaryNames.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    
    var argumentsText by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }
    var helpContent by remember { mutableStateOf("") }
    var helpTitle by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    var restartDelayText by remember { mutableStateOf("") }
    var maxRestartsText by remember { mutableStateOf("") }
    
    LaunchedEffect(config) {
        argumentsText = config.argumentsString
        restartDelayText = (config.restartDelay / 1000).toString()
        maxRestartsText = if (config.maxRestarts == -1) "" else config.maxRestarts.toString()
    }
    
    // 页面销毁时保存未提交的更改
    DisposableEffect(Unit) {
        onDispose {
            val delay = restartDelayText.toLongOrNull()?.times(1000) ?: config.restartDelay
            val max = maxRestartsText.toIntOrNull() ?: -1
            if (delay != config.restartDelay || max != config.maxRestarts) {
                viewModel.updateConfig(config.copy(
                    restartDelay = delay,
                    maxRestarts = max
                ))
            }
        }
    }
    
    LaunchedEffect(expanded) {
        if (expanded && availableBinaryNames.isEmpty()) {
            viewModel.loadAvailableBinaryNames()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "应用数据管理",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "基本配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        helpTitle = "基本配置"
                        helpContent = "设置要启动的程序基本信息：\n• 程序文件名：输入要运行的程序名称\n  示例：xray、singbox、frp等"
                        showHelpDialog = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = "帮助",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = config.binaryName,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("程序文件名") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableBinaryNames.forEach { kernelName ->
                        DropdownMenuItem(
                            text = { Text(kernelName) },
                            onClick = {
                                viewModel.updateConfig(config.copy(binaryName = kernelName))
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                    
                    // 如果没有可用的内核，显示提示
                    if (availableBinaryNames.isEmpty()) {
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "没有可用的内核",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { },
                            enabled = false,
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
            

        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "启动参数",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.updateConfig(config.copy(
                                argumentsString = argumentsText
                            ))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "保存",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                argumentsText = clipText
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "粘贴参数",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            helpTitle = "启动参数"
                            helpContent = "程序启动时传递的命令行参数：\n• 直接输入完整的命令参数行\n• 支持所有shell语法，与Linux终端完全相同\n• 点击保存图标手动保存启动参数\n• 点击粘贴图标可从剪贴板粘贴参数\n• 修改后需要点击保存图标才会生效\n\n示例：\nrun -config /sdcard/config.json"
                            showHelpDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = "帮助",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
                
            OutlinedTextField(
                value = argumentsText,
                onValueChange = { newText ->
                    argumentsText = newText
                },
                label = { Text("启动参数") },
                placeholder = { Text("例如: run -config /path/config.json --debug") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // 显示执行方式说明
            if (argumentsText.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "执行方式: 通过shell原样传递启动参数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "执行示例: sh -c \"libcore $argumentsText\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "重启设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        helpTitle = "重启设置"
                        helpContent = "程序异常退出后的处理方式：\n• 自动重启：默认关闭，开启后程序崩溃会自动重新启动\n• 重启延迟：程序退出后等待多久再重启（单位：秒）\n• 最大重启次数：防止无限重启，空白表示不限制\n建议：新用户先保持关闭状态"
                        showHelpDialog = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = "帮助",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "自动重启",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = config.autoRestart,
                        onCheckedChange = { 
                            viewModel.updateConfig(config.copy(autoRestart = it))
                        }
                    )
                }
                
                if (config.autoRestart) {
                    OutlinedTextField(
                        value = restartDelayText,
                        onValueChange = { restartDelayText = it },
                        label = { Text("重启延迟（秒）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                OutlinedTextField(
                    value = maxRestartsText,
                    onValueChange = { maxRestartsText = it },
                    label = { Text("最大重启次数（空为无限制）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "应用数据管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        helpTitle = "应用数据管理"
                        helpContent = "此功能用于应用本身的数据保存与恢复：\n• 保存数据：将当前页面的所有设置（程序名、启动参数、重启设置等）保存到文件中，用于备份\n• 加载数据：从之前保存的文件中恢复所有设置，用于快速恢复配置\n注意：这里管理的是应用本身的数据参数，跟运行的程序无关。"
                        showHelpDialog = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = "帮助",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.loadConfig()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("加载数据")
                }
                
                Button(
                    onClick = {
                        viewModel.saveConfig()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存数据")
                }
            }
        }
        
        // 帮助弹窗
        if (showHelpDialog) {
            HelpDialog(
                title = helpTitle,
                content = helpContent,
                onDismiss = { showHelpDialog = false }
            )
        }
        
        val uriHandler = LocalUriHandler.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                 onClick = {
                     // 项目地址
                     uriHandler.openUri("https://github.com/sky22333/svcmgr")
                 },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "GitHub",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // 底部间距，确保内容不被导航栏遮挡
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun HelpDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Help,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("知道了")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp)
    )
}