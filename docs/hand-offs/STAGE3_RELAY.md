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

下一步：

- Step 5：函数参数（next）；
- Step 6：语义选区、替换和删除统一；
- Step 7：Android 命中测试与渲染全面切换到 semantic cursor。
