# 渲染小优化与模组图标

日期：2026-10-11。开发版本继续使用 `0.7.5-inside`。

用户要求通读所有代码、评估并实施渲染小优化，制作启动器图标；确认使用 PCL2 的模组管理列表。工作前已将 HEAD 源码归档到 `exports/backups/before-render-optimization/source.zip`，本轮只在工作区修改。

## 已确认的边界

保持现有黏丝默认密度、配置上限、随机分布、皮肤表面附着规则、介质内根部、透明度、活动范围与可选按键行为。保留 FirstPersonModel 和 3D Skin Layers 的实时姿势与动态尺寸处理；不缓存可能变化的第三方网格，不依据根部小包围盒裁剪整条连接。

## 优化方案

黏丝生成时，相邻面共享环顶点。每个顶点只做一次贴肤裁剪，直接生成最终面，保留原有绕序、法线、颜色、端帽及面顺序；省去中间网格和逐面重复裁剪。每连接只获取一次插值后的实体位置，纹理四边形提交直接使用四个顶点，避免临时数组。单连接有效性判断复用原有力计算，不再调用完整聚合求解，最终物理力聚合不变。

脚部模型已有按实体、tick、partialTick 的缓存。本轮还试验了三角形查询预计算，密集复杂皮肤有小幅收益，但较少查询会增加每姿势的初始化时间与对象分配；经测量和复核，已撤回这一试验。最终交付沿用原有脚部缓存与皮肤查询，实验 CSV 仅用于解释取舍。

## 图标方案与 PCL 限制

制作一张方形 Minecraft 像素风泥潭／乳白胶水图标，放在 JAR 根目录 `icon.png`，使用 NeoForge `logoFile` 和 `logoBlur=false`；保留原横幅资源。图标另行导出 PNG，方便以后上传模组平台。

PCL 当前官方 `MyLocalModItem.xaml.vb` 第 379～384 行从匹配到的在线项目加载图标，没有项目就使用 NoIcon。只替换 JAR 里的 logo 不能强制改变这一列表；本轮不创建 Modrinth／CurseForge 项目，也不修改启动器本身。游戏内图标打包与解码应通过验证。

参考：[NeoForge metadata](https://docs.neoforged.net/docs/gettingstarted/modfiles/)、[PCL 官方列表源码](https://github.com/Meloong-Git/PCL/blob/main/Plain%20Craft%20Launcher%202/Pages/PageInstance/MyLocalModItem.xaml.vb#L379)。
