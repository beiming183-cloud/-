# Stage 6 接力状态：应用工作流体验

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/workflow-experience`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已合并；0.3.17/0.3.18 真机集中验收继续延后）

## 目标

Stage 5 已经把统计、方程、矩阵、向量从逗号字符串桥接升级成 core-owned 输入规格、统一 workflow session 与 Android 专用输入页。Stage 6 不再扩展数值算法，重点把这些页面从“能输入”推进到完整、可发现、可恢复的应用工作流。

兼容原则：

- 不改 `CnCwModeEngine` 的既有数值算法；
- 旧逗号输入路径继续保留；
- Stage 3 语义编辑、Stage 4 结构化结果、Stage 5 workflow session 不回退；
- `com.beibei.calculator` 与长期 Release 签名链保持不变；
- 真机检查继续集中到阶段末。

## Step 1：core-owned WorkflowAction 协议（in progress）

把“新增/删除行、调整尺寸、移动焦点、执行/返回”等动作从隐藏的按键约定抽成 core-owned 动作能力。UiState 发布当前可用动作，Android 只负责展示/触发，不再自己猜某种工作流支持什么。

## Step 2：输入校验与错误定位

每个单元格/字段在 core 中有校验状态；空白、不可解析表达式、维度不匹配等错误能定位到具体单元格，EXE 失败后保持原输入数据和焦点。

## Step 3：尺寸/阶数配置工作流

矩阵、向量、联立方程、多项式不再依赖 `SHIFT+方向键` 的隐藏手势；提供显式、可发现的尺寸/元数/阶数配置动作，同时保留旧快捷键。

## Step 4：结果 ↔ 输入往返

结果页支持“返回修改”“新建一组”“重复计算”；返回输入时恢复全部表格数据、尺寸和焦点，不重新解析展示字符串。

## Step 5：Android 工作流控件与集中回归

为可用 WorkflowAction 提供统一 LCD 操作条/触摸命中；完成统计、方程、矩阵、向量集中回归并生成长期签名测试包。
