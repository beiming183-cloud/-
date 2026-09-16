# Stage 3 接力状态：语义编辑器

更新日期：2026-09-16

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage3/semantic-editor`

基线：`main@decb643`（Stage 2 PR #2 已合并）

## 目标

Stage 3 不继续在 Android View 中堆光标补丁，而是把编辑位置从单一 token 下标逐步升级为真正的语义位置，使分数、幂、根号和函数内部可以按数学结构移动、选择和删除。

原则：

- 不推倒现有编辑器；
- 保留旧 `int cursor` 兼容层，逐步迁移；
- AC、Ans、历史、复制粘贴、长按重复、固定签名和 Stage 2 真机行为不得回归；
- 每接入一种结构，先补核心回归，再接 Android 触摸/渲染。

## Step 1：语义位置模型（completed）

- `CnCwCursorPath`、`semanticCursor()` 与 legacy cursor 兼容层已完成；
- semantic cursor suite 已纳入 `:core:check`。

## Step 2：分数语义编辑（completed）

- `FRACTION_NUMERATOR` / `FRACTION_DENOMINATOR`；
- 上下切换、左右进入/退出、结构安全 DEL、空槽和替换已完成。

## Step 3：幂指数语义编辑（completed）

- `SUPERSCRIPT_BASE` / `SUPERSCRIPT_EXPONENT`；
- 结构移动、上下切换、DEL、空槽和整体/精细选择兼容已完成。

## Step 4：根号 / n 次根语义编辑（completed）

- `RADICAL_CONTENT` / `ROOT_INDEX` / `ROOT_CONTENT`；
- 左右/上下结构移动、DEL、空槽、精细替换和求值回归已完成。

## Step 5：函数参数语义编辑（completed）

- 普通函数发布 `FUNCTION_ARGUMENT`；
- `childPath=[函数模板 token 位置, 参数序号]` 区分多参数；
- 单/多参数左右移动、结构安全 DEL、空参数和函数求值回归已完成；
- 裸 `sin(` 的单次 DEL 删除完整函数 token 行为继续保留；
- Step 5 产品代码清理 head `625e897`，正式 PR CI run `35097482012` 全绿。

### Step 5 收口备注

- 文档收口期间曾误写 `README.md` 为占位文本，随后从误操作前 commit 原样恢复；
- 恢复后的 README blob SHA 为原始 `c1c95661cabc9ee3ef7badeb590b5c7632dee81d`，内容无实际变化。

## Step 6：统一语义选区、替换和删除（completed）

1. `CnCwUiState` 新增 `semanticSelectionAnchor()` / `semanticSelectionFocus()`，选区方向性 anchor/focus 现在同时具有 `CnCwCursorPath` 语义表示；
2. 分数、幂、根号、n 次根和函数参数的精细选区都映射到对应 semantic slot 与槽内 offset；
3. 函数参数触摸选区正式升级为参数级精细选择，例如 `sin(30)` 可以只选择 `30` 或其中一部分；
4. 多参数函数仍保护结构逗号：选区跨越参数边界时自动吸附为完整函数，不能单独删除或替换结构逗号；
5. 嵌套结构保持最内层结构保护，不允许生成破坏内部函数/根式语法的半结构选区；
6. 参数精细选区可以直接 DEL、按键替换或粘贴替换，删除到空参数后和替换后都保持 `FUNCTION_ARGUMENT` 语义位置；
7. 分数、幂、根号原有精细选择与整体结构选择继续通过；键盘 `SHIFT+方向键` 的既有结构选择习惯继续保留；
8. legacy `selectionStart()` / `selectionEnd()` 仍保留供 Android Stage 2 抓手兼容；
9. 一次性 Step 6 patch workflow/script 已删除；
10. 产品代码清理 head `f827b90` 的正式 PR CI run `35098992722` 已通过完整核心回归、Android APK 构建、固定签名检查和 artifact 上传。

## Step 7：Android 语义命中与渲染迁移（code completed，pending device validation）

1. 新增 `CnCwSemanticSpan`，由核心发布每个可编辑 semantic slot 的 `childPath`、slot 类型、槽边界和所属结构边界；Android 不需要重新解析分数、幂、根号或函数 token 语法；
2. `CnCwUiState` 发布 `semanticSpans()`；legacy token boundary 继续保留为回退；
3. `CnCwMachine` 新增 `moveCursorTo(CnCwCursorPath)` 和 touch-selection 语义路径重载；无效/过期路径 fail closed 到保留的 legacy boundary；
4. Android `CalculatorView` 的点击光标、水平滑动、长按起始位置、选区抓手拖动都开始使用 `CnCwCursorPath`；
5. 命中同时参考 x/y：分数分子与分母、幂指数、n 次根指数可以通过垂直位置区分；没有可靠 semantic span 时仍走 Stage 2 x-only token boundary 回退；
6. 选区抓手绘制开始使用 `semanticSelectionAnchor()` / `semanticSelectionFocus()` 反投影到显示位置，而不是只使用平铺 token 下标；
7. Step 7 新增 semantic touch round-trip 回归，semantic cursor suite 增至 243 checks；
8. 开发临时 workflow 第一次因未恢复 debug keystore 在 `validateSigningCn991Debug` 停止，核心回归已经成功；随后改为签名无关的 Android Java 编译验证并成功，产品 commit 为 `a09a0c8`；
9. 一次性 Step 7 patch helper / workflow 已删除；清理 head `7964ed4` 的正式 PR CI run `35100905159` 已通过完整核心回归、APK 构建、固定签名检查和 artifact 上传；
10. 为集中真机验收，验证版升级为 `0.3.14 / versionCode 328`，应用对外名称仍为“北北计算器”，包名与固定签名不变，可覆盖安装。

### Stage 3 真机验收重点

1. 普通表达式：点击移动、水平滑动、左右选区抓手手感不得比 Stage 2 退化；
2. 分数：点击分子/分母能进入对应槽，↑/↓ 可切换，DEL 不拆坏分数；
3. 幂：点击底数/指数能区分，指数区域不应误落到根级 token 边界；
4. 根号 / n 次根：被开方数与根指数命中符合视觉位置；
5. 函数：`sin(30)` 参数可精细选中；`sum(x,1,3)` 单参数可选，跨逗号仍吸附完整函数；
6. 复制、粘贴、选区替换、长按连续输入/删除、Ans 展开、历史计算继续复验；
7. 若命中区域在真机上偏高/偏低，优先调 Android semantic span 的 y 几何容差，不改核心编辑协议。

## 下一步

- 当前验证版源码 head 已包含 `0.3.14 / versionCode 328`；发布 APK 前以最新 head 的正式 PR CI 为准，不复用早期 0.3.13 artifact；
- 安装 0.3.14 Stage 3 验证 APK并完成上述真机复验；
- 真机通过后，把 PR #3 从 Draft 收口，最后再决定合并 Stage 3；
- Stage 3 合并后再进入下一阶段的统计、方程、矩阵、向量等应用模式结果协议统一，不在当前分支继续扩任务。
