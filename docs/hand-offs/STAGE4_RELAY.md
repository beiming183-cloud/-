# Stage 4 接力状态：应用模式结果协议

更新日期：2026-09-16

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage4/result-protocol`

基线：`main@5667f996e4cf17dad8e9fbce280b5b731fc458e8`（Stage 3 PR #3 已合并）

## 目标

把统计、方程、矩阵、向量等应用模式从“核心拼两行字符串、Android 被动显示”的临时桥接，升级为核心拥有的结构化结果协议。

迁移原则：

- 现有 `display()` 文本继续保留为兼容回退，迁移期间不破坏已有结果；
- 结构化结果由 core 拥有，Android 只负责渲染；
- 不把统计/方程/矩阵公式搬进 Android；
- 每迁移一个模式先补 core regression，再接 UI；
- Stage 3 语义编辑、复制粘贴、Ans、历史、长期 Release 签名链不得回归。

## Step 1：统一结果基础协议 + 统计（completed）

已完成：

1. `CnCwModeEngine.ModeResult` 从 `display + primaryValue` 扩展为普通不可变类，避免重新引入大型 record 风险；
2. 新增 `ResultLayout`：`TEXT / KEY_VALUE / VECTOR / MATRIX / TABLE`；
3. 新增有序 `ResultItem(label, value)`；
4. `ModeResult` 新增 `layout()` / `title()` / `items()`，同时完整保留旧 `display()` / `primaryValue()` API；
5. 一元统计发布 `n / x̄ / σx / sx`；
6. 双变量统计发布 `x̄ / ȳ / σx / σy`；
7. 线性回归发布 `a / b / r`；
8. 旧两行 `display()` 文本保持不变，因此 Android 当前显示没有行为变化；
9. `CnCwModeEngineSuite` 已覆盖布局、标题、顺序和具体值；
10. 一次性 workflow/script 已删除；clean head `33a72b6` 的正式 PR CI run `35116398191` 已通过完整核心回归、Android Debug APK 构建与 artifact 上传。

## Step 2：方程 / SOLVE 结果结构化（in progress）

计划：

1. 多项式根发布有序 `x1 / x2 / ...`；
2. 2–4 元联立方程发布有序 `x1 / x2 / ...`；
3. SOLVE 发布 `x` 与 `L-R`；
4. 保留原 `display()` 文本和 `primaryValue()` 行为；
5. 补充实根、复根、联立和 SOLVE 的结构化协议回归。

## 后续

- Step 3：矩阵 / 向量结果结构化；
- Step 4：`CnCwUiState` 携带结构化结果，Android 专用渲染；
- Step 5：真机复验并决定 Stage 4 收口范围。
