package com.androidservice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var arguments by remember { mutableStateOf(config.arguments.toMutableList()) }
    var newArgument by remember { mutableStateOf("") }
    
    LaunchedEffect(config) {
        arguments = config.arguments.toMutableList()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "配置管理",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "基本配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                OutlinedTextField(
                    value = config.binaryName,
                    onValueChange = { 
                        viewModel.updateConfig(config.copy(binaryName = it))
                    },
                    label = { Text("二进制文件名") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = config.workingDirectory,
                    onValueChange = { 
                        viewModel.updateConfig(config.copy(workingDirectory = it))
                    },
                    label = { Text("工作目录") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "启动参数",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newArgument,
                        onValueChange = { newArgument = it },
                        label = { Text("新参数") },
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = {
                            if (newArgument.isNotBlank()) {
                                arguments.add(newArgument)
                                viewModel.updateConfig(config.copy(arguments = arguments.toList()))
                                newArgument = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加参数")
                    }
                }
                
                arguments.forEachIndexed { index, argument ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = argument,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = {
                                arguments.removeAt(index)
                                viewModel.updateConfig(config.copy(arguments = arguments.toList()))
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "删除参数")
                        }
                    }
                }
            }
        }
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "重启设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自动重启")
                    Switch(
                        checked = config.autoRestart,
                        onCheckedChange = { 
                            viewModel.updateConfig(config.copy(autoRestart = it))
                        }
                    )
                }
                
                if (config.autoRestart) {
                    OutlinedTextField(
                        value = (config.restartDelay / 1000).toString(),
                        onValueChange = { 
                            val delay = it.toLongOrNull()?.times(1000) ?: config.restartDelay
                            viewModel.updateConfig(config.copy(restartDelay = delay))
                        },
                        label = { Text("重启延迟（秒）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = if (config.maxRestarts == -1) "" else config.maxRestarts.toString(),
                        onValueChange = { 
                            val max = it.toIntOrNull() ?: -1
                            viewModel.updateConfig(config.copy(maxRestarts = max))
                        },
                        label = { Text("最大重启次数（空为无限制）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                Text("加载配置")
            }
            
            Button(
                onClick = {
                    viewModel.saveConfig()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("保存配置")
            }
        }
    }
}