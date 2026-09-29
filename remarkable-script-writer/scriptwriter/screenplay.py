"""Lay out a parsed Fountain script in standard US screenplay format and render it to PDF.

Format: US Letter, Courier 12pt (10 characters and 6 lines per inch), 1" top and
bottom margins, 1.5" left margin, 1" right margin. One page is roughly one minute
of screen time.
"""

import re
from collections import deque
from dataclasses import dataclass, field

from reportlab.lib.pagesizes import LETTER
from reportlab.pdfgen import canvas

from .fountain import Script, strip_emphasis

INCH = 72.0
FONT_SIZE = 12
LINE_H = 12  # 6 lines per inch
CHAR_W = 7.2  # Courier 12pt advance width
LINES_PER_PAGE = 54  # 9" of body text
TOP = 1 * INCH
PAGE_W, PAGE_H = LETTER

# element type -> (left edge in inches, max characters per line, blank lines before)
LAYOUT = {
    "scene_heading": (1.5, 60, 2),
    "action": (1.5, 60, 1),
    "character": (3.7, 38, 1),
    "parenthetical": (3.1, 25, 0),
    "dialogue": (2.5, 35, 0),
    "lyrics": (2.5, 35, 0),
    "transition": (1.5, 60, 1),
    "centered": (1.5, 60, 1),
}
RIGHT_EDGE = 7.5  # inches; transitions are right-aligned here


@dataclass
class Line:
    x: float  # inches
    text: str  # Fountain-marked-up text (emphasis markers intact)
    align: str = "left"
    kind: str = ""


@dataclass
class Block:
    kind: str
    lines: list
    space_before: int = 0
    keep_with_next: bool = False
    splittable: bool = False
    scene: object = None  # SceneInfo for scene headings
    character: str = ""  # for dialogue groups


@dataclass
class SceneInfo:
    number: str
    heading: str
    page: int = 0
    lines: int = 0  # body lines the scene occupies, including spacing
    characters: set = field(default_factory=set)
    synopsis: str = ""


@dataclass
class Layout:
    pages: list  # list of pages; each page is a list of (line_index_on_page, Line)
    scenes: list
    outline: list  # (level, title, page, line) for PDF bookmarks
    dialogue: dict  # character -> [speeches, words]


# ---------------------------------------------------------------- text helpers

EMPHASIS_RE = re.compile(r"(\\[*_])|(\*\*\*|\*\*|\*|_)(?=\S)(.+?)(?<=\S)\2")


def styled_chars(text):
    """Return a list of (char, bold, italic, underline) for Fountain emphasis markup."""
    out = []

    def walk(s, b, i, u):
        pos = 0
        for m in EMPHASIS_RE.finditer(s):
            out.extend((ch, b, i, u) for ch in s[pos : m.start()])
            if m.group(1):
                out.append((m.group(1)[1], b, i, u))
            else:
                mark = m.group(2)
                walk(
                    m.group(3),
                    b or mark in ("***", "**"),
                    i or mark in ("***", "*"),
                    u or mark == "_",
                )
            pos = m.end()
        out.extend((ch, b, i, u) for ch in s[pos:])

    walk(text, False, False, False)
    return out


def wrap(text, width):
    """Word-wrap marked-up text to `width` visible characters.

    Returns marked-up line strings. Emphasis spanning a break is closed and
    reopened so each line renders on its own.
    """
    result = []
    for paragraph in text.split("\n"):
        chars = styled_chars(paragraph)
        plain = "".join(c[0] for c in chars)
        if not plain.strip():
            result.append("")
            continue
        start, n = 0, len(plain)
        while start < n:
            if n - start <= width:
                end, nxt = n, n
            else:
                cut = plain.rfind(" ", start, start + width + 1)
                if cut <= start:
                    end = nxt = start + width
                else:
                    end, nxt = cut, cut + 1
            result.append(_remark(chars[start:end]))
            start = nxt
            while start < n and plain[start] == " ":
                start += 1
    return result


def _remark(chars):
    """Turn styled chars back into marked-up text."""
    out, cur = [], (False, False, False)
    for ch, *style in chars:
        style = tuple(style)
        if style != cur:
            out.append(_close(cur) + _open(style))
            cur = style
        out.append("\\" + ch if ch in "*_" else ch)
    out.append(_close(cur))
    return "".join(out)


def _open(style):
    b, i, u = style
    return ("_" if u else "") + ("*" * ((2 if b else 0) + (1 if i else 0)))


def _close(style):
    b, i, u = style
    return ("*" * ((2 if b else 0) + (1 if i else 0))) + ("_" if u else "")


def visible_len(text):
    return len(styled_chars(text))


# ---------------------------------------------------------------- blocks

def _lines(kind, text, align="left"):
    x, width, _ = LAYOUT[kind]
    return [Line(x, ln, align=align, kind=kind) for ln in wrap(text, width)]


def build_blocks(script: Script, scene_numbers=False):
    """Turn elements into layout blocks; also collect scene and dialogue stats."""
    blocks, scenes, dialogue = [], [], {}
    scene = None
    pending_synopsis = ""  # a synopsis describes the scene that follows it
    els = script.elements
    i = 0
    while i < len(els):
        el = els[i]
        t = el.type
        space = LAYOUT.get(t, (0, 0, 1))[2]
        if t == "page_break":
            blocks.append(Block("page_break", []))
        elif t == "section":
            blocks.append(Block("section", [], scene=(el.level, el.text)))
        elif t == "synopsis":
            if i > 0 and els[i - 1].type == "scene_heading":
                scene.synopsis = scene.synopsis or el.text
            else:
                pending_synopsis = pending_synopsis or el.text
        elif t == "scene_heading":
            number = el.scene_number or str(len(scenes) + 1)
            scene = SceneInfo(number, el.text, synopsis=pending_synopsis)
            pending_synopsis = ""
            scenes.append(scene)
            lines = _lines(t, el.text)
            if scene_numbers:
                lines[0].kind = "scene_heading#" + number
            blocks.append(Block(t, lines, space, keep_with_next=True, scene=scene))
        elif t == "character":
            lines = _lines("character", el.text)
            words = 0
            j = i + 1
            while j < len(els) and els[j].type in ("parenthetical", "dialogue", "lyrics"):
                sub = els[j]
                text = f"*{sub.text}*" if sub.type == "lyrics" and sub.text else sub.text
                for k, line in enumerate(_lines(sub.type, text)):
                    if sub.type == "parenthetical" and k > 0:
                        line.x += 0.1  # hang continuation lines inside the parenthesis
                    lines.append(line)
                if sub.type != "parenthetical":
                    words += len(sub.text.split())
                j += 1
            name = re.sub(r"\s*\(.*?\)", "", el.text).strip()
            entry = dialogue.setdefault(name, [0, 0])
            entry[0] += 1
            entry[1] += words
            if scene is not None:
                scene.characters.add(name)
            blocks.append(Block("dialogue", lines, space, splittable=True, character=name))
            i = j
            continue
        elif t == "lyrics":
            lines = _lines(t, f"*{el.text}*")
            if i > 0 and els[i - 1].type == "lyrics" and blocks and blocks[-1].kind == "lyrics":
                blocks[-1].lines.extend(lines)  # consecutive lyric lines form one verse
            else:
                blocks.append(Block(t, lines, 1))
        elif t == "transition":
            blocks.append(Block(t, [Line(RIGHT_EDGE, el.text, align="right", kind=t)], space))
        elif t == "centered":
            lines = _lines(t, el.text)
            for line in lines:
                line.x, line.align = 4.5, "center"
            blocks.append(Block(t, lines, space))
        else:  # action, or a stray parenthetical/dialogue line with no character
            blocks.append(Block("action", _lines("action", el.text), LAYOUT["action"][2],
                                splittable=True))
        i += 1
    return blocks, scenes, dialogue


# ---------------------------------------------------------------- pagination

def paginate(blocks):
    """Assign blocks to pages. Returns (pages, outline)."""
    pages = [[]]
    used = 0
    outline = []
    scene = None
    section_level = -1
    queue = deque(blocks)

    def place(block, space):
        nonlocal used
        if scene is not None:
            scene.lines += space + len(block.lines)
        used += space
        for line in block.lines:
            pages[-1].append((used, line))
            used += 1

    def new_page():
        nonlocal used
        pages.append([])
        used = 0

    while queue:
        block = queue.popleft()
        if block.kind == "page_break":
            if used:
                new_page()
            continue
        if block.kind == "section":
            depth, title = block.scene
            section_level = min(depth - 1, section_level + 1)
            outline.append((section_level, title, len(pages), used))
            continue

        space = block.space_before if used else 0
        need = space + len(block.lines)
        if block.keep_with_next:
            nxt = next((b for b in queue if b.lines), None)
            if nxt is not None:
                need += nxt.space_before + min(2, len(nxt.lines))

        if used + need > LINES_PER_PAGE:
            head = tail = None
            if block.splittable and used:
                head, tail = split_block(block, LINES_PER_PAGE - used - space)
            elif not used:
                # Bigger than an empty page: split wherever it falls.
                head, tail = split_block(block, LINES_PER_PAGE, force=True)
            if head is not None:
                place(head, space)
                new_page()
                queue.appendleft(tail)
                continue
            if used:
                new_page()
                space = 0

        if block.kind == "scene_heading":
            scene = block.scene
            scene.page = len(pages)
            outline.append((section_level + 1, f"{scene.number}. {scene.heading}", len(pages), used))
        place(block, space)

    return pages, outline


def split_block(block, room, force=False):
    """Split a block to fit `room` lines. Returns (head, tail), or (None, None) if it can't."""
    lines = block.lines
    if force:
        if len(lines) <= room:
            return None, None
        k = room
    elif block.kind == "dialogue":
        # Reserve a line for (MORE). Keep the name and at least one line of speech
        # on this page, and at least one line of speech for the continuation.
        k = None
        for cand in range(2, min(room - 1, len(lines) - 1) + 1):
            if lines[cand - 1].kind in ("dialogue", "lyrics"):
                k = cand
        if k is None:
            return None, None
        char_x = LAYOUT["character"][0]
        name = lines[0].text
        cont = name if "CONT'D" in name else f"{name} (CONT'D)"
        head = Block("dialogue", lines[:k] + [Line(char_x, "(MORE)", kind="more")],
                     block.space_before, character=block.character)
        tail = Block("dialogue", [Line(char_x, cont, kind="character")] + lines[k:], 0,
                     splittable=True, character=block.character)
        return head, tail
    else:
        # Widow/orphan control: at least two lines on each side of the break.
        k = min(room, len(lines) - 2)
        if k < 2:
            return None, None
    head = Block(block.kind, lines[:k], block.space_before)
    tail = Block(block.kind, lines[k:], 0, splittable=True, character=block.character)
    return head, tail


def layout(script, scene_numbers=False):
    blocks, scenes, dialogue = build_blocks(script, scene_numbers)
    pages, outline = paginate(blocks)
    return Layout(pages, scenes, outline, dialogue)


# ---------------------------------------------------------------- rendering

FONTS = {
    (False, False): "Courier",
    (True, False): "Courier-Bold",
    (False, True): "Courier-Oblique",
    (True, True): "Courier-BoldOblique",
}


def draw_marked(c, x, y, text, align="left"):
    chars = styled_chars(text)
    width = len(chars) * CHAR_W
    if align == "right":
        x -= width
    elif align == "center":
        x -= width / 2
    i = 0
    while i < len(chars):
        j = i
        style = chars[i][1:]
        while j < len(chars) and chars[j][1:] == style:
            j += 1
        run = "".join(ch[0] for ch in chars[i:j])
        b, it, u = style
        c.setFont(FONTS[(b, it)], FONT_SIZE)
        rx = x + i * CHAR_W
        c.drawString(rx, y, run)
        if u:
            c.setLineWidth(0.6)
            c.line(rx, y - 1.5, rx + len(run) * CHAR_W, y - 1.5)
        i = j


def render(script: Script, path, scene_numbers=False, title_page=True):
    lay = layout(script, scene_numbers)
    c = canvas.Canvas(path, pagesize=LETTER)
    c.setTitle(script.title or "Screenplay")
    author = script.title_page.get("author") or script.title_page.get("authors")
    if author:
        c.setAuthor(strip_emphasis(author).replace("\n", ", "))
    c.setCreator("reMarkable Script Writer")

    if title_page and script.title_page:
        draw_title_page(c, script.title_page)
        c.bookmarkPage("title")
        c.addOutlineEntry("Title page", "title", 0)
        c.showPage()

    marks = {}
    for idx, (level, title, page, line) in enumerate(lay.outline):
        marks.setdefault(page, []).append((idx, level, title, line))

    for pno, page in enumerate(lay.pages, start=1):
        if pno > 1:
            c.setFont("Courier", FONT_SIZE)
            c.drawRightString(RIGHT_EDGE * INCH, PAGE_H - 0.5 * INCH, f"{pno}.")
        # Bookmarks for scenes and sections on this page.
        for idx, level, title, line in marks.get(pno, []):
            key = f"o{idx}"
            c.bookmarkHorizontalAbsolute(key, PAGE_H - TOP - line * LINE_H + LINE_H)
            c.addOutlineEntry(title, key, level)
        for row, ln in page:
            y = PAGE_H - TOP - row * LINE_H - FONT_SIZE * 0.8
            if ln.kind.startswith("scene_heading#"):
                number = ln.kind.split("#", 1)[1]
                c.setFont("Courier", FONT_SIZE)
                c.drawRightString(1.5 * INCH - 0.4 * INCH, y, number)
                c.drawString(RIGHT_EDGE * INCH + 0.3 * INCH, y, number)
            draw_marked(c, ln.x * INCH, y, ln.text, ln.align)
        c.showPage()
    c.save()
    return lay


def draw_title_page(c, tp):
    def block(text, y, align="center", x=PAGE_W / 2):
        for raw in text.split("\n"):
            if raw.strip():
                draw_marked(c, x, y, raw.strip(), align)
            y -= LINE_H
        return y

    y = PAGE_H - 3.5 * INCH
    if tp.get("title"):
        y = block(tp["title"], y)
    y -= LINE_H * 2
    credit = tp.get("credit") or ("Written by" if (tp.get("author") or tp.get("authors")) else "")
    if credit:
        y = block(credit, y) - LINE_H
    author = tp.get("author") or tp.get("authors")
    if author:
        y = block(author, y) - LINE_H * 2
    if tp.get("source"):
        block(tp["source"], y)

    bottom = 1.0 * INCH + LINE_H * 6
    left_text = "\n".join(v for v in (tp.get("draft date"), tp.get("contact")) if v)
    if left_text:
        block(left_text, bottom, align="left", x=1.5 * INCH)
    right_text = "\n".join(v for v in (tp.get("notes"), tp.get("copyright")) if v)
    if right_text:
        lines = right_text.split("\n")
        width = max(visible_len(ln) for ln in lines) * CHAR_W
        block(right_text, bottom, align="left", x=RIGHT_EDGE * INCH - width)


# ---------------------------------------------------------------- stats

def eighths(lines):
    """Scene length in page-eighths, the unit schedulers use (1/8 page ~ 6.75 lines)."""
    n = max(1, round(lines / (LINES_PER_PAGE / 8)))
    whole, rem = divmod(n, 8)
    if whole and rem:
        return f"{whole} {rem}/8"
    return f"{whole}" if whole else f"{rem}/8"


def report(script, scene_numbers=False):
    lay = layout(script, scene_numbers)
    pages = len(lay.pages)
    out = []
    title = script.title or "Untitled"
    out.append(f"{title}")
    out.append("=" * len(title))
    out.append(f"Pages: {pages}  (about {pages} minute{'s' if pages != 1 else ''} of screen time)")
    out.append(f"Scenes: {len(lay.scenes)}")

    ie = {"INT": 0, "EXT": 0, "INT/EXT": 0, "OTHER": 0}
    tod = {}
    for s in lay.scenes:
        head = s.heading
        if re.match(r"^(INT\.?/EXT|I/E)", head):
            ie["INT/EXT"] += 1
        elif head.startswith("INT"):
            ie["INT"] += 1
        elif head.startswith("EXT"):
            ie["EXT"] += 1
        else:
            ie["OTHER"] += 1
        if " - " in head:
            t = head.rsplit(" - ", 1)[1].strip()
            tod[t] = tod.get(t, 0) + 1
    out.append("Interior/exterior: " + ", ".join(f"{k} {v}" for k, v in ie.items() if v))
    if tod:
        out.append("Time of day: " + ", ".join(f"{k} {v}" for k, v in sorted(tod.items(), key=lambda kv: -kv[1])))

    out.append("")
    out.append("SCENES")
    out.append(f"{'#':>4}  {'pg':>3}  {'length':>6}  heading")
    for s in lay.scenes:
        out.append(f"{s.number:>4}  {s.page:>3}  {eighths(s.lines):>6}  {s.heading}")
        if s.synopsis:
            out.append(f"{'':>20}{s.synopsis}")

    if lay.dialogue:
        out.append("")
        out.append("DIALOGUE")
        out.append(f"{'character':<24}{'speeches':>9}{'words':>8}{'scenes':>8}")
        scene_count = {}
        for s in lay.scenes:
            for ch in s.characters:
                scene_count[ch] = scene_count.get(ch, 0) + 1
        for name, (speeches, words) in sorted(lay.dialogue.items(), key=lambda kv: -kv[1][1]):
            out.append(f"{name:<24}{speeches:>9}{words:>8}{scene_count.get(name, 0):>8}")
    return "\n".join(out)
