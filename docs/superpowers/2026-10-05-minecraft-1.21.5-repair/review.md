> Historical record: this document describes the earlier Minecraft 1.21.5 repair. The current target is Minecraft 1.21.11; see [the current README](../../../README.md).

# Minecraft 1.21.5 修复复核

2026-10-05，针对当前未提交工作区完成独立只读审查和主代理验证。

## 审查结论

现有 Java 源码、构建配置、元数据和资源未发现阻断问题。独立审查对照 NeoForge 21.5.98 实际源码核实了数据生成、动态伤害注册表、模型提供器，以及路线图中的呼吸事件、附件同步和网络线程要求。运行验证结果见 [最终报告](final_report.md)。

## 复核发现与处理

1. 成品校验仅检查 NeoForge 版本范围前缀，会错误接受 `[21.5.0,)` 和非法范围 `[21.5.invalid`。已改为严格要求本次构建声明的 `[21.5.98,)`；内存中修改新 JAR 元数据的负例检查确认两种错误范围均被拒绝，正确成品通过。
2. 路线图漏列已有语言键对应的 Wax Leaves、Moor Grass 和 Filter。已补入内容清单及对应方块、物品任务，保留完整的 91 个语言键。

## 范围核对

- G01：构建和源码已改为 Minecraft 1.21.5 / NeoForge 21.5.98 / Java 21，元数据使用目标版本的 `logoFile`。
- G02：两条蜜蜂声音绑定已有字幕键，移除无效的 `category` 字段。
- R01–R06：路线图明确身体与眼部接触的区别、可下沉碰撞、按世界解析伤害类型、运行时配置与数据生成的边界、单一呼吸流程及手动附件同步。
- 路线图同时修正 1.21.5 装备、客户端流体扩展、注册属性 ID 和物品/流体处理器要求，保留 9 个阶段与 41 个任务编号。
- 保留现有伤害标签和平衡。焦油抗火规则仍待后续玩法设计确定。
- 当前仍是基础框架；尚未实现的流沙、生物、物品、网络交互和世界生成没有被标记为完成。

## 核对依据

使用 [官方 1.21.5 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.5-ModDevGradle)、[NeoForge 21.5 发布说明](https://neoforged.net/news/21.5release/)、目标版本开发文档及本地生成的 `build/moddev/artifacts/neoforge-21.5.98-sources.jar`。此前 26.2 验证记录已加历史说明，当前验收采用本次 1.21.5 的实际构建与运行结果。
