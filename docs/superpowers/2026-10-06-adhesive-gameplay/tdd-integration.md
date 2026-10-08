# 服务器接入与回归证据

在纯规则、注册、板、留靴、世界生成和动作模块各自的 RED/GREEN 记录之外，主代理完成实际 NeoForge 服务器接入。

## 真实失败

- 初次静止深胶水回归在 22:57:08 报 `Still glue must cancel gravity and old equipment/load sinking`。旧重力与装备负重仍在作用；生产物理改为胶水静止保持深度，仅由接受的挣扎产生下陷增量。
- 22:58:52 接入回归在胶水池注册检查失败，随后完成世界生成模块。
- 06:51:21 增加实体插入回调重入测试，报 `pending insertion callback cannot recover a duplicate of feet equipment`。插入成功回调可在原装备尚未清空时回收新实体；增加转移锁，插入完成并清空装备后才开放回收，异常时丢弃未完成实体。
- 后续 59 组通过，但日志出现未初始化 `WORLD_SURFACE_WG` 高度图错误。修正测试边界地形的高度图初始化，继续保留日志 ERROR 检查。

## 审查后补充回归

专用请求跨服务器 tick 接受、原始短按及 OS repeat、板上救援速度、同板补胶与切换新板保留冷却、胶水长期接触后的离开、水洗残留不断有效连接、立即移除物理锚点及六 tick 视觉回缩。

药水测试使用固定随机种子 86610，并在实际 RandomSource 中验证三十个 `nextInt(3)` 都非零，隔离合法化学副反应。仍严格要求中心 LEVEL 4、四周 LEVEL 7，没有放松生产行为断言。

## 当前通过证据

2026-10-07 两次独立服务器进程均通过 **61 组**、真实 `/reload` 及无 ERROR 检查。第一次将命名、损伤、附魔钻石靴转移并保存；第二次从真实区块实体存储加载唯一留靴，核对完整组件并实际回收一次。

- `run/1.21.11/validation/gameplay-smoke-persistence-prepare.log`
- `run/1.21.11/validation/gameplay-smoke-persistence-recover.log`

这些运行数据被 Git 忽略；关键结论和命令保留在验收报告。客户端输入与模型测试另在实际 Minecraft 客户端执行，最终结果以 final_report.md 为准。
