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

已完成第一层基础设施：

1. 新增 `CnCwCursorPath` 不可变位置对象；
2. 位置由以下部分组成：
   - `childPath`：表达式树中的子节点路径；
   - `slot`：当前位置所在的语义槽；
   - `offset`：槽内插入偏移；
   - `legacyTokenBoundary`：迁移期间对应的旧 token 边界；
3. 预留的语义槽包括：
   - `ROW`
   - `FRACTION_NUMERATOR`
   - `FRACTION_DENOMINATOR`
   - `RADICAL_CONTENT`
   - `ROOT_INDEX`
   - `ROOT_CONTENT`
   - `SUPERSCRIPT_BASE`
   - `SUPERSCRIPT_EXPONENT`
   - `FUNCTION_ARGUMENT`
4. `CnCwUiState` 新增 `semanticCursor()`；当前 bootstrap 阶段由旧 token cursor 生成 root-boundary path；
5. 旧 `cursor()` 完全保留，Android 现有触摸逻辑无需立即改写；
6. 新增 `CnCwCursorPathSuite`，验证 root compatibility、nested path value semantics，以及 UI state 与旧 cursor 的同步；
7. 新增 Gradle `cwCursorPathTest` 并纳入 `:core:check`。

### Step 1 当前验收标准

- 旧 token 光标行为保持不变；
- 每个 UI state 都能同时读取旧 cursor 和 semantic cursor；
- semantic path 可以表达分数/幂/根号/函数的未来嵌套位置；
- `:core:check` 必须包含 semantic cursor suite。

## Step 2：分数内部移动（completed）

分数语义编辑已收口：

1. 分子 / 分母分别发布 `FRACTION_NUMERATOR` / `FRACTION_DENOMINATOR`；
2. `UP` / `DOWN` 在分子与分母之间切换，并尽量保持局部 offset；
3. 左右移动采用显式语义路径：`root-before → numerator → denominator → root-after`，边界不再靠跳 token 猜测；
4. 分数边缘允许“同一 legacy boundary、不同 semantic slot”，因此可以区分分数内部和外部；
5. DEL 在分数内部只删除槽内容；分母起点回到分子、分子起点退出分数，不允许删除结构分隔符；
6. 从 `root-after` DEL 会原子删除完整分数；
7. 分子或分母暂时为空时仍保留 FRACTION 自然树和可见光标；
8. 分母可独立选中并替换，继续扩选可覆盖完整分数，替换后结构保持正确；
9. 旧 `int cursor`、Stage 2 触摸协议、复制粘贴和固定签名继续保留。

## Step 3：幂指数语义编辑（completed）

幂结构已按与分数一致的迁移原则接入 semantic cursor：

1. `^` 模板发布 `SUPERSCRIPT_BASE` / `SUPERSCRIPT_EXPONENT`；
2. 左右路径为 `root-before → base → exponent → root-after`；
3. `UP` 从 base 进入 exponent，`DOWN` 从 exponent 回到 base，并尽量保持局部 offset；
4. DEL 在 base / exponent 内只删除槽内容，不允许单独删除结构 `^`；
5. exponent 起点 DEL 回到 base，base 起点 DEL 退出幂；
6. 从显式 `root-after` DEL 原子删除完整幂；
7. base 或 exponent 暂时为空时，SUPERSCRIPT 自然树仍保留并显示光标；
8. 触摸精细选区可独立选择/替换 exponent 或 base；键盘 `SHIFT+方向键` 继续遵守 Stage 2 协议，把幂整体视为一个选择单元；
9. 分数语义优先级保持不变，旧 `int cursor` 和 Stage 2 触摸协议继续兼容。

## Step 4：根号 / n 次根语义编辑（completed）

根号结构已进入 semantic cursor：

1. `sqrt(` 发布 `RADICAL_CONTENT`；
2. 通用 `root(index, content)` 分别发布 `ROOT_INDEX` / `ROOT_CONTENT`；
3. 固定三次根 `root(3,` 发布 `ROOT_CONTENT`，固定根指数不伪装成可编辑槽；
4. 左右移动采用 `root-before → content → root-after`，n 次根采用 `root-before → index → content → root-after`；
5. n 次根 `UP` 从 content 进入 index、`DOWN` 从 index 回到 content；简单根号/固定三次根会消费上下键，避免误触历史回溯；
6. DEL 只删除 index/content 槽内容，不允许逐个破坏根号模板、参数分隔逗号或闭括号；从 root-after DEL 原子删除完整根结构；
7. index/content 为空时仍保留语义槽和可见光标；
8. 键盘结构选区继续把闭合根式视为整体；触摸精细选区可独立替换 radicand 或 n 次根 index；
9. `sqrt(9)` 与 `root(3,8)` 的求值回归继续通过，未改 evaluator 参数顺序；
10. 分数、幂语义优先级和 Stage 2 兼容协议保持不变。

## Step 5：函数参数语义编辑（completed）

普通函数参数已经接入 semantic cursor，同时保留 Stage 2 交互契约：

1. 普通函数（如 `sin(`、`cos(`、`ln(`、`sum(`）发布 `FUNCTION_ARGUMENT`；`sqrt/root` 继续由 Step 4 专用根号语义处理，`e^(`/`×10^(` 也不伪装成普通函数；
2. `childPath` 使用 `[函数模板 token 位置, 参数序号]` 区分多参数函数中的每一个参数；
3. 单参数函数左右路径为 `root-before → argument → root-after`；多参数函数按 `arg0 → arg1 → ...` 移动，结构逗号不会成为可编辑光标位置；
4. DEL 在参数内部只删除参数内容，在后续参数起点会回到前一参数，不允许删除结构逗号；从闭合函数 `root-after` DEL 会原子删除完整函数；
5. 参数暂时为空时仍保留 `FUNCTION_ARGUMENT` 语义位置，可以继续输入或粘贴；
6. 保留 Stage 2 的裸函数兼容行为：刚输入 `sin(` 这类只有一个空参数的裸函数 token 时，DEL 一次仍会删除完整函数 token；
7. Step 5 暂时保留 Stage 2 的函数整体触摸选区，参数级精细触摸选区留到 Step 6；
8. `sin(30)` 与 `sum(x,1,3)` 求值回归继续通过；
9. 开发过程中两次由旧回归拦住兼容性变化（裸函数 DEL、函数内部触摸选区），均按阶段边界恢复后再通过；
10. 一次性 Step 5 patch/compat workflow 与脚本已全部删除；产品代码清理 head `625e897` 的正式 PR CI run `35097482012` 已通过完整核心回归、Android APK 构建、固定签名检查和 artifact 上传。

### Step 5 收口备注

- 收口文档期间曾误写 `README.md` 为占位文本；随后立即从误操作前 commit 原样恢复。
- 恢复后的 README blob SHA 为原始 `c1c95661cabc9ee3ef7badeb590b5c7632dee81d`，因此 README 内容无实际变化。

## Step 6：统一语义选区、替换和删除（completed）

选区协议已经从“仅有 token 起止下标”继续迁移到语义位置，同时保留旧下标供 Android 兼容：

1. `CnCwUiState` 新增 `semanticSelectionAnchor()` / `semanticSelectionFocus()`，方向性 anchor/focus 均发布 `CnCwCursorPath`；
2. 分数分子/分母、幂底数/指数、根号内容/根指数、函数参数都可以把选区 anchor/focus 映射到对应 semantic slot 与槽内 offset；
3. 触摸选区在同一可编辑语义槽内保持精细选择，不再强制扩大到整个函数；因此 `sin(30)` 可以只选 `30` 或其中一部分；
4. 多参数函数通过 `[函数模板位置, 参数序号]` 标识参数；选区一旦跨越结构逗号，会自动吸附为完整函数调用，结构逗号不会被单独删除或替换；
5. 嵌套结构仍优先保护最内层结构边界：选择跨越内部函数参数时会先吸附内部结构，不允许生成破坏嵌套语法的半结构选区；
6. 参数精细选区可直接 DEL 或粘贴替换；删除到空参数后仍停留在原 `FUNCTION_ARGUMENT`，粘贴替换后也保持参数语义身份；
7. 分数、幂、根号原有精细选择与整体结构选择协议继续工作，键盘 `SHIFT+方向键` 的既有结构选择习惯未被强行改写；
8. 旧 `selectionStart()` / `selectionEnd()` 继续保留，Android Stage 2 抓手代码仍可工作；Step 7 再迁移到 semantic selection anchor/focus；
9. 更新旧 golden regression：Step 6 起，函数单参数内部触摸拖选的正式契约改为参数级精细选区；
10. 一次性 Step 6 patch workflow/script 已删除；产品代码清理 head `f827b90` 的正式 PR CI run `35098992722` 已通过完整核心回归、Android APK 构建、固定签名检查和 artifact 上传。

## Step 7：Android 语义命中与渲染迁移（next）

下一步只迁移 Android 适配层，不再改变 Step 1–6 的核心数学编辑协议：

1. 触摸点击与拖动命中逐步读取 `semanticCursor()` / `semanticSelectionAnchor()` / `semanticSelectionFocus()`；
2. 自然表达式节点提供分子、分母、指数、根号内容、函数参数等可进入区域，不再只靠平铺 token 字符串宽度判断；
3. 保留 legacy token boundary 作为回退，直到真机手势全部验收；
4. 真机复验点击光标、水平滑动、上下结构移动、左右选区抓手、复制粘贴、DEL、长按连续输入/删除；
5. Step 7 通过后，再决定 PR #3 是否从 Draft 收口并合并 Stage 3。
