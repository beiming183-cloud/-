# Stage 2 自动接力状态

基线提交：`41577725d004d6886b16ed103783962d65e25aaa`

工作分支：`auto/stage2`

原则：不得修改既有功能语义；每一步只完成本步明确任务，测试与 APK 构建通过后写 hand-off 并提交，再进入下一步。

## 当前状态

- 当前步骤：Stage 2 Step 3
- 状态：completed
- 指定模型：GPT-5.6 Sol High
- 已完成：Stage 2 Step 2（回归测试与 CN991 Debug APK 构建通过）

## Stage 2 Step 2

目标：统一按下、长按、重复、取消的手势事件模型，并加入方向键长按连续移动。

必须完成：

- 加入方向键长按连续移动。
- 不做函数备用菜单。
- 保持 AC、语义 DEL、历史、复制粘贴逻辑不变。
- 运行现有核心回归测试。
- 构建 CN991 调试 APK。
- 完成后新增 `docs/hand-offs/STAGE2_STEP2.md`。
- 提交 Step 2 代码与 hand-off。

验收门：

- `./gradlew :core:regressionTest` 通过。
- `./gradlew :app:assembleCn991Debug` 通过。
- `STAGE2_STEP2.md` 存在并记录实现、验证、已知限制与下一步。
- 未引入函数备用菜单。
- 未改变 AC、语义 DEL、历史、复制粘贴既有语义。

## Stage 2 Step 3

指定模型：GPT-5.6 Sol High

必须完成：

- 滑动光标。
- 语义光标移动。
- 语义选区。
- 运行现有核心回归测试。
- 构建 CN991 调试 APK。
- 完成 hand-off 并提交。

建议 hand-off：`docs/hand-offs/STAGE2_STEP3.md`

## 接力规则

1. 当前步骤只有在验收门全部通过后才能标记为完成。
2. 完成 Step 2 后，接力到 Step 3，并切换到 GPT-5.6 Sol High。
3. 任一步遇到真正阻塞时停止，不通过猜测、静默改模或删减验收条件绕过。
4. 模型额度不足时保留当前分支、提交、测试结果和 hand-off 状态，由后续执行器从最近已提交状态继续。
5. 不直接修改 `main`；所有 Stage 2 工作保持在 `auto/stage2` Draft PR 中。

## Stage 2 Step 3 验证结果

- 指定模型：GPT-5.6 Sol High。
- 回归测试与 CN991 Debug APK 构建通过。
- hand-off：docs/hand-offs/STAGE2_STEP3.md。
