# 实施与验证计划

1. 在 `AdhesiveRulesTest` 增加独立竖向距离、64 条连接、数量归一化和非黏性介质回归；新增纯 Java 接触约束检查。先运行确认失败，再实现 `AdhesiveRules` 与接触采样规则。
2. 修改 `AdhesionController`、`SinkingState` 和 `AdhesiveTetherEntity`：实际脚部接触、同格移动补丝、FIFO 替换、液面跟随及失效清理。保持受困原点和挣扎进度；仅最新的每脚连接绘制黏膜。
3. 修改 `AdhesiveTetherRenderer`：每连接一条主丝、根部宽度约束、远距细节和客户端显示预算。补充服务器独立距离配置。
4. 增加纯显示参数测试；将 `GlueCoatingRenderer` 泛化为全材质覆盖，增加厚度和透明度；使用原生部件即时姿势及 FirstPersonModel 的状态标记，避免延迟提交时错误隐藏或对齐。保持 Skin Layers 间距。
5. 在 `ModConfig` 和中文本地化生成器增加全局及材质设置。修改 `MuddyPlayerLayer` 和第一人称手臂路径；材质 tint 不丢失，缓存有上限。
6. 构建、既有纯 Java/Python 回归；加入实际游戏接触、FIFO、64 丝、第一人称模型及所有材质渲染检查。扩展隔离客户端脚本以选择官方兼容 JAR，并验证其 SHA256。
7. 执行独立服务端、实际 JAR 非飞行客户端、FirstPersonModel 与 Skin Layers 合装。检查日志与必要截图；完成规格和代码质量审查、中文说明与 Git 提交。此次不发布、不修改桌面副本。

按用户节省开支的偏好，由主代理执行与审查，不启动额外子代理。
