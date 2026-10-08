# 任务 1：黏连纯规则的 RED / GREEN 证据

2026-10-06。按维护者已批准的 design.md / plan.md 开发，只改当前工作目录。没有修改桌面副本、GitHub、Gradle 配置、挣扎规则或其他现有文件，没有自行提交。

## 顺序与真实执行结果

先写 `AdhesiveRulesTest.java` 和独立编译运行脚本，再补只有接口和空返回的编译桩。所有下面的 RED 都是成功编译后的行为断言失败，不是缺少类导致的编译错误。

每轮执行同一命令，使用 Java 21、不启动 Minecraft、不访问 Gradle 缓存：

```powershell
$env:JAVA_HOME='D:/download/jdk21/jdk-21.0.9+10'
& ./tools/verify_adhesive_rules.ps1
```

1. **RED：材料规则缺失。** 退出码 1，`AssertionError: missing material profile: glue`。随后实现显式材料映射。
2. **RED：距离和连接状态缺失。** 退出码 1，`AssertionError: exact maximum distance remains attached`。随后实现脚端到锚点的弹性拉力、最大距离边界、失效判定与四个有效连接的上限。
3. **RED：合力未封顶。** 退出码 1，`AssertionError: four anchors obey total force ceiling: 0.8 != 0.045`。随后先合成各方向的力，再按向量范数封顶。
4. **RED：极大有限输入的求和溢出。** 退出码 1，`AssertionError: finite extreme forces do not overflow when summed`。随后改为缩放后求和，在恢复量级前判断上限，避免中间无限值和 NaN。
5. **GREEN：初版。** 退出码 0，`AdhesiveRules: 235 behavioral checks passed`。
6. **边界审查后的 GREEN。** 增加涂胶板距离、零弹性、超额锚点不影响已接受合力、极大反向力相互抵消的断言。退出码 0，`AdhesiveRules: 241 behavioral checks passed`。

最终 241 个断言分为五组：材料、距离/方向、封顶/抵消、生命周期/数量、非法及极端输入。计数包含用于定位问题的共同断言；并不代表 241 种独立游戏场景。

## 稳定集成接口

包名：`com.mfqm.morefunquicksandmod.gameplay`。

```java
AdhesiveRules.MAX_BONDS // 4
AdhesiveRules.MAX_TOTAL_FORCE // .045
record Profile(double maxDistance, double stiffness, double restLength)
record Point(double x, double y, double z) // ZERO, length(), isFinite()
record Bond(Point anchor, Point foot, Profile profile, double strength, boolean valid)
// 四参 Bond 构造器默认 valid=true。
record Result(Point force, int activeBonds, int brokenBonds)
Profile profileFor(String bareMaterialId)
Result evaluate(List<Bond> bonds)
Result evaluate(List<Bond> bonds, double maxTotalForce)
```

`profileFor` 使用裸材料 ID，未知或 null 返回 null。胶水距离 2.5、弹性 .032；涂胶板距离 2.5、弹性 .04；焦油距离 1.2、弹性 .028；蜂蜜距离 1.8、弹性 .018；sinking_slime / mucus 距离 1.8、弹性 .022。上述普通介质松弛长度 .2，板子 .12。

短粗泥潭显式包括 mud、bog、morass、mire、moor、wet_peat、brown_clay、sinking_clay、slurry：距离 .8、弹性 .025、松弛长度 .12。水状 liquid_mire / stable_liquid_mire、普通流沙、干流沙与其他未列材料没有黏丝 Profile。这一映射已与主代理核对。

力始终由脚端指向锚点，不施加压缩推力。距离**等于**最大值仍连接，严格超出即断开；零距离保留连接、拉力为零。strength 大于 1 时取 1，零或负值断开。按原列表次序保留前四个有效连接，失效条目不占名额，超额条目计为断开。调用者列表保持不变。

默认总力封顶 .045 格/tick。配置可降低上限，不能提高硬上限；上限为 0 时连接仍存在但不施力。负数或非有限上限抛出 IllegalArgumentException。无效的 Bond、Profile、坐标、强度，或无法用 double 表示的位移/弹性力，被计为断开，不会污染其他有效连接。

## 核验边界与任务范围

实际检查包括：最大距离后一浮点值、零距离、松弛区、三维方向、强度减半、方向相反抵消、按向量范数封顶、四个锚点上限、移除锚点、耗尽强度、空列表、null 条目和字段、NaN/正负无限、非法 Profile、double 极大值和次正规数。

这层规则不读取世界、对象、配置或时间。服务器控制器负责检测锚点消失、区块卸载、死亡、传送、维度变化和短暂寿命，并把结果作为 Bond.valid 传入。纯规则测试只验证失效信号立即停止回拉，不能替代真实服务器上的这些生命周期检查。靴子、板子涂层、动作、下陷和饥饿留给后续任务。

独立脚本输出位于 `build/validation/adhesive-rules`；不会修改真实游戏存档。主代理负责后续 Gradle 接入、独立规范审查和代码质量审查。
