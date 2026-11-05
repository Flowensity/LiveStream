# 03 - 技术选型核对与依赖版本治理

## 1. 核心技术栈与选型理由

| 领域 | 选型组件 | 版本/说明 | 选型理由与决策说明 |
| :--- | :--- | :--- | :--- |
| **基础语言** | Kotlin | 1.9.22 | 业界成熟稳定的 Kotlin 语言版本，与 Compose Compiler 1.5.8 官方深度适配。 |
| **构建系统** | Gradle + AGP | Gradle 8.5 / AGP 8.2.2 | 稳定工业级组合，支持 Java 17、Gradle Version Catalog、配置缓存与并行构建。 |
| **Java 工具链** | Temurin OpenJDK | 17.0.21 | 统一规范全局字节码目标为 Java 17，享受最新 JVM 优化与语言特性。 |
| **UI 框架** | Jetpack Compose + Compose for TV | BOM 2024.02.00 / TV 1.0.0-alpha10 | 统一的声明式 UI 体系，支持手机触控与电视 D-pad 遥控器焦点感知。 |
| **依赖注入** | Google Dagger Hilt | 2.50 | 官方推荐依赖注入框架，与 AndroidX ViewModel、Compose 无缝整合。 |
| **媒体播放** | AndroidX Media3 (ExoPlayer) | 1.2.1 | 官方下一代媒体播放库，原生高效支持 HLS、DASH、HTTP 渐进式流媒体与硬件解码。 |
| **本地持久化** | Room + DataStore | Room 2.6.1 / DataStore 1.0.0 | Room 强类型结构化存储频道与 EPG，DataStore 偏好存储用户播放配置与键位偏好。 |
| **网络层** | OkHttp + Retrofit | OkHttp 4.12.0 / Retrofit 2.9.0 | 成熟可靠的 HTTP 协议栈，方便集中配置连接池、DNS 缓存、超时策略与拦截器。 |
| **异步流** | Kotlin Coroutines + Flow | 1.8.0 | 原生支持结构化并发、协程生命周期取消与背压控制。 |
| **测试套件** | JUnit4 + MockK + Turbine | 4.13.2 / 1.13.9 / 1.0.0 | 全面覆盖单元测试、协程 Flow 状态流断言与依赖模拟。 |

---

## 2. 软硬件兼容与性能基线

- **minSdk**：23 (Android 6.0 Marshmallow)，覆盖绝大多数国内存量 Android TV、机顶盒与移动设备。
- **targetSdk**：34 (Android 14)，满足现代安全、权限规范与 Android 系统兼容性。
- **compileSdk**：34，与平台安装 SDK 保持一致。
- **解码偏好**：硬件解码优先（`MediaCodecSelector.DEFAULT`），仅当系统硬件解码器报错或不支持时由策略决策，严禁无差别全局强制软解。
- **显示载体**：默认 SurfaceView 渲染，降低合成延迟与内存拷贝开销，仅在需要复杂 UI 旋转或半透明混色时才按需回退 TextureView。
