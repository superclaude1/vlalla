# Legado 参考笔记（阅读器 + TTS）

> 来源：`D:\migrated\legado-master`（gedoor/legado master 快照）
> 用途：章境 Phase 5（阅读器）与 Phase 6（TTS）的实现基准。Legado 为 View 系，章境为 Compose —— **移植算法与结构，不移植渲染代码**。

---

## 一、分页算法（TextChapterLayout.kt）

### 1.1 数据结构（三级实体）

```
TextChapter（章）
 └─ TextPage[]（页）
     ├─ index / chapterIndex / title / isCompleted
     ├─ lines: TextLine[]
     └─ text: 本页拼接文本（用于翻页索引计算）
TextLine（行）
 ├─ text / isTitle / isParagraphEnd
 ├─ paragraphNum（段号，跨页递增）
 ├─ chapterPosition（全局字符偏移 = 上页末行偏移 + charSize + 换行）
 ├─ pagePosition（页内偏移）
 └─ columns: TextColumn[]（字符级列：charData + start/end x 坐标）
```

**核心设计：`chapterPosition` 全局字符偏移是唯一进度锚**。字体/主题改变 → 重新分页 → 用偏移找回位置（章境 0.6.0 已有字符偏移基础，可直接对齐此模型）。

### 1.2 排版流程（逐段填页）

```
foreach 段落 content:
  1. StaticLayout（或自定义 ZhLayout 两端对齐）按 visibleWidth 断行
  2. foreach layout.line:
     - lineStart/lineEnd 截取行文本
     - measureTextSplit()：按字符宽度聚类（CJK 逐字 + 零宽字符 U+200B/200C/200D/2060 处理）
     - 计算 desiredWidth = Σ 字符宽度
     - 行类型分发：
       * 段落首行 → addCharsToLineFirst（段首缩进 paragraphIndent + 两端对齐）
       * 中间行 → addCharsToLineMiddle（两端对齐）
       * 末行 → addCharsToLineNatural（自然排列，不拉伸）
     - 标题（isTitle && isMiddleTitle 等）→ x 轴居中
     - calcTextLinePosition()：更新 paragraphNum / chapterPosition / pagePosition
     - durY += textHeight * lineSpacingExtra
  3. 段间 durY += textHeight * paragraphSpacing / 10
  4. prepareNextPageIfNeed(durY + textHeight)：durY 超出 visibleHeight → onPageCompleted() 发页
```

### 1.3 两端对齐算法（ZhLayout / addCharsToLineMiddle）

```
residualWidth = visibleWidth - desiredWidth
有空格词（英文）: d = residualWidth / 空格数 → 分配到空格（wordSpacing）
纯 CJK:           d = residualWidth / (字符数 - 1) → 每字符间加字距
                  extraLetterSpacing = d / textSize，偏移 -d/2 居中
exceed(): 末尾字符越界 → 全行按比例回缩（边界保护）
```

### 1.4 增量分页与取消

- `Channel<TextPage>(UNLIMITED)`：每完成一页立即 `trySend` —— **UI 可先渲染第一页，后续页陆续到达**（长章不白屏）
- 排版协程在 IO；`currentCoroutineContext().ensureActive()` 逐段检查取消
- 异常/取消/完成都通过 listener 回调（LayoutProgressListener）

### 1.5 → 章境 Compose 移植方案

| Legado 概念 | Compose 对应 |
|---|---|
| StaticLayout / ZhLayout | `TextMeasurer.measure(AnnotatedString, constraints)`，行结果 `TextLayoutResult` 遍历 lineStart/lineEnd |
| 逐字符宽度数组 | `TextLayoutResult.getBoundingBox(offset)` / `getHorizontalPosition`，或自建宽度表 |
| TextPage/TextLine/TextColumn | 数据类照搬（页/行/字符范围），渲染交给 `Text` + `drawText`（Compose 自带两端对齐 `TextAlign.Justify`） |
| Channel 增量发页 | `Flow<TextPage>` 或 StateFlow 列表追加 |
| 两端对齐自定义 | Compose `TextAlign.Justify` 基本够用；精细字距控制用 `AnnotatedString.SpanStyle(letterSpacing)` |
| 缩进 | 段首插入全角空格或 `firstBaselineToTop` + 缩进 Span |

---

## 二、TTS 架构

### 2.1 系统 TTS（help/TTS.kt，145 行）——健壮性范式

```
speak(text):
  - 懒初始化：TextToSpeech == null 时才 new TextToSpeech(appCtx, initListener)
  - 已初始化：按 "\n" 分句 QUEUE_ADD（先 QUEUE_FLUSH 清空旧队列）
  - 任一 speak() 返回 ERROR → clearTts()（stop+shutdown）→ 重建引擎
onInit(SUCCESS) → 挂 UtteranceProgressListener → 补发队列
onDone() → 60 秒空闲后自动 clearTts() 释放资源（防引擎占用）
```

### 2.2 在线 TTS 队列（HttpReadAloudService.kt，617 行）——合成管线范式

**整段模式 downloadAndPlayAudios()**：
1. 遍历段落列表（跳过已朗读的 nowSpeak 之前）
2. **缓存键 = md5(文本)**（`md5SpeakFileName`）—— 内容哈希，跨会话复用
3. 缓存未命中 → `getSpeakStream(httpTts, speakText)` HTTP 合成 → 存文件；**单段失败 → pauseReadAloud()**（不崩、不中断数据，可重试）
4. 空文本 → 静音占位音频（保证播放时序）
5. `exoPlayer.addMediaItem` 逐段入队（合成完一段入队一段）
6. **preDownloadAudios()：预合成下一章前 10 段**（当前章播完无缝接下一章）

**流式模式**：Downloader channel + `CacheDataSource` 包裹 `InputStreamDataSource`（合成即播），`loadingState.debounce(1s)` 控制预取节奏。

**音频缓存**：`SimpleCache(128MB, LRU)` —— 容量淘汰。

**错误策略**：`CustomLoadErrorHandlingPolicy` + `downloadErrorNo/playErrorNo` 计数；不可恢复 → pauseReadAloud。

### 2.3 多引擎配置（data/entities/HttpTTS.kt + HttpTTSDao）

HttpTTS 实体保存多组引擎配置（URL/参数/启停），朗读时选当前引擎 —— **这是降级链的基础模型**：章境可做成「健康检查 + 按优先级切换引擎」。

### 2.4 → 章境 TTS 移植映射

| Legado | 章境 0.6.0 现状 | 动作 |
|---|---|---|
| md5(文本) 文件缓存 | 已有内容哈希缓存（分段） | 升级：键加入音色/语速/情绪参数 + LRU 容量淘汰 |
| 单段失败 pause 可重试 | 单段失败抛错中断 | 改为：失败退避重试 N 次 → 跳过该块，整章继续 |
| 下一章预取 10 段 | 无预取 | 预取窗口 2 块（对齐 6.4 指标） |
| 静音占位 | 无 | 空块占位，保证时序 |
| 懒初始化+ERROR 重建 | init 90s 超时 | 对齐 help/TTS.kt 模式 |
| 128MB LRU | 无容量淘汰 | SimpleCache 或自实现 LRU |
| 多引擎配置 | 单引擎选择 | 引擎列表 + 健康检查 + 自动降级 |

---

## 三、排版参数对照（Legado ReadBookConfig → 章境）

| 参数 | Legado | 章境 0.6.0 | 差距 |
|---|---|---|---|
| 字号 | contentPaint.textSize | ✅ 有 | — |
| 行距 | lineSpacingExtra | ✅ 有 | — |
| 段距 | paragraphSpacing | ❌ 缺 | Phase 5 补 |
| 段首缩进 | paragraphIndent（可配 0-4 字） | ❌ 缺（或固定） | Phase 5 补 |
| 字距 | extraLetterSpacing（两端对齐派生） | ❌ 缺 | Phase 5 补 |
| 页边距 | paddingLeft/Top（ChapterProvider） | ✅ 有 | — |
| 两端对齐 | textFullJustify 开关 | Compose Justify | 对齐默认开 |
| 标题居中 | isMiddleTitle 开关 | 部分 | 对照补 |
| 主题 | 纸张/羊皮纸/夜间 | ✅ 0.6.0 有 | — |

---

## 四、结论：可移植 vs 仅参考

**直接移植（算法/结构）**：
1. 三级分页实体 + chapterPosition 全局偏移锚定
2. 逐段填页流程 + 增量发页 Channel
3. 两端对齐的 residual 分配算法（Compose Justify 兜底，字距精细控制用 letterSpacing）
4. TTS：md5 内容缓存键、单段失败跳过、预取窗口、懒初始化+重建、LRU 缓存、多引擎降级模型

**仅参考（View 系渲染/业务耦合）**：
- ContentTextView 自绘（Compose Text 替代）
- ReadView 手势（Compose 手势体系重写）
- 书源/规则引擎部分（与章境无关）
