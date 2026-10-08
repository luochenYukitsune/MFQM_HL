# 0.6.1-dev 介质阻力调整

仅修改当前工作目录；桌面的 MIT 发布副本以及 GitHub 0.6.0 均未修改。

## 对照依据

旧版原始类和现有反编译材料位于用户提供的 1.7.10 目录及本地 `run/1.21.11/legacy-audit/decompiled`。检查了流体属性类、各 Block 的 `mr_kof` 表达式、水平速度重置和 `CustomPotionEvent.onLivingUpdateEvent` 的速度公式。

当前 13 个现代流体注册的 viscosity 数值与旧版一致，不需要通过修改这些数值假造手感差异。NeoForge 的 FluidType 属性与实体运动钩子相互独立；本项目使用自己的 QuicksandPhysics 处理实体接触。依据为本地提取的 `run/1.21.11/target-sources/net/neoforged/neoforge/fluids/FluidType.java` 以及 [NeoForge 实体开发文档](https://docs.neoforged.net/docs/1.21.11/entities/livingentity/)。

## 实现

- 新增纯 Java 的 LegacyResistance，从旧 Block 的系数、指数和上限计算材质及变体相关的阻力。
- 用 clearance 表示旧坐标体系中的表面距离：玩家参考高度 1.62，其他生物参考高度 1.5。现代输入 depth 仍从脚部测量。这是对旧坐标与现代碰撞深度的适配，尚未按所有实体模型逐项校准。
- 输入速度使用旧 CustomPotionEvent 的非水平碰撞分支，最低速度为原速度的 2%。不再对速度与属性重复套用同一个减速系数。
- 惯性阻尼独立处理：泥土保留 0.71 的水平速度，流沙等旧类直接清除水平漂移。救援期间单独保留水平与垂直拉力。
- 湿泥沼、软蜡等根据现代 variant 调整阻力；Liquid Mire、Stable Liquid Mire、Moor、Acid 和临时吸入口仍保留已有专用模型。
- 跳跃增加与表面距离相关的吸力；负重、涉水靴、救生衣、氧气和伤害路径继续沿用当前实现。

此轮重点是介质粘滞和跳跃吸力。仍未逐帧复刻旧版随机浮力、位置回退、所有碰撞纠正分支或全部垂直运动常数，不能视为与 1.7.10 完全一致。掉落物的运动路径本轮没有调整。

## 验证

- 独立 Java 检查：26 组行为和 35 个阻力边界样本通过，包括旧公式参考点、不同介质、深度、湿度状态和跳跃。
- `gradlew.bat build` 成功，含相同运动检查。
- `verify_jar.py` 通过；`verify_port.py` 为 0 错误。
- `smoke_port.py --reload` 的 39 组隔离专服玩法检查通过，包含实际实体的深层流沙阻力、水平漂移清除及救援拉力，并成功重载。
- 本轮未进行真人手感或双真人联机验收。

本地产物：`build/libs/MFQM-neoforge-1.21.11-0.6.1-dev.jar`。许可及原作者声明已包含在包内。

## 贴图试验

新增 `docs/texture-candidates/mud-preview.png`。仅用于风格预览，不进入 JAR；原运行时贴图未替换。候选图的分辨率、接缝与远距离显示还需要验收。
