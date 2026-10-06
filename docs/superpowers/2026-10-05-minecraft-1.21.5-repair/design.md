> Historical record: this document describes the earlier Minecraft 1.21.5 repair. The current target is Minecraft 1.21.11; see [the current README](../../../README.md).

# Minecraft 1.21.5 兼容修复设计

用户已确认目标是 Minecraft 1.21.5，并在玩法与版本审查后授权修复。范围为修复现存代码、资源与路线图错误；完整玩法仍按移植路线图逐阶段实现。

## 版本与构建

固定 NeoForge 21.5.98、Java 21，沿用官方 1.21.5 MDK 使用的 Gradle 9.2.1 和现有固定 ModDevGradle 插件。适配 ResourceLocation、ResourceKey.location 等 API，以及 logoFile 元数据。成品文件名包含加载器和 Minecraft 版本。

## 资源与运行目录

连接已有的两条声音字幕，去掉失效的 category 字段。通过官方目标版本资源加载代码确认包元数据可缺省。开发运行目录按 Minecraft 版本隔离，避免 1.21.5 打开上一轮 26.2 的测试世界。

## 玩法方案修订

修正路线图的身体与眼部检测、碰撞、动态注册表、配置生命周期、单一呼吸流程和附件同步设计。保留现有伤害平衡；抗火规则属于尚未确定的设计，不凭名称改变伤害标签。

## 方案取舍

选择完整适配现有基础框架。只修改版本号不能修复源码和字节码不兼容；立即新增全套玩法会超出本次已确认的缺陷范围。

## 验收

先由成品校验复现旧 JAR 的版本与字幕问题，再验证 Java 21 编译、数据生成可重复、成品资源完整和客户端/专用服务端启动。不得将未实现的玩法描述为已验证。
