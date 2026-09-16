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

## Step 1：语义位置模型（进行中）

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

## 下一步：Step 2 分数内部移动

Step 1 CI 通过后，下一项只做分数，不同时改幂和根号：

1. 为自然表达式中的 `FRACTION` 建立 numerator / denominator 子路径；
2. 定义左右移动规则：
   - 分子末尾向右可进入分母或退出分数；
   - 分母开头向左可回到分子或退出分数；
3. 定义上下规则：分子 ↔ 分母；
4. DEL 在嵌套位置只能做语义删除，不能拆坏分数模板；
5. 旧 token cursor 继续作为外部触摸兼容边界；
6. 增加分子、分母、上下切换、左右退出、删除和回归测试。

分数路径稳定后，再依次接入：

- Step 3：幂指数；
- Step 4：根号 / n 次根；
- Step 5：函数参数；
- Step 6：语义选区、替换和删除统一；
- Step 7：Android 命中测试与渲染全面切换到 semantic cursor。
