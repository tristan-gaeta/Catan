#!/usr/bin/env python3
"""Draws the SVG diagrams in docs/images from geometry.json.

geometry.json is dumped from the real Kotlin enums (see docs/diagrams/README.md), so every vertex and edge number in
the pictures is the ordinal the code uses. Run from anywhere:

    python3 docs/diagrams/generate.py
"""
import json
import math
import re
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "images"
DATA = json.loads((HERE / "geometry.json").read_text(encoding="utf-8"))
# the current wire format version, read from the source so the bit-layout diagram never goes stale
BITS_KT = HERE.parents[1] / "core/src/commonMain/kotlin/com/tgaetaprojects/settledown/game/codec/Bits.kt"
FORMAT_VERSION = re.search(r"const val FORMAT_VERSION = (\d+)", BITS_KT.read_text(encoding="utf-8")).group(1)

SQRT3 = math.sqrt(3)
DIRECTIONS = ["NE", "NW", "W", "SW", "SE", "E"]
DIR_VECTORS = {"NE": (1, -1), "NW": (0, -1), "W": (-1, 0), "SW": (-1, 1), "SE": (0, 1), "E": (1, 0)}
# Corner d sits 30 degrees counter-clockwise of edge direction d (see Vertex.fromCoordinates).
CORNER_ANGLE = {"E": 30, "NE": 90, "NW": 150, "W": 210, "SW": 270, "SE": 330}

INK = "#1f2933"
MUTED = "#616e7c"
LINE = "#9aa5b1"
PAPER = "#ffffff"
LAND = "#f5f1e6"
SEA = "#dcecf7"
ACCENT = "#c2410c"
BLUE = "#1d4ed8"
TERRAIN_FILL = {
    "FOREST": "#5f9e6e", "HILL": "#d08c60", "MOUNTAIN": "#a3a7b3",
    "FIELDS": "#ecc85c", "PASTURE": "#b5d98a", "DESERT": "#ead9b0",
}
SIDE_COLORS = ["#2563eb", "#16a34a", "#d97706", "#9333ea", "#dc2626", "#0891b2"]
FONT = "font-family='Helvetica, Arial, sans-serif'"

HEXES = {h["name"]: h for h in DATA["hexes"]}


def center(h, size, ox, oy):
    return ox + size * SQRT3 * (h["q"] + h["r"] / 2), oy + size * 1.5 * h["r"]


def corner(cx, cy, size, direction):
    a = math.radians(CORNER_ANGLE[direction])
    return cx + size * math.cos(a), cy - size * math.sin(a)


def polygon(cx, cy, size):
    return " ".join(f"{x:.1f},{y:.1f}" for x, y in (corner(cx, cy, size, d) for d in DIRECTIONS))


def prev_dir(d):
    return DIRECTIONS[(DIRECTIONS.index(d) + 5) % 6]


def vertex_positions(size, ox, oy):
    pos = {}
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        for d, v in h["v"].items():
            pos[v] = corner(cx, cy, size, d)
    return pos


def edge_positions(size, ox, oy):
    """Edge ordinal -> (midpoint, endpoint a, endpoint b, outward normal for coastal edges)."""
    pos = {}
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        for d, e in h["e"].items():
            a = corner(cx, cy, size, d)
            b = corner(cx, cy, size, prev_dir(d))
            mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            nx, ny = mx - cx, my - cy
            n = math.hypot(nx, ny)
            pos.setdefault(e, []).append(((mx, my), a, b, (nx / n, ny / n)))
    return pos


def svg(width, height, body, title):
    return (f"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 {width} {height}' width='{width}' "
            f"height='{height}' {FONT}>\n<title>{title}</title>\n"
            f"<rect width='100%' height='100%' fill='{PAPER}'/>\n{body}\n</svg>\n")


def text(x, y, s, size=12, fill=INK, weight="normal", anchor="middle", extra=""):
    return (f"<text x='{x:.1f}' y='{y:.1f}' font-size='{size}' fill='{fill}' font-weight='{weight}' "
            f"text-anchor='{anchor}' dominant-baseline='central' {extra}>{escape(str(s))}</text>")


def arrow_defs(color=INK, name="arrow"):
    return (f"<defs><marker id='{name}' viewBox='0 0 10 10' refX='9' refY='5' markerWidth='7' markerHeight='7' "
            f"orient='auto-start-reverse'><path d='M0,0 L10,5 L0,10 z' fill='{color}'/></marker></defs>")


def board(size, ox, oy, fill=lambda h: LAND, stroke=LINE):
    parts = []
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        parts.append(f"<polygon points='{polygon(cx, cy, size)}' fill='{fill(h)}' stroke='{stroke}' "
                     f"stroke-width='1.2'/>")
    return parts


def write(name, content):
    (OUT / name).write_text(content, encoding="utf-8")
    print("wrote", OUT / name)


# ---- 1. Axial coordinates ----

def hex_coordinates():
    size, ox, oy = 52, 330, 300
    p = board(size, ox, oy)
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        p.append(text(cx, cy - 9, h["name"], 20, weight="bold"))
        p.append(text(cx, cy + 13, f"({h['q']}, {h['r']})", 11, MUTED))
    # compass of Direction vectors
    cx0, cy0 = 760, 130
    p.append(text(cx0, 30, "Direction", 15, weight="bold"))
    p.append(arrow_defs(INK, "dir"))
    for d in DIRECTIONS:
        dq, dr = DIR_VECTORS[d]
        dx, dy = SQRT3 * (dq + dr / 2), 1.5 * dr
        n = math.hypot(dx, dy)
        x2, y2 = cx0 + 55 * dx / n, cy0 + 55 * dy / n
        p.append(f"<line x1='{cx0}' y1='{cy0}' x2='{x2:.1f}' y2='{y2:.1f}' stroke='{INK}' stroke-width='1.6' "
                 f"marker-end='url(#dir)'/>")
        lx, ly = cx0 + 82 * dx / n, cy0 + 82 * dy / n
        p.append(text(lx, ly - 7, d, 13, weight="bold"))
        p.append(text(lx, ly + 8, f"({dq}, {dr})", 10, MUTED))
    # the two stored axes
    p.append(arrow_defs(ACCENT, "axis"))
    ax, ay = 660, 250
    for label, (dq, dr) in (("+q", (1, 0)), ("+r", (0, 1))):
        dx, dy = SQRT3 * (dq + dr / 2), 1.5 * dr
        n = math.hypot(dx, dy)
        p.append(f"<line x1='{ax}' y1='{ay}' x2='{ax + 60 * dx / n:.1f}' y2='{ay + 60 * dy / n:.1f}' stroke='{ACCENT}' "
                 f"stroke-width='2.5' marker-end='url(#axis)'/>")
        p.append(text(ax + 78 * dx / n, ay + 78 * dy / n, label, 14, ACCENT, "bold"))
    notes = [
        "Pointy-top hexes in axial (q, r) coordinates.",
        "The third cube coordinate s = −q − r is implied.",
        "",
        "Hex ordinals are reading order: A is",
        "the top-left tile, S the bottom-right.",
        "",
        "Direction ordinals run counter-clockwise",
        "from NE, so opposite = (ordinal + 3) mod 6.",
    ]
    for i, line in enumerate(notes):
        p.append(text(630, 340 + i * 18, line, 12, MUTED, anchor="start"))
    write("hex-coordinates.svg", svg(980, 600, "\n".join(p), "Axial coordinates of the 19 hexes"))


# ---- 2. Corner and side naming on one hex ----

def hex_corners():
    size, cx, cy = 150, 300, 230
    p = [arrow_defs(INK)]
    p.append(f"<polygon points='{polygon(cx, cy, size)}' fill='{LAND}' stroke='{INK}' stroke-width='2'/>")
    p.append(text(cx, cy - 12, "hex (q, r)", 16, weight="bold"))
    p.append(text(cx, cy + 12, "hex.vertices[d] · hex.edges[d]", 12, MUTED))
    for d in DIRECTIONS:
        x, y = corner(cx, cy, size, d)
        p.append(f"<circle cx='{x:.1f}' cy='{y:.1f}' r='7' fill='{ACCENT}'/>")
        a = math.radians(CORNER_ANGLE[d])
        p.append(text(x + 30 * math.cos(a), y - 26 * math.sin(a), f"corner {d}", 13, ACCENT, "bold"))
        # side d between corner d and corner d-1
        bx, by = corner(cx, cy, size, prev_dir(d))
        mx, my = (x + bx) / 2, (y + by) / 2
        nx, ny = (mx - cx) / math.hypot(mx - cx, my - cy), (my - cy) / math.hypot(mx - cx, my - cy)
        p.append(text(mx - 26 * nx, my - 26 * ny, f"side {d}", 13, BLUE, "bold"))
    notes = [
        ("Side d is the edge shared with the neighbor in direction d.", INK),
        ("Corner d sits 30° counter-clockwise of side d,", INK),
        ("so side d runs from corner d to corner d − 1:", INK),
        ("side NE joins corners NE and E.", MUTED),
        ("", INK),
        ("Corner NE is the north point and corner SW the south.", INK),
        ("Each vertex is stored once, as the north or south", MUTED),
        ("corner of one cell (Vertex.fromCoordinates);", MUTED),
        ("each edge as the NE, NW or W side of one cell", MUTED),
        ("(Edge.fromCoordinate).", MUTED),
    ]
    for i, (line, color) in enumerate(notes):
        p.append(text(520, 120 + i * 20, line, 13, color, anchor="start"))
    write("hex-corners.svg", svg(1000, 460, "\n".join(p), "Corner and side names on one hex"))


# ---- 3 and 4. Vertex and edge ordinals ----

def vertex_ordinals():
    size, ox, oy = 62, 340, 320
    p = board(size, ox, oy)
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        p.append(text(cx, cy, h["name"], 18, LINE, "bold"))
    for v, (x, y) in sorted(vertex_positions(size, ox, oy).items()):
        p.append(f"<circle cx='{x:.1f}' cy='{y:.1f}' r='11' fill='{PAPER}' stroke='{ACCENT}' stroke-width='1.5'/>")
        p.append(text(x, y, v, 10, ACCENT, "bold"))
    p.append(text(340, 26, "Vertex ordinals (V0–V53)", 16, weight="bold"))
    caption = [
        "Each cell of a radius-3 grid owns its north and south corners. Cells are numbered",
        "row by row (r, then q), north before south, so V5 is the north of A and V6 its south.",
    ]
    for i, line in enumerate(caption):
        p.append(text(340, 604 + i * 18, line, 12, MUTED))
    write("vertex-ordinals.svg", svg(680, 650, "\n".join(p), "Vertex ordinals"))


def edge_ordinals():
    size, ox, oy = 62, 340, 320
    p = board(size, ox, oy)
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        p.append(text(cx, cy, h["name"], 18, LINE, "bold"))
    for e, places in sorted(edge_positions(size, ox, oy).items()):
        (mx, my), a, b, _ = places[0]
        coastal = len(places) == 1
        color = BLUE if coastal else INK
        p.append(f"<rect x='{mx - 11:.1f}' y='{my - 8:.1f}' width='22' height='16' rx='4' fill='{PAPER}' "
                 f"stroke='{color}' stroke-width='{1.6 if coastal else 0.8}'/>")
        p.append(text(mx, my, e, 10, color, "bold" if coastal else "normal"))
    p.append(text(340, 26, "Edge ordinals (E0–E71)", 16, weight="bold"))
    caption = [
        "Each cell of a radius-3 grid owns its NE, NW and W sides, numbered cell by cell",
        "in that order. Blue outlines mark the 30 coastal edges (one bordering hex).",
    ]
    for i, line in enumerate(caption):
        p.append(text(340, 604 + i * 18, line, 12, MUTED))
    write("edge-ordinals.svg", svg(680, 650, "\n".join(p), "Edge ordinals"))


# ---- 5. Padded storage grid behind Hex.fromCoordinate ----

def storage_grid():
    cell = 54
    p = [text(330, 26, "Hex.board: a 5 × 5 rhombus with the corners trimmed", 15, weight="bold")]
    ox, oy = 70, 80
    for r in range(-2, 3):
        for q in range(-2, 3):
            index = 5 * (r + 2) + q  # Hex.getIndex, before the validity check
            x = ox + (q + 2) * cell + (r + 2) * cell / 2
            y = oy + (r + 2) * cell
            valid = abs(q) + abs(r) + abs(q + r) <= 4
            name = next((h["name"] for h in HEXES.values() if h["q"] == q and h["r"] == r), None)
            cell_index = 5 * (r + 2) + (q + 2)  # position in the untrimmed 25-cell grid
            fill = LAND if valid else "#eef0f2"
            dash = "" if valid else "stroke-dasharray='4 3'"
            if cell_index < 2 or cell_index > 22:
                fill, dash = PAPER, "stroke-dasharray='2 4'"
            p.append(f"<rect x='{x:.1f}' y='{y:.1f}' width='{cell - 4}' height='{cell - 4}' rx='6' fill='{fill}' "
                     f"stroke='{LINE}' {dash}/>")
            label = name if valid else ("trimmed" if cell_index < 2 or cell_index > 22 else "null")
            p.append(text(x + (cell - 4) / 2, y + 17, label, 14 if valid else 10, INK if valid else MUTED,
                          "bold" if valid else "normal"))
            if not (cell_index < 2 or cell_index > 22):
                p.append(text(x + (cell - 4) / 2, y + 35, f"[{index}]", 10, ACCENT))
    notes = [
        "index = 5 (r + 2) + q",
        "",
        "Rows are r, columns are q, each row shifted half a cell",
        "so the rhombus lines up with the hexagon.",
        "",
        "The cells before A and after S are never needed,",
        "so the array starts 2 cells late: 21 slots, not 25.",
        "The two in-range corners hold null.",
        "",
        "Vertex.board and Edge.board use the same trick on a",
        "7 × 7 grid (radius 3), so sea cells around the island",
        "can own the coastal corners and sides.",
    ]
    for i, line in enumerate(notes):
        p.append(text(470, 90 + i * 19, line, 12, INK if i == 0 else MUTED, "bold" if i == 0 else "normal",
                      anchor="start"))
    write("hex-storage-grid.svg", svg(860, 380, "\n".join(p), "Padded storage grid for hex lookups"))


# ---- 6. Frame pieces and harbor slots ----

def frame_sides():
    size, ox, oy = 52, 330, 300
    p = board(size, ox, oy)
    edges = edge_positions(size, ox, oy)
    for side, side_edges in enumerate(DATA["sides"]):
        color = SIDE_COLORS[side]
        for slot, e in enumerate(side_edges):
            (mx, my), a, b, (nx, ny) = edges[e][0]
            p.append(f"<line x1='{a[0]:.1f}' y1='{a[1]:.1f}' x2='{b[0]:.1f}' y2='{b[1]:.1f}' stroke='{color}' "
                     f"stroke-width='6' stroke-linecap='round'/>")
            p.append(text(mx + 18 * nx, my + 18 * ny, slot, 11, color, "bold"))
        # side label beyond the middle slot
        (mx, my), _, _, (nx, ny) = edges[side_edges[2]][0]
        p.append(text(mx + 64 * nx, my + 64 * ny, f"side {side}", 14, color, "bold"))
        d = DIR_VECTORS[DIRECTIONS[side]]
        start = next(h["name"] for h in HEXES.values() if (h["q"], h["r"]) == (2 * d[0], 2 * d[1]))
        p.append(text(mx + 64 * nx, my + 64 * ny + 16, f"{DIRECTIONS[side]}: starts at {start}", 10, MUTED))
        p.append(text(mx + 64 * nx, my + 64 * ny + 30, f"fixed setup: {DATA['fixed']['frame'][side]}", 10, MUTED))
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        p.append(text(cx, cy, h["name"], 15, LINE, "bold"))
    notes = [
        ("FramePiece.sideEdges(side)", INK, "bold"),
        ("Each side covers 5 coastal edges, counter-clockwise.", MUTED, "normal"),
        ("Side d starts at the corner hex 2 steps out in", MUTED, "normal"),
        ("direction d; side 0 (NE) is the top of the island.", MUTED, "normal"),
        ("", MUTED, "normal"),
        ("Harbor slots printed on the pieces", INK, "bold"),
        ("GENERIC, LUMBER, ORE: one harbor, slot 2", MUTED, "normal"),
        ("WOOL, BRICK, GRAIN: 2:1 at slot 1, 3:1 at slot 4", MUTED, "normal"),
        ("", MUTED, "normal"),
        ("Shuffling the six pieces is the whole harbor setup.", MUTED, "normal"),
    ]
    for i, (line, color, weight) in enumerate(notes):
        p.append(text(680, 200 + i * 19, line, 12, color, weight, anchor="start"))
    write("frame-sides.svg", svg(1020, 600, "\n".join(p), "Frame sides and harbor slots"))


# ---- 7. Number disc spiral ----

def number_spiral():
    size, ox, oy = 52, 300, 290
    p = board(size, ox, oy)
    order = DATA["spirals"]["NE"]
    pts = [center(HEXES[n], size, ox, oy) for n in order]
    p.append(arrow_defs(ACCENT, "spiral"))
    path = " ".join(f"{'M' if i == 0 else 'L'}{x:.1f},{y:.1f}" for i, (x, y) in enumerate(pts))
    p.append(f"<path d='{path}' fill='none' stroke='{ACCENT}' stroke-width='2.5' opacity='0.6' "
             f"marker-end='url(#spiral)'/>")
    discs = "A B C D E F G H I J K L M N O P Q R".split()
    numbers = [5, 2, 6, 3, 8, 10, 9, 12, 11, 4, 8, 10, 9, 4, 5, 6, 3, 11]
    for i, (x, y) in enumerate(pts):
        p.append(f"<circle cx='{x:.1f}' cy='{y:.1f}' r='17' fill='{PAPER}' stroke='{ACCENT}' stroke-width='1.5'/>")
        p.append(text(x, y, i + 1, 13, ACCENT, "bold"))
        p.append(text(x, y + 30, order[i], 10, LINE))
    p.append(text(300, 24, "Hex.spiralInwardCC(NE)", 15, weight="bold"))
    rows = [
        ("Discs are laid in A–R order along the spiral, skipping the desert:", INK),
        ("", INK),
    ]
    for i, (line, color) in enumerate(rows):
        p.append(text(600, 150 + i * 19, line, 12, color, anchor="start"))
    for i, (letter, n) in enumerate(zip(discs, numbers)):
        col, row = i % 6, i // 6
        x, y = 610 + col * 52, 195 + row * 46
        red = n in (6, 8)
        p.append(f"<circle cx='{x}' cy='{y}' r='17' fill='{LAND}' stroke='{LINE}'/>")
        p.append(text(x, y - 4, n, 13, ACCENT if red else INK, "bold"))
        p.append(text(x, y + 9, letter, 9, MUTED))
    more = [
        "The start corner is the one random choice (pickWith);",
        "each of the 6 directions gives a rotated copy of this path.",
        "Small numbers on the path: visiting order 1–19.",
    ]
    for i, line in enumerate(more):
        p.append(text(600, 350 + i * 19, line, 12, MUTED, anchor="start"))
    write("number-spiral.svg", svg(960, 580, "\n".join(p), "Number disc spiral"))


# ---- 8. A generated board ----

HARBOR_LABEL = {"GENERIC": "3:1", "BRICK": "2:1 brick", "LUMBER": "2:1 lumber", "ORE": "2:1 ore",
                "GRAIN": "2:1 grain", "WOOL": "2:1 wool"}


def layout_board(key, heading, filename, title):
    size, ox, oy = 56, 360, 330
    sample = DATA[key]
    p = [f"<polygon points='{polygon(ox, oy, size * 5.4)}' fill='{SEA}' transform='rotate(30 {ox} {oy})'/>"]
    p += board(size, ox, oy, fill=lambda h: TERRAIN_FILL[sample["terrain"][h["name"]]], stroke=PAPER)
    edges = edge_positions(size, ox, oy)
    for e, port in sample["ports"].items():
        (mx, my), a, b, (nx, ny) = edges[int(e)][0]
        p.append(f"<line x1='{a[0]:.1f}' y1='{a[1]:.1f}' x2='{b[0]:.1f}' y2='{b[1]:.1f}' stroke='{INK}' "
                 f"stroke-width='4'/>")
        for end in (a, b):
            p.append(f"<line x1='{end[0]:.1f}' y1='{end[1]:.1f}' x2='{mx + 24 * nx:.1f}' y2='{my + 24 * ny:.1f}' "
                     f"stroke='{INK}' stroke-width='1' stroke-dasharray='3 2'/>")
        p.append(f"<rect x='{mx + 34 * nx - 31:.1f}' y='{my + 34 * ny - 10:.1f}' width='62' height='20' rx='10' "
                 f"fill='{PAPER}' stroke='{INK}'/>")
        p.append(text(mx + 34 * nx, my + 34 * ny, HARBOR_LABEL[port], 10, INK, "bold"))
    for h in HEXES.values():
        cx, cy = center(h, size, ox, oy)
        n = sample["numbers"][h["name"]]
        terrain = sample["terrain"][h["name"]]
        if terrain == "DESERT":
            p.append(text(cx, cy, "desert", 12, MUTED))
            p.append(text(cx, cy + 18, "robber", 10, MUTED))
            continue
        p.append(f"<circle cx='{cx:.1f}' cy='{cy:.1f}' r='18' fill='{PAPER}' stroke='{INK}' stroke-width='0.8'/>")
        p.append(text(cx, cy - 2, n, 15, ACCENT if n in (6, 8) else INK, "bold"))
        pips = 6 - abs(7 - n)
        p.append(text(cx, cy + 11, "·" * pips, 12, ACCENT if n in (6, 8) else INK))
        p.append(text(cx, cy + 32, terrain.lower(), 9, INK))
    p.append(text(360, 22, heading, 15, weight="bold"))
    legend = [("FOREST", "lumber"), ("HILL", "brick"), ("MOUNTAIN", "ore"), ("FIELDS", "grain"),
              ("PASTURE", "wool"), ("DESERT", "nothing")]
    for i, (t, res) in enumerate(legend):
        y = 120 + i * 26
        p.append(f"<rect x='720' y='{y - 9}' width='18' height='18' rx='3' fill='{TERRAIN_FILL[t]}'/>")
        p.append(text(746, y, f"{t.lower()} → {res}", 12, INK, anchor="start"))
    frame = ", ".join(s.lower() for s in sample["frame"])
    p.append(text(720, 300, "frame, side 0 to 5:", 12, MUTED, anchor="start"))
    p.append(text(720, 318, frame, 11, MUTED, anchor="start"))
    write(filename, svg(960, 660, "\n".join(p), title))


# ---- 9. Engine phases ----

def phases():
    w, h = 230, 62
    boxes = {
        "first": (40, 60, "RollForFirstPlayer", "ties roll again"),
        "settle": (40, 210, "SetupSettlement(round)", "snake order; round 2 pays out"),
        "road": (40, 360, "SetupRoad(round, v)", "a road touching settlement v"),
        "pre": (420, 60, "PreRoll", "roll, or play a card first"),
        "main": (420, 360, "Main", "trade and build in any order"),
        "rb": (420, 520, "RoadBuilding(n, returnTo)", "up to 2 free roads"),
        "discard": (790, 210, "Discard(pending)", "everyone over 7 cards"),
        "robber": (790, 360, "MoveRobber(returnTo)", "then rob a neighbor"),
        "over": (790, 520, "GameOver(winner)", "no actions accepted"),
    }
    p = [arrow_defs(INK)]
    for key, (x, y, title, sub) in boxes.items():
        fill = "#fff7ed" if key == "over" else ("#eff6ff" if key in ("first", "settle", "road") else LAND)
        dash = "stroke-dasharray='5 4'" if key == "over" else ""
        p.append(f"<rect x='{x}' y='{y}' width='{w}' height='{h}' rx='10' fill='{fill}' stroke='{INK}' "
                 f"stroke-width='1.4' {dash}/>")
        p.append(text(x + w / 2, y + 22, title, 14, weight="bold"))
        p.append(text(x + w / 2, y + 43, sub, 11, MUTED))

    def at(key, fx, fy):
        """A point on [key]'s box, as fractions of its width and height."""
        x, y, *_ = boxes[key]
        return x + w * fx, y + h * fy

    def line(points, label=None, label_at=(0, 0), anchor="middle"):
        d = " ".join(f"{'M' if i == 0 else 'L'}{x:.0f},{y:.0f}" for i, (x, y) in enumerate(points))
        p.append(f"<path d='{d}' fill='none' stroke='{INK}' stroke-width='1.3' marker-end='url(#arrow)'/>")
        if label:
            p.append(text(*label_at, label, 11, BLUE, anchor=anchor,
                          extra="paint-order='stroke' stroke='white' stroke-width='4'"))

    line([at("first", .5, 1), at("settle", .5, 0)], "highest roll goes first", (163, 166), "start")
    line([at("settle", .35, 1), at("road", .35, 0)], "PlaceSettlement", (112, 285), "end")
    line([at("road", .65, 0), at("settle", .65, 1)], "PlaceRoad: next player", (198, 285), "start")
    line([at("road", 1, .5), (330, 391), (330, 91), at("pre", 0, .5)], "after the last PlaceRoad", (340, 150),
         "start")
    line([at("pre", .35, 1), at("main", .35, 0)], "RollDice, not 7", (492, 240), "end")
    line([at("main", .65, 0), at("pre", .65, 1)], "EndTurn: next player", (578, 240), "start")
    line([at("pre", 1, .5), (905, 91), at("discard", .5, 0)], "RollDice 7, someone over 7", (780, 78))
    line([at("pre", 1, .9), (810, 360)], "7, no one over 7", (694, 168), "start")
    p.append(text(694, 184, "or PlayKnight", 11, BLUE, anchor="start"))
    line([at("discard", .5, 1), at("robber", .5, 0)], "last Discard", (915, 316), "start")
    line([at("main", 1, .25), at("robber", 0, .25)], "PlayKnight", (720, 362))
    line([at("robber", 0, .75), at("main", 1, .75)], "MoveRobber", (720, 420))
    line([at("main", .35, 1), at("rb", .35, 0)], "PlayRoadBuilding", (492, 451), "end")
    line([at("rb", .65, 0), at("main", .65, 1)], "last free road", (578, 451), "start")
    notes = [
        "MoveRobber and RoadBuilding resume at returnTo: Main, or PreRoll when the card was played before the roll.",
        "GameOver follows any accepted action after set-up that leaves the current player with 10 points.",
    ]
    for i, note in enumerate(notes):
        p.append(text(40, 618 + i * 19, note, 12, MUTED, anchor="start"))
    write("engine-phases.svg", svg(1060, 660, "\n".join(p), "Engine phases"))


# ---- 10. Bit layout of an encoded action ----

def action_bits():
    fields = [
        ("FORMAT_VERSION", 8, "#e0e7ff", FORMAT_VERSION),
        ("BlobKind", 8, "#fce7f3", "ACTION = 1"),
        ("actor", 2, "#dcfce7", "RED = 0"),
        ("Tag", 5, "#fef3c7", "BUILD_ROAD = 5"),
        ("edge", 7, "#e0f2fe", "E42 = 42"),
    ]
    unit = 26
    ox, oy = 40, 90
    p = [text(470, 26, "ActionCodec.encode(RED, BuildRoad(E42)): 30 bits in 4 bytes", 15, weight="bold"),
         text(470, 50, "Fields are packed least significant bit first, with no padding between them.", 12, MUTED)]
    bit = 0
    for name, width, fill, value in fields:
        x = ox + bit * unit
        p.append(f"<rect x='{x}' y='{oy}' width='{width * unit}' height='46' fill='{fill}' stroke='{INK}'/>")
        p.append(text(x + width * unit / 2, oy + 15, name, 12, weight="bold"))
        if width >= 4:
            p.append(text(x + width * unit / 2, oy + 32, f"{width} bits · {value}", 10, MUTED))
        else:
            # too narrow for the value; put it under the box
            p.append(text(x + width * unit / 2, oy + 32, f"{width} bits", 10, MUTED))
            p.append(text(x + width * unit / 2, oy + 60, value, 10, MUTED))
        bit += width
    for b in range(0, 33, 8):
        x = ox + b * unit
        p.append(f"<line x1='{x}' y1='{oy - 10}' x2='{x}' y2='{oy + 70}' stroke='{ACCENT}' stroke-dasharray='3 3'/>")
        if b < 32:
            p.append(text(x + 4 * unit, oy + 88, f"byte {b // 8}", 11, ACCENT))
    notes = [
        "Each enum is written as its ordinal in bitsFor(entries.size) bits: "
        "4 colors → 2, 18 tags → 5, 72 edges → 7.",
        "The last 2 bits of byte 3 are padding. Inside a GameRecord there is no header per action,",
        "so this action costs 14 bits; a whole game averages about 2 bytes per action.",
    ]
    for i, line in enumerate(notes):
        p.append(text(ox, 225 + i * 19, line, 12, MUTED, anchor="start"))
    write("action-bits.svg", svg(940, 300, "\n".join(p), "Bit layout of an encoded action"))


# ---- 11. What stays on the server ----

def trust_boundary():
    p = [arrow_defs(INK)]
    p.append(f"<rect x='20' y='50' width='430' height='380' rx='14' fill='#fff7ed' stroke='{ACCENT}' "
             f"stroke-width='1.5'/>")
    p.append(text(235, 75, "Server only", 15, ACCENT, "bold"))
    p.append(f"<rect x='560' y='50' width='360' height='380' rx='14' fill='#eff6ff' stroke='{BLUE}' "
             f"stroke-width='1.5'/>")
    p.append(text(740, 75, "Sent to clients", 15, BLUE, "bold"))
    server = [
        ("gameSeed", "decides every roll, draw and steal"),
        ("Game", "all hands, deck order, generator"),
        ("Game.copy(chance)", "for bot search; needs its own chance"),
        ("SnapshotCodec.encode(game)", "full state, resumes exactly"),
        ("GameRecord", "seeds + actions; replays the game"),
        ("ActionResult.Accepted.events", "includes what other players drew"),
    ]
    client = [
        ("boardSeed / BoardLayout", "public; rebuilds the island"),
        ("game.viewFor(player)", "own hand, others' card counts"),
        ("SnapshotCodec.encodeView(view)", "a PlayerView as bytes"),
        ("accepted.eventsFor(player)", "events with hidden cards removed"),
        ("ActionCodec", "actions in both directions"),
    ]
    for i, (name, sub) in enumerate(server):
        y = 100 + i * 54
        p.append(f"<rect x='40' y='{y}' width='390' height='44' rx='8' fill='{PAPER}' stroke='{LINE}'/>")
        p.append(text(55, y + 15, name, 13, INK, "bold", anchor="start"))
        p.append(text(55, y + 32, sub, 11, MUTED, anchor="start"))
    for i, (name, sub) in enumerate(client):
        y = 100 + i * 54
        p.append(f"<rect x='580' y='{y}' width='320' height='44' rx='8' fill='{PAPER}' stroke='{LINE}'/>")
        p.append(text(595, y + 15, name, 13, INK, "bold", anchor="start"))
        p.append(text(595, y + 32, sub, 11, MUTED, anchor="start"))
    def row(i):
        return 100 + i * 54 + 22

    halo = "paint-order='stroke' stroke='#ffffff' stroke-width='4'"
    for y1, y2, label in ((row(1), row(1), "viewFor"), (row(5), row(3), "eventsFor")):
        p.append(f"<line x1='432' y1='{y1}' x2='576' y2='{y2}' stroke='{INK}' stroke-width='1.5' "
                 f"marker-end='url(#arrow)'/>")
        p.append(text(504, (y1 + y2) / 2 - 12, label, 11, INK, extra=halo))
    p.append(f"<line x1='576' y1='{row(4)}' x2='432' y2='{row(1) + 12}' stroke='{INK}' stroke-width='1.5' "
             f"stroke-dasharray='5 3' marker-end='url(#arrow)'/>")
    p.append(text(520, 262, "Action → apply", 11, INK, extra=halo))
    p.append(text(470, 26, "Who may see what", 16, weight="bold"))
    write("trust-boundary.svg", svg(940, 450, "\n".join(p), "Server and client data"))


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    hex_coordinates()
    hex_corners()
    vertex_ordinals()
    edge_ordinals()
    storage_grid()
    frame_sides()
    number_spiral()
    layout_board("sample", "BoardLayout.generate(42)", "sample-board.svg", "A generated board")
    layout_board("fixed", "BoardLayout.fixedSetup", "fixed-setup.svg", "The rulebook's fixed setup")
    phases()
    action_bits()
    trust_boundary()
