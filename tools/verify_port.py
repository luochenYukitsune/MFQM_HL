"""Check actual release resources and legacy coverage; registry runtime is checked in-game."""
import json
import sys
import struct
import argparse
from pathlib import Path
from zipfile import ZipFile

BLOCKS = "mud bog soft_snow dry_quicksand soft_quicksand morass wet_peat peat brown_clay wax quicksand sandstone_trap jungle_quicksand liquid_mire stable_liquid_mire sinky_liquid sinking_slime mucus mire moor hardened_clay sinking_clay tangleroot_moss dense_web tar larvae corrupted_sand swallowing_flesh acid slurry gas soft_gravel honey solid_honey honeycomb liquid_chocolate chocolate sinking_rug lure blossom blossom_slab vore_hole meat_wall meat_hole wax_wood custom_lily_pad moor_grass tendrils leaves_pile".split()
VARIANT_COUNTS = {"mud": 4, "tendrils": 4, "soft_snow": 2, "soft_gravel": 2,
                  "morass": 9, "brown_clay": 8, "wax_wood": 8, "honeycomb": 3,
                  "sinking_rug": 16, "blossom": 12, "blossom_slab": 6,
                  "moor_grass": 6, "meat_wall": 11}


def registered_items():
    mapping = Path(__file__).with_name("legacy_item_map.json")
    items = set(BLOCKS) | set(json.loads(mapping.read_text(encoding="utf-8")))
    items.update(f"{block}_variant_{n}" for block, count in VARIANT_COUNTS.items() for n in range(1, count))
    items.update(f"{mob}_spawn_egg" for mob in ("vore_slime", "muddy_blob", "sand_blob", "tar_slime", "bee"))
    assert len(items) == 178
    return items


def check(files):
    errors = []
    items = registered_items()
    for item in items:
        if f"assets/mfqm/items/{item}.json" not in files:
            errors.append(f"missing client item definition: {item}")
    for block in BLOCKS:
        path = f"assets/mfqm/blockstates/{block}.json"
        if path not in files:
            errors.append(f"missing blockstate: {block}")
    textures = [p for p in files if p.startswith("assets/mfqm/textures/") and p.endswith(".png")]
    if len(textures) < 246:
        errors.append(f"only {len(textures)} gameplay textures, expected at least 246")
    animations = [p for p in files if p.startswith("assets/mfqm/textures/") and p.endswith(".mcmeta")]
    if len(animations) < 48:
        errors.append(f"only {len(animations)} animations, expected at least 48")
    for lang in ("en_us", "ru_ru"):
        if f"assets/mfqm/lang/{lang}.json" not in files:
            errors.append(f"missing language {lang}")
    if not any(p.startswith("data/mfqm/worldgen/placed_feature/") for p in files):
        errors.append("no placed world features")
    recipes = [p for p in files if p.startswith("data/mfqm/recipe/") and p.endswith(".json")]
    if len(recipes) != 205:
        errors.append(f"expected 205 core recipes, found {len(recipes)}")
    templates = [p for p in files if p.startswith("data/mfqm/mfqm_compat_recipe/") and p.endswith(".json")]
    if len(templates) != 9:
        errors.append(f"expected nine optional compatibility templates, found {len(templates)}")
    else:
        covered = {json.loads(files[path]).get("source_line") for path in templates}
        if covered != {2279, 2313, 2317, 2321, 2356, 2359, 2363, 2410, 2447}:
            errors.append("optional compatibility templates do not cover all original call sites")
    for chest in ("desert_tomb", "desert_tomb_rare", "honey"):
        if f"data/mfqm/loot_table/chests/{chest}.json" not in files:
            errors.append(f"missing chest loot table: {chest}")
    if "data/mfqm/structure/desert_tomb.nbt" not in files:
        errors.append("missing original desert tomb template")
    for path, raw in files.items():
        if not path.endswith(".json"):
            continue
        try:
            data = json.loads(raw)
        except (ValueError, UnicodeError) as exc:
            errors.append(f"invalid JSON {path}: {exc}")
            continue
        if path.startswith("assets/mfqm/models/"):
            for texture in data.get("textures", {}).values():
                if texture.startswith("mfqm:"):
                    target = "assets/mfqm/textures/" + texture.split(":", 1)[1] + ".png"
                    if target not in files:
                        errors.append(f"unresolved texture {texture} in {path}")
        if path.startswith(("assets/mfqm/items/", "assets/mfqm/blockstates/")):
            def model_refs(value):
                if isinstance(value, dict):
                    for key, child in value.items():
                        if key == "model" and isinstance(child, str) and child.startswith("mfqm:"):
                            target = "assets/mfqm/models/" + child.split(":", 1)[1] + ".json"
                            if target not in files: errors.append(f"unresolved model {child} in {path}")
                        else: model_refs(child)
                elif isinstance(value, list):
                    for child in value: model_refs(child)
            model_refs(data)
        if path in recipes:
            result = data.get("result", {})
            output = result.get("id", "") if isinstance(result, dict) else result
            if output.startswith("mfqm:") and output.split(":", 1)[1] not in items:
                errors.append(f"unknown recipe output {output} in {path}")
        if path.startswith("assets/mfqm/equipment/"):
            for layer_type, layers in data.get("layers", {}).items():
                for layer in layers:
                    texture = layer["texture"]
                    if texture.startswith("mfqm:") and f"assets/mfqm/textures/entity/equipment/{layer_type}/{texture.split(':', 1)[1]}.png" not in files:
                        errors.append(f"missing equipment texture {texture} in {path}")
    for path in animations:
        png = path.removesuffix(".mcmeta")
        if png not in files:
            errors.append(f"orphan animation metadata: {path}")
            continue
        animation = json.loads(files[path]).get("animation", {})
        width, height = struct.unpack(">II", files[png][16:24])
        frame_width = animation.get("width", width if "height" in animation else min(width, height))
        frame_height = animation.get("height", height if "width" in animation else min(width, height))
        if frame_width <= 0 or frame_height <= 0 or width % frame_width or height % frame_height:
            errors.append(f"invalid animation frame dimensions in {path}")
            continue
        frames = width // frame_width * (height // frame_height)
        for frame in animation.get("frames", []):
            index = frame if isinstance(frame, int) else frame["index"]
            if not 0 <= index < frames: errors.append(f"invalid animation frame {index} in {path}")
    return errors


def check_legacy(files, old):
    legacy = old / "assets/morefunquicksandmod"
    errors = []
    pngs = list((legacy / "textures").rglob("*.png"))
    if len(pngs) != 246: errors.append(f"legacy reference has {len(pngs)} PNGs, expected 246")
    for path in pngs:
        target = "assets/mfqm/" + path.relative_to(legacy).as_posix().lower()
        if files.get(target) != path.read_bytes(): errors.append(f"legacy pixels changed or absent: {target}")
    for locale, original in (("en_us", "en_US.lang"), ("ru_ru", "ru_RU.lang")):
        language = json.loads(files[f"assets/mfqm/lang/{locale}.json"])
        for line in (legacy / "lang" / original).read_text(encoding="utf-8-sig").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                if language.get(key) != value: errors.append(f"legacy translation changed or absent: {locale}/{key}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path, nargs="?", default=Path("src/main/resources"))
    parser.add_argument("--legacy", type=Path, help="Also verify all original PNG bytes and translations")
    args = parser.parse_args()
    target = args.target
    if target.is_dir():
        files = {p.relative_to(target).as_posix(): p.read_bytes() for p in target.rglob("*") if p.is_file()}
    else:
        with ZipFile(target) as archive:
            files = {p: archive.read(p) for p in archive.namelist() if not p.endswith("/")}
    errors = check(files)
    if args.legacy: errors.extend(check_legacy(files, args.legacy))
    for error in errors:
        print(error)
    print(f"Port resource check: {len(errors)} errors")
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
