"""Bake new adhesive assets from AI sources without replacing legacy textures.

Requires Python 3.11+, Pillow and NumPy. Processing is explicitly user-authorized.
--verify validates runtime files; --bake deterministically installs new files only.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from fluid_alpha import expected_hash

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/adhesive-textures'
TEX = ROOT / 'src/main/resources/assets/mfqm/textures'
MANIFEST = DOC / 'manifest.json'
FRAMES = 32
FLUID_ALPHA = 180
OVERLAY_ALPHA = 180
FILES = ['blocks/glue_still.png', 'blocks/glue_flow.png',
         'blocks/stickyboard_wood.png', 'blocks/stickyboard_glue.png',
         'items/bucketofglue.png', 'entity/adhesive_strand.png'] + [
         f'entity/mudoverlays/glueoverlay{i}.png' for i in range(10)]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rgba(path):
    return np.array(Image.open(path).convert('RGBA'))


def weld(array):
    array = array.astype(float)
    array[0] = array[-1] = (array[0] + array[-1]) / 2
    array[:, 0] = array[:, -1] = (array[:, 0] + array[:, -1]) / 2
    return np.rint(array).astype('uint8')


def tiles():
    image = Image.open(DOC / 'sources/glue.png').convert('RGBA').resize((32, 32), Image.Resampling.BOX)
    a = np.array(image)
    # Retain generated alpha variation, normalize to usable milky translucency.
    alpha = np.clip(FLUID_ALPHA + .12 * (a[:, :, 3].astype(float) - a[:, :, 3].mean()), 160, 200)
    # Neutral white keeps the base useful for procedurally tinted strands too.
    luma = np.array(image.convert('L'), dtype=float)
    luma = np.clip(237 + (luma - luma.mean()) * .8, 223, 253).astype('uint8')
    gray = np.array(Image.fromarray(luma).quantize(colors=24).convert('L'))
    glue = np.dstack([gray, gray, np.minimum(gray.astype(int) + 2, 255).astype('uint8'), alpha.astype('uint8')])
    wood = Image.open(DOC / 'sources/wood.png').convert('RGB').resize((32, 32), Image.Resampling.BOX)
    wood = wood.quantize(colors=24).convert('RGBA')
    return weld(glue), weld(np.array(wood))


def animation(tile, flowing):
    output = []
    for n in range(FRAMES):
        phase = 2 * math.pi * n / FRAMES
        frame = tile.copy()
        for y in range(32):
            frame[y] = np.roll(tile[y], round(math.sin(phase) * math.sin(2 * math.pi * y / 32)), axis=0)
        if flowing:
            # Periodic one-pixel pulse reads as slow thick movement, no hard scrolling jump.
            frame = np.roll(frame, round(math.sin(phase)), axis=0)
        frame[:, :, :3] = np.clip(frame[:, :, :3].astype(float) * (1 + .015 * math.sin(phase)), 0, 255)
        frame = weld(frame)
        if flowing:
            frame = np.tile(frame, (2, 2, 1))
        output.append(frame)
    return np.concatenate(output, axis=0)


def overlay_mask(level):
    return rgba(DOC / f'sources/masks/slimeoverlay{level}.png')[:, :, 3]


def bake():
    glue, wood = tiles()
    records = {}

    def save(name, array, **fields):
        assert name in FILES, f'Attempt to write outside the new adhesive asset contract: {name}'
        path = TEX / name
        path.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray(array.astype('uint8')).save(path, optimize=True)
        records[name] = dict(size=list(Image.open(path).size), sha256=sha(path), **fields)

    for flowing in (False, True):
        name = f'blocks/glue_{"flow" if flowing else "still"}.png'
        save(name, animation(glue, flowing), frame_size=64 if flowing else 32, frames=FRAMES, flowing=flowing)
        metadata = {'animation': {'frametime': 3, 'interpolate': False}}
        meta_path = TEX / (name + '.mcmeta')
        meta_path.write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
        records[name]['metadata_sha256'] = sha(meta_path)
    save('blocks/stickyboard_wood.png', wood)
    board_glue = glue.copy()
    board_glue[:2, :, 3] = board_glue[-2:, :, 3] = 0
    board_glue[:, :2, 3] = board_glue[:, -2:, 3] = 0
    save('blocks/stickyboard_glue.png', board_glue)
    strand = glue.copy()
    strand[:, :, :3] = np.repeat(strand[:, :, :3].mean(axis=2, keepdims=True).astype('uint8'), 3, axis=2)
    save('entity/adhesive_strand.png', strand)
    bucket = rgba(DOC / 'sources/bucketofhoney-reference.png')
    r, g, b = [bucket[:, :, i].astype(float) for i in range(3)]
    liquid = (bucket[:, :, 3] > 0) & (r > b * 1.2) & (g > b * 1.1)
    if liquid.sum() < 12:
        raise AssertionError('Bucket reference contains no readable liquid area')
    shade = np.clip((r + g + b) / 3 / ((r + g + b)[liquid].mean() / 3), .75, 1.15)
    bucket[:, :, :3][liquid] = np.clip(glue[:, :, :3] * shade[:, :, None], 0, 255).astype('uint8')[liquid]
    # Inventory icon remains opaque inside its original silhouette.
    save('items/bucketofglue.png', bucket, liquid_pixels=int(liquid.sum()))
    pattern = np.tile(glue[:, :, :3], (2, 4, 1))
    for level in range(10):
        mask = overlay_mask(level)
        alpha = np.rint(mask.astype(float) * OVERLAY_ALPHA / 255).astype('uint8')
        save(f'entity/mudoverlays/glueoverlay{level}.png', np.dstack([pattern, alpha]),
             mask_sha256=sha(DOC / f'sources/masks/slimeoverlay{level}.png'))
    sources = {str(p.relative_to(DOC)): sha(p) for p in sorted((DOC / 'sources').rglob('*.png'))}
    MANIFEST.write_text(json.dumps({'outputs': records, 'sources': sources,
                                  'fluid_alpha_target': FLUID_ALPHA, 'overlay_alpha_scale': OVERLAY_ALPHA}, indent=2) + '\n', encoding='utf-8')
    preview(glue, wood, board_glue)


def preview(glue, wood, board_glue):
    image = Image.new('RGBA', (768, 640), '#c3c9d0')
    draw = ImageDraw.Draw(image)
    draw.text((8, 8), 'Glue repeating surface / coated wooden board / glue player UV levels', fill='#202020')
    repeat = Image.fromarray(np.tile(glue, (3, 3, 1))).resize((384, 384), Image.Resampling.NEAREST)
    image.alpha_composite(repeat, (0, 32))
    coated = Image.alpha_composite(Image.fromarray(wood), Image.fromarray(board_glue))
    image.alpha_composite(coated.resize((320, 320), Image.Resampling.NEAREST), (420, 32))
    for j, level in enumerate((0, 4, 9)):
        for y in range(64):
            for x in range(128):
                c = '#758392' if (x // 8 + y // 8) % 2 else '#c9d0d7'
                draw.rectangle((j * 256 + x * 2, 464 + y * 2, j * 256 + x * 2 + 1, 464 + y * 2 + 1), fill=c)
        overlay = Image.open(TEX / f'entity/mudoverlays/glueoverlay{level}.png').resize((256, 128), Image.Resampling.NEAREST)
        image.alpha_composite(overlay, (j * 256, 464))
        draw.text((j * 256 + 6, 440), f'Coverage {level + 1}/10 (UV map)', fill='#202020')
    image.convert('RGB').save(DOC / 'preview.png')


def verify():
    missing = [name for name in FILES if not (TEX / name).exists()]
    if missing:
        raise AssertionError(f'{len(missing)} adhesive assets missing: ' + ', '.join(missing))
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    assert set(manifest['outputs']) == set(FILES), 'Unexpected outputs or incomplete manifest'
    for name, record in manifest['outputs'].items():
        path = TEX / name
        a = rgba(path)
        assert list(Image.open(path).size) == record['size'], f'Dimensions changed: {name}'
        assert sha(path) == record['sha256'], f'Hash changed: {name}'
        if name in ('blocks/glue_still.png', 'blocks/glue_flow.png'):
            size = 64 if '_flow' in name else 32
            assert a.shape == (size * FRAMES, size, 4), f'Frame dimensions: {name}'
            frame_list = a.reshape(FRAMES, size, size, 4)
            for frame in frame_list:
                assert np.array_equal(frame[0], frame[-1]) and np.array_equal(frame[:, 0], frame[:, -1]), f'Seam: {name}'
                assert 155 <= frame[:, :, 3].min() <= frame[:, :, 3].max() <= 205, f'Alpha: {name}'
                assert abs(frame[:, :, 3].mean() - FLUID_ALPHA) < 3, f'Mean alpha: {name}'
                if size == 64:
                    for oy, ox in ((0, 32), (32, 0), (32, 32)):
                        assert np.array_equal(frame[:32, :32], frame[oy:oy + 32, ox:ox + 32]), 'Flowing tile repeats'
            differences = [np.abs(frame_list[(i + 1) % FRAMES].astype(float) - frame_list[i]).mean() for i in range(FRAMES)]
            assert differences[-1] <= max(differences[:-1]) + .01, f'Animation wrap: {name}'
            assert max(differences) > 0, f'Animation not moving: {name}'
            meta_path = TEX / (name + '.mcmeta')
            assert sha(meta_path) == record['metadata_sha256'], f'Animation metadata changed: {name}'
            assert json.loads(meta_path.read_text()) == {'animation': {'frametime': 3, 'interpolate': False}}
        else:
            assert a.shape == ((64, 128, 4) if 'glueoverlay' in name else (32, 32, 4)), name
    still = rgba(TEX / 'blocks/glue_still.png')[:32]
    flow = rgba(TEX / 'blocks/glue_flow.png')[:32, :32]
    assert np.array_equal(still, flow), 'Still and flow phase-zero material identity'
    coverage = []
    for level in range(10):
        mask = overlay_mask(level)
        actual = rgba(TEX / f'entity/mudoverlays/glueoverlay{level}.png')[:, :, 3]
        expected = np.rint(mask.astype(float) * OVERLAY_ALPHA / 255).astype('uint8')
        assert np.array_equal(actual, expected), f'Legacy UV alpha mask {level}'
        assert np.array_equal(actual > 0, mask > 0), f'Legacy UV support {level}'
        coverage.append(float(actual.mean()))
    assert all(b > a for a, b in zip(coverage, coverage[1:])), 'Coverage must strictly increase'
    bucket = rgba(TEX / 'items/bucketofglue.png')
    original_bucket = rgba(DOC / 'sources/bucketofhoney-reference.png')
    assert np.array_equal(bucket[:, :, 3], original_bucket[:, :, 3]), 'Bucket silhouette preserved'
    assert np.all(rgba(TEX / 'blocks/stickyboard_wood.png')[:, :, 3] == 255), 'Opaque wooden base'
    board_alpha = rgba(TEX / 'blocks/stickyboard_glue.png')[:, :, 3]
    assert board_alpha[:2].max() == board_alpha[-2:].max() == board_alpha[:, :2].max() == board_alpha[:, -2:].max() == 0, 'Board glue rim'
    assert board_alpha[2:-2, 2:-2].min() > 0, 'Board glue center'
    for relative, expected in manifest['sources'].items():
        assert sha(DOC / relative) == expected, f'Source changed: {relative}'
    protected = json.loads((DOC / 'protected-textures.json').read_text(encoding='utf-8'))
    for relative, expected in protected.items():
        relative = relative.replace('\\', '/')
        assert relative not in FILES, f'New output incorrectly marked protected: {relative}'
        assert sha(TEX / relative) == expected_hash(relative, expected), f'Existing texture changed: {relative}'
    print(f'PASS: {len(FILES)} adhesive textures; 64 animation frames/seams/alpha/loop; flowing repeat; 10 UV masks and coverage; bucket silhouette; source/output hashes; {len(protected)} protected assets match originals or exact approved alpha revision')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bake', action='store_true')
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    if not (args.bake or args.verify):
        parser.error('Choose --bake and/or --verify')
    if args.bake:
        bake()
    if args.verify:
        verify()
