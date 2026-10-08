# 任务 2：纯 Java 挣扎规则的 TDD 记录

日期：2026-10-06。仅修改当前工作目录中的规则、独立测试及验证脚本；未提交 Git、未修改桌面副本或 GitHub。

## 实际 RED / GREEN

先写行为测试及可编译的空规则骨架，然后实际执行独立 Java 21 检查。RED 在第一条行为断言失败：

```text
java.lang.AssertionError: first F edge is accepted
```

失败日志保存在 `build/struggle-red.log`。随后实现规则并运行 GREEN，再增加时钟极值、组合输入和 episode 重置边界测试；最终输出：

```text
StruggleRules: 739 assertions passed; board fed=19.8s hungry=33.6s
```

通过日志保存在 `build/struggle-green.log`。739 包含对连续静止和持续按键逐 tick 的断言，不代表 739 个独立测试用例。

验证命令：

```powershell
$env:JAVA_HOME='D:\download\jdk21\jdk-21.0.9+10'
& tools/verify_struggle_rules.ps1
```

脚本将独立编译产物写到当前工作目录 `build/validation/struggle-rules`，不依赖 Minecraft、Gradle 或额外测试框架。

## 公共 API 与调用语义

`StruggleRules` 完全不依赖 Minecraft。服务器控制器对一个连续受困 episode 持有不可变的 `State`，每个服务器 tick 调用一次：

```java
Result result = StruggleRules.step(state, serverTick, medium,
        new Input(movementIntent, jumping, keyDown), foodLevel);
state = result.state();
```

- `Medium.GLUE / BOARD / OTHER` 分别区分胶水、粘鼠板和其他可困住介质。
- `Input.moving` 必须来自移动意图，不能只依靠实际位移；完全黏住的人尝试移动也会产生弱挣扎进度。
- `keyDown` 和 `jumping` 检测上升沿；F 与跳跃冲动共用 12 tick 的服务器冷却，避免同时触发双倍冲动。移动的弱进度按 tick 计算。
- 网络 F 消息可以在到达 tick 输入一次 `keyDown=true`，下一 tick 恢复 false；客户端必须发送真实按键上升沿，而不是操作系统的重复按键事件。
- 同 tick 重复或旧 tick 调用原样返回状态，不释放按键、不增加努力、不消费饥饿、不重复输出深度增量。初始 `Long.MIN_VALUE` 哨兵独立处理，验证覆盖 `Long.MAX_VALUE` 附近的冷却边界。
- `Result.acceptedPress` 只表示 F 被服务器接受，适合启动一次 12 tick 的动画；`active` 表示这个 tick 有实际挣扎输入。动画持续时间本身不重复产生收益或下陷。
- `Result.sinkDelta` 是这个 tick 额外增加的下陷量，不能当成累计深度反复施加；F 只在被接受的 tick 输出一次。`adhesionReduction` 同样是本 tick 的黏力比例差。
- `State.adhesionScale(medium)` 返回从初始黏力到当前的累计比例。板子可降到 0；胶水和其他介质保留至少 0.2 的黏连，不会仅凭累计按键完全解除连接。
- `State.boardReleased(medium)` 只在 BOARD 上达到脱困努力阈值时返回 true。
- 控制器在真正结束受困 episode 时替换为 `State.initial()`；停止挣扎不能重置进度。板子更换、目标介质/锚点改变时，控制器需按 episode 身份决定是否开启新 episode，不能把另一块板的脱困进度无限继承。

## 已验证的玩法边界

初始胶水不会因时间推移自动下陷。移动意图、跳跃尝试和 F 都能积累努力；达到 4 努力后才开始由后续挣扎产生下陷增量。停止挣扎后增量立即为 0；规则从不返回负深度，不会自动回浮，也不会清除已触发的胶水下陷状态。

板子脱困阈值为 34 努力。每 12 tick 合理按一次 F，正常饱食度在第 396 tick（19.8 秒）达到阈值。移动每 tick 增加 0.008 努力，移动与 F 结合仍保持至少 15 秒。饱食度低于 6 时努力及下陷动作效率为 60%，第 672 tick（33.6 秒）仍可脱困。每次服务器接受 F 消耗 0.2 exhaustion；拒绝请求或按住 F 不重复消耗。

板子挣扎削弱黏力但不输出下陷；OTHER 的专用挣扎同时削弱黏力并付出额外下陷代价。实际可用下陷空间、浅池底部、窒息、绳索救援、实体运动及动画由游戏集成层处理，纯规则不替这些系统做决定。

## 留靴判断的边界

`shouldLoseBoot(state, medium, probability, sample)` 是纯概率判定；`markBootChecked(state)` 幂等地标记 episode 已判断。推荐使用 `evaluateBootLoss(...)` 一次得到新状态与结果，失败抽样也标记已判断；只支持 GLUE 和 BOARD，OTHER 不触发也不消耗标记。

控制器仅在真实奋力拔脚或黏连断裂事件调用此 API，正常站立不调用。概率须为有限的 0..1，样本为有限的 0..<1；不操作装备或物品实体。实际装备转移成功与失败、完整装备数据保存及拾取幂等性仍须由对应持久实体任务验证。新的受困 episode 才恢复一次判定机会。
