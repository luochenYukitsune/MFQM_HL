> Historical record: these checks targeted Minecraft 26.2. The current target is Minecraft 1.21.5; use ../2026-10-05-minecraft-1.21.5-repair/ and the root README for current instructions.

# MFQM 26.2 基础框架修复设计

用户已在全面审查后授权“进行完全修复”。修复覆盖审查发现的 R01–R12，保持 Minecraft 26.2、Java 25 及现有配置/资源；尚未实现的流沙方块、生物与玩法不属于本次缺陷修复。

## 构建与加载

采用官方 26.2 MDK 的 Gradle 9.2.1、ModDevGradle 2.0.148、Foojay 1.0.0、NeoForge 26.2.0.88，补全 Wrapper 并验证发行包校验和。修复模组元数据的版本展开、依赖范围和必需 license 字段。`All Rights Reserved` 保持仓库当前未授予开源许可的状态。移除旧版 pack.mcmeta，让 NeoForge 提供当前版本的资源包元数据。

## Java 与数据

修复配置类命名遮蔽，使用 Identifier，删除已经不再存在的护甲材质注册表占位。数据生成改为 26.2 的 GatherDataEvent.Client，采用当前 ModelProvider，语言以已有完整 JSON 为唯一来源。使用动态注册表 bootstrap 生成五种 DamageType，再让标签引用实际存在的条目。生成的 JSON 纳入源码和 JAR，只忽略数据生成缓存。

## 验证

先保留原有失败证据，再执行 Wrapper、Java 编译、完整构建、runData、重复生成一致性和 JAR 内容检查。执行客户端及专用服务器启动；记录实际日志、依赖/环境限制，不代用户接受 EULA。独立复核修改是否覆盖审查问题且没有增加未请求的玩法。

## 运行验证中补充的修复

创作物栏在没有注册物品时展示原版沙子，后续自动列出实际注册物品，满足基础框架可见的原设计。补齐五种伤害的基本、玩家归因和命名物品归因死亡消息。将已弃用的 logoFile 改为 bannerFile 并沿用现有宽图；接通 runServer 的标准输入，使终端命令和正常退出可用。三种运行配置分别使用 run/client、run/server、run/data。
