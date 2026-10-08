# 新黏性素材验证记录

日期：2026-10-06。

## RED：资源尚不存在

先编写完整验证脚本，再执行：

```powershell
python tools/refresh_adhesive_textures.py --verify
```

退出码 1：`AssertionError: 16 adhesive assets missing`，列出胶水 still/flow、底板、胶层、胶水桶、黏丝材质和 10 级胶水玩家蒙层。失败明确源于待实现资源缺失。

## GREEN：烘焙生成后通过

```powershell
python tools/refresh_adhesive_textures.py --bake --verify
```

退出码 0：16 张新 PNG，两个流体各 32 帧，共 64 帧全部通过边缘、透明度、循环验证；流动贴图四块重复正确，10 级玩家 UV alpha 区域保持模板支持并严格递增。桶轮廓 alpha 与模板完全相同。

## 完成后的强化检查

新增旧资源哈希保护和写入文件白名单，验证源图、输出和元数据哈希。反复烘焙后输出与第一次哈希一致。单独 `--verify` 再次通过。预览已目视检查：3×3 胶水表面相邻铺设连续，浅木边框清晰，乳白胶层透出底材，玩家 UV 覆盖从足部逐步增加；32×32 桶图标保持金属桶外形和白色内部液体。

仅素材处理验证；本代理未运行 Gradle，也未修改 Java、模型、语言、桌面副本或 GitHub。
