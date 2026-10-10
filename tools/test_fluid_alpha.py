"""Do not let a historical validator accept arbitrary newer textures."""
import hashlib
import unittest
import numpy as np
from fluid_alpha import expected_hash, expected_alpha, revision


class FluidAlphaRevisionTests(unittest.TestCase):
    def test_only_exact_historical_hash_can_upgrade(self):
        record = revision('blocks/honey_still.png')
        self.assertEqual(expected_hash('blocks/honey_still.png', record['before_sha256']), record['sha256'])
        self.assertEqual(expected_hash('blocks/honey_still.png', 'unrecognized'), 'unrecognized')
        self.assertEqual(expected_hash('blocks/honey_still.png', record['sha256']), record['sha256'])

    def test_windows_protected_paths_use_the_same_revision(self):
        self.assertEqual(revision('blocks\\honey_still.png'), revision('blocks/honey_still.png'))

    def test_unrelated_texture_is_unchanged(self):
        alpha = np.array([[0, 90, 255]], dtype=np.uint8)
        self.assertEqual(expected_hash('blocks/bog_still.png', 'baseline'), 'baseline')
        self.assertIs(expected_alpha('blocks/bog_still.png', alpha), alpha)

    def test_alpha_upgrade_checks_source_and_preserves_input(self):
        original = np.full((2, 3), 204, dtype=np.uint8)
        result = expected_alpha('blocks/honey_still.png', original)
        self.assertTrue((result == 180).all())
        self.assertTrue((original == 204).all())
        self.assertEqual(result.dtype, original.dtype)
        with self.assertRaises(AssertionError):
            expected_alpha('blocks/honey_still.png', np.zeros((2, 3), dtype=np.uint8))

    def test_manifest_matches_every_approved_runtime_image(self):
        from pathlib import Path
        from PIL import Image
        root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mfqm/textures'
        for family in ('honey', 'tar', 'slime', 'mucus', 'sinkyliquid'):
            for kind in ('still', 'flowing'):
                name = f'blocks/{family}_{kind}.png'
                record = revision(name)
                self.assertEqual(hashlib.sha256((root/name).read_bytes()).hexdigest(), record['sha256'])
                with Image.open(root/name) as image:
                    self.assertTrue((np.array(image.convert('RGBA'))[:, :, 3] == record['alpha']).all())


if __name__ == '__main__':
    unittest.main()
