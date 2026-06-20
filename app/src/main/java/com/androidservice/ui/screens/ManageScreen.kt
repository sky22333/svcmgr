package com.androidservice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.R
import com.androidservice.data.AppConfigFile
import com.androidservice.manager.RemoteConfigRefreshResult
import com.androidservice.ui.EmptyState
import com.androidservice.ui.PageHeader
import com.androidservice.ui.rememberDateTimeFormatter
import com.androidservice.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ManageScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToConfigEdit: (String?) -> Unit = {}
) {
    val files by viewModel.appConfigFiles.collectAsStateWithLifecycle()
    val refreshingRemoteFiles by viewModel.refreshingRemoteFiles.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormatter = rememberDateTimeFormatter("yyyy-MM-dd HH:mm")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        PageHeader("配置文件", "创建、编辑、远程拉取、复制路径或删除程序使用的配置文件")

        SnackbarHost(hostState = snackbarHostState)

        if (files.isEmpty()) {
            EmptyState(Icons.Filled.Description, "暂无配置文件", "点击右下角按钮新建")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(files, key = { it.fileName }) { file ->
                    ConfigFileRow(
                        configFile = file,
                        path = viewModel.getAppConfigFilePath(file.fileName),
                        dateFormatter = dateFormatter,
                        isRefreshing = file.fileName in refreshingRemoteFiles,
                        onEdit = { onNavigateToConfigEdit(file.fileName) },
                        onRefresh = { viewModel.refreshRemoteConfigFile(file.fileName) },
                        onDelete = { viewModel.deleteAppConfigFile(file.fileName) },
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigFileRow(
    configFile: AppConfigFile,
    path: String,
    dateFormatter: SimpleDateFormat,
    isRefreshing: Boolean,
    onEdit: () -> Unit,
    onRefresh: suspend () -> RemoteConfigRefreshResult,
    onDelete: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val refreshSuccessMessage = stringResource(R.string.config_remote_refresh_success)
    val refreshContentDescription = stringResource(R.string.config_remote_refresh)
    val formatRefreshFailure: (String) -> String = { message ->
        resources.getString(R.string.config_remote_refresh_failed, message)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    configFile.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(formatSize(configFile.size))
                        append(" · ")
                        append(dateFormatter.format(Date(configFile.lastModified)))
                        if (configFile.remoteUrl.isNotBlank()) {
                            append(" · 远程")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (configFile.remoteUrl.isNotBlank()) configFile.remoteUrl else path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (configFile.remoteUrl.isNotBlank()) {
                IconButton(
                    onClick = {
                        scope.launch {
                            when (val result = onRefresh()) {
                                is RemoteConfigRefreshResult.Success -> {
                                    snackbarHostState.showSnackbar(refreshSuccessMessage)
                                }
                                is RemoteConfigRefreshResult.Failure -> {
                                    snackbarHostState.showSnackbar(formatRefreshFailure(result.message))
                                }
                            }
                        }
                    },
                    enabled = !isRefreshing,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = refreshContentDescription,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setPlainText("path", path)
                        snackbarHostState.showSnackbar("路径已复制")
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制路径")
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除配置文件") },
            text = { Text("确定删除 ${configFile.fileName}？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatSize(size: Long): String {
    return when {
        size >= 1024 * 1024 -> "%.1f MB".format(size / 1024f / 1024f)
        size >= 1024 -> "%.1f KB".format(size / 1024f)
        else -> "$size B"
    }
}
