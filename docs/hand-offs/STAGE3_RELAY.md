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
8. legacy `selectionStart()` / `selectionEnd()` 仍保留供 Android Stage 2 抓手兼容，Step 7 再迁移 Android；
9. 一次性 Step 6 patch workflow/script 已删除；
10. 产品代码清理 head `f827b90` 的正式 PR CI run `35098992722` 已通过完整核心回归、Android APK 构建、固定签名检查和 artifact 上传。

## Step 7：Android 语义命中与渲染迁移（next）

下一步只迁移 Android 适配层，不再改变 Step 1–6 的核心数学编辑协议：

1. 触摸点击与拖动命中逐步读取 `semanticCursor()` / `semanticSelectionAnchor()` / `semanticSelectionFocus()`；
2. 自然表达式节点提供分子、分母、指数、根号内容、函数参数等可进入区域，不再只靠平铺 token 字符串宽度判断；
3. legacy token boundary 暂时继续作为回退；
4. 真机复验点击光标、水平滑动、上下结构移动、左右选区抓手、复制粘贴、DEL、长按连续输入/删除；
5. Step 7 通过后，再决定 PR #3 是否从 Draft 收口并合并 Stage 3。
