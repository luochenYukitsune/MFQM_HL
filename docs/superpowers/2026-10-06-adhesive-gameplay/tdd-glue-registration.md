# 胶水注册与胶水桶：RED / GREEN 记录

范围：已批准计划任务 3。仅当前工作目录；没有修改桌面副本或 GitHub，没有自行提交。

## RED

2026-10-06，先在 `ItemPortChecks` 加入通过实际游戏注册表查询的预期：`glue`、`flowing_glue`、胶水液体方块和胶水桶。运行 `python tools/smoke_port.py`，编译和服务器启动成功，行为断言按预期失败：

```text
IllegalStateException: Glue source fluid must be registered
BUILD SUCCESSFUL in 23s
```

这证明测试捕捉到未注册的新内容，而不是类缺失或编译错误。

随后先加入注册和普通无序配方，实际执行配方匹配、组装及 `getRemainingItems`。第二次服务器运行按预期失败：

```text
IllegalStateException: Water bucket container transfers into glue output; cannot duplicate an empty bucket
BUILD SUCCESSFUL in 29s
```

完整第二次 RED 日志见同目录 `glue-recipe-red.log`。普通无序配方会输出胶水桶并另退空桶，一只输入桶因此变成两只。

## GREEN

实现 `GlueBucketRecipe`：委托原版 `ShapelessRecipe` 完成匹配、组装、配方书显示、JSON 和流编码，只对水桶输入取消额外剩余物；其他输入的剩余物沿用原版规则。使用独立 `mfqm:glue_bucket` serializer，不修改原有配方。

`python tools/smoke_port.py --reload` 通过：

```text
MFQM_PORT_CHECKS_COMPLETE checks=42 items=179
MFQM_RELOAD_CHECKS_COMPLETE longStick=true compat=0
BUILD SUCCESSFUL in 31s
PASS: dedicated-server gameplay assertions and resource loading
```

完整 GREEN 日志见同目录 `glue-registration-green.log`。新增三组行为断言，保留原有 39 组。覆盖注册配对、真实无序配方精确原料与数量、拒绝空桶原料、容器守恒、胶水桶作为其他配方原料时退空桶、放置/收取源、拒绝流动胶水、无权限放置/收取不改变世界和物品。

## 接入与资源

当前仅新增胶水后的数量：50 方块（49 旧身份全部保留）、14 流体类型、28 source/flowing fluid 身份、179 物品、163 个可见创造物品。胶水本身不注册无用途的方块物品；胶水桶由现有创造栏遍历自动加入。核心配方从 205 增至 206，旧配方未移除。后续粘鼠板会进一步增加数量。

新增资源引用：`blocks/glue_still`、`blocks/glue_flow`、`items/bucketofglue`。本任务只编写 JSON，PNG、透明渲染层和客户端流体纹理由主代理制作与接入，尚未在本任务验证客户端显示。

胶水 `SinkingMaterial` 使用 stillSink=0、struggleSink=0、mobility=.03、bootLimit=0、acceptsBuoyancy=false、usesAir=true；累计挣扎下陷由后续 `AdhesionController` 负责。普通 `SinkingMotion` 仍有负重附加下陷，因此主代理必须对胶水向下速度统一覆盖，才能满足停止挣扎后停止下陷的设计。

额外共享文件变更经过主代理确认：`MFQM.java` 注册配方 serializer；`ItemGameplayHooks.java` 仅补胶水桶回收映射；`PhysicsPortChecks.java` 仅注册数量段改为明列的旧 49/13 身份集合加胶水，并保留逐项行为断言。其余物理内容未修改。`ModCreativeTabs.java` 无须改动。
