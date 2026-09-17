# Stage 6 接力状态：剩余应用模式完整化

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/remaining-apps`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已合并；0.3.17/0.3.18 真机检查按用户要求延后集中进行）

## 目标

Stage 4/5 已完成统计、方程、矩阵、向量的结构化结果与输入工作流。Stage 6 继续把 991 产品中剩余应用模式从临时文本桥接升级为 core-owned 协议和专用 UI，同时保留旧输入路径与数值引擎。

## 计划

1. Step 1：函数表。新增 `f(x)` / `f(x),g(x)` 固定字段输入规格，结果升级为真正 `TABLE` 网格，保留旧 display 文本。
2. Step 2：不等式 / 比例。补结构化输入规格和结果协议。
3. Step 3：复数 / 进制。补模式级状态、格式化结果和必要的专用交互，不重写表达式引擎。
4. Step 4：Android 表格分页/滚动及剩余模式专用结果显示。
5. Step 5：完整回归、长期签名测试版，与 0.3.17/0.3.18 一起集中真机验收。

## 兼容约束

- 不修改已验证的 Stage 3 语义编辑行为；
- Stage 4 结果协议与 Stage 5 workflow session 继续复用，不另造第二套状态容器；
- 旧逗号输入路径继续可用；
- `Application ID = com.beibei.calculator` 与长期 Release 签名链保持不变；
- PR 保持 Draft，除非用户明确要求合并。

## 当前状态

- Stage 6 分支已建立。
- Step 1：in progress。
