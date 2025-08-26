## svcmgr

现代化的 Android 二进制程序管理器

## 📱 项目简介

**svcmgr** 是一个 Android 二进制程序管理应用，专为管理和监控跨平台二进制程序（如 openlist、frpc 等工具）而设计。应用提供了直观的图形界面来启动、停止、配置和监控二进制服务的运行状态。

## ✨ 核心功能

### 🏠 服务管理
- **一键启停** - 简单的启动/停止按钮控制服务的运行状态
- **实时状态** - 显示服务运行状态、进程ID、启动时间等信息
- **自动重启** - 支持服务异常退出后自动重启
- **前台服务** - 通过系统通知保持服务稳定运行

### ⚙️ 配置管理
- **二进制选择** - 自动获取打包的可用二进制文件
- **参数配置** - 灵活配置启动参数和环境变量
- **配置持久化** - 使用 DataStore 安全存储配置信息
- **手动保存** - 配置修改后需手动保存，避免意外更改

### 📋 日志监控
- **实时日志** - 显示服务运行时的详细日志输出
- **内存优化** - 限制日志条目数量，防止内存溢出
- **可选择文本** - 支持复制日志内容进行分析

### 📁 文件管理
- **配置文件管理** - 创建、编辑、删除应用配置文件
- **文件路径显示** - 显示配置文件的完整路径
- **修改时间追踪** - 显示文件最后修改时间

## 📋 系统要求

- **Android 版本**: Android 7.0 (API 24) 及以上
- **架构支持**: ARM64 (arm64-v8a)

## 🔧 二进制文件支持

应用通过 JNI 库的形式集成二进制文件，高兼容性的启动方式，支持各种跨平台项目

例如：将`frp_android_arm64`类似的安卓二进制程序改为`libfrpc.so`，然后编译打包即可

```
app/src/main/jniLibs/arm64-v8a/
├── libfrpc.so    # frpc 内核
└── libopenlist.so   # openlist 内核
```

## 🛠️ 开发环境

### 环境要求
- **Android Studio**: 2023.1.1 (Hedgehog) 或更高版本
- **JDK**: 17 或更高版本
- **Android SDK**: API 35
- **Gradle**: 8.7.2

### 构建步骤

```bash
# 构建调试版本
./gradlew assembleDebug

# 构建发布版本
./gradlew assembleRelease
```

## 📁 项目结构

```
app/src/main/java/com/androidservice/
├── MainActivity.kt                    # 主活动入口
├── AndroidServiceApplication.kt       # 应用程序类
├── data/                             # 数据模型定义
│   └── ServiceState.kt               # 服务状态、配置等数据类
├── manager/                          # 核心管理器
│   ├── BinaryManager.kt              # 二进制文件扫描和管理
│   ├── ProcessManager.kt             # 进程启动和监控
│   ├── ConfigManager.kt              # 服务配置管理
│   └── AppConfigManager.kt           # 应用配置文件管理
├── service/                          # Android 服务
│   └── BinaryProcessService.kt       # 前台服务实现
├── ui/                              # UI 组件
│   ├── MainScreen.kt                 # 主界面和导航
│   ├── screens/                      # 各个功能页面
│   └── theme/                        # UI 主题定义
└── viewmodel/                       # ViewModel 层
    └── MainViewModel.kt              # 主要的状态管理
```


## ⚠️ 免责声明

- 本程序仅供学习交流使用，请勿用于非法用途
- 使用本程序需遵守当地法律法规
- 作者不对使用者的任何行为承担责任

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star！⭐**

</div>
