# 旧跨模组配方的 1.21.11 兼容桥

205 条本模组配方之外的 9 个旧注册调用均已保存为可映射模板。它们来自反编译 `MoreFunQuicksandMod/main/MFQM.java`，与 `recipe-manifest.json` 的 `optional_dependency_calls` 一一对应。桥不会根据旧数字 ID、模组名称或 metadata 猜测现代物品。

默认提供 9 个空物品标签，因此没有明确映射时不会注入配方，也不会因为缺少可选依赖刷出警告。映射由整合包作者提供；这些标签表达旧物品的用途，不能视为现代第三方模组已存在相同物品的保证。

## 九个调用和映射

- 2279：MFQM 肥料 → IC2 肥料 ×1，无序合成。输出标签 `mfqm:compat/ic2/fertilizer`。
- 2313：BOP 蜂巢 → MFQM 蜡片 ×9，熔炼。输入标签 `mfqm:compat/bop/hive`。
- 2317：BOP `FleshItem` 的旧 meta=2 空蜂巢 → 蜡片 ×3，熔炼。输入标签 `mfqm:compat/bop/empty_honeycomb`。
- 2321：BOP `FoodItem` 的旧 meta=9 含蜜蜂巢 → 蜡片 ×3，熔炼。输入标签 `mfqm:compat/bop/filled_honeycomb`。
- 2356：4 个 BOP 旧 meta=0 泥土与 1 个水桶 → 更深的 MFQM 泥土 ×4。输入标签 `mfqm:compat/bop/mud`，保持原十字形排布。
- 2359：4 个同类 BOP 泥土、4 个水桶和中心空桶 → MFQM 泥潭桶 ×1。
- 2363：5 个同类 BOP 泥土和 4 个水桶 → MFQM 泥潭 ×4。旧源给出的未使用 `d` 键已移除。
- 2410：MFQM 防毒面具、2 个 AOA Doomstone 和 1 个 Toxic Lump → AOA Face Mask ×1。输入标签 `mfqm:compat/aoa/doomstone`、`mfqm:compat/aoa/toxic_lump`；输出标签 `mfqm:compat/aoa/face_mask`。保持原排布。
- 2447：BOP 泥球以 2×2 排布 → MFQM 泥土 ×1。输入标签 `mfqm:compat/bop/mud_ball`。

三个熔炼配方保持原经验 0.1，采用普通熔炉的 200 tick 周期。水桶通过 1.21.11 合成余料 API 返回空桶。独立的旧 meta 已转换为 MFQM 的对应变体物品，例如 2356 输出 `mfqm:mud_variant_1`。

## 数据包映射

在数据包的 `data/mfqm/tags/item/compat/…` 下添加标签文件。例如，核实当前依赖物品注册 ID 后，将 IC2 肥料的实际 ID 写入 `data/mfqm/tags/item/compat/ic2/fertilizer.json`：

```json
{
  "replace": true,
  "values": ["verified_dependency:actual_fertilizer_id"]
}
```

示例 ID 是占位符，必须替换为当前整合包真实注册 ID。输入标签可以包含多个等价物品；每个输出标签必须恰好包含一个物品。空标签或缺失标签使对应配方保持禁用，多物品输出标签使对应配方被跳过并记录原因。空气和未启用的实验特性物品不会成为可用映射。

`biomesOPlenty=false` 会关闭七个 BOP 模板，`adventOfAscension=false` 会关闭 AOA 模板。IC2 调用在旧版没有对应的通用开关，是否启用由明确的输出映射决定。原九个调用均位于旧工具 `Turn*` 条件之外；桥不会因为某个工具被用作材料而关闭整个下游配方。若模板输出改为 MFQM 工具，仍会遵循该工具的获取开关。

## 覆盖和关闭模板

模板位于 `data/mfqm/mfqm_compat_recipe/compat/*.json`，其完整内容可由高优先级数据包覆盖。文件路径对应配方 ID，例如 `compat/ic2_fertilizer.json` 对应 `mfqm:compat/ic2_fertilizer`。包装结构如下：

```json
{
  "source_line": 2279,
  "integration": "ic2",
  "enabled": true,
  "result_tag": "mfqm:compat/ic2/fertilizer",
  "recipe": {
    "type": "minecraft:crafting_shapeless",
    "ingredients": ["mfqm:fertilizer"],
    "result": {"count": 1}
  }
}
```

- `enabled:false` 明确关闭模板。
- `integration` 支持 `bop`、`aoa`、`ic2`、`none`。
- `result_tag` 可省略，此时 `recipe.result` 必须提供普通的 `id`。输出的 `count`、`components` 均保留。
- 包装层和 `recipe` 层均支持 `neoforge:conditions`，包括已有的 `mfqm:item_enabled`。所有条件必须满足。
- 可选的 `required_tags` 为标签 ID 字符串数组，不带 `#`；任意一个为空都使模板禁用。
- 普通输入采用 1.21.11 的物品 ID 字符串、`#物品标签`或替代物品数组。桥将标签内容解析成当次加载的明确物品集合，保持每个输入位置不变。

若在普通目录 `data/mfqm/recipe/compat/` 提供同名文件，普通配方拥有这个 ID，桥完全让出该 ID。即使普通配方的条件不满足，桥也不会重新添加它。其他模组已注册的同 ID holder 同样被保留。

旧 metadata 若在现代依赖中改成物品组件，需要覆盖模板输入使用组件谓词。仅把一个含多种组件状态的物品放进标签，无法表达旧 meta=2 与 meta=9 的区别。例如：

```json
{
  "neoforge:ingredient_type": "neoforge:components",
  "items": "#mfqm:compat/bop/empty_honeycomb",
  "components": {"verified_dependency:actual_component": "verified_value"},
  "strict": false
}
```

组件名和值也必须来自当前依赖的实际 API。桥保留 `components`、`predicate`、`custom_data` 内的负载，不把其中的字符串误当成标签映射。

旧版曾反射修改 BOP 的全局配方列表；移植桥不会删除第三方配方。如果当前依赖仍提供同样的四泥球配方并产生另一个输出，整合包作者需要根据真实的现代配方 ID，通过数据包明确关闭或覆盖冲突配方。

## 加载及重载

桥在 `ServerAboutToStartEvent` 中读取已加载标签和世界配置，在 `OnDatapackSyncEvent` 的全服重载分支中重新解析。它在同步前创建真实 `RecipeHolder`，更新 `RecipeMap`，调用 `finalizeRecipeLoading` 重建配方书显示和熔炉输入属性。玩家加入时不会重复注入，但会请求同步已有的合成及熔炼配方。

旧注入 holder 以对象身份追踪，不能使用只比较 ID 的 `RecipeHolder.equals`。删除映射或关闭模板会移除桥拥有的配方；同 ID 的新数据包或其他模组 holder 不会被错误删除。卸载服务器时清除追踪记录。

## 验收

`CompatRecipeChecks.verify(ServerLevel)` 与 `verifyLive(ServerLevel)` 只允许在 `-Dmfqm.portChecks=true` 下运行，已供统一开发验收器调用。纯 fixture 使用明确的原版物品测试解析，不改真实标签，也不修改真实服务器的配方管理器。

验收覆盖九个模板的实际 `Recipe.CODEC` 解码、合成或熔炼输入匹配、错误输入拒绝、原输出数量、水桶余料、空映射静默跳过、输出歧义拒绝、输入多选、兼容开关、获取开关、真实条件解析、同 ID 保护、旧 holder 移除，以及独立管理器中配方显示和熔炉属性的重建与清除。

真实 `/reload` 的隔离测试映射保存在 [compat-fixture-tags.json](compat-fixture-tags.json)。该文件明确标记为测试 fixture，不能复制为生产兼容映射。验收脚本可把其中九个标签写入独立测试数据包，验证冷启动与重载时 `active=9`，随后禁用该包再次重载并验证 `active=0`。`verifyLive` 重新从当前数据包和配置计算预期配方，并与服务器实际 holder 和配方书显示比较。

实现依据是目标源码中的 `RecipeManager`、`RecipeMap`、`RecipeHolder`、`FileToIdConverter`、`OnDatapackSyncEvent`、`ICondition` 与 `TagKey`，而非旧 Forge API 的类名替换。
