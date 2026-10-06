# Minecraft 1.21.11 迁移结果

2026-10-05：已将现有 MFQM 基础框架从 1.21.5 迁移到 **Minecraft 1.21.11 / NeoForge 21.11.45 / Java 21**。所有改动保留在当前未提交工作区。

## 修改

- 更新构建目标与模组描述，Minecraft 依赖精确为 `[1.21.11]`，NeoForge 最低版本为 `[21.11.45,)`。
- 将标识符与资源键调用改为目标版本的 `Identifier`、`ResourceKey.identifier()`。
- 沿用固定 Gradle 9.2.1、ModDevGradle 2.0.148 和 Java 21 工具链。
- 更新成品校验、README 和玩法路线图；重新核实附件同步、物品/流体传输、渲染、粒子、数据生成、呼吸和流体运动接口。
- 开发运行目录隔离到 `run/1.21.11/`，干净构建清除旧目标 JAR。旧测试世界和历史报告保留。
- 保留全部 65 个配置键及默认值、91 个语言键、19 个生成 JSON、两条声音字幕和原 PNG/OGG；伤害平衡未改变。

## 验证

1. 新版成品校验器拒绝旧 1.21.5 JAR，明确报告 Minecraft 和 NeoForge 依赖错误。
2. Java 21 源码编译通过；`clean build` 通过，耗时 15 秒。当前没有独立单元测试源码，Gradle `test` 为 `NO-SOURCE`。
3. 两次 `runData` 均通过，每次写入文件数均为 0；19 个 JSON 的路径与 SHA-256 与迁移前完全一致。
4. 新 JAR 校验通过，24 个模组 class 均为 Java 21 字节码版本 65；元数据已展开，生成数据、伤害引用、死亡翻译、字幕和素材完整，无重复 ZIP 条目或生成缓存。
5. 修改内存 JAR 元数据的负例检查通过：版本范围 `[21.11.0,)` 和非法范围 `[21.11.invalid` 均被拒绝。
6. 专用服务器使用 FancyModLoader 10.0.36 成功加载隔离世界。等待区块与实体就绪后调用五种 `mfqm` 伤害类型，五只猪的生命值均从 10 降至 8；测试实体及强制加载状态清理完成，服务器正常保存退出，Gradle 报告 `BUILD SUCCESSFUL`。
7. 客户端确认 Minecraft 1.21.11、NeoForge 21.11.45、MFQM 加载，以及 OpenGL、模组资源包、OpenAL 声音引擎和 GUI 图集初始化。观察完成后结束本次测试进程，不将该项描述为客户端 Gradle 正常退出。
8. 服务器配置实际生成 59 项，客户端配置 6 项；COMMON 为空。服务器仅绑定 `127.0.0.1`、动态端口，使用 `smoke-world`；没有写入 EULA 接受文件。
9. 独立范围与质量审查通过；源码、文档及 Git 差异空白检查通过。

本机验证使用 Microsoft OpenJDK 21.0.9+10 与 Python 3.12.9。第一次依赖下载发生暂时性连接重置，重试成功；未关闭证书验证或修改仓库网络配置。

## 成品

`build/libs/MFQM-neoforge-1.21.11-0.1.0.jar`，111,067 字节。

SHA-256：`5aad8b618cf9a7de1427ea006b866c0f1473b147df18eda3e3b802542db985dc`。

```powershell
python tools/verify_jar.py build/libs/MFQM-neoforge-1.21.11-0.1.0.jar
```

## 本地证据与限制

日志及测试控制脚本位于被 Git 忽略的 `run/1.21.11/`：

- `data/logs/latest.log`
- `validation/generated-1215-baseline.json` 与 `generated-12111-result.json`
- `validation/server_smoke.first-attempt.log` 与 `server_smoke.log`
- `server/logs/latest.log`
- `validation/client_smoke.log` 与 `client/logs/latest.log`

首次服务器伤害测试未找到实体，原因是旧测试命令没有等待区块/实体可用；增加就绪确认后重跑通过。该次失败记录已保留。客户端有原版命令歧义警告，没有观察到阻止模组初始化或资源加载的错误。

当前仍是基础框架，完整流沙下沉、生物、物品和世界生成尚未实现。本次不声称已验证未来玩法、多人下沉、实际字幕播放或界面视觉布局。后续计划见 [路线图](../plans/2026-07-29-mfqm-neoforge-port-plan.md)，独立审查见 [复核记录](review.md)。
