# Stage 6 接力状态：集成与剩余应用工作流

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/integration`

总 PR：Draft PR #9 `北北计算器：Stage 6 集成与剩余应用工作流`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已按用户授权合并；0.3.17 / 0.3.18 真机检查继续按用户要求延后集中进行）

## 当前状态

Stage 6 的代码开发、结构化工作流补齐、Android 交互补齐、真机前静态审计和云端构建/长期签名门禁已经完成。后续仍只从 `stage6/integration` 继续，不再回到旧的 Stage 6 并行分支。

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

### F. 真机前静态审计修复

静态审计发现两处 CI 原先无法暴露、但真机交互会遇到的问题，均已修复并补回归：

- WorkflowAction 原 35dp 操作区在 4–6 个动作时会拆成两排，按钮高度约 16.5dp；现改为 42dp 高单排布局，6 个动作仍保持独立命中，避免真机按钮过小。
- 多项式升/降阶原来按“行位置”保留数据，导致 `a2` 升阶后可能被误当成 `a3`；现按幂次语义保持，升阶只新增空白最高阶系数，降阶只移除最高阶系数。
- 联立方程增/减元数原来会让旧 RHS 常数列落到新变量列；现显式保持 `x1..xn` 系数与最右增强列 `b` 的语义位置。
- 上述语义保持同时覆盖触摸 WorkflowAction 和既有 `SHIFT + 方向` 尺寸快捷方式，因为两条路径最终共用同一 core action。

### G. 本轮明确不扩展

- Complex / Base-N 暂不混入这一轮结构化表单工作流；
- 未借 Stage 6 重写既有数学算法。

## 云端验证

### Stage 6 完成门禁

GitHub Actions run `35188434866`：`success`。

- 完整 `:core:check`；
- Android `Cn991Debug`；
- Android `Cn991Release` + R8；
- package `com.beibei.calculator`；
- versionName `0.3.18`；
- versionCode `332`；
- certificate SHA-256 `DA6901B21ED9CCD8E33F4BFA6D2726183913F4E6C13910705E210184A17A5D33`；
- 生成 `stage6-stable-signed-apk`，artifact ID `10482769251`。

### 真机前审计修复门禁

GitHub Actions run `35197523499`：`success`。

- 新增尺寸语义保持回归并通过完整 `:core:check`；
- Android Debug 构建通过；
- Android Release + R8 通过；
- 长期证书与 `com.beibei.calculator` 包名再次硬校验通过；
- 生成最新长期签名测试 APK：`stage6-audit-signed-apk`，artifact ID `10486064308`；
- 正式修复 commit：`7d44faf1eecf8d8ab65e864ca838a365c38ae732`。

两轮门禁都只验证并生成 artifact，没有创建 GitHub Release；对应临时 workflow / patch 均已删除。

## 唯一继续点

- branch：`stage6/integration`
- PR：Draft PR #9
- Stage 6 代码开发项：已完成
- 真机前静态审计：已完成
- 云端 core / Debug / Release / R8 / 长期签名门禁：已完成
- 最新推荐测试包：artifact `10486064308`
- 集中真机验收清单：`docs/hand-offs/STAGE6_DEVICE_ACCEPTANCE.md`
- 下一项：按验收清单集中进行真机测试（包含此前延后的 0.3.17 / 0.3.18 检查，以及 Stage 6 新增交互）
- 真机验收完成且用户明确授权之前，不合并 PR #9 到 `main`
- 禁止从 `main`、`stage6/remaining-apps` 或旧并行分支重新创建 Stage 6 工作线；中断后先核对本 relay 与当前 branch head。

## 真机验收最高优先级

- 覆盖安装/长期签名链；
- WorkflowAction 单排按钮的可读性、命中与 disabled 状态；
- 多项式 `a2/a1/a0` 升降阶后语义不变；
- 联立方程增减元数时 RHS 始终留在最右 `b` 列；
- 函数表单/双函数输入、TABLE 分页与 Page rocker；
- 二/三/四次不等式关系选择和系数输入；
- 两种比例表单以及旧逗号输入回退；
- Ans、历史、复制粘贴、错误恢复、异步 EXE revision 门禁；
- 长数字、分数、SCI/ENG 与自然显示；
- 长按连续输入/删除和语义光标/选区。

## 兼容边界

- 不重写既有数学算法；
- 旧逗号字符串输入继续作为回退；
- Stage 3 语义编辑、Stage 4 结构化结果、Stage 5 结构化输入不得回归；
- Ans、历史、复制粘贴、错误恢复和异步 input revision 门禁不得回归；
- 不改变 `com.beibei.calculator` 与长期 Release 签名链；
- Stage 6 总 PR 保持 Draft，只有用户明确要求后才可合并。
