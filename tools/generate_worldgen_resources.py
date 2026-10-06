"""Modern worldgen data. Generation algorithms live in server-side Java features."""
from pathlib import Path
import json
from restore_legacy_resources import write

root = Path("src/main/resources")
def data(path, value): write(root, "data/mfqm/" + path + ".json", value)

data("worldgen/configured_feature/terrain", {"type": "mfqm:legacy_terrain", "config": {}})
for dimension in ("overworld", "nether"):
    height = {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"} if dimension == "overworld" else {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 12}, "max_inclusive": {"absolute": 110}}}
    data("worldgen/placed_feature/terrain_" + dimension, {"feature": "mfqm:terrain", "placement": [{"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"}, height, {"type": "minecraft:biome"}]})
data("neoforge/biome_modifier/terrain", {"type": "mfqm:terrain_membership", "surface_feature": "mfqm:terrain_overworld", "nether_feature": "mfqm:terrain_nether"})
data("neoforge/biome_modifier/swamp_color", {"type": "mfqm:swamp_color"})
data("worldgen/structure/desert_tomb", {"type": "mfqm:desert_tomb", "biomes": "#mfqm:spawns_quicksand_desert", "step": "surface_structures", "spawn_overrides": {}, "terrain_adaptation": "none"})
data("worldgen/structure_set/desert_tombs", {"structures": [{"structure": "mfqm:desert_tomb", "weight": 1}], "placement": {"type": "minecraft:random_spread", "salt": 170111854, "spacing": 32, "separation": 12}})
for mob, tag, weight in (("muddy_blob", "muddy_blob_spawnable", 20), ("sand_blob", "sand_blob_spawnable", 25), ("tar_slime", "tar_slime_spawnable", 15), ("vore_slime", "muddy_blob_spawnable", 12)):
    data("neoforge/biome_modifier/spawn_" + mob, {"type": "neoforge:add_spawns", "biomes": "#mfqm:" + tag, "spawners": {"type": "mfqm:" + mob, "weight": weight, "minCount": 1, "maxCount": 3}})

base_entries = json.loads(Path("tools/chest_base_entries.json").read_text(encoding="utf-8"))["entries"]

def loot(entries, rolls, category):
    added = [{"type": "minecraft:item", "name": "minecraft:" + name, "weight": weight, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]} for name, weight, low, high in entries]
    return {"type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": base_entries[category] + added}]}
# These 17 additions and roll ranges are recovered from GenerateDesertTombs.
normal = [("diamond", 3, 1, 3), ("iron_ingot", 10, 1, 5), ("gold_ingot", 5, 1, 3), ("bone", 15, 1, 3), ("stick", 15, 1, 3)]
normal += [(name, 1, 1, 1) for name in ("iron_pickaxe", "iron_sword", "iron_chestplate", "iron_helmet", "iron_leggings", "iron_boots")]
normal += [("coal", 5, 3, 7), ("arrow", 5, 3, 7), ("gunpowder", 3, 1, 1), ("flint", 5, 1, 1), ("string", 15, 1, 1), ("feather", 5, 1, 1)]
rare_weights = {"diamond": 10, "gold_ingot": 10, "bone": 5, "stick": 5, "gunpowder": 5, "string": 5}
rare = [(name, rare_weights.get(name, weight), low, high) for name, weight, low, high in normal]
data("loot_table/chests/desert_tomb", loot(normal, (3, 8), "desert_pyramid"))
data("loot_table/chests/desert_tomb_rare", loot(rare, (6, 17), "simple_dungeon"))
honey = loot([("diamond", 16, 1, 3), ("gold_ingot", 16, 4, 16), ("emerald", 16, 1, 3)], (5, 9), "simple_dungeon")
honey["pools"].append({"rolls": 1, "entries": [{"type": "mfqm:registry_bonus"}]})
data("loot_table/chests/honey", honey)
print("World features, structure set, spawns, swamp color and 3 chest loot tables generated.")
