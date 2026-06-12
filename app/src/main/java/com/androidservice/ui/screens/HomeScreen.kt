package com.androidservice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.data.ServiceState
import com.androidservice.data.ServiceStatus
import com.androidservice.ui.AnimatedCount
import com.androidservice.ui.FlatPanel
import com.androidservice.ui.MetricRow
import com.androidservice.ui.PageHeader
import com.androidservice.ui.SectionTitle
import com.androidservice.ui.SoftDivider
import com.androidservice.ui.StatusDot
import com.androidservice.ui.rememberDateTimeFormatter
import com.androidservice.ui.theme.ThemeSeed
import com.androidservice.ui.theme.ThemeSeedOptions
import com.androidservice.viewmodel.MainViewModel
import java.util.Date

@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    seedColor: Color,
    onSeedColorChange: (Color) -> Unit
) {
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
    val logCount by viewModel.logCount.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val timeFormatter = rememberDateTimeFormatter("yyyy-MM-dd HH:mm:ss")
    var showSeedDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            PageHeader(
                title = "svcmgr",
                subtitle = "管理本机打包的二进制服务、配置文件与运行日志",
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSeedDialog = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "主题配色")
            }
        }

        ServicePanel(
            status = serviceStatus,
            state = serviceState,
            onStart = viewModel::startService,
            onStop = viewModel::stopService
        )

        FlatPanel {
            SectionTitle("运行概览")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryMetric("程序", config.binaryName.ifBlank { "未选择" })
                SummaryMetric("日志", logCount.toString())
                SummaryMetric("重启", serviceState.restartCount.toString())
            }
            SoftDivider()
            MetricRow("进程 ID", serviceState.processId?.toString() ?: "未运行")
            MetricRow(
                "启动时间",
                serviceState.startTime?.let { timeFormatter.format(Date(it)) } ?: "-"
            )
        }

        Spacer(Modifier.height(22.dp))
    }

    if (showSeedDialog) {
        ThemeSeedDialog(
            selectedColor = seedColor,
            onSeedSelected = {
                onSeedColorChange(it.color)
                showSeedDialog = false
            },
            onDismiss = { showSeedDialog = false }
        )
    }
}

@Composable
private fun ThemeSeedDialog(
    selectedColor: Color,
    onSeedSelected: (ThemeSeed) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题配色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeSeedOptions.forEach { seed ->
                    TextButton(
                        onClick = { onSeedSelected(seed) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(
                            modifier = Modifier
                                .size(if (seed.color == selectedColor) 18.dp else 14.dp)
                                .background(seed.color, CircleShape)
                        )
                        Text(
                            text = seed.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
private fun ServicePanel(
    status: ServiceStatus,
    state: ServiceState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val active = status == ServiceStatus.RUNNING
    val busy = status == ServiceStatus.STARTING || status == ServiceStatus.STOPPING
    val statusColor = when (status) {
        ServiceStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ServiceStatus.ERROR -> MaterialTheme.colorScheme.error
        ServiceStatus.STARTING, ServiceStatus.STOPPING -> MaterialTheme.colorScheme.tertiary
        ServiceStatus.STOPPED -> MaterialTheme.colorScheme.outline
    }

    FlatPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("服务状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(status.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusDot(color = statusColor, active = active || busy)
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStart,
                enabled = status == ServiceStatus.STOPPED || status == ServiceStatus.ERROR,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("启动")
            }
            OutlinedButton(
                onClick = onStop,
                enabled = status == ServiceStatus.RUNNING,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Text("停止")
            }
        }

        MetricRow("当前程序", state.binaryName.ifBlank { "未启动" })
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedCount(value)
    }
}

private fun ServiceStatus.label(): String = when (this) {
    ServiceStatus.STOPPED -> "已停止"
    ServiceStatus.STARTING -> "正在启动"
    ServiceStatus.RUNNING -> "运行中"
    ServiceStatus.STOPPING -> "正在停止"
    ServiceStatus.ERROR -> "异常"
}

