# Stage 6 接力状态：剩余结构化应用工作流

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/remaining-workflows`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已合并；0.3.17/0.3.18 真机验收按用户要求延后集中进行）

## 目标

Stage 5 已经把统计、方程、矩阵、向量升级为 core-owned 输入规格、统一 workflow session 和 Android 专用输入页。Stage 6 继续复用同一套架构，完成 991 HOME 中仍依赖逗号字符串桥接的主要结构化应用：函数表、不等式、比例。

兼容原则：

- 不改既有数值算法；
- 旧逗号输入继续作为兼容回退；
- 新输入页仍通过 `CnCwModeEngine` 求值，避免双实现；
- Stage 3 语义编辑、Stage 4 结果协议、Stage 5 workflow session 不得回归；
- `com.beibei.calculator` 与长期 Release 签名链保持不变；
- Complex / Base-N 留到后续专门阶段，不混入表单工作流。

## Step 1：函数表专用表单（in progress）

- 单函数：`f(x)`、开始、结束、步长；
- 双函数：`f(x)`、`g(x)`、开始、结束、步长；
- 复用 `FIXED_FIELDS` session；
- EXE 继续委托原 `FunctionTableEngine`。

## Step 2：不等式专用输入

- 二/三/四次命令自动确定系数数量；
- 关系使用结构化选择而不是要求用户输入 1/2/3/4；
- 系数页复用统一 workflow session。

## Step 3：比例专用输入

- `A:B=X:D` 显示 A/B/D 三个已知量；
- `A:B=C:X` 显示 A/B/C 三个已知量；
- 结果保持 core-owned 结构化显示。

## Step 4：函数表滚动结果 / 工作流可用性

- 函数表结果升级为真正 `TABLE` 结构；
- Android 支持结果滚动/分页；
- BACK 返回原输入数据。

## Step 5：集中回归与长期签名测试包

- 完整 `:core:check`；
- Android Debug / Release 构建；
- 包名、版本和长期证书硬校验；
- PR 保持 Draft，真机检查继续延后到用户指定时间。
