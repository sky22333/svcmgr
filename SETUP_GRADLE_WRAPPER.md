# Gradle Wrapper 设置指南

## 问题描述
项目缺少 `gradle-wrapper.jar` 文件，导致 Gradle Wrapper 无法正常工作。

## 解决方案

### 方法 1: 使用已安装的 Gradle 生成 Wrapper

如果您已经安装了 Gradle：

```bash
# 在项目根目录执行
gradle wrapper --gradle-version 8.9
```

这会自动生成：
- `gradle/wrapper/gradle-wrapper.jar`
- 更新 `gradle/wrapper/gradle-wrapper.properties`

### 方法 2: 从官方下载 Gradle Wrapper JAR

1. 下载 Gradle 8.9 wrapper JAR：
   ```bash
   # Linux/macOS
   curl -o gradle/wrapper/gradle-wrapper.jar https://services.gradle.org/distributions-snapshots/gradle-8.9-wrapper.jar
   
   # 或者访问: https://services.gradle.org/distributions-snapshots/gradle-8.9-wrapper.jar
   # 手动下载并放到 gradle/wrapper/ 目录
   ```

2. Windows PowerShell：
   ```powershell
   Invoke-WebRequest -Uri "https://services.gradle.org/distributions-snapshots/gradle-8.9-wrapper.jar" -OutFile "gradle\wrapper\gradle-wrapper.jar"
   ```

### 方法 3: 使用 Android Studio 或 IntelliJ

1. 在 Android Studio 中打开项目
2. IDE 会自动检测并提示修复 Gradle Wrapper
3. 点击"修复"或"同步项目"

### 方法 4: 复制现有项目的 Wrapper

从另一个正常工作的 Android 项目中复制：
- `gradle/wrapper/gradle-wrapper.jar`

## 验证修复

修复后，运行以下命令验证：

```bash
# Linux/macOS
./gradlew --version

# Windows
gradlew.bat --version
```

应该看到 Gradle 版本信息。

## 构建项目

修复后，可以正常构建：

```bash
# 清理项目
./gradlew clean

# 构建 Debug APK
./gradlew assembleDebug

# 查看生成的 APK
ls app/build/outputs/apk/debug/
```

## 注意事项

- `gradle-wrapper.jar` 是二进制文件，必须是正确的版本
- 确保网络连接正常，首次运行会下载 Gradle 发行版
- 如果仍有问题，删除 `~/.gradle` 目录并重试

## 项目状态

修复 Gradle Wrapper 后，项目配置是：

- ✅ **架构支持**: 仅 ARM64 (arm64-v8a)
- ✅ **JDK 版本**: JDK 17
- ✅ **依赖版本**: 最新稳定版
- ✅ **构建配置**: Debug 自动签名
- ✅ **CI/CD**: GitHub Actions 就绪

修复完成后，所有构建命令都应该正常工作。