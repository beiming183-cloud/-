# CN991 架构审查与分层改造方案

审查日期：2026-09-15

目标：在不推倒重写计算器的前提下，明确触摸/按键、手势、语义编辑、计算和显示之间的边界，为长按、连续删除、滑动光标、选区复制和说明书功能补齐建立稳定基础。

## 一、现有架构结论

当前项目已经有两个有价值的基础：

- `CnCwMachine` 是核心状态机，Android 视图主要通过 `CnCwUiState` 读取状态。
- 计算能力已经拆出多个引擎，例如标量、复数、矩阵、向量、统计、电子表格和应用模式引擎。

但目前的真实边界如下：

```text
CalculatorView
  ├─ 触摸识别
  ├─ 显示绘制
  ├─ 光标命中测试
  ├─ 长按复制菜单
  ├─ 剪贴板格式清洗
  ├─ 粘贴字符映射
  └─ 直接调用 CnCwMachine.dispatch()

CnCwTouchRouter
  └─ 只记录按下/抬起，没有长按、重复、滑动状态

CnCwMachine
  ├─ 按键路由和菜单导航
  ├─ 表达式 token 编辑
  ├─ 光标位置
  ├─ 错误/结果/历史状态
  ├─ 计算调度
  ├─ Ans/变量/格式转换
  └─ 应用模式工作流

各数学引擎
  └─ 负责具体计算，但输入协议和显示协议尚未统一
```

## 二、已确认的耦合点

### 1. 手势层还没有独立存在

`CalculatorView.onTouchEvent()` 目前在同一个方法里处理按下、移动、抬起、显示区长按和光标拖动。普通按键按下后立即调用 `dispatchKey()`；`CnCwTouchRouter` 只负责防重复按键，不负责手势生命周期。

结果是：长按连续输入如果直接加在 View 中，会把“长按”实现成多次普通按键派发，后续很难统一处理取消、滑出、重复速度和触感。

### 2. `CnCwMachine` 是巨型状态机

它同时管理：菜单、应用、编辑 token、光标、错误、结果、历史、Ans、变量、格式、表格焦点和计算调用。这样可以工作，但任何新交互都要穿过大量分支，容易产生“第一次按键只关闭错误，第二次才清空”这类状态优先级问题。

### 3. 光标仍然是 token 下标，不是语义编辑位置

当前核心使用 `int cursor`，并能返回 token 显示文本；显示区再用绘制宽度反推 token 边界。自然表达式树已经存在，但光标没有独立的语义路径模型。

这会影响：分数上下移动、指数位置、函数参数、根号内部、整块删除和滑动命中。

### 4. 剪贴板逻辑在 Android View 内

复制/粘贴、光标清洗、分数转小数和字符映射都在 `CalculatorView`。复制的内容来源是 `state.expression()` 或 `state.result()`，但没有独立的语义导出协议。因此后续增加“复制选区”“复制公式树”“复制结果原值”会继续把显示层和编辑层绑在一起。

### 5. 错误、结果、编辑状态没有形成明确状态类型

`CnCwMachine` 中通过 `resultShown`、`errorShown`、`lastError`、`tokens`、`cursor` 等多个字段组合出当前状态。状态转换依赖分支顺序，AC、返回、方向键和新输入对错误/结果的优先级容易不一致。

### 6. 应用模式已有入口，但输入/结果协议不统一

统计、分布、函数表、方程、不等式、矩阵、向量、比例和电子表格已经有引擎或工作流入口，但都通过 `String source`、`List<String> fields`、`ModeResult(display, primaryValue)` 等不同方式连接。后续要补说明书功能，需要统一“输入字段、计算结果、错误、可继续编辑内容”的协议。

## 三、目标边界

```text
Android 触摸/硬件输入
        ↓ 只产生原始事件
GestureController
        ↓ 产生 Tap / LongPress / Repeat / Swipe / Cancel
InputCommand
        ↓ 统一进入核心
SemanticEditor
        ↓ 维护表达式树、语义光标、选区、撤销、复制导出
CalculationSession
        ↓ 调用标量/复数/应用引擎
CalculationResult
        ↓ 统一的结果、错误、Ans、格式协议
CnCwUiState / Renderer
        ↓ 只读渲染
屏幕、触感、Toast、剪贴板适配器
```

### 输入层

输入层只负责把 Android `MotionEvent`、硬件重复事件转换为统一事件，不直接修改计算状态。

建议的事件类型：

```text
PointerDown(pointerId, position, hitTarget)
PointerUp(pointerId, position)
PointerCancel(pointerId)
LongPress(target)
Repeat(target, repeatIndex)
Swipe(start, current, target)
HardwareKey(key, repeat)
```

### 手势层

手势层负责阈值和生命周期：按下反馈、长按触发、连续重复、滑出取消、滑回恢复、多指冲突和触感节流。它只输出 `InputCommand`，不直接调用计算引擎。

### 语义编辑层

编辑层应成为普通计算和各应用模式共享的输入协议，至少提供：

```text
insert(command)
deleteBackward()
move(direction / semanticTarget)
select(anchor, focus)
clearInput()
copyExpression()
copySelection()
paste(expressionText)
snapshot()
```

光标建议从单个 token 下标升级为语义位置：

```text
CursorPath(root, childIndex, slot)
```

其中 `slot` 表示函数参数、分数分子/分母、幂底/幂顶、根号内部等位置。对外仍可保留 token 下标作为兼容字段，但新交互不能只依赖它。

### 计算层

计算层接收不可变的 `ExpressionSnapshot` 或应用字段，不读取 Android 状态，不写显示字符串。统一返回：

```text
CalculationResult {
  status: SUCCESS | ERROR
  exactValue
  decimalValue
  complexValue
  displayForms
  error
  continuation
  ansUpdate
}
```

### 显示层

显示层只消费 `CnCwUiState` 和语义渲染节点。它不负责推断公式、不负责清洗复制文本、不负责决定 AC 的语义。

## 四、最小改造顺序

### 第一步：先统一状态意图，不动计算公式

- 为 AC、返回、确认、新输入定义明确优先级。
- 把“编辑中、已出结果、错误中、菜单中、应用入口”整理成显式状态枚举或状态标签。
- 保留现有 `dispatch(CnCwKey)` 作为兼容入口，内部先转为 `InputCommand`。

验收：错误后单按 AC、结果后按 AC、菜单中按 AC、`SHIFT+AC` 的行为全部有独立测试。

### 第二步：抽出手势控制器

- 新增平台无关的手势状态机。
- View 只投递 Pointer 事件并消费反馈。
- 重复输入由手势控制器产生 `Repeat`，不能由 View 手写循环调用 `dispatchKey()`。

验收：DEL、方向键、数字键分别覆盖短按、长按、滑出取消、多指和触感节流。

### 第三步：抽出剪贴板/选择协议

- 核心提供公式、选区、结果的结构化导出。
- Android 只负责把导出文本放入系统剪贴板。
- 粘贴先解析为编辑命令或语义节点，再进入编辑器。

验收：复制公式不含光标；复制结果不含公式；复制选区只含选区；粘贴 `× ÷ − √ ² ³ i` 可重新计算。

### 第四步：升级语义光标

- 为自然表达式节点建立边界和可进入的子位置。
- 左右按语义位置移动。
- 上下按结构移动。
- 整体删除结构节点，避免分数/函数被拆坏。

验收：分数、幂、根号、函数、括号、复数输入各有移动和删除测试。

### 第五步：统一应用模式结果协议

- 统计、方程、矩阵、向量、表格等模式统一返回成功/错误/可继续编辑/主结果。
- 显示文本只在渲染适配器生成。
- Ans、变量和格式转换由会话层统一更新。

## 五、暂时不要做的事情

- 不要先把所有按键改成 Android 原生 Button；这会破坏当前自绘布局并不能解决语义问题。
- 不要先在 View 中加入定时器循环派发普通按键。
- 不要把光标改成字符下标作为最终方案。
- 不要为每个应用模式各写一套复制、错误和结果处理。
- 不要在架构层完成大规模计算公式重写；计算引擎应在边界稳定后逐项补齐。

## 六、模型接力建议

### 6 Astra 高

负责本文件的架构审查、状态边界、接口设计和最终验收，不直接承担大量机械改动。

### 5.6 Terra 高

负责计算会话、结果协议、语义编辑器和说明书公式链路；改动后必须补核心回归测试。

### 5.6 Sol 中/高

负责手势控制器、长按重复、触感、复制/粘贴适配和 Android 交互；不得绕过核心状态机。

### 5.6 Sol 低

负责小范围样式、文案、测试补充和单文件机械修改。

## 七、阶段一完成标准

- [ ] 有独立的手势事件模型设计。
- [ ] 有明确的编辑状态、结果状态、错误状态边界。
- [ ] 复制/粘贴协议不依赖屏幕绘制字符串。
- [ ] 光标语义路径设计完成，并保留旧 token 下标兼容层。
- [ ] 普通模式和应用模式共享输入/结果协议。
- [ ] 每个边界都有回归测试计划。

