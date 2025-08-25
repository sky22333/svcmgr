package com.androidservice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.R
import com.androidservice.data.ServiceState
import com.androidservice.data.ServiceStatus
import com.androidservice.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel()
) {
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Android Service Manager",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        ServiceStatusCard(
            serviceState = serviceState,
            serviceStatus = serviceStatus,
            onStartClick = {
                viewModel.startService()
            },
            onStopClick = {
                viewModel.stopService()
            },
            onRestartClick = {
                viewModel.restartService()
            }
        )
        
        ServiceInfoCard(serviceState = serviceState)
    }
}

@Composable
fun ServiceStatusCard(
    serviceState: ServiceState,
    serviceStatus: ServiceStatus,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.service_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                StatusIndicator(status = serviceStatus)
            }
            
            Text(
                text = when (serviceStatus) {
                    ServiceStatus.STOPPED -> stringResource(R.string.service_stopped)
                    ServiceStatus.STARTING -> "正在启动..."
                    ServiceStatus.RUNNING -> stringResource(R.string.service_running)
                    ServiceStatus.STOPPING -> "正在停止..."
                    ServiceStatus.ERROR -> "服务错误"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = serviceStatus == ServiceStatus.STOPPED,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.start_service))
                }
                
                Button(
                    onClick = onStopClick,
                    enabled = serviceStatus == ServiceStatus.RUNNING,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.stop_service))
                }
                
                OutlinedButton(
                    onClick = onRestartClick,
                    enabled = serviceStatus == ServiceStatus.RUNNING,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.restart_service))
                }
            }
        }
    }
}

@Composable
fun ServiceInfoCard(serviceState: ServiceState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "服务信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            InfoRow("进程ID", serviceState.processId?.toString() ?: "未运行")
            InfoRow("二进制名称", serviceState.binaryName.ifEmpty { "未配置" })
            InfoRow("重启次数", serviceState.restartCount.toString())
            if (serviceState.startTime != null) {
                InfoRow("启动时间", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(serviceState.startTime)))
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatusIndicator(status: ServiceStatus) {
    val color = when (status) {
        ServiceStatus.STOPPED -> MaterialTheme.colorScheme.outline
        ServiceStatus.STARTING, ServiceStatus.STOPPING -> MaterialTheme.colorScheme.tertiary
        ServiceStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ServiceStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .fillMaxSize()
        ) {
            // 使用Surface来创建圆形指示器
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = color
            ) {}
        }
    }
}