"""Generate anatomical ten-tier overlays from existing full-coverage PNGs.

This performs the previously authorized UV processing, without AI generation or
editing source textures. Run --bake once; --verify checks pixels and provenance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
TEX=ROOT/'src/main/resources/assets/mfqm/textures/entity/mudoverlays'
MANIFEST=ROOT/'docs/superpowers/2026-10-08-coating-height/texture-manifest.json'
FAMILIES=('mudoverlay','slimeoverlay','taroverlay','honeyoverlay','glueoverlay')
GLUE_ALPHA_SCALE=.55


def height_mask(width,height):
    if width<64 or width%64 or height*2!=width:
        raise ValueError('Expected a uniformly scaled legacy 64x32 UV atlas')
    scale=width//64
    h=np.full((height,width),np.inf)
    def flat(x,y,w,d,value):h[y*scale:(y+d)*scale,x*scale:(x+w)*scale]=value
    def side(x,y,w,d,top):
        rows=np.arange(d*scale)/scale
        h[y*scale:(y+d)*scale,x*scale:(x+w)*scale]=(top-rows)[:,None]
    # Heights above the soles in the standing 32-pixel player rig. A side pixel
    # is included only when its entire vertical span has reached the medium.
    side(0,8,32,8,32)
    flat(8,0,8,8,32);flat(16,0,8,8,24)
    side(0,20,16,12,12)
    flat(4,16,4,4,12);flat(8,16,4,4,0)
    side(16,20,24,12,24)
    flat(20,16,8,4,24);flat(28,16,8,4,12)
    side(40,20,16,12,24)
    flat(44,16,4,4,24);flat(48,16,4,4,12)
    # Wide/slim atlases overlap. Conservatively keep their shared shoulder UV
    # dry until shoulder height; one column of a slim palm's underside remains
    # dry until then as well, rather than painting a wide arm's dry shoulder.
    return h


def crop_overlay(source,level,family):
    if level not in range(1,11) or family not in FAMILIES:
        raise ValueError('Invalid overlay family or coverage tier')
    if source.ndim!=3 or source.shape[2]!=4:raise ValueError('Expected RGBA')
    out=source.copy()
    heights=height_mask(source.shape[1],source.shape[0])
    alpha=source[:,:,3].astype(float)
    if family=='glueoverlay':alpha=np.rint(alpha*GLUE_ALPHA_SCALE)
    out[:,:,3]=np.where(heights<=32*level/10+1e-9,alpha,0).astype('uint8')
    return out


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def rgba(path):return np.array(Image.open(path).convert('RGBA'))


def bake():
    sources={};outputs={}
    for family in FAMILIES:
        source_path=TEX/f'{family}9.png'
        sources[source_path.name]=sha(source_path)
        source=rgba(source_path)
        for level in range(1,11):
            name=f'{family}_height{level-1}.png';path=TEX/name
            Image.fromarray(crop_overlay(source,level,family)).save(path,optimize=True)
            outputs[name]={'sha256':sha(path),'size':list(Image.open(path).size),'level':level,'source':source_path.name}
    MANIFEST.parent.mkdir(parents=True,exist_ok=True)
    MANIFEST.write_text(json.dumps({'glue_alpha_scale':GLUE_ALPHA_SCALE,'sources':sources,'outputs':outputs},indent=2)+'\n',encoding='utf-8')


def verify():
    manifest=json.loads(MANIFEST.read_text(encoding='utf-8'))
    expected={f'{f}_height{i}.png' for f in FAMILIES for i in range(10)}
    assert set(manifest['outputs'])==expected,'Missing or unexpected generated overlay'
    assert manifest['glue_alpha_scale']==GLUE_ALPHA_SCALE
    for name,digest in manifest['sources'].items():assert sha(TEX/name)==digest,f'Source changed: {name}'
    for family in FAMILIES:
        source=rgba(TEX/f'{family}9.png');heights=height_mask(source.shape[1],source.shape[0])
        previous=np.zeros(source.shape[:2],dtype=bool)
        for level in range(1,11):
            name=f'{family}_height{level-1}.png';actual=rgba(TEX/name)
            assert sha(TEX/name)==manifest['outputs'][name]['sha256'],name
            assert np.array_equal(actual,crop_overlay(source,level,family)),name
            painted=actual[:,:,3]>0
            assert np.all(heights[painted]<=32*level/10+1e-9),f'Paint above tier: {name}'
            assert np.all(painted[previous]) and painted.sum()>previous.sum(),f'Progression: {name}'
            if level<=3:
                assert not painted[:source.shape[0]//2].any(),f'Head painted from leg contact: {name}'
                assert not painted[source.shape[0]//2:,source.shape[1]//4:].any(),f'Torso/arms painted from leg contact: {name}'
            previous=painted
    print('PASS: 50 anatomical overlays; height caps/UV top-bottom faces/monotonic tiers; leg-only tiers 1-3; unchanged sources; glue alpha 55%')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bake',action='store_true');parser.add_argument('--verify',action='store_true')
    args=parser.parse_args()
    if not (args.bake or args.verify):parser.error('Choose --bake and/or --verify')
    if args.bake:bake()
    if args.verify:verify()
