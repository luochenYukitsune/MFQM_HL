# 0.6.9-dev 修复交付

针对“面条状黏丝、根部露出胶水”的反馈，连续黏丝改为有实体厚度的扁胶带；胶水及涂胶板的主宽度比 0.6.8-dev 增加 50%。脚部连接点朝向各自根部，减少松垂及横向散开的连接。

新增客户端液面角点计算，显示根部的整个端面收在原生液面最低角下，且避开方块侧边和底部。流体变化时重新计算，过薄时缩小根部或停止绘制。保留无分叉、3D 厚度、贴脚薄壳、第一人称脚点对齐、身体覆盖及所有现有玩法规则。

## 已完成的轻量检查

- `gradlew.bat verifyWetAdhesiveStyle jar --console=plain --max-workers=2` 成功；Java 主源码与测试源码编译通过，1786 项定向几何检查通过。
- `python tools/verify_jar.py build/libs/MFQM-neoforge-1.21.11-0.6.9-dev.jar` 通过，目标为 Minecraft 1.21.11 / NeoForge 21.11.45+ / Java 21。
- `python tools/verify_port.py build/libs/MFQM-neoforge-1.21.11-0.6.9-dev.jar`：0 errors。

按用户要求，本次没有运行完整 `build` 回归、客户端、服务器或第三方合载实测，也没有生成图片。上述结果证明代码可编译、几何边界断言和打包资源通过检查；实际观感待用户进游戏测试。

## 产物

- 路径：`build/libs/MFQM-neoforge-1.21.11-0.6.9-dev.jar`
- 大小：2,495,872 字节。
- SHA-256：`84a8e640a8d53ed0297201242257e1e11bce9f1c6b17773be6a4a86e8dc8d426`
- 同步协议保持 6。替换实例中的旧 MFQM JAR，避免同时加载多个版本。

所有修改位于当前工作目录；桌面源码副本与用户的游戏实例未修改。未发布远程 release。
