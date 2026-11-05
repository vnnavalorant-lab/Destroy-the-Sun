import json, math, struct, sys
import numpy as np
from PIL import Image

P='/home/claude/sunmod'
UP='/mnt/user-data/uploads/'

def rotmat(r):
    rx,ry,rz=[math.radians(a) for a in r]
    Rx=np.array([[1,0,0],[0,math.cos(rx),-math.sin(rx)],[0,math.sin(rx),math.cos(rx)]])
    Ry=np.array([[math.cos(ry),0,math.sin(ry)],[0,1,0],[-math.sin(ry),0,math.cos(ry)]])
    Rz=np.array([[math.cos(rz),-math.sin(rz),0],[math.sin(rz),math.cos(rz),0],[0,0,1]])
    return Rz@Ry@Rx           # ordem ZYX do Blockbench (as rotações do modelo são de eixo único)

def corners(x0,y0,z0,x1,y1,z1):
    return {
     'down':[(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)],
     'up':[(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)],
     'north':[(x1,y1,z0),(x1,y0,z0),(x0,y0,z0),(x0,y1,z0)],
     'south':[(x0,y1,z1),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1)],
     'west':[(x0,y1,z0),(x0,y0,z0),(x0,y0,z1),(x0,y1,z1)],
     'east':[(x1,y1,z1),(x1,y0,z1),(x1,y0,z0),(x1,y1,z0)]}

def build(name, bb, png):
    d=json.load(open(UP+bb)); tex=Image.open(UP+png).convert('RGBA'); TW,TH=tex.size
    quads=[]; boxes=[]
    for e in d['elements']:
        inf=e.get('inflate',0) or 0
        f=[e['from'][i]-inf for i in range(3)]; t=[e['to'][i]+inf for i in range(3)]
        o=np.array(e.get('origin',[0,0,0]),float); R=rotmat(e.get('rotation') or [0,0,0])
        cs=corners(*f,*t)
        for fname,fc in e['faces'].items():
            pts=[R@(np.array(p,float)-o)+o for p in cs[fname]]
            nn=np.cross(pts[1]-pts[0],pts[2]-pts[0])+np.cross(pts[2]-pts[0],pts[3]-pts[0])
            if np.linalg.norm(nn)/2<1e-4: continue
            nn/=np.linalg.norm(nn)
            u1,v1,u2,v2=fc['uv']
            uvs=[(u1,v1),(u1,v2),(u2,v2),(u2,v1)]
            uvs=[(min(max(u,0.5),TW-0.5),min(max(v,0.5),TH-0.5)) for u,v in uvs]
            quads.append((pts,uvs,nn))
        # hitbox detalhada: 1 OBB por cubo (já com inflate/contorno)
        lo=np.minimum(f,t); hi=np.maximum(f,t)
        half=np.maximum((hi-lo)/2,0.25); c0=(lo+hi)/2
        boxes.append((R@(c0-o)+o, half, R.T))   # eixos = linhas de R.T  (axis_i = R @ e_i)
    allp=np.array([p for q in quads for p in q[0]])
    mn,mx=allp.min(0),allp.max(0)
    shift=np.array([-(mn[0]+mx[0])/2, -mn[1], -(mn[2]+mx[2])/2])      # centraliza em X/Z, apoia em y=0
    out=bytearray(b'SUN1')+struct.pack('>ii',1,len(quads))
    for pts,uvs,n in quads:
        for p,(u,v) in zip(pts,uvs):
            q=(p+shift)/16
            out+=struct.pack('>8f',q[0],q[1],q[2],u/TW,v/TH,*n)
    open(f'{P}/src/client/resources/assets/sunmod/models/{name}.mesh','wb').write(out)
    hb=bytearray(struct.pack('>i',len(boxes)))
    for c,h,ax in boxes:
        c=(c+shift)/16; h=h/16
        hb+=struct.pack('>3f',*c)+struct.pack('>3f',*h)+struct.pack('>9f',*ax.reshape(-1))
    import os; os.makedirs(f'{P}/src/main/resources/data/sunmod/hitboxes',exist_ok=True)
    open(f'{P}/src/main/resources/data/sunmod/hitboxes/{name}.bin','wb').write(hb)
    os.makedirs(f'{P}/src/main/resources/assets/sunmod/textures/entity',exist_ok=True)
    tex.save(f'{P}/src/main/resources/assets/sunmod/textures/entity/{name}.png')
    size=(mx-mn)/16
    print(name,'quads',len(quads),'boxes',len(boxes),'size (blocks) x,y,z =',np.round(size,3))
    return quads,shift,tex,size

def render(quads,shift,tex,yaw,pitch,size=480,scale=8.5,bg=(70,70,78,255),cy=1.4):
    TW,TH=tex.size; T=np.array(tex); ya,pa=math.radians(yaw),math.radians(pitch)
    def tf(p):
        x,y,z=p[0]+shift[0],p[1]+shift[1]-22,p[2]+shift[2]
        x,z=x*math.cos(ya)+z*math.sin(ya),-x*math.sin(ya)+z*math.cos(ya)
        y,z=y*math.cos(pa)-z*math.sin(pa),y*math.sin(pa)+z*math.cos(pa)
        return np.array([x,y,z])
    img=np.zeros((size,size,4),np.uint8); img[:,:]=bg; zb=np.full((size,size),1e9)
    for pts,uvs,n in quads:
        P_=[tf(p-shift) for p in pts]; N=tf(n+np.array([0,22,0])-shift)-tf(np.array([0,22,0])-shift)
        if N[2]>=0: continue
        shade=0.6+0.3*max(0,-N[2])*0.6+0.25*max(0,N[1])
        for tri in ((0,1,2),(0,2,3)):
            a,b,c=[P_[i] for i in tri]; ua,ub,uc=[uvs[i] for i in tri]
            sx=lambda p:(size/2-p[0]*scale, size/2-p[1]*scale)
            (ax,ay),(bx,by),(cx,cy_)=sx(a),sx(b),sx(c)
            den=(by-cy_)*(ax-cx)+(cx-bx)*(ay-cy_)
            if abs(den)<1e-9: continue
            for y in range(int(max(0,min(ay,by,cy_))),int(min(size-1,max(ay,by,cy_)+1))+1):
                for x in range(int(max(0,min(ax,bx,cx))),int(min(size-1,max(ax,bx,cx)+1))+1):
                    px,py=x+.5,y+.5
                    w0=((by-cy_)*(px-cx)+(cx-bx)*(py-cy_))/den; w1=((cy_-ay)*(px-cx)+(ax-cx)*(py-cy_))/den; w2=1-w0-w1
                    if min(w0,w1,w2)<-1e-6: continue
                    z=w0*a[2]+w1*b[2]+w2*c[2]
                    if z>=zb[y,x]: continue
                    u=w0*ua[0]+w1*ub[0]+w2*uc[0]; v=w0*ua[1]+w1*ub[1]+w2*uc[1]
                    col=T[min(TH-1,max(0,int(v))),min(TW-1,max(0,int(u)))]
                    if col[3]==0: continue
                    zb[y,x]=z; img[y,x]=(int(col[0]*shade),int(col[1]*shade),int(col[2]*shade),255)
    return Image.fromarray(img)

if __name__=='__main__':
    for name,bb,png in (('machine_ice','machine_ice_1.bbmodel','machine_ice_1.png'),('upgrader','upgrader.bbmodel','upgrader.png')):
        q,sh,tex,size=build(name,bb,png)
        render(q,sh,tex,0,0).save(f'/tmp/mach/{name}_front.png')
        render(q,sh,tex,40,-22).save(f'/tmp/mach/{name}_34.png')
