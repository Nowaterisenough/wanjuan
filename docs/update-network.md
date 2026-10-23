# 更新检查与系统 VPN

## 请求路径

检查更新和更新日志共用 `GitHubReleaseClient`，使用全新标准 OkHttp 客户端，而不是复制书源客户端。保留系统 DNS、TLS 验证和代理选择，不指定直连代理、不绑定物理网络，也不主动绕过 Android VPN/TUN。最终是否被 TUN 接管、是否经过代理出口，由 VPN 应用的接管名单和分流规则决定。

连接超时 15 秒、单次调用 30 秒；网络错误以及 HTTP 502/503/504 至多重试一次，整个操作最多 45 秒。证书错误、权限错误和限流不重试。响应体在取消保护范围内读取并关闭，超时或取消会关闭仍在读取的请求。

错误提示区分 DNS、连接、TLS、超时、GitHub 限流、无公开发布、缺少 APK 和异常响应格式。没有更高版本属于正常结果，不进入错误处理。支持标准 APK MIME 和 `application/octet-stream`，比较版本时兼容旧小时版本号与新分钟版本号。

## Cronet 修复

143.0.7445.0 的 Native provider 构造函数依赖 `HttpEngineNativeProvider`；其官方发布目录单独提供 `httpengine_native_provider_java.jar`。下载脚本现在包含这个同版本包，不再用 `-dontwarn` 隐藏缺失的 provider。Java 备用实现所需的 util 类已包含在 common 包中，不额外引入 util jar，以免重复类。

Native、HttpEngine、Java provider 的反射构造入口需要保留。新增保留规则放在非自动生成的 `app/proguard-rules.pro`，避免更新上游规则时被覆盖。

引擎构造与请求初始化均位于失败回退边界内；普通异常和类链接失败回退 OkHttp，取消和虚拟机致命错误不被吞掉。普通 OkHttp 回退自身的错误不会触发重复发送。Cronet 开关按每次请求即时读取，关闭后新的请求立即使用 OkHttp，不需要重建客户端或重启。打开时触发库预下载；下载未完成时继续用 OkHttp。

## 回归验证

- JVM：provider 初始化失败、缺类、取消、回退次数、系统代理选择、响应体取消、总超时、限流、重试、版本选择。
- Android：Cronet 开关即时生效、更新网络隔离、开启 Cronet 后读取真实公开 GitHub 发布信息。
- 正常 R8 Release：使用 `tools/release-probe` 的纯 Android Instrumentation，通过反射构造三个 provider 并实际检查更新；避免 AndroidX 测试运行库与已混淆的 Kotlin/回移库冲突。
- 手机 TUN 路由仍须在用户实际 VPN 环境中验证；模拟器测试不能代替手机端接管和分流规则验证。
