# 0.7.5-inside 同版本修订验收

日期：2026-10-11。保持 Minecraft 1.21.11、NeoForge 21.11.45、Java 21 和网络协议 7。

专用挣扎键默认关闭，已有客户端配置升级时补入 false。模组列表 → MFQM → 配置 → 客户端设置 → 操作设置，可手动启用；开关立即生效。关闭时隐藏按键菜单项且不提示冲突，保留自定义键位并恢复原版 F 换手。自动受困提示及工具上的 F 介绍移除，README 不再介绍挣扎。普通走跑、跳跃、黏连和下陷使用原有服务器物理。

旧源码和原发布文件保存在工作区忽略的 exports 下。本次沿用 v0.7.5 与同名 JAR，替换同一 GitHub 预发布的 JAR、完整源码 ZIP、SHA-256 清单及中文说明；保留原有桌面快照，新修订源码另存一份。详细发布提交及远端资产摘要记录在 exports/release-0.7.5-revised/manifest.json 和 github-release.json。

## 验证

- 红阶段：旧 JAR 在真实隔离客户端的“默认 F 不产生请求”断言失败，见 evidence/red-client.txt。
- 最终 `gradlew build prepareInstalledClient -PmfqmClientChecks --offline` 通过，13 组现有 Java 程序共 65,636 项检查通过；JAR 版本、字节码和资源验证通过。
- 最终 `smoke_viscosity_client.py --actions-only`：实际从 mods 下加载本次 JAR；默认关闭、原版换手、没有受困操作提示、实际菜单隐藏、关闭不显示键位冲突、自定义 G 保留、未按 Ctrl 的 Ctrl+F 不吞普通 F、短按只发一次、长按不连发也不触发共享换手、关闭后队列不重放均通过。
- 胶水禁用 F 后等待 20 tick，服务端努力值、下陷代价与动作时间保持不变；手动开启后五次 F 正确增加努力值，下陷共 0.05 格，后续 60 tick 不重放代价。
- 普通走路、救援、有限短跳、实际受击及涂胶粘鼠板检查通过；测量中两端未飞行且 FOV 维持原值。中文资源及配置屏幕检查通过。

本轮不新增截图、不重跑光影或完整 23 场景矩阵，运行贴图未修改。最终客户端日志见 evidence/green-client.txt；产物摘要见 validation.json。
