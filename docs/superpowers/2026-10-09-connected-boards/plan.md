# 实施计划

1. 核对目标版本 `BlockBehaviour` / `FenceBlock` 的放置、邻居更新和变换 API。
2. `StickyBoardBlock.java` 增加水平连接属性、放置／邻居更新、旋转与镜像，保留独立胶量和掉落。
3. `tools/connected_boards.py` 生成 multipart 方块状态和九块互不重叠的胶面模型；保留单板物品模型与现有贴图。
4. 定向检查 16 种连接组合、8 个胶量状态的模型覆盖、内部接缝、外圈木边和四角；编译打包，不启动游戏。
5. 更新中文使用提示、README、玩法说明和修复记录，自审、保存 Git 提交并交付包含两项修改的 0.6.11-dev JAR。
