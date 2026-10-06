"""Recover fixed tomb placement from decompiled 1.7.10 code into a modern template."""
import gzip
import json
import re
import struct
from pathlib import Path

TYPES = {"short": 2, "int": 3, "long": 4, "string": 8, "list": 9, "compound": 10}


def utf(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def payload(kind, value):
    if kind in ("short", "int", "long"):
        return struct.pack({"short": ">h", "int": ">i", "long": ">q"}[kind], value)
    if kind == "string":
        return utf(value)
    if kind == "list":
        subtype, entries = value
        return bytes([TYPES[subtype]]) + struct.pack(">i", len(entries)) + b"".join(payload(subtype, entry) for entry in entries)
    if kind == "compound":
        return b"".join(bytes([TYPES[t]]) + utf(k) + payload(t, v) for k, (t, v) in value.items()) + b"\0"
    raise ValueError(kind)


def state(block, meta):
    if block == "sandstone":
        return {"Name": ("string", "minecraft:" + {0: "sandstone", 1: "chiseled_sandstone", 2: "cut_sandstone"}[meta])}
    if block in ("stone_slab", "double_stone_slab"):
        name = "sandstone_slab" if meta % 8 == 1 else "smooth_stone_slab"
        slabtype = "double" if block == "double_stone_slab" else "top" if meta >= 8 else "bottom"
        return {"Name": ("string", "minecraft:" + name), "Properties": ("compound", {"type": ("string", slabtype), "waterlogged": ("string", "false")})}
    if block == "SandBlock":
        return {"Name": ("string", "mfqm:dry_quicksand"), "Properties": ("compound", {"level": ("string", str(meta))})}
    return {"Name": ("string", "minecraft:" + block)}


def main():
    source = Path("run/1.21.11/legacy-audit/recovered-worldgen/GenerateDesertTombs.java").read_text(encoding="utf-8")
    fields = json.loads(Path("run/1.21.11/legacy-audit/mcp-fields.json").read_text(encoding="utf-8"))
    source = source[source.index("   public boolean func_76484_a("):]
    operations = re.findall(r"this\.(setBlock|generateSpawner|generateChest2?)\(([^;]+)\);", source)
    assert len(operations) == 8504, len(operations)
    placed = {}
    chests = spawners = 0
    for op, arguments in operations:
        args = [part.strip() for part in arguments.split(",")]
        assert args[0] == "world", arguments
        pos = []
        for axis, expression in zip("ijk", args[1:4]):
            match = re.fullmatch(axis + r"(?:\s*([+-])\s*(-?\d+))?", expression)
            assert match, expression
            pos.append(int(match[2] or 0) * (-1 if match[1] == "-" else 1))
        pos = tuple(pos)
        meta = args[-1] if op != "generateSpawner" else "0"
        nbt = None
        if op == "setBlock":
            reference = args[4]
            assert reference.startswith("Blocks.") or reference == "MFQM.SandBlock", reference
            block = fields[reference.split(".", 1)[1]] if reference.startswith("Blocks.") else "SandBlock"
            blockstate = state(block, int(meta))
        elif op.startswith("generateChest"):
            facing = {2: "north", 3: "south", 4: "west", 5: "east"}[int(meta)]
            blockstate = {"Name": ("string", "minecraft:chest"), "Properties": ("compound", {"facing": ("string", facing), "type": ("string", "single"), "waterlogged": ("string", "false")})}
            nbt = {"id": ("string", "minecraft:chest"), "LootTable": ("string", "mfqm:chests/desert_tomb_rare" if op == "generateChest2" else "mfqm:chests/desert_tomb")}
            chests += 1
        else:
            blockstate = {"Name": ("string", "minecraft:spawner")}
            nbt = {"id": ("string", "minecraft:mob_spawner"), "SpawnData": ("compound", {"entity": ("compound", {"id": ("string", "minecraft:skeleton" if spawners % 2 == 0 else "minecraft:zombie")})}), "MinSpawnDelay": ("short", 400), "MaxSpawnDelay": ("short", 1600), "MaxNearbyEntities": ("short", 3), "SpawnRange": ("short", 32), "SpawnCount": ("short", 1)}
            spawners += 1
        placed[pos] = (blockstate, nbt)
    assert chests == 9 and spawners == 6, (chests, spawners)
    minimum = tuple(min(p[d] for p in placed) for d in range(3))
    size = tuple(max(p[d] for p in placed) - minimum[d] + 1 for d in range(3))
    palette, indices, blocks = [], {}, []
    for pos, (blockstate, nbt) in sorted(placed.items()):
        key = json.dumps(blockstate, sort_keys=True)
        if key not in indices:
            indices[key] = len(palette)
            palette.append(blockstate)
        entry = {"pos": ("list", ("int", [pos[d] - minimum[d] for d in range(3)])), "state": ("int", indices[key])}
        if nbt:
            entry["nbt"] = ("compound", nbt)
        blocks.append(entry)
    data = {"DataVersion": ("int", 4671), "size": ("list", ("int", list(size))), "palette": ("list", ("compound", palette)), "blocks": ("list", ("compound", blocks)), "entities": ("list", ("compound", []))}
    out = Path("src/main/resources/data/mfqm/structure/desert_tomb.nbt")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(b"\x0a\0\0" + payload("compound", data), mtime=0))
    report = {"source_operations": len(operations), "unique_positions": len(placed), "size": size, "legacy_minimum": minimum, "palette": len(palette), "chests": chests, "spawners": spawners}
    Path("run/1.21.11/legacy-audit/tomb-conversion.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
