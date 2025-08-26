package com.androidservice.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.data.AppConfigFile
import com.androidservice.data.ConfigFileType
import com.androidservice.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    fileName: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var fileNameState by remember { mutableStateOf(fileName ?: "config.json") }
    var contentState by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var shouldSave by remember { mutableStateOf(false) }
    var isContentLoaded by remember { mutableStateOf(false) }
    
    // 防抖状态
    var isSaveInProgress by remember { mutableStateOf(false) }
    
    // 添加文件名键来确保LaunchedEffect正确触发
    val currentFileName = remember(fileName) { fileName }

    // 分离文件加载效果
    LaunchedEffect(currentFileName) {
        if (currentFileName != null && currentFileName.isNotEmpty() && 
            currentFileName != "null" && currentFileName != "undefined") {
            
            if (isContentLoaded && fileNameState == currentFileName) {
                return@LaunchedEffect
            }
            
            isLoading = true
            isContentLoaded = false
            
            try {
                val configFile = withContext(Dispatchers.IO) {
                    viewModel.loadAppConfigFile(currentFileName)
                }
                
                // 确保在Main线程更新UI状态
                withContext(Dispatchers.Main) {
                    if (configFile != null) {
                        val newText = configFile.content
                        
                         val safeSelection = if (newText.isEmpty()) TextRange.Zero else TextRange(0, 0)
                        
                        contentState = TextFieldValue(
                            text = newText,
                            selection = safeSelection,
                            composition = null // 明确设置composition为null避免IME问题
                        )
                        fileNameState = configFile.fileName
                        isContentLoaded = true
                    } else {
                        errorMessage = "配置文件不存在: $currentFileName"
                        showError = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "加载文件失败: ${e.localizedMessage ?: e.message ?: "文件读取错误"}"
                    showError = true
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
    
    // 保存操作效果
    LaunchedEffect(shouldSave) {
        if (shouldSave && !isSaveInProgress) {
            isSaveInProgress = true
            isLoading = true
            
            try {
                val success = withContext(Dispatchers.IO) {
                    viewModel.saveAppConfigFile(
                        AppConfigFile(
                            fileName = fileNameState.trim(),
                            content = contentState.text,
                            fileExtension = getFileExtension(fileNameState.trim())
                        )
                    )
                }
                if (success) {
                    showSaveSuccess = true
                    errorMessage = "配置文件保存成功"
                    delay(500) // 短暂延迟
                    onNavigateBack()
                } else {
                    errorMessage = "保存失败：文件写入错误"
                    showError = true
                }
            } catch (e: Exception) {
                errorMessage = "保存失败: ${e.localizedMessage ?: "未知错误"}"
                showError = true
            } finally {
                isLoading = false
                shouldSave = false
                isSaveInProgress = false
            }
        }
    }
    
    LaunchedEffect(showSaveSuccess, showError) {
        if (showSaveSuccess) {
            delay(2000)
            showSaveSuccess = false
        }
        
        if (showError) {
            delay(3000)
            showError = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (fileName != null) "编辑配置" else "新建配置",
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // 边界条件检查
                            when {
                                fileNameState.isBlank() -> {
                                    errorMessage = "文件名不能为空"
                                    showError = true
                                    return@IconButton
                                }
                                contentState.text.isBlank() -> {
                                    errorMessage = "配置内容不能为空"
                                    showError = true
                                    return@IconButton
                                }
                                !isValidFileName(fileNameState) -> {
                                    errorMessage = "文件名包含非法字符或格式不正确"
                                    showError = true
                                    return@IconButton
                                }
                                contentState.text.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024 -> { // 2MB限制，与AppConfigManager保持一致
                                    errorMessage = "配置文件过大，请控制在2MB以内"
                                    showError = true
                                    return@IconButton
                                }
                                isSaveInProgress -> {
                                    // 防止重复点击
                                    return@IconButton
                                }
                                else -> {
                                    // 触发保存操作
                                    shouldSave = true
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "保存"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = remember { SnackbarHostState() }
            ) {
                when {
                    showSaveSuccess -> {
                        Snackbar(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text("配置文件保存成功")
                        }
                    }
                    showError -> {
                        Snackbar(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(errorMessage)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 文件名输入框
            OutlinedTextField(
                value = fileNameState,
                onValueChange = { fileNameState = it },
                label = { Text("文件名") },
                placeholder = { Text("例如: config.json, settings.toml") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
            )

            // 文件类型提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "例如这些格式的配置:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ConfigFileType.values().joinToString(", ") { it.extension },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 清空内容按钮
                OutlinedButton(
                    onClick = { 
                        contentState = TextFieldValue(
                            text = "",
                            selection = TextRange.Zero,
                            composition = null
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 粘贴按钮
                OutlinedButton(
                    onClick = { 
                        val clipText = clipboardManager.getText()?.text ?: ""
                        contentState = TextFieldValue(
                            text = clipText,
                            selection = if (clipText.isEmpty()) TextRange.Zero else TextRange(0, 0),
                            composition = null
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "粘贴",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 配置内容输入框
            OutlinedTextField(
                value = contentState,
                onValueChange = { contentState = it },
                label = { Text("配置内容") },
                placeholder = { Text("在此输入配置文件内容...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
            )



            // 底部间距
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun isValidFileName(fileName: String): Boolean {
    if (fileName.isBlank() || fileName.length > 255) return false
    
    // 检查非法字符
    val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    
    if (fileName.any { it in invalidChars }) return false
    
    if (fileName.startsWith('.')) return false
    
    return true
}

private fun getFileExtension(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "")
    return if (extension.isNotEmpty() && extension != fileName) {
        ".$extension"
    } else {
        ""
    }
}