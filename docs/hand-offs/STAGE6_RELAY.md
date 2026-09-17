# Stage 6 接力状态：集成与剩余应用工作流

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/integration`

总 PR：Draft PR #9 `北北计算器：Stage 6 集成与剩余应用工作流`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已按用户授权合并；0.3.17 / 0.3.18 真机检查继续按用户要求延后集中进行）

## 当前状态

Stage 6 的代码开发、结构化工作流补齐、Android 交互补齐和云端构建/长期签名门禁已经完成。后续仍只从 `stage6/integration` 继续，不再回到旧的 Stage 6 并行分支。

Stage 6 曾并行推进三条工作流，现已统一收敛：

1. `stage6/calculation-session`：typed 计算会话 / outcome 状态协议；
2. `stage6/workflow-experience`：WorkflowAction、输入校验与工作流体验；
3. `stage6/remaining-workflows`：函数表、不等式、比例等剩余结构化应用。

PR #6 / #7 / #8 仅保留为历史记录，不应再分别合并。PR #10 的有效函数表增量已精确收敛到本分支，其独立 relay 不再作为继续依据。所有本轮一次性 patch / 临时验证 workflow 均已清理。

## 已完成能力

### A. typed 计算状态

- 引入 typed `EDITING / RESULT / ERROR` 会话状态与结果/错误 payload；
- `CnCwUiState` 发布 typed snapshot，同时保留旧 getter 兼容；
- 成功结果、错误、Ans、历史和异步 evaluation snapshot 接入统一状态链；
- 数值算法、自然显示和既有按键行为保持兼容。

### B. 统一结构化工作流

- core-owned `WorkflowAction` 与输入校验；
- 可定位到具体输入格的错误状态；
- 结构化工作流统一复用 `WorkflowSpec / CnCwWorkflowSession`；
- 显式尺寸/阶数动作与既有快捷键并存；
- 结果返回输入时保留工作流数据和焦点；
- `CnCwMachine.performWorkflowAction(...)` 提供统一执行入口，Android 不再自行推断工作流规则；
- Android 已加入由 core actions 驱动的统一操作条与触摸命中，disabled 状态同样由 core 控制。

### C. 函数表

- 单函数 / 双函数固定字段输入接入统一 workflow session；
- 单函数真实命令 id `f` 与既有 `single` 保持兼容；
- 结果升级为真正的 core-owned `TABLE` payload，继续复用既有 `FunctionTableEngine`；
- Android 已实现真实表格显示；
- `↑/↓` 逐行滚动，`Page↑/Page↓` 整页翻页；
- 显示当前行范围与滚动条；分页状态仅属于 Android View，不污染数学状态。

### D. 二/三/四次不等式

- 关系字段升级为结构化 `CHOICE`；
- UI 显示 `>`、`<`、`≥`、`≤`，内部继续兼容旧关系码 `1..4`；
- 二/三/四次分别提供 3 / 4 / 5 个系数输入；
- 方向键循环关系选项，Android 不再要求用户输入关系数字码；
- 求解仍复用原 `PolynomialEngine`，未重写数学算法。

### E. 比例

- `A:B=X:D` 使用 A / B / D 标签化固定字段；
- `A:B=C:X` 使用 A / B / C 标签化固定字段；
- 比例结果升级为 core-owned `KEY_VALUE`，Android 无需解析 `X=...` 文本；
- 保留旧逗号输入兼容：在首字段直接输入逗号可回退到原 `A,B,D` / `A,B,C` 路径；
- 求解仍复用原 `RatioEngine`。

### F. 本轮明确不扩展

- Complex / Base-N 暂不混入这一轮结构化表单工作流；
- 未借 Stage 6 重写既有数学算法。

## 最终验证

最终只验证、不发布的门禁：GitHub Actions run `35188434866`，结果 `success`。

通过内容：

- 长期 Release 签名材料成功恢复；
- 完整 `:core:check` 通过；
- Android `Cn991Debug` 构建通过；
- Android `Cn991Release` 构建通过；
- R8 压缩通过，未出现 `Invalid stack map table`；
- Release package：`com.beibei.calculator`；
- Release versionName：`0.3.18`；
- Release versionCode：`332`；
- Release certificate SHA-256：`DA6901B21ED9CCD8E33F4BFA6D2726183913F4E6C13910705E210184A17A5D33`，与长期证书一致；
- 最终回归中包括 `197` 条 CN CW machine checks、`115` 条 workflow-spec/session checks，另有 calculation-state、semantic、validation、manual utility、general regression 等套件全部通过；
- 已生成长期签名测试 APK artifact：`stage6-stable-signed-apk`，artifact ID `10482769251`，来源 run `35188434866`。

该门禁没有创建 GitHub Release；临时最终门禁 workflow 已在验证结束后删除。

## 唯一继续点

- branch：`stage6/integration`
- PR：Draft PR #9
- Stage 6 代码开发项：已完成
- 云端 core / Debug / Release / R8 / 长期签名门禁：已完成
- 下一项：按用户之前的安排，集中进行真机验收（包含此前延后的 0.3.17 / 0.3.18 检查，以及 Stage 6 新增交互）
- 真机验收完成且用户明确授权之前，不合并 PR #9 到 `main`
- 禁止从 `main`、`stage6/remaining-apps` 或旧并行分支重新创建 Stage 6 工作线；中断后先核对本 relay 与当前 branch head。

## 真机验收重点

集中验收时优先检查：

- 函数表单/双函数输入、TABLE 分页与 Page rocker；
- 二/三/四次不等式关系选择和系数输入；
- 两种比例表单以及旧逗号输入回退；
- WorkflowAction 操作条触摸范围、disabled 状态和计算/返回；
- Ans、历史、复制粘贴、错误恢复、异步 EXE revision 门禁；
- 长数字、分数、SCI/ENG 与自然显示；
- 长按连续输入/删除和语义光标/选区；
- 与既有 `com.beibei.calculator` 安装包的覆盖安装/升级链。

## 兼容边界

- 不重写既有数学算法；
- 旧逗号字符串输入继续作为回退；
- Stage 3 语义编辑、Stage 4 结构化结果、Stage 5 结构化输入不得回归；
- Ans、历史、复制粘贴、错误恢复和异步 input revision 门禁不得回归；
- 不改变 `com.beibei.calculator` 与长期 Release 签名链；
- Stage 6 总 PR 保持 Draft，只有用户明确要求后才可合并。
