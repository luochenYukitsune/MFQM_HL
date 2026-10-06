# MFQM 1.7.10 与 1.21.11 内容对照

审查日期：2026-10-05。当前目标：Minecraft 1.21.11、NeoForge 21.11.45、Java 21。

> 本文件保留移植前的缺失审查快照。2026-10-06 已实现内容和验收结果见[本次移植报告](../2026-10-06-gameplay-port/final_report.md)；下文“当前为空”等描述指审查当时状态。

## 结论：当前只有基础框架，原版玩法尚未移植

截图里的沙子是原版占位物品。当前 `ModBlocks`、`ModItems`、`ModFluids`、`ModEntities` 只有空的 DeferredRegister，没有任何实际条目。`ModCreativeTabs.java:24` 在物品注册为空时明确添加 `Items.SAND`，因此这个页面准确反映了当前项目状态。

仓库当前 `HEAD` 中的 `ModBlocks`、`ModItems` 也已经为空，旧模型提供器写着后续阶段 TODO。此次对照没有证据表明完整玩法在升级到 1.21.11 时被删除；此前完成的修复是构建、加载、配置、数据生成与版本 API 适配，不能当作原模组的完整移植。

现有五种伤害类型和十二个分类标签是基础数据。没有接触检测、下沉、救援等运行逻辑来消费它们；手动 `/damage` 验证和客户端启动成功都不能证明流沙玩法存在。

直接证据：

- [当前方块注册器](../../../src/main/java/com/mfqm/morefunquicksandmod/registry/ModBlocks.java)
- [当前物品注册器](../../../src/main/java/com/mfqm/morefunquicksandmod/registry/ModItems.java)
- [当前流体注册器](../../../src/main/java/com/mfqm/morefunquicksandmod/registry/ModFluids.java)
- [当前实体注册器](../../../src/main/java/com/mfqm/morefunquicksandmod/registry/ModEntities.java)
- [创作栏占位逻辑](../../../src/main/java/com/mfqm/morefunquicksandmod/registry/ModCreativeTabs.java)

## 来源和核对方法

参考目录为用户指定的 `D:/download/MoreFunQuicksandMod-1.1.1-1.7.10 (2)`，`mcmod.info` 标明 MFQM 1.1.1 / Minecraft 1.7.10。该目录包含 **161 个编译后的 `.class` 和 300 个资源文件**，没有作者的 `.java` 开发源码。

使用本机已有的 Vineflower 1.11.2 反编译得到 **158 个 Java 文件**，三个消息 Handler 内部类合并到父类。审查分为注册/物品/配方/配置、方块/流体/沉陷逻辑、实体/客户端/生成，以及公共代理/三个网络消息。注册和配方调用数另用 JDK 21 `javap -c -p` 读取原 `MFQM.class` 复核，结果一致。资源清单使用相对路径及 SHA-256 对照。

第一轮的 `CustomWorldGen.generateSurface`、`GenerateDesertTombs.generate5` 因堆内存不足未恢复。随后单线程、3 GB 堆重试两类成功，退出码为 0，新输出中没有反编译失败标记。保留第一轮供既有行号引用，恢复版本放在独立目录；158 个文件这一计数不重复包含重试版本。

本次没有在 1.7.10 中运行原模组。反编译文本不是作者原始源码，也不保证可重新编译；恢复输出仍可见 boolean/int 重建异常，庞大生成方法中的摆块坐标和概率需具体移植时逐项验证。旧目录未被修改，反编译材料未加入新版源码或发布 JAR。

本地证据（`run/` 已被 Git 忽略，以下文件只存在于本次工作区）：

- [类、语言和资源哈希清单](../../../run/1.21.11/legacy-audit/inventory.json)
- [注册、物品、配方和配置详细核对](../../../run/1.21.11/legacy-audit/items-findings.md)
- [沉陷和介质行为详细核对](../../../run/1.21.11/legacy-audit/physics-findings.md)
- [实体、客户端、方块实体和生成详细核对](../../../run/1.21.11/legacy-audit/entities-findings.md)
- [第一轮反编译主类](../../../run/1.21.11/legacy-audit/decompiled/MoreFunQuicksandMod/main/MFQM.java)
- [恢复的 CustomWorldGen](../../../run/1.21.11/legacy-audit/recovered-worldgen/CustomWorldGen.java)
- [恢复的 GenerateDesertTombs](../../../run/1.21.11/legacy-audit/recovered-worldgen/GenerateDesertTombs.java)

## 实际注册清单

旧版有 **49 个方块注册、43 处独立物品注册、13 个 Fluid 对象注册、16 类实体注册**；当前这些对应条目均为 **0**。这些数不是创造栏格数：方块注册通常还带有 ItemBlock，多个外观和状态共用旧 metadata，部分 helper 刻意隐藏，部分食物放在原版食物分类。

49 个方块全部在 preInit 无条件注册。独立物品实际为 40–43：38 个在 preInit 恒注册、两个前置靴在 postInit 恒注册；Emptycomb/HoneycombItem 在没有 BiomesOPlenty 的回退分支注册，Wading Boots 在未复用外部靴子时注册。16 类实体中十类恒注册，两种触手受独立开关控制，四种 Blob/Slime 受 InitMobs 总开关控制。

以下保留旧版注册 ID 以便对照。现代端需要明确制定小写 Identifier 和 BlockState/Data Component 映射，不能原样使用带空格或大写的旧名称。

### 49 个方块 ID

- `Mud`、`Bog`、`SoftSnow`、`DryQuicksand`、`SoftQuicksand`、`Morass`、`WetPeat`、`HPeat`、`BrownClay`、`Wax`。
- `Quicksand`、`SandstoneTrapBlock`、`JungleQuicksand`、`LiquidMire0`、`LiquidMire1`、`SinkyLiquid`、`SinkingSlime`、`Mucus`、`Mire`、`Moor`。
- `HardenedClay`、`SinkingClay`、`TangleRootMoss`、`DenseWebbing`、`Tar`、`Larvae`、`CorruptedSand`、`SwallowingFlesh`、`Acid`、`Slurry`。
- `Gas`、`SoftGravel`、`Honey`、`SolidHoney`、`Honeycomb`、`LiquidChocolate`、`ChocolateBlock`、`SinkingRug`、`LureBlock`、`BlossomBlock`。
- `BlossomBlockSlab`、`TempVore`、`MeatWall`、`TempMeat`、`WaxWood`、`CustomLilyPad`、`MoorGrass`、`Tendrils`、`LeavesPile`。

重要状态包括四阶泥、Brown/Mineral Clay、Soft Snow/Ash、Soft Gravel/Silt Sludge、16 色 Quick-Rug、蜂巢三态、Plant Wall/半砖部件、肉壁部件、蜡木方向、Moor/Bog/Cranberry 草、四态 Tendrils。它们不能只用同名普通立方体代替。

### 43 处独立物品注册 ID

- 容器 13 个：`BucketOfLiquidBog`、`Bucket of Clay`、`BucketOfQuicksand`、`BucketOfSand`、`BucketOfMire`、`BucketOfSlime`、`BucketOfMucus`、`BucketOfTar`、`BucketOfAcid`、`BucketOfChocolate`、`BucketOfChocolatePowder`、`BucketOfHoney`、`BucketOfSlurry`。
- 工具、装备和材料 15 个：`Fertilizer`、`LongStick`、`Rescuing`、`Rope`、`Cable`、`Coil`、`Hook`、`Filter`、`GasMask`、`GrapplingHook`、`GrapplingHookBroken`、`LiqGunItem`、`WaxPiece`、`LifeJacket`、`PeatItem`。
- 食物八个：`LarvaRaw`、`LarvaCooked`、`Cranberry`、`Donut0`、`Donut1`、`Donut2`、`Donut3`、`Donut4`。
- 饮品/投掷物两个：`Potion`、`SPotion`。
- postInit 五个：`Emptycomb`、`HoneycombItem`、`wadingBoots`、`preWadingBoots0`、`preWadingBoots1`。

Clay Bucket 一个 ID 有两种变体；Chocolate Powder Bucket 是普通材料物品，不能倒出巧克力粉流体。Potion 一个 ID 包含 Bottle of Mire、Sinking Potion、Hot Chocolate；SPotion 独立。Stable Mire 与普通 Mire 共用桶，Sinky 没有专属旧版桶。

### 13 个 Fluid 注册名

`LiquidMire`、`LiquidMireS`、`Bog`、`Slurry`、`JungleQuicksand`、`SandFluid`、`Tar`、`SlimeFluid`、`AcidFluid`、`MucusFluid`、`LiquidChocolate`、`Honey`、`SinkyFluid`。

旧类名 QuicksandFluid 的实际注册名是 JungleQuicksand；普通 QuicksandBlock 是固体沉陷方块。路线图此前把两者各算一种流体，得到错误的 14。现代 source/flowing 实例和 FluidType 的总条目数也不能与旧 Fluid 对象数直接比较。

### 16 类实体及四类方块实体

- 五种生物：Bee、VoreSlime、MuddyBlob、SandBlob、TarSlime。
- 两种捕获辅助：Tentacles、MudTentacles。
- 四种旧客户端临时表现：Bubble、TarTreads、SlimeHole、LongStick。
- 三种连接/救援工具：Rope、Hook、Rescue。
- 两种投射物：SPotion、LiqBall。

旧版四类 TileEntity 为 Lure、Blossom、Larvae、Meat，另有三个动态渲染器。公共侧 `GameRegistry.registerTileEntity` 仅注册 Lure；其他三类出现在客户端 `ClientRegistry.registerTileEntity` 调用中。新版目前没有任何对应 BlockEntityType 或实现；不能把旧客户端注册方式直接照搬到专用服务器。

## 未移植的玩法

### 沉陷、缺氧、装备与救援

旧版根据介质、沉入深度、移动、转身、跳跃、蹲下、体型和难度计算阻力、吸附及挣扎结果，具有特殊碰撞高度和状态传播。Mire/Morass/SinkingClay 踩踏会破坏表层，Wax 随温度软化，Mud 根据深度状态和邻域决定可站立的碰撞面。当前项目没有这些逻辑。

还有玩家/生物空气、按介质区分的伤害和水下呼吸豁免、盔甲重量、36 槽背包重量、靴子支撑、救生衣浮力、长杆探底、空手救援、绳/钩的锚点和拉力、断绳/断钩及工具修复。旧代码的客户端运动和消息处理需要重建为现代服务器权威流程。

Gas 主要按浓度施加饥饿、恶心、迟缓、中毒等原版效果，并识别防护头盔；不能从名称或当前 `gas_asphyxiation` 数据推断原版所有气体都使用统一空气计时器。Liquid Mire、Stable Mire、Larvae、MeatHole、VoreHole 等也存在空气路径例外和各自伤害。

### 次级物品交互和配方

原 class 中有 **55 处有序合成、31 处无序合成、8 处熔炼调用**。这不是最终配方总数：Quick-Rug 的八处无序调用在 16 色循环中执行，单这一项便有 128 个配方，其他调用还受条件分支影响。新版没有相应配方、掉落或燃料实现。

容易漏掉的内容包括液体枪从世界源块装弹并发射可放置液体的弹丸、Slurry Bucket 肥田、Sinking Potion 对仙人掌生成 Mucus Blossom、投掷药水放置 SinkyLiquid、SinkyLiquid 转化周围普通方块、两阶段前置靴加工成 Wading Boots，以及泥炭/沥青燃料。

`PotionStuck` 是瞬时扩展实体减速状态，未注册为独立 Potion/MobEffect，NBT 存取为空。`Rescuing` 是隐藏的临时手救援道具，离开选中栏位或掉落会清理，不能当作普通可合成的 Rescue Hook。

### 生物与客户端

四种 Blob/Slime 有不同吞没过程、逃脱、减益、泥污覆盖、攻击、掉落及生成条件。MuddyBlob/TarSlime 有夺取手持工具、保存、死亡返还机制；SandBlob 有沾水/雨伤害、减伤和分裂。Bee 有飞行、追逐和蜂巢刷怪机制。当前没有生物实现。

客户端缺少第三人称身体泥污、第一人称手泥污、泥污衰退/清洗、缺氧气泡 HUD、介质遮罩、视角切换、气泡/洞纹/沥青拉丝、绳线与钢缆、长杆动画、触手、动态肉壁/幼虫/花部件。旧版只有 Bee 和共享 VoreSlime 两个独立 Model 类，其他类型大量使用程序几何、物品渲染或粒子，不是 16 个独立模型。

### 自然生成与兼容

缺失范围包括各类泥潭/流沙/黏土/沥青坑、蜡树与蜡地、蜂巢和奖励箱/刷怪笼、蜘蛛巢与刷怪笼、混合 Moor/Morass 草地、黏液花多层结构、肉/酸陷阱、沙漠墓多房间/奖励/刷怪笼，以及独立的原版沙漠神殿 TNT 替换陷阱。

恢复的主世界方法确认，原版丛林也会生成蜂巢和黏液花；Moor/Morass 是含水、草层或桦木的成簇地貌，软流沙还生成周围岩石/崖壁。多数分支共享生成互斥状态，不能机械变成各自独立的均匀池。高度常按 `height - 62` 调整，但若干黏土分支仍有固定高度，原参数也不是对全部特征统一加偏移。

旧版还针对 BiomesOPlenty、TwilightForest、TheBetweenlands、AbyssalCraft、Wildycraft、AdventOfAscension、ExtraUtilities Deep Dark 做生成适配，以及若干外部装备、配方、乘骑兼容。新版没有这些行为。第三方模组是否存在可用的 1.21.11 版本需单独核实，旧维度 ID 和名称不能直接移植。旧 End 和部分 AOA 维度生成方法为空，也不能把入口存在当作已实现玩法。

## 资源缺失

旧 `assets/morefunquicksandmod` 共 300 文件（新版命名空间为 `mfqm`）：

- 247 张 PNG：logo 一张、方块 162 张、物品 54 张、实体 25 张、装备五张。除 logo 外的 **246 张玩法贴图当前全部缺失**。
- 48 个动画 `.mcmeta`，当前全部缺失。
- 两个 bee OGG 和一个 sounds.json；logo 和两个 OGG 与当前文件 SHA-256 一致，已保留。
- 英文、俄文 `.lang` 各一个，各 242 个独立键。当前只有英文 JSON，含 91 个基础/预留键；俄文未带入，语言键也不能作为已实现内容证据。

当前 `assets/mfqm` 只有五个文件：logo、sounds.json、英文 JSON、两个 bee OGG。没有 `textures/`、方块/物品模型或实际物品定义。旧贴图名称、UV、动画与现代资源结构需要一起适配，单纯复制并不能生成可用物品。

## 配置差异

旧版声明 82 项配置：Mods 九项、Terrain Generator 39 项、Mobs 五项、Items 八项、Options 21 项。当前声明 65 项：SERVER 59、CLIENT 六项，COMMON 的 compat 为空。两者不是完整一一映射。

- 缺失或合并的生成项：独立 Hardened Clay 与路径开关、Nether waste pits（不是现有 Wasteland slurry 开关）、神殿 TNT 替换、BoP marsh 改造、沼泽水色。
- 缺失玩法总开关：InitMobs 和 QSHandRescue。
- 九个旧兼容/世界模式项未实现。三项旧 DataWatcher 槽位属于过时实现细节，应由现代状态系统替代，不应机械恢复数字槽位。
- 四种 Blob/Slime 的自然生成、HookAsRider、强制第一人称的旧声明默认 true，当前默认 false；这些差异尚未经过玩法设计/测试。
- 原 Turn* 项通常控制配方，现有 enable* 需要明确是否保持该语义。AltitudeShift 的旧声明称水位，默认 62 / TerraFirmaCraft 144，不能未经核对就解释为相对偏移。

本次记录这些差异，没有修改配置键、默认值或玩法平衡。

## 本次修订和后续实现顺序

已按旧版证据修正 [玩法移植路线图](../plans/2026-07-29-mfqm-neoforge-port-plan.md)，保留九阶段和 41 个任务编号，并补齐上述资源、状态、装备、生成、客户端和配置验收项。主要纠正了：14 → 13 旧 Fluid 对象、Tent → Tendrils、Wax Leaves → Dead Leaves Pile、甜甜圈五种真实变体、Rescuing 的隐藏性质、MudBall 的外部引用、完整实体清单及两模型/四方块实体事实。

下一步应先实现一条真正可玩的流沙路径：方块与物品注册、原贴图/现代模型、创作栏、身体与眼部接触、下沉/挣扎/缺氧、退出恢复及双人救援验证；通过后再扩展其他介质和装备。仅把所有名字注册成普通方块，会填满菜单但无法恢复原模组玩法。

本次产出是内容对照和路线图修订；没有新增玩法，没有修改游戏代码或重建 JAR。文档结构、注册清单计数、文件引用和差异格式另行校验。
