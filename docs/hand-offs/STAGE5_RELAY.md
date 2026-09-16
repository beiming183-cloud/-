# Stage 5 接力状态：应用输入与结果工作流

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage5/application-workflows`

基线：`main@66b21ab77609574189ef578b39a2499e04ee6655`（Stage 4 PR #4 已按用户授权合并；0.3.17 真机验收延后集中进行）

## 目标

Stage 4 已经把统计、方程、矩阵、向量的结果升级为 core-owned 结构化协议。Stage 5 把同一批应用的输入侧从“逗号字符串桥接”升级为 core-owned 工作流规格、编辑状态与 Android 专用输入页，同时保留旧输入方式作为兼容回退。

兼容原则：

- 不改数值算法与既有结果协议；
- 从应用模式首页直接开始输入，仍走原逗号字符串路径；
- 从命令菜单按 OK/EXE 进入新的结构化输入页；
- Stage 3 语义编辑、复制粘贴、Ans、历史不得回归；
- `com.beibei.calculator` 与长期 Release 签名链不得改变；
- 0.3.17 及 Stage 5 真机排版检查延后集中进行，不阻塞主线开发。

## Step 1：core-owned 输入规格协议（completed）

已完成：

1. 新增不可变 `CnCwWorkflowSpec.WorkflowSpec / FieldSpec`；
2. 输入布局统一为 `FIXED_FIELDS / SERIES / PAIRED_SERIES / COEFFICIENTS / GRID / VECTOR_SET`；
3. core 明确提供标题、字段标签、字段类型、最小/最大行列；
4. 覆盖一元统计、双变量统计、线性回归、多项式、联立、SOLVE、矩阵和向量；
5. 新增独立 workflow-spec regression 并挂入 `:core:check`。

## Step 2：统计专用数据输入状态（completed）

已完成：

1. 新增通用 `CnCwWorkflowSession` 行优先编辑状态；
2. 一元统计使用单列数据，双变量/回归使用双列数据；
3. 支持单元格读写、方向移动、增加/删除统计行；
4. 不完整数据拒绝静默提交；
5. session 通过兼容序列化继续调用既有统计引擎，没有复制数值算法。

## Step 3：方程专用系数输入状态（completed）

已完成：

1. 多项式系数按阶数使用 3–5 个系数槽；
2. 2–4 元联立方程使用 n×(n+1) 增广系数表，并拒绝非法形状；
3. SOLVE 使用 `f(x)` 与初值两个固定字段；
4. 三类输入均复用同一 session，并继续委托原方程/SOLVE 引擎计算。

## Step 4：矩阵 / 向量专用输入状态（completed）

已完成：

1. 矩阵使用 1×1 至 4×4 网格；
2. 向量使用 1–2 行、2D/3D 分量网格；
3. `SHIFT+方向键` 可调整矩阵/向量尺寸、联立元数与多项式系数数目；
4. session 可深拷贝并发布不可变 Snapshot，适配异步 EXE 与 Android renderer。

## Step 5：Machine / UiState / Android 专用输入页（code completed）

已完成：

1. `CnCwMachine` 持有结构化 workflow session，evaluation snapshot 深拷贝 session；
2. `CnCwUiState` 只发布不可变 workflow Snapshot；
3. `OK/ENTER` 提交当前格并移动；统计到末行可自动增加下一行；
4. `EXE` 先检查空白项，再通过原 `CnCwModeEngine` 完成计算并继续使用 Stage 4 结果协议；
5. `BACK` 可从结果页返回原输入表，数据不丢；
6. Android 新增统计表格、方程系数表、矩阵/向量网格输入渲染；
7. LCD 上可直接点击单元格切换编辑位置；
8. 旧逗号字符串输入仍可从模式首页直接输入，不被新页面强制替换；
9. 一次性 patch/workflow 已清理；产品代码 clean CI 已通过完整 core regression、Android Debug 构建和 artifact 上传。

## 0.3.18 长期签名真机测试版

- tag：`beibei-0.3.18`；
- versionName：`0.3.18`；
- versionCode：`332`；
- Application ID：`com.beibei.calculator`；
- APK：`beibei-calculator-0.3.18-release.apk`；
- APK SHA-256：`48e18ab98ca01bb62abd71a6fd6a9ced0cd7264cd5b7552aea8fc5f8021af9a4`；
- Release 构建已通过完整 core regression、R8、包名/版本/长期证书硬校验；
- 临时 release workflow 已删除。

## 后续

- 0.3.17 结果页 + 0.3.18 输入页后续一起集中真机验收；
- PR #5 在用户明确要求合并前保持 Draft，不提前合并。
