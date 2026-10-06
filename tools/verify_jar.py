"""Validate the distributable's Minecraft 1.21.11 compatibility and resources.

Usage: python tools/verify_jar.py path/to/mod.jar (Python 3.11+).
"""

import argparse
import json
import struct
import tomllib
from collections import Counter
from pathlib import Path
from zipfile import ZipFile


def verify(jar_path: Path) -> list[str]:
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    with ZipFile(jar_path) as jar:
        names = jar.namelist()
        entries = set(names)
        require(all(count == 1 for count in Counter(names).values()), "Duplicate JAR entries")
        require(not any(".cache/" in name for name in names), "Datagen cache included in JAR")
        metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        dependencies = {entry["modId"]: entry for entry in metadata["dependencies"]["mfqm"]}
        require(dependencies["minecraft"]["versionRange"] == "[1.21.11]", "Minecraft dependency must be [1.21.11]")
        require(dependencies["neoforge"]["versionRange"] == "[21.11.45,)", "NeoForge dependency must be [21.11.45,)")
        mod = next(entry for entry in metadata["mods"] if entry["modId"] == "mfqm")
        require("${" not in str(metadata), "Unexpanded mod metadata")
        require(mod.get("logoFile") in entries, "Missing logoFile resource")
        require("META-INF/accesstransformer.cfg" in entries, "Missing recipe manager access transformer")
        if "META-INF/accesstransformer.cfg" in entries:
            require("public net.minecraft.world.item.crafting.RecipeManager recipes" in jar.read("META-INF/accesstransformer.cfg").decode("utf-8"), "Missing acquisition/compatibility recipe access")

        classes = [name for name in names if name.startswith("com/mfqm/") and name.endswith(".class")]
        require(bool(classes), "No mod classes found")
        majors = {struct.unpack(">H", jar.read(name)[6:8])[0] for name in classes}
        require(majors == {65}, f"Expected Java 21 bytecode (65), found {sorted(majors)}")

        generated = Path(__file__).resolve().parents[1] / "src/generated/resources"
        for path in generated.rglob("*.json"):
            name = path.relative_to(generated).as_posix()
            require(name in entries, f"Generated resource missing: {name}")
            if name in entries:
                require(json.loads(jar.read(name)) == json.loads(path.read_text(encoding="utf-8")), f"Generated resource differs: {name}")

        language = json.loads(jar.read("assets/mfqm/lang/en_us.json"))
        for name in names:
            if name.startswith("data/mfqm/damage_type/") and name.endswith(".json"):
                damage_type = json.loads(jar.read(name))
                message = "death.attack." + damage_type["message_id"]
                for suffix in ("", ".player", ".item"):
                    require(message + suffix in language, f"Missing death translation: {message + suffix}")
            if name.startswith("data/minecraft/tags/damage_type/") and name.endswith(".json"):
                for value in json.loads(jar.read(name))["values"]:
                    identifier = value if isinstance(value, str) else value["id"]
                    if identifier.startswith("mfqm:"):
                        require(f"data/mfqm/damage_type/{identifier.split(':', 1)[1]}.json" in entries, f"Unknown damage tag reference: {identifier}")

        sounds = json.loads(jar.read("assets/mfqm/sounds.json"))
        subtitles = {definition.get("subtitle") for definition in sounds.values()}
        for key in language:
            if key.startswith("sound.mfqm."):
                require(key in subtitles, f"Unbound sound subtitle: {key}")
        for event, definition in sounds.items():
            require("category" not in definition, f"Legacy sound category: {event}")
            if definition.get("subtitle"):
                require(definition["subtitle"] in language, f"Missing subtitle translation: {event}")
            for sound in definition["sounds"]:
                if isinstance(sound, dict) and sound.get("type") == "event":
                    continue
                identifier = sound if isinstance(sound, str) else sound["name"]
                namespace, path = identifier.split(":", 1)
                require(f"assets/{namespace}/sounds/{path}.ogg" in entries, f"Missing sound file: {identifier}")
    return errors


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    args = parser.parse_args()
    problems = verify(args.jar)
    for problem in problems:
        print("FAIL:", problem)
    if problems:
        raise SystemExit(1)
    print("PASS: Minecraft 1.21.11, NeoForge 21.11.45+, Java 21 and packaged resources")
