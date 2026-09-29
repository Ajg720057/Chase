"""A small parser for Fountain, the plain-text screenplay markup (https://fountain.io).

It turns a .fountain file into a title-page dict and a flat list of Elements
that the screenplay renderer and the stats report consume.
"""

import re
from dataclasses import dataclass, field

SCENE_RE = re.compile(r"^(INT\.?/EXT|INT/EXT|I/E|INT|EXT|EST)[\.\s]", re.IGNORECASE)
SCENE_NUMBER_RE = re.compile(r"\s*#([\w.\-]+)#\s*$")
TITLE_KEY_RE = re.compile(r"^([A-Za-z][A-Za-z ]*):\s*(.*)$")
PAGE_BREAK_RE = re.compile(r"^={3,}\s*$")


@dataclass
class Element:
    # One of: scene_heading, action, character, parenthetical, dialogue, lyrics,
    # transition, centered, page_break, section, synopsis
    type: str
    text: str = ""
    scene_number: str = ""
    level: int = 0  # section depth (number of #)
    dual: bool = False  # character line marked with ^ (dual dialogue)


@dataclass
class Script:
    title_page: dict = field(default_factory=dict)
    elements: list = field(default_factory=list)

    @property
    def title(self):
        return strip_emphasis(self.title_page.get("title", "")).replace("\n", " ").strip()


def strip_emphasis(text):
    return re.sub(r"(\*{1,3}|_)(?=\S)(.+?)(?<=\S)\1", r"\2", text)


def _strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)  # boneyard
    text = re.sub(r"\[\[.*?\]\]", "", text, flags=re.DOTALL)  # notes
    return text


def _is_character(line):
    if line.startswith("@"):
        return True
    name = re.sub(r"\(.*?\)", "", line).rstrip("^ ").strip()
    return (
        bool(name)
        and any(ch.isalpha() for ch in name)
        and name == name.upper()
        and not name.startswith("!")
    )


def _parse_title_page(lines):
    """Return (title_page_dict, index of first body line)."""
    i = 0
    while i < len(lines) and not lines[i].strip():
        i += 1
    if i >= len(lines) or not TITLE_KEY_RE.match(lines[i]):
        return {}, 0
    title, key = {}, None
    while i < len(lines) and lines[i].strip():
        line = lines[i]
        m = TITLE_KEY_RE.match(line)
        if m and not line[0].isspace():
            key = m.group(1).strip().lower()
            title[key] = [m.group(2).strip()] if m.group(2).strip() else []
        elif key is not None:
            title[key].append(line.strip())
        i += 1
    return {k: "\n".join(v) for k, v in title.items()}, i


def parse(text):
    text = _strip_comments(text.replace("\r\n", "\n").replace("\t", "    "))
    lines = text.split("\n")
    title_page, start = _parse_title_page(lines)
    lines = lines[start:]
    n = len(lines)

    out = []
    in_dialogue = False
    last_action_line = -2  # source index of the last line appended to an action

    def blank(idx):
        return idx < 0 or idx >= n or not lines[idx].strip()

    def add_action(idx, text):
        nonlocal last_action_line
        if last_action_line == idx - 1 and out and out[-1].type == "action":
            out[-1].text += "\n" + text
        else:
            out.append(Element("action", text))
        last_action_line = idx

    for i, raw in enumerate(lines):
        line = raw.strip()

        if not line:
            # Two spaces on an otherwise empty line keep a dialogue block open.
            if in_dialogue and raw.startswith("  "):
                out.append(Element("dialogue", ""))
                continue
            in_dialogue = False
            continue

        if in_dialogue:
            if line.startswith("(") and line.endswith(")"):
                out.append(Element("parenthetical", line))
            elif line.startswith("~"):
                out.append(Element("lyrics", line[1:].strip()))
            else:
                out.append(Element("dialogue", line))
            continue

        prev_blank, next_blank = blank(i - 1), blank(i + 1)

        if PAGE_BREAK_RE.match(line):
            out.append(Element("page_break"))
        elif line.startswith("#"):
            depth = len(line) - len(line.lstrip("#"))
            out.append(Element("section", line[depth:].strip(), level=depth))
        elif line.startswith("=") and not line.startswith("=="):
            out.append(Element("synopsis", line[1:].strip()))
        elif line.startswith("!"):
            add_action(i, raw.lstrip()[1:])
        elif line.startswith(">") and line.endswith("<"):
            out.append(Element("centered", line[1:-1].strip()))
        elif line.startswith(">"):
            out.append(Element("transition", line[1:].strip().upper()))
        elif line.startswith("~"):
            out.append(Element("lyrics", line[1:].strip()))
        elif prev_blank and (
            (line.startswith(".") and len(line) > 1 and line[1] != ".") or SCENE_RE.match(line)
        ):
            heading = line[1:] if line.startswith(".") else line
            number = ""
            m = SCENE_NUMBER_RE.search(heading)
            if m:
                number = m.group(1)
                heading = heading[: m.start()]
            out.append(Element("scene_heading", heading.strip().upper(), scene_number=number))
        elif prev_blank and next_blank and line.isupper() and line.endswith("TO:"):
            out.append(Element("transition", line))
        elif prev_blank and not next_blank and _is_character(line):
            name = line[1:] if line.startswith("@") else line
            dual = name.endswith("^")
            out.append(Element("character", name.rstrip("^").strip(), dual=dual))
            in_dialogue = True
        else:
            add_action(i, raw.rstrip())

    return Script(title_page, out)
