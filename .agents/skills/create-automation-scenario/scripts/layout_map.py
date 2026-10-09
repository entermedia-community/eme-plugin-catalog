"""Lay out the Automation map: each automationlabel's child scenarios (connectedtop) on a
semicircle below the label. Prints the layout and any overlaps; pass --write to update
html/data/lists/automationposition/scenariopositions.xml. Add new labels to ROWS."""
import re,glob,math,collections,sys,os
B=os.path.join(os.path.dirname(os.path.abspath(__file__)),'../../../../html/data/lists/')
P=B+'automationposition/scenariopositions.xml'
pos={}
s=open(P).read()
for i,x,y in re.findall(r'id="([^"]+)"\s+posx="([^"]+)"\s+posy="([^"]+)"',s): pos.setdefault(i,(float(x),float(y)))
texts={i:t for i,t in re.findall(r'<data id="([^"]+)"[^>]*text="([^"]+)"',open(B+'automationlabel/scenariolabels.xml').read())}
kids=collections.defaultdict(list)
for f in glob.glob(B+'automationscenario/*.xml'):
    for tag in re.findall(r'<data [^>]*>',open(f).read()):
        i=re.search(r'id="([^"]+)"',tag).group(1); c=re.search(r'connectedtop="([^"]*)"',tag)
        if c: kids[c.group(1)].append(i)
ALPHA=math.radians(15)  # arc starts/ends this far above horizontal
BOX=200                 # scenario node size
GAP=290                 # min distance between neighbour centres (>= BOX*sqrt(2))
DROP=100                # extra push down so scenarios sit below the label
def radius(n):
    if n<2: return 320
    step=(math.pi-2*ALPHA)/(n-1)
    return max(320, GAP/(2*math.sin(step/2)))
def lw(l): return 80+9*len(texts[l])
ROWS=[['file_management_tools','communication_tools','content_creation_tools'],['eme_chat','entity_chat','team_chat']]
out={}; rowy=1430.0; LH=60
for row in ROWS:
    hws=[radius(len(kids[l]))*math.cos(ALPHA)+BOX/2 for l in row]
    total=sum(2*h for h in hws)+120*(len(row)-1)
    x=1800-total/2; maxh=0
    for l,hw in zip(row,hws):
        cx=x+hw; cy=rowy+LH/2
        out[l]=(cx-lw(l)/2, rowy)
        n=len(kids[l]); R=radius(n)
        # welcome menu first, then by current x (unpositioned last)
        order=sorted(kids[l],key=lambda i:(not i.startswith('welcome_menu'), pos.get(i,(1e9,0))[0]))
        for k,i in enumerate(order):
            th=math.pi/2 if n==1 else math.pi-ALPHA-k*(math.pi-2*ALPHA)/(n-1)
            out[i]=(cx+R*math.cos(th)-BOX/2, cy+R*math.sin(th)-BOX/2+DROP)
        maxh=max(maxh,R+BOX/2+LH)
        x+=2*hw+120
    rowy+=maxh+200
# overlap check among scenario boxes
ids=[i for i in out if i not in texts]
bad=[(a,b) for n,a in enumerate(ids) for b in ids[n+1:] if abs(out[a][0]-out[b][0])<BOX and abs(out[a][1]-out[b][1])<BOX]
for i,(x,y) in sorted(out.items(),key=lambda t:(t[1][1]//400,t[1][0])): print(f'{i:36s} {x:8.1f} {y:8.1f}')
print('overlaps:',bad)
if '--write' in sys.argv:
    seen=set()
    def rep(m):
        i=m.group(1)
        if i in seen: return ''   # drop duplicate rows
        seen.add(i)
        if i not in out: return m.group(0)
        x,y=out[i]
        return re.sub(r'posx="[^"]*"\s+posy="[^"]*"',f'posx="{x:.1f}" posy="{y:.1f}"',m.group(0))
    s2=re.sub(r'[ \t]*<data id="([^"]+)"[^>]*>\s*<name/>\s*</data>[ \t]*\n?',rep,s)
    for i in out:
        if i not in seen: s2=s2.replace('</root>',f'  <data id="{i}" posx="{out[i][0]:.1f}" posy="{out[i][1]:.1f}">\n    <name/>\n  </data>\n</root>')
    s2=re.sub(r'\n\s*\n(\s*\n)+','\n\n',s2)
    open(P,'w').write(s2)
