## svcmgr

现代化 Android 二进制服务管理器。

`svcmgr` 用于管理打包在应用内的 ARM64 二进制程序，例如 `openlist`、`frpc`、`cloudflared` 等。应用提供启动、停止、运行状态、配置文件编辑、配置备份和实时日志查看能力。

## 功能

- 服务控制：启动、停止、前台服务通知、运行状态与进程 ID 展示。
- 程序发现：自动扫描 `jniLibs/arm64-v8a/lib*.so`，需要编译前放进去，重新编译后才生效
- 运行配置：保存程序名、启动参数、环境变量、自动重启、重启延迟和最大重启次数。
- 配置文件：在应用私有目录中创建、编辑、复制路径和删除 `.json`、`.toml`、`.yaml`、`.conf`、`.txt`、`.xml` 等配置文件。
- 实时日志：聚合 stdout、stderr 和应用日志，保留最近 500 条，支持选择复制。

## 二进制文件

将可执行的 Android ARM64 程序放入：

```text
app/src/main/jniLibs/arm64-v8a/
```

命名示例：

```text
libfrpc.so
libopenlist.so
libcloudflared.so
```

应用会显示为：

```text
frpc
openlist
cloudflared
```

然后重新编译

## 项目结构

```text
app/src/main/java/com/androidservice/
  data/        数据模型
  manager/     二进制、运行配置、配置文件和进程管理
  service/     前台服务
  ui/          Compose UI 与主题
  viewmodel/   应用状态中心
```

## 内核来源

cloudflared：
```
# 下载源码
git clone https://github.com/cloudflare/cloudflared.git

# 交叉编译环境变量
export GOOS=android
export GOARCH=arm64
export CGO_ENABLED=0

# 获取版本号和构建时间
VERSION="$(git describe --tags --always --match '[0-9]*.*.*')"
BUILD_TIME="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

# 构建
go build \
  -trimpath \
  -tags "osusergo netgo" \
  -ldflags "-s -w -X main.Version=${VERSION} -X main.BuildTime=${BUILD_TIME}" \
  -o cloudflared-android-arm64 \
  ./cmd/cloudflared
```

其余内核均来自上游官方的安卓版本

## 免责声明

本项目仅供学习和合法场景使用。使用者需自行遵守当地法律法规，并对自己的使用行为负责。
