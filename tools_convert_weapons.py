import json, math, os, shutil
from PIL import Image

UP='source_models/'
OUT='src/main/resources/assets/sunmod/'
os.makedirs(OUT+'models/item',exist_ok=True); os.makedirs(OUT+'textures/item',exist_ok=True); os.makedirs(OUT+'items',exist_ok=True)

ALLOWED=[-45,-22.5,0,22.5,45]
def snap(a):
    return min(ALLOWED,key=lambda v:abs(v-a))

def r3(v): return round(v+0.0,4)

report={}
def bake_z(f,t,org,k):
    """gira a caixa k*90 graus em Z em torno de org (exato, continua alinhada aos eixos)."""
    k%=4
    def rot(p):
        x,y=p[0]-org[0],p[1]-org[1]
        for _ in range(k): x,y=-y,x
        return [x+org[0],y+org[1],p[2]]
    a,b=rot(f),rot(t)
    inverted=f[0]>t[0] and f[1]>t[1] and f[2]>t[2]
    lo=[min(a[i],b[i]) for i in range(3)]; hi=[max(a[i],b[i]) for i in range(3)]
    return (hi,lo) if inverted else (lo,hi)

FACE_SWAP_Z180={'east':'west','west':'east','up':'down','down':'up','north':'north','south':'south'}

def convert(name):
    d=json.load(open(UP+name+'.bbmodel'))
    RW,RH=d['resolution']['width'],d['resolution']['height']
    lo=[1e9]*3; hi=[-1e9]*3
    prepared=[]; notes=[]
    for i,e in enumerate(d['elements']):
        inf=e.get('inflate',0) or 0
        f=[e['from'][k]-inf for k in range(3)]; t=[e['to'][k]+inf for k in range(3)]
        rot=list(e.get('rotation') or [0,0,0]); org=list(e.get('origin',[0,0,0]))
        is_outline=(e.get('name')=='outline') or (f[0]>t[0])
        rx,ry,rz=rot
        swap=False
        # Z em múltiplos de 90: embutido nas coordenadas (exato). Z180 + X(a) == X(-a) depois do Z180.
        if abs(rz)>45 and abs(rz/90-round(rz/90))<1e-6:
            k=int(round(rz/90))
            f,t=bake_z(f,t,org,k)
            if k%4==2:
                rx=-rx; swap=True
            elif k%2!=0 and abs(rx)>1e-6:
                notes.append(f'#{i}: Z{rz} com X{rx} não é exato')
            rz=0
        nz=[(ax,a) for ax,a in zip('xyz',(rx,ry,rz)) if abs(a)>1e-6]
        rotation=None
        if nz:
            ax,a=nz[0]
            if len(nz)>1:
                # JSON de item só aceita 1 eixo: fica com o Z (diagonal visível), perde o X
                ax,a=[(x,v) for x,v in nz if x=='z'][0]
                notes.append(f'#{i} {e.get("name")}: rotação {[rx,ry,rz]} em 2 eixos -> mantido só Z{a} (X descartado)')
            s_=snap(a)
            if abs(s_-a)>1e-6: notes.append(f'#{i} {e.get("name")}: ângulo {a} ajustado para {s_}')
            if s_!=0: rotation={'angle':s_,'axis':ax,'origin':org}
        prepared.append((e,f,t,rotation,is_outline,swap))
        for k in range(3):
            lo[k]=min(lo[k],f[k],t[k]); hi[k]=max(hi[k],f[k],t[k])
    shift=[8.0,0.0,8.0]
    if lo[1]<0: shift[1]=-lo[1]
    if lo[2]+shift[2]<-15: shift[2]+=(-15-(lo[2]+shift[2]))
    for k in range(3):
        assert lo[k]+shift[k]>=-16 and hi[k]+shift[k]<=32,(name,k,lo[k]+shift[k],hi[k]+shift[k])
    out=[]; sc=16.0/RW; sv=16.0/RH
    for e,f,t,rotation,is_outline,swap in prepared:
        el={'name':e.get('name','cube'),'from':[r3(f[k]+shift[k]) for k in range(3)],'to':[r3(t[k]+shift[k]) for k in range(3)]}
        if rotation:
            el['rotation']={'angle':rotation['angle'],'axis':rotation['axis'],'origin':[r3(rotation['origin'][k]+shift[k]) for k in range(3)]}
        faces={}
        for fn,fc in e['faces'].items():
            if is_outline: uv=[0.1,0.1,0.4,0.4]
            else:
                u1,v1,u2,v2=fc['uv']; uv=[r3(u1*sc),r3(v1*sv),r3(u2*sc),r3(v2*sv)]
            face={'uv':uv,'texture':'#0'}
            if swap and not is_outline: face['rotation']=180
            faces[FACE_SWAP_Z180[fn] if swap else fn]=face
        el['faces']=faces
        out.append(el)
    return out,shift,notes,(lo,hi),(RW,RH)

DISPLAY={
 'gui':{'rotation':[25,225,0],'translation':[0,-1,0],'scale':[0.55,0.55,0.55]},
 'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[0.45,0.45,0.45]},
 'fixed':{'rotation':[0,90,0],'translation':[0,-2,0],'scale':[0.6,0.6,0.6]},
 'head':{'rotation':[0,0,0],'translation':[0,12,0],'scale':[0.6,0.6,0.6]},
 'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,3,2],'scale':[0.55,0.55,0.55]},
 'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,3,2],'scale':[0.55,0.55,0.55]},
 'firstperson_righthand':{'rotation':[0,0,0],'translation':[2,2,0],'scale':[0.6,0.6,0.6]},
 'firstperson_lefthand':{'rotation':[0,0,0],'translation':[-2,2,0],'scale':[0.6,0.6,0.6]},
}

for item,name in (('gun_ice','gun_ice_cube'),('lazer_ice','lazer_ice_cube'),('gravity_gun','gravity_gun_ice_cube')):
    els,shift,notes,ext,res=convert(name)
    tex=f'sunmod:item/{item}'
    model={'texture_size':list(res),'textures':{'0':tex,'particle':tex},'elements':els,'display':DISPLAY}
    json.dump(model,open(OUT+f'models/item/{item}.json','w'),indent=1)
    shutil.copy(UP+name+'.png',OUT+f'textures/item/{item}.png')
    json.dump({'model':{'type':'minecraft:model','model':f'sunmod:item/{item}'}},open(OUT+f'items/{item}.json','w'),indent=1)
    print(item,'elementos',len(els),'shift',shift,'extent',[round(x,2) for x in ext[0]],[round(x,2) for x in ext[1]])
    for n in notes: print('   ',n)
