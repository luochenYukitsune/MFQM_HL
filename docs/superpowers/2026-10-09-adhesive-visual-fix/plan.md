# 执行计划

1. WetAdhesiveStyleTest 增加根部近底、平顺胶带和加宽断言，先记录失败。
2. WetAdhesiveStyle / AdhesiveTetherRenderer 修正根部深度、均匀收腰、宽度与透明度，身体端与物理规则不变。
3. StickyBoardConnections 延迟修复加载区块内旧板的连接；不强制加载相邻区块。
4. AdhesiveVisualClientChecks 与现有安装测试启动器增加单独的简化范围。检查实际放置、旧状态刷新、移除、模型覆盖和根部范围，截图两张。
5. 编译打包、重点回归，查看游戏日志和截图。审查修改，产出 0.7.1-dev 本地包并安装到指定实例（先备份旧模组）。桌面副本、GitHub 发布不变。
