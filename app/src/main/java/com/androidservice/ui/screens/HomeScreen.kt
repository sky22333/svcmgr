package com.androidservice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 顶部间距，避免被状态栏遮挡
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "svcmgr",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        ServiceStatusSection(
            serviceState = serviceState,
            serviceStatus = serviceStatus,
            onStartClick = {
                viewModel.startService()
            },
            onStopClick = {
                viewModel.stopService()
            }
        )
        
        ServiceInfoSection(serviceState = serviceState)
        
        // 底部间距，确保内容不被导航栏遮挡
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ServiceStatusSection(
    serviceState: ServiceState,
    serviceStatus: ServiceStatus,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
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
                text = stringResource(R.string.service_status),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // 启动/停止按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onStartClick,
                enabled = serviceStatus == ServiceStatus.STOPPED,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow, 
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.start_service),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            
            FilledTonalButton(
                onClick = onStopClick,
                enabled = serviceStatus == ServiceStatus.RUNNING,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    Icons.Filled.Stop, 
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.stop_service),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun ServiceInfoSection(serviceState: ServiceState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "服务信息",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow("进程ID", serviceState.processId?.toString() ?: "未运行")
            InfoRow("程序名称", serviceState.binaryName.ifEmpty { "未配置" })
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
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
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
    )
}