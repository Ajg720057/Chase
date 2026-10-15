import sys, math, random
import numpy as np
from PIL import Image, ImageFilter

W, H = 1080, 1920
out = sys.argv[1]

def lerp(a, b, t): return a + (b - a) * t
def hexc(h): h=h.lstrip('#'); return np.array([int(h[i:i+2],16) for i in (0,2,4)], float)

def gradient(stops):
    """Vertical gradient. stops: list of (y_frac, '#hex')."""
    ys = np.linspace(0, 1, H)
    img = np.zeros((H, W, 3))
    for i in range(len(stops)-1):
        y0,c0 = stops[i]; y1,c1 = stops[i+1]
        m = (ys>=y0)&(ys<=y1)
        t = ((ys[m]-y0)/(y1-y0))[:,None]
        t = t*t*(3-2*t)
        img[m] = (hexc(c0)*(1-t)+hexc(c1)*t)[:,None,:]
    return img

def ridge(base, rough, seed, octaves=9, smooth=0.55):
    rng = random.Random(seed)
    n = 2**octaves+1
    pts = [0.0]*n
    pts[0]=rng.uniform(-1,1); pts[-1]=rng.uniform(-1,1)
    step=n-1; amp=1.0
    while step>1:
        half=step//2
        for i in range(half, n-1, step):
            pts[i]=(pts[i-half]+pts[i+half])/2+rng.uniform(-amp,amp)
        step=half; amp*=smooth
    xs=np.linspace(0,n-1,W)
    y=np.interp(xs, np.arange(n), pts)
    return (base + y*rough)*H

def fill_below(img, ys, color, top_color=None, fade=0.0):
    yy=np.arange(H)[:,None]
    mask=yy>=ys[None,:]
    col=hexc(color)
    if top_color is not None:
        # lighter at the ridge, darker lower down
        d=np.clip((yy-ys[None,:])/(H*0.25),0,1)[:,:,None]
        layer=hexc(top_color)*(1-d)+col*d
    else:
        layer=np.broadcast_to(col,(H,W,3))
    # antialias edge
    edge=np.clip(yy-ys[None,:]+0.5,0,1)[:,:,None]
    img[:] = img*(1-edge)+layer*edge
    return img

def glow(img, cx, cy, r, color, strength):
    yy,xx=np.mgrid[0:H,0:W]
    d=np.sqrt((xx-cx)**2+(yy-cy)**2)
    g=np.exp(-(d/r)**2)[:,:,None]*strength
    img[:] = img+(hexc(color)-img)*np.clip(g,0,1)

def disc(img, cx, cy, r, color):
    yy,xx=np.mgrid[0:H,0:W]
    d=np.sqrt((xx-cx)**2+(yy-cy)**2)
    a=np.clip(r-d+0.5,0,1)[:,:,None]
    img[:] = img*(1-a)+hexc(color)*a

def stars(img, n, seed, ymax):
    rng=np.random.default_rng(seed)
    for _ in range(n):
        x=rng.integers(0,W); y=int(rng.random()**1.6*ymax*H)
        b=rng.random()
        s=1 if b<0.85 else 2
        img[y:y+s,x:x+s]=np.maximum(img[y:y+s,x:x+s], 150+105*b)

def mist(img, y, h, color, a):
    ys=np.arange(H)
    t=np.exp(-((ys-y*H)/(h*H))**2)[:,None,None]*a
    img[:] = img+(hexc(color)-img)*t

def reflect(img, y_water, tint, a):
    yw=int(y_water*H)
    above=img[max(0,2*yw-H):yw][::-1].copy()
    n=min(H-yw, above.shape[0])
    ref=above[:n]
    # ripple
    rng=np.random.default_rng(3)
    for i in range(n):
        shift=int(math.sin(i*0.35)*3*(i/n+0.2))
        ref[i]=np.roll(ref[i],shift,axis=0)
    ref=ref*(1-a)+hexc(tint)*a
    img[yw:yw+n]=ref
    # horizontal glints
    for _ in range(260):
        x=rng.integers(0,W-80); y=yw+int(rng.random()**1.5*n)
        L=rng.integers(10,70)
        img[y:y+2,x:x+L]=img[y:y+2,x:x+L]*0.75+255*0.25*(1-(y-yw)/n)

def trees(img, base, seed, color, density, hmin, hmax):
    rng=random.Random(seed)
    yy,xx=np.mgrid[0:H,0:W]
    x=-20
    while x<W+20:
        h=rng.uniform(hmin,hmax)*H; w=h*rng.uniform(0.22,0.3)
        by=base[min(max(int(x),0),W-1)]+rng.uniform(0,10)
        # triangle tree
        top=by-h
        x0,x1=int(max(0,x-w)),int(min(W,x+w))
        y0,y1=int(max(0,top)),int(min(H,by))
        if x1>x0 and y1>y0:
            sub_y,sub_x=yy[y0:y1,x0:x1],xx[y0:y1,x0:x1]
            half=(sub_y-top)/h*w
            m=np.clip(half-np.abs(sub_x-x)+0.5,0,1)[:,:,None]
            img[y0:y1,x0:x1]=img[y0:y1,x0:x1]*(1-m)+hexc(color)*m
        x+=rng.uniform(4,18)/density

def finish(img, path):
    rng=np.random.default_rng(7)
    img=img+rng.normal(0,2.2,img.shape)
    im=Image.fromarray(np.clip(img,0,255).astype(np.uint8))
    im.save(path,'WEBP',quality=82, method=6)

scenes={}
def scene(f): scenes[f.__name__]=f; return f

@scene
def alpine_dawn():
    img=gradient([(0,'#2b3a67'),(0.35,'#7d6b9e'),(0.55,'#f2a07b'),(0.62,'#ffd6a5'),(1,'#ffd6a5')])
    glow(img,W*0.62,H*0.56,420,'#fff1d0',0.8)
    disc(img,W*0.62,H*0.57,70,'#fff6e0')
    for i,(b,r,c,tc) in enumerate([(0.52,0.12,'#8a7fa8','#b49cbf'),(0.6,0.1,'#5f5a85','#857aa3'),(0.68,0.08,'#3d3d63','#56547d'),(0.77,0.06,'#252645','#33345a')]):
        fill_below(img, ridge(b,r,10+i), c, tc)
        mist(img,b+0.05,0.03,'#f6c7b0',0.25*(1-i/4))
    trees(img, ridge(0.86,0.02,99), 99, '#141530', 1.0, 0.05, 0.11)
    fill_below(img, np.full(W, H*0.93), '#141530')
    return img

@scene
def ocean_sunset():
    img=gradient([(0,'#1d2b53'),(0.25,'#7e2553'),(0.45,'#ff6b5a'),(0.58,'#ffb26b'),(0.6,'#ffcf8b'),(1,'#ffcf8b')])
    glow(img,W*0.5,H*0.58,380,'#ffe7b0',0.85)
    disc(img,W*0.5,H*0.585,110,'#fff0c8')
    reflect(img,0.6,'#3a2a5a',0.3)
    return img

@scene
def misty_forest():
    img=gradient([(0,'#bcd3c9'),(0.5,'#e4ece4'),(1,'#e4ece4')])
    cols=['#9fb8ab','#7f9d8e','#5f8070','#3f6152','#244436']
    for i,c in enumerate(cols):
        base=ridge(0.42+i*0.11,0.025,30+i)
        trees(img, base, 40+i, c, 0.7+i*0.15, 0.07+i*0.025, 0.14+i*0.04)
        fill_below(img, base+H*0.02, c)
        mist(img,0.5+i*0.11,0.05,'#e4ece4',0.35)
    return img

@scene
def starry_lake():
    img=gradient([(0,'#05070f'),(0.35,'#0f1b3d'),(0.55,'#23406b'),(0.6,'#2f5580'),(1,'#2f5580')])
    stars(img,1400,5,0.55)
    # milky way band
    yy,xx=np.mgrid[0:H,0:W]
    band=np.exp(-(((yy-H*0.05)-(xx*0.55))/140.0)**2)*np.clip(1-yy/(H*0.55),0,1)
    img+=band[:,:,None]*np.array([40,35,60])
    stars(img,500,6,0.4)
    disc(img,W*0.78,H*0.14,46,'#f4f1e6'); glow(img,W*0.78,H*0.14,180,'#9fb3d9',0.35)
    fill_below(img, ridge(0.5,0.08,77), '#0b1428','#1a2b4a')
    trees(img, ridge(0.585,0.01,8), 8, '#060a14', 1.2, 0.03, 0.07)
    fill_below(img, np.full(W,H*0.6), '#060a14')
    reflect(img,0.6,'#0a1830',0.45)
    return img

@scene
def desert_dunes():
    img=gradient([(0,'#5b8bb5'),(0.4,'#a9c8de'),(0.55,'#f4dcc0'),(1,'#f4dcc0')])
    disc(img,W*0.25,H*0.2,60,'#fffaf0'); glow(img,W*0.25,H*0.2,260,'#fff6e0',0.5)
    cols=[('#e8b98a','#f3cfa4'),('#d99a68','#ecb98a'),('#c47d4f','#dc9a6a'),('#a5603a','#c47d4f')]
    for i,(c,tc) in enumerate(cols):
        fill_below(img, ridge(0.55+i*0.1,0.06,60+i,octaves=7,smooth=0.35), c, tc)
    return img

@scene
def aurora():
    img=gradient([(0,'#030812'),(0.6,'#0c2236'),(1,'#0c2236')])
    stars(img,900,11,0.6)
    yy,xx=np.mgrid[0:H,0:W]
    for k,(c,off,amp) in enumerate([('#3dffb0',0.25,90),('#5fd6ff',0.18,60),('#b07dff',0.12,50)]):
        cy=H*off+np.sin(xx/170.0+k)*amp+np.sin(xx/63.0+k*2)*25
        d=(yy-cy)
        # rays rise above the bright lower edge and fade out softly
        curtain=np.where(d<0, np.exp(d/(220+k*60)), np.exp(-(d/30)**2))
        curtain*=0.75+0.25*np.sin(xx/11.0+k)**2
        img+= (curtain[:,:,None]*hexc(c)*0.55)
    fill_below(img, ridge(0.6,0.07,90), '#0a1320','#16293d')
    fill_below(img, ridge(0.7,0.04,91), '#060c16')
    trees(img, ridge(0.74,0.01,92), 92, '#03060c', 1.4, 0.04, 0.09)
    fill_below(img, np.full(W,H*0.76), '#03060c')
    return np.clip(img,0,255)

@scene
def rolling_hills():
    img=gradient([(0,'#7fb3e0'),(0.45,'#cfe6f2'),(0.55,'#fdf2d6'),(1,'#fdf2d6')])
    glow(img,W*0.7,H*0.45,300,'#fff8e8',0.6)
    cols=[('#a9cfa0','#c6e0b6'),('#86b97e','#a5cf96'),('#5f9a5e','#7fb57a'),('#3f7a45','#5a9a5c'),('#2a5c33','#3f7a45')]
    for i,(c,tc) in enumerate(cols):
        fill_below(img, ridge(0.5+i*0.09,0.05,120+i,octaves=6,smooth=0.3), c, tc)
        mist(img,0.53+i*0.09,0.02,'#fdf2d6',0.25)
    return img

@scene
def coastal_cliffs():
    img=gradient([(0,'#e9a6a6'),(0.3,'#f6c9a8'),(0.5,'#fbe3c4'),(0.52,'#7fa6c2'),(1,'#2c4f6e')])
    glow(img,W*0.35,H*0.48,260,'#fff3dc',0.7)
    disc(img,W*0.35,H*0.49,55,'#fff7e6')
    rng=np.random.default_rng(1)
    for _ in range(300):
        x=rng.integers(0,W-60); y=int(H*0.52+rng.random()**1.4*H*0.48)
        img[y:y+2,x:x+rng.integers(15,60)]+=35
    # cliff on right
    yy,xx=np.mgrid[0:H,0:W]
    edge=W*0.55+ridge(0,0.03,200)[np.clip(yy[:,0],0,W-1)][:,None]*0+np.sin(yy/60.0)*12
    top=ridge(0.42,0.04,201)
    m=(xx>edge-(yy-H*0.42)*0.15)&(yy>top[None,:])
    img[m]=hexc('#3a3348')*0.9
    m2=(xx>edge+40-(yy-H*0.42)*0.1)&(yy>top[None,:]+60)
    img[m2]=hexc('#262233')
    return img

for name,f in scenes.items():
    if len(sys.argv)>2 and name not in sys.argv[2:]: continue
    finish(np.clip(f(),0,255), f"{out}/{name}.webp")
    print(name)
