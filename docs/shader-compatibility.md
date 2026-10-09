# 光影兼容说明

目标环境是 Minecraft 1.21.11、NeoForge 21.11.45、Iris 1.10.8、Sodium 0.8.14。阴影阶段检测使用可选的只读 Iris API；ITT／ITPR 池面另有限定源码特征的可选兼容桥，编译时修改内存中的材质路径。未安装 Iris 时使用原版渲染。游戏内覆盖透明强度、像素厚度和黏丝数量设置继续可用。

## 0.7.2-dev 修复

- IterationT 和 IterationRP 的实体及流体路径仅使用纹理 alpha，会忽略顶点透明度。立体覆盖、黏丝和胶膜把最终透明度编码进纹理，顶点保留原色；避免变成实心覆盖，也避免重复相乘。
- 立体覆盖使用带内缩边距、关闭模糊的透明度纹理格，四角 UV 非退化。平面及远景覆盖把强度和消退烘入缓存贴图，保留原来的六面模型，避免逐像素远景网格变重。
- 蜂蜜、焦油、黏液、黏稠液体和黏液分泌物的源/流动动画只降低 alpha，保留全部 RGB 像素、帧尺寸和动画信息；对应流体进入透明渲染层。其他泥浆、酸等维持原材质策略。
- ITT／ITPR 默认把未知透明流体当作玻璃，只计算吸收的白胶看起来近乎完全透明。现在仅为六种 MFQM 黏性流体选择一个未占用的临时材质 ID：ITT 使用它原有的普通 alpha 混合，ITPR 使用它原有的透明颜色合成，同时正确记录深度、保留块光。白胶有乳白遮盖层，其他流体保留颜色和动画纹理，仍可透出池底；不会重复叠加玻璃吸收。原版水和玻璃不修改。
- 绳索、触手与器官凸起使用实际几何法线；延迟绘制保存提交时的光照值。
- Iris 阴影绘制不使用 FirstPersonModel 主视角的临时足位缓存。此为跨绘制阶段的防护，未宣称复现了旧版阴影闪动。

## 验证方法与范围

`tools/smoke_shader_client.py` 将用户的光影包、设置以及已安装的 Iris、Sodium、FirstPersonModel 2.7.3、3D Skin Layers 1.11.3 复制进工作区独立实例，使用打包后的 JAR 和复制测试世界。不会修改用户原存档、光影设置或桌面导出副本。

ITT（IterationT 3.2.0）、ITPR（IterationRP Alpha 0.8.26 hotfix）检查关闭飞行的实际移动、高位黏丝、介质内根部、立体及平面覆盖、第一人称足位，再正对胶水、蜂蜜、焦油和黏液四池保存可解码的截图。池面保存修复前后同场景对照，并检查实际 Iris 源/流动状态材质映射和源码适配已生效。Complementary Reimagined 使用身体和第一人称场景作为对照。其余本地光影只做静态审查与简短的实际加载检查，不等于完成全部视觉或玩法验收。实际结果见 [本次验证记录](superpowers/2026-10-09-leg-height-shaders/final_report.md)。

测试启动采用正式启动器默认的 `enableB3DValidationLayer=false`，仅写独立实例配置。NeoForge 开发环境默认启用的验证设备包装器与当前 Iris 的 `GlDevice` 强制转换不兼容；不会因此修改模组或用户实例配置。

## 实际限制

九个本地光影包没有 MFQM 专用的方块/实体材料映射。除上述两包的限定池面桥以外，本次修复通用绘制输入、透明度、法线与第一人称采样。池面桥不写入光影 ZIP 或配置，不再分发包源码；签名不匹配或包已有 MFQM 映射时保持原样。临时 ID 只用于该包加载，既不占用已有材料号，也不进入存档。

ITT／ITPR 的普通半透明池面路径舍弃玻璃折射和玻璃反射；ITT 的光照沿原有提前混合路径，ITPR 采用包的透明颜色近似光照。目标是可见的半透明黏性介质，尚未实现专属流体散射/PBR。普通显示通过，并不能保证每款光影的 PTGI、体素反射、PBR、透明阴影和多层透明排序都有完全相同的效果。部分包的 alpha 丢弃阈值仍会隐藏非常淡的表面。

本地 Continuum 2.1 Alpha 的源码引用 `colorLookup3D.DAT`，但 ZIP 内文件为 `colorLookup3D.dat`，Iris 报找不到查找表。原包未修改，不能将其列为无错误兼容通过。

依据：[Iris 1.21.11 管线映射](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/pipeline/IrisPipelines.java)、[Iris 公共 API](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java)、[Iris 材质映射读取](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/shaderpack/IdMap.java)、[程序源码 getter](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/shaderpack/programs/ProgramSource.java)，以及用户本地包、安装的 Iris JAR 和当前 NeoForge 源码。
