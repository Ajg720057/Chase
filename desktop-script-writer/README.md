# Draftroom

A screenwriting app for your computer: the desktop version of the reMarkable Script Writer. You
type straight into a formatted script page, with the story-planning tools and writing prompts
built in around it.

## Two ways to use it

- **From its claude.ai link.** Open it in your browser. Projects save to your account as you
  type, and the **Assistant** can give you AI notes on your scenes, loglines, structure and
  characters.
- **As a file on your computer.** Download [`dist/Draftroom.html`](dist/Draftroom.html), save it
  somewhere permanent (on Windows, your Documents folder), and double-click it. It opens in Edge or
  Chrome. It opens in your browser and works offline. Projects save in that browser,
  and the Assistant falls back to writing prompts (it has no AI notes). PDF export needs an
  internet connection, to load its PDF library; offline, use **Export → Print…** and pick
  *Microsoft Print to PDF* on Windows. Keep opening the file from the same place in the same
  browser, because that browser holds your saved projects.

## What's inside

| Section | What you do there |
|---|---|
| **Overview** | Title page details, a *next step* suggestion, page count vs. target, beat timeline, dialogue share per character, interior/exterior split, project list |
| **Idea** | Logline builder that assembles your answers into a sentence live, three logline drafts, premise & theme, one-page synopsis with a word count |
| **Structure** | Film: 15-beat *Save the Cat!* sheet with page targets scaled to your length and a "your draft is here" marker, three-act map, eight sequences, subplot grid. TV: series bible, season arc and 10-episode grid, act-by-act episode structure with act outs |
| **Cast** | Character profiles (want, need, wound, flaw, arc, voice…), live stats from the script (speeches, words, scenes, first page), a sample of how each character sounds, one-click profiles for characters who speak but have none |
| **Scene board** | Every scene in the script as an index card with goal, conflict and turn (+ → – / – → +). Drag cards (or use the arrows) to reorder, and the script moves with them. There's also an idea pile for scenes you might use |
| **Script** | The editor (below), plus a scene list, and a companion panel showing your page, current beat and its question, the scene's card, script notes for that scene, and *Stuck?* prompts |
| **Rewrite** | Rewrite checklist in five passes, automatic script notes (long action paragraphs, long speeches, "we see", camera directions, adverb parentheticals, characters speaking before they're introduced, missing INT./EXT. or time of day, page count over target), feedback log, free notes |
| **Guide** | Shortcuts, screenplay format rules, Fountain, typical lengths and a glossary |

### The editor

- **Enter** starts the likely next element: scene heading → action, character → dialogue,
  parenthetical → dialogue, dialogue → action. Enter on an empty line turns it back into action.
- **Tab / Shift+Tab** changes the current element; **Alt + 1–7** (⌥ + 1–7 on a Mac) sets one directly.
- Typing `INT.` or `EXT.` makes a scene heading. Locations you've used are suggested, then times
  of day after ` - `. Character names are suggested from the script and your cast list, and
  typing `(` after a name offers V.O., O.S. and CONT'D.
- Parentheticals close themselves. **Ctrl + B / I / U** adds bold, italic or underline.
  **Ctrl + Z** undoes, and **Ctrl + Y** redoes (⌘ instead of Ctrl on a Mac). Shortcut labels in
  the app match your computer.
- Pasting a scene from any Fountain or plain-text screenplay formats it.
- **See pages** shows the script paginated exactly as it will print, including (MORE) and
  (CONT'D) across page breaks.
- **Focus** hides the side panels.

### Export and import

**Export** gives you:

- A **screenplay PDF**: US Letter, Courier 12pt, industry margins, a title page and scene
  bookmarks.
- A **Fountain** text file, which Highland, WriterSolo, Final Draft (via import) and the
  reMarkable formatter all read.
- A **project backup** in JSON, with everything including cards, profiles and notes.

**Import** takes a Fountain file or a backup.

To mark up a draft by hand, export the PDF and put it on your reMarkable, or format it with
`python3 -m scriptwriter format` from [`../remarkable-script-writer`](../remarkable-script-writer).

## Saving

- Everything saves as you type.
- Opened from the claude.ai link, projects save to your account, which is private to you, and
  a copy stays in the browser.
- Opened as a local file, projects save in that browser only. Clearing site data, or using a
  private window, loses them.
- Either way, use **Export → Project backup** now and then.

## Development

Source is split for editing and inlined by the build:

```
draftroom.html     page shell and styles (the script goes where /*@@APP@@*/ is)
src/core.js        story data, Fountain parser/exporter, pagination engine, script checks
src/editor.js      the screenplay editor
src/app.js         saving, views, scene board, export
src/main.js        assistant, events, startup
build.py           writes dist/Draftroom.html (standalone) and dist/draftroom-artifact.html
```

```sh
python3 build.py
```
