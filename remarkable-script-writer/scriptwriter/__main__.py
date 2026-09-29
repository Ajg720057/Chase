"""reMarkable Script Writer command-line interface.

    python -m scriptwriter workbook -o workbook.pdf --mode film
    python -m scriptwriter new my-film.fountain --title "My Film" --author "Me"
    python -m scriptwriter format my-film.fountain -o my-film.pdf
    python -m scriptwriter stats my-film.fountain
"""

import argparse
import os
import sys
from datetime import date

from . import fountain, screenplay, workbook
from .workbook import BEATS, TV_STRUCTURE, scale


def cmd_workbook(args):
    opts = workbook.Options(
        mode=args.mode,
        title=args.title or "",
        target_pages=args.pages,
        tv_format=args.tv_format,
        episodes=args.episodes,
        characters=args.characters,
        scene_card_pages=args.scene_card_pages,
        outline_pages=args.outline_pages,
        script_pages=args.script_pages,
        notes_pages=args.notes_pages,
    )
    out = args.output or f"screenplay-workbook-{args.mode}.pdf"
    pages = workbook.build(out, opts)
    print(f"Wrote {out} ({pages} pages)")


OPENING = """FADE IN:

EXT. LOCATION - DAY

Write what we see.

CHARACTER
What they say.
"""

TV_NOTES = {
    "Cold Open": "Hook the audience before the titles: a question, a laugh or a shock.",
    "Teaser": "Hook the audience before the titles: a question, a laugh or a shock.",
    "Tag": "A last laugh or twist that sends us out wanting the next episode.",
}


def film_skeleton(target):
    acts = [("ACT ONE", 0, 6), ("ACT TWO", 6, 13), ("ACT THREE", 13, 15)]
    out = []
    for act, a, z in acts:
        out.append(f"# {act}\n")
        for name, p1, p2, desc in BEATS[a:z]:
            pages = f"p. {scale(p1, target)}" if p1 == p2 else f"pp. {scale(p1, target)}-{scale(p2, target)}"
            out.append(f"## {name} ({pages})\n\n= {desc}\n")
            if len(out) == 2:
                out.append(OPENING)
    return "\n".join(out)


def tv_skeleton(fmt):
    out = []
    for name, a, z in TV_STRUCTURE[fmt]:
        note = TV_NOTES.get(name, "End the act on a question that carries us through the break.")
        out.append(f"# {name.upper()} (pp. {a}-{z})\n\n= {note}\n")
        if len(out) == 1:
            out.append(OPENING)
    return "\n".join(out)


def cmd_new(args):
    if os.path.exists(args.file) and not args.force:
        sys.exit(f"{args.file} already exists (use --force to overwrite)")
    today = date.today()
    title = args.title or os.path.splitext(os.path.basename(args.file))[0].replace("-", " ").title()
    body = film_skeleton(args.pages) if args.mode == "film" else tv_skeleton(args.tv_format)
    text = f"""Title: {title}
Credit: Written by
Author: {args.author or "Your Name"}
Draft date: {today:%B} {today.day}, {today.year}
Contact: you@example.com

/*
Structure sections (#) and synopses (=) are not printed. They show up as
bookmarks in the formatted PDF and in the stats report, so leave them in as
signposts while you draft. Delete this note whenever you like.
*/

{body}"""
    with open(args.file, "w") as f:
        f.write(text)
    print(f"Wrote {args.file}")


def _read(path):
    with open(path, encoding="utf-8-sig") as f:
        return fountain.parse(f.read())


def cmd_format(args):
    script = _read(args.file)
    out = args.output or os.path.splitext(args.file)[0] + ".pdf"
    lay = screenplay.render(script, out, scene_numbers=args.scene_numbers, title_page=not args.no_title_page)
    print(f"Wrote {out} ({len(lay.pages)} script pages, {len(lay.scenes)} scenes)")


def cmd_stats(args):
    print(screenplay.report(_read(args.file), scene_numbers=True))


def main(argv=None):
    p = argparse.ArgumentParser(prog="scriptwriter", description="Screenplay tools for the reMarkable 2.")
    sub = p.add_subparsers(dest="command", required=True)

    w = sub.add_parser("workbook", help="generate the hyperlinked handwriting workbook PDF")
    w.add_argument("-o", "--output")
    w.add_argument("--mode", choices=["film", "tv", "both"], default="film")
    w.add_argument("--title", help="print your project's title on every page")
    w.add_argument("--pages", type=int, default=110, help="target feature length for beat pages (default 110)")
    w.add_argument("--tv-format", choices=["hour", "half-hour"], default="hour")
    w.add_argument("--episodes", type=int, default=3, help="episode structure worksheets (TV)")
    w.add_argument("--characters", type=int, default=8, help="character profile pages")
    w.add_argument("--scene-card-pages", type=int, default=10, help="pages of scene cards (6 per page)")
    w.add_argument("--outline-pages", type=int, default=5)
    w.add_argument("--script-pages", type=int, default=40, help="guided handwriting script pages")
    w.add_argument("--notes-pages", type=int, default=10)
    w.set_defaults(func=cmd_workbook)

    n = sub.add_parser("new", help="start a Fountain script pre-filled with structure signposts")
    n.add_argument("file")
    n.add_argument("--title")
    n.add_argument("--author")
    n.add_argument("--mode", choices=["film", "tv"], default="film")
    n.add_argument("--pages", type=int, default=110)
    n.add_argument("--tv-format", choices=["hour", "half-hour"], default="hour")
    n.add_argument("--force", action="store_true")
    n.set_defaults(func=cmd_new)

    f = sub.add_parser("format", help="format a Fountain script as an industry-standard screenplay PDF")
    f.add_argument("file")
    f.add_argument("-o", "--output")
    f.add_argument("--scene-numbers", action="store_true", help="print scene numbers (shooting script)")
    f.add_argument("--no-title-page", action="store_true")
    f.set_defaults(func=cmd_format)

    s = sub.add_parser("stats", help="page count, scene list with lengths, and dialogue per character")
    s.add_argument("file")
    s.set_defaults(func=cmd_stats)

    args = p.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()
