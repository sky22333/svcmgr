# Android Service Manager - 开发文档

## 项目概述

Android Service Manager 是一个现代化的 Android 应用，用于管理跨平台二进制内核（如 xray、V2Ray、frp等），功能类似于 Windows 的 NSSM（Non-Sucking Service Manager）。

## 技术架构

### 核心技术栈

- **Kotlin**: Google 官方推荐，支持空安全、协程、Flow
- **Jetpack Compose**: 声明式 UI，取代 XML 布局
- **Material Design 3**: Material You，动态主题
- **Android Architecture Components**: ViewModel、LiveData、Lifecycle、Navigation
- **协程 (Coroutines)**: 处理异步任务
- **DataStore**: 现代化的数据存储方案

### 项目结构

```
app/src/main/java/com/androidservice/
├── MainActivity.kt                    # 主活动
├── AndroidServiceApplication.kt       # 应用程序类
├── data/                             # 数据模型
│   └── ServiceState.kt
├── manager/                          # 管理器类
│   ├── BinaryManager.kt              # 二进制文件管理
│   ├── ProcessManager.kt             # 进程管理
│   └── ConfigManager.kt              # 配置管理
├── service/                          # 服务类
│   └── BinaryProcessService.kt       # 前台服务
├── ui/                              # UI 相关
│   ├── MainScreen.kt                # 主界面导航
│   ├── screens/                     # 各个屏幕
│   └── theme/                       # 主题定义
└── viewmodel/                       # ViewModel
    └── MainViewModel.kt
```

## 核心功能实现

### 1. 二进制文件管理 (BinaryManager)

- **ARM64 专用**: 专为 arm64-v8a 架构优化
- **自动复制**: 从 assets 复制到应用私有目录
- **权限设置**: 自动设置可执行权限
- **智能选择**: 根据设备架构自动选择最佳二进制文件

### 2. 进程管理 (ProcessManager)

- **进程启动**: 支持自定义参数和环境变量
- **进程监控**: 实时监控进程状态和输出
- **进程停止**: 优雅停止和强制终止
- **日志收集**: 收集标准输出和错误输出

### 3. 前台服务 (BinaryProcessService)

- **前台服务**: 确保进程在后台不被系统杀死
- **START_STICKY**: 系统资源允许时自动重启
- **通知管理**: Material Design 3 风格的通知
- **状态同步**: 与UI实时同步服务状态

### 4. 自动重启机制

- **异常检测**: 监听进程退出状态
- **重启策略**: 可配置的重启延迟和最大重启次数
- **故障恢复**: 智能处理进程崩溃和异常退出

### 5. 配置管理 (ConfigManager)

- **DataStore集成**: 使用 Preferences DataStore 存储配置
- **文件导入导出**: JSON格式的配置文件
- **实时同步**: 配置变更实时保存和同步

### 6. 现代化UI

- **Jetpack Compose**: 完全使用声明式UI
- **Material Design 3**: 支持动态主题和深色模式
- **响应式设计**: 适配不同屏幕尺寸
- **底部导航**: 简洁的四页面导航结构

## 开发环境要求

- Android Studio Hedgehog | 2023.1.1 或更高版本
- Kotlin 2.1.0+
- Android SDK API 35 (compileSdk)
- 最低支持 Android 7.0 (API 24)
- Gradle 8.9
- JDK 17+

## 构建配置

### Gradle 配置特性

- **ARM64专用**: 专为 arm64-v8a 架构构建
- **JDK 17**: 使用最新的 JDK 17 环境
- **代码混淆**: Release版本启用ProGuard
- **增量编译**: 优化构建速度

### 依赖管理

项目使用 Version Catalog (libs.versions.toml) 管理依赖版本，主要依赖包括：

- Jetpack Compose BOM 2024.12.01
- Navigation Compose 2.8.4
- ViewModel Compose 2.8.7
- Coroutines 1.9.0
- WorkManager 2.10.0
- DataStore 1.1.1
- Gson 2.11.0

## CI/CD 流程

### GitHub Actions 工作流

1. **开发构建** (.github/workflows/build.yml)
   - 触发条件: push 到 main/develop 分支，PR 到 main
   - 构建 debug APK
   - 运行 lint 检查
   - 上传构建产物

2. **发布构建** (.github/workflows/build-and-release.yml)
   - 触发条件: 创建 tag 或手动触发
   - 构建所有架构的 APK 和 AAB
   - 创建 GitHub Release
   - 上传发布包

### 发布流程

1. 更新版本号 (app/build.gradle.kts)
2. 创建 git tag: `git tag v1.0.0`
3. 推送 tag: `git push origin v1.0.0`
4. GitHub Actions 自动构建并发布

## 安全考虑

- **权限最小化**: 只请求必要的权限
- **数据隔离**: 二进制文件存储在应用私有目录
- **配置保护**: 敏感配置排除在备份之外
- **进程隔离**: 二进制进程在独立的进程空间运行

## 性能优化

- **协程使用**: 所有异步操作使用协程
- **内存管理**: 及时释放资源，避免内存泄漏
- **UI优化**: 使用Compose的状态管理优化重组
- **构建优化**: 启用R8代码压缩和混淆

## 调试和测试

### 日志系统

- **分级日志**: DEBUG, INFO, WARN, ERROR
- **来源标识**: 区分系统、应用、进程日志
- **实时显示**: UI实时显示日志输出

### 测试建议

1. **单元测试**: 测试核心业务逻辑
2. **集成测试**: 测试组件间交互
3. **UI测试**: 使用 Compose Testing 测试界面
4. **设备测试**: 在不同架构设备上测试

## 故障排除

### 常见问题

1. **二进制文件无法执行**
   - 检查文件权限设置
   - 确认架构匹配
   - 验证文件完整性

2. **服务无法启动**
   - 检查权限配置
   - 查看系统日志
   - 验证配置参数

3. **进程异常退出**
   - 查看错误日志
   - 检查依赖库
   - 验证运行环境

### 日志收集

应用内置完整的日志系统，支持：
- 实时日志显示
- 日志级别过滤
- 多源日志聚合
- 历史日志查询

## 扩展开发

### 添加新功能

1. 在对应的管理器类中添加功能实现
2. 更新 ViewModel 添加状态管理
3. 创建或更新 Compose UI
4. 更新配置模型（如需要）
5. 添加相应的测试用例

### 自定义主题

项目支持 Material Design 3 动态主题，可以通过修改 `ui/theme/` 目录下的文件来自定义主题。

### 国际化支持

在 `res/values/` 目录下添加不同语言的字符串资源文件，如：
- `values-en/strings.xml` (英文)
- `values-zh-rCN/strings.xml` (简体中文)
- `values-zh-rTW/strings.xml` (繁体中文)

## 贡献指南

1. Fork 项目
2. 创建功能分支: `git checkout -b feature/amazing-feature`
3. 提交更改: `git commit -m 'Add amazing feature'`
4. 推送分支: `git push origin feature/amazing-feature`
5. 创建 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证，详见 LICENSE 文件。