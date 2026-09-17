# Stage 6 接力状态：集成与剩余应用工作流

更新日期：2026-09-17

仓库：`beiming183-cloud/beibei-calculator`

工作分支：`stage6/integration`

基线：`main@9a549c091df673742ef1cb5081fa738946bb5ade`（Stage 5 已按用户授权合并；0.3.17 / 0.3.18 真机检查继续延后集中进行）

## 当前集成状态

Stage 6 曾并行推进三条工作流，现已统一集成到本分支，后续只从 `stage6/integration` 继续：

1. `stage6/calculation-session`：typed 计算会话 / outcome 状态协议；
2. `stage6/workflow-experience`：WorkflowAction、输入校验与工作流体验；
3. `stage6/remaining-workflows`：函数表、不等式、比例等剩余结构化应用。

三条分支均各自通过正式 CI。集成时只有 `core/build.gradle` 的回归任务登记和 `STAGE6_RELAY.md` 发生冲突；`CnCwMachine`、`CnCwWorkflowSpecSuite` 等产品/回归代码均可自动合并。集成分支同时保留 `cwCalculationStateTest` 与 `cwWorkflowValidationTest`，并已在临时 integration run `35170050256` 中通过完整 `:core:check` 与 Android Debug 构建。

所有并行分支的一次性 patch / 验证文件已从集成结果删除。PR #6 / #7 / #8 仅作为历史记录，不应再分别合并到 `main`。PR #10 的有效函数表增量已精确收敛到本分支；其独立 relay 不作为后续接力依据。

## 已纳入的能力

### A. typed 计算状态

- 引入 typed `EDITING / RESULT / ERROR` 会话状态与结果/错误 payload；
- `CnCwUiState` 可发布 typed snapshot，同时保留旧 getter 兼容；
- 成功结果、错误、Ans、历史和异步 evaluation snapshot 开始向统一状态源迁移；
- 数值算法、自然显示和旧按键行为保持兼容。

### B. 应用工作流体验

- core-owned WorkflowAction / 输入校验能力；
- 可定位到具体输入格的错误状态；
- 结构化工作流继续复用 Stage 5 的 `WorkflowSpec / CnCwWorkflowSession`；
- 显式尺寸/阶数动作与既有快捷键并存；
- 结果返回输入时保留工作流数据和焦点。

### C. 剩余结构化应用

- 函数表单函数 / 双函数固定字段输入已接入统一 workflow session；
- 函数表结果已升级为真正的 `TABLE` core payload，保留旧 display 文本与现有 FunctionTableEngine；
- 单函数真实命令 id `f` 与既有 `single` 兼容；
- 不等式关系选择与相应规格已开始结构化；
- Complex / Base-N 暂不混入这一轮表单工作流。

## 下一步（继续在本分支完成）

1. 完成二/三/四次不等式的关系选择、系数数量与输入闭环；
2. 完成比例 `A:B=X:D`、`A:B=C:X` 的标签化输入；
3. 在 Android 为函数表 `TABLE` 结果补滚动/分页；
4. 补统一 Android WorkflowAction 操作条与触摸命中；
5. 完整 core regression、Android Debug/Release 构建与长期签名测试包；
6. 真机验收继续按用户要求延后集中进行。

## 唯一继续点

- branch：`stage6/integration`
- 已完成断点：函数表结构化输入 + `TABLE` core payload
- 下一未完成项：不等式二/三/四次关系选择、系数数量与输入闭环
- 禁止从 `main` 或 `stage6/remaining-apps` 重新创建 Stage 6 工作线；中断后先核对本 relay 与当前 branch head。

## 兼容边界

- 不重写既有数学算法；
- 旧逗号字符串输入继续作为回退；
- Stage 3 语义编辑、Stage 4 结构化结果、Stage 5 结构化输入不得回归；
- Ans、历史、复制粘贴、错误恢复和异步 input revision 门禁不得回归；
- 不改变 `com.beibei.calculator` 与长期 Release 签名链；
- Stage 6 总 PR 保持 Draft，用户明确要求后再合并。
