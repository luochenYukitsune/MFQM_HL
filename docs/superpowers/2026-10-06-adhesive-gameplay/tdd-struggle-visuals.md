# 挣扎动作与腿脚黏丝：开发证据

## RED / GREEN

- 先建立 `StrugglePoseTest` 和零姿态 stub，运行 Java 21 的 `tools/verify_struggle_pose.ps1`。
- RED 实际输出：`AssertionError: shallow action pulls one foot up`；编译成功，行为失败。
- 实现 12 tick 正弦动作包络、深度混合、左右交替腿部动作、共用足部端点与细化宽度后，GREEN 374 项通过。
- 核对 vanilla `LivingEntityRenderer` 的旋转/负缩放后，补充右足方向断言，当前测试共 375 项。
- 为正常行走时足部端点追加实际行为测试：零动作 walking stub 实际 RED 为 `vanilla walking foot alternation`；实现当前 HumanoidModel 步态公式后，GREEN 共 378 项。

## 当前版本 API 与渲染规则

- 本地 1.21.11 `HumanoidModel.ArmPose` 真实构造器为 `(boolean,boolean,IArmPoseTransformer)`，枚举扩展使用双 boolean descriptor；官方消耗品文档示例 descriptor 少一个 boolean，以源码为准。
- 使用 render-state `ContextKey` 保存不可变 Frame，模型在 deferred `setupAnim` 中通过扩展姿态调用。宽/细玩家模型和蒙层均走相同动画。
- 正在使用真实工具、骑乘、飞行、观察模式时保留原姿态；挣扎收益仍由服务器处理。
- `RenderHandEvent` 在 vanilla 手部 push/pop 外触发。挣扎空手绘制使用自己的 push/pop 并取消原空手提交，避免两手矩阵叠加、污染后续绘制；实际使用工具不覆盖。
- 拉丝几何回调捕获局部不可变向量/颜色，不捕获复用 render state；每锚点 3 条分段拉丝与一圈足踝/小腿膜，服务器实体位置为起点，脚部共用动作端点为终点。
- 留靴使用真实同步 ItemStack 的 GROUND 模型，保留装备颜色、组件外观和附魔闪光。

## 初期集成安排（历史记录）

- `StruggleVisualChecks.run(Minecraft)` 供主代理接入实际客户端验收：宽/细模型、左右脚、浅/深动作、蒙层一致、动作结束重置。
- Gradle 编译、枚举扩展加载、实际客户端截图和服务器/客户端配套测试由主代理协调运行，未执行之前不声称游戏内验收通过。

## 独立审查后的足部修正

- 移除 renderer 中按当前碰撞箱高度缩放腿部、只套 humanoid 两脚位置的近似路径。
- 玩家使用 sampler 私有 `AvatarRenderer` 和私有 baked 宽/细 `PlayerModel`：完整 AvatarRenderState、实际 setupAnim、实际 renderer offset/setupRotations/scale、root/leg ModelPart 矩阵采样。不会改动正式 renderer 已排队的模型。
- 牛、猪、羊使用独立 baked 成人/幼年模型。连接前右及后左足；直接采样其腿部模型行走相位，动物不套玩家挣扎动作。
- 二次复审后，猫、狼、兔、鸡也改为私有原版 renderer 和实际模型采样，覆盖猫坐卧、狼坐姿、兔跳跃、幼体与冷暖变种；不再使用固定站姿端点。兔子包含 haunch→hind_foot 层级。
- 新增 actual-client 几何验收：蹲姿腿部 Z 位移、renderer Y 偏移、改变蹲姿碰撞箱不会缩腿，以及牛猪羊真实前后脚位置、行走相位与静止足底高度。这些检查需在协调的客户端中观察 RED / GREEN，不将编译当成验收。
- 同步实体支持 `beginBreak()`：物理锚点先由控制器移除，仅留 6 tick 客户端回缩/淡出；客户端捕获足端一次；死亡/失去目标及时清理。主代理负责控制器调用。

依据：[NeoForge 1.21.11 消耗品/姿态文档](https://docs.neoforged.net/docs/1.21.11/items/consumables/)与项目 `run/1.21.11/target-sources` 当前 API 源码。

## 实际 JAR 客户端验收

2026-10-07 已从隔离游戏的 mods/MFQM-neoforge-1.21.11-0.6.1-dev.jar 加载，CodeSource 明确为 JAR。宽／细玩家、蹲姿、七种动物成人／幼体、坐卧／行走／跳跃及实际实体状态提取共 **60 个模型场景通过**；F 短按通过真实网络产生服务器 effort，OS REPEAT 不增加收益。

第一轮四足行走断言错误有真实 RED。复核 `QuadrupedModel.java:72–73` 后确定右前腿与左后腿为对角同相，不是反相；最终保留精确同向位移和坐标诊断断言，再实际 GREEN。复审意见本身也需核对源码和运行结果。

手持物品且未使用时，以自己的 push/pop 调用原版 renderArmWithItem 进行轻微拉扯；正在使用物品保留原姿态。主手双手地图的空副手不重复绘制。空手仍绘制实际模型手臂。截图阶段开启 HUD，避免 vanilla hideGui 同时隐藏第一人称手。
