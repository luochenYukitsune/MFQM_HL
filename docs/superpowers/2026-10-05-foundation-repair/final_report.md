> Historical record: these checks targeted Minecraft 26.2. The current target is Minecraft 1.21.5; use ../2026-10-05-minecraft-1.21.5-repair/ and the root README for current instructions.

# 基础框架修复完成

原全面审查中的 R01–R12，以及验证中发现的创作物栏、死亡消息、banner 元数据和服务器输入问题均已修复。目标仍为 Minecraft 26.2 / NeoForge 26.2.0.88 / Java 25。

最终干净构建、两次数据生成、专用服务器启动及正常退出、客户端初始化和 JAR 内容检查均通过。19 个生成 JSON 随源码保留；测试游戏进程已关闭。

交付产物：`build/libs/MFQM-0.1.0.jar`，111,036 字节，SHA-256 `b07d79497332321718f0bcb4994448f882a0e7c64ee8bae40320d6d4594bc7ed`。

详细修复和验证依据见 [review.md](review.md)，构建命令见仓库根目录 README.md。改动保留在工作区，尚未提交 Git。当前完成的是基础框架修复；完整玩法移植继续按后续路线图实现。
