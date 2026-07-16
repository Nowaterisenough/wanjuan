# Cloudflare WebView 验证生命周期修复设计

## 目标

修复发现刷新遇到 Cloudflare Challenge 时反复调起或停留在前台 WebView 的问题。正常页面即使包含 Cloudflare 的普通 JSD 检测脚本，也不得被判定为仍处于 Challenge；真实 Challenge 解除后，WebView 应自动把可用页面交回原请求并关闭。

## 约束

- App 代码保持书源无关，不在 Kotlin 代码中加入禁漫天堂名称、域名或其他站点特例。
- 登录页和站点自有 `/verify.php` 仍保留人工处理能力。
- 真实 Cloudflare Challenge 优先尝试后台静默处理，失败时才显示前台 WebView。
- `refetchAfterSuccess=false` 必须继续表示“使用当前 WebView 页面”，不得退化为验证后重新发起 HTTP 请求。
- 任何构建只使用签名 Release，构建版本号严格递增；修改完成后安装到已连接的模拟器并启动应用。

## 已确认根因

动态书源已重新导入，因此本次问题不是旧 `loginCheckJs` 残留。当前实现仍存在两个相互叠加的生命周期缺陷：

1. `CloudflareVerification.isChallengeBody` 把 `challenge-platform`、`cf-ray`、`cdn-cgi/challenge-platform` 等普通基础设施标记单独视为有效 Challenge。站点正常 `200` 页面也会注入 `/cdn-cgi/challenge-platform/scripts/jsd/main.js`，导致 WebView 已经加载出正常内容和广告后仍被判断为“挑战未结束”。
2. `WebViewModel.shouldAutoReturnAfterCloudflareChallenge` 和 `shouldAutoReturnCloudflarePage` 都要求 `refetchAfterSuccess=true`。动态书源为了直接使用 WebView 中已经通过验证的 HTML，调用的是 `startBrowserAwait(..., false)`，因此现有代码禁止自动回传和关闭。

上次修复只收紧了动态书源首次调用 `startBrowserAwait` 的条件，没有覆盖原生 WebView 从“挑战页”切换到“正常页”的状态判定和回传行为。

## 方案

### 1. 收紧 Cloudflare Challenge 正文判定

将 `CloudflareVerification` 的正文判断改为识别“正在进行的 Challenge”，而不是识别页面是否使用 Cloudflare：

- 明确挑战标题，如 `Just a moment`、`Checking your browser`、`Attention Required`。
- 明确挑战表单或交互控件，如 `challenge-form`、`cf-turnstile-response`、`challenges.cloudflare.com`。
- 明确挑战运行时配置，如 `_cf_chl_opt`、`cf_chl_*`。
- 普通页面中孤立出现的 `challenge-platform/scripts/jsd/main.js`、`cf-ray`、`cf_clearance` 或通用 `/cdn-cgi/` 资源不构成 Challenge。

该判断继续集中在通用 Kotlin 组件中，供后台 WebView 和前台 WebView 复用。

### 2. 分离“结果来源”与“是否自动结束”

`refetchAfterSuccess` 只决定验证成功后的结果来源：

- `true`：重新请求原 URL，优先返回新的 HTTP 响应；重新请求仍失败时再保存当前 WebView HTML。
- `false`：直接保存当前 WebView URL 和 HTML，不重新请求。

它不再控制 WebView 是否自动结束。只要本次浏览器承担的是源验证、曾检测到真实 Challenge，随后页面不再是 Challenge，就自动保存结果并关闭。

对于标题明确为 Cloudflare 的验证请求，如果首次加载已经是正常网络页面，也可以直接自动回传；保存路径仍严格遵循 `refetchAfterSuccess`。

### 3. 让动态书源显式区分验证类型

动态书源的 `loginCheckJs` 保留现有登录失效、站点验证和 Cloudflare 三类判断，但为 Cloudflare 分支传入包含 `Cloudflare` 的标题：

- 登录失效：`startBrowserAwait(url, "登录", false)`。
- 站点 `/verify.php`：`startBrowserAwait(url, "验证", false)`。
- Cloudflare Challenge：`startBrowserAwait(url, "Cloudflare 验证", false)`。

这样通用验证组件可以只对 Cloudflare 流程启用后台静默 WebView，不会错误跳过需要用户输入的登录页或站点验证码。

## 数据流

1. 发现请求取得 HTTP 响应并执行动态书源 `loginCheckJs`。
2. 普通响应直接进入发现解析器。
3. 明确 Cloudflare Challenge 调用 `startBrowserAwait`，通用验证组件先用后台 WebView 加载相同 URL、请求头和 Cookie。
4. 后台页面变为正常内容后，使用其最终 URL 和 HTML 恢复原请求，不显示前台 WebView。
5. 后台处理超时或仍是 Challenge 时，才打开前台 WebView。
6. 用户完成 Challenge 后，前台页面一旦变为正常内容，立即按 `refetchAfterSuccess` 选择结果来源、关闭 WebView，并恢复发现解析。

## 错误处理

- 后台 WebView 超时、页面为空或仍含明确挑战特征时，回退到现有前台人工验证。
- 登录和站点自有验证不使用 Cloudflare 静默路径，避免绕过人工输入。
- 前台验证结果为空时保留现有错误语义，不把空 HTML 当作成功。
- 普通 Cloudflare 基础设施脚本不再阻塞自动结束，但明确挑战标题、表单和运行时配置仍会阻止回传。
- 多个并发请求继续共享同一书源的 Cloudflare 验证状态，避免同时打开多个 WebView。

## 测试设计

### Cloudflare 判定单元测试

- 正常 `200` 页面包含 `/cdn-cgi/challenge-platform/scripts/jsd/main.js` 时返回 `false`。
- 正常页面孤立包含 `cf-ray`、`cf_clearance` 或通用 `/cdn-cgi/` 资源时返回 `false`。
- `Just a moment`、`challenge-form`、`cf-turnstile-response`、`_cf_chl_opt` 等明确挑战页面返回 `true`。

### WebView 生命周期单元测试

将自动结束条件抽成可独立测试的策略：

- 非源验证浏览器不自动回传。
- 源验证曾进入 Challenge、随后加载正常页面时自动回传。
- 上述行为不受 `refetchAfterSuccess` 真假的影响。
- `refetchAfterSuccess=false` 时选择当前 WebView HTML。
- `refetchAfterSuccess=true` 时选择重新请求路径。

### 动态书源 Rhino 测试

- 正常页面不调用浏览器。
- 登录失效和 `/verify.php` 使用原有人工验证标题。
- 明确 Cloudflare Challenge 使用 `Cloudflare 验证` 标题，并保留 `refetchAfterSuccess=false`。
- 两份动态书源副本保持一致；若某个副本为本地忽略文件，测试不得把它作为仓库检出的必要条件。

### Release 验证

- 运行相关 Release 单元测试和完整 Release 单元测试。
- 生成严格递增版本号的签名 Release APK 并验证签名。
- 安装到已连接模拟器并启动应用。
- 重新导入更新后的动态书源，验证正常发现刷新不显示 WebView；真实 Challenge 优先静默通过，必须人工处理时只显示一次，并在通过后自动关闭。

## 非目标

- 不为某个站点在 Kotlin 中添加域名白名单。
- 不取消真实 Cloudflare、登录或站点验证码流程。
- 不改变普通浏览器、书源登录页或非 Cloudflare 验证页的手动关闭行为。
