# Android Service Manager

<div align="center">
  <h3>🚀 现代化的 Android 二进制服务管理器</h3>
  <p>基于 Kotlin + Jetpack Compose + Material Design 3 构建</p>
  
  [![Build Status](https://github.com/your-username/android-service/actions/workflows/build.yml/badge.svg)](https://github.com/your-username/android-service/actions)
  [![Release](https://img.shields.io/github/v/release/your-username/android-service)](https://github.com/your-username/android-service/releases)
  [![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
  [![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
</div>

## 🎯 项目简介

Android Service Manager 是一个现代化的 Android 应用，专为管理跨平台二进制内核（如 xray、V2Ray、frp 等）而设计，功能类似于 Windows 的 NSSM（Non-Sucking Service Manager）。

### ✨ 核心特性

- 🔧 **二进制文件管理** - 专为 ARM64 架构优化
- 🚀 **进程服务管理** - 启动、停止、监控二进制进程
- 🔄 **自动重启机制** - 智能监控进程状态，异常时自动重启
- 📊 **实时日志显示** - 实时捕获和显示进程输出
- ⚙️ **配置管理系统** - 支持配置导入导出、参数自定义
- 🎨 **现代化界面** - Material Design 3 + Jetpack Compose
- 🌙 **动态主题支持** - 支持浅色/深色模式，跟随系统设置
- 🔒 **前台服务保护** - 确保服务在后台稳定运行

## 📱 界面预览

| 主页 | 管理 | 日志 | 配置 |
|:---:|:---:|:---:|:---:|
| 服务状态控制 | 二进制文件管理 | 实时日志显示 | 配置参数设置 |

## 🏗️ 技术架构

### 核心技术栈

- **Kotlin** - Google 官方推荐，空安全、协程支持
- **Jetpack Compose** - 声明式 UI 框架
- **Material Design 3** - 现代化 UI 设计语言
- **Android Architecture Components** - MVVM 架构组件
- **Coroutines & Flow** - 异步编程和响应式数据流
- **DataStore** - 现代化数据存储方案

### 架构特点

- **MVVM 架构** - ViewModel + LiveData/Flow 状态管理
- **单一数据源** - 统一的状态管理和数据流
- **协程异步** - 全面使用 Kotlin 协程处理异步任务
- **组件化设计** - 模块化的代码结构，易于维护扩展

## 📥 下载安装

### 系统要求

- Android 7.0 (API 24) 及以上版本
- 支持架构: ARM64 (arm64-v8a)

### 安装方式

1. **从 Releases 下载**
   - [最新版本](https://github.com/your-username/android-service/releases/latest)
   - 选择对应架构的 APK 文件，或下载通用版本

2. **手动安装**
   ```bash
   # 启用未知来源安装
   # 下载 APK 后直接安装
   adb install android-service-manager-v1.0.0-universal.apk
   ```

## 🚀 使用指南

### 快速开始

1. **安装应用**
   - 下载并安装对应版本的 APK
   - 授予应用必要的权限（通知、前台服务等）

2. **准备二进制文件**
   - 将需要管理的二进制文件按架构放入对应目录
   - 或通过应用的管理功能导入二进制文件

3. **配置服务**
   - 在"配置"页面设置二进制文件名和启动参数
   - 设置自动重启选项和重启策略

4. **启动服务**
   - 在"主页"点击启动按钮开始服务
   - 在"日志"页面查看实时运行状态

### 详细功能说明

#### 🏠 主页面
- **服务状态** - 实时显示服务运行状态
- **快速控制** - 启动、停止、重启服务
- **状态信息** - 显示进程 ID、运行时长、重启次数等

#### 🔧 管理页面
- **二进制文件列表** - 显示所有可用的二进制文件
- **架构信息** - 显示文件架构和可执行状态
- **文件管理** - 支持删除和重新扫描文件

#### 📊 日志页面
- **实时日志** - 实时显示进程输出和系统日志
- **日志过滤** - 支持按级别过滤日志内容
- **自动滚动** - 新日志自动滚动到底部

#### ⚙️ 配置页面
- **基本配置** - 设置二进制文件名和工作目录
- **启动参数** - 添加、删除启动参数
- **重启设置** - 配置自动重启和重启策略
- **配置管理** - 导入导出配置文件

### 二进制文件准备

应用支持以下目录结构来管理不同架构的二进制文件：

```
assets/
└── arm64-v8a/
    └── xray          # ARM64 架构版本
```

## 🛠️ 开发构建

### 开发环境

- Android Studio Hedgehog 2023.1.1+
- JDK 11+
- Android SDK API 35
- Kotlin 2.1.0+

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/your-username/android-service.git
cd android-service

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 构建所有架构版本
./gradlew build
```

### 项目结构

```
app/src/main/java/com/androidservice/
├── MainActivity.kt                    # 主活动
├── AndroidServiceApplication.kt       # 应用程序类
├── data/                             # 数据模型
├── manager/                          # 管理器类
│   ├── BinaryManager.kt              # 二进制文件管理
│   ├── ProcessManager.kt             # 进程管理
│   └── ConfigManager.kt              # 配置管理
├── service/                          # 服务类
├── ui/                              # UI 组件
└── viewmodel/                       # ViewModel 层
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 贡献指南

1. Fork 项目
2. 创建功能分支: `git checkout -b feature/amazing-feature`
3. 提交更改: `git commit -m 'Add amazing feature'`
4. 推送分支: `git push origin feature/amazing-feature`
5. 创建 Pull Request

### 开发规范

- 遵循 Kotlin 编码规范
- 使用 Jetpack Compose 进行 UI 开发
- 遵循 MVVM 架构模式
- 添加适当的单元测试

## 📄 许可证

本项目采用 Apache 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

- [Android Jetpack](https://developer.android.com/jetpack) - 现代化 Android 开发组件
- [Material Design](https://material.io/) - 设计系统和 UI 组件
- [Kotlin](https://kotlinlang.org/) - 现代化编程语言

## 📞 支持

- 📧 邮箱: your-email@example.com
- 🐛 问题反馈: [GitHub Issues](https://github.com/your-username/android-service/issues)
- 💬 讨论: [GitHub Discussions](https://github.com/your-username/android-service/discussions)

---

<div align="center">
  <p>⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！</p>
</div>
