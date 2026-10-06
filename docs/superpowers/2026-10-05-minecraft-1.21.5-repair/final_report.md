> Historical record: this document describes the earlier Minecraft 1.21.5 repair. The current target is Minecraft 1.21.11; see [the current README](../../../README.md).

# Minecraft 1.21.5 修复结果

2026-10-05：已完成本次审查确认的现有框架兼容性、声音资源和玩法路线图修复。目标固定为 Minecraft 1.21.5、NeoForge 21.5.98、Java 21。工作区保持未提交状态。

## 修改结果

- 固定目标依赖与 Java 工具链；Minecraft 依赖范围为 `[1.21.5]`，NeoForge 最低版本为 `[21.5.98,)`。
- 使用目标版本的 `ResourceLocation`、`ResourceKey.location()` 和 `logoFile`。保留原作者署名、许可证、模组 ID 和原配置键及默认值。
- 绑定两条已有蜜蜂声音字幕，保留 PNG、OGG 和全部 91 个语言键。
- 产物名称包含 Minecraft 版本；开发运行目录隔离在 `run/1.21.5/`，旧版本构建成品由干净构建清除。
- 修订玩法路线图中的已知 API 和设计错误，补全 Wax Leaves、Moor Grass、Filter 清单。完整玩法仍按路线图逐步实现。
- 增加 `tools/verify_jar.py`，检查成品版本、Java 字节码、生成资源、伤害引用、死亡翻译、字幕和素材。

## 验证结果

1. 旧成品校验按预期失败，复现 8 项版本、字节码、图标字段和声音资源问题。
2. Java 21 编译通过。`clean build` 通过，耗时 14 秒；项目没有独立单元测试源码，Gradle 的 `test` 为 `NO-SOURCE`。
3. 两次 `runData` 均通过；19 个生成 JSON 的路径和 SHA-256 完全一致，第二次没有重写资源。
4. 新 JAR 校验通过：24 个模组 class 文件均为 Java 21 字节码版本 65，生成资源完整且与源码目录一致，无重复 ZIP 条目或数据生成缓存。
5. 校验脚本负例通过：拒绝过低的 NeoForge 版本下限及非法版本范围。
6. 专用服务器以 Minecraft 1.21.5 / NeoForge 21.5.98 成功加载隔离测试世界，初始化 MFQM，并正常保存、退出；Gradle 报告 `BUILD SUCCESSFUL`。
7. 服务器在世界就绪后分别调用五种 `mfqm` 自定义伤害类型，五只测试猪均由 10 点生命值降至 8 点，之后清理测试实体和强制加载区块。
8. 客户端完成目标版本模组加载、OpenGL、MFQM 资源包、OpenAL 声音引擎和 GUI 图集初始化。观察完成后结束本次测试进程；该项验证不声称客户端 Gradle 任务正常结束。
9. 生成服务器配置 59 项、客户端配置 6 项，共 65 项；COMMON 配置为空，不产生有效配置文件。服务器仅绑定 `127.0.0.1`，使用动态端口和 `smoke-world`，未读取旧测试世界或写入 EULA 接受文件。

本机验证使用 Microsoft OpenJDK 21.0.9+10、Gradle 9.2.1、ModDevGradle 2.0.148、Python 3.12.9。首次依赖下载遇到暂时性 TLS 连接重置；重试时仅在命令中指定 TLS 1.2，未修改仓库网络配置或关闭证书验证。

## 成品

`build/libs/MFQM-neoforge-1.21.5-0.1.0.jar`，111,054 字节。

SHA-256：`9e46b882a215ed93fa90414eb73858363e60dc8d8f888d3c87d3cfaa88049dd3`。

重复成品校验：

```powershell
python tools/verify_jar.py build/libs/MFQM-neoforge-1.21.5-0.1.0.jar
```

## 证据与限制

本地运行证据保存在被 Git 忽略的测试目录：

- `run/1.21.5/data/logs/latest.log`
- `run/1.21.5/validation/generated-before.json`
- `run/1.21.5/validation/server_smoke.log`
- `run/1.21.5/server/logs/latest.log`
- `run/1.21.5/validation/client_smoke.log`
- `run/1.21.5/client/logs/latest.log`

客户端开发账号的占位令牌导致 Realms 认证失败；日志也有上游资源 URL、着色器和命令歧义警告。这些信息未阻止模组与本地客户端初始化，本次没有验证 Realms 服务。

当前 21 个 Java 文件仍构成基础框架；尚无完整流沙下沉、生物、物品或世界生成行为。因此此次验证覆盖构建、资源、配置、启动和动态伤害加载，不包含未来玩法、多人下沉、字幕实际播放或界面视觉布局测试。现有伤害平衡未改变。

独立审查发现的两项问题已处理，见 [复核记录](review.md)；后续工作见 [玩法移植路线图](../plans/2026-07-29-mfqm-neoforge-port-plan.md)。
