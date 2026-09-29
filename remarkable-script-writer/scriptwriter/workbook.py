"""Generate the Screenplay Workbook: a hyperlinked PDF notebook for the reMarkable 2.

Every page carries a tab bar linking to each section, so the PDF behaves like an
app on the tablet: tap a tab to jump, write on the page with the pen. Pages are
3:4, the reMarkable 2 screen's aspect ratio (1404 x 1872 px), so nothing is
letterboxed.
"""

from dataclasses import dataclass

from reportlab.lib.colors import Color, black, white
from reportlab.lib.utils import simpleSplit
from reportlab.pdfgen import canvas

W, H = 468.0, 624.0  # points, 3:4
ML, MR = 30.0, 24.0  # left/right margins
NAV_H = 30.0
BOTTOM = 34.0  # content must stay above this (top-based: H - BOTTOM)
LINE = 20.0  # handwriting line spacing

INK = black
DARK = Color(0.25, 0.25, 0.25)
MID = Color(0.45, 0.45, 0.45)
LIGHT = Color(0.72, 0.72, 0.72)
FAINT = Color(0.86, 0.86, 0.86)

TABS = [
    ("home", "Home"),
    ("idea", "Idea"),
    ("story", "Story"),
    ("cast", "Cast"),
    ("scenes", "Scenes"),
    ("outline", "Outline"),
    ("script", "Script"),
    ("notes", "Notes"),
    ("guide", "Guide"),
]


@dataclass
class Options:
    mode: str = "film"  # film, tv or both
    title: str = ""
    target_pages: int = 110  # feature length used to scale beat page targets
    tv_format: str = "hour"  # hour or half-hour
    episodes: int = 3  # episode structure worksheets (TV)
    characters: int = 8
    scene_card_pages: int = 10
    outline_pages: int = 5
    script_pages: int = 40
    notes_pages: int = 10


class Book:
    def __init__(self, path, opts: Options):
        self.opts = opts
        self.c = canvas.Canvas(path, pagesize=(W, H))
        self.c.setTitle(f"{opts.title} - Screenplay Workbook" if opts.title else "Screenplay Workbook")
        self.c.setAuthor("")
        self.c.setCreator("reMarkable Script Writer")
        self.page_no = 0
        self.section = None
        self.section_pages = {}  # section id -> first page no

    # ------------------------------------------------------------ primitives

    def y(self, t):
        """Convert a distance from the top of the page into a PDF y coordinate."""
        return H - t

    def text(self, x, t, s, font="Helvetica", size=10, color=INK, align="left"):
        c = self.c
        c.setFillColor(color)
        c.setFont(font, size)
        if align == "center":
            c.drawCentredString(x, self.y(t), s)
        elif align == "right":
            c.drawRightString(x, self.y(t), s)
        else:
            c.drawString(x, self.y(t), s)

    def para(self, x, t, width, s, font="Helvetica", size=9, color=DARK, leading=None):
        leading = leading or size * 1.3
        for line in simpleSplit(s, font, size, width):
            self.text(x, t, line, font, size, color)
            t += leading
        return t

    def hline(self, x1, x2, t, color=LIGHT, width=0.6, dash=None):
        c = self.c
        c.setStrokeColor(color)
        c.setLineWidth(width)
        c.setDash(*dash) if dash else c.setDash()
        c.line(x1, self.y(t), x2, self.y(t))
        c.setDash()

    def vline(self, x, t1, t2, color=LIGHT, width=0.6, dash=None):
        c = self.c
        c.setStrokeColor(color)
        c.setLineWidth(width)
        c.setDash(*dash) if dash else c.setDash()
        c.line(x, self.y(t1), x, self.y(t2))
        c.setDash()

    def rect(self, x, t, w, h, stroke=LIGHT, fill=None, width=0.8, radius=0):
        c = self.c
        c.setStrokeColor(stroke)
        c.setLineWidth(width)
        if fill is not None:
            c.setFillColor(fill)
        if radius:
            c.roundRect(x, self.y(t + h), w, h, radius, stroke=1, fill=1 if fill is not None else 0)
        else:
            c.rect(x, self.y(t + h), w, h, stroke=1, fill=1 if fill is not None else 0)

    def link(self, key, x, t, w, h):
        self.c.linkAbsolute("", key, Rect=(x, self.y(t + h), x + w, self.y(t)), thickness=0)

    def checkbox(self, x, t, label=None, size=8, font_size=8):
        self.rect(x, t - size + 1, size, size, stroke=MID, width=0.7)
        if label:
            self.text(x + size + 3, t, label, size=font_size, color=DARK)
            return x + size + 3 + self.c.stringWidth(label, "Helvetica", font_size) + 9
        return x + size + 6

    def rules(self, t, bottom, x1=ML, x2=W - MR, spacing=LINE, color=LIGHT):
        """Ruled writing lines from t down to bottom. Returns t after the last line."""
        t += spacing
        while t <= bottom:
            self.hline(x1, x2, t, color)
            t += spacing
        return t - spacing

    def field(self, t, label, lines=1, hint=None, x1=ML, x2=W - MR, spacing=LINE):
        """A labeled prompt followed by writing lines. Returns t after the field."""
        self.text(x1, t, label.upper(), "Helvetica-Bold", 7.5, INK)
        if hint:
            lw = self.c.stringWidth(label.upper(), "Helvetica-Bold", 7.5)
            self.text(x1 + lw + 6, t, hint, "Helvetica-Oblique", 7.5, MID)
        for _ in range(lines):
            t += spacing
            self.hline(x1, x2, t)
        return t + 13

    def table(self, t, columns, rows, row_h=LINE, header_size=7.5):
        """columns: list of (label, width or None=fill). Draws header + ruled grid."""
        total = W - MR - ML
        fixed = sum(w for _, w in columns if w)
        flex = [i for i, (_, w) in enumerate(columns) if not w]
        widths = [w if w else (total - fixed) / len(flex) for _, w in columns]
        x = ML
        for (label, _), w in zip(columns, widths):
            self.text(x + 3, t, label.upper(), "Helvetica-Bold", header_size)
            x += w
        top = t + 5
        self.hline(ML, W - MR, top, DARK, 0.9)
        for r in range(rows):
            self.hline(ML, W - MR, top + (r + 1) * row_h)
        x = ML
        for w in widths[:-1]:
            x += w
            self.vline(x, top, top + rows * row_h, FAINT)
        return top + rows * row_h + 12

    # ------------------------------------------------------------ page chrome

    def page(self, section, title, subtitle=None, key=None, outline=None, level=1, compact=False):
        """Start a page. Returns the top-based y where content may begin."""
        c = self.c
        if self.page_no:
            c.showPage()
        self.page_no += 1
        self.section = section
        pkey = f"p{self.page_no}"
        c.bookmarkPage(pkey)
        if section not in self.section_pages:
            self.section_pages[section] = self.page_no
            c.bookmarkPage(section)
            label = dict(TABS)[section]
            c.addOutlineEntry(label, section, 0)
        if key:
            c.bookmarkPage(key)
        if outline:
            c.addOutlineEntry(outline, key or pkey, level)
        self._nav(section)
        self._footer(section)
        if compact:
            self.text(ML, NAV_H + 16, title.upper(), "Helvetica-Bold", 8, DARK)
            return NAV_H + 22
        self.text(ML, NAV_H + 30, title, "Helvetica-Bold", 17)
        t = NAV_H + 30
        if subtitle:
            t = self.para(ML, t + 14, W - ML - MR, subtitle, "Helvetica-Oblique", 8.5, MID, 11) - 11
        self.hline(ML, W - MR, t + 9, INK, 1.2)
        return t + 26

    def _nav(self, active):
        x0, x1 = 8.0, W - 8.0
        w = (x1 - x0) / len(TABS)
        for i, (sid, label) in enumerate(TABS):
            x = x0 + i * w
            if sid == active:
                self.rect(x + 1, 4, w - 2, NAV_H - 8, stroke=INK, fill=INK, radius=3)
                color = white
            else:
                self.rect(x + 1, 4, w - 2, NAV_H - 8, stroke=LIGHT, width=0.6, radius=3)
                color = DARK
            self.text(x + w / 2, NAV_H / 2 + 3, label, "Helvetica-Bold" if sid == active else "Helvetica",
                      8, color, align="center")
            self.link(sid, x, 0, w, NAV_H)

    def _footer(self, section):
        label = dict(TABS)[section]
        self.text(ML, H - 14, f"‹ {label}", "Helvetica", 7, MID)
        self.link(section, ML - 4, H - 26, 60, 18)
        self.text(W / 2, H - 14, self.opts.title or "Screenplay Workbook", "Helvetica", 7, LIGHT,
                  align="center")
        self.text(W - MR, H - 14, str(self.page_no), "Helvetica", 7, MID, align="right")

    def fits(self, t):
        return t <= H - BOTTOM

    def save(self):
        self.c.showPage()
        self.c.save()


# ================================================================ content

BEATS = [  # Blake Snyder's "Save the Cat!" beat sheet; pages are for a 110-page script.
    ("Opening Image", 1, 1, "A snapshot of the hero and their world before the story changes them."),
    ("Theme Stated", 5, 5, "Someone (rarely the hero) voices what the story is really about."),
    ("Set-Up", 1, 10, "Hero, world, and what's missing in their life. Plant what will pay off later."),
    ("Catalyst", 12, 12, "The inciting incident that knocks the hero's world off balance."),
    ("Debate", 12, 25, "The hero hesitates. Should I go? What will it cost?"),
    ("Break into Two", 25, 25, "The hero chooses to enter the new world and pursue the goal."),
    ("B Story", 30, 30, "A new relationship that carries the theme: love interest, mentor, rival."),
    ("Fun and Games", 30, 55, "The promise of the premise: the set pieces the trailer is made of."),
    ("Midpoint", 55, 55, "A false victory or false defeat. Stakes rise; a clock may start ticking."),
    ("Bad Guys Close In", 55, 75, "Outside pressure and inside doubt tighten around the hero."),
    ("All Is Lost", 75, 75, "The lowest point. Something or someone dies, literally or symbolically."),
    ("Dark Night of the Soul", 75, 85, "The hero wallows, then finally grasps the lesson of the theme."),
    ("Break into Three", 85, 85, "A and B stories meet; armed with the lesson, the hero acts."),
    ("Finale", 85, 110, "The hero proves they've changed and confronts the central conflict."),
    ("Final Image", 110, 110, "A mirror of the Opening Image that shows how much has changed."),
]

SEQUENCES = [
    ("Status quo & inciting incident", "Establish the hero's world; end on the event that disrupts it."),
    ("Predicament & lock-in", "The hero reacts, then commits to the goal. End of Act I."),
    ("First obstacle", "The first, most obvious attempt to solve the problem."),
    ("Escalation to midpoint", "Bigger attempts, bigger failures, leading to the midpoint turn."),
    ("Complication", "A new plan after the midpoint; subplots deepen and cost rises."),
    ("Lowest point", "Everything the hero tried collapses. End of Act II."),
    ("New tension & final push", "A twist or revelation sets up the last attempt."),
    ("Climax & resolution", "The final confrontation, then a glimpse of the new normal."),
]

ACTS = [  # page bounds match the beat sheet (110-page scale)
    ("Act I", "Setup", 1, 25, "Who is the hero, what do they want, and what disrupts their world?"),
    ("Act IIA", "Rising action", 25, 55, "What new world or plan do they commit to? What's fun about it?"),
    ("Act IIB", "Things fall apart", 55, 85, "How do the stakes rise? What do they lose?"),
    ("Act III", "Resolution", 85, 110, "How do they win or lose, and who are they now?"),
]

TV_STRUCTURE = {
    "half-hour": [("Cold Open", 1, 3), ("Act One", 3, 14), ("Act Two", 14, 27), ("Tag", 27, 30)],
    "hour": [
        ("Teaser", 1, 5),
        ("Act One", 5, 16),
        ("Act Two", 16, 27),
        ("Act Three", 27, 38),
        ("Act Four", 38, 49),
        ("Act Five", 49, 58),
    ],
}

REWRITE_PASSES = [
    ("Structure", [
        "The inciting incident lands early enough (film: by ~p. 12).",
        "The hero makes an active choice to enter Act II.",
        "The midpoint changes the direction or stakes of the story.",
        "The climax is won or lost by the hero's own choice.",
        "Every scene earns its place. Could two be combined?",
    ]),
    ("Character", [
        "Every character wants something in every scene they're in.",
        "The protagonist changes (or tragically refuses to).",
        "The antagonist is at least as capable as the hero.",
        "Cover the names: can you tell who is speaking?",
    ]),
    ("Scene", [
        "Each scene enters late and leaves early.",
        "Each scene turns: a value shifts from + to - or - to +.",
        "There is conflict or tension in every scene.",
    ]),
    ("Dialogue", [
        "Subtext over on-the-nose: people rarely say what they mean.",
        "Greetings, small talk and filler lines are cut.",
        "Exposition is dramatized, not explained.",
    ]),
    ("Polish", [
        "Action paragraphs are four lines or fewer; present tense, active verbs.",
        "Speaking characters are in CAPS, with an age, the first time we meet them.",
        "Page count sits in range; spelling and formatting are clean.",
    ]),
]


def scale(p, target):
    return max(1, round(p * target / 110))


def page_range(a, b, target):
    a, b = scale(a, target), scale(b, target)
    return f"p. {a}" if a == b else f"pp. {a}–{b}"


# ================================================================ pages

def cover(b: Book):
    t = b.page("home", "Screenplay Workbook", key="cover", outline="Cover")
    t += 40
    b.text(W / 2, t, "A writing room in your pocket", "Helvetica-Oblique", 11, MID, align="center")
    t += 60
    if b.opts.title:
        b.text(W / 2, t, b.opts.title.upper(), "Courier-Bold", 20, align="center")
        t += 40
    else:
        t = b.field(t, "Title", 2, spacing=30) + 10
    for label in ("Written by", "Format", "Draft", "Started", "Contact"):
        t = b.field(t, label, 1, spacing=26) + 4
    b.rect(ML, t + 10, W - ML - MR, 44, stroke=LIGHT)
    b.text(W / 2, t + 29, "Tap a tab at the top of any page to jump to that section.", "Helvetica", 8.5,
           DARK, align="center")
    b.text(W / 2, t + 43, "Start with Home → Guide if this is your first time.", "Helvetica", 8.5, DARK,
           align="center")
    b.link("home-index", ML, t + 10, W - ML - MR, 44)


def home(b: Book, index):
    """index: list of (section, label, description, [(chip label, key), ...])"""
    t = b.page("home", "Home", "Your map of the workbook. Tap any box or number to jump there.",
               key="home-index", outline="Home")
    box_w = W - ML - MR
    for sid, label, desc, chips in index:
        h = 40 if not chips else 40 + 22 * ((len(chips) - 1) // 12 + 1)
        b.rect(ML, t, box_w, h, stroke=LIGHT, radius=4)
        b.text(ML + 10, t + 16, label, "Helvetica-Bold", 11)
        b.text(ML + 10, t + 30, desc, "Helvetica", 8, MID)
        b.link(sid, ML, t, box_w, 36)
        b.text(ML + box_w - 10, t + 16, "›", "Helvetica-Bold", 13, MID, align="right")
        cx, ct = ML + 10, t + 40
        for i, (chip, key) in enumerate(chips):
            if i and i % 12 == 0:
                cx, ct = ML + 10, ct + 22
            cw = 33
            b.rect(cx, ct, cw - 4, 18, stroke=LIGHT, width=0.5, radius=2)
            b.text(cx + (cw - 4) / 2, ct + 12, chip, "Helvetica", 7.5, DARK, align="center")
            b.link(key, cx, ct, cw - 4, 18)
            cx += cw
        t += h + 6


# ---------------------------------------------------------------- idea

def idea_pages(b: Book):
    t = b.page("idea", "Logline", "One sentence that sells the story: who wants what, what stands in the "
               "way, and what happens if they fail.", key="idea-logline", outline="Logline")
    b.rect(ML, t, W - ML - MR, 38, stroke=LIGHT, fill=Color(0.95, 0.95, 0.95))
    b.para(ML + 10, t + 15, W - ML - MR - 20,
           "When [INCITING INCIDENT], a [FLAWED PROTAGONIST] must [GOAL] or else [STAKES], "
           "despite [ANTAGONIST / OBSTACLE].", "Helvetica-Oblique", 9, DARK, 12)
    t += 54
    for label, hint in [
        ("Protagonist", "Who, plus the flaw that makes it hard for them"),
        ("Inciting incident", "What knocks their world off balance?"),
        ("Goal", "Specific and visible: what can the camera see them chase?"),
        ("Antagonist / obstacle", "Who or what actively opposes them?"),
        ("Stakes", "What happens if they fail?"),
        ("The hook", "What's fresh or ironic about this?"),
    ]:
        t = b.field(t, label, 1, hint)
    t += 4
    for n in (1, 2, 3):
        t = b.field(t, f"Logline, draft {n}", 2)

    t = b.page("idea", "Premise & Theme", "What the story is, and what it's really about.",
               key="idea-premise", outline="Premise & theme")
    x2 = (W - MR + ML) / 2 - 8
    x3 = x2 + 16
    b.field(t, "Genre", 1, x2=x2)
    t = b.field(t, "Tone", 1, x1=x3)
    b.field(t, "Target length", 1, x2=x2)
    t = b.field(t, "Audience / rating", 1, x1=x3)
    t = b.field(t, "Comparable titles", 1, "\"X meets Y\"; recent and successful")
    t = b.field(t, "Theme", 2, "What question does the story ask about how to live?")
    t = b.field(t, "Controlling idea", 1, "The story's answer: \"___ happens when ___\"")
    t = b.field(t, "Central dramatic question", 1, "Will [hero] [achieve goal]?")
    t = b.field(t, "Why this story, why me, why now?", 3)
    t = b.field(t, "Title ideas", 3)

    t = b.page("idea", "One-Page Synopsis", "Tell the whole story, ending included, in present tense.",
               key="idea-synopsis", outline="Synopsis")
    b.rules(t - 14, H - BOTTOM)


# ---------------------------------------------------------------- story

def film_story_pages(b: Book):
    target = b.opts.target_pages
    t = b.page("story", "Three-Act Structure",
               f"The shape of a {target}-page feature. Mark where your turning points land.",
               key="story-acts", outline="Three-act structure")
    # Timeline diagram with a rising tension curve.
    x0, x1 = ML + 6, W - MR - 6
    base = t + 92
    span = x1 - x0
    c = b.c
    c.setStrokeColor(DARK)
    c.setLineWidth(1.4)
    p = c.beginPath()
    pts = [(0, 10), (0.11, 16), (0.23, 36), (0.4, 42), (0.5, 58), (0.6, 50), (0.68, 26), (0.77, 44),
           (0.9, 82), (0.97, 70), (1.0, 40)]
    p.moveTo(x0, b.y(base - pts[0][1]))
    for i in range(1, len(pts)):
        xa, ya = pts[i - 1]
        xb, yb = pts[i]
        mx = (xa + xb) / 2
        p.curveTo(x0 + mx * span, b.y(base - ya), x0 + mx * span, b.y(base - yb), x0 + xb * span,
                  b.y(base - yb))
    c.drawPath(p, stroke=1, fill=0)
    b.hline(x0, x1, base, INK, 1.2)
    marks = [(1, "1"), (12, "Catalyst"), (25, "Act I break"), (55, "Midpoint"), (75, "All is lost"),
             (85, "Act II break"), (99, "Climax"), (110, str(target))]
    for page, label in marks:
        x = x0 + (page - 1) / 109 * span
        b.vline(x, base - 4, base + 4, INK, 1)
        b.text(x, base + 14, label, "Helvetica", 6.5, DARK, align="center")
        if page not in (1, 110):
            b.text(x, base + 23, f"p. {scale(page, target)}", "Helvetica", 6, MID, align="center")
    b.text(x0, t + 4, "TENSION", "Helvetica-Bold", 6.5, MID)
    t = base + 40
    for name, sub, a, z, q in ACTS:
        b.text(ML, t, f"{name.upper()}  ·  {sub}", "Helvetica-Bold", 8.5)
        b.text(W - MR, t, page_range(a, z, target), "Helvetica", 7.5, MID, align="right")
        b.text(ML, t + 12, q, "Helvetica-Oblique", 7.5, MID)
        t = b.rules(t + 8, t + 8 + 3 * LINE, spacing=LINE) + 18

    per_page = 5
    for pg in range(0, len(BEATS), per_page):
        t = b.page("story", "Beat Sheet" + (" (cont.)" if pg else ""),
                   f"Blake Snyder's Save the Cat! beats, scaled to {target} pages." if not pg else None,
                   key=f"story-beats-{pg // per_page}", outline="Beat sheet" if not pg else None)
        for i, (name, a, z, desc) in enumerate(BEATS[pg:pg + per_page], start=pg + 1):
            b.text(ML, t, f"{i}.", "Helvetica-Bold", 9, MID)
            b.text(ML + 16, t, name.upper(), "Helvetica-Bold", 9)
            b.text(W - MR, t, page_range(a, z, target), "Helvetica-Bold", 8, DARK, align="right")
            b.text(ML + 16, t + 12, desc, "Helvetica-Oblique", 7.5, MID)
            t = b.rules(t + 12, t + 12 + 3 * LINE) + 16

    t = b.page("story", "Eight Sequences",
               "Break the script into eight ~15-minute movements, each with its own mini-goal and turn.",
               key="story-sequences", outline="Eight sequences")
    block_h = (H - BOTTOM - t) / len(SEQUENCES)
    for i, (name, desc) in enumerate(SEQUENCES):
        top = t
        a, z = round(i * target / 8) + 1, round((i + 1) * target / 8)
        b.text(ML, t, f"SEQ {i + 1}  ·  {name.upper()}", "Helvetica-Bold", 8.5)
        b.text(W - MR, t, f"pp. {a}–{z}", "Helvetica", 7.5, MID, align="right")
        b.text(ML, t + 11, desc, "Helvetica-Oblique", 7.5, MID)
        b.rules(t + 11, top + block_h - 12, spacing=(block_h - 25) / 2)
        t = top + block_h


def plot_tracker(b: Book):
    t = b.page("story", "Plot & Subplot Tracker",
               "Where does each storyline advance? One row per plot; note its beat in each act.",
               key="story-plots", outline="Plot & subplot tracker")
    cols = [("Plot", 70), ("Act I", None), ("Act IIA", None), ("Act IIB", None), ("Act III", None)]
    rows = 6
    row_h = (H - BOTTOM - t - 30) / rows
    t = b.table(t, cols, rows, row_h=row_h)
    for r, label in enumerate(["A", "B", "C", "D", "E", "F"]):
        b.text(ML + 4, t - 12 - (rows - r) * row_h + 16, label, "Helvetica-Bold", 14, FAINT)


def tv_story_pages(b: Book):
    fmt = b.opts.tv_format
    t = b.page("story", "Series Bible", "The pitch document for the whole show.",
               key="story-bible", outline="Series bible")
    t = b.field(t, "Series logline", 2)
    b.text(ML, t, "FORMAT", "Helvetica-Bold", 7.5)
    x = ML + 50
    for label in ("Half-hour", "Hour", "Single-cam", "Multi-cam", "Serialized", "Procedural", "Hybrid"):
        x = b.checkbox(x, t, label)
    t += 22
    t = b.field(t, "The world", 3, "Where and when; what makes it specific")
    t = b.field(t, "The engine", 3, "What generates a new story every episode?")
    t = b.field(t, "Tone & comparable shows", 2)
    t = b.field(t, "Themes", 2)
    t = b.field(t, "The pilot", 2, "What happens, and how does it set up the series?")
    t = b.field(t, "Where could seasons 2–5 go?", 2)

    t = b.page("story", "Season Arc", "The story of the season, from pilot to finale.",
               key="story-season", outline="Season arc")
    t = b.field(t, "Where the season starts", 2)
    t = b.field(t, "Midseason turn", 2)
    t = b.field(t, "Where the season ends (and what's left open)", 2)
    b.text(ML, t, "MAIN CAST ARCS", "Helvetica-Bold", 7.5)
    t = b.table(t + 12, [("Character", 90), ("Starts as", None), ("Ends as", None)], 7)

    t = b.page("story", "Episode Grid", "One row per episode: the A, B and C stories and the arc beat.",
               key="story-grid", outline="Episode grid")
    rows = 10
    row_h = (H - BOTTOM - t - 20) / rows
    b.table(t, [("Ep", 22), ("Title", 70), ("A story", None), ("B / C story", None), ("Arc beat", 80)], rows,
            row_h=row_h)
    for r in range(rows):
        b.text(ML + 11, t + 5 + r * row_h + row_h / 2 + 3, str(r + 1), "Helvetica-Bold", 9, MID, align="center")

    acts = TV_STRUCTURE[fmt]
    per_page = 4 if fmt == "half-hour" else 3
    label = "Half-hour" if fmt == "half-hour" else "One-hour"
    for ep in range(1, b.opts.episodes + 1):
        for pg in range(0, len(acts), per_page):
            first = pg == 0
            t = b.page("story", f"Episode Structure" + ("" if first else " (cont.)"),
                       f"{label} episode, ~{acts[-1][2]} pages. End each act on a question the audience "
                       "must stay through the break to answer." if first else None,
                       key=f"story-ep{ep}-{pg}", outline=f"Episode structure {ep}" if first else None)
            if first:
                b.field(t, "Episode #", 1, x2=ML + 80)
                t = b.field(t, "Title", 1, x1=ML + 96)
            chunk = acts[pg:pg + per_page]
            block_h = (H - BOTTOM - t) / len(chunk)
            spacing = min(30, (block_h - 22) / 4)
            for name, a, z in chunk:
                top = t
                b.text(ML, t, name.upper(), "Helvetica-Bold", 9)
                b.text(W - MR, t, f"pp. {a}\u2013{z}", "Helvetica", 7.5, MID, align="right")
                t += 4
                for tag in ("A", "B", "C", "Act out:"):
                    t += spacing
                    if len(tag) == 1:
                        b.text(ML, t - 3, tag, "Helvetica-Bold", 7.5, MID)
                        b.hline(ML + 12, W - MR, t)
                    else:
                        b.text(ML, t - 3, tag, "Helvetica-Oblique", 7.5, MID)
                        b.hline(ML + 40, W - MR, t)
                t = top + block_h


# ---------------------------------------------------------------- cast

def cast_pages(b: Book):
    t = b.page("cast", "Cast List", "Everyone with a line. Link each name to a profile page.",
               key="cast-roster", outline="Cast list")
    rows = int((H - BOTTOM - t - 20) // LINE)
    b.table(t, [("Name", 100), ("Role", 70), ("In one line", None), ("1st pg", 36)], rows)

    for n in range(1, b.opts.characters + 1):
        t = b.page("cast", f"Character {n}", key=f"cast-{n}", outline=f"Character {n}", level=1)
        b.field(t, "Name", 1, x2=W - MR - 150)
        t = b.field(t, "Age", 1, x1=W - MR - 140)
        x = ML
        for label in ("Protagonist", "Antagonist", "Ally", "Mentor", "Love interest", "Other"):
            x = b.checkbox(x, t, label)
        t += 26
        spacing = 19
        for label, lines, hint in [
            ("Look & first impression", 2, "How we meet them on the page"),
            ("Want", 2, "Their conscious, external goal"),
            ("Need", 2, "What they must learn or accept"),
            ("Wound", 1, "The past event that shaped them"),
            ("Flaw & the lie they believe", 2, None),
            ("Arc", 2, "Who they are on page 1 → who they are at the end"),
            ("Voice", 2, "Vocabulary, rhythm, what they never say"),
            ("Secret", 1, None),
            ("Key relationships", 2, None),
        ]:
            t = b.field(t, label, lines, hint, spacing=spacing)

    t = b.page("cast", "Relationship Map",
               "Put your protagonist in the middle. Draw lines to others; label each with the conflict.",
               key="cast-map", outline="Relationship map")
    for gy in range(int(t) + 6, int(H - BOTTOM), 18):
        for gx in range(int(ML) + 6, int(W - MR), 18):
            b.c.setFillColor(LIGHT)
            b.c.circle(gx, b.y(gy), 0.7, stroke=0, fill=1)
    cy = (t + H - BOTTOM) / 2
    b.c.setStrokeColor(INK)
    b.c.setFillColor(white)
    b.c.setLineWidth(1.2)
    b.c.circle(W / 2, b.y(cy), 36, stroke=1, fill=1)
    b.text(W / 2, cy + 3, "PROTAGONIST", "Helvetica-Bold", 7, MID, align="center")


# ---------------------------------------------------------------- scenes

def scene_card_pages(b: Book):
    cols, rows = 2, 3
    gap = 10
    n = 1
    for pg in range(b.opts.scene_card_pages):
        t = b.page("scenes", "Scene Cards" if not pg else f"Scene Cards · {n}–{n + 5}",
                   "One card per scene. Every scene needs a goal, a conflict and a turn." if not pg else None,
                   key=f"scenes-{pg}", outline="Scene cards" if not pg else None, compact=pg > 0)
        card_w = (W - ML - MR - gap) / cols
        card_h = (H - BOTTOM - t - gap * (rows - 1)) / rows
        for r in range(rows):
            for col in range(cols):
                x = ML + col * (card_w + gap)
                top = t + r * (card_h + gap)
                scene_card(b, x, top, card_w, card_h, n)
                n += 1


def scene_card(b: Book, x, t, w, h, n):
    b.rect(x, t, w, h, stroke=DARK, width=0.8, radius=4)
    pad = 7
    b.text(x + pad, t + 14, f"#{n}", "Helvetica-Bold", 10, LIGHT)
    cx = x + pad + 26
    for label in ("INT", "EXT", "DAY", "NIGHT"):
        cx = b.checkbox(cx, t + 13, label, size=7, font_size=6.5) - 4
    b.text(x + w - pad, t + 14, "pg ___", "Helvetica", 6.5, MID, align="right")
    b.hline(x, x + w, t + 20, LIGHT, 0.5)
    tt = t + 20
    rows = [("Location", 1), ("Who", 1), ("Goal", 1), ("Conflict", 1), ("Turn", 1), ("", 2)]
    spacing = (h - 26) / sum(n for _, n in rows)
    for label, lines in rows:
        for i in range(lines):
            tt += spacing
            if label and i == 0:
                b.text(x + pad, tt - 3, label.upper(), "Helvetica-Bold", 5.5, MID)
            if tt < t + h - 4:
                b.hline(x + pad + (40 if label else 0), x + w - pad, tt, FAINT, 0.5)
    b.text(x + w - pad, t + h - 5, "+ / –", "Helvetica", 6.5, LIGHT, align="right")


def locations_page(b: Book):
    t = b.page("scenes", "Locations", "Every set the script needs. Fewer locations = a cheaper shoot.",
               key="scenes-locations", outline="Locations")
    rows = int((H - BOTTOM - t - 20) // LINE)
    b.table(t, [("Location", 130), ("INT/EXT", 48), ("Scenes", 60), ("Notes", None)], rows)


# ---------------------------------------------------------------- outline

def outline_pages(b: Book):
    n = 1
    for pg in range(b.opts.outline_pages):
        t = b.page("outline", "Step Outline" if not pg else "Step Outline (cont.)",
                   "Scene by scene, in order. What happens, and why does the story need it?" if not pg else None,
                   key=f"outline-{pg}", outline="Step outline" if not pg else None, compact=pg > 0)
        row_h = 2 * LINE
        rows = int((H - BOTTOM - t - 20) // row_h)
        b.table(t, [("#", 24), ("Scene heading", 130), ("What happens / purpose", None), ("pg", 28)], rows,
                row_h=row_h)
        for r in range(rows):
            rt = t + 5 + r * row_h
            b.hline(ML + 154, W - MR - 28, rt + LINE, FAINT, 0.5)
            b.text(ML + 12, rt + LINE + 3, str(n), "Helvetica", 7.5, LIGHT, align="center")
            n += 1


# ---------------------------------------------------------------- script

# Screenplay element positions in inches on an 8.5" page, and their guide labels.
GUIDES = [(1.5, "ACTION", 0), (2.5, "DIALOGUE", 1), (3.1, "(paren)", 0), (3.7, "CHARACTER", 1)]


def script_pages(b: Book):
    # Map the 1.5"-7.5" text area of a typed page onto the full writable width.
    def gx(inches):
        return ML + (inches - 1.5) / 6.0 * (W - ML - MR)

    for pg in range(b.opts.script_pages):
        t = b.page("script", f"Script \u00b7 page {pg + 1}", key=f"script-{pg}",
                   outline="Script pages" if pg == 0 else None, compact=True)
        b.text(W - MR, t - 6, "draft pg ______", "Helvetica", 7, MID, align="right")
        t += 8
        for inches, label, row in GUIDES:
            x = gx(inches)
            b.vline(x, t + 9 * row, H - BOTTOM, FAINT, 0.6, dash=(2, 3))
            b.text(x + 2, t + 6 + 9 * row, label, "Helvetica", 5.5, MID)
        b.rules(t + 12, H - BOTTOM)


# ---------------------------------------------------------------- notes

def notes_pages(b: Book):
    passes = REWRITE_PASSES
    split = 3
    for pg, chunk in enumerate((passes[:split], passes[split:])):
        t = b.page("notes", "Rewrite Checklist" + (" (cont.)" if pg else ""),
                   "Do one pass per focus. Tick each item when the draft passes." if not pg else None,
                   key=f"notes-rewrite-{pg}", outline="Rewrite checklist" if not pg else None)
        for name, items in chunk:
            b.text(ML, t, f"{name.upper()} PASS", "Helvetica-Bold", 9)
            t += 18
            for item in items:
                b.checkbox(ML + 4, t, None, size=9)
                b.text(ML + 20, t, item, "Helvetica", 8.5, DARK)
                t += 18
            t += 10
        b.text(ML, t, "NOTES", "Helvetica-Bold", 7.5)
        b.rules(t, H - BOTTOM)

    t = b.page("notes", "Feedback Log", "Notes from readers. Look for patterns before you act on one.",
               key="notes-feedback", outline="Feedback log")
    row_h = 2 * LINE
    rows = int((H - BOTTOM - t - 20) // row_h)
    t2 = b.table(t, [("From / date", 80), ("Note", None), ("What I'll do", 110), ("✓", 20)], rows,
                 row_h=row_h)

    for pg in range(b.opts.notes_pages):
        t = b.page("notes", "Notes", key=f"notes-{pg}", outline="Notes pages" if not pg else None, compact=True)
        b.rules(t, H - BOTTOM)


# ---------------------------------------------------------------- guide

def guide_pages(b: Book):
    t = b.page("guide", "How to Use This Workbook", "A path from idea to finished draft.",
               key="guide-start", outline="How to use")
    steps = [
        ("Idea", "idea", "Nail the logline first. If you can't say it in one sentence, the story isn't clear yet."),
        ("Story", "story", "Map the structure: acts, beats and sequences (film) or bible, arc and episodes (TV)."),
        ("Cast", "cast", "Give every major character a want, a need and a flaw that puts them in conflict."),
        ("Scenes", "scenes", "Brainstorm scenes on cards. Each needs a goal, a conflict and a turn."),
        ("Outline", "outline", "Put the cards in order as a step outline. Fix structure here, not in pages."),
        ("Script", "script", "Draft by hand on the guided pages: dotted lines mark where action, dialogue, "
         "parentheticals and character names start. Or type in Fountain and format it with the app."),
        ("Notes", "notes", "Rewrite in passes, and log feedback from readers."),
    ]
    for i, (name, sid, desc) in enumerate(steps, start=1):
        b.rect(ML, t, 22, 22, stroke=INK, fill=INK, radius=11)
        b.text(ML + 11, t + 15, str(i), "Helvetica-Bold", 10, white, align="center")
        b.text(ML + 32, t + 9, name.upper(), "Helvetica-Bold", 9.5)
        b.para(ML + 32, t + 21, W - ML - MR - 40, desc, "Helvetica", 8.5, DARK, 11)
        b.link(sid, ML, t, W - ML - MR, 30)
        t += 50
    t += 4
    b.text(ML, t, "TIPS FOR THE REMARKABLE", "Helvetica-Bold", 8)
    t += 14
    for tip in [
        "Links work when you tap with a finger or the pen tip. Close the side toolbar for the full page.",
        "Use the fineliner or ballpoint at the thinnest size so handwriting fits between the lines.",
        "Duplicate the PDF before you start a new project, and keep this copy as a blank template.",
        "Rename the PDF to your script's title so it's easy to find in My Files.",
    ]:
        t = b.para(ML + 8, t, W - ML - MR - 8, "•  " + tip, "Helvetica", 8, DARK, 11) + 3

    t = b.page("guide", "Screenplay Format", "The industry-standard page. Readers judge format in seconds.",
               key="guide-format", outline="Screenplay format")
    # Mini page sample.
    sw = W - ML - MR
    sh = 158
    b.rect(ML, t, sw, sh, stroke=LIGHT)
    k = sw / 8.5  # points per inch in the sample
    lh = 9.4
    sample = [
        (1.5, "INT. DINER - NIGHT", "left"),
        None,
        (1.5, "Rain streaks the windows. MAYA (30s, tired eyes)", "left"),
        (1.5, "slides into a booth across from OTIS.", "left"),
        None,
        (3.7, "MAYA", "left"),
        (3.1, "(quietly)", "left"),
        (2.5, "You said you'd come alone.", "left"),
        None,
        (3.7, "OTIS", "left"),
        (2.5, "I did. They followed me.", "left"),
        None,
        (7.5, "CUT TO:", "right"),
    ]
    tt = t + 22
    for item in sample:
        if item:
            x, s, align = item
            b.text(ML + x * k, tt, s, "Courier", 7.6, INK, align=align)
        tt += lh
    labels = [(0, "Scene heading"), (2, "Action"), (5, "Character"), (6, "Parenthetical"), (7, "Dialogue"),
              (12, "Transition")]
    for idx, lab in labels:
        b.text(ML + 0.2 * k, t + 22 + idx * lh, lab, "Helvetica-Oblique", 5.5, MID)
    t += sh + 16
    t = b.table(t, [("Element", 80), ("From left", 52), ("Width", 42), ("Rules", None)], 0)
    t -= 12
    spec = [
        ("Scene heading", '1.5"', '6.0"', "INT./EXT. LOCATION - DAY/NIGHT, all caps."),
        ("Action", '1.5"', '6.0"', "Present tense; what we see and hear. ≤ 4 lines."),
        ("Character", '3.7"', "—", "CAPS. Add (V.O.), (O.S.) or (CONT'D) as needed."),
        ("Parenthetical", '3.1"', '2.0"', "Brief, lowercase; use sparingly."),
        ("Dialogue", '2.5"', '3.5"', "What they say. No quotation marks."),
        ("Transition", "right", "—", "CUT TO:, ends at 7.5\". Rarely needed."),
    ]
    for row in spec:
        t += 14
        widths = [80, 52, 42]
        x = ML
        for i, cell in enumerate(row):
            b.text(x + 3, t, cell, "Helvetica-Bold" if i == 0 else "Helvetica", 7.5, DARK)
            x += widths[i] if i < 3 else 0
        b.hline(ML, W - MR, t + 5, FAINT, 0.5)
    t += 20
    for line in [
        "Page: US Letter, Courier 12 pt, 1\" top & bottom, 1.5\" left, 1\" right. Page numbers top right.",
        "One page ≈ one minute of screen time.",
        "Introduce speaking characters in CAPS with an age the first time they appear.",
        "Write only what the camera can see or the microphone can hear.",
    ]:
        t = b.para(ML, t, W - ML - MR, "•  " + line, "Helvetica", 8, DARK, 11) + 2

    t = b.page("guide", "Typing Your Draft: Fountain",
               "Fountain is plain text that the Script Writer app formats into a proper screenplay PDF.",
               key="guide-fountain", outline="Fountain cheat sheet")
    rows = [
        ("Scene heading", "INT. DINER - NIGHT", "Starts with INT, EXT, EST or I/E, or force with ."),
        ("Action", "Maya slides into the booth.", "Any ordinary paragraph."),
        ("Character", "MAYA", "A line in CAPS, then dialogue on the next line."),
        ("Extension", "MAYA (V.O.)", "V.O., O.S., CONT'D in parentheses."),
        ("Parenthetical", "(quietly)", "On its own line under the character."),
        ("Dialogue", "You said you'd come alone.", "Lines right after the character."),
        ("Transition", "CUT TO:", "Caps, ending in TO:, or force with >."),
        ("Centered", "> THE END <", "Wrap in > and <."),
        ("Lyrics", "~Oh the last train north", "Start the line with ~."),
        ("Emphasis", "*italic* **bold** _underline_", ""),
        ("Section", "# ACT ONE", "Not printed; becomes a bookmark."),
        ("Synopsis", "= Maya finds the ticket.", "Not printed; shows in the stats report."),
        ("Note", "[[check this later]]", "Not printed."),
        ("Page break", "===", ""),
        ("Title page", "Title: My Script", "Key: value lines at the top of the file."),
    ]
    for name, example, note in rows:
        b.text(ML, t, name.upper(), "Helvetica-Bold", 7)
        b.text(ML + 80, t, example, "Courier", 8.5, INK)
        if note:
            b.text(ML + 80, t + 11, note, "Helvetica-Oblique", 7, MID)
        b.hline(ML, W - MR, t + 17, FAINT, 0.5)
        t += 28

    t = b.page("guide", "Length & Format Reference", "Typical page counts. One page ≈ one minute.",
               key="guide-lengths", outline="Lengths")
    t = b.table(t, [("Type", 170), ("Pages", 70), ("Notes", None)], 0) - 12
    for kind, pages, note in [
        ("Feature: comedy / horror", "90–105", "Shorter, faster."),
        ("Feature: drama / thriller", "100–120", ""),
        ("Feature: epic", "120–135", "Harder to sell from a new writer."),
        ("Half-hour: single-camera", "25–35", "Film-style format."),
        ("Half-hour: multi-camera", "45–55", "Double-spaced dialogue; caps action."),
        ("One-hour drama", "50–65", "Teaser + 4–5 acts, or no act breaks for streaming."),
        ("Short film", "5–15", "Festivals favor under 15 minutes."),
    ]:
        t += 16
        b.text(ML + 3, t, kind, "Helvetica-Bold", 8, DARK)
        b.text(ML + 173, t, pages, "Helvetica", 8, DARK)
        b.text(ML + 243, t, note, "Helvetica", 7.5, MID)
        b.hline(ML, W - MR, t + 6, FAINT, 0.5)
    t += 34
    b.text(ML, t, "WORDS TO KNOW", "Helvetica-Bold", 8)
    t += 14
    for term, meaning in [
        ("Spec script", "Written on your own to sell or show, not commissioned. No camera directions or scene numbers."),
        ("Shooting script", "Production draft with scene numbers, revision colors and locked pages."),
        ("Slugline", "Another name for the scene heading."),
        ("Beat", "A single story moment, or (in dialogue) a pause: \"A beat.\""),
        ("Button", "A strong last line or image that ends a scene."),
        ("Cold open / teaser", "The pre-credits scene of a TV episode that hooks the audience."),
        ("Act out", "The cliffhanger at the end of a TV act, before a commercial break."),
        ("Bible", "The TV document describing the series: world, characters, arcs and future seasons."),
    ]:
        b.text(ML, t, term, "Helvetica-Bold", 8)
        t = b.para(ML + 90, t, W - ML - MR - 90, meaning, "Helvetica", 8, DARK, 10.5) + 4


# ================================================================ assembly

def build(path, opts: Options):
    b = Book(path, opts)
    film = opts.mode in ("film", "both")
    tv = opts.mode in ("tv", "both")

    chips_cast = [(str(n), f"cast-{n}") for n in range(1, opts.characters + 1)]
    chips_cards = [(f"{p * 6 + 1}", f"scenes-{p}") for p in range(opts.scene_card_pages)]
    chips_script = [(str(p + 1), f"script-{p}") for p in range(0, opts.script_pages, 5)]
    story_chips = []
    if film:
        story_chips += [("Acts", "story-acts"), ("Beats", "story-beats-0"), ("Seq", "story-sequences")]
    if tv:
        story_chips += [("Bible", "story-bible"), ("Arc", "story-season"), ("Grid", "story-grid")]
        story_chips += [(f"Ep{e}", f"story-ep{e}-0") for e in range(1, opts.episodes + 1)]
    story_chips.append(("Plots", "story-plots"))
    index = [
        ("idea", "Idea", "Logline, premise, theme and synopsis",
         [("Log", "idea-logline"), ("Prem", "idea-premise"), ("Syn", "idea-synopsis")]),
        ("story", "Story", "Structure: " + ("acts, beats and sequences" if film else "")
         + (" · " if film and tv else "") + ("bible, season arc and episodes" if tv else ""), story_chips),
        ("cast", "Cast", "Cast list, character profiles and relationship map",
         [("List", "cast-roster")] + chips_cast + [("Map", "cast-map")]),
        ("scenes", "Scenes", f"{opts.scene_card_pages * 6} scene cards and a locations list",
         chips_cards + [("Loc", "scenes-locations")]),
        ("outline", "Outline", "Step outline: the whole story scene by scene", []),
        ("script", "Script", f"{opts.script_pages} guided pages for drafting by hand (jump by page)",
         chips_script),
        ("notes", "Notes", "Rewrite checklist, feedback log and blank pages",
         [("Rew", "notes-rewrite-0"), ("Fdbk", "notes-feedback"), ("Blank", "notes-0")]),
        ("guide", "Guide", "How to use this, screenplay format, Fountain and lengths",
         [("Start", "guide-start"), ("Fmt", "guide-format"), ("Ftn", "guide-fountain"),
          ("Len", "guide-lengths")]),
    ]

    cover(b)
    home(b, index)
    idea_pages(b)
    if film:
        film_story_pages(b)
    if tv:
        tv_story_pages(b)
    plot_tracker(b)
    cast_pages(b)
    scene_card_pages(b)
    locations_page(b)
    outline_pages(b)
    script_pages(b)
    notes_pages(b)
    guide_pages(b)
    b.save()
    return b.page_no
