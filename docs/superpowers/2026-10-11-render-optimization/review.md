# 辅助审查：最终保留的渲染小优化

2026-10-11。辅助审查完整读取 95 个非客户端主源码、当前 14 个 Java 测试和全部 27 个 Python/PowerShell 工具；逐文件清单见 [auxiliary-read-coverage.txt](auxiliary-read-coverage.txt)。客户端本次重点文件已完整读过，其余客户端由主审负责；辅助审查读取范围不等同于全部客户端代码。

## 结论

最终保留差异未发现可证实的阻断问题。优化保持物理聚合、视觉密度、随机种子、根部约束、皮肤表面选择和延迟绘制快照。没有改预测、网络节奏、世界生成密度或可选模组生命周期。

- `AdhesiveRules.java:49`、`AdhesionController.java:206`：`isActive` 直接调用原 `forceFor`，与原单元素 `evaluate(...).activeBonds()>0` 的生命周期判断相同。零伸长和零刚度仍算有效连接；失效、非有限值、越界及乘法溢出仍拒绝。仅去掉生命周期检查的单元素列表、64 元素聚合数组和 Result，最终所有连接的 `evaluate`、力上限、相反力抵消及连接次序不变。
- `WetAdhesiveStyle.java:144`：每个环顶点只裁剪一次，原始环仍用于原绕序判断，最终四个剪后顶点重算法线。根帽、身端帽和退化面过滤仍存在；根端中心本来就不会被裁剪，身端中心恰好在选中的皮肤平面上。共享不可变 Vec 不会引入新的分叉或跨帧修改。1024 根与旧逐面裁剪逻辑逐 Quad 精确相等的回归覆盖颜色、坐标、法线和次序。
- `AdhesiveTetherRenderer.java:53`：一次提取内复用相同 partial tick 的 `targetPosition`，其间没有位置修改。局部皮肤查询仍减 `skinOffset`、命中后加回；FPM 采样和平移保持原实现，远近/断裂分支保持原几何。延迟回调仍捕获不可变列表及 light/color 数值，不持有会被复用的 State。
- `TranslucentGeometry.java:28`：将四点临时数组展开为四次相同顶点提交，顺序、UV、alpha、光照、法线与覆盖层参数均相同。
- `ClientPortChecks.java:61` 和 `tools/verify_jar.py:35`：图标资源入口改为根目录 `icon.png`，256×256 PNG 头/尺寸及 `logoBlur=false` 在打包检查中验证，实际游戏检查用 NativeImage 解码并关闭流和图像。此验证只在 installed-check 模式运行。辅助审查没有自行声明 PCL 会从本地 JAR 展示该图标。

## 未交付的皮肤查询预计算实验

建议撤去 Prepared，主审已撤去对应 Foot API、专项测试和 benchmark 源码。它的不可变快照、volatile/synchronized 发布及 FPM 平移逻辑在正确性上成立；但懒构建仅避开干地无查询时的三角形预计算。实际查询一旦发生，复杂皮肤仍为每姿势创建每面 Surface 和两个 Triangle。最终测量的 32 条查询慢约 2%～8%，密集 256 条快约 9%～18%，简单六面路径也有少量包装成本。当前用户要小优化，保留已有 Foot 每 target/tick/partialTick 缓存及原查询算法更合适。实验数据保存在 `evidence/rejected-skin-query-benchmark.csv`，不代表游戏 FPS。

## 其他可落实的小候选（本次未改）

这些是读取代码确认的重复工作，不是已测得的 FPS 改善，也不是游戏缺陷。

- `LegacyStateBlock.java:53`、`:59`：动态泥潭/泥泽碰撞查询重复创建固定尺寸 VoxelShape。可把三个泥潭形状和一个泥泽形状提升为静态常量，仍每次计算当前 mudDepth/邻区块条件。风险低；不能缓存依赖邻块的最终选形结果。其余普通 BlockState 已有原版形状缓存，收益不要泛化。
- `MediumReactions.java:230`：每次融合检查创建目标字符串数组、Identifier 和 TagKey。可静态保存有序目标/TagKey 配对，检查时仍调用当前 `state.is(tag)`，保留数据包重载后的标签成员与优先级。风险低，主要影响大量融合/投射物处理，普通帧收益有限。
- `QuicksandPhysics.java:324`：连续介质顶部探测重复读取同一 next/top BlockState。局部变量可复用同次循环读取，保留 `hasChunkAt` 边界和实际流体高度计算。风险低，不能将整个 contact 跨移动、传送或介质变动缓存。
- `MediumReactions.java:66`：所有随机 tick 先扫描水/热邻居，一些 switch 分支根本不使用。可把纯查询移到实际需要的分支，保持 RNG 调用及方块更新次序。风险较低，主要改善集成服务器耗时，需原反应回归。
- `LegacyResistance.java:16`：返回不可变 Profile 时重复创建常量配置。可复用固定材质常量，少数 variant 曲线用有限档表；必须保留原整数除法和公式。HotSpot 可能已消除部分分配，先测量再实施。

暂不建议顺便改空地 Jump/State 重建或周期同步：`AdhesionController.java:94`、`QuicksandPhysics.java:177`、`:297` 确有重复初始化/同步，但这些涉及恢复、跨维度、覆盖层衰减及服务器权威。应独立设计有效状态转换/脏字段测试，不属于此次低风险渲染小修。

## 验证和证据边界

辅助改动按测试先行：新 `isActive` 缺失时，规则脚本出现 javac 红灯，该诊断只保留在工具输出；目录里没有红灯日志文件，不声称存在。最小实现后亲自运行 `tools/verify_adhesive_rules.ps1`，8415 项通过，日志见 `adhesive-green.txt`。主审报告最终完整 Gradle build 通过；辅助审查没有另外启动 Gradle 或大范围客户端测试。

主审的共享环顶点微基准包含每次几何构建，256 根耗时约降低 9%～21%，减少分配约 1.08 MB / 1.69 MB（3/6 段），见 `evidence/geometry-benchmark.csv`。JIT 标量替换会影响数字，这不能换算为整机 FPS。最终实际客户端/FPM/3D Skin Layers 回归结果见主审验收报告。

## 客户端夹具同步补充审查

最终回归曾在跳起阶段收到 66 个尚未标记断裂的客户端实体，附件和服务端仍为 64，因而旧夹具在半个同步快照上错误断言胶膜数量。检查夹具现等待实体数、附件数和两脚各一个 cuff 同时就绪；就绪后，原有胶膜恰好两片、全部根部与皮肤几何检查仍立刻失败，不把几何错误当作同步等待。

跳跃阶段只在第一个达到六 tick 的检查严格验证原 `.12～.31` 高度区间，随后停止跳跃输入；等待不重置原 900 tick 阶段超时。站立、第一人称对齐、成功日志和可选截图均在完整同步检查成功后继续。同步期间服务端诊断降低到每十 tick 一次，避免每 tick 阻塞查询。跳高验证与稍后的稳定几何检查是两个时点，本次没有截图，也不声称稳定几何一定采自腾空第六 tick。生产渲染、网络发送和物理生命周期没有为此改变。
