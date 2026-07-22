# 章境 Android

“章境”是一款面向长篇小说的原生 Android 应用。它把 TXT 小说转换成可阅读、可对话、可检索、可视化并可配音的故事世界。当前版本为 `0.4.1 (10)`。

## 主要功能

- 流式导入 TXT，自动识别 UTF-8、UTF-16、GB18030，支持 15 MB 以上文件。
- 自动识别中英文卷、章标题；无标题文本按语句边界自动分章。
- 使用 OpenAI-compatible API 分析人物、别名、关系、剧情节点、地点与角色重要度。
- 首次默认分析 15 章，之后可自定义章数增量分析，并显示全书分析进度。
- 聊天式阅读：角色对白靠左、短旁白居中，角色真名与别名会统一映射。
- 角色多会话对话，支持新建、自动命名、重命名、清空与删除。
- 角色记忆库支持剧情、关系、原文、笔记和已确认对话；带本地中文全文检索、筛选与推荐。
- 严格限制记忆与聊天历史预算，并在界面和服务层共同阻止未分析章节剧透。
- 剧情链、角色关系、保护关系、地点迁移图，以及 Neo4j Cypher 导出。
- Room 本地保存书架、阅读进度、分析、记忆、会话、配音设置与演绎脚本。
- 删除小说时同步清理图谱、会话、记忆索引和本地音频。
- Material 3 界面、深色模式、自定义封面、启动图标与书架空状态。

## 多平台小说配音

章境可按章节生成有声小说，并分别为旁白、男性角色、女性角色和通用角色选择音色。

- `Edge TTS`：Android WebSocket 直连，无需 API Key 或电脑中转服务。
- `Fish Audio`：支持服务检测、模型选择、同步“我的音色”、搜索公开音色并加入角色音色池。
- `OpenAI-compatible TTS`：支持自定义 Base URL、模型、音色 ID，以及可选的 `instructions` 演绎参数。
- 支持全局主力引擎、单本小说引擎、旁白绑定和角色独立音色绑定。
- 配音前由 LLM 生成情绪、语气、停顿、语速和音量标注；LLM 不可用时自动采用本地规则。
- 演绎脚本和音频按内容哈希缓存；临时目录生成成功后再原子替换旧配音，失败时保留原结果。
- 对可重试的限流和服务端错误进行退避重试，并保留每段状态和错误信息。

Fish Audio 的公开可见音色不等于自动获得发布或商业使用权，请自行确认相应授权。

## 服务设置

进入底部“设置”页面：

1. 在“LLM 分析服务”中填写 OpenAI-compatible Base URL 和 API Key，检测并选择模型后保存。
2. 在“小说配音服务”中选择 Edge TTS、Fish Audio 或 OpenAI-compatible TTS。
3. 检测服务并保存平台配置；Fish Audio 可同步/搜索音色，兼容 TTS 可手动加入音色 ID。
4. 在小说详情的角色区域为旁白或角色绑定音色，然后进入阅读页生成章节配音。

LLM 与 TTS API Key 使用 Android Keystore 的 AES-GCM 密钥加密，仅保存在设备本地。

## 构建与测试

环境要求：JDK 17、Android SDK 34。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

连接 Android 设备或启动模拟器后可运行完整设备测试：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。
