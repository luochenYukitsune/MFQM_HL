"""Bake AI material sources into Minecraft textures. Requires Pillow and NumPy.

User-approved processing: palette/size normalization, looping animation and UV masking.
Run --bake once inputs.json and sources are available; --verify checks installed outputs.
Originals are archived before any replacement and always used for repeatable baking.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

import numpy as np
from PIL import Image
from fluid_alpha import expected_hash, expected_alpha as approved_alpha

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/texture-refresh"
TEXTURES = ROOT / "src/main/resources/assets/mfqm/textures"
BACKUP = DOC / "originals.zip"
MANIFEST = DOC / "manifest.json"
PREFIXES = ("mud", "mire", "moor", "morass", "bog", "peat", "tar", "honey", "slurry")


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def original(name):
    with ZipFile(BACKUP) as z:
        return Image.open(io.BytesIO(z.read(name))).convert("RGBA")


def preserve_originals():
    DOC.mkdir(parents=True, exist_ok=True)
    if BACKUP.exists():
        return
    selected = [p for p in (TEXTURES / "blocks").glob("*.png") if p.name.startswith(PREFIXES)]
    selected += list((TEXTURES / "entity/mudoverlays").glob("*.png"))
    selected += [TEXTURES / "entity" / name for name in ("muddyblob.png", "tarslime.png")]
    selected += [p for p in (TEXTURES / "items").glob("*.png") if any(s in p.stem for s in ("bucketofmire", "bucketofliquidbog", "bucketofslurry", "bucketoftar", "bucketofhoney", "honeycomb", "peat", "potion_mud"))]
    with ZipFile(BACKUP, "w", ZIP_DEFLATED) as z:
        for p in sorted(selected):
            z.write(p, p.relative_to(TEXTURES).as_posix())
            meta = p.with_suffix(".png.mcmeta")
            if meta.exists():
                z.write(meta, meta.relative_to(TEXTURES).as_posix())


def source_tiles():
    inputs = json.loads((DOC / "inputs.json").read_text(encoding="utf-8"))
    tiles = {}
    for key, name in inputs.items():
        image = Image.open(DOC / name).convert("RGB").resize((32, 32), Image.Resampling.NEAREST)
        a = np.array(image, dtype=float)
        # Weld the opposing boundary pixels before quantization; no new imagery is invented here.
        a[:, 0] = a[:, -1] = (a[:, 0] + a[:, -1]) / 2
        a[0] = a[-1] = (a[0] + a[-1]) / 2
        a[:, 0] = a[:, -1] = (a[:, 0] + a[:, -1]) / 2
        tiles[key] = Image.fromarray(a.astype("uint8")).quantize(colors=32).convert("RGB")
    return tiles


def material(name):
    for key in ("honeycomb", "morass", "moor", "mire", "peat", "slurry", "honey", "tar", "bog"):
        if key in name:
            return key
    return "mud"


def adjusted(tile, old):
    rgb = np.array(tile, dtype=float)
    ref = np.array(old.convert("RGB"), dtype=float)
    # Preserve original state palette relationships while using the newly generated microstructure.
    ratio = np.clip((ref.mean(axis=(0, 1)) + 8) / (rgb.mean(axis=(0, 1)) + 8), .55, 1.65)
    return np.clip(rgb * (.45 + .55 * ratio), 0, 255).astype("uint8")


def frames(tile, count, flowing):
    base = np.array(tile, dtype=np.uint8)
    size = len(base)
    output_size = size * (2 if flowing else 1)
    strip = Image.new("RGB", (output_size, output_size * count))
    for n in range(count):
        phase = 2 * math.pi * n / count
        frame = base.copy()
        if flowing:
            frame = np.roll(frame, round(n * size / count), axis=0)
        else:
            # Tiny periodic ripples; the cycle closes because phase is periodic.
            for y in range(size):
                frame[y] = np.roll(base[y], round(math.sin(phase) * math.sin(2 * math.pi * y / size)), axis=0)
            frame = np.clip(frame.astype(float) * (1 + .025 * math.sin(phase)), 0, 255).astype("uint8")
        # Re-weld after motion, then duplicate the tile for Minecraft's 2x flowing UV domain.
        a = frame.astype(float)
        a[0] = a[-1] = (a[0] + a[-1]) / 2
        a[:, 0] = a[:, -1] = (a[:, 0] + a[:, -1]) / 2
        frame = a.astype("uint8")
        if flowing:
            frame = np.tile(frame, (2, 2, 1))
        strip.paste(Image.fromarray(frame), (0, n * output_size))
    return strip


def save(name, image, records, **extra):
    path = TEXTURES / name
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    records[name] = {"size": list(image.size), "sha256": sha(path.read_bytes()), **extra}


def bake():
    preserve_originals()
    tiles = source_tiles()
    records = {}
    with ZipFile(BACKUP) as z:
        originals = sorted(n for n in z.namelist() if n.endswith(".png"))
        for name in originals:
            old = original(name)
            if name.startswith("entity/mudoverlays/"):
                continue
            key = material(name)
            tile = tiles[key]
            if name.startswith("blocks/"):
                if name + ".mcmeta" in z.namelist():
                    metadata = json.loads(z.read(name + ".mcmeta"))
                    animation = metadata["animation"]
                    fw = animation.get("width", min(old.size))
                    fh = animation.get("height", min(old.size))
                    count = old.width // fw * (old.height // fh)
                    flowing = "_flowing" in name
                    image = frames(tile, count, flowing).convert("RGBA")
                    # Keep RGB generation unchanged and apply the approved fluid-alpha revision.
                    alpha = np.array(old.resize(image.size, Image.Resampling.NEAREST).getchannel("A"))
                    image.putalpha(Image.fromarray(approved_alpha(name, alpha)))
                    animation.pop("width", None)
                    animation.pop("height", None)
                    (TEXTURES / (name + ".mcmeta")).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
                    save(name, image, records, frames=count, frame_size=image.width, flowing=flowing)
                else:
                    image = Image.fromarray(adjusted(tile, old)).convert("RGBA")
                    ref = old.resize((32, 32), Image.Resampling.NEAREST)
                    image.putalpha(ref.getchannel("A"))
                    if "grass" in name:
                        # Keep plant/berry placement readable; regenerate the muddy ground beneath it.
                        a = np.array(ref)
                        plant = (a[:, :, 1] > a[:, :, 0] * 1.15) | (a[:, :, 0] > a[:, :, 1] * 1.8)
                        out = np.array(image)
                        out[plant] = a[plant]
                        image = Image.fromarray(out)
                    save(name, image, records)
            else:
                # Retain icon silhouettes and entity UV layouts, replacing material microdetail.
                size = (old.width * 2, old.height * 2)
                ref = np.array(old.resize(size, Image.Resampling.NEAREST))
                base = np.array(tile.resize((32, 32), Image.Resampling.NEAREST))
                pattern = np.tile(base, (math.ceil(size[1] / 32), math.ceil(size[0] / 32), 1))[:size[1], :size[0]]
                out = ref.copy()
                rgb = ref[:, :, :3].astype(float)
                opaque = ref[:, :, 3] > 0
                warm = (rgb[:, :, 0] >= rgb[:, :, 2] * 1.15) & (rgb[:, :, 0] > rgb[:, :, 1] * 1.03)
                use = opaque & (warm if name.startswith("items/") else np.ones(opaque.shape, dtype=bool))
                if key == "tar":
                    use = opaque & (rgb.mean(axis=2) < 100)
                # Original silhouette shading stays; new material supplies detail and color identity.
                shade = np.clip(rgb.mean(axis=2) / max(1, rgb[use].mean() if use.any() else 1), .65, 1.25)
                out[:, :, :3][use] = np.clip(pattern * shade[:, :, None], 0, 255).astype("uint8")[use]
                save(name, Image.fromarray(out), records)

    # Preserve exact original coverage masks and UV areas for all ten levels.
    for family, seed, mask in (("mudoverlay", "coating", "mudoverlay"), ("slimeoverlay", "coating", "slimeoverlay"),
                               ("taroverlay", "tar", "slimeoverlay"), ("honeyoverlay", "honey", "slimeoverlay")):
        source = np.array(tiles[seed].convert("L"), dtype=float)
        lo, hi = np.percentile(source, (5, 95))
        texture = np.clip(170 + 85 * (source - lo) / max(1, hi - lo), 150, 255).astype("uint8")
        if family == "slimeoverlay":
            texture = np.roll(texture, 7, axis=1)
        gray = np.tile(texture, (2, 4))
        for level in range(10):
            old_mask = original(f"entity/mudoverlays/{mask}{level}.png")
            alpha = np.array(old_mask.resize((128, 64), Image.Resampling.NEAREST).getchannel("A"))
            rgba = np.dstack([gray, gray, gray, alpha])
            rgba[alpha == 0, :3] = 0
            save(f"entity/mudoverlays/{family}{level}.png", Image.fromarray(rgba), records,
                 mask=f"entity/mudoverlays/{mask}{level}.png", level=level)

    MANIFEST.write_text(json.dumps({"version": "0.6.1-dev", "sources": json.loads((DOC / "inputs.json").read_text()),
                                   "textures": records}, indent=2) + "\n", encoding="utf-8")
    verify()


def verify():
    entries = json.loads(MANIFEST.read_text(encoding="utf-8"))["textures"]
    coverage = {}
    for name, info in entries.items():
        path = TEXTURES / name
        assert sha(path.read_bytes()) == expected_hash(name, info["sha256"]), f"unexpected image modification: {name}"
        image = Image.open(path).convert("RGBA")
        assert list(image.size) == info["size"], f"size mismatch: {name}"
        if "frames" in info:
            assert image.height == image.width * info["frames"], name
            metadata = json.loads((TEXTURES / (name + ".mcmeta")).read_text())["animation"]
            with ZipFile(BACKUP) as z:
                previous = json.loads(z.read(name + ".mcmeta"))["animation"]
            timing = {k: v for k, v in metadata.items() if k not in ("width", "height")}
            old_timing = {k: v for k, v in previous.items() if k not in ("width", "height")}
            assert timing == old_timing, f"legacy playback timing changed: {name}"
            animation = [np.array(image.crop((0, n * image.width, image.width, (n + 1) * image.width)))
                         for n in range(info["frames"])]
            assert len({a.tobytes() for a in animation}) > 1, f"static animation: {name}"
            expected_alpha = np.array(original(name).resize(image.size, Image.Resampling.NEAREST).getchannel("A"))
            assert np.array_equal(np.array(image.getchannel("A")), approved_alpha(name, expected_alpha)), f"fluid transparency changed: {name}"
            for frame in animation:
                assert np.array_equal(frame[0], frame[-1]), f"vertical tile seam: {name}"
                assert np.array_equal(frame[:, 0], frame[:, -1]), f"horizontal tile seam: {name}"
                if info["flowing"]:
                    half = frame.shape[0] // 2
                    assert np.array_equal(frame[:half, :half], frame[half:, half:]), f"flow UV scale mismatch: {name}"
            changes = [float(np.abs(a.astype(float)-b.astype(float)).mean())
                       for a, b in zip(animation, animation[1:])]
            wrap = float(np.abs(animation[-1].astype(float)-animation[0].astype(float)).mean())
            assert wrap <= max(changes) * 1.1 + 1, f"abrupt animation loop: {name}"
        elif name.startswith("blocks/") and "grass" not in name and "tartread" not in name:
            rgb = np.array(image)[:, :, :3]
            assert np.array_equal(rgb[0], rgb[-1]) and np.array_equal(rgb[:, 0], rgb[:, -1]), f"block tile seam: {name}"
        if "mask" in info:
            expected = np.array(original(info["mask"]).resize(image.size, Image.Resampling.NEAREST).getchannel("A"))
            actual = np.array(image.getchannel("A"))
            assert np.array_equal(actual, expected), f"UV/coverage alpha altered: {name}"
            assert (actual == 0).any() and (actual > 0).any(), f"missing transparency: {name}"
            family = Path(name).stem.rstrip("0123456789")
            coverage.setdefault(family, []).append((info["level"], float(actual.mean())))
    for family, levels in coverage.items():
        assert len(levels) == 10, family
        levels.sort()
        assert all(b[1] > a[1] for a, b in zip(levels, levels[1:])), f"non-growing coverage: {family}"
    assert len(coverage) == 4, "missing coating families"
    for medium in ("bog", "mire", "slurry", "tar", "honey"):
        still = np.array(Image.open(TEXTURES / f"blocks/{medium}_still.png").convert("RGBA"))[:32]
        flowing = np.array(Image.open(TEXTURES / f"blocks/{medium}_flowing.png").convert("RGBA"))[:32, :32]
        assert np.array_equal(still, flowing), f"source/flowing material or alpha mismatch: {medium}"
    print(f"PASS: {len(entries)} refreshed textures, 10 looping fluid strips, 40 UV-safe transparent coatings")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bake", action="store_true")
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if args.bake:
        bake()
    elif args.verify:
        verify()
    else:
        parser.error("choose --bake or --verify")
