# MFQM_HL — More Fun Quicksand Mod 0.6.0

**版本：0.6.0 · Minecraft 1.21.11 · NeoForge 21.11.45 · Java 21 · MIT**

这是源工作区的完整项目导出，包含 Java 源码、测试、全部运行时资源、已生成的数据、Gradle Wrapper、开发工具和文档。Git 内部文件、构建缓存、运行目录和测试存档未包含在导出中；整个目录可移动到其他位置独立构建。

本模组基于 MoreFunQuicksandMod 1.1.1 / Minecraft 1.7.10 的玩法移植。当前版本已实现方块、流体、物品、装备、救援、生物、客户端表现和世界生成；创造栏显示 162 项，共注册 178 个物品。

当前版本基于 **Minecraft 1.21.11、NeoForge 21.11.45、Java 21**，后续计划支持更多游戏版本，不同版本将提供对应构建。工作目录名称中的“26.2”是历史遗留名称，与模组兼容版本无关。

## 已实现内容

- 49 个旧版方块身份，包含表壳、软化、方向、成熟、颜色等状态；13 对源/流动流体及对应的容器规则。
- 沉陷、挣扎、负重、装备浮力、呼吸与伤害、污染，以及离开介质后的恢复。Liquid Mire 的正常水下呼吸与其他介质的自定义空气各只有一个处理者。
- 长棍、绳索、抓钩、空手救援、液体枪、饮品、投掷药水、食物和肥料；5 件可穿戴装备。
- 16 种实体角色，包括 4 种 blob、蜜蜂、触手、气泡及救援/投射辅助实体；4 种方块实体。
- 205 条核心配方覆盖旧版的 85 个注册调用；其余 9 个跨模组调用由可选兼容桥表示，按数据包标签映射现代依赖，缺失映射时不启用。
- 主世界/下界介质地貌、蜂巢、蜡树、蛛巢、黏液花、废物出口、神殿机关和沙漠墓。沙漠墓的 48×20×27 布局沿用旧版固定放置代码。
- 原始 246 张 PNG、48 个动画文件、英文/俄文旧语言键、2 个蜜蜂音效和标志。PNG 像素未重绘；流动黏液的动画帧尺寸已按现代加载要求修正。
- 服务端权威状态、附件同步、空气 HUD、泥污、视野覆盖、实体及流体渲染。

装备救生衣时，潜行可暂时关闭浮力。连接工具默认使用 **R** 收紧、**F** 放线、**X** 释放，均可在游戏按键设置中修改。空手救援产生的临时物品不会显示在创造栏。

## 构建与运行

安装 JDK 21 并配置 `JAVA_HOME`：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

macOS / Linux 使用 `sh ./gradlew`。首次构建需要下载官方依赖。

当前版本的构建产物为 `build/libs/MFQM-neoforge-1.21.11-0.6.0.jar`，放入 Minecraft 1.21.11 / NeoForge 实例的 `mods` 文件夹即可。未来支持更多版本后，请按对应版本选择构建产物。世界生成对新生成的区块生效。

配置包含 66 个 SERVER、6 个 CLIENT 和 9 个 COMMON 项。获取工具的开关控制配方，注册保持稳定；修改服务器配置后需重启服务器，数据包可通过 `/reload` 重载。旧版数字 DataWatcher 槽位已由现代附件替代。

## 验证

```powershell
.\gradlew.bat runData build
python tools/verify_jar.py build/libs/MFQM-neoforge-1.21.11-0.6.0.jar
python tools/verify_port.py build/libs/MFQM-neoforge-1.21.11-0.6.0.jar
python tools/smoke_port.py --reload
python tools/smoke_port.py --disable-long-stick --reload
python tools/smoke_port.py --disable-long-stick --reload --compat-fixture
python tools/smoke_client_port.py
```

Python 工具需要 Python 3.11+。服务器检查仅允许绑定本机、随机端口和独立的 `smoke-world`；客户端检查使用复制的测试存档。测试工具会为该独立测试服务器写入 EULA 接受值；正式实例请使用者自行阅读并决定是否接受。开发数据位于已忽略的 `run/1.21.11/`。

`build` 包含 14 项纯 Java 运动行为断言。服务器检查覆盖桶/弹药、呼吸、碰撞、救援生命周期、生物捕获、配方与生成机关；客户端检查验证创造栏、全部物品/方块状态模型、流体资源和实体渲染注册。具体检查结果见下方报告。

`src/generated/resources` 中的 19 个 JSON 是构建输入，需与源码一并保留；缓存不进入 JAR。模型、配方和世界生成数据位于 `src/main/resources`。正常构建不需要旧模组目录或反编译缓存。

## 适配边界

本次移植恢复了旧版内容清单并重写了核心玩法。现代运动/AI、随机地貌密度、奖励的原版基础池及部分辅助实体渲染存在适配差异；现有验收无法证明与 1.7.10 的每个数值或像素完全一致。外部模组的旧 metadata 物品需要显式映射，尚未完成所有第三方模组的真实合载及双真人网络验收。

## 记录

- [本次移植与验证报告](docs/superpowers/2026-10-06-gameplay-port/final_report.md)
- [独立审查和修复记录](docs/superpowers/2026-10-06-gameplay-port/review.md)
- [可选跨模组配方映射](docs/superpowers/2026-10-06-gameplay-port/compat-recipes.md)
- [1.7.10 内容对照](docs/superpowers/2026-10-05-legacy-content-audit/comparison.md)：保留移植前的缺失证据；当前状态以本次报告为准。
- [实现路线图](docs/superpowers/plans/2026-07-29-mfqm-neoforge-port-plan.md)
- [目标版本迁移记录](docs/superpowers/2026-10-05-minecraft-1.21.11-migration/final_report.md)

## 目录结构

- `src/main/java`：模组 Java 源码。
- `src/main/resources`：方块/物品模型、原始资产、配方、世界生成、结构和元数据。
- `src/generated/resources`：构建使用的数据生成输出。
- `src/test`：运动行为断言。
- `gradle`、`gradlew`、`gradlew.bat`：构建 Wrapper。
- `tools`：资源校验、隔离运行验收及旧版转换工具。
- `docs`：设计、审查、移植对照和历史验证记录。
- `EXPORT_MANIFEST.json`：导出文件清单。历史报告中的本地 `run` 日志和 `build` 产物未随源码导出，需自行构建或重新运行验收。

## 许可证与致谢

本项目的源码和模组资源采用 [MIT License](LICENSE)。项目维护者已确认原作者开放授权，并允许将整个项目按 MIT 授权。你可以按照 MIT 条款使用、修改和分发本项目，分发时请保留版权及许可声明。原作者来源及第三方材料声明见 [NOTICE.md](NOTICE.md)。历史报告中的旧许可证描述不代表本导出的当前许可。

原作者和贡献者：MrBlackGoo、CrishNate、elix_x、VanderCat、Sanic。移植署名沿用 MFQM Team；MIT 版权行使用 MFQM contributors。

## 下载 0.6.0 预发布版

前往 [GitHub Release](https://github.com/luochenYukitsune/MFQM_HL/releases/tag/v0.6.0) 下载 `MFQM-neoforge-1.21.11-0.6.0.jar`，放入 Minecraft 1.21.11 / NeoForge 21.11.45 或兼容后续版本实例的 `mods` 文件夹。Release 同时提供源码压缩包。

维护者已认可当前状态，并提供 JAR 供使用；该版本保持 Pre-release 标记。已有适配差异和验证边界见上文。JAR 内包含 MIT 许可及原作者声明。
