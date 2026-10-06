> Historical record: these checks targeted Minecraft 26.2. The current target is Minecraft 1.21.5; use ../2026-10-05-minecraft-1.21.5-repair/ and the root README for current instructions.

# 基础框架修复复核

2026-10-05，修复范围为当前已存在的基础框架代码。原始审查 R01–R12 均已处理，独立子代理复核后未发现剩余源码缺陷。

## 已处理问题

- R01：补齐两个 Wrapper 启动脚本和有效 JAR；由 Gradle 9.2.1 重新生成，JAR SHA-256 为 `423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9`。发行包使用官方 SHA-256 校验，脚本换行格式由 .gitattributes 固定。
- R02–R03：固定 Gradle 9.2.1、ModDevGradle 2.0.148、Foojay 1.0.0、NeoForge 26.2.0.88，保留 Java 25 和 Minecraft 26.2。版本参照 [官方 26.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle)。
- R04–R06：修复 ModConfig 命名遮蔽；使用 Identifier；移除已删除的护甲材质注册表占位。
- R07：迁移 GatherDataEvent.Client、模型及标签提供器；客户端入口按 Dist.CLIENT 隔离，专用服务器加载成功。
- R08–R09：补齐 license、展开版本和依赖模板、限制 Minecraft 26.2；保留原作者署名。
- R10：生成资源接入主资源集，允许随源码提交；仅忽略生成缓存，缓存也从资源处理排除。
- R11：实际生成五个伤害类型定义，并在包含这些定义的 lookup 上生成严格伤害标签；补齐全部已声明的 12 个分类标签，成员保持为空。
- R12：移除过时的 pack.mcmeta，由 NeoForge 提供当前版本的包元数据。

附加修复：空创作物栏隐藏、死亡归因消息缺失、logoFile 弃用、开发服务器标准输入未接入。完整英文语言文件保留为唯一来源，避免生成器覆盖或资源重复。

## 实际验证

- Wrapper --version：Gradle 9.2.1 / Java 25.0.3。
- compileJava：全部源码通过，24 个编译类均为 Java 25 的 major version 69。
- 最终 clean build --offline：17 秒成功，所有源码和资源重新构建，无项目构建警告；仓库没有单元测试，test 任务为 NO-SOURCE。`--offline` 限制 Gradle 依赖解析，NeoFormRuntime 仍会检查官方版本清单。
- runData 两次：通过，输出 19 个 JSON；第二次 written: 0，内容 SHA-256 完全一致。
- 资源检查：五种伤害定义、两个伤害标签、12 个分类标签均有效；91 条英文翻译无重复键，15 个死亡消息分支及其参数齐全。
- JAR 检查：全部生成 JSON 与源码逐字节一致；语言资源仅一份；图片与两段 OGG 均存在；没有缓存、旧包格式或未展开的模板变量。
- 最终 JAR：111,036 字节，SHA-256 `b07d79497332321718f0bcb4994448f882a0e7c64ee8bae40320d6d4594bc7ed`；确认已包含 bannerFile，并移除旧 logoFile。
- 专用服务器：DEDICATED_SERVER 环境加载 MFQM 0.1.0，世界达到 Done；启动完成后执行 datapack list 和 stop，Gradle 正常成功退出。26.2 的模组数据汇总到 mod_data 包。
- 客户端：加载 MFQM，重载 mod/mfqm 资源，OpenGL、声音引擎及 GUI 纹理初始化完成。启动验证后关闭测试进程。
- 配置：实际生成 mfqm-server.toml 和 mfqm-client.toml。修复结束时没有遗留客户端或服务器测试进程；git diff --check 通过。

验证使用本机 JDK 25；数据工具的辅助 JDK 21 通过命令行指定本机已有安装位置，没有写入项目配置。首次下载遇到的临时连接失败通过重试解决。服务器测试命令在世界启动后发送，避免启动前命令携带未初始化的维度上下文。

日志还有本机 Windows 性能计数器诊断和上游 shader/翻译警告，未阻断客户端或服务器加载。本次修复没有修改系统配置，也没有改写或接受 EULA；NeoForge 的开发模式完成了运行验证。

## 范围

当前框架已可构建和运行；流沙方块、生物、装备、世界生成等完整玩法仍属于后续移植任务，现有配置中对应的开关也尚无玩法消费者。本次不宣称功能移植已完成。
