"""Recognize the exact, approved 0.7.2 fluid-alpha revision without relaxing hashes."""
import json
from pathlib import Path

MANIFEST = Path(__file__).resolve().parents[1] / 'docs/texture-refresh/fluid-alpha-updates.json'


def revision(name):
    # The older protected list was written with Windows path separators.
    name = str(name).replace('\\', '/')
    return json.loads(MANIFEST.read_text(encoding='utf-8'))['textures'].get(name)


def expected_hash(name, baseline_hash):
    record = revision(name)
    return record['sha256'] if record and record['before_sha256'] == baseline_hash else baseline_hash


def expected_alpha(name, original):
    record = revision(name)
    if not record:
        return original
    if not (original == record['previous_alpha']).all():
        raise AssertionError(f'Unexpected original fluid alpha: {name}')
    result = original.copy()
    result.fill(record['alpha'])
    return result
