> Historical record: this document describes the earlier Minecraft 1.21.5 repair. The current target is Minecraft 1.21.11; see [the current README](../../../README.md).

# Minecraft 1.21.5 修复计划

1. 增加 `tools/verify_jar.py`，检查成品版本依赖、字节码、生成资源、伤害引用、声音字幕和素材；先在旧成品上确认失败。
2. 更新 `gradle.properties`、`build.gradle`、模组 TOML 和四个使用新版标识符/资源键的 Java 文件。用 Java 21 编译解决具体 API 差异。
3. 修复声音资源，修正配置注释，更新 README 的运行目录、产物名和当前修复记录。
4. 由独立子代理修订玩法路线图；主代理复核是否覆盖审查 R01–R06 及 1.21.5 装备 API。
5. 执行数据生成并比较两次结果，完成干净构建和成品校验。
6. 在隔离的 `run/1.21.5` 目录验证客户端初始化和专用服务端正常启动/退出。记录限制与验证证据，保留未提交工作区。

依赖安装和 Gradle 缓存更新属于构建验证。运行期间保持服务端仅绑定回环地址；不改动旧测试世界或替用户接受 EULA。
