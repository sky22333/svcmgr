package com.androidservice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.R
import com.androidservice.data.AppConfigFile
import com.androidservice.data.ConfigFileType
import com.androidservice.manager.RemoteConfigFetcher
import com.androidservice.manager.RemoteConfigRefreshResult
import com.androidservice.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    fileName: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val refreshingRemoteFiles by viewModel.refreshingRemoteFiles.collectAsStateWithLifecycle()
    val formatRefreshFailure: (String) -> String = { message ->
        resources.getString(R.string.config_remote_refresh_failed, message)
    }

    var fileNameState by remember(fileName) { mutableStateOf(fileName ?: "config.json") }
    var remoteUrlState by remember(fileName) { mutableStateOf("") }
    var contentState by remember { mutableStateOf(TextFieldValue("")) }
    var isSaving by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val syncKey = fileNameState.trim()
    val isRemoteRefreshing = syncKey in refreshingRemoteFiles || isSyncing

    LaunchedEffect(fileName) {
        if (fileName.isNullOrBlank()) return@LaunchedEffect
        val configFile = viewModel.loadAppConfigFile(fileName)
        if (configFile == null) {
            snackbarHostState.showSnackbar("配置文件不存在")
            return@LaunchedEffect
        }
        fileNameState = configFile.fileName
        remoteUrlState = configFile.remoteUrl
        contentState = TextFieldValue(
            text = configFile.content,
            selection = TextRange.Zero
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (fileName == null) "新建配置" else "编辑配置",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (remoteUrlState.isNotBlank()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    syncRemoteConfig(
                                        fileName = fileNameState,
                                        remoteUrl = remoteUrlState,
                                        content = contentState.text,
                                        viewModel = viewModel,
                                        snackbarHostState = snackbarHostState,
                                        onSyncing = { isSyncing = it },
                                        onContentUpdated = { content ->
                                            contentState = TextFieldValue(content, TextRange.Zero)
                                        },
                                        formatRefreshFailure = formatRefreshFailure,
                                    )
                                }
                            },
                            enabled = !isSaving && !isRemoteRefreshing,
                        ) {
                            if (isRemoteRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Sync,
                                    contentDescription = stringResource(R.string.config_remote_refresh),
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                saveFile(
                                    fileName = fileNameState,
                                    remoteUrl = remoteUrlState,
                                    content = contentState.text,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState,
                                    onSaving = { isSaving = it },
                                    onSaved = onNavigateBack
                                )
                            }
                        },
                        enabled = !isSaving && !isRemoteRefreshing
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = fileNameState,
                onValueChange = { fileNameState = it },
                label = { Text("文件名") },
                placeholder = { Text("config.json") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "支持: ${ConfigFileType.entries.joinToString(", ") { it.extension }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = remoteUrlState,
                onValueChange = { remoteUrlState = it },
                label = { Text(stringResource(R.string.config_remote_url_label)) },
                placeholder = { Text(stringResource(R.string.config_remote_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.config_remote_url_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { contentState = TextFieldValue("") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = null)
                    Text("清空")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val text = clipboard.getPlainText().orEmpty()
                            contentState = TextFieldValue(text, selection = TextRange.Zero)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Text("粘贴")
                }
                Button(
                    onClick = {
                        scope.launch {
                            saveFile(
                                fileName = fileNameState,
                                remoteUrl = remoteUrlState,
                                content = contentState.text,
                                viewModel = viewModel,
                                snackbarHostState = snackbarHostState,
                                onSaving = { isSaving = it },
                                onSaved = onNavigateBack
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && !isRemoteRefreshing,
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("保存")
                }
            }

            OutlinedTextField(
                value = contentState,
                onValueChange = { contentState = it },
                label = { Text("配置内容") },
                placeholder = { Text("在此输入配置文件内容") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

private suspend fun syncRemoteConfig(
    fileName: String,
    remoteUrl: String,
    content: String,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onSyncing: (Boolean) -> Unit,
    onContentUpdated: (String) -> Unit,
    formatRefreshFailure: (String) -> String,
) {
    val trimmedUrl = remoteUrl.trim()
    if (trimmedUrl.isBlank()) return

    onSyncing(true)
    val trimmedName = fileName.trim()
    val existing = if (trimmedName.isNotBlank() && isValidFileName(trimmedName)) {
        viewModel.loadAppConfigFile(trimmedName)
    } else {
        null
    }
    val result = try {
        if (existing != null) {
            if (existing.remoteUrl != trimmedUrl) {
                viewModel.saveAppConfigFile(
                    existing.copy(
                        content = content,
                        remoteUrl = trimmedUrl,
                    ),
                )
            }
            viewModel.refreshRemoteConfigFile(trimmedName)
        } else {
            viewModel.fetchRemoteConfigPreview(trimmedUrl)
        }
    } finally {
        onSyncing(false)
    }

    when (result) {
        is RemoteConfigRefreshResult.Success -> {
            onContentUpdated(result.content)
            if (existing != null) {
                snackbarHostState.showSnackbar("远程配置已更新")
            } else if (trimmedName.isNotBlank() && isValidFileName(trimmedName)) {
                viewModel.saveAppConfigFile(
                    AppConfigFile(
                        fileName = trimmedName,
                        content = result.content,
                        remoteUrl = trimmedUrl,
                        fileExtension = fileExtension(trimmedName),
                    ),
                )
                snackbarHostState.showSnackbar("远程配置已更新")
            } else {
                snackbarHostState.showSnackbar("远程配置已拉取")
            }
        }
        is RemoteConfigRefreshResult.Failure -> {
            snackbarHostState.showSnackbar(formatRefreshFailure(result.message))
        }
    }
}

private suspend fun saveFile(
    fileName: String,
    remoteUrl: String,
    content: String,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onSaving: (Boolean) -> Unit,
    onSaved: () -> Unit
) {
    val trimmedName = fileName.trim()
    when {
        trimmedName.isBlank() -> {
            snackbarHostState.showSnackbar("文件名不能为空")
            return
        }
        !isValidFileName(trimmedName) -> {
            snackbarHostState.showSnackbar("文件名包含非法字符")
            return
        }
        content.isBlank() -> {
            snackbarHostState.showSnackbar("配置内容不能为空")
            return
        }
        content.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES -> {
            snackbarHostState.showSnackbar("配置文件不能超过 2MB")
            return
        }
        remoteUrl.isNotBlank() && !RemoteConfigFetcher.isSupportedUrl(remoteUrl) -> {
            snackbarHostState.showSnackbar("远程 URL 无效")
            return
        }
    }

    onSaving(true)
    val success = viewModel.saveAppConfigFile(
        AppConfigFile(
            fileName = trimmedName,
            content = content,
            remoteUrl = remoteUrl.trim(),
            fileExtension = fileExtension(trimmedName)
        )
    )
    onSaving(false)

    if (success) {
        snackbarHostState.showSnackbar("保存成功")
        onSaved()
    } else {
        snackbarHostState.showSnackbar("保存失败")
    }
}

private fun isValidFileName(fileName: String): Boolean {
    val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    return fileName.length <= 255 && !fileName.startsWith(".") && fileName.none { it in invalidChars }
}

private fun fileExtension(fileName: String): String {
    return fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it != fileName }?.let { ".$it" }.orEmpty()
}

private const val MAX_FILE_BYTES = 2 * 1024 * 1024
