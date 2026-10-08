# 胶水、粘鼠板、黏连与挣扎：实施计划

状态：维护者于 2026-10-06 批准现有设计并授权完成文档修改后施工。按同目录已修订 design.md 实施；最后收放规则已明确：F 始终挣扎，绳索手持挂接后右键收绳、潜行右键放绳。文档修改已完成，可以进入测试与实现。

## 开发范围

只修改 C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2。保留桌面导出副本与 GitHub 0.6.0 发布。完整移植基线已保存为 Git 提交 d040e9c，开发在 codex/adhesive-gameplay 分支进行。当前 Minecraft 1.21.11 / NeoForge 21.11.45 / Java 21。开发版本沿用 0.6.1-dev。

实现前记录当前变更范围。每个行为先写失败测试并实际执行，再最小实现和重构。现有资源覆盖检查继续保留，新增内容使用明确的预期身份和数量；不能通过删除旧断言或直接根据当前实现生成全部期望来隐藏缺失。

## 架构和协作约定

物理规则使用纯 Java 模型，可在不启动游戏时验证。服务器附件保存瞬态受困、挣扎进度和锚点引用；同步给客户端的是动画与显示快照。实际装备由独立持久实体保存，黏丝辅助实体不保存。

新增黏连的合力由一个服务器控制器计算并封顶。视觉辅助实体只负责锚点同步与生命周期，不各自叠加回拉。现有 tar_treads 的独立回拉整合到新控制器，保留原注册与配置兼容。

客户端保持提交数据不可变。人体姿态使用 NeoForge ArmPose 枚举扩展和渲染状态修饰器，第一人称通过手部与镜头事件表现。玩家蒙层必须采用相同挣扎姿态。非人形生物不强加人体手臂姿势，使用受阻运动与黏连反馈。

模块按依赖顺序推进；无共享文件修改的独立任务可并行。按 superpowers 为实现任务分配新子代理，先做规范审查，再做代码质量审查。共享注册、配置、网络和主物理接入由主代理协调，避免并发覆盖。

## 验证契约

- 静止胶水不持续下陷，挣扎达到阈值后逐步下陷，停止后停止加深；深处有窒息。
- 每次按键只触发一次动作；持有按键、同 tick 重复和冷却期请求不连发。
- F 始终挣扎；挂接后的绳索只有手持右键／潜行右键才能收放，同一次输入不能触发挣扎和放绳。
- 板子初始束缚明显，普通玩家合理挣扎后约 15～25 秒可以挪出；饥饿效率降低但不禁用。
- 合力有上限，连接超距断开，锚点不无限增长；救援能够克服黏连。
- 水清理蒙层，不能因此直接删除有效连接。
- 靴子真实转移，保留完整数据，存档重载仍可取回；重复拾取、满背包、实体创建失败无复制或吞物品。
- 旧方块、配方、装备和救援保留覆盖；新资源与中文提示能被实际游戏加载。

## 批次一：纯规则和新增介质

### 任务 1：黏连距离、合力和生命周期

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/test/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveRulesTest.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveRules.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/tools/verify_adhesive_rules.ps1（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/build.gradle

小步骤目标各 2～5 分钟：先测距离边界、零距离、多个锚点合力上限、失效锚点和最大锚点数；执行 RED；再实现 Profile、Bond、合力和断裂判定；执行 GREEN。纯函数不读取游戏对象或全局配置。

接口意图：材料 profile 描述最大距离、弹性与黏力；由锚点和目标足部位置计算有限向量；超过距离立即返回断开。非有限输入被拒绝，不产生 NaN 速度。

### 任务 2：挣扎、胶水下陷和粘鼠板脱困

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/test/java/com/mfqm/morefunquicksandmod/gameplay/StruggleRulesTest.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/StruggleRules.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveRules.java

先测静止、移动意图、跳跃和专用按键，冷却重复、停止后深度保持、饥饿效率以及板子 15～25 秒目标；实际失败后实现。验证同样的挣扎在板子上削弱黏力、在深胶水中付出下陷代价。

动画相位使用服务器接受动作的时间和时长，不用客户端按键次数决定实际收益。判定尝试移动需要移动意图，不能只依赖实际位移，否则完全被黏住时按方向键不产生挣扎。

### 任务 3：胶水注册、桶和基础资源

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModFluids.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModBlocks.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModItems.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModCreativeTabs.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingMaterial.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/item/ItemPortChecks.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/data/mfqm/recipe/glue_bucket.json（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/assets/mfqm/（新增胶水 blockstate、模型、桶客户端物品和语言键）

先在真实注册/物品检查加入 glue、flowing_glue、glue_bucket 的预期；跑到缺失失败，再实现。胶水使用既有 SinkingLiquidBlock/LegacyBucketItem 路径，统一接入受困与覆盖；不要把只改 FluidType.viscosity 当作实际运动实现。

测试放置、源方块回收、拒绝无权限位置、容器剩余物，以及胶水中的停止/挣扎/窒息行为。

### 任务 4：可回收、补胶的薄板

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/block/StickyBoardBlock.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModBlocks.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModItems.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModCreativeTabs.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/item/ItemPortChecks.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/data/mfqm/recipe/sticky_board.json（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/data/mfqm/loot_table/blocks/sticky_board.json（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/assets/mfqm/blockstates/sticky_board.json（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/assets/mfqm/models/block/sticky_board*.json（新增）

先测试空板不束缚、涂胶板束缚、右键补满正确返回桶、回收重放保存涂层，再实现约 1/16 厚底板和涂层状态。使用方块状态与物品 BLOCK_STATE 数据组件保存涂层，优先避免无必要的方块实体。

验证支持面移除、破坏、流体相邻与连续拼接。涂层按使用和挣扎消耗，空板保留；拆板必须清理对应锚点并释放留存装备。

批次检查点：纯规则通过、新介质和板子的真实资源/交互测试通过；提交简短结果给维护者，不额外要求已经授权的同类操作批准。

## 批次二：服务器物理与装备

### 任务 5：挣扎输入与同步

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/network/ModNetworking.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/ClientNetworking.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/MfqmClient.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingState.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/QuicksandPhysics.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/PhysicsPortChecks.java

先测有效受困请求、无受困请求、死亡、冷却、重复、菜单中按键与按住不连发。再添加独立挣扎意图包与移动意图，服务器只接受玩家自身的操作，验证真实接触和频率。更新协议版本与同步编码，避免同版本旧包结构静默误解。

动画/进度同步包括目标自身和附近观察者。离开、死亡、维度变化、瞬移或失效时停止动作并清理瞬态状态。新输入字段不作为永久世界状态保存。

默认挣扎键 F 始终用于挣扎。绳索必须手持；首次右键挂接，挂接后右键收绳、潜行右键放绳。撤掉原 F 放绳输入路径，并同步更新映射、中文提示与测试。保留手持工具时 R 收绳／X 断开辅助操作，不允许 F 同时放绳。

### 任务 6：锚点、黏连合力与原物理接入

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesionController.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/AdhesiveTetherEntity.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/SurfaceEffectEntity.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModEntities.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/QuicksandPhysics.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingState.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/ModConfig.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/PhysicsPortChecks.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/EntityPortChecks.java

先测实际锚点上限、超距、锚点移除、瞬移、区块卸载和救援仍能拉动，测试旧焦油回拉不会叠加。再接入统一控制器，综合现有阻力、胶水/板子规则与 capped 合力。

介质映射显式列出：焦油、蜂蜜、胶水、sinking_slime、mucus 与黏稠泥潭；普通干流沙不画黏丝。按材料免疫规则和目标状态处理。板子筛选玩家/中小型 Mob；创造、观察者和大型豁免对象不受困。

### 任务 7：留靴的真实装备转移与存档

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/StuckBootsEntity.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModEntities.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesionController.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingState.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/EntityPortChecks.java

先以命名、损耗、附魔靴子测试真实转移、重复触发、创建失败、死亡后、存档重载、介质消失和重复取回。以确定的随机输入测试 0%、100% 和默认概率规则，不能用偶然通过的随机测试证明无丢失。

一次受困最多判断一次，只有强拉伸/挣断时触发。先确保世界接受留存实体，再清空实际脚部装备；失败保留原装备。靴子对象持久化原 ItemStack，视觉无装备副本奖励。

### 任务 8：空手、长棍和绳索取靴

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/StuckBootsEntity.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/item/ConnectorItem.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/ConnectorEntity.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/registry/ModEntities.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/entity/EntityPortChecks.java

先测近距空手、长棍范围、绳索命中/回收，远距、隔墙、错误工具和重复操作被拒绝；满背包也不吞物品。再新增装备取回分支并保留角色救援/支撑棍行为。

同步落实新的绳索手持交互：挂接后右键开始收绳，潜行右键放绳，松开停止收放；右键已有连接不再无条件丢弃它。移除原潜行自动收绳对新放绳操作的反向干扰；收起工具不允许继续控制。测试真实手持、放手、切换物品、R/X 辅助、F 无放绳副作用和靴子回收。

解绑或移除介质释放正常物品；区块卸载不释放重复掉落。每次转移保持世界装备总数不增加。

批次检查点：真实服务器验证束缚、救援、装备和保存重载；记录重要边界结果，发现偏离设计则先修正。

## 批次三：视觉、动作与自然生成

### 任务 9：AI 胶水贴图与玩家蒙层

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/tools/refresh_adhesive_textures.py（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/docs/adhesive-textures/（新增来源、许可说明、预览与校验）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/assets/mfqm/textures/（新增胶水、板子、黏丝和胶水 10 级蒙层）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/MuddyPlayerLayer.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/LegacyCoatingModel.java

应用 imagegen 技能与既有用户授权的脚本缩放/UV/循环处理流程。生成白色半透明胶水材料及底板，沿用游戏现有像素风与更新后的材料质感。

先补透明度、逐帧接缝、UV 蒙层与覆盖递增验证；再烘焙可用图片与循环动画。保留原泥、焦油、蜂蜜的已满意贴图。新增文件的原始生成来源不打入运行 JAR。

### 任务 10：第三人称、第一人称与镜头

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/StruggleClient.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/StrugglePose.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/StruggleEnumParams.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/MfqmClient.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/LegacyCoatingModel.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/ModConfig.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/META-INF/enumextensions.json（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/META-INF/neoforge.mods.toml
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/ClientPortChecks.java

先测相位起止、不同深度、镜头开关、宽/细臂、离开后复原与两名玩家不串动作，再实现。正确使用 1.21.11 ArmPose 的双布尔参数构造器；不照抄旧版本枚举签名。

浅层交替抬腿、拔脚；深层扭身撑拉；手持和实际使用救援工具不被强制终止。皮肤、盔甲与蒙层用同一姿态，第一人称手臂/蒙层同时变化，镜头只有轻微、可关闭的起伏。

### 任务 11：拉丝、足踝黏膜与装备显示

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/AdhesiveTetherRenderer.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/StuckBootsRenderer.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/HelperRenderer.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/MfqmClient.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/client/ClientPortChecks.java

先补离地脚端点、动作相位、透明纹理及 renderer 实际加载检查，再实现渐细、轻微下垂、回缩的多条拉丝和随腿动作的黏膜。胶水白半透明、蜂蜜琥珀、焦油黑褐、泥潭短粗。

所有提交闭包只捕获不可变快照；目标旋转/移动/抬脚时端点跟随足部。用游戏截图验证角色腿脚、盔甲、板子拼接和成片胶水，不能只验证注册数。

### 任务 12：胶水池与遗迹板子

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/worldgen/GluePoolFeature.java（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/worldgen/ModWorldgen.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/worldgen/TerrainBiomeModifier.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/worldgen/DesertTombPiece.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/ModConfig.java
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/data/mfqm/worldgen/configured_feature/glue_pool.json（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/data/mfqm/worldgen/placed_feature/glue_pool.json（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/data/mfqm/loot_table/chests/desert_tomb*.json
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/java/com/mfqm/morefunquicksandmod/validation/ModPortChecks.java

先测适用生物群系、浅深池、关闭配置、区块写入范围、地形密封和遗迹实际放置，再实现独立池生成，不改变旧池的互斥选择比例。森林/沼泽候选区块使用可配置中等频率，小池深浅都有。

本模组遗迹放少量涂胶板，宝箱可提供板子/材料。处理支撑面、空气空间和当前结构边界，不遮住必需箱子、刷怪器或破坏入口。既有已生成区块不做自动回填。

批次检查点：实际客户端画面和独立世界生成结果可供查看，说明仍需真人试玩的手感边界。

## 批次四：验收与交付

执行状态：任务 1～13 已完成。真实服务端 66 组、数据包重载、跨进程留靴、默认概率自然池观察及最终安装 JAR 客户端验收全部通过；六张实拍已检查并保存。额外的跳跃和落回缺陷先复现再修复，记录见 final_report.md 和 review.md。

### 任务 13：中文文案、全量回归和审查

文件：
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/README.md
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/docs/adhesive-gameplay.md（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/assets/mfqm/lang/zh_cn.json
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/assets/mfqm/lang/en_us.json
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/src/main/resources/assets/mfqm/lang/ru_ru.json
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/tools/verify_port.py
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/tools/smoke_port.py
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/tools/smoke_client_port.py
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/docs/superpowers/2026-10-06-adhesive-gameplay/review.md（新增）
- C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/docs/superpowers/2026-10-06-adhesive-gameplay/final_report.md（新增）

补中文提示、物品说明、配置说明和实测使用方法，明确这是开发版本。新注册与资源断言有明确身份，旧内容覆盖不能减弱。评审先检查设计符合性，再检查装备数据、生命周期、性能与渲染质量；阻塞问题修复后重跑相关测试。

依次执行：
1. tools/verify_sinking_motion.ps1 与新增纯规则检查。
2. 新贴图 verify 与原 tools/refresh_textures.py --verify。
3. python tools/verify_port.py。
4. Java 21 下 gradlew.bat build --console=plain。
5. python tools/smoke_port.py --reload（隔离测试世界，包含新真实玩法和保存重载检查）。
6. python tools/smoke_client_port.py（隔离存档，新增胶水、薄板、动作与黏连验收场景）。
7. 视觉查看截图，检查拼接、透明、腿脚端点、动作/盔甲/覆盖层和配置关闭。

产物为 C:/Users/Administrator/.codex/worktrees/2d32/MFQM-NeoForge-26.2/build/libs/MFQM-neoforge-1.21.11-0.6.1-dev.jar。记录文件大小与 SHA256，提供本地可点击链接。报告代码验证与真人手感试玩的区别。默认保留当前工作目录，不提交桌面副本、不发布 GitHub、不开 PR。
