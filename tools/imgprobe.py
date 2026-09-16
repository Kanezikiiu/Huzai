import glob
import sys

def kind_of(b):
    if b[:3] == b'GIF':
        return 'GIF'
    if b[:4] == b'RIFF' and b[8:12] == b'WEBP':
        return 'WEBP'
    if b[:2] == b'\xff\xd8':
        return 'JPEG'
    if b[:4] == b'\x89PNG':
        return 'PNG'
    return '?'

for p in sorted(glob.glob(sys.argv[1] if len(sys.argv) > 1 else 't_*.bin')):
    b = open(p, 'rb').read()
    k = kind_of(b)
    anim = (b'ANIM' in b[:4096]) if k == 'WEBP' else None
    frames = b.count(b'\x21\xf9\x04') if k == 'GIF' else None
    print(f'{p:14s} size={len(b):>8} {k:5s} webp_ANIM={anim} gif_frames={frames}')
