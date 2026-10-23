# AI 功能与接口

## 入口与能力

| 入口 | 使用资料 | 能力与边界 |
| --- | --- | --- |
| 主界面 AI 聊天 | 对话和用户启用的本地工具 | 保留书架、阅读记录、书源和设置工具，统一走 Responses 函数调用 |
| 小说阅读菜单 → AI 阅读助手 | 已完成加载的当前章节正文 | 本章解读、人物资料、自由提问；超过 60000 字明确提示只发送前 60000 字 |
| 小说选文 → 询问 AI | 选文与当前章节正文 | 解释选文，使用相同阅读面板和上下文构造 |
| 漫画阅读菜单 → AI 阅读助手 | 当前漫画页，或本章前最多 12 页图像 | 解读对白、画面、人物与情节；显示实际页数和覆盖范围，不把部分页面冒充整章 |
| 人物资料 → 仅分析书内 | 本章正文或当前漫画页 | 生平线索、主要事迹、人物关系；缺失信息明确说明 |
| 人物资料 → 联网检索 | 同样的阅读上下文及 Responses `web_search` | 核对同名人物、区分历史与原著、显示可点击来源；检索内容可能涉及后续剧情 |

阅读助手不提供修改书架、修改设置或执行书源脚本的工具。打开入口时提示阅读内容将发送到配置的服务，只有用户继续后才加载、发送。模型识别或检索仍可能不准确，不能把生成结果等同于原文。

## 唯一接口协议

- 配置保留提供商地址、API Key、自定义请求头、模型，以及普通聊天的系统提示与本地工具开关。
- 所有生成请求使用 `POST /v1/responses`、`input`、`stream: true`、`store: false`，不回退 Chat Completions。
- 支持主机地址、`/v1`、完整 `/responses` 地址；旧完整 `/chat/completions` 地址在请求时归一化为同前缀的 `/responses`，不改写保存的密钥、模型或提供商 ID。
- 模型发现仍使用 `/models`，它仅列出服务返回的模型，不保证每个模型都支持 Responses、图像或 `web_search`；能力不支持时显示服务错误，不伪装成功。
- 原 Tavily、MCP 接口配置及外部 Skill 导入入口移除，旧执行器移除，旧 Skill 不再注入请求。旧持久化字段暂留以兼容历史备份，不参与请求，也不触发后台连接。

## 流式、工具与隐私

- 根据 Responses SSE 帧解析文本、拒绝和公开的推理摘要；只有 `response.completed` 才成功。失败、未完成、断流都会报错。
- 函数工具使用扁平定义，显式 `strict: false` 保留既有可选参数语义。续轮保留完整输出条目以及加密 reasoning，结果用 `function_call_output` 和原 `call_id` 回传。
- 阅读面板使用 Activity 生命周期，关闭、停止、切换会话会取消 HTTP 请求；旧请求不能覆盖新会话。
- 漫画复用通用图片加载器，支持已有缓存、书源鉴权和本地图片。不把书源凭据传给 AI；只发送解码、缩放后的 JPEG data URL。单页上限 1600 × 3200，最多 12 页，总 JPEG 上限 8 MiB。长条漫画缩放后文字可能不清晰，此时可改用当前页并追问。
- 图像数据仅暂存内存，不写聊天历史或请求诊断。诊断不记录正文、图像和鉴权头。`store: false` 不等同于保证服务端完全不留存数据，仍以用户配置服务的隐私政策为准。
- 联网引用只接受 HTTP(S) URL，作为明确可点击的来源列表显示。
- 人物联网模式要求使用工具；如果兼容服务仍未返回 `web_search_call` 记录，答案会明确标记未经联网核验，不冒充检索成功。

## 验证与官方依据

- `TestAiResponsesProtocol`：端点兼容、请求形状、图片、工具续轮、SSE、引用、拒绝、断流、取消等。
- `TestAiResponsesInstrumented`：本地 HTTP 模拟服务、真实客户端工具续轮和取消、阅读上下文、漫画图像加载、设置与菜单。测试保存并恢复 AI 配置，不调用真实模型。
- 官方资料：[Responses 迁移](https://developers.openai.com/api/docs/guides/migrate-to-responses)、[流式响应](https://developers.openai.com/api/docs/guides/streaming-responses)、[函数调用](https://developers.openai.com/api/docs/guides/function-calling)、[联网搜索](https://developers.openai.com/api/docs/guides/tools-web-search)、[图像输入](https://developers.openai.com/api/docs/guides/images-vision)。
