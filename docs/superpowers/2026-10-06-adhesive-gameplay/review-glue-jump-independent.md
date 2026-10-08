# 胶水跳跃增量独立审查

审查者：自然生成验证子代理（未参与跳跃实现）。仅静态阅读，未修改跳跃源码或启动 Gradle。

阅读范围：`GlueJumpMixin`、`mfqm.mixins.json`、`neoforge.mods.toml` 混入配置、`QuicksandPhysics.isHeldByGlue/beforeEntityTick`、`AdhesionPortChecks.verifyGlueJump`，并核对 1.21.11 NeoForge 目标 `LivingEntity.aiStep` 源码。

胶水修复本身未发现 Critical/Major：目标方法内确有 3 处 `jumpInFluid(FluidType)` 与 1 处 `jumpFromGround` 调用，混入在移动前抑制原生向上冲量，未清除 jumping/JumpControl 意图，也未改写已有救援速度。胶水判定在客户端和服务端读取当前真实接触；死亡、创造、旁观与飞行状态通过免疫/豁免条件放行。重力临时修饰在离开胶水后移除。

真实生物回归采用 `pig.tick()` 和 `JumpControl.jump()`，只移除随机游荡目标，检查连续 20 tick 不自行上浮、仍记录挣扎意图、救援可以上升、离开后正常地面跳跃；这比直接调用最终物理函数更能覆盖原生移动顺序。运行结果由主任务提供，本审查未重复运行。

## 已确认并修复：粘鼠板原生跳跃

最初独立静态审查指出 Major：全新涂胶粘鼠板的向上速度限制只位于移动后的 `AdhesionController.prepare`，胶水混入对板返回放行。原生 `jumpFromGround` 先移动超过 `boardUnder` 的接触限制，绕过强约束。主任务随后在实际 JAR 客户端 12:07:40 复现：起始 Y=253.0625、服务器 Y=253.8156999805212、客户端最大 Y=254.54885093363816。

实现者将混入统一改为双侧 `isJumpHeld`：胶水保持原行为，板子以真实当前位置、实际涂层和释放快照判断。耗尽板在预移动阶段立即放行；不同板位置或涂层增加立即重新限制，不依赖下一次 Post 才重置状态。保留 jumping 意图和原有救援速度，创造/旁观/飞行豁免仍有效。

第二次独立审查发现另一个 Major：已释放板的 anchors 为空，普通跳起后再落回原板会命中旧 `dryTicks>0 && anchors.isEmpty()` 新事件分支，重置已释放状态。主任务于 12:12:34 在真实服务器复现，证据为 `tdd-board-relanding-red.log`。最小修复对这一分支增加“尚未释放”限制，同位置、同涂层的正常跳离与落回保持释放；不同板或补胶仍通过各自条件启动新事件。

## 最终增量复审

只读核对 `SinkingState.STREAM_CODEC` 三个新增同步值（`boardReleased`、可空 `episodeBoard`、`previousBoardCharge`）的读写顺序完全对称；可空位置的存在位与坐标对应。自定义网络注册协议已经升级为 3，客户端预测不使用未同步的纯服务器挣扎状态。维度切换 `clear` 将三值归零，并清除旧世界 helper、输入时钟、动画与运动限制。

真实猪回归现已覆盖：新板连续跳跃受限、通过正常生物自救实际削弱、起跳后完整落回仍释放、再次真实起跳、再次落回后原位补胶立即限制、另一块板不能继承旧释放、耗尽板立即正常起跳。客户端阶段同时检查实际服务端/客户端位移，以及已释放板落回后的 effort≥34、连接数为 0，避免只检查第一次跃起峰值而遗漏重新困住。

本次最终静态复审未发现剩余 Critical/Major。新的完整服务器、真实 JAR 客户端与保存重启结果由主任务统一执行记录；此静态结论不代替运行验收，也未宣称尚未执行的测试已通过。
