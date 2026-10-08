"""Bake/verify connected board JSON geometry using existing textures; no game launch."""
import argparse
import json
from itertools import product
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mfqm'
DIRECTIONS = ('north', 'east', 'south', 'west')
# Non-overlapping pieces of the glue surface in model pixels: x1, z1, x2, z2.
PIECES = {
    'center': ((), (1, 1, 15, 15)),
    'north': (('north',), (1, 0, 15, 1)),
    'east': (('east',), (15, 1, 16, 15)),
    'south': (('south',), (1, 15, 15, 16)),
    'west': (('west',), (0, 1, 1, 15)),
    'north_east': (('north', 'east'), (15, 0, 16, 1)),
    'south_east': (('south', 'east'), (15, 15, 16, 16)),
    'south_west': (('south', 'west'), (0, 15, 1, 16)),
    'north_west': (('north', 'west'), (0, 0, 1, 1)),
}


def bake():
    parts = [{'apply': {'model': 'mfqm:block/sticky_board'}}]
    for name, (directions, bounds) in PIECES.items():
        x1, z1, x2, z2 = bounds
        model_name = 'sticky_board_glue_' + name
        model = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
                 'textures': {'glue': 'mfqm:blocks/stickyboard_glue', 'particle': 'mfqm:blocks/stickyboard_glue'},
                 'elements': [{'from': [x1, 1, z1], 'to': [x2, 1.1875, z2],
                               'faces': {'up': {'texture': '#glue', 'uv': [x1, z1, x2, z2]}}}]}
        (ASSETS / 'models/block' / (model_name + '.json')).write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
        condition = {'charge': '1|2|3|4|5|6|7', **{direction: 'true' for direction in directions}}
        parts.append({'when': condition, 'apply': {'model': 'mfqm:block/' + model_name}})
    (ASSETS / 'blockstates/sticky_board.json').write_text(json.dumps({'multipart': parts}, indent=2) + '\n', encoding='utf-8')


def verify():
    state = json.loads((ASSETS / 'blockstates/sticky_board.json').read_text(encoding='utf-8'))
    assert 'multipart' in state, 'board needs neighbor-aware multipart geometry'
    cases = 0
    for flags in product((False, True), repeat=4):
        neighbors = dict(zip(DIRECTIONS, flags))
        for charge in range(8):
            values = {'charge': str(charge), **{k: str(v).lower() for k, v in neighbors.items()}}
            coverage = [[0] * 16 for _ in range(16)]
            bases = 0
            for part in state['multipart']:
                if not all(values[k] in allowed.split('|') for k, allowed in part.get('when', {}).items()):
                    continue
                model = part['apply']['model']
                if model == 'mfqm:block/sticky_board':
                    bases += 1
                    continue
                data = json.loads((ASSETS / 'models' / (model.removeprefix('mfqm:') + '.json')).read_text(encoding='utf-8'))
                assert data['render_type'] == 'minecraft:cutout'
                for element in data['elements']:
                    x1, y1, z1 = element['from']
                    x2, y2, z2 = element['to']
                    assert y1 == 1 and y2 == 1.1875, 'all glue pieces share the original top height'
                    assert element['faces']['up']['uv'] == [x1, z1, x2, z2], 'UVs continue across piece boundaries'
                    for z in range(z1, z2):
                        for x in range(x1, x2):
                            coverage[z][x] += 1
            assert bases == 1, 'always exactly one wooden base'
            for z in range(16):
                for x in range(16):
                    exposed = ((x == 0 and not neighbors['west']) or (x == 15 and not neighbors['east'])
                               or (z == 0 and not neighbors['north']) or (z == 15 and not neighbors['south']))
                    expected = int(charge > 0 and not exposed)
                    assert coverage[z][x] == expected, (neighbors, charge, x, z, 'gap, overlap or unwanted inner frame')
            cases += 1
    print(f'Connected boards: {cases} charge/connection combinations passed; seams, corners, outer frames and UVs verified')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bake', action='store_true')
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    if args.bake:
        bake()
    if args.verify or not args.bake:
        verify()
