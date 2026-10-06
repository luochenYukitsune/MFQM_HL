# Minecraft 1.21.11 迁移设计

用户已明确要求把项目目标从 1.21.5 改为 1.21.11。沿用已修复的基础框架，保留现有资源、配置键和伤害平衡，迁移构建目标、源码 API、成品校验和当前开发说明。

采用官方 1.21.11 MDK 当前的 NeoForge 21.11.45、Java 21、ModDevGradle 2.0.148；现有 Gradle 9.2.1 Wrapper 保持固定。Minecraft 元数据要求精确 `[1.21.11]`，开发运行目录自动隔离到 `run/1.21.11/`。

根据目标版本文档和实际编译适配 Identifier、资源键及数据生成接口。路线图也改为 1.21.11，重新核实附件同步、传输 API、事件、渲染和模型要求。之前的 1.21.5 报告保留为历史记录。

验收采用成品校验失败/通过、Java 21 编译、两次数据生成比较、干净构建、客户端初始化、专用服务器启动与五种伤害调用。当前没有完整流沙玩法，本次版本迁移不新增整套玩法。

参考：[官方 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.11-ModDevGradle)、[21.11 发布说明](https://neoforged.net/news/21.11release/)、[1.21.11 开发文档](https://docs.neoforged.net/docs/1.21.11/gettingstarted/)。
