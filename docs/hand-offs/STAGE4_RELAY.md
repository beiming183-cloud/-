# Stage 4 接力状态：应用模式结果协议

更新日期：2026-09-16

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage4/result-protocol`

基线：`main@5667f996e4cf17dad8e9fbce280b5b731fc458e8`（Stage 3 PR #3 已合并）

## 目标

把统计、方程、矩阵、向量等应用模式从“核心拼两行字符串、Android 被动显示”的临时桥接，升级为核心拥有的结构化结果协议。

迁移原则：

- 现有 `display()` 文本继续保留为兼容回退；
- 结构化结果由 core 拥有，Android 只负责渲染；
- 不把统计/方程/矩阵公式搬进 Android；
- 每迁移一个模式先补 core regression，再接 UI；
- Stage 3 语义编辑、复制粘贴、Ans、历史、长期 Release 签名链不得回归。

## Step 1：统一结果基础协议 + 统计（completed）

已完成：

1. `CnCwModeEngine.ModeResult` 从 `display + primaryValue` 扩展为兼容的不可变结构；
2. 新增 `ResultLayout`：`TEXT / KEY_VALUE / VECTOR / MATRIX / TABLE`；
3. 新增有序 `ResultItem(label, value)`；
4. 保留原 `display()` / `primaryValue()` API；
5. 一元统计发布 `n / x̄ / σx / sx`；
6. 双变量统计发布 `x̄ / ȳ / σx / σy`；
7. 线性回归发布 `a / b / r`；
8. 结构化布局、标题、顺序和值均有 core regression。

## Step 2：方程 / SOLVE 结果结构化（completed）

已完成：

1. 多项式方程发布有序 `x1 / x2 / ...`，包含复根文本；
2. 2–4 元联立方程发布有序 `x1 / x2 / ...`；
3. SOLVE 发布 `x` 与 `L-R`；
4. 旧 `display()` 与 `primaryValue()` 保持兼容；
5. 实根、复根、联立和 SOLVE 均有结构化协议回归。

## Step 3：矩阵 / 向量结果结构化（completed）

已完成：

1. `ModeResult` 新增可选 row-major grid：`rows / columns / cells`；
2. 矩阵使用 `MATRIX` 布局，保存完整矩阵网格；
3. 方阵额外发布 `det`，非方阵不伪造行列式；
4. 单向量使用 `VECTOR` 布局，保存分量并发布模长、单位向量；
5. 双向量保存两行分量并发布点积与夹角；
6. 普通 `TEXT` 结果继续无 grid，兼容旧模式。

## Step 4：UiState + Android 专用结果渲染（completed）

已完成：

1. `CnCwMachine` 保存最近一次 core-owned `ModeResult`；
2. `CnCwUiState` 发布 `applicationResult()` 与 `hasStructuredApplicationResult()`；
3. history / evaluation snapshot 保留结构化结果；
4. 普通 Calculate 结果不发布 application result，继续走原来的自然结果显示；
5. Android 对 `KEY_VALUE` 使用专用键值布局；
6. Android 对 `MATRIX / VECTOR` 使用网格布局，并显示附加项目；
7. `TEXT` 结果继续使用原 `state.result()` 渲染，因此分数、科学计数法、Ans 检查链路不被重写；
8. 一次性 Step 1–4 workflow / patch script 已全部删除；
9. clean head `52cff66cb467fbde351a751cf8f83fa971978e1a` 的正式 PR CI run `35117876549` 已通过完整核心回归、Android Debug APK 构建与 artifact 上传。

## Step 5：真机复验与收口（device validation ready）

代码侧已完成：

1. 版本提升到 `0.3.17 / versionCode 331`；
2. Application ID 保持 `com.beibei.calculator`；
3. 使用长期 Release 签名构建，证书硬校验通过；
4. Release 构建经过完整 core regression 与 R8 构建检查；
5. tag `beibei-0.3.17` 已作为 prerelease 发布，名称 `北北计算器 0.3.17 · Stage 4 真机测试版`；
6. APK：`beibei-calculator-0.3.17-release.apk`；
7. APK SHA-256：`46af8798c435fdcee0723abb39f0bc8f996f634b390424ac7357abd92e34672e`；
8. 临时 0.3.17 发布 workflow 已从分支删除；
9. 清理后的 head `bffd5006e3107d9fd3e410b3e698ace4f4ccce07` 的正式 PR CI run `35118715685` 已通过完整核心回归、Android Debug APK 构建与 artifact 上传。

真机验收重点：

1. 一元统计、双变量统计、线性回归的键值结果排版；
2. 多项式根、复根、联立方程、SOLVE 的结果可读性；
3. 方阵/非方阵的矩阵网格、det 显示；
4. 单向量/双向量的分量、模长、单位向量、点积、夹角；
5. 快速回归 Stage 3 的幂光标、分数/根号/函数精细编辑、复制粘贴、Ans 与历史。

真机通过后再把 PR #4 转 Ready 并合并到 `main`。在此之前 PR #4 保持 Draft。
