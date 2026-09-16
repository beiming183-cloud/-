# Stage 6 接力状态：计算会话与结果状态协议

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/calculation-session`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已按用户授权 squash 合并；0.3.17/0.3.18 真机验收继续延后集中进行）

## 目标

Stage 3 已完成语义编辑兼容迁移，Stage 4/5 已完成应用模式结构化结果和结构化输入工作流。Stage 6 处理剩余的核心状态债务：当前编辑、结果、错误、Ans、历史仍由 `resultShown`、`errorShown`、`result`、`applicationResult`、`lastError`、`exactAns`、`complexAns` 等字段共同决定，容易让后续模式继续堆条件分支。

Stage 6 引入平台无关、typed 的计算会话/结果协议，并采用兼容式迁移：先让新协议准确镜像现有行为，再逐步把各计算路径改为只写一个 outcome/source of truth。Android 继续只读取 `CnCwUiState`，数值算法本身不在本阶段重写。

## Step 1：typed outcome / phase 兼容层

- 新增 `CnCwCalculationState`（或等价类型）；
- 明确 `EDITING / RESULT / ERROR` 三种 phase；
- 结果 payload 至少区分 scalar/exact、complex、application/text；
- `CnCwUiState` 发布 typed snapshot，同时保留原兼容 getter；
- 核心回归证明新旧状态一致。

## Step 2：统一成功结果提交

- 普通标量、精确值、复数、应用模式通过统一 commit-result 路径；
- Ans 更新策略由 typed result 决定，不再散落在 evaluator 分支；
- 保留现有显示文本与自然格式。

## Step 3：统一错误提交与恢复

- `Math ERROR / Syntax ERROR / domain/range` 等通过 typed error outcome；
- 错误光标、BACK/AC/左右恢复行为保持兼容；
- 逐步把 `errorShown/lastError` 降为兼容派生字段。

## Step 4：历史与异步快照

- 历史记录保存 typed outcome；
- `copyForEvaluation()` 深拷贝会话状态；
- 慢计算结果仍受 input revision 门禁，不覆盖新输入。

## Step 5：Android 消费迁移与回归

- Android 结果/错误渲染优先读取 typed state；
- 保留现有自然科学计数、结构化应用结果、Ans 展开、剪贴板行为；
- 与 0.3.17/0.3.18 遗留真机项目一起集中验收。

## 明确不做

- 不在 Stage 6 重写数学算法；
- 不删除 Stage 3 token/cursor 兼容层；
- 不更换 `com.beibei.calculator`、长期 release 签名或安装链；
- 不因状态重构改变已经通过回归的按键、AC、历史、复制粘贴和结构化输入行为。
