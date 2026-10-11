# 方形模组图标

最终资源：`src/main/resources/icon.png`，256×256 RGB PNG。使用内置图像生成工具制作一张原图，再通过 Pillow 的最近邻采样缩放，未改动原横幅 `assets/mfqm/logo.png`。本次只生成这一张必要的模组图标。

最终图像提示的整理版本：

> 用于 Minecraft 更多趣味流沙模组的方形像素风图标，适合缩到很小尺寸仍清晰辨认。中心是一块等距视角的棕色泥土／泥潭方块，边沿有少量绿色苔藓；顶部有乳白色黏胶小池和两条短黏丝。旁边放一块很小的木质陷入警示牌，用靴子图案提示陷落。深蓝色背景，轮廓明确，块状阴影，无文字、无字母、无水印，不使用写实渐变。

保留生成原图于本地导出目录，最终 PNG 可单独用作在线平台项目图标。`logoFile="icon.png"`、`logoBlur=false` 位于 NeoForge metadata。

游戏内模组列表会使用该资源。PCL2 的模组管理列表从匹配到的在线项目加载图标，未匹配时显示默认图标；嵌入 JAR 不能独自改变这一行为。参考 [PCL 官方源码](https://github.com/Meloong-Git/PCL/blob/main/Plain%20Craft%20Launcher%202/Pages/PageInstance/MyLocalModItem.xaml.vb#L379)、[NeoForge 图标字段说明](https://docs.neoforged.net/docs/gettingstarted/modfiles/)。
