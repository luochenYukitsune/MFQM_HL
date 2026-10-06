# Minecraft 1.21.11 迁移复核

2026-10-05：独立代理先核对迁移范围，再审查全部 21 个 Java 文件、构建配置、元数据、成品校验脚本、资源、README 和最终路线图。主代理完成构建与运行验证，见 [最终报告](final_report.md)。

## 结论

范围与代码质量审查通过，无剩余阻断问题。没有新增完整玩法或改变现有伤害平衡；当前目标与产物、依赖范围、文档、运行目录一致。

对照精确 NeoForge 21.11.45 / Minecraft 1.21.11 源码确认：

- `Identifier`、`ResourceKey.identifier()`、DamageType 默认构造语义、`GatherDataEvent.Client`、动态注册表数据生成与现有模型/标签提供器均匹配。
- 模组资源包无需手写 `pack.mcmeta`，当前元数据字段和资源路径可加载。
- 路线图中的附件自动同步、MapCodec / Value I/O、事务式 ResourceHandler、ItemAccess、流体层/雾、实体 submit 和粒子提取接口已按目标版本修订。
- 保留 9 个阶段、41 个任务编号、65 个配置键及原内容清单，未将未来玩法任务标记为完成。

## 发现与处理

1. Python 校验器产生的 `__pycache__` 未被忽略，可能进入后续全量暂存。已加入 `.gitignore`，实际 `git check-ignore` 通过。
2. 路线图一处要求优先使用内建附件同步，另一处却无条件新增同类状态包。已改为只有需要超出内建同步的行为时才实现自定义状态协议。
3. 旧服务器测试把召唤与选择器操作放在同一批命令中，首次伤害验收未找到测试实体。测试脚本已增加区块加载及实体可选择性确认；重跑后五种伤害均生效，服务器正常保存退出。现有模组源码不需要为测试时序问题增加补丁。

## 依据

[官方 1.21.11 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.11-ModDevGradle)、[21.11 发布说明](https://neoforged.net/news/21.11release/)、[1.21.11 文档](https://docs.neoforged.net/docs/1.21.11/gettingstarted/)及本地 `build/moddev/artifacts/neoforge-21.11.45-sources.jar`。此前 1.21.5 与 26.2 的报告保留为历史证据，不能作为当前版本运行验证。
