# 渲染优化与图标验收

2026-10-11，开发版本继续使用 `0.7.5-inside`。本次只修改工作区，GitHub 现有发布、桌面源码副本和正式游戏实例保持原状。改动前源码备份：`exports/backups/before-render-optimization/source.zip`，对应提交 `2618b29f394e36b6d3bfe308654952e1538b65fe`。

## 交付内容

通读 145 个主 Java 源文件、当前 14 个测试／微基准文件、全部 27 个 Python/PowerShell 工具及构建包装脚本和 metadata。完整评估、读取清单和复核见 [audit.md](audit.md)、[main-read-coverage.txt](main-read-coverage.txt)、[auxiliary-read-coverage.txt](auxiliary-read-coverage.txt)、[review.md](review.md)。资源另通过完整解析和打包验证。

- 黏丝环顶点裁剪由逐面重复执行改为每顶点一次，省去中间网格，保留原有面序、法线、颜色和几何。
- 每连接提取复用插值角色位置；纹理四边形直接提交四个顶点，减少临时数组。
- 单连接有效性直接复用原力计算，避免为每连接分配完整聚合缓冲；最终物理求力和上限仍沿用原实现。
- 新增 256×256 像素风方形图标，`logoFile="icon.png"`、`logoBlur=false`，保留旧横幅；模组描述改为当前贴肤残留，移除已雪藏按键的宣传。
- 修正显式启用的客户端检查夹具：连接生成、cuff 重分配和断裂分包时先等待完整同步，再严格验收胶膜与几何。未改变生产网络或渲染逻辑。

皮肤面预计算试验因低密度成本增加而撤回。已有脚部缓存、皮肤投射、第一人称临时偏移、视觉密度、随机性、皮肤表层与介质内根部约束保持原样。

## 测量

Java 21，固定 256 根黏丝／脚，`-Xms256m -Xmx256m`；新旧实现交替顺序，预热后取九次中位数，包含完整几何构建，用线程分配计数记录字节。复现入口是 `WetAdhesiveBenchmark`，不作为带时限的构建断言。

- 三段：旧 1.167890 ms，新 1.057040 ms，耗时减少约 9.5%；分配从 4,083,733 到 3,004,437 字节，减少约 26.4%。
- 六段：旧 1.491040 ms，新 1.182170 ms，耗时减少约 20.7%；分配从 4,034,581 到 2,344,981 字节，减少约 41.9%。

原始数据：[geometry-benchmark.csv](evidence/geometry-benchmark.csv)。这些数值只对应合成场景的 CPU 几何生成和分配，不能转换为整个游戏的 FPS。半透明叠加和大量顶点的 GPU 成本仍存在。本次没有减少用户选择的黏丝数量，也没有重测所有光影包。

## 验证

最终 `build prepareInstalledClient -PmfqmClientChecks --offline` 成功。13 个 Java 检查程序合计 **74,834 项**，其中 1,024 根新增随机黏丝与旧两遍裁剪结果逐面精确相等；单连接有效性矩阵覆盖原有聚合判断。JAR 与移植资源校验通过，资源检查为 0 errors。见 [build.txt](evidence/build.txt)和[artifact-validation.txt](evidence/artifact-validation.txt)。

实际安装 JAR 在隔离客户端合载 3D Skin Layers 1.11.3、FirstPersonModel 2.7.3、Not Enough Animations 1.12.6，命令：

```powershell
python tools/smoke_viscosity_client.py --coating-only --skin-layers --first-person --animations --timeout 240
```

最终通过图标 NativeImage 解码、600 个覆盖组合、宽／细臂、全部材质显示开关、资源重载、有界缓存、动态注入皮肤尺寸、站立／潜行／转身／切换视角／F6、64 个真实物理连接、完整根帽在介质内部以及整条身体端位于皮肤面外侧的检查。实际第一人称对齐最大误差约 `1.34e-7` 格，低于 `.012` 阈值；稳定场景显示 48 束／384 根，实际数随近距离淡出减少，默认上限仍为约 512 根。严格验证关闭飞行的真实短跳；短跳高度与同步稳定后的几何分别采样。本轮请求截图数为 0。

最终客户端证据：[client-final.txt](evidence/client-final.txt)。外部兼容模组及其内嵌库存在先前已记录的 `supported_formats` 资源包 metadata 报错，实际模型合载通过；这些是单独识别的第三方报错，并未据此忽略 MFQM 失败。夹具修正前的同步失败保留于 [client-before-sync-fixture.txt](evidence/client-before-sync-fixture.txt)。

## 图标与 PCL2

图标已嵌入 JAR，实际游戏成功解码 256×256 PNG。独立 PNG、原图和制作提示见 [icon.md](icon.md)。PCL2 模组管理列表从匹配的在线项目加载图标，没有匹配项目时显示默认图标；单独添加本地 JAR 图标不能改变这一行为。要让该列表显示新图标，需要后续关联 Modrinth／CurseForge 项目。依据 [PCL 官方源码](https://github.com/Meloong-Git/PCL/blob/main/Plain%20Craft%20Launcher%202/Pages/PageInstance/MyLocalModItem.xaml.vb#L379)和[NeoForge metadata 文档](https://docs.neoforged.net/docs/gettingstarted/modfiles/)。

## 本地产物

`exports/render-optimization-2026-10-11/`：最终 JAR、`MFQM-icon.png`、生成原图和 `SHA256SUMS.txt`。这些导出被 Git 忽略；源码资源与审查记录进入版本管理。

- JAR：`MFQM-neoforge-1.21.11-0.7.5-inside.jar`，2,692,502 字节，SHA256 `cae2dc8f2e8e2b286d270fe9a671ddb05808cf8138158d5549bd439021b5a400`。
- 图标：SHA256 `b5b0edc4bc4e44d5e41d06d88c82d3dc0fd89fefc1b5483e79468ae78d20ce18`。

当前是本地优化构建，本轮没有上传或替换 GitHub 发布附件。
