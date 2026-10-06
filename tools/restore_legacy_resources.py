"""Copy original assets without changing pixels and build modern resource definitions."""
import json
import re
import shutil
import sys
from pathlib import Path
from verify_port import BLOCKS

LIQUID = {
    "bog": "bog", "dry_quicksand": "sand", "jungle_quicksand": "junglequicksand",
    "liquid_mire": "mire", "stable_liquid_mire": "mire", "sinky_liquid": "sinkyliquid",
    "sinking_slime": "slime", "mucus": "mucus", "tar": "tar", "acid": "acid",
    "slurry": "slurry", "honey": "honey", "liquid_chocolate": "choco",
}
TEXTURES = {
    "mud": "mud", "soft_snow": "softsnow", "soft_quicksand": "softquicksand0",
    "morass": "morass0", "wet_peat": "peat0", "peat": "peat", "brown_clay": "brown_clay0",
    "wax": "wax0", "quicksand": "quicksand", "sandstone_trap": "quicksand",
    "mire": "mire0", "moor": "moor0", "hardened_clay": "clay5", "sinking_clay": "clay0_0",
    "tangleroot_moss": "moss_front", "dense_web": "denseweb", "larvae": "larvae",
    "corrupted_sand": "corruptedsand", "swallowing_flesh": "swallowingflesh", "gas": "gas0",
    "soft_gravel": "softgravel0", "solid_honey": "honey", "honeycomb": "honeycomb0",
    "chocolate": "chocolate", "sinking_rug": "minecraft:block/white_wool", "lure": "blossom6",
    "blossom": "blossom0", "blossom_slab": "blossom0", "vore_hole": "meat0",
    "meat_wall": "meat0", "meat_hole": "meat0", "wax_wood": "waxtree0",
    "custom_lily_pad": "minecraft:block/lily_pad", "moor_grass": "bog_grass",
    "tendrils": "tent0", "leaves_pile": "leaves",
}
COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split()


def write(root, path, data):
    out = root / path
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def texture(block, variant):
    if block in LIQUID:
        return LIQUID[block] + "_still"
    if block == "brown_clay":
        return ("brown_clay" if variant < 4 else "mineral_clay") + str(variant % 4)
    if block in ("mire", "moor", "morass", "wax", "soft_quicksand", "soft_gravel", "gas", "honeycomb", "tendrils", "blossom", "blossom_slab", "meat_wall"):
        names = {"mire": ("mire", 4), "moor": ("moor", 8), "morass": ("morass", 6),
                 "wax": ("wax", 4), "soft_quicksand": ("softquicksand", 2),
                 "soft_gravel": ("softgravel", 3), "gas": ("gas", 15),
                 "honeycomb": ("honeycomb", 2), "tendrils": ("tent", 3),
                 "blossom": ("blossom", 7), "blossom_slab": ("blossom", 7), "meat_wall": ("meat", 11)}
        prefix, maximum = names[block]
        return prefix + str(min(variant, maximum))
    if block == "sinking_rug":
        return "minecraft:block/" + COLORS[variant] + "_wool"
    if block == "wax_wood":
        return "waxtree1" if variant >= 4 else "waxtree0"
    if block == "moor_grass":
        return "bog_grass_cberry" if variant == 5 else "bog_grass"
    return TEXTURES[block]


def model(block, variant):
    tex = texture(block, variant)
    tex = tex if ":" in tex else "mfqm:blocks/" + tex
    if block == "wax":
        stage = min(variant // 3, 4)
        return {"parent": "minecraft:block/cube_bottom_top", "textures": {"side": f"mfqm:blocks/wax{stage}s", "top": f"mfqm:blocks/wax{stage}", "bottom": f"mfqm:blocks/wax{stage}"}}
    if block == "morass":
        return {"parent": "minecraft:block/cube_bottom_top", "textures": {"side": "mfqm:blocks/moor_side0", "top": f"mfqm:blocks/moor{max(0, min(variant, 9) - 1)}", "bottom": "mfqm:blocks/moor_side1"}}
    if block == "sinking_clay":
        top = f"clay0_{variant}" if variant < 5 else "clay" + str(min(2, variant - 5) + 1)
        return {"parent": "minecraft:block/cube_bottom_top", "textures": {"side": "mfqm:blocks/clay5" if variant >= 7 else "mfqm:blocks/clay4", "top": "mfqm:blocks/" + top, "bottom": "mfqm:blocks/clay3"}}
    if block in ("moor_grass", "tendrils", "dense_web", "lure"):
        return {"parent": "minecraft:block/cross", "textures": {"cross": tex}, "render_type": "minecraft:cutout"}
    if block in ("leaves_pile", "custom_lily_pad", "blossom_slab"):
        height = 8 if block == "blossom_slab" else 0.25
        bottom = 8 if block == "blossom_slab" and variant == 0 else 0
        return {"textures": {"all": tex, "particle": tex}, "elements": [{"from": [0, bottom, 0], "to": [16, bottom + height, 16], "faces": {d: {"texture": "#all"} for d in ("up", "down", "north", "south", "east", "west")}}], "render_type": "minecraft:cutout"}
    if block == "tangleroot_moss":
        bottom = -12.8 if variant in (0, 2, 3, 5, 6) else 0
        top = 0 if variant in (0, 2, 6) else 3.2 if variant in (1, 3, 5) else 16
        return {"textures": {"side": "mfqm:blocks/moss_front", "top": "mfqm:blocks/moss_up", "particle": "mfqm:blocks/moss_front"}, "elements": [{"from": [0, bottom, 0], "to": [16, top, 16], "faces": {d: {"texture": "#top" if d in ("up", "down") else "#side"} for d in ("up", "down", "north", "south", "east", "west")}}], "render_type": "minecraft:cutout"}
    result = {"parent": "minecraft:block/cube_all", "textures": {"all": tex}}
    if block == "gas":
        result["render_type"] = "minecraft:translucent"
    return result


def main():
    old = Path(sys.argv[1] if len(sys.argv) > 1 else "D:/download/MoreFunQuicksandMod-1.1.1-1.7.10 (2)")
    legacy = old / "assets/morefunquicksandmod"
    root = Path("src/main/resources")
    current = root / "assets/mfqm"
    copied = 0
    for path in (legacy / "textures").rglob("*"):
        if path.is_file():
            dest = current / path.relative_to(legacy).as_posix().lower()
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, dest)
            copied += 1
    # The legacy flowing mucus strip is 32x304; modern atlas loading requires explicit frames.
    write(current, "textures/blocks/mucus_flowing.png.mcmeta", {"animation": {"frametime": 8, "width": 32, "height": 16}})
    for block in BLOCKS:
        variants = [0] if block in LIQUID else range(16)
        states = {}
        for variant in variants:
            name = block if variant == 0 else f"{block}_{variant}"
            write(current, f"models/block/{name}.json", model(block, variant))
            states["" if block in LIQUID else f"variant={variant}"] = {"model": "mfqm:block/" + name}
        write(current, f"blockstates/{block}.json", {"variants": states})
        write(current, f"items/{block}.json", {"model": {"type": "minecraft:model", "model": "mfqm:block/" + block}})
        for variant in range(1, 16):
            if block in LIQUID:
                break
            write(current, f"items/{block}_variant_{variant}.json", {"model": {"type": "minecraft:model", "model": f"mfqm:block/{block}_{variant}"}})
    for atlas in ("blocks", "items"):
        write(root, f"assets/minecraft/atlases/{atlas}.json", {"sources": [{"type": "minecraft:directory", "source": atlas, "prefix": atlas + "/"}]})
    equipment = {"gas_mask": "gasmask", "life_jacket": "lifejacket", "wading_boots": "wadingboots", "tall_leather_boots": "prewadingboots0", "slimy_tall_leather_boots": "prewadingboots1"}
    for item, original in equipment.items():
        source = current / f"textures/armor/{original}.png"
        dest = current / f"textures/entity/equipment/humanoid/{item}.png"
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        write(current, f"equipment/{item}.json", {"layers": {"humanoid": [{"texture": "mfqm:" + item}]}})
    # Retain complete original translations; modern registration names are added by the item map.
    for oldname, newname in [("en_US.lang", "en_us"), ("ru_RU.lang", "ru_ru")]:
        target = current / f"lang/{newname}.json"
        lang = json.loads(target.read_text(encoding="utf-8")) if target.exists() else {}
        for line in (legacy / "lang" / oldname).read_text(encoding="utf-8-sig").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                lang[key] = value
        lang["itemGroup.mfqm"] = "More Fun Quicksand Mod"
        write(current, f"lang/{newname}.json", lang)
    mapping = Path("tools/legacy_item_map.json")
    if mapping.exists():
        restore_item_definitions(current, json.loads(mapping.read_text(encoding="utf-8")))
    print(f"Restored {copied} original texture/animation files and 49 block resource families.")


BLOCK_KEYS = dict(zip(BLOCKS, "Mud0 Bog SoftSnow SoftSand SoftQuicksand Morass0 WetPeat HPeat BrownClay0 Wax Quicksand Quicksand JungleQuicksand LiquidMire SLiquidMire SinkyLiquid SinkingSlime Mucus Mire Moor HardenedClay SinkingClay TangleRootMoss DenseWeb Tar Larvae CorruptedSand SwallowingFlesh Acid Slurry Gas SoftGravel0 Honey SolidHoney Honeycomb0 LiquidChocolate ChocolateBlock Quick-Rug0 LureBlock BlossomBlock0 BlossomBlockSlab0 TempVore MeatWall0 TempMeat WaxWood0 CustomLilyPad MoorGrass0 Tendrils0 LeavesPile".split()))
VARIANTS = {"mud": ("Mud", 4), "morass": ("Morass", 9), "brown_clay": ("BrownClay", 8), "soft_gravel": ("SoftGravel", 2), "honeycomb": ("Honeycomb", 3), "sinking_rug": ("Quick-Rug", 16), "blossom": ("BlossomBlock", 12), "blossom_slab": ("BlossomBlockSlab", 6), "wax_wood": ("WaxWood", 8), "moor_grass": ("MoorGrass", 6), "tendrils": ("Tendrils", 4), "meat_wall": ("MeatWall", 11)}

def restore_item_definitions(current, mapping):
    for item, data in mapping.items():
        write(current, f"models/item/{item}.json", {"parent": "minecraft:item/handheld" if item in ("long_stick", "grappling_hook") else "minecraft:item/generated", "textures": {"layer0": "mfqm:items/" + data["texture"]}})
        write(current, f"items/{item}.json", {"model": {"type": "minecraft:model", "model": "mfqm:item/" + item}})
    # 1.21.11 uses per-creature egg textures instead of the removed two-tint template.
    for mob in ("vore_slime", "muddy_blob", "sand_blob", "tar_slime", "bee"):
        egg = mob + "_spawn_egg"
        write(current, f"models/item/{egg}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/" + ("bee" if mob == "bee" else "slime") + "_spawn_egg"}})
        write(current, f"items/{egg}.json", {"model": {"type": "minecraft:model", "model": "mfqm:item/" + egg}})
    write(current, "particles/mud_bubble.json", {"textures": ["minecraft:bubble"]})
    for locale in ("en_us", "ru_ru"):
        lang = json.loads((current / f"lang/{locale}.json").read_text(encoding="utf-8"))
        for item, data in mapping.items():
            lang["item.mfqm." + item] = data[locale]
        for block, oldkey in BLOCK_KEYS.items():
            lang["block.mfqm." + block] = lang.get("tile." + oldkey + ".name", block.replace("_", " ").title())
            lang["item.mfqm." + block] = lang["block.mfqm." + block]
        for block, (prefix, count) in VARIANTS.items():
            for variant in range(1, count):
                lang[f"item.mfqm.{block}_variant_{variant}"] = lang.get(f"tile.{prefix}{variant}.name", lang["block.mfqm." + block])
        lang["item.mfqm.soft_snow_variant_1"] = lang["block.mfqm.soft_snow"]
        for fluid in LIQUID:
            lang["fluid_type.mfqm." + fluid] = lang["block.mfqm." + fluid]
            lang["fluid.mfqm." + fluid] = lang["block.mfqm." + fluid]
        entities = {"bee": "Bee", "vore_slime": "VoreSlime", "muddy_blob": "MuddyBlob", "sand_blob": "SandBlob", "tar_slime": "TarSlime"}
        for entity in "bee vore_slime muddy_blob sand_blob tar_slime tentacles mud_tentacles bubble tar_treads slime_hole long_stick rope hook rescue sinking_potion liquid_ball".split():
            lang["entity.mfqm." + entity] = lang.get("entity." + entities.get(entity, "") + ".name", entity.replace("_", " ").title())
        for mob in ("vore_slime", "muddy_blob", "sand_blob", "tar_slime", "bee"):
            lang[f"item.mfqm.{mob}_spawn_egg"] = lang["entity.mfqm." + mob] + (" Spawn Egg" if locale == "en_us" else " — яйцо призывания")
        lang.update({"message.mfqm.probe_depth": "Depth: %s blocks" if locale == "en_us" else "Глубина: %s блоков", "key.mfqm.reel_in": "Reel in" if locale == "en_us" else "Подтянуть", "key.mfqm.pay_out": "Pay out cable" if locale == "en_us" else "Ослабить трос", "key.mfqm.release": "Release connector" if locale == "en_us" else "Отцепить", "key.categories.mfqm.controls": "MFQM Controls" if locale == "en_us" else "Управление MFQM"})
        write(current, f"lang/{locale}.json", lang)

if __name__ == "__main__":
    main()
