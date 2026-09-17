# Stage 6 接力状态：集成与剩余应用工作流

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/integration`

总 PR：Draft PR #9 `北北计算器：Stage 6 集成与剩余应用工作流`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已按用户授权合并；0.3.17 / 0.3.18 真机检查继续按用户要求集中进行）

## 当前状态

Stage 6 的代码开发、结构化工作流补齐、Android 交互补齐、真机前静态审计和云端构建/长期签名门禁已经完成。2026-09-17 已开始 MuMu 模拟器集中验收：WorkflowAction 基础触控、多项式升降阶语义、联立方程增减元数 RHS 语义、函数表逐行/Page 导航均已有通过证据；函数表 TABLE 结果页曾泄露内部输入串，已修复并于 artifact `10495340801` 上完成覆盖安装复验，确认顶部内部串/编辑光标消失且逐行/Page 翻页无回归。

函数表复验通过后又新增 `CnCwStage6AcceptanceSuite`，直接从 HOME/按键路径验证不等式与比例的 Stage 6 交互契约，并已挂入 `:core:check`。正式 PR CI run `35220348788` 已通过完整核心回归、Android Debug 构建和 artifact 上传。

后续仍只从 `stage6/integration` 继续，不再回到旧的 Stage 6 并行分支。

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
- 显示当前行范围与滚动条；分页状态仅属于 Android View，不污染数学状态；
- MuMu 初验确认 21 行表格的逐行、PageDown、PageUp 和列对齐正确；
- TABLE 结果页内部编辑串/光标泄露已修复并完成复验：未卸载 `com.beibei.calculator`，artifact `10495340801` 通过 `adb install -r` 覆盖成功，首次安装时间保持不变；`f(x)=x`、范围 `0–20`、步长 `1` 正常；顶部不再出现 `x,0,20,1` 和编辑光标；↓：`1–4/21` → `2–5/21`，PageDown 到 `6–9/21`，PageUp 回到 `2–5/21`，无错位或分页回归。
- 多项式和联立方程此前的尺寸语义保持验收继续有效，无需再次重复测试。

### D. 二/三/四次不等式

- 关系字段升级为结构化 `CHOICE`；
- UI 显示 `>`、`<`、`≥`、`≤`，内部继续兼容旧关系码 `1..4`；
- 二/三/四次分别提供 3 / 4 / 5 个系数输入；
- 方向键循环关系选项，Android 不再要求用户输入关系数字码；
- 求解仍复用原 `PolynomialEngine`，未重写数学算法；
- 新 acceptance regression 自动验证：三个次数命令的字段数量与 choice 关系；普通数字不能覆盖关系格；左右循环与 DEL 恢复默认关系；空白输入定位第一系数；非法表达式停留并定位错误格；完整二次不等式计算后 BACK 返回输入且系数保留。

### E. 比例

- `A:B=X:D` 使用 A / B / D 标签化固定字段；
- `A:B=C:X` 使用 A / B / C 标签化固定字段；
- 比例结果升级为 core-owned `KEY_VALUE`，Android 无需解析 `X=...` 文本；
- 保留旧逗号输入兼容：在首字段直接输入逗号可回退到原 `A,B,D` / `A,B,C` 路径；
- 求解仍复用原 `RatioEngine`；
- 新 acceptance regression 自动验证两种结构化比例结果、KEY_VALUE 协议、BACK 后字段保留，以及首字段逗号回退旧输入并继续正确求解。

### F. 真机前静态审计修复

静态审计发现两类 CI 原先无法暴露、但实际交互会遇到的问题，均已修复并补回归：

- WorkflowAction 原 35dp 操作区在 4–6 个动作时会拆成两排，按钮高度约 16.5dp；现改为 42dp 高单排布局，6 个动作仍保持独立命中，避免按钮过小。
- 多项式升/降阶原来按“行位置”保留数据，导致 `a2` 升阶后可能被误当成 `a3`；现按幂次语义保持，升阶只新增空白最高阶系数，降阶只移除最高阶系数。
- 联立方程增/减元数原来会让旧 RHS 常数列落到新变量列；现显式保持 `x1..xn` 系数与最右增强列 `b` 的语义位置。
- 上述语义保持同时覆盖触摸 WorkflowAction 和既有 `SHIFT + 方向` 尺寸快捷方式，因为两条路径最终共用同一 core action。

### G. 模拟器验收后追加修复：TABLE 结果页状态隔离

MuMu 初验用 `f(x)=x`、`0..20`、步长 `1` 生成 21 行函数表时，表格与分页均正确，但结果页顶部仍显示内部串 `x,0,20,1` 和编辑光标。

根因在 Android `CalculatorView.drawApplicationScreen(...)`：结构化 TABLE 结果已经存在时，仍先执行 natural expression / selection 绘制，再绘制表格。现已增加 `tableResultShown` 状态守卫：

- TABLE 结果存在时，不再绘制编辑表达式、编辑光标或选择手柄；
- TABLE 继续正常走 core-owned `drawStructuredApplicationResult(...)`；
- KEY_VALUE / MATRIX / VECTOR 等其它结构化结果不受此专门守卫影响，保持原有显示行为；
- 未修改函数表数学计算、表格数据或分页状态。

严格验证 run `35215973055` 已通过源码状态守卫、完整 core 回归、Debug、Release + R8、长期签名和包名硬校验，并生成 `stage6-table-result-fix-signed-apk`，artifact ID `10495340801`。

2026-09-17 复验结果：

- 未卸载 `com.beibei.calculator`；
- artifact `10495340801` 使用 `adb install -r` 覆盖安装成功，首次安装时间保持不变，确认属于同包升级；
- `f(x)=x`、范围 `0–20`、步长 `1` 正常；
- 顶部不再出现 `x,0,20,1` 和编辑光标；
- ↓：`1–4/21` → `2–5/21`；PageDown 到 `6–9/21`；PageUp 回到 `2–5/21`；
- 表格无错位或分页回归。

该问题状态现为：**代码修复、云端门禁、覆盖安装与 MuMu 复验全部通过。**

### H. 本轮明确不扩展

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
- 生成长期签名测试 APK：`stage6-audit-signed-apk`，artifact ID `10486064308`；
- 正式修复 commit：`7d44faf1eecf8d8ab65e864ca838a365c38ae732`。

### 函数表结果页显示修复门禁

GitHub Actions run `35215973055`：`success`。

- TABLE 显示状态源码守卫通过；
- 完整 `:core:check` 通过；
- Android Debug 通过；
- Android Release + R8 通过；
- 长期证书和 package `com.beibei.calculator` 硬校验通过；
- 生成 `stage6-table-result-fix-signed-apk`，artifact ID `10495340801`，artifact SHA-256 `bda1568cbd141de2efed82a34083ad9253f571e857859e8f8a310c7c987fa654`；
- 正式产品修复已提交至 `stage6/integration`；
- 临时验证 workflow 已删除；未创建 GitHub Release；
- 2026-09-17 MuMu 复验与 `adb install -r` 同包覆盖验证均通过。

### Stage 6 acceptance regression 门禁

GitHub Actions run `35220348788`：`success`。

- 新增 `CnCwStage6AcceptanceSuite` 并挂入 `:core:check`；
- 从 HOME/真实按键入口覆盖不等式命令、choice 循环/DEL、空白/非法输入定位、结果返回输入；
- 覆盖两种结构化比例、KEY_VALUE 结果、BACK 保留字段和首字段逗号旧桥接；
- 覆盖双函数 workflow 的 TABLE 结果协议与 BACK 输入保留；
- 完整核心回归通过；
- Android Debug APK 构建和 artifact 上传通过。

## 2026-09-17 MuMu 集中验收状态

测试基线：MuMu，显示覆盖尺寸 `1260 x 2800`；初验包 artifact `10486064308`，函数表复验包 artifact `10495340801`；包名 `com.beibei.calculator`。

已确认：

- WorkflowAction 四按钮基本触控与“提高/降低阶数”命中正常；
- 多项式二次 → 三次 → 二次时系数按幂次保留，无串位；
- 联立方程二元 → 三元 → 二元时新变量列为空，RHS `b` 始终在最右侧；
- 函数表逐行 / PageDown / PageUp 和 `x / f(x)` 列对齐正常；
- TABLE 顶部内部串/编辑光标问题已通过修复包复验；
- artifact `10495340801` 对现有 `com.beibei.calculator` 使用 `adb install -r` 成功，首次安装时间保持不变，确认同包覆盖升级链在该模拟器实例上可用。

无需再次测试：

- 多项式调阶数据语义；
- 联立方程增减元数 RHS 语义；
- 函数表 TABLE 状态隔离、逐行/Page 翻页与列对齐。

已有自动化门禁、人工仅需看 UI/触控表现：

- 不等式 CHOICE 逻辑、DEL 默认值、空白/非法输入定位、计算后 BACK 数据保留；
- 两种比例求值、结构化 KEY_VALUE、BACK 数据保留和旧逗号桥；
- 双函数 workflow 数据协议和 BACK 保留。

仍待人工确认：

- 不等式/比例/双函数的 Android 标签、布局、触控命中和视觉结果；
- 统计/矩阵/向量 WorkflowAction 的边界 disabled 与 6 按钮触控；
- Ans、历史、复制粘贴、自然显示、语义光标/选区、DEL/AC、长按、SCI/ENG、长数字；
- 真机触感、快速滑动、多指行为（模拟器结论不能替代真机结论）。

详细验收清单见 `docs/hand-offs/STAGE6_DEVICE_ACCEPTANCE.md`。

## 唯一继续点

- branch：`stage6/integration`
- PR：Draft PR #9
- 当前 branch head：以 PR #9 最新 head 为准；中断后先核对，不从旧提交重开
- Stage 6 代码开发项：已完成
- 真机前静态审计：已完成
- 云端 core / Debug / Release / R8 / 长期签名门禁：已完成
- MuMu 集中验收：进行中；多项式、联立、函数表和同包覆盖升级已通过
- Stage 6 acceptance regression：已完成并进入永久 `:core:check`
- 当前可继续使用复验包：artifact `10495340801`
- 集中验收清单：`docs/hand-offs/STAGE6_DEVICE_ACCEPTANCE.md`
- 下一项：不再重复多项式、联立和函数表；人工继续不等式/比例/双函数 UI、WorkflowAction 边界、旧功能回归与真机交互测试
- 所有集中验收完成且用户明确授权之前，不合并 PR #9 到 `main`
- 禁止从 `main`、`stage6/remaining-apps` 或旧并行分支重新创建 Stage 6 工作线；中断后先核对本 relay 与当前 branch head。

## 兼容边界

- 不重写既有数学算法；
- 旧逗号字符串输入继续作为回退；
- Stage 3 语义编辑、Stage 4 结构化结果、Stage 5 结构化输入不得回归；
- Ans、历史、复制粘贴、错误恢复和异步 input revision 门禁不得回归；
- 不改变 `com.beibei.calculator` 与长期 Release 签名链；
- Stage 6 总 PR 保持 Draft，只有用户明确要求后才可合并。
