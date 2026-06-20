package com.androidservice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.R
import com.androidservice.data.AppConfigFile
import com.androidservice.manager.RemoteConfigRefreshResult
import com.androidservice.ui.AppDimens
import com.androidservice.ui.CompactIconButton
import com.androidservice.ui.EmptyState
import com.androidservice.ui.PageHeader
import com.androidservice.ui.rememberDateTimeFormatter
import com.androidservice.ui.rememberFormatRefreshFailure
import com.androidservice.ui.screenHorizontalPadding
import com.androidservice.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ManageScreen(
    viewModel: MainViewModel = viewModel(),
    snackbarHostState: SnackbarHostState,
    listBottomPadding: Dp = AppDimens.fabClearance,
    onNavigateToConfigEdit: (String?) -> Unit = {},
) {
    val files by viewModel.appConfigFiles.collectAsStateWithLifecycle()
    val refreshingRemoteFiles by viewModel.refreshingRemoteFiles.collectAsStateWithLifecycle()
    val dateFormatter = rememberDateTimeFormatter("yyyy-MM-dd HH:mm")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenHorizontalPadding()
            .padding(top = AppDimens.screenTop),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing),
    ) {
        PageHeader(
            title = stringResource(R.string.files_title),
            subtitle = stringResource(R.string.files_subtitle),
        )

        if (files.isEmpty()) {
            EmptyState(
                Icons.Filled.Description,
                stringResource(R.string.files_empty_title),
                stringResource(R.string.files_empty_desc),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = listBottomPadding.coerceAtLeast(AppDimens.screenBottom)),
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
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val refreshSuccessMessage = stringResource(R.string.config_remote_refresh_success)
    val refreshContentDescription = stringResource(R.string.config_remote_refresh)
    val formatRefreshFailure = rememberFormatRefreshFailure()
    val pathCopiedMessage = stringResource(R.string.files_path_copied)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    configFile.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(formatSize(configFile.size))
                        append(" · ")
                        append(dateFormatter.format(Date(configFile.lastModified)))
                        if (configFile.remoteUrl.isNotBlank()) {
                            append(" · ")
                            append(stringResource(R.string.files_remote_tag))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (configFile.remoteUrl.isNotBlank()) configFile.remoteUrl else path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    modifier = Modifier.size(AppDimens.iconButtonSize),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = refreshContentDescription,
                            modifier = Modifier.size(AppDimens.iconSize),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            CompactIconButton(
                onClick = { showMenu = true },
                contentDescription = stringResource(R.string.action_more),
                icon = Icons.Filled.MoreVert,
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy_path), style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        scope.launch {
                            clipboard.setPlainText("path", path)
                            snackbarHostState.showSnackbar(pathCopiedMessage)
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = true
                    },
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.files_delete_title), style = MaterialTheme.typography.titleSmall) },
            text = {
                Text(
                    stringResource(R.string.files_delete_message, configFile.fileName),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
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
