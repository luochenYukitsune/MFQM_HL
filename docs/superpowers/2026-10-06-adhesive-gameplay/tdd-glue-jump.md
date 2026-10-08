# 胶水跳跃浮升回归

## 真实游戏 RED

2026-10-07 11:59:43，安装工作目录构建 JAR 的隔离 NeoForge 客户端先完成四幅场景与真实 F 网络检查，再通过 `ClientInput.tick()` 持续提供 60 tick 跳跃。该输入经过原版 `LocalPlayer.applyInput()`、流体跳跃和实际位移，未伪造服务器速度或挣扎状态。

日志保存于 [tdd-glue-jump-red.log](tdd-glue-jump-red.log)：

```text
MFQM_ADHESIVE_JUMP_OBSERVED startY=252.75 serverY=253.95890626252034 maxClientY=253.95890626252034 effortBefore=0.0 effortAfter=0.25
```

持续跳跃使玩家浮升约 1.209 格，违反深胶水中挣扎可能加深下陷的设计。先前的 F 场景与静止深度检查未覆盖此输入。启动器捕获真实 ERROR 并仅结束自身 Java 进程。

## 原因与最小修复

本地 1.21.11 NeoForge 源码中，`ILivingEntityExtension.jumpInFluid()` 无条件增加约 0.04 的 Y 速度，不读取 `FluidType.canSwim(false)`；`LivingEntity.aiStep()` 随后执行实际 travel 位移。服务器 Post 清速度不能追回客户端已经产生的位置。

通过 NeoForge 内置 MixinExtras 的 `WrapWithCondition`，仅在 `QuicksandPhysics.isHeldByGlue()` 成立时阻止 `aiStep()` 内原版流体和地面跳跃的上升冲量。`javap` 核对目标为三个流体调用与一个地面调用。此条件适用于双方及普通生物，保持原始跳跃意图、饥饿与挣扎规则，并保留已有救援上升速度。创造、旁观、飞行和介质免疫目标不受限制，离开胶水后恢复普通跳跃。

此处没有流体跳跃专用的 NeoForge 事件，因此使用窄范围调用条件，而不清空玩家输入或全局取消 jump。

参考：[NeoForge Mixin 元数据登记](https://docs.neoforged.net/docs/gettingstarted/modfiles/)、[MixinExtras 提供方说明](https://github.com/LlamaLad7/MixinExtras)、[WrapWithCondition 调用与可组合条件](https://javadoc.io/static/io.github.llamalad7/mixinextras-common/0.5.4/com/llamalad7/mixinextras/injector/v2/WrapWithCondition.html)。实际环境使用 NeoForge JAR 内附 0.5.3，未额外捆绑依赖。

## 新增验收

- 真实客户端持续 space 60 tick：深胶水不可浮升，原始意图仍增加服务器 effort。
- 真实客户端创造模式不飞行持续 space 20 tick：正常跳跃，零黏连及零挣扎消耗。
- 真实服务器普通猪：保留 vanilla `Mob.aiStep`、travel、JumpControl，仅移除随机游走目标；连续 20 tick 流体跳跃不可浮升，意图仍计入 effort。
- 同一猪的实际救援位移可以向上，移到干地后原版地面跳跃仍可用。

## 胶水真实游戏 GREEN

主任务重新构建并安装 JAR 后，2026-10-07 12:03:57 观察到持续跳跃的起始、服务器及最高客户端 Y 均为 252.75，服务器 effort 从 0 增至 0.25。12:03:59 创造模式验证从 251.280996 上升至最高 253.442149，黏连和 effort 都为 0。全部六幅真实渲染截图、真实 F 网络和持续跳跃流程完成，启动器退出码 0。完整日志保存于 [tdd-glue-jump-green.log](tdd-glue-jump-green.log)。

## 粘鼠板相邻问题 RED 与修复

独立复审又发现粘鼠板只在 Post 清速度，不能阻止客户端原版地面跳跃先移动离开薄板。2026-10-07 12:07:40 真实持续跳跃回归记录：

```text
MFQM_ADHESIVE_BOARD_JUMP_OBSERVED startY=253.0625 serverY=253.8156999805212 maxClientY=254.54885093363816 effortBefore=0.0 effortAfter=0.25
```

最高客户端浮升约 1.486 格，未达到设计的削弱程度。完整日志保存于 [tdd-board-jump-red.log](tdd-board-jump-red.log)。

沿用同一个窄范围调用条件，将仍束缚目标的涂胶板加入 `isJumpHeld()`。附件同步释放标志、板位置和上次涂胶量；物理接触优先读取真实方块，使耗尽板立即恢复跳跃，新板或原位补胶第一 tick 不会继承陈旧释放状态。已削弱到可逃离的板即使仍有少量涂胶，也允许正常跳跃。附件协议随之升级至 3，双方需要相同版本。

新增真实客户端检查包括全新板持续跳跃、真实 F 累计削弱后仍有涂胶板的普通跳跃、结束旧 episode 后耗尽板的普通跳跃。真实猪的回归保留原版 JumpControl 与 travel，通过普通生物自救的 80 tick 节奏积累到释放（仅推进隔离世界时钟，不写 effort），覆盖释放、原位补胶、新板和涂层耗尽时的实际位移。

独立审查进一步发现旧 `newBoard` 分支会将跳起后的短暂无接触视为新捕获，导致已挣脱角色落回同一涂层又被重新困住。2026-10-07 12:12:34 的真实服务器猪回归在实际跳起、完整落回后失败：`refill starts with an existing release snapshot`，此时还未补胶。日志保存于 [tdd-board-relanding-red.log](tdd-board-relanding-red.log)。最小修复仅将“短暂离开且没有 anchors”的新 episode 判断限于尚未挣脱的状态；位置改变、涂胶量增加以及真正结束旧 episode 的原有重置仍保留。真实回归强化为落回后再次起跳，客户端也要求落回后 effort 保持 34、anchors 为 0。

2026-10-07 12:13:52 主任务重跑真实服务器通过 66 组检查，新增真实猪的板束缚、自动挣扎释放、完整落回、第二次跳跃、原位补胶、新板和涂层耗尽全部通过；12:13:55 reload 通过，12:13:58 跨进程存档重启取回留靴通过，未出现 ERROR。日志保存于 [tdd-board-jump-server-green.log](tdd-board-jump-server-green.log)。

粘鼠板安装 JAR 客户端最终结果见下方最终 GREEN；本文不将编译或静态检查视为游戏验证通过。

## 真实客户端验收夹具的共享磨耗 RED

最终客户回归首先在 `phase=21` 弱化阶段超时。未降低 34 effort 的门槛或伪造进度，而是加入真实服务器诊断，记录 raw F 请求、effort、涂胶量、anchors、饥饿、位置与服务器时间。2026-10-07 12:21:20 第 300 客户端 tick 的 effort 为 23.25、涂胶量 2（单个玩家此时应剩 3）；12:21:25 第 400 tick 的 effort 为 30.25、涂胶量已经 0、材料为空、anchors 为 0。后续 episode 正常结束，无法再累积至 34，12:21:35 出现明确超时。完整证据和堆栈保存于 [tdd-board-shared-fixture-red.log](tdd-board-shared-fixture-red.log)。

根因是截图夹具中牛的位置 X=61.25，宽度 0.9，其脚部包围盒最小 X≈60.8，`boardUnder()` 同样先选到玩家使用的 `(60,253,0)` 板。即使牛设置 NoAI，普通生物的自救仍按设计发生并消耗共享涂层，因此玩家约 30 effort 时涂层已经合法耗尽。生产玩法无需改变。

夹具修复只在四张胶水/板子照片完成后进行：将牛移到新建的独立受支撑板 `(65,253,4)`，牛的脚部中心 `(65.5,253.0625,4.5)` 保证整个脚部包围盒仅落在该板上；随后对玩家目标板补满涂层再执行边界测试。原来的照片场景和共享涂层消耗规则保留。

## 最终安装 JAR 客户端 GREEN

2026-10-07 12:25:02，主任务完成重建并将产物安装至隔离实际 NeoForge 游戏的 `mods` 目录，未注入开发类目录。实际 JAR SHA-256：

```text
edfb3dc5a272ae3fc2fbbde533b434c006e7ab54cac376163282b869dd45e250
```

完整日志保存于 [tdd-installed-client-green.log](tdd-installed-client-green.log)，验证脚本成功退出并确认六幅真实渲染截图。60 个实际模型案例和原始短按/重复输入检查通过；胶水持续跳跃、创造免疫及全新板束缚全部通过。通过真实 F 网络累积 effort 至 34 后，仍有涂胶板上的跳跃最高 Y 约 254.3147、anchors 为 0，落回后 release 保持；耗尽板跳跃时 effort/anchors 均为 0。

隔离牛后，第 300 tick 的玩家 effort 为 23.25、涂胶量为 3，第 400 tick 的 effort 为 31.25、涂胶量为 1。这与正常个人磨耗一致，并与旧夹具在同阶段提前减掉额外涂层的 RED 形成直接对照。生产消耗规则没有修改。

本次新增增量审查的胶水跳跃、板跳跃及已挣脱板落回重新困住三个 Major 均有独立检查、真实失败和修复后真实游戏/服务器通过证据。最终静态复核未发现尚未处理的 Critical/Major。真实服务器普通生物、双侧客户端输入、救援、创造模式、原位补胶、新板和耗尽板均已验证；多玩家同时救援、第三方动作/属性模组兼容仍需另外的实际环境验收。
