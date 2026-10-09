"""Build/verify a technical alpha lookup texture, not a new art asset."""
from pathlib import Path
import argparse
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
TEXTURE=ROOT/'src/main/resources/assets/mfqm/textures/entity/translucent_alpha.png'

def build():
    image=Image.new('RGBA',(64,64))
    for alpha in range(256):
        x,y=(alpha%16)*4,(alpha//16)*4
        for dx in range(4):
            for dy in range(4):image.putpixel((x+dx,y+dy),(255,255,255,alpha))
    image.save(TEXTURE)

def verify():
    with Image.open(TEXTURE) as image:
        assert image.size==(64,64) and image.mode=='RGBA'
        for alpha in range(256):
            for dx in range(4):
                for dy in range(4):
                    assert image.getpixel(((alpha%16)*4+dx,(alpha//16)*4+dy))==(255,255,255,alpha)
    print('PASS: all 256 alpha tiles preserve RGB, including interpolation padding')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify',action='store_true')
    args=parser.parse_args()
    if not args.verify:build()
    verify()
