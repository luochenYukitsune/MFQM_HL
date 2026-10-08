# 0.6.6-dev 第一人称兼容、动态黏丝与全材质覆盖验收

用户通过 ask 批准设计后实施。目标 Minecraft 1.21.11 / NeoForge 21.11.45 / Java 21；所有修改与游戏测试位于当前工作目录。桌面副本和日常 Minecraft 实例未变更，本次未发布 GitHub Release。未新增子代理或 AI 图像。

## 已完成

- 适配 FirstPersonModel 2.7.3：残留随当前原生身体、手臂和腿部姿势提交，继承第一人称位移/隐藏部件，明确跳过头部覆盖；真实下视、潜行持物和原版手臂模式通过。未安装时保留正常渲染。
- 默认最多 64 条活动黏丝；移动接触新位置才增加，同一格内也生效，满额让最旧连接先断开。每只脚只保留最新的一层黏膜；总拉力按数量归一化，增加密度不额外增加束缚。
- 根部限制在实际介质体积内，包含中心和横截面；薄流层、边缘、胶板及液面下降共用约束。液面变化更新旧根部，移除介质释放连接。
- 竖向距离独立配置：胶水 4 格、蜂蜜/黏液 2.7 格、焦油/粘鼠板 2 格；原水平活动范围保留。泥潭、沙类、黏土等不再拉丝，仍有下陷、阻力、挣扎及残留。
- 全部残留统一使用像素凸起，保留各自颜色、较淡胶膜、浸入高度、水洗和渐隐。中文游戏配置提供全局及胶水、焦油、蜂蜜、黏液、泥沙各类的透明强度、凸起厚度、黏丝显示数量。

配置入口为“模组 → MFQM → 配置 → 客户端显示 → 画面显示”。透明度和厚度采用全局值乘材质值，黏丝采用较小上限；设为 0 分别隐藏覆盖、使用平面、隐藏黏丝。`glueCoating3d` 原保存键保留，但现在控制全部残留的立体效果。显示设置不改变服务端物理。

## 自动化和实际游戏

- `gradlew build prepareInstalledClient -PmfqmClientChecks -PmfqmTextureChecks` 通过：运动 35 组、阻力边界 35 个、覆盖高度 23 项；黏连 241 项、接触/根部 78 项、有限走跳 112 项、覆盖颜色 7 项、像素表面 387 项、挣扎 739 项、姿态 378 项。
- Python 工具回归 29 项通过；`coating_height.py --verify` 检验 50 张解剖高度蒙版与原 PNG 哈希不变；最终 JAR 的 `verify_jar.py` 和 `verify_port.py` 均为 0 错误。
- 专用服务器 75 组和真实 `/reload` 通过：[服务器日志](server.log)。专项覆盖同格补丝、64 条 FIFO、静止不补丝、活动原点不移动、既有根部随液面降低、独立竖向断裂、薄层边缘、介质移除、非黏性接触仍有物理。
- 最终 JAR 23 组生存/创造地面实际走跳全部通过，两端持续检查 `flying=false`：[地面日志](ground-final.log)。真实 W 120 tick 后胶水移动 **1.2486 格**、30 条连接；胶板 W 60 tick 移动 **0.7092 格**，跑跳峰值 **0.26 格**，受困 FOV 乘数 **1.0**。33 次真实 F 后解除胶板连接，再正常行走 **12.3620 格**。F 下陷不重复消费，救援和真实受击击退生效并到期。
- 真实交替 W/S 累积到客户端/服务端 **64 条连接**，显示调为 16 后物理仍为 64，每脚一层黏膜、根部在介质内：[密集黏丝日志](dynamic-client.log)。此轮 JAR 哈希为 `a4a758a72b380c7a56242f62a646bb20d2f84785b43a7b2dabc7392473b48903`；随后将相同 FIFO 移除逻辑接入已测的纯函数并加强夹具，最终构建、服务端与完整地面回归另行通过。
- 无附加外观模组的覆盖检查通过：[原版日志](coating-vanilla.log)。最终 JAR 合装官方 FirstPersonModel **2.7.3** 与 3D Skin Layers **1.11.3** 通过：[合载日志](coating-firstperson.log)。包括五类材质/宽细臂/六部位的 600 个蒙版组合、真实皮肤外层网格、资源重载、全部非胶水材质实际立体提交、透明度 0 隐藏、厚度 0 平面、第一人称真实身体与隐藏头、潜行持物和手臂模式。原版日志对应实施期间的 `fe48b757…`，最终合载与地面日志的 JAR 均为下方最终哈希。
- 实际游戏中文检查为 570 个英文键全部覆盖，中文键 826 个；配置界面与注册内容名称检查通过。

只新增并查看了三张必要的实际游戏截图，实施过程中拍摄；最终合载和完整地面重测不再截图。四种非胶水材质从左到右为蜂蜜、焦油、黏液、泥沙，头像头顶名称是测试模型名称：[全材质立体残留](all-material-coatings.png)。第一人称下视可见胶膜仅在腿部、上身未被浅层污染：[第一人称身体](firstperson-model-coating.png)。真实非飞行角色脚边多条黏丝可见：[64 条动态黏丝](64-dynamic-strands.png)。

## 兼容来源与限制

适配依据作者官方 2.7.3 的 [渲染状态接口](https://github.com/tr7zw/FirstPersonModel/blob/2.7.3/src/main/java/dev/tr7zw/firstperson/access/LivingEntityRenderStateAccess.java)、[模型姿势处理](https://github.com/tr7zw/FirstPersonModel/blob/2.7.3/src/main/java/dev/tr7zw/firstperson/mixins/HumanoidModelMixin.java) 和 [公开 API](https://github.com/tr7zw/FirstPersonModel/blob/2.7.3/src/main/java/dev/tr7zw/firstperson/api/FirstPersonAPI.java)。1.21.11 的延迟绘制使用每个渲染状态的相机标记，比只读提交期间的短暂全局标记可靠。MFQM 仅以可选反射读取，不打包或复制第三方实现。

官方测试 JAR 的 SHA-256：

- FirstPersonModel：`3e4ee53a6cc14f0f34041360d1d6b6ab9fa6358e9228785afa3a9378b86b5956`。
- 3D Skin Layers：`222f325cbee333ab7be5a87788462ca71cda09e2c693f1b1c0186c1d44979d20`。

这两个官方包及其内嵌 `trender`、`transition` 的资源包描述缺少格式 75 所要求的 `supported_formats`，合载时会记录 `AbstractPackResources` 错误。原样保留上游 JAR，测试仅允许这四个包的精确描述格式错误继续，其他错误立即失败。真实身体、皮肤外层和 MFQM 覆盖已完成渲染检查；不声称上游全部界面资源都正常。

未完成大量生物同时受困的帧率/网络压力基准、两个真人客户端联机、非标准玩家模型或其他光影/外观模组组合。规格与代码审查见 [审查记录](review.md)。

## 产物和安装

`build/libs/MFQM-neoforge-1.21.11-0.6.6-dev.jar`，**2,466,852 字节**。

SHA-256：`e48c72123c973157a04be77009468add7281fe4db109f2b0332d922e2ae450cd`。

用此 JAR 替换 `mods` 中旧 MFQM，避免同时保留多个版本。同步协议已升为 **6**，客户端与服务端必须一起更新到 0.6.6-dev。旧泥潭拉丝配置键为兼容文件保留，已不再影响非黏性介质。
