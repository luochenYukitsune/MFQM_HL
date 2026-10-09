# More Fun Quicksand Mod — Minecraft 1.21.11 / NeoForge

当前版本为 **0.7.1 预发布版**，继续标记为 **Pre-release**。相较 v0.7.0 的修复见 [0.7.1 更新日志](release-notes-0.7.1.zh-CN.md)，此前功能更新见 [0.7.0 更新日志](release-notes-0.7.0.zh-CN.md)。

**0.7.1** 让黏丝介质端向所在液体格底部延伸，胶带加宽、减少收腰与下垂；身体端与活动范围保持原有规则。粘鼠板接缝不再采样胶面贴图的透明框，旧存档中的连接也会在区块加载后自动刷新。

基于 MoreFunQuicksandMod 1.1.1 / Minecraft 1.7.10 的玩法移植。当前开发版增加胶水、粘鼠板、腿脚黏丝及专用挣扎；创造栏显示 165 项，包含 180 个注册物品。

目标固定为 **Minecraft 1.21.11、NeoForge 21.11.45、Java 21**。工作目录名称中的“26.2”是历史名称，不代表模组兼容版本。

## 已实现内容

- 49 个旧方块身份及其状态，另有胶水和粘鼠板；14 对源/流动流体与对应容器规则。
- 沉陷、挣扎、负重、装备浮力、呼吸与伤害、污染和离开介质后的恢复。Liquid Mire 的正常水下呼吸与其他介质的自定义空气各自只有一个处理者。
- 长棍、绳索、抓钩、空手救援、液体枪、饮品、投掷药水、食物和肥料；5 件可穿戴装备。
- 18 种实体角色，包括 4 种 blob、蜜蜂、触手、气泡、救援／投射辅助实体及黏丝、留靴实体；4 种方块实体。
- 207 条核心配方（205 条移植配方及胶水桶、粘鼠板两条新增配方）；其余 9 个跨模组调用由可选兼容桥表示，缺失映射时不启用。
- 主世界/下界介质地貌、蜂巢、蜡树、蛛巢、黏液花、废物出口、神殿机关和沙漠墓。墓的 48×20×27 布局来自旧版固定放置代码。
- 沿用旧版资源清单、48 个动画文件、英文/俄文旧语言键、2 个蜜蜂声音和标志。0.6.1-dev 更新了 100 张泥潭、焦油、蜂蜜及玩家蒙层等相关贴图；未更新的原始图继续保留。
- 服务端权威状态、附件同步、空气 HUD、泥污、视野覆盖、实体及流体渲染。
- 0.6.2-dev 重写多介质实际行走与跳跃约束，关闭飞行的创造玩家默认也会受困；飞行和旁观免疫，创造模式仍免受伤害。完整简体中文名称、操作提示、死亡消息及配置界面已接入。
- 0.6.3-dev 允许黏连范围内实际走动、跑动和有限短跳，挣扎扩大范围后可脱困；黏度不再通过速度属性改变 FOV。创造栏相邻显示已涂胶粘鼠板与空底板，胶面模型的木边框和渲染层已修正。
- 0.6.4-dev 扩大各介质的受困活动半径，并让黏丝接近边缘才回拉。胶水身体覆盖改为白色半透明的逐像素凸起，兼容可选的 3D Skin Layers；客户端可关闭立体覆盖。
- 0.6.5-dev 将胶膜调得更薄、更透明，并按实际浸入高度重排五类身体蒙版：腿部接触不会新增上半身污渍，潜行不会突然提高覆盖等级。深陷留下的残留仍需消退或水洗。
- 0.6.6-dev 适配 FirstPersonModel 的第一人称身体覆盖；全部介质残留支持逐像素立体效果与中文强度设置。移动接触不断增加黏丝，最多 64 条，满额替换旧连接；根部限制在实际介质内。独立延长竖向距离，移除泥潭等非黏性介质的拉丝。
- 0.6.7-dev 修复 FirstPersonModel 临时模型偏移遗漏造成的脚环悬在身体前方；黏丝改为脚边短密束，每接触五条细丝，远处旧丝淡出。全部身体残留的默认凸起减半，缩小离皮肤的间隙。保留活动范围、走跑短跳、物理连接和挣扎规则。
- 0.6.8-dev 将脚环改为随深度变化、贴合原生脚部姿势的半透明薄壳，带实际侧壁和边缘厚度。每个接触只有一根连续、具有封闭六边截面的立体短丝，拉伸变细、松弛弯曲、断裂回缩，胶水及涂胶板黏丝半径加倍。胶水、焦油、蜂蜜、黏液和涂胶粘鼠板共用结构并保留各自颜色。
- 0.6.9-dev 将连续黏丝改为有实体厚度的扁胶带形状，减少松垂，腿脚端点朝向各自的根部；胶水及涂胶板的主宽度比 0.6.8-dev 增加 50%。根部按实际流体表面的最低边角收进液体，包含整个端面，避免液面斜坡或边缘露出根部。保留无分叉、贴脚薄壳、第一人称对齐和现有活动范围。
- 0.6.10-dev 将身体端点稳定随机抬高到胶膜高度的 25%～75%，默认每个接触显示 8 根具有独立根部的完整黏丝，最多约 512 根；新增中文密度设置，可调 1～8。服务器仍为 64 个物理连接，每脚仍只有一层胶膜，不增加黏力或网络实体。
- 0.6.11-dev 同高度、四边相邻的粘鼠板自动拼成大板：内部木边被胶面覆盖，只保留外围木边，2×2 中心的四角也连通。拆掉相邻板恢复木边；每格胶量、补胶和回收独立。复用现有贴图，单板物品模型不变。

装备救生衣时，潜行暂时关闭浮力。**F 始终用于挣扎**。绳索要求手持：首次右键挂接，挂接后按住右键收绳、潜行右键放绳，松开停止；**R** 收绳、**X** 释放辅助键也要求手持。按键可修改。新玩法的配方、留靴回收和风险见 [玩法说明](docs/adhesive-gameplay.md)。

## 构建和运行

安装 JDK 21 并配置 `JAVA_HOME`：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

macOS / Linux 使用 `sh ./gradlew`。首次构建需要下载官方依赖。

产物：`build/libs/MFQM-neoforge-1.21.11-0.7.1.jar`，替换实例 `mods` 中旧版 MFQM，避免同时保留多个版本。粘鼠板新增连接状态，同步协议为 7，客户端与服务端需一起更新为同版。世界生成在新生成区块生效。

配置包含原有玩法以及黏连距离、留靴概率、胶水池／板生成和动作显示开关，可从模组列表打开中文配置界面。`creativeGroundPhysics` 默认开启，控制关闭飞行的创造玩家是否受困；服务器决定玩法设置。获取工具的开关控制配方，注册保持稳定；配方设置修改后可通过 `/reload` 重载，世界生成设置仅影响新区块。旧数字 DataWatcher 槽位由现代附件替代。

## 验证

```powershell
.\gradlew.bat runData build
python tools/verify_jar.py build/libs/MFQM-neoforge-1.21.11-0.7.1.jar
python tools/verify_port.py build/libs/MFQM-neoforge-1.21.11-0.7.1.jar
python tools/connected_boards.py --verify
python -m unittest discover -s tools -p test_coating_height.py
python tools/coating_height.py --verify
python tools/smoke_port.py --reload
python tools/smoke_port.py --reload --natural-glue
python tools/smoke_port.py --disable-long-stick --reload
python tools/smoke_port.py --disable-long-stick --reload --compat-fixture
python tools/smoke_client_port.py
python tools/smoke_port.py --reload --persistence-phase prepare
python tools/smoke_port.py --reload --persistence-phase recover
.\gradlew.bat prepareInstalledClient -PmfqmClientChecks -PmfqmTextureChecks
python tools/smoke_installed_client.py
.\gradlew.bat prepareInstalledClient -PmfqmClientChecks
python tools/smoke_viscosity_client.py --creative-ground-traps
python tools/smoke_viscosity_client.py --bounded-only --board-visual
python tools/smoke_viscosity_client.py --coating-only --coating-visual
python tools/smoke_viscosity_client.py --coating-only --skin-layers --coating-visual
python tools/smoke_viscosity_client.py --coating-only --skin-layers --first-person --coating-visual
python tools/smoke_viscosity_client.py --coating-only --skin-layers --first-person --animations --coating-visual
python tools/smoke_viscosity_client.py --dynamic-only --dynamic-visual
python tools/smoke_viscosity_client.py --visual-only
```

立体胶水兼容测试的外部 JAR 仅放在被 Git 忽略的 `run/compat`，不随 MFQM 发布。测试使用作者官方 3D Skin Layers 1.11.3 / 1.21.11 NeoForge 构建并核对哈希；该构建及其内嵌库存在资源包描述格式报错，实际皮肤网格和胶膜仍完成了合载渲染检查，详见 [0.6.4-dev 验收报告](docs/superpowers/2026-10-08-glue-coating-3d/final_report.md)。

仅修改动作测试夹具时，可用 `--actions-only` 定向重测 F、救援、普通走路、受击和粘鼠板；该作用域不会冒充完整的 23 场景介质回归。

Python 工具需要 Python 3.11+。服务器检查只允许绑定本机、随机端口和独立的 `smoke-world`；客户端检查使用复制的测试存档。测试工具为该独立测试服务器写入 EULA 接受值，正式实例由使用者阅读并决定接受。开发数据位于忽略的 `run/1.21.11/`。

`build` 包含 35 组运动检查、35 个阻力边界样本，以及黏连 241 项、接触/根部 78 项、范围/短跳 112 项、覆盖颜色/贴肤间隙 12 项、像素胶膜 388 项、短密黏丝 92 项、湿黏薄壳与立体胶带 2156 项、挣扎 739 项、动作姿态 378 项断言。服务器检查覆盖配方、束缚、救援、留靴、换维度与地形保护，并通过真实存档重启验证留靴。安装检查通过代码来源断言排除直接加载开发类。黏度工具默认不截图，主套件使用生存及关闭飞行的创造模式、真实 W 与跳跃输入，测量期间检查两端 `flying=false`，随后验证胶水持续移动、F、救援、受击和已涂胶板。`--bounded-only` 仅运行粘鼠板、FOV 与范围走跳检查；`--board-visual` 显式增加一张必要的空板/胶板模型截图。飞行对照需单独使用 `--flight-controls`，不进入主验收。自然生成检查保持默认概率，扫描未生成过的地表森林候选区块。旧玩法结果见[验收报告](docs/superpowers/2026-10-06-adhesive-gameplay/final_report.md)，此前黏度记录见[黏度修复](docs/superpowers/2026-10-07-viscosity-fix.md)。

0.7.0 发布时按用户要求仅编译、打包并运行轻量几何及 JAR 资源检查；未启动游戏。0.7.1-dev 恢复简化的真实游戏验证，`--visual-only` 只运行跨区块 2×2 板实际放置、拆除、旧状态修复及黏丝两场景，保存两张必要截图；不代表重跑完整玩法或兼容矩阵。拼接工具增加实际纹理透明度检查，需安装 Pillow，避免几何已连通但接缝仍透出木色。详见[本次修正记录](docs/superpowers/2026-10-09-adhesive-visual-fix/final_report.md)。

`src/generated/resources` 中的 19 个 JSON 是构建输入，需与源码一起保留；缓存不进入 JAR。模型、配方和世界生成数据位于 `src/main/resources`。正常构建不需要旧模组目录或反编译缓存。

0.6.5-dev 另包含 23 个覆盖高度边界检查、6 项 Python UV 行为测试以及 50 张新高度蒙版的像素/哈希校验。实际服务器对四种介质测试 56 个站立/潜行接触深度；客户端检查五类材质、宽/细臂与六个身体部位的 600 个覆盖组合。贴图烘焙后运行 `python tools/coating_height.py --bake --verify` 更新高度蒙版。

## 适配边界

这次恢复了旧版内容清单并重写核心玩法。现代运动/AI、随机地貌密度、奖励的原版基础池及部分辅助实体渲染有适配差异；现有验收不能证明与 1.7.10 每个数值或像素完全相同。外部模组的旧 metadata 物品需要明确映射，尚未进行所有第三方模组的真实合载及双真人网络验收。

## 记录

- [0.6.11-dev 相邻粘鼠板拼接](docs/superpowers/2026-10-09-connected-boards/final_report.md)
- [0.6.10-dev 随机提高身体连接点与浓密独立黏丝](docs/superpowers/2026-10-09-dense-strands/final_report.md)
- [0.6.9-dev 根部浸没与立体胶带修复记录](docs/superpowers/2026-10-08-anchored-strands/final_report.md)
- [0.6.8-dev 湿黏薄壳与立体连续黏丝验收](docs/superpowers/2026-10-08-wet-adhesive/final_report.md)
- [0.6.7-dev 第一人称脚部对齐与短密黏丝验收](docs/superpowers/2026-10-08-compact-strands/final_report.md)
- [0.6.6-dev 第一人称兼容、动态黏丝与全材质覆盖验收](docs/superpowers/2026-10-08-dynamic-adhesion/final_report.md)
- [0.6.5-dev 胶膜浓度与身体覆盖高度验收](docs/superpowers/2026-10-08-coating-height/final_report.md)
- [0.6.4-dev 活动范围与立体胶水验收](docs/superpowers/2026-10-08-glue-coating-3d/final_report.md)
- [0.6.3-dev 视角、有限走跳与粘鼠板验收](docs/superpowers/2026-10-07-bounded-adhesion/final_report.md)
- [0.6.2-dev 黏度、跳跃与汉化验收](docs/superpowers/2026-10-07-viscosity/final_report.md)
- [胶水与黏连设计](docs/superpowers/2026-10-06-adhesive-gameplay/design.md)
- [胶水与黏连验收](docs/superpowers/2026-10-06-adhesive-gameplay/final_report.md)
- [本次移植与验证报告](docs/superpowers/2026-10-06-gameplay-port/final_report.md)
- [独立审查和修复记录](docs/superpowers/2026-10-06-gameplay-port/review.md)
- [可选跨模组配方映射](docs/superpowers/2026-10-06-gameplay-port/compat-recipes.md)
- [1.7.10 内容对照](docs/superpowers/2026-10-05-legacy-content-audit/comparison.md)：保留移植前的缺失证据，当前状态以本次报告为准。
- [实现路线图](docs/superpowers/plans/2026-07-29-mfqm-neoforge-port-plan.md)
- [目标版本迁移记录](docs/superpowers/2026-10-05-minecraft-1.21.11-migration/final_report.md)

项目采用 [MIT License](LICENSE)，原作者和第三方材料声明见 [NOTICE.md](NOTICE.md)。历史报告中的旧许可证与原始像素描述仅对应当时的快照。

## 当前玩法与显示

同高度、四边相邻的粘鼠板自动拼成大板。内部胶面延伸到接缝、补齐四角，只在整片外缘保留木边；对角或高低错位的板不连接。拆除邻板恢复外缘。每格胶层消耗、补胶、回收和受困仍独立，空底板不会因拼接自动获得胶层。物品栏继续显示原单板外观。0.7.1-dev 在区块完成加载后自动修复旧板的连接，也处理已加载的跨区块邻板。

FirstPersonModel 会临时移动正在渲染的身体。现在从同一帧的原生渲染状态取得实际双脚世界端点，避免脚环使用恢复后的物理位置而浮在身体前方；不硬编码偏移。切换视角和 F6 开关时清除相机脚点。验收直接比较实际身体腿部矩阵与黏丝提交位置，覆盖站立、潜行、转身及真实胶水/胶板受困；合载 FirstPersonModel 2.7.3、3D Skin Layers 1.11.3 和 Not Enough Animations 1.12.6。

每个接触默认显示 8 根独立、完整且无分叉的立体黏丝，仍采用有封闭侧壁和端面的扁胶带截面；胶水及涂胶板比其他介质更宽。身体端点朝向各自根部，稳定随机分布在胶膜高度的 25%～75%，从脚踝到小腿下部随接触深度变化。每根根部在原接触方块内独立散布，液面只需计算一次；每根沿自身连接方向回缩。水平 0.45～0.65 格、相对介质表面竖向 0.9～1.15 格逐根淡出，远处旧丝不参与绘制。

0.7.1-dev 进一步加宽胶带并提高不透明度，中段保留至少 88% 宽度、减少下垂，避免过度收腰形成不规则轮廓。介质端下移到所在格底部附近（根部中心通常距底部约 0.06 格），浅流体和板面自动限制深度以容纳完整截面。此内部延长不消耗介质表面以上的可见距离，不额外提高身体连接点。

中文“黏丝束显示上限”仍控制最多 64 个接触，新设置“每个接触的独立黏丝数量”默认 8、可调 1～8；满额时最多约 512 根。旧配置直接采用新密度默认值，不必重置文件。实际数量受接触和距离影响，静止不会无限补充。物理连接、活动范围和竖向断裂配置保持原有规则，每脚仍只有一层胶膜。高密度会增加客户端绘制开销，可在游戏中调低密度或接触上限；本版未进行实际游戏性能测试。

全部残留统一跟随当前玩家模型的部件姿势，支持 FirstPersonModel 2.7.3 的第一人称身体与可选 3D Skin Layers 1.11.3。泥沙、焦油、蜂蜜、黏液采用与胶水相同的逐像素凸起；保留各自色调、浸入高度及水洗。中文配置界面可调整全局和各材质的覆盖透明强度、凸起厚度、黏丝显示数量；原有 `glueCoating3d` 配置键现在控制全部材质。

默认凸起现为 0.06～0.12 模型像素，较 0.6.6-dev 减半。不可见的皮肤外层不再撑开附着膜；可见外层的包围壳安全余量缩至 0.02 模型像素，厚度滑块仍可调整立体感。立体皮肤仍按部件整体尺寸避让，尚未对透明洞逐像素贴面。新增贴肤检查与截图见本次验收。

实际接触同一方块内的新位置也会补充黏丝，默认上限 64，满额让最旧的依次断开；每只脚仅保留一层黏膜，数量不会叠加束缚。显示根部按原生流体四个边角的最低高度收进液面下，并为整个端面预留侧边、底部和液面余量；液层变薄会缩小根部，无法容纳端面时不绘制，介质消失立即失效。此调整只影响显示坐标。胶水竖向距离默认 4 格、蜂蜜/黏液 2.7 格、焦油/粘鼠板 2 格，与水平活动范围独立。泥潭、沙类及黏土等已移除黏丝，仍保留下陷、阻力、挣扎和身体残留。

外部兼容 JAR 仅用于工作区隔离测试，不打包发布。使用官方 FirstPersonModel 2.7.3 / 1.21.11 NeoForge 发行包；它及 Skin Layers 的内嵌库存在资源包描述格式报错，验收日志会明确记录，不会屏蔽其他渲染错误。0.6.6-dev 的动态连接和全材质覆盖验证见[此前报告](docs/superpowers/2026-10-08-dynamic-adhesion/final_report.md)。

## 新版材质与拼接验证

0.6.1-dev 重新生成并接入了泥潭、泥沼、泥炭、焦油、蜂蜜、相关图标及玩家附着层，共 100 张运行时 PNG。方块基本纹理为 32×32，流动液体使用 64×64 帧，玩家蒙层使用 128×64 图像配合原 UV 布局。液体帧数、播放节奏以及蜂蜜原有透明度保留。

玩家附着层分为泥土、黏液、焦油、蜂蜜四组，每组保留 10 级覆盖和清洗渐隐。已检验同材质平铺边缘、源液/流液初始帧配色与透明度、流动 UV 重复区域及全部动画循环；客户端检查会生成两个角度的相邻方块和真实流体出口截图。

原图备份、AI 源图、运行时贴图清单及拼接预览见 [贴图更新记录](docs/texture-refresh/README.md)。重新烘焙或验证需要 Pillow 与 NumPy：

```powershell
python tools/refresh_textures.py --bake
python tools/refresh_textures.py --verify
```

新版材质有意改变了部分原始像素，旧版字节完全一致检查 `verify_port.py --legacy` 因此会报告这些变化；正常资源校验和新版材质校验仍必须通过。
