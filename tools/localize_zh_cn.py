"""Rebuild complete Simplified Chinese, retaining legacy aliases and format tokens."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/mfqm/lang"
WORDS = {
    "More Fun Quicksand Mod": "更多趣味流沙", "MFQM stuff": "更多趣味流沙",
    "Mud": "泥潭", "Quicksand": "流沙", "Tar": "焦油", "Bog": "泥沼",
    "Mire": "黏稠泥潭", "Temp Morass Converter": "泥沼转化块", "Moor": "泥泽",
    "Slurry": "泥浆", "Slime Quicksand": "黏液流沙", "Jungle Quicksand": "丛林流沙",
    "Acid": "酸液", "Mucus": "黏液", "Chocolate": "巧克力", "Honey": "蜂蜜",
    "Corrupted Sand": "腐化沙", "Soft Snow": "松软积雪", "Soft Gravel": "松软砂砾",
    "Sinking Clay": "沉陷黏土", "Brown Clay": "褐色黏土", "Hardened Clay Path": "硬化黏土小径",
    "Larvae": "幼虫群", "Dense Webbing": "浓密蛛网", "Fleshy Pit": "血肉深坑",
    "Swallowing Flesh": "吞噬血肉", "Temp Vore Hole": "吞噬陷坑", "Poison Gas": "毒气",
    "Tangleroot Moss": "缠根苔藓", "Wax": "蜂蜡", "Wax Jungle Log": "蜡化丛林原木",
    "Solid Honey": "凝固蜂蜜", "Honeycomb": "蜂巢", "Chocolate Block": "巧克力块",
    "Plant Wall": "植生墙", "Smelling Blossom": "芳香花", "Tent Block": "帐篷块",
    "Peat": "泥炭", "Wet Peat": "湿泥炭", "Wax Leaves": "蜡化树叶", "Moor Grass": "泥泽草",
    "Gas Mask": "防毒面具", "Life Jacket": "救生衣", "Wading Boots": "涉水靴",
    "Long Stick": "长棍", "Grappling Hook": "抓钩", "Rope": "绳索", "Cable": "缆绳",
    "Rescue Hook": "救援钩", "Liquid Gun": "液体枪", "Sinking Potion": "沉陷药水",
    "Fertilizer": "肥料", "Mud Ball": "泥球", "Empty Honeycomb": "空蜂巢",
    "Raw Larva": "生幼虫", "Cooked Larva": "熟幼虫", "Cranberry": "蔓越莓",
    "Plain Donut": "原味甜甜圈", "Chocolate Donut": "巧克力甜甜圈", "Honey Donut": "蜂蜜甜甜圈",
    "Cranberry Donut": "蔓越莓甜甜圈", "Meat Donut": "肉馅甜甜圈", "Filter": "过滤芯",
    "Muddy Blob": "泥沼怪", "Sandy Blob": "流沙怪", "Tar Slime": "焦油史莱姆",
    "Vore Slime": "吞噬史莱姆", "Bee": "蜜蜂", "Tentacles": "触手", "Sinking": "沉陷",
    "Increases sink speed in quicksand": "加快在流沙中的下陷速度",
    "Bee hurts": "蜜蜂受伤", "Bee buzzes": "蜜蜂嗡嗡叫",
    "Thinnish mud": "浅泥潭", "Deep mud": "深泥潭", "Bottomless mud": "无底泥潭",
    "Dry Quicksand": "干流沙", "Soft Quicksand": "软流沙", "Mineral Clay": "矿物黏土",
    "Liquid Mire": "液态泥潭", "Sinky Liquid": "沉陷液", "Sinking Slime": "沉陷黏液",
    "Hardened Clay": "硬化黏土", "Liquid Chocolate": "液态巧克力", "Silt Sludge": "淤泥",
    "Honeycomb Empty": "空蜂巢", "Honeycomb Filled": "装满蜂蜜的蜂巢", "Sinking Rug": "沉陷地毯",
    "Flesh": "血肉", "Meat Wall Bottom": "血肉墙底部", "Meat Wall Conner": "血肉墙转角",
    "Meat Wall Side": "血肉墙侧面", "Meat Wall Gland": "血肉腺体", "Temp Meat": "血肉转化块",
    "waterlily": "睡莲", "Bog Grass": "泥沼草", "Cranberry Grass": "蔓越莓草丛",
    "Tendrils": "卷须", "Dead Leaves Pile": "枯叶堆", "Sand": "沙", "MucusFluid": "黏液",
    "Slime": "史莱姆黏液", "Bucket of Liquid Bog": "泥沼桶", "Bucket of Brown Clay": "褐色黏土桶",
    "Bucket of Mineral Clay": "矿物黏土桶", "Error bucket": "未知介质桶", "Bucket of Quicksand": "流沙桶",
    "Bucket of Sand": "干流沙桶", "Bucket of Mire": "液态泥潭桶", "Bucket of Slime": "沉陷黏液桶",
    "Bucket of Mucus": "黏液桶", "Bucket of Tar": "焦油桶", "Bucket of Acid": "酸液桶",
    "Bucket of Chocolate": "巧克力桶", "Bucket of Chocolate Powder": "巧克力粉桶",
    "Bucket of Slurry": "泥浆桶", "Bucket of Honey": "蜂蜜桶", "Rescuing": "救援绳",
    "Coil": "绳线轴", "Hook": "挂钩", "Grappling Hook Broken": "损坏的抓钩",
    "Larva Raw": "生幼虫", "Larva Cooked": "熟幼虫", "Filled Honeycomb": "装满蜂蜜的蜂巢",
    "Donut": "原味甜甜圈", "Glazed Donut": "糖霜甜甜圈", "Glazed Donut With Sprinkles": "彩糖糖霜甜甜圈",
    "Pink Donut With Sprinkles": "彩糖粉色甜甜圈", "Bottle of Mire": "瓶装泥浆",
    "Hot Chocolate": "热巧克力", "Error Potion": "未知药水", "Throwable Sinking Potion": "喷溅型沉陷药水",
    "Some viscous and sticky substance": "黏稠的沉陷药剂", "Some viscous and sticky substance now can be thrown": "可投掷的黏稠沉陷药剂",
    "Warm and sweet treat in a glass": "一杯温暖香甜的饮品", "Tall Leather Boots": "高筒皮靴",
    "Slimy Tall Leather Boots": "覆有黏液的高筒皮靴", "Mired": "深陷泥潭", "Empty": "未装填",
    "Maybe Water": "未知液体", "Funny toy which able to store and spit": "可储存并喷射介质的液体枪",
    'any "liquid" you could find in the world': "可装填世界中的多种流体与沉陷介质",
    "Cant be used in Creative": "仅限生存模式手持使用", "Can be attached to any solid material": "可挂接在坚固的支撑物上",
    "excluding sand and foliage": "不支持沙子与树叶", "Controls": "操作说明", "RMB": "鼠标右键",
    "shoot the hook": "发射抓钩", "Hold right click": "按住鼠标右键", "reel in while holding the tool": "手持工具时收绳",
    "Sneak + right click": "潜行并按住鼠标右键", "pay out; X disconnects": "放绳；按 X 断开连接",
    "The Hook is broken": "抓钩已损坏", "Can be fixed": "可以修复", "Gives you buoyancy in water": "在水中提供浮力",
    "prevents you from sinking": "帮助抵抗下陷", "Works in some forms of quicksand": "也适用于部分流沙和泥潭",
    "Hold Crouch button": "按住潜行键", "for sinking": "暂时关闭浮力以向下潜入",
    "Can be use as walking stick for testing": "可用作探测杖", "the ground in front of you": "探测前方地面与介质深度",
    "Also gives you much more stable footing that": "持续撑住长棍可获得更稳定的支撑",
    "increases your chance for surviving quicksand": "帮助在流沙中挣扎脱困", "RMB CLK": "单击鼠标右键",
    "Test ground": "探测地面", "RMB HOLD": "按住鼠标右键", "Increase chance for successful struggling": "撑住长棍辅助脱困",
    "Cant be used with Creative": "仅限生存模式手持使用", "Can be attached around solid columns": "可挂接在坚固的柱子",
    "and edges, or into the leaves": "边缘或树叶上", "throw the rope": "投出绳索",
    "It's melting in your hands": "握在手中会逐渐融化", "Twice better than Bone Meal": "效果约为骨粉的两倍",
    "Basic protection for your breathing": "为呼吸提供基础防护", "Mud Tentacles": "泥潭触手",
    "Bubble": "气泡", "Tar Treads": "焦油附着", "Slime Hole": "黏液陷坑", "Rescue": "救援连接",
    "Liquid Ball": "介质弹", "Depth: %s blocks": "深度：%s 格", "Reel in": "收绳（需手持）",
    "Pay out cable": "放绳（需手持）", "Release connector": "断开连接（需手持）", "MFQM Controls": "更多趣味流沙操作",
    "Vore Slime Spawn Egg": "吞噬史莱姆刷怪蛋", "Muddy Blob Spawn Egg": "泥沼怪刷怪蛋",
    "Sandy Blob Spawn Egg": "流沙怪刷怪蛋", "Tar Slime Spawn Egg": "焦油史莱姆刷怪蛋", "Bee Spawn Egg": "蜜蜂刷怪蛋",
    "Glue": "胶水", "Glue Bucket": "胶水桶", "Sticky Board": "粘鼠板", "Struggle": "挣扎",
    "Coated Sticky Board": "粘鼠板（已涂胶）", "Empty Sticky Board Base": "粘鼠板底板（未涂胶）",
    "Glue coating: %s/7. Place on solid ground to trap targets.": "胶层：%s/7；放在坚固地面上即可困住目标。",
    "No glue: this base cannot trap targets. Apply a glue bucket.": "尚未涂胶，不能困住目标；用胶水桶右键补胶。",
    "Struggling weakens bonds but deepens sinking. Rest to hold your depth.": "挣扎会削弱黏连，也会加深下陷；停止挣扎可保持深度。",
    "Reusable base. Coat with a glue bucket; repeated struggle wears out the glue.": "底板可回收重复使用；用胶水桶补胶，挣扎会消耗涂层。",
}
DEATHS = {
    "quicksand_suffocation": "%1$s 在沉陷介质中窒息了", "tar_burn": "%1$s 被滚烫的焦油灼伤了",
    "gas_asphyxiation": "%1$s 在毒气中窒息了", "acid_dissolve": "%1$s 被酸液溶解了",
    "flesh_consumption": "%1$s 被血肉吞噬了",
}
CONFIG = {
    "adhesiveBonds": "启用黏丝束缚", "creativeGroundPhysics": "创造模式地面受困",
    "glueBondDistance": "胶水黏丝断裂距离", "honeyBondDistance": "蜂蜜黏丝断裂距离", "tarBondDistance": "焦油黏丝断裂距离",
    "slimeBondDistance": "黏液黏丝断裂距离", "mudBondDistance": "泥潭黏丝断裂距离", "bootLossChance": "拔脚留靴概率",
    "glueActivityRadius": "胶水受困活动半径", "honeyActivityRadius": "蜂蜜受困活动半径",
    "tarActivityRadius": "焦油受困活动半径", "slimeActivityRadius": "黏液受困活动半径",
    "mudActivityRadius": "泥潭受困活动半径", "boardActivityRadius": "粘鼠板受困活动半径",
    "glueCoating3d": "贴肤薄膜细节（全部材质）",
    "coatingOpacity": "全局覆盖透明强度", "coatingThickness": "全局胶层微起伏", "strandDisplayLimit": "每个角色黏丝束显示上限",
    "strandDensity": "每个接触的独立黏丝数量",
    "materialOpacity": "此材质覆盖透明强度", "materialThickness": "此材质胶层微起伏", "materialStrands": "此材质黏丝束显示上限",
    "glueVerticalDistance": "胶水黏丝竖向断裂距离", "honeyVerticalDistance": "蜂蜜黏丝竖向断裂距离",
    "tarVerticalDistance": "焦油黏丝竖向断裂距离", "slimeVerticalDistance": "黏液黏丝竖向断裂距离", "boardVerticalDistance": "粘鼠板黏丝竖向断裂距离",
    "genGluePools": "生成胶水池", "genStickyBoards": "遗迹生成粘鼠板", "gluePoolChance": "胶水池候选间隔",
    "gluePoolShallowDepth": "浅胶水池深度", "gluePoolMinDeepDepth": "深胶水池最小深度", "gluePoolMaxDeepDepth": "深胶水池最大深度",
    "genMud": "生成泥潭", "genMire": "生成黏稠泥潭", "genDeepMud": "生成深泥潭", "genLiquidMire": "生成液态泥潭",
    "genMoor": "生成泥沼转化地形", "genBog": "生成泥沼", "genMorass": "生成泥泽", "genQuicksand": "生成流沙",
    "genSoftQuicksand": "生成软流沙", "genSoftQuicksandForest": "森林生成软流沙", "genJungleQuicksand": "生成丛林流沙",
    "genSinkingSand": "生成干流沙", "genSoftSnow": "生成松软积雪", "genHardenedClay": "生成硬化黏土",
    "genHardenedClayPath": "生成硬化黏土小径", "genSinkingClay": "生成沉陷黏土", "genLarvae": "生成幼虫坑",
    "genWeb": "生成浓密蛛网", "genWebSpawner": "蛛网生成刷怪笼", "genTar": "生成焦油池", "genSlime": "生成沉陷黏液池",
    "genCorruptedSand": "生成腐化沙", "genMeat": "生成血肉深坑", "genMeatSwallow": "生成吞噬血肉",
    "genWastePit": "生成泥浆废物坑", "genMucusBlossom": "生成黏液花", "genMoss": "生成缠根苔藓",
    "genBrownClay": "生成褐色黏土", "genMineralClay": "生成矿物黏土", "genWax": "生成蜂蜡地形",
    "genBeeHive": "生成蜂巢", "genNetherHoney": "下界生成蜂蜜", "genGravelPit": "生成砂砾坑",
    "genDesertTombs": "生成沙漠墓", "genNetherWastePit": "下界生成废物坑", "genTempleQuicksand": "神殿生成流沙陷阱",
    "genMarshReforming": "启用湿地地形改造", "customSwampWaterColor": "使用自定义沼泽水色", "altitudeShift": "生成高度偏移",
    "enableCustomSlimes": "启用模组黏液生物", "spawnVoreSlime": "生成吞噬史莱姆", "spawnMuddyBlob": "生成泥沼怪",
    "spawnSandBlob": "生成流沙怪", "spawnTarSlime": "生成焦油史莱姆", "enableWadingBoots": "启用涉水靴配方",
    "enableLongStick": "启用长棍配方", "enableRope": "启用绳索配方", "enableGrapplingHook": "启用抓钩配方",
    "enableLifeJacket": "启用救生衣配方", "enableGasMask": "启用防毒面具配方", "enableSinkingPotion": "启用沉陷药水配方",
    "enableLiqGun": "启用液体枪配方", "hotTar": "焦油造成热伤害", "tentaclesInFlesh": "血肉坑生成触手",
    "mudTentacles": "泥潭生成触手", "gasSlurry": "泥浆毒气效果", "bubblesOnSurface": "介质表面生成气泡",
    "tarTreads": "启用焦油黏连", "hookAsRider": "启用抓钩搭乘操作", "enableHandRescue": "启用空手救援",
    "realisticSuffocation": "玩家使用缓冲空气量", "realisticMobSuffocation": "生物使用缓冲空气量",
    "realisticBoots": "计算靴子负重", "realisticArmor": "计算盔甲负重", "weightCalc": "计算背包负重",
    "damageMultiplier": "模组伤害倍率", "forceFirstPerson": "深陷时强制第一人称", "coverPlayerWithMud": "显示玩家介质覆盖层",
    "customAirHud": "显示沉陷空气量", "quicksandOpacity": "深陷时遮挡第一人称视野", "bubbleEffects": "显示表面气泡",
    "tarTreadsEffect": "显示焦油附着效果", "struggleAnimation": "显示挣扎动作", "struggleCamera": "挣扎时镜头轻微起伏",
    "enableStruggleKey": "启用专用挣扎键（默认关闭）",
    "adhesiveTethers": "显示腿脚黏膜与拉丝", "biomesOPlenty": "超多生物群系兼容生成", "defaultWorld": "原版世界生成",
    "twilightForest": "暮色森林兼容生成", "betweenlands": "交错次元兼容生成", "abyssalcraft": "深渊国度兼容生成",
    "wildycraft": "荒野模组兼容生成", "adventOfAscension": "虚无世界兼容生成", "extraUtilities": "额外实用设备兼容生成",
    "forceCustomWorldGen": "强制自定义世界生成",
}

def build():
    english = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
    previous = json.loads((LANG / "zh_cn.json").read_text(encoding="utf-8"))
    result = {}
    for key, value in english.items():
        if key.startswith("death.attack."):
            cause = key.split(".")[2]
            result[key] = DEATHS[cause] if key.count(".") == 2 else (
                "%1$s 在逃离%2$s时" + DEATHS[cause].replace("%1$s ", "") if key.endswith(".player") else
                "%1$s 在逃离手持%3$s的%2$s时" + DEATHS[cause].replace("%1$s ", ""))
        elif key in previous:
            result[key] = previous[key]
        elif key in ("container.mfqm.honey_chest", "tooltip.mfqm.life_jacket", "tooltip.mfqm.wading_boots",
                     "mfqm.configuration.strandDensity", "mfqm.configuration.strandDensity.tooltip",
                     "mfqm.configuration.controls", "mfqm.configuration.enableStruggleKey", "mfqm.configuration.enableStruggleKey.tooltip"):
            continue  # Explicit translations below.
        else:
            result[key] = WORDS[value]  # Fail explicitly on a newly introduced untranslated value.
    result.update(previous)
    result.update({"itemGroup.mfqm": "更多趣味流沙", "key.categories.mfqm.controls": "更多趣味流沙操作",
        "entity.mfqm.adhesive_tether": "黏丝连接", "entity.mfqm.stuck_boots": "留存靴子",
        "entity.mfqm.rope": "绳索连接", "entity.mfqm.hook": "抓钩连接", "entity.mfqm.rescue": "救援连接",
        "entity.mfqm.sinking_potion": "喷溅型沉陷药水", "container.mfqm.honey_chest": "蜂蜜宝箱"})
    for key, label in CONFIG.items():
        result["mfqm.configuration." + key] = label
        result["mfqm.configuration." + key + ".tooltip"] = label + "。服务器设置由服务器决定；世界生成设置仅影响新区块。"
    result.update({"mfqm.configuration.title": "更多趣味流沙设置",
        **{"mfqm.configuration." + k: v for k, v in {"adhesive": "黏连与胶水", "worldgen": "世界生成", "mobs": "生物生成",
            "items": "工具获取", "options": "玩法与物理", "hud": "界面与视角", "rendering": "画面显示", "compat": "模组兼容", "controls": "操作设置"}.items()},
        "mfqm.configuration.enableStruggleKey.tooltip": "仅影响本机。手动开启后可使用专用挣扎键，默认 F，可在按键设置中修改；关闭时隐藏该按键项，F 正常换手。立即生效，不显示自动操作提示。",
        "mfqm.configuration.creativeGroundPhysics.tooltip": "开启后，关闭飞行的创造玩家也会黏住、下陷并需要挣扎。飞行和旁观始终免疫；创造模式仍免受伤害。",
        "mfqm.configuration.glueCoating3d.tooltip": "将胶水、焦油、蜂蜜、黏液及泥沙等残留显示为贴合实际皮肤和衣物的薄膜，沿用 3D Skin Layers 的凹凸表面。关闭后取消微起伏，仍保留贴肤覆盖，不影响黏力和水洗。",
        "mfqm.configuration.gluePoolChance.tooltip": "适宜新区块平均每此数量尝试一个胶水池候选；值越小，候选越多，地形不适宜时仍不会生成。",
        "mfqm.configuration.bootLossChance.tooltip": "胶水或涂胶粘鼠板中奋力拔脚、挣断时的单次留靴概率。每次连续受困只检查一次；靴子的原有组件完整保留。",
        "mfqm.configuration.realisticSuffocation.tooltip": "开启时玩家耗尽沉陷空气量后才受窒息伤害；关闭时头部没入危险介质便可能受伤。",
        "mfqm.configuration.realisticMobSuffocation.tooltip": "开启时生物耗尽沉陷空气量后才受窒息伤害；关闭时头部没入危险介质便可能受伤。",
        "mfqm.configuration.hookAsRider": "抓钩附着生物",
        "mfqm.configuration.hookAsRider.tooltip": "允许抓钩辅助实体附着在普通生物上，不会让玩家骑乘该生物。",
        "tooltip.mfqm.life_jacket": "在支持的沉陷介质中提供浮力；潜行时关闭，胶水中无效。",
        "tooltip.mfqm.wading_boots": "支撑浅层部分沉陷介质；深处、胶水及涂胶板中无效。胶水中拔脚可能留下靴子。",
        "itemGrapplingHook.instruction3": "不能挂在沙子、砂砾或树叶上",
        "itemGrapplingHook.instruction10": "放绳；默认 X 断开",
        "itemRope.instruction10": "放绳；默认 X 断开",
        "item.mfqm.sand_bucket": "沙子桶", "item.BucketOfSand.name": "沙子桶",
        "item.mfqm.quicksand_bucket": "丛林流沙桶", "item.BucketOfQuicksand.name": "丛林流沙桶",
    })
    for kind, label in (("server", "服务器玩法"), ("common", "通用设置"), ("client", "客户端设置")):
        prefix = "mfqm.configuration.section.mfqm." + kind + ".toml"
        result[prefix] = label
        result[prefix + ".title"] = label
    for medium in ("glue", "honey", "tar", "slime", "mud", "board"):
        result["mfqm.configuration." + medium + "ActivityRadius.tooltip"] = "完整黏力时可实际走动的半径，单位为格。挣扎会扩大范围；黏丝接近边缘才回拉。断裂距离至少保留半径加 0.4 格，供转身和短跳。"
    for family,label in (("glue","胶水与涂胶板"),("tar","焦油"),("honey","蜂蜜与蜂蜡"),("slime","黏液与其他软质残留"),("mud","泥沙与其他残留")):
        result["mfqm.configuration."+family+"Visuals"]=label+"显示设置"
    for field in ("coatingOpacity","materialOpacity"):
        result["mfqm.configuration."+field+".tooltip"]="仅影响本机显示。1 为原始透明强度，0 隐藏覆盖；全局值与材质值相乘，不改变浸入高度、水洗或黏力。"
    for field in ("coatingThickness","materialThickness"):
        result["mfqm.configuration."+field+".tooltip"]="仅影响本机显示。1 为默认薄胶层的细微起伏；全局值与材质值相乘，0 取消起伏。不会把整个身体撑成厚壳，不改变浸入高度和黏力。"
    for field in ("strandDisplayLimit","materialStrands"):
        result["mfqm.configuration."+field+".tooltip"]="每个角色可见接触组数量，默认 64，范围 0～64；每组最多 128 根独立完整黏丝，由密度设置控制。取全局与材质上限中较小的值，优先显示脚边新连接；远处旧丝淡出，不影响服务器物理。"
    result["mfqm.configuration.strandDensity.tooltip"]="每个接触的黏丝基数，默认 8，范围 1～128；每个角色满额最多约 512 根，上限可调至 8192 根。接触少时每组最多增密四倍，但不超出总预算；移动后接触逐渐增加。位置、宽厚和轻微弯曲稳定随机，根部始终在介质内且不分叉。高数值会增加绘制开销，不改变物理黏力。"
    for medium in ("glue","honey","tar","slime","board"):
        result["mfqm.configuration."+medium+"VerticalDistance.tooltip"]="黏丝竖向延伸超过此距离时断开，单位为格。与水平活动半径独立；延长不会使角色飞行。服务器决定此值。"
    for key in ("mudBondDistance","mudActivityRadius"):
        result["mfqm.configuration."+key+".tooltip"]="保留的旧版配置项。泥潭等非黏性介质已不再生成黏丝，此项不再影响它们；下陷与挣扎仍生效。"
    source = (ROOT / "src/main/java/com/mfqm/morefunquicksandmod/ModConfig.java").read_text(encoding="utf-8")
    missing = set(re.findall(r'\.define(?:InRange)?\("([^"]+)"', source)) - CONFIG.keys()
    if missing:
        raise ValueError("Untranslated configuration: " + str(missing))
    (LANG / "zh_cn.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Complete zh_cn:", len(english), "English keys covered;", len(result), "Chinese keys total")

if __name__ == "__main__":
    build()
