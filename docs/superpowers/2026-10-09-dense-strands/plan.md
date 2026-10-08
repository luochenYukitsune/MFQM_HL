# 实施计划

1. `client/CompactStrandStyle.java`：提供最多 8 根独立丝的稳定散布角度；更新对应纯 Java 检查。
2. `client/WetAdhesiveStyle.java`：稳定随机提高身体端点、为同一接触分配不同且完整浸没的根部；定向检查随机稳定性、覆盖范围和薄层边界。
3. `client/RenderedAdhesiveSurface.java` 与 `client/AdhesiveTetherRenderer.java`：每个接触只计算一次液面，生成独立完整网格、逐根短距离淡出和回缩。
4. `ModConfig.java`、汉化资源和 `tools/localize_zh_cn.py`：增加 1～8 的客户端密度设置，修正旧的“五条细丝”说明；维护未执行的客户端夹具断言。
5. 编译和运行两个定向纯几何检查、打包 0.6.10-dev、校验资源，更新 README、玩法与修复记录；自审后保存 Git 提交并交付 JAR。
