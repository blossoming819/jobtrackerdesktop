# ApplyMate 0.1 实施清单

## 已确认的产品边界

- 复用 JobTrackerDesktop 作为唯一的本地数据宿主；不创建第二套 Resume 或 JobApplication 主数据。
- 浏览器扩展只在用户主动操作后扫描和填写；不自动搜索岗位、绕过验证或提交申请。
- 填写必须经过 `Fill Plan` 人工确认；低置信度、敏感、主观或冲突字段不得静默填写。
- 扩展默认不保存完整 Candidate Profile；它按字段向本地桌面服务读取最小必要数据。

## 决策记录

### D-01：简历解析的视觉能力

0.1 采用“文本优先、视觉按需”的两级策略：

1. 默认使用 Apache Tika 提取 PDF/DOCX 文本，并进行结构化解析。
2. 当文本质量检查失败（扫描件、乱码、双栏阅读顺序明显异常、表格丢失）时，标记为 `VISION_RECOMMENDED`，由用户选择继续使用视觉解析。
3. 后端预留统一 `ResumeParserProvider` 接口，并由 `LlmProvider` 适配层承接不同厂商（例如 OpenAI、Qwen、DeepSeek）；第一版不把视觉模型设为强制依赖。

原因：常规可提取文本的简历不应为视觉调用增加成本、延迟或隐私暴露；但完全不保留视觉路径会明显降低扫描 PDF 和复杂排版简历的可用性。

### D-01a：多厂商 LLM Provider

业务模块不得直接依赖某个厂商 SDK 或模型名。所有模型配置由桌面端设置页面管理，并在本机保存；API Key 不进入 contracts、日志、浏览器扩展或数据库快照。

```text
ResumeParseService / LowConfidenceMatchService
                    ↓
             LlmProvider interface
                    ↓
 OpenAIProvider / QwenProvider / DeepSeekProvider / MockProvider
```

Provider 能力由配置明确声明：`structuredOutput`、`visionInput`、`jsonSchema`、`streaming`。简历解析默认只要求 `structuredOutput`；只有用户选择视觉兜底且当前 Provider 声明 `visionInput=true` 时，才允许发送页面图像。若不支持视觉，则返回可解释的 `VISION_UNAVAILABLE`，而非临时换用其他厂商或静默降级。

### D-02：扩展发布与本地服务可用性

开发期使用 Chrome/Edge 的“加载已解压扩展”。发布期准备 Chrome Web Store 包，但不依赖商店安装完成后才验证核心功能。

扩展通过 `http://127.0.0.1:<port>` 与桌面端通信，必须同时满足：

- 扩展 manifest 声明精确的 localhost `host_permissions`；
- 桌面服务只绑定 loopback 地址；
- 首次配对由用户在桌面端确认，生成可撤销的扩展会话 token；
- 服务校验 token、Origin 和已配对的 Extension ID；生产版 ID 固定后写入允许列表，开发版单独配对；
- API 不允许通配 CORS，也不向任意网页暴露档案数据。

桌面端未启动时的降级规则：

| 能力 | 是否可用 | 行为 |
| --- | --- | --- |
| 扫描当前页面 | 可用 | 仅在页面内生成 FieldContext，不读取用户档案 |
| 查看/编辑 Candidate Profile | 不可用 | 提示启动 JobTrackerDesktop |
| 生成基于档案的 Fill Plan | 不可用 | 不使用扩展缓存冒充正式档案 |
| 执行已确认的自动填写 | 不可用 | 防止过期或未经授权的数据填写 |

## 实施主线

| 阶段 | 目的 | 主要产物 | 完成判定 |
| --- | --- | --- | --- |
| A. 契约基线 | 固化跨端数据语言 | `contracts/` Schema、Field Registry、DTO | Schema 与示例档案能被校验 |
| B. Profile 基座 | 建立唯一的长期求职档案 | Profile CRUD、快照、前端入口 | 手工维护档案且可查看历史版本 |
| C. 简历解析 | 把现有 Resume 转为待确认档案 | Tika、解析记录、Draft、Diff、Provider 接口 | 解析不会静默覆盖已确认字段 |
| D. 安全连接 | 让扩展受控访问桌面端 | 配对、token、最小字段 API、严格 CORS | 未配对扩展和网页无法读取档案 |
| E. 页面理解 | 统一表示招聘页面字段 | WXT 扩展、Scanner、FieldContext | 能扫描原生控件并展示上下文 |
| F. 填写决策 | 先解释再填写 | Matcher、RecordResolver、Transformer、Fill Plan | 用户能确认每一项的值、来源与置信度 |
| G. 写入与验证 | 可靠地辅助填写 | Generic Filler、Verifier、错误码 | 原生控件填写后可回读验证 |
| H. 投递闭环 | 连接填写前后工作流 | Application Draft、Fill Session 摘要 | 用户确认后生成现有 JobApplication 记录 |

## 推荐提交顺序

1. **提交 1：A 阶段** — 只新增 `contracts/`、示例和校验测试，不改运行逻辑。
2. **提交 2：B 阶段** — 后端 Profile/Snapshot 与前端档案页面，保持 Resume/JobApplication 不变。
3. **提交 3：C 阶段** — 简历文本提取、解析草稿和差异确认；云端 Provider 可配置、默认关闭。
4. **提交 4：D 阶段** — 本地握手与最小字段 API；先替换现有宽松的 `/api/**` CORS 策略，再接扩展。
5. **提交 5：E 阶段** — WXT、离线扫描和本地 HTML fixture 测试。
6. **提交 6：F/G 阶段** — Fill Plan、人工确认、填写和回读验证。
7. **提交 7：H 阶段** — 用户确认生成投递草稿；仍不自动提交招聘网站。

## 每阶段验收问题

- A：前端、扩展、后端是否对同一字段 key 与枚举达成一致？
- B：Candidate Profile 是否是唯一跨公司个人事实源，且可以回滚？
- C：解析结果是否始终先让用户确认？视觉解析是否只在必要时发送？
- D：任意普通网页能否访问 `127.0.0.1` API？答案必须是否定的。
- E：桌面端关闭时，扩展是否只扫描而不读取或填写个人数据？
- F：每个计划项是否有值来源、匹配原因、置信度和填写策略？
- G：网页重渲染或拒绝写入时，是否明确报告失败而非假装成功？
- H：是否仍由用户本人点击最终“提交申请”？
