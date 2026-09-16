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

## Step 1：统一结果基础协议 + 统计（in progress）

计划：

1. 将 `CnCwModeEngine.ModeResult` 从仅有 `display + primaryValue` 扩展为兼容的不可变结构；
2. 增加结果布局类型、标题和有序 key/value 项；
3. 保留 `display()` / `primaryValue()` 原 API；
4. 一元统计、双变量统计、线性回归先发布结构化结果；
5. `CnCwModeEngineSuite` 增加结构化协议回归。

## 后续

- Step 2：方程 / SOLVE 结果结构化；
- Step 3：矩阵 / 向量结果结构化；
- Step 4：`CnCwUiState` 携带结构化结果，Android 专用渲染；
- Step 5：真机复验并决定 Stage 4 收口范围。
