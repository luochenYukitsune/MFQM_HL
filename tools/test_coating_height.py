"""Behavioral checks for anatomical skin UV coverage, independent of saved hashes."""
import unittest
import numpy as np
from coating_height import height_mask, crop_overlay


class CoatingHeightTests(unittest.TestCase):
    def test_uv_anatomy(self):
        h = height_mask(64, 32)
        for x,y,expected in [(8,18,0),(4,18,12),(5,22,10),
                             (20,18,24),(30,18,12),(22,25,19),
                             (45,18,24),(50,18,12),(46,25,19),
                             (10,3,32),(18,3,24),(10,12,28)]:
            self.assertEqual(h[y,x], expected, (x,y))
        self.assertTrue(np.isinf(h[3,35]), 'unused hat UV must stay transparent')

    def test_lower_legs_never_include_upper_body(self):
        source = np.full((64,128,4),255,dtype=np.uint8)
        for level in range(1,4):
            a = crop_overlay(source,level,'mudoverlay')[:,:,3]
            self.assertFalse(a[:32].any(), 'head remains dry')
            self.assertFalse(a[32:,32:].any(), 'torso and arms remain dry')
            self.assertTrue(a[32:,:32].any(), 'feet retain material')

    def test_height_cap_and_progression(self):
        source = np.full((64,128,4),255,dtype=np.uint8)
        h = height_mask(128,64)
        previous = np.zeros((64,128),dtype=bool)
        for level in range(1,11):
            a = crop_overlay(source,level,'honeyoverlay')[:,:,3]>0
            self.assertTrue(np.all(h[a]<=32*level/10),level)
            self.assertTrue(np.all(a[previous]),level)
            self.assertGreater(a.sum(),previous.sum(),level)
            previous=a

    def test_part_thresholds(self):
        source=np.full((32,64,4),255,dtype=np.uint8)
        self.assertEqual(crop_overlay(source,3,'taroverlay')[18,30,3],0)
        self.assertEqual(crop_overlay(source,4,'taroverlay')[18,30,3],255)
        self.assertEqual(crop_overlay(source,7,'taroverlay')[3,18,3],0)
        self.assertEqual(crop_overlay(source,8,'taroverlay')[3,18,3],255)
        self.assertEqual(crop_overlay(source,9,'taroverlay')[3,10,3],0)
        self.assertEqual(crop_overlay(source,10,'taroverlay')[3,10,3],255)

    def test_source_color_alpha_and_holes(self):
        source=np.zeros((64,128,4),dtype=np.uint8)
        source[:]=[237,239,241,180]
        source[60,10,3]=0
        original=source.copy()
        glue=crop_overlay(source,10,'glueoverlay')
        self.assertEqual(glue[61,10,3],99)
        self.assertEqual(glue[60,10,3],0)
        self.assertTrue(np.array_equal(glue[61,10,:3],source[61,10,:3]))
        self.assertTrue(np.array_equal(source,original),'source PNG data is never mutated')

    def test_invalid_input(self):
        for level in [0,11]:
            with self.assertRaises(ValueError):crop_overlay(np.zeros((32,64,4),dtype=np.uint8),level,'mudoverlay')
        with self.assertRaises(ValueError):height_mask(63,32)


if __name__=='__main__':unittest.main()
