# TRUBBI 리브랜딩(나이트 드라이브 블루) 벡터 자산 생성.
# 사용: python3 -m pip install fonttools && python3 tools/brand/make_brand.py <Sora[wght].ttf> app/src/main/res
# 글꼴: Sora (SIL OFL 1.1, https://github.com/google/fonts/tree/main/ofl/sora). 글자 윤곽만 벡터로 옮기고 글꼴 파일은 앱에 넣지 않는다.
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

font_path, res = sys.argv[1], sys.argv[2]

BLUE = "#FF2457F5"
AMBER = "#FFF5A524"
WHITE = "#FFFFFFFF"

# ── 마크(100 단위 좌표) ─────────────────────────────────────────────────────
# 원근으로 뻗은 도로(가운데 점선은 구멍)와 지평선 위의 위치 핀(가운데 구멍).
def dash(y0, y1, w=4.0):
    r = w / 2
    return f"M{50 - r},{y0} a{r},{r} 0 0,1 {w},0 v{y1 - y0} a{r},{r} 0 0,1 {-w},0 z"

ROAD = "M42,44 L58,44 L80,88 L20,88 Z " + " ".join([dash(50, 54), dash(60, 67), dash(74, 84)])
PIN = ("M50,10 C42.5,10 37,15.5 37,22.5 C37,31.5 50,42 50,42 C50,42 63,31.5 63,22.5 C63,15.5 57.5,10 50,10 Z "
       "M45,22.5 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0 z")


def vector(w, h, vw, vh, body):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- 2026-10 리브랜딩. tools/brand/make_brand.py 로 만들었다: 도로 위 지평선의 위치 핀. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{w}dp"\n    android:height="{h}dp"\n'
            f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n{body}</vector>\n')


def path(d, color, indent="    "):
    return (f'{indent}<path\n{indent}    android:fillColor="{color}"\n'
            f'{indent}    android:fillType="evenOdd"\n{indent}    android:pathData="{d}" />\n')


def mark_paths(road=WHITE, pin=AMBER, indent="    "):
    return path(ROAD, road, indent) + path(PIN, pin, indent)


def group(body, s, tx, ty):
    return (f'    <group\n        android:scaleX="{s}"\n        android:scaleY="{s}"\n'
            f'        android:translateX="{tx}"\n        android:translateY="{ty}">\n{body}    </group>\n')


def write(name, text):
    with open(f"{res}/drawable/{name}.xml", "w") as f:
        f.write(text)
    print("wrote", name)


# 적응형 아이콘 앞면(108dp, 가운데 66dp 안전 영역). 마크 세로 10~88 → 54dp 높이로 가운데.
s = round(54 / 78, 4)
tx = round(54 - 50 * s, 3)
ty = round(54 - 49 * s, 3)
write("ic_launcher_foreground", vector(108, 108, 108, 108, group(mark_paths(indent="        "), s, tx, ty)))
write("ic_launcher_monochrome", vector(108, 108, 108, 108,
                                     group(path(ROAD, "#FF000000", "        ") + path(PIN, "#FF000000", "        "), s, tx, ty)))
write("ic_launcher_background", vector(108, 108, 108, 108, f'    <path\n        android:fillColor="{BLUE}"\n        android:pathData="M0,0h108v108h-108z" />\n'))

# 둥근 사각 배지(앱 안 로고·알림 큰 아이콘·플로팅 버튼).
badge = f'    <path\n        android:fillColor="{BLUE}"\n        android:pathData="M24,0 H76 A24,24 0 0,1 100,24 V76 A24,24 0 0,1 76,100 H24 A24,24 0 0,1 0,76 V24 A24,24 0 0,1 24,0 Z" />\n'
write("brand_mark", vector(100, 100, 100, 100, badge + mark_paths()))

# 상태 표시줄 알림 아이콘(단색, 24dp): 마크만.
write("ic_stat_logo", vector(24, 24, 100, 100, path(ROAD, WHITE) + path(PIN, WHITE)))

# ── 워드마크: Sora ExtraBold "TRUBBI" ──────────────────────────────────────────
ft = TTFont(font_path)
ft = instantiateVariableFont(ft, {"wght": 800})
gs = ft.getGlyphSet()
cmap = ft.getBestCmap()
hmtx = ft["hmtx"]
TRACK = 40  # 글자 사이를 조금 띄워 작은 크기에서도 B·B 가 붙지 않게
pen = SVGPathPen(gs)
x = 0
bounds = BoundsPen(gs)
for ch in "TRUBBI":
    g = cmap[ord(ch)]
    gs[g].draw(TransformPen(pen, (1, 0, 0, -1, x, 0)))
    gs[g].draw(TransformPen(bounds, (1, 0, 0, -1, x, 0)))
    x += hmtx[g][0] + TRACK
xmin, ymin, xmax, ymax = bounds.bounds
d = pen.getCommands()
w, h = xmax - xmin, ymax - ymin
body = (f'    <group\n        android:translateX="{-xmin}"\n        android:translateY="{-ymin}">\n'
        f'        <path\n            android:fillColor="{WHITE}"\n            android:pathData="{d}" />\n    </group>\n')
dp_h = 24
write("brand_wordmark", vector(round(w / h * dp_h, 1), dp_h, round(w, 1), round(h, 1), body))
