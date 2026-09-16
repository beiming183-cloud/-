# Stage 3 接力状态：语义编辑器

更新日期：2026-09-16

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage3/semantic-editor`

基线：`main@decb643`（Stage 2 PR #2 已合并）

## 目标

Stage 3 把编辑位置从单一 token 下标升级为真正的语义位置，使分数、幂、根号和函数内部可以按数学结构移动、选择、删除和触摸命中。旧 `int cursor` / token boundary 暂时保留为兼容回退，Stage 2 已验收的 AC、Ans、历史、复制粘贴和长按重复行为不得回归。

## 已完成

### Step 1：语义位置模型

- `CnCwCursorPath`、`semanticCursor()` 与 legacy cursor 兼容层完成；
- semantic cursor suite 纳入 `:core:check`。

### Step 2：分数语义编辑

- `FRACTION_NUMERATOR` / `FRACTION_DENOMINATOR`；
- 上下切换、左右进入/退出、结构安全 DEL、空槽和替换完成。

### Step 3：幂指数语义编辑

- `SUPERSCRIPT_BASE` / `SUPERSCRIPT_EXPONENT`；
- 结构移动、上下切换、DEL、空槽和整体/精细选择兼容完成。

### Step 4：根号 / n 次根语义编辑

- `RADICAL_CONTENT` / `ROOT_INDEX` / `ROOT_CONTENT`；
- 左右/上下结构移动、DEL、空槽、精细替换和求值回归完成。

### Step 5：函数参数语义编辑

- 普通函数发布 `FUNCTION_ARGUMENT`；
- `childPath=[函数模板 token 位置, 参数序号]` 区分多参数；
- 单/多参数移动、结构安全 DEL、空参数和求值回归完成；
- 裸 `sin(` 单次 DEL 删除完整函数 token 的 Stage 2 行为保留。

### Step 6：统一语义选区、替换和删除

- `CnCwUiState` 发布 `semanticSelectionAnchor()` / `semanticSelectionFocus()`；
- 分数、幂、根号、n 次根、函数参数支持语义精细选区；
- `sin(30)` 可只选参数；多参数函数跨结构逗号会吸附成完整函数；
- 选区 DEL、按键替换、粘贴替换后保持正确 semantic slot；
- legacy `selectionStart()` / `selectionEnd()` 保留供兼容。

### Step 7：Android 语义命中与渲染迁移

代码已完成，等待真机验收：

- 新增 `CnCwSemanticSpan`，核心发布每个可编辑槽的 `childPath`、slot、槽边界和所属结构边界；
- `CnCwUiState` 发布 `semanticSpans()`；
- `CnCwMachine` 新增 `moveCursorTo(CnCwCursorPath)` 及 touch-selection 语义路径重载；无效/过期路径回退 legacy boundary；
- `CalculatorView` 的点击、水平滑动、长按起点、选区抓手拖动使用 semantic path；
- Android 命中同时参考 x/y，可区分分数上下槽、幂指数、n 次根指数；无法可靠命中时保留 Stage 2 x-only 回退；
- 选区抓手位置使用 semantic selection path 反投影；
- semantic cursor suite 共 243 checks。

## 长期正式安装链

0.3.14 暴露出 Debug 签名依赖 Actions cache 的问题。该链已经停止作为正式分发方案。

从 0.3.15 起建立新的长期 Release 身份：

- 对外名称：`北北计算器`；
- Application ID：`com.beibei.calculator`；
- 当前版本：`0.3.16`；
- 当前 versionCode：`330`；
- Release certificate SHA-256：`DA6901B21ED9CCD8E33F4BFA6D2726183913F4E6C13910705E210184A17A5D33`；
- Release key 只从 GitHub Actions Secret 恢复，不再使用 cache 保存正式签名；
- 正式发布前硬校验 tag、APK versionName、versionCode、Application ID 和证书指纹；已有 tag 不允许覆盖；
- Release 构建若出现 R8 `Invalid stack map table`，直接失败并禁止发布。

0.3.15 是长期安装链起点；0.3.16 使用同一 Application ID 与同一 Release key 发布，可直接覆盖 0.3.15。0.3.16 是当前 Stage 3 真机验收基线。

## R8 双变量统计风险处理

0.3.15 首次正式构建曾对 `StatisticsEngine$TwoVariableResults` 报 R8 `Invalid stack map table` warning。统计公式本身没有报错；问题来自包含大量 `double` 分量的 Java 17 record 生成字节码。

已完成根因修复：

- `TwoVariableResults` 改为普通 `public static final` 不可变类；
- 构造参数顺序和 `n()`、`sumX()`、`meanX()`、`sampleVarianceX()` 等访问接口保持不变；
- 不修改双变量统计公式与上层调用协议；
- 临时 Release+R8 检查在保留 ProGuard keep 规则时通过；
- 随后删除 keep 规则恢复最小 ProGuard 配置，再次通过 Release+R8 检查；
- warning 的消失来自代码结构修复，而非 suppress/keep 绕过；
- 一次性 R8 检查 workflow 已删除；
- 长期发布 workflow 已加入同类 warning 的 fail-closed 门禁；
- 0.3.16 正式 Release 已在该修复和门禁下成功构建、身份校验并发布。

## Stage 3 真机验收

统一使用 **0.3.16 Release**，不再使用 0.3.14 Debug 或 0.3.15 作为最终 Stage 3 基线。

1. 普通表达式：点击移动、水平滑动、左右抓手不能比 Stage 2 退化；
2. 分数：点击分子/分母能进入对应槽，↑/↓ 正常，DEL 不拆结构；
3. 幂：底数/指数触摸可区分；
4. 根号 / n 次根：被开方数与根指数命中符合视觉位置；
5. 函数：`sin(30)` 参数可精细选择；`sum(x,1,3)` 单参数可选、跨逗号吸附完整函数；
6. 复验复制、粘贴、替换、长按连续输入/删除、Ans 展开和历史；
7. 顺手复验双变量统计，确认正式 Release 运行正常；
8. 若真机命中偏高/偏低，只调 Android semantic span 几何容差，不改 Step 1–6 核心编辑协议。

## 下一步

- 完成 0.3.16 Release 真机集中验收；
- 真机通过后把 PR #3 从 Draft 收口并合并到 `main`；
- Stage 3 合并后进入应用模式结果协议统一，优先统计、方程、矩阵、向量，不在当前分支继续扩任务。
