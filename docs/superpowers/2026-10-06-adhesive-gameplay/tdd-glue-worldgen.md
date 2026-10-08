# 胶水池与遗迹陷阱验证

2026-10-06，任务 12。仅修改当前工作目录；不修改桌面副本、GitHub 或旧地形池的加权选择。

## 测试先行

先新增 `GlueWorldgenPortChecks` 并接入隔离服务器检查。初始断言要求 `mfqm:glue_pool` 已注册；此时没有胶水池 production 类、注册或 datapack。

检查调用真实 `ConfiguredFeature.place` 和 `DesertTombPiece.postProcess`。测试的 WorldGenLevel 代理只提供确定生物群系和拒绝某写入位置，其余操作交给实际 ServerLevel；不会以替代算法冒充实际放置。夹具保存并还原方块，配置恢复使用 finally。

RED：root 在 2026-10-06 22:58:52 运行隔离服务器，`MFQM_PORT_CHECKS_FAILED IllegalStateException: Glue pool feature must be registered`，构建本身成功，约 22 秒。此时没有新增胶水池 production 实现。日志：`run/1.21.11/validation/gameplay-smoke.log`。

GREEN：2026-10-07 06:48:24 隔离服务器完整通过 `checks=59 items=180`，构建成功约 46 秒，无运行 ERROR。包括新增世界生成 5 组、粘鼠板、胶水控制器、留靴和绳索。独立日志已保存为 `tdd-worldgen-green.log`，避免后续 smoke 覆盖证据。

此后追加边界候选、维度排除、配置频率和分区块遗迹放置一致性断言。06:49:29 再次运行在已有 `EntityPortChecks.java:107` 的固定药水 LEVEL4 断言失败；此次尚未到达世界生成增强断言，不能将此次算作 GREEN。已告知 root 检查原药水夹具邻域残留，增强验证待 root 重跑。

root 后续确认药水失败根因为融合反应的随机消耗，负责将化学反应夹具控制化。06:52:38 增强断言全通过，但 edge candidate 在实际已生成 ServerLevel 区块读取世界生成专用 `WORLD_SURFACE_WG` 时触发 `Unprimed heightmap` ERROR，smoke 正确拒绝该运行。测试在重建平坦 terrain 后显式 `Heightmap.primeHeightmaps`，用实际方块预初始化 WG surface；production 世界生成高度图保持原样，不抑制日志。此修正等待 root 最终回归。

## API 依据

- 当前项目提取的 Minecraft 1.21.11 `FeaturePlaceContext`、`ConfiguredFeature`、`StructureTemplate`、`TemplateStructurePiece`、`WorldGenLevel` 源码。
- [NeoForge 1.21.11 生物群系修改器文档](https://docs.neoforged.net/docs/1.21.11/worldgen/biomemodifier/)：新的 PlacedFeature 通过 biome modifier 注入生成步骤；避免同一 feature 多处插入造成排序环。

## 范围

注册、湿润主世界 biome tag、独立池生成、可关闭/配置频率和深度、浅深池、底面/侧壁密封、候选区块写入边界、拒绝写入时不挖半池、少量遗迹地面板、宝箱材料。旧区块不会被回填。
