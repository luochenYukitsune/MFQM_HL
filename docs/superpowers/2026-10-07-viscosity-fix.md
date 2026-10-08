# 实际行走黏度回归

用户在自己的 NeoForge 21.11.45 实例中反馈所有介质像水。只读核对该实例日志与 JAR SHA-256，确实加载上一轮最终 0.6.1-dev（edfb3dc5a272ae3fc2fbbde533b434c006e7ab54cac376163282b869dd45e250）。当时为创造模式；上一版明确给予创造免束缚，不能据此断定未更新。

独立代理与本地 21.11.45 源码复核确认另一个真实缺陷：液体原版 travel 固定使用水／岩浆加速度，MOVEMENT_SPEED 属性无法充分控制；客户端先位移，服务端 Post 才施阻尼。之前验收未覆盖多介质真实连续 W 位移。

依据：[NeoForge FluidType 源码](https://github.com/neoforged/NeoForge/blob/1.21.11/src/main/java/net/neoforged/neoforge/fluids/FluidType.java)、[LivingEntity 补丁](https://github.com/neoforged/NeoForge/blob/1.21.11/patches/net/minecraft/world/entity/LivingEntity.java.patch)，并以本地缓存的准确目标源码为最终依据。

修复目标延续已批准的黏度设计：胶水最黏，焦油其次、蜂蜜较轻；源、流动薄层、浅池与深池都由实际接触和深度控制。静止胶水保持深度、挣扎单次下陷及救援不会被预测覆盖。原版水不受影响。用户选择创造地面也受困，`creativeGroundPhysics` 默认开启；飞行／旁观仍免疫，创造免伤。无新增图片。

用户指出此前混合测试最后几项在飞行。新版主验收已分离飞行对照，只包含生存与关闭飞行的创造；测量的每个客户端和服务端 tick 都断言 `flying=false`，结果显式记录两端标志。飞行对照仅在独立 `--flight-controls` 调用中运行。

工作步骤：

1. 独立安装 JAR、真实 ClientInput W 位移测试先记录 RED，不能每 tick 重置位置或速度。
2. 在真正 move 前限制加速度与惯性，双侧共享运动规则，避免服务端 Post 二次阻尼；保留服务端饥饿、涂层与装备副作用。
3. 覆盖浅／深／流动、水对照、持续跳跃、救援、离开恢复；独立复审。
4. 通过构建和实际安装 JAR 验证后生成明确的新版本 JAR，仅修改工作目录。

已完成，最终版本为 0.6.2-dev；非飞行主回归、跳跃/挣扎/救援/受击、71 组服务端检查及实际汉化加载通过。产物哈希和原始日志见[最终验收](2026-10-07-viscosity/final_report.md)。
