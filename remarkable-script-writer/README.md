# reMarkable Script Writer

A movie and TV script-writing assistant for the **reMarkable 2**. It has two parts:

1. **The Screenplay Workbook:** a hyperlinked PDF you write in with the pen. A tab bar on every
   page jumps between sections, so on the tablet it works like an app. It walks you from idea to
   finished draft: logline → structure → characters → scene cards → step outline → script pages →
   rewrite.
2. **The formatter:** type your draft in [Fountain](https://fountain.io) (plain text) on any
   device, and the formatter turns it into an industry-standard screenplay PDF. It also produces a
   stats report: page count, scene list with lengths, and dialogue per character.

The app needs no developer mode, SSH or hacks, so it works on every reMarkable 2 firmware version.

## Quick start: just the PDFs

Ready-made PDFs are in [`dist/`](dist):

| File | Use it for |
|---|---|
| `screenplay-workbook-film.pdf` | Feature films (beat sheet scaled to 110 pages) |
| `screenplay-workbook-tv-hour.pdf` | One-hour TV (teaser + 5 acts) |
| `screenplay-workbook-tv-half-hour.pdf` | Half-hour TV (cold open + 2 acts + tag) |
| `sample-screenplay.pdf` | An example of the formatter's output |

**Put one on your tablet** in any of these ways:

- Drag it into the reMarkable desktop app, or upload it at my.remarkable.com.
- Over USB: turn on *Settings → Storage → USB web interface* on the tablet, then open
  <http://10.11.99.1> in a browser and drag the file in.

Tap the tabs at the top of each page to navigate. Close the side toolbar for the full page.

## What's in the workbook

| Tab | Pages |
|---|---|
| **Home** | Cover (title, draft, contact) and a map with a tappable link to every page |
| **Idea** | Logline builder (protagonist, inciting incident, goal, obstacle, stakes, hook, 3 drafts) · premise & theme (genre, tone, comps, controlling idea, central dramatic question) · one-page synopsis |
| **Story** (film) | Three-act map with tension curve and page targets · 15-beat *Save the Cat!* beat sheet with page targets · eight-sequence breakdown · plot & subplot tracker |
| **Story** (TV) | Series bible (format, world, story engine, pilot, future seasons) · season arc with cast arcs · 10-episode grid (A/B/C stories, arc beats) · episode structure worksheets with act-out prompts · plot & subplot tracker |
| **Cast** | Cast list · character profiles (want, need, wound, flaw & lie, arc, voice, secret, relationships) · relationship map |
| **Scenes** | Scene cards, 6 per page (INT/EXT, DAY/NIGHT, location, who, goal, conflict, turn +/–) · locations list |
| **Outline** | Numbered step outline |
| **Script** | Handwriting pages with guides at the standard screenplay indents for action, dialogue, parentheticals and character names |
| **Notes** | Rewrite checklist (structure, character, scene, dialogue and polish passes) · feedback log · blank pages |
| **Guide** | How to use the workbook · screenplay format reference · Fountain cheat sheet · length reference and glossary |

The PDF also has a table of contents, which the tablet shows in its document menu.

## The command-line tool

It needs Python 3.8+ and ReportLab:

```sh
pip install -r requirements.txt
```

### Build a custom workbook

```sh
python3 -m scriptwriter workbook --mode film --title "The Last Train North" --pages 100
python3 -m scriptwriter workbook --mode tv --tv-format half-hour --episodes 6
python3 -m scriptwriter workbook --mode both --characters 12 --script-pages 60
```

| Option | Default | Meaning |
|---|---|---|
| `--mode film\|tv\|both` | `film` | Which structure pages to include |
| `--title` | – | Prints your project title on every page |
| `--pages` | 110 | Target feature length; beat and act page targets scale to it |
| `--tv-format hour\|half-hour` | `hour` | Act structure for the TV pages |
| `--episodes` | 3 | Number of episode structure worksheets |
| `--characters` | 8 | Number of character profile pages |
| `--scene-card-pages` | 10 | Scene card pages (6 cards each) |
| `--outline-pages` | 5 | Step outline pages |
| `--script-pages` | 40 | Handwriting script pages |
| `--notes-pages` | 10 | Blank notes pages |
| `-o` | `screenplay-workbook-<mode>.pdf` | Output file |

### Start a typed script

```sh
python3 -m scriptwriter new my-film.fountain --title "My Film" --author "Your Name"
python3 -m scriptwriter new pilot.fountain --mode tv --tv-format hour
```

This writes a Fountain file with a title page, plus the structure as unprinted `#` sections and
`=` synopses: the beats with page targets for film, or the acts for TV. They act as signposts
while you draft, and they become bookmarks in the formatted PDF.

### Format it as a screenplay

```sh
python3 -m scriptwriter format my-film.fountain              # writes my-film.pdf
python3 -m scriptwriter format my-film.fountain --scene-numbers
```

The output follows industry format:

- US Letter, Courier 12 pt, 1.5" left margin, and page numbers top right.
- Scene headings are kept with the lines that follow them.
- A speech that runs over a page break gets `(MORE)` / `(CONT'D)`.
- Bold, italic and underline are supported.
- The title page is built from the Fountain header.
- Every section and scene gets a bookmark.

Put the PDF on the reMarkable to read and mark up the draft with the pen.

### Get a report

```sh
python3 -m scriptwriter stats my-film.fountain
```

```
Pages: 3  (about 3 minutes of screen time)
Scenes: 6
Interior/exterior: INT 2, EXT 3, OTHER 1

SCENES
   #   pg  length  heading
   1    1     4/8  EXT. RAIL YARD - NIGHT
   ...
DIALOGUE
character                speeches   words  scenes
MAYA                            7     115       3
```

Scene lengths are in eighths of a page, the unit used for scheduling a shoot.

## A suggested workflow

1. Plan with the pen in the workbook: Idea → Story → Cast → Scenes → Outline.
2. Draft either by hand on the Script pages, or by typing Fountain in any text editor. With a
   reMarkable Type Folio you can also type on the tablet.
3. Run `format` and put the PDF on the tablet. Read it and mark it up with the pen.
4. Rewrite using the Notes → Rewrite Checklist, and log readers' notes in the Feedback Log.

## Development

```sh
python3 -m unittest discover -s tests   # link checks also need: pip install pymupdf
./build.sh                              # rebuild the PDFs in dist/
```

Code layout: `scriptwriter/fountain.py` (parser), `scriptwriter/screenplay.py` (layout,
pagination, PDF, stats), `scriptwriter/workbook.py` (workbook pages), and
`scriptwriter/__main__.py` (command-line interface).

Pages are 468 × 624 pt, the same 3:4 ratio as the reMarkable 2's 1404 × 1872 screen, and
everything is black and grey for e-ink.

The Fountain support covers scene headings (including forced `.` headings and `#1#` numbers),
action, characters (`@` forced, `^` dual), parentheticals, dialogue, lyrics, transitions,
centered text, page breaks, sections, synopses, notes, boneyard, emphasis and title pages.
Dual dialogue is printed one speech after the other rather than side by side.
