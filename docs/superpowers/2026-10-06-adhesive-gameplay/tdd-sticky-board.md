# 粘鼠板：实际服务器 RED / GREEN

仅开发工作目录；使用 `tools/smoke_port.py` 的隔离、仅本机监听的 `smoke-world`。不操作桌面副本或 GitHub。

## RED

先添加 `StickyBoardPortChecks` 的真实注册断言，并挂入 `ModPortChecks`，尚未注册板子。

2026-10-06 22:48，运行 `python tools/smoke_port.py`。服务器正常启动、Gradle 运行本身成功，服务器玩法断言失败：

```
MFQM_PORT_CHECKS_FAILED
java.lang.IllegalStateException: Sticky board must be registered
BUILD SUCCESSFUL in 29s
```

验证脚本最终退出 1，确认缺失注册产生实际失败，而非只检查测试源码文本。

随后补齐真实方块 use、ItemStack 容器转换、真实 loot table、BlockItem 回收重放、邻居支持移除、混合木板配方测试，再实现新块和资源。

## GREEN

2026-10-06 22:56，主控制器源码和留靴模块可编译后，运行相同 `python tools/smoke_port.py`：

```
MFQM_PORT_PASS board: exact thin collision, empty/nonempty adhesion, real right-click and single bucket return
MFQM_PORT_PASS board: full/creative refill, charge consumption, real loot and re-placement preserve coating without duplication
MFQM_PORT_PASS board: adjacent boards/glue retain support, floating placement rejected and removed support breaks only affected board
MFQM_PORT_PASS board: actual tagged mixed-wood recipe produces three empty bases
MFQM_PORT_CHECKS_COMPLETE checks=49 items=180
BUILD SUCCESSFUL in 29s
PASS: dedicated-server gameplay assertions and resource loading
```

四组板子检查全部通过，同轮保留旧内容和已接入的新胶水、留靴测试；运行日志无 ERROR。服务器配置因新增玩法配置字段自动补齐，属正常升级。

本轮证明板子真实块/物品/配方/掉落行为。控制器实际受困和画面将由主代理后续接入验收；不把注册/交互通过当作整体手感、透明拼接或新物理通过。

## 接口与边界

- `StickyBoardBlock.CHARGE`：整数 0～7；默认 0，正值才有涂层。
- `isCoated(BlockState)`、`coatedState(int)`、`coatedStack(int)`。
- `consumeCoating(ServerLevel, BlockPos, int)`：返回剩余涂层；非正数不补胶，不载入未加载区块，不操作非板方块，扣尽后底板保留。
- 底板碰撞厚度 1/16；要求下方完整上支撑面。移除支持通过实际邻居更新破坏，按真实 loot 回收状态。
- 胶水桶右键补满；满板不消耗胶水也不溢到旁边。生存一个胶水桶变一个空桶；创造不消耗。不允许建造或交互的位置保持原状态和物品。
- `minecraft:copy_state` 将剩余 charge 保存到 `minecraft:block_state` 数据组件，普通 BlockItem 按组件实际重放；无需方块实体。
- 同一物品 ID 在创造栏有空板和满胶板，1.21.11 的 `minecraft:select` / `minecraft:block_state` 选择不同模型。
- 注册数量为 51 块、180 物品；旧内容保留，创造可见条目为 165（包含满胶变体）。

实际束缚、挣扎和目标筛选由统一控制器负责，本模块不重复施加物理拉力。
