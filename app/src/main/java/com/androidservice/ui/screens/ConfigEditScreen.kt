package com.androidservice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.androidservice.ui.AppDimens
import com.androidservice.ui.CompactIconButton
import com.androidservice.ui.rememberFormatRefreshFailure
import com.androidservice.ui.screenHorizontalPadding
import com.androidservice.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    fileName: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val refreshingRemoteFiles by viewModel.refreshingRemoteFiles.collectAsStateWithLifecycle()
    val formatRefreshFailure = rememberFormatRefreshFailure()

    var fileNameState by remember(fileName) { mutableStateOf(fileName ?: "config.json") }
    var remoteUrlState by remember(fileName) { mutableStateOf("") }
    var contentState by remember(fileName) { mutableStateOf(TextFieldValue("")) }
    var isSaving by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val syncKey = fileNameState.trim()
    val isRemoteRefreshing = syncKey in refreshingRemoteFiles || isSyncing

    val refreshSuccess = stringResource(R.string.config_remote_refresh_success)
    val fetchedMessage = stringResource(R.string.edit_fetched)

    LaunchedEffect(fileName) {
        if (fileName.isNullOrBlank()) {
            fileNameState = "config.json"
            remoteUrlState = ""
            contentState = TextFieldValue("")
            return@LaunchedEffect
        }
        val configFile = viewModel.loadAppConfigFile(fileName)
        if (configFile == null) {
            snackbarHostState.showSnackbar(resources.getString(R.string.edit_not_found))
            return@LaunchedEffect
        }
        fileNameState = configFile.fileName
        remoteUrlState = configFile.remoteUrl
        contentState = TextFieldValue(
            text = configFile.content,
            selection = TextRange.Zero,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (fileName == null) R.string.edit_new_title else R.string.edit_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(AppDimens.iconSize),
                        )
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
                                        refreshSuccessMessage = refreshSuccess,
                                        fetchedMessage = fetchedMessage,
                                    )
                                }
                            },
                            enabled = !isSaving && !isRemoteRefreshing,
                            modifier = Modifier.size(AppDimens.iconButtonSize),
                        ) {
                            if (isRemoteRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Sync,
                                    contentDescription = stringResource(R.string.config_remote_refresh),
                                    modifier = Modifier.size(AppDimens.iconSize),
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
                                    onSaved = onNavigateBack,
                                    resources = resources,
                                )
                            }
                        },
                        enabled = !isSaving && !isRemoteRefreshing,
                        modifier = Modifier.size(AppDimens.iconButtonSize),
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = stringResource(R.string.action_save),
                            modifier = Modifier.size(AppDimens.iconSize),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .screenHorizontalPadding(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.panelSpacing),
        ) {
            OutlinedTextField(
                value = fileNameState,
                onValueChange = { fileNameState = it },
                label = { Text(stringResource(R.string.edit_file_name), style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text(stringResource(R.string.edit_file_name_hint), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                colors = compactTextFieldColors(),
            )

            Text(
                text = stringResource(
                    R.string.edit_supported_types,
                    ConfigFileType.entries.joinToString(", ") { it.extension },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = remoteUrlState,
                onValueChange = { remoteUrlState = it },
                label = { Text(stringResource(R.string.config_remote_url_label), style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text(stringResource(R.string.config_remote_url_hint), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                colors = compactTextFieldColors(),
            )

            Text(
                text = stringResource(R.string.config_remote_url_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactIconButton(
                    onClick = { contentState = TextFieldValue("") },
                    contentDescription = stringResource(R.string.action_clear),
                    icon = Icons.Filled.Clear,
                )
                CompactIconButton(
                    onClick = {
                        scope.launch {
                            val text = clipboard.getPlainText().orEmpty()
                            contentState = TextFieldValue(text, selection = TextRange.Zero)
                        }
                    },
                    contentDescription = stringResource(R.string.action_paste),
                    icon = Icons.Filled.ContentPaste,
                )
            }

            OutlinedTextField(
                value = contentState,
                onValueChange = { contentState = it },
                label = { Text(stringResource(R.string.edit_content), style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text(stringResource(R.string.edit_content_hint), style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = compactTextFieldColors(),
            )

            Spacer(Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun compactTextFieldColors() = OutlinedTextFieldDefaults.colors()

private suspend fun syncRemoteConfig(
    fileName: String,
    remoteUrl: String,
    content: String,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onSyncing: (Boolean) -> Unit,
    onContentUpdated: (String) -> Unit,
    formatRefreshFailure: (String) -> String,
    refreshSuccessMessage: String,
    fetchedMessage: String,
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
            when {
                existing != null -> snackbarHostState.showSnackbar(refreshSuccessMessage)
                trimmedName.isNotBlank() && isValidFileName(trimmedName) -> {
                    viewModel.saveAppConfigFile(
                        AppConfigFile(
                            fileName = trimmedName,
                            content = result.content,
                            remoteUrl = trimmedUrl,
                        ),
                    )
                    snackbarHostState.showSnackbar(refreshSuccessMessage)
                }
                else -> snackbarHostState.showSnackbar(fetchedMessage)
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
    onSaved: () -> Unit,
    resources: android.content.res.Resources,
) {
    val trimmedName = fileName.trim()
    when {
        trimmedName.isBlank() -> {
            snackbarHostState.showSnackbar(resources.getString(R.string.edit_name_empty))
            return
        }
        !isValidFileName(trimmedName) -> {
            snackbarHostState.showSnackbar(resources.getString(R.string.edit_name_invalid))
            return
        }
        content.isBlank() -> {
            snackbarHostState.showSnackbar(resources.getString(R.string.edit_content_empty))
            return
        }
        content.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES -> {
            snackbarHostState.showSnackbar(resources.getString(R.string.edit_content_too_large))
            return
        }
        remoteUrl.isNotBlank() && !RemoteConfigFetcher.isSupportedUrl(remoteUrl) -> {
            snackbarHostState.showSnackbar(resources.getString(R.string.edit_url_invalid))
            return
        }
    }

    onSaving(true)
    val success = viewModel.saveAppConfigFile(
        AppConfigFile(
            fileName = trimmedName,
            content = content,
            remoteUrl = remoteUrl.trim(),
        ),
    )
    onSaving(false)

    if (success) {
        snackbarHostState.showSnackbar(resources.getString(R.string.edit_saved))
        onSaved()
    } else {
        snackbarHostState.showSnackbar(resources.getString(R.string.edit_save_failed))
    }
}

private fun isValidFileName(fileName: String): Boolean {
    val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    return fileName.length <= 255 && !fileName.startsWith(".") && fileName.none { it in invalidChars }
}

private const val MAX_FILE_BYTES = 2 * 1024 * 1024
