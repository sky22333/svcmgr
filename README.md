## android-service开发任务
角色：你是一个 Android 高级开发工程师，精通KT语言现代化开发技术栈。
目标：开发一个现代化 Android App，用于管理跨平台二进制内核（如 xray、V2Ray frp等等），功能类似于 Windows 的 NSSM（Non-Sucking Service Manager）。首先制定一个任务列表，依次从核心功能到完整功能逐步开发，避免一次全面实现导致太复杂导致BUG太多，支持流水线自动构建和发布（不需要太复杂的流水线，只需要手动触发，支持传入tag，然后自动构建和发布到Releases即可，不要其他的，尽量简化），确保每次都能成功构建和运行，不会有BUG和错误。
技术栈：
  - Kotlin（谷歌官方推荐，支持空安全、协程、Flow）
  - Jetpack Compose（声明式 UI，取代 XML 布局）
  - Material Design 3（Material You，动态主题）
  - Android Architecture Components（ViewModel、LiveData、Lifecycle、Navigation）
功能需求：
  1. 二进制管理
     - 支持多 ABI（armeabi-v7a, arm64-v8a, x86, x86_64）二进制打包
     - 将 assets/<abi>/binary 拷贝到 App 私有目录，并赋可执行权限
     - 可启动、停止和监控二进制进程
     - 支持日志输出到 Compose UI（实时更新）
  2. 前台服务
     - 使用 Foreground Service 确保进程在后台不易被系统杀死
     - 返回 START_STICKY 保证系统资源允许时自动重启
     - 可配置通知栏样式（Material Design 3）
  3. 自动重启机制
     - 监听二进制退出状态，自动重启
     - 可结合 WorkManager 或 AlarmManager 定期检查服务状态
  4. UI/UX
     - 使用 Compose 构建动态 Material You 风格界面
     - 实时显示服务状态（运行/停止）、日志输出、ABI 信息、启动参数
     - UI现代化美观，拥有底部导航栏，并且避免使用卡片容器，所有元素直接显示在主容器中，避免一眼就能看出是AI开发的风格
     - 支持深色/浅色主题跟随系统
  5. 配置管理
     - 支持加载/保存二进制配置文件（如 config.json）
     - 可通过 UI 动态修改启动参数
     - 使用 ViewModel + Flow/LiveData 管理状态
  6. 权限与兼容性
     - INTERNET, FOREGROUND_SERVICE 等必要权限
     - 不同 Android 版本适配前台服务策略（8.0+）
其他要求：
  - 全程使用 Kotlin 协程处理异步任务（如进程输出、拷贝文件、重启服务）
  - UI 交互响应流畅，尽量避免阻塞主线程
  - 代码架构遵循 AAC + Compose + MVVM
  - 重点优化性能和电量消耗
输出：
  - 提供完整 Kotlin 代码示例（包括 Foreground Service、ViewModel、Compose UI）
  - 支持多 ABI 二进制管理和启动
  - 具备自动重启机制和日志实时显示
  - Material You 动态主题适配
