'use strict';

/* =====================================================================
   Utilities
   ===================================================================== */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const uid = () => Math.random().toString(36).slice(2, 10);
const clamp = (n, a, b) => Math.max(a, Math.min(b, n));
const words = (s) => (String(s || '').match(/[A-Za-z0-9'’-]+/g) || []).length;
const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;
// Shortcut labels follow the platform: Alt/Ctrl on Windows and Linux, ⌥/⌘ on a Mac.
const IS_MAC = /Mac|iPhone|iPad/.test((navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || navigator.userAgent);
const KEY = IS_MAC
  ? { alt: '⌥', altPlus: '⌥', mod: '⌘', modPlus: '⌘', shift: '⇧ ', redo: '⌘ + Shift + Z' }
  : { alt: 'Alt', altPlus: 'Alt+', mod: 'Ctrl', modPlus: 'Ctrl+', shift: 'Shift+', redo: 'Ctrl + Y (or Ctrl + Shift + Z)' };

function getPath(obj, path) {
  return path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj);
}
function setPath(obj, path, value) {
  const keys = path.split('.');
  let o = obj;
  keys.slice(0, -1).forEach((k, i) => {
    if (o[k] == null || typeof o[k] !== 'object') o[k] = /^\d+$/.test(keys[i + 1]) ? [] : {};
    o = o[k];
  });
  o[keys[keys.length - 1]] = value;
}

/* =====================================================================
   Story knowledge: beats, structures, prompts
   ===================================================================== */
// Blake Snyder's "Save the Cat!" beats; page numbers are for a 110-page script.
const BEATS = [
  ['Opening Image', 1, 1, 'A snapshot of the hero and their world before the story changes them.', 'What single image shows who your hero is before anything happens?'],
  ['Theme Stated', 5, 5, 'Someone (rarely the hero) voices what the story is really about.', 'Who says the thing your hero isn\'t ready to hear yet?'],
  ['Set-Up', 1, 10, 'Hero, world, and what\'s missing in their life. Plant what will pay off later.', 'What does your hero want, and what do they actually need?'],
  ['Catalyst', 12, 12, 'The inciting incident that knocks the hero\'s world off balance.', 'What happens TO your hero that they can\'t ignore?'],
  ['Debate', 12, 25, 'The hero hesitates. Should I go? What will it cost?', 'What is the hero afraid of losing if they say yes?'],
  ['Break into Two', 25, 25, 'The hero chooses to enter the new world and pursue the goal.', 'What active choice does the hero make? It must be theirs.'],
  ['B Story', 30, 30, 'A new relationship that carries the theme: love interest, mentor, rival.', 'Who helps your hero learn the lesson of the theme?'],
  ['Fun and Games', 30, 55, 'The promise of the premise: the set pieces the trailer is made of.', 'What scenes would an audience buy a ticket to see?'],
  ['Midpoint', 55, 55, 'A false victory or false defeat. Stakes rise; a clock may start ticking.', 'What changes the game halfway through?'],
  ['Bad Guys Close In', 55, 75, 'Outside pressure and inside doubt tighten around the hero.', 'How do the hero\'s flaws make things worse?'],
  ['All Is Lost', 75, 75, 'The lowest point. Something or someone dies, literally or symbolically.', 'What is the worst thing that could happen to your hero right now?'],
  ['Dark Night of the Soul', 75, 85, 'The hero wallows, then finally grasps the lesson of the theme.', 'What does the hero finally understand about themselves?'],
  ['Break into Three', 85, 85, 'A and B stories meet; armed with the lesson, the hero acts.', 'What new plan comes from what the hero just learned?'],
  ['Finale', 85, 110, 'The hero proves they\'ve changed and confronts the central conflict.', 'How does the hero win (or lose) in a way only the changed hero could?'],
  ['Final Image', 110, 110, 'A mirror of the Opening Image that shows how much has changed.', 'How does your last image answer your first?'],
];
const ACTS = [
  ['a1', 'Act I', 'Setup', 1, 25, 'Who is the hero, what do they want, and what disrupts their world?'],
  ['a2a', 'Act IIA', 'Rising action', 25, 55, 'What new world or plan do they commit to? What\'s fun about it?'],
  ['a2b', 'Act IIB', 'Things fall apart', 55, 85, 'How do the stakes rise? What do they lose?'],
  ['a3', 'Act III', 'Resolution', 85, 110, 'How do they win or lose, and who are they now?'],
];
const SEQUENCES = [
  ['Status quo & inciting incident', 'Establish the hero\'s world; end on the event that disrupts it.'],
  ['Predicament & lock-in', 'The hero reacts, then commits to the goal. End of Act I.'],
  ['First obstacle', 'The first, most obvious attempt to solve the problem.'],
  ['Escalation to midpoint', 'Bigger attempts, bigger failures, leading to the midpoint turn.'],
  ['Complication', 'A new plan after the midpoint; subplots deepen and cost rises.'],
  ['Lowest point', 'Everything the hero tried collapses. End of Act II.'],
  ['New tension & final push', 'A twist or revelation sets up the last attempt.'],
  ['Climax & resolution', 'The final confrontation, then a glimpse of the new normal.'],
];
const TV_STRUCTURE = {
  'half-hour': [['Cold Open', 1, 3], ['Act One', 3, 14], ['Act Two', 14, 27], ['Tag', 27, 30]],
  hour: [['Teaser', 1, 5], ['Act One', 5, 16], ['Act Two', 16, 27], ['Act Three', 27, 38], ['Act Four', 38, 49], ['Act Five', 49, 58]],
};
const REWRITE_PASSES = [
  ['Structure', [
    'The inciting incident lands early enough (film: by about page 12).',
    'The hero makes an active choice to enter Act II.',
    'The midpoint changes the direction or the stakes of the story.',
    'The climax is won or lost by the hero\'s own choice.',
    'Every scene earns its place. Could two be combined?',
  ]],
  ['Character', [
    'Every character wants something in every scene they\'re in.',
    'The protagonist changes (or tragically refuses to).',
    'The antagonist is at least as capable as the hero.',
    'Cover the names: can you tell who is speaking?',
  ]],
  ['Scene', [
    'Each scene enters late and leaves early.',
    'Each scene turns: a value shifts from + to – or – to +.',
    'There is conflict or tension in every scene.',
  ]],
  ['Dialogue', [
    'Subtext over on-the-nose: people rarely say exactly what they mean.',
    'Greetings, small talk and filler lines are cut.',
    'Exposition is dramatized, not explained.',
  ]],
  ['Polish', [
    'Action paragraphs are four lines or fewer, in present tense with active verbs.',
    'Speaking characters appear in CAPS, with an age, the first time we meet them.',
    'The page count sits in range, and spelling and formatting are clean.',
  ]],
];
const STUCK = [
  'Write the scene from the point of view of the person who wants the opposite thing.',
  'What is the worst thing that could happen in this scene? Let it happen.',
  'Cut the first line and the last line of the scene. Is it better?',
  'Give a character a secret they are trying to keep for the whole scene.',
  'What would your hero never say out loud? Make them almost say it.',
  'Move this scene to a location that makes it harder: a funeral, a car, an elevator.',
  'Add a clock. What happens if they don\'t finish in the next five minutes?',
  'Replace one line of dialogue with an action that says the same thing.',
  'Who is the smartest person in this scene? Let them win.',
  'Start the scene in the middle of an argument.',
  'What does the audience know that the character doesn\'t? Use it.',
  'Let the quietest character in the scene make the decision.',
  'Write the scene badly, fast, and on purpose. You can fix bad pages; you can\'t fix blank ones.',
  'What object could carry the emotion of this scene?',
  'Your hero gets exactly what they want in this scene. What does it cost them?',
  'End the scene one beat earlier than feels comfortable.',
  'List five things that could interrupt this scene. Use the most surprising one.',
  'Have someone lie. Let the audience know it\'s a lie.',
  'What is the character doing with their hands while they talk?',
  'Write the next scene as a single paragraph of action, no dialogue.',
  'Which character has the least power here? Give them a way to take some.',
  'Read your last page aloud. Cut every line you stumble on.',
  'Ask: why does this scene have to happen today and not tomorrow?',
  'Swap who is chasing whom: let the pursued become the pursuer.',
];
const REVISIONS = [
  ['White', 'transparent'], ['Blue', '#6f9fe8'], ['Pink', '#ee9ac2'], ['Yellow', '#f1d34f'], ['Green', '#6fc58c'],
  ['Goldenrod', '#d9a431'], ['Buff', '#d9c19a'], ['Salmon', '#f29a7c'], ['Cherry', '#d6445f'],
];
const ELEMENTS = [
  ['scene', 'Scene heading', 'INT. LOCATION - DAY'],
  ['action', 'Action', 'What we see and hear'],
  ['character', 'Character', 'NAME'],
  ['paren', 'Parenthetical', '(beat)'],
  ['dialogue', 'Dialogue', 'What they say'],
  ['transition', 'Transition', 'CUT TO:'],
  ['section', 'Act / section', 'ACT ONE (not printed)'],
];
const EL = Object.fromEntries(ELEMENTS.map(([k, label, ph]) => [k, { label, ph }]));
const CYCLE = ['action', 'character', 'dialogue', 'paren', 'transition', 'scene'];
const NEXT = { scene: 'action', action: 'action', character: 'dialogue', paren: 'dialogue', dialogue: 'action', transition: 'scene', section: 'scene' };

function scale(p, target) { return Math.max(1, Math.round((p * target) / 110)); }
function pageRange(a, b, target) {
  const x = scale(a, target), y = scale(b, target);
  return x === y ? `p. ${x}` : `pp. ${x}–${y}`;
}

/* =====================================================================
   Fountain import / export
   ===================================================================== */
const SCENE_RE = /^(INT\.?\/EXT|INT\/EXT|I\/E|INT|EXT|EST)[.\s]/i;

function stripComments(t) {
  return t.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\[\[[\s\S]*?\]\]/g, '');
}
function isCharacterLine(line) {
  if (line.startsWith('@')) return true;
  const name = line.replace(/\(.*?\)/g, '').replace(/\^\s*$/, '').trim();
  return !!name && /[A-Za-z]/.test(name) && name === name.toUpperCase() && !name.startsWith('!');
}

function parseFountain(text) {
  text = stripComments(text.replace(/\r\n?/g, '\n').replace(/\t/g, '    '));
  let lines = text.split('\n');
  const title = {};
  let i = 0;
  while (i < lines.length && !lines[i].trim()) i++;
  if (i < lines.length && /^[A-Za-z][A-Za-z ]*:/.test(lines[i])) {
    let key = null;
    while (i < lines.length && lines[i].trim()) {
      const m = lines[i].match(/^([A-Za-z][A-Za-z ]*):\s*(.*)$/);
      if (m && !/^\s/.test(lines[i])) { key = m[1].trim().toLowerCase(); title[key] = m[2].trim() ? [m[2].trim()] : []; }
      else if (key) title[key].push(lines[i].trim());
      i++;
    }
    lines = lines.slice(i);
  }
  for (const k in title) title[k] = title[k].join('\n');
  const out = [];
  const n = lines.length;
  const blank = (k) => k < 0 || k >= n || !lines[k].trim();
  let inDialogue = false, lastAction = -2, pendingSynopsis = null;
  const push = (t, x) => { const b = { id: uid(), t, x }; out.push(b); return b; };
  for (let k = 0; k < n; k++) {
    const raw = lines[k], line = raw.trim();
    if (!line) { inDialogue = false; continue; }
    if (inDialogue) {
      if (line.startsWith('(') && line.endsWith(')')) push('paren', line);
      else push('dialogue', line.replace(/^~/, ''));
      continue;
    }
    const pb = blank(k - 1), nb = blank(k + 1);
    if (/^={3,}\s*$/.test(line)) continue;
    if (line.startsWith('#')) { push('section', line.replace(/^#+/, '').trim()); continue; }
    if (line.startsWith('=')) { pendingSynopsis = line.slice(1).trim(); const last = out[out.length - 1]; if (last && last.t === 'scene' && !last.syn) { last.syn = pendingSynopsis; pendingSynopsis = null; } continue; }
    if (line.startsWith('!')) { addAction(raw.trimStart().slice(1)); continue; }
    if (line.startsWith('>') && line.endsWith('<')) { addAction(line.slice(1, -1).trim()); continue; }
    if (line.startsWith('>')) { push('transition', line.slice(1).trim().toUpperCase()); continue; }
    if (pb && ((line.startsWith('.') && line.length > 1 && line[1] !== '.') || SCENE_RE.test(line))) {
      const b = push('scene', (line.startsWith('.') ? line.slice(1) : line).replace(/\s*#[\w.-]+#\s*$/, '').trim().toUpperCase());
      if (pendingSynopsis) { b.syn = pendingSynopsis; pendingSynopsis = null; }
      continue;
    }
    if (pb && nb && line === line.toUpperCase() && /TO:$/.test(line)) { push('transition', line); continue; }
    if (pb && !nb && isCharacterLine(line)) { push('character', line.replace(/^@/, '').replace(/\^\s*$/, '').trim()); inDialogue = true; continue; }
    addAction(raw.trimEnd());
    function addAction(t) {
      const last = out[out.length - 1];
      if (lastAction === k - 1 && last && last.t === 'action') last.x += '\n' + t;
      else push('action', t);
      lastAction = k;
    }
  }
  const strip = (s) => (s || '').replace(/(\*{1,3}|_)(?=\S)(.+?)(?<=\S)\1/g, '$2');
  return {
    title: strip(title.title || '').replace(/\n/g, ' ').trim(),
    author: strip(title.author || title.authors || '').replace(/\n/g, ', '),
    contact: title.contact || '',
    draftDate: (title['draft date'] || '').replace(/\n/g, ' '),
    blocks: out,
  };
}

function toFountain(p) {
  const out = [];
  out.push(`Title: ${p.title || 'Untitled'}`);
  out.push('Credit: Written by');
  out.push(`Author: ${p.author || ''}`);
  if (p.draftDate) out.push(`Draft date: ${p.draftDate}`);
  if (p.contact) out.push('Contact:\n' + p.contact.split('\n').map((l) => '    ' + l).join('\n'));
  out.push('');
  const s = p.script;
  for (let i = 0; i < s.length; i++) {
    const b = s[i], x = (b.x || '').trimEnd();
    const prev = s[i - 1];
    const inGroup = (b.t === 'dialogue' || b.t === 'paren') && prev && ['character', 'dialogue', 'paren'].includes(prev.t);
    if (!x && b.t !== 'dialogue') continue;
    if (!inGroup) out.push('');
    switch (b.t) {
      case 'scene': out.push((SCENE_RE.test(x) ? '' : '.') + x.toUpperCase()); break;
      case 'section': out.push('# ' + x); break;
      case 'character': out.push((/[A-Za-z]/.test(x) ? '' : '@') + x.toUpperCase()); break;
      case 'paren': out.push(`(${x.replace(/^\(|\)$/g, '')})`); break;
      case 'dialogue': out.push(x || '  '); break;
      case 'transition': out.push(/TO:$/.test(x.toUpperCase()) ? x.toUpperCase() : '> ' + x.toUpperCase()); break;
      default:
        out.push(x.split('\n').map((l) => {
          const t = l.trim();
          const risky = (t && t === t.toUpperCase() && /[A-Z]/.test(t)) || SCENE_RE.test(t) || /^[.#=>~@!]/.test(t);
          return risky ? '!' + l : l;
        }).join('\n'));
    }
  }
  return out.join('\n').replace(/\n{3,}/g, '\n\n') + '\n';
}

/* =====================================================================
   Screenplay layout: US Letter, Courier 12pt, 6 lines/inch, 54 lines/page
   ===================================================================== */
const LAY = { scene: [1.5, 60, 2], action: [1.5, 60, 1], character: [3.7, 38, 1], paren: [3.1, 25, 0], dialogue: [2.5, 35, 0], transition: [1.5, 60, 1] };
const LPP = 54;
const RIGHT = 7.5;
const EMPH = /(\\[*_])|(\*\*\*|\*\*|\*|_)(?=\S)(.+?)(?<=\S)\2/g;

function styled(text) {
  const out = [];
  (function walk(s, b, i, u) {
    let pos = 0, m;
    const re = new RegExp(EMPH.source, 'g');
    while ((m = re.exec(s))) {
      for (const ch of s.slice(pos, m.index)) out.push([ch, b, i, u]);
      if (m[1]) out.push([m[1][1], b, i, u]);
      else walk(m[3], b || m[2] === '***' || m[2] === '**', i || m[2] === '***' || m[2] === '*', u || m[2] === '_');
      pos = m.index + m[0].length;
    }
    for (const ch of s.slice(pos)) out.push([ch, b, i, u]);
  })(text, false, false, false);
  return out;
}
function wrapStyled(text, width) {
  // Returns an array of lines; each line is an array of [ch, b, i, u].
  const res = [];
  for (const para of String(text).split('\n')) {
    const chars = styled(para);
    const plain = chars.map((c) => c[0]).join('');
    if (!plain.trim()) { res.push([]); continue; }
    let start = 0;
    const n = plain.length;
    while (start < n) {
      let end, next;
      if (n - start <= width) { end = n; next = n; }
      else {
        const cut = plain.lastIndexOf(' ', start + width);
        if (cut <= start) { end = next = start + width; } else { end = cut; next = cut + 1; }
      }
      res.push(chars.slice(start, end));
      start = next;
      while (start < n && plain[start] === ' ') start++;
    }
  }
  return res;
}

function layoutScript(script) {
  // Build printable blocks from editor blocks.
  const blocks = [];
  for (let i = 0; i < script.length; i++) {
    const b = script[i];
    const x = (b.x || '').trim() ? b.x : '';
    if (b.t === 'section') { blocks.push({ kind: 'section', title: x, src: i, lines: [] }); continue; }
    if (b.t === 'character') {
      const lines = wrapStyled(x.toUpperCase(), LAY.character[1]).map((c) => ({ x: LAY.character[0], c, kind: 'character', src: i }));
      let j = i + 1;
      while (j < script.length && (script[j].t === 'paren' || script[j].t === 'dialogue')) {
        const s = script[j];
        if ((s.x || '').trim()) {
          wrapStyled(s.x, LAY[s.t][1]).forEach((c, k) => lines.push({ x: LAY[s.t][0] + (s.t === 'paren' && k ? 0.1 : 0), c, kind: s.t, src: j }));
        }
        j++;
      }
      blocks.push({ kind: 'dialogue', lines, space: 1, split: true, src: i, name: x.replace(/\s*\(.*?\)/g, '').trim().toUpperCase() });
      i = j - 1;
      continue;
    }
    if (!x) continue;
    if (b.t === 'scene') {
      blocks.push({ kind: 'scene', lines: wrapStyled(x.toUpperCase(), 60).map((c) => ({ x: 1.5, c, kind: 'scene', src: i })), space: 2, keep: true, src: i, id: b.id });
    } else if (b.t === 'transition') {
      blocks.push({ kind: 'transition', lines: [{ x: RIGHT, c: styled(x.toUpperCase()), align: 'right', kind: 'transition', src: i }], space: 1, src: i });
    } else {
      const t = b.t === 'paren' || b.t === 'dialogue' ? 'action' : b.t;
      blocks.push({ kind: 'action', lines: wrapStyled(x, 60).map((c) => ({ x: 1.5, c, kind: t, src: i })), space: 1, split: true, src: i });
    }
  }
  // Paginate.
  const pages = [[]];
  let used = 0;
  const srcPage = new Array(script.length).fill(0);
  const scenes = [];
  const outline = [];
  let scene = null;
  const q = blocks.slice();
  const place = (blk, space) => {
    if (scene) scene.lines += space + blk.lines.length;
    used += space;
    for (const ln of blk.lines) {
      pages[pages.length - 1].push({ row: used, ...ln });
      if (!srcPage[ln.src]) srcPage[ln.src] = pages.length;
      used++;
    }
  };
  const newPage = () => { pages.push([]); used = 0; };
  while (q.length) {
    const blk = q.shift();
    if (blk.kind === 'section') { srcPage[blk.src] = pages.length; outline.push({ level: 0, title: blk.title, page: pages.length }); continue; }
    let space = used ? blk.space : 0;
    let need = space + blk.lines.length;
    if (blk.keep) {
      const nx = q.find((b) => b.lines.length);
      if (nx) need += nx.space + Math.min(2, nx.lines.length);
    }
    if (used + need > LPP) {
      let head = null, tail = null;
      if (blk.split && used) [head, tail] = splitBlock(blk, LPP - used - space);
      else if (!used) [head, tail] = splitBlock(blk, LPP, true);
      if (head) { place(head, space); newPage(); q.unshift(tail); continue; }
      if (used) { newPage(); space = 0; }
    }
    if (blk.kind === 'scene') {
      scene = { id: blk.id, src: blk.src, heading: blk.lines.map((l) => l.c.map((c) => c[0]).join('')).join(' '), page: pages.length, lines: 0 };
      scenes.push(scene);
      outline.push({ level: 1, title: `${scenes.length}. ${scene.heading}`, page: pages.length });
    }
    place(blk, space);
  }
  // Fill page numbers for blocks that produced no lines (empty ones) from their neighbours.
  let last = 1;
  for (let i = 0; i < srcPage.length; i++) { if (srcPage[i]) last = srcPage[i]; else srcPage[i] = last; }
  return { pages, srcPage, scenes, outline, pageCount: pages.length === 1 && !pages[0].length ? 0 : pages.length };
}

function splitBlock(blk, room, force) {
  const lines = blk.lines;
  let k;
  if (force) {
    if (lines.length <= room) return [null, null];
    k = room;
  } else if (blk.kind === 'dialogue') {
    k = null;
    for (let c = 2; c <= Math.min(room - 1, lines.length - 1); c++) if (lines[c - 1].kind === 'dialogue') k = c;
    if (k == null) return [null, null];
    const name = lines[0].c.map((c) => c[0]).join('');
    const cont = /CONT'D/.test(name) ? name : `${name} (CONT'D)`;
    return [
      { ...blk, lines: lines.slice(0, k).concat([{ x: LAY.character[0], c: styled('(MORE)'), kind: 'more', src: lines[k - 1].src }]) },
      { ...blk, space: 0, lines: [{ x: LAY.character[0], c: styled(cont), kind: 'character', src: lines[k].src }].concat(lines.slice(k)) },
    ];
  } else {
    k = Math.min(room, lines.length - 2);
    if (k < 2) return [null, null];
  }
  return [{ ...blk, lines: lines.slice(0, k) }, { ...blk, space: 0, lines: lines.slice(k), keep: false }];
}

function eighths(lines) {
  const n = Math.max(1, Math.round(lines / (LPP / 8)));
  const w = Math.floor(n / 8), r = n % 8;
  return w && r ? `${w} ${r}/8` : w ? `${w}` : `${r}/8`;
}

/* =====================================================================
   Script analysis: stats and lint
   ===================================================================== */
function analyze(p, lay) {
  const s = p.script;
  const chars = {};
  const sceneOf = new Array(s.length).fill(-1);
  let sc = -1;
  let totalWords = 0;
  for (let i = 0; i < s.length; i++) {
    if (s[i].t === 'scene') sc++;
    sceneOf[i] = sc;
    totalWords += words(s[i].x);
  }
  for (let i = 0; i < s.length; i++) {
    if (s[i].t !== 'character' || !(s[i].x || '').trim()) continue;
    const name = s[i].x.replace(/\s*\(.*?\)/g, '').trim().toUpperCase();
    const c = chars[name] || (chars[name] = { name, speeches: 0, words: 0, scenes: new Set(), first: i, lines: [] });
    c.speeches++;
    c.scenes.add(sceneOf[i]);
    for (let j = i + 1; j < s.length && (s[j].t === 'dialogue' || s[j].t === 'paren'); j++) {
      if (s[j].t === 'dialogue') { c.words += words(s[j].x); if (c.lines.length < 60) c.lines.push(s[j].x); }
    }
  }
  const ie = { INT: 0, EXT: 0, 'INT/EXT': 0 }, tod = {};
  const locations = new Set();
  for (const b of s) {
    if (b.t !== 'scene' || !(b.x || '').trim()) continue;
    const h = b.x.toUpperCase().trim();
    if (/^(INT\.?\/EXT|I\/E)/.test(h)) ie['INT/EXT']++; else if (/^INT/.test(h)) ie.INT++; else if (/^EXT/.test(h)) ie.EXT++;
    const m = h.match(/^(?:INT\.?\/EXT\.?|INT\/EXT\.?|I\/E\.?|INT\.?|EXT\.?|EST\.?)\s*(.*?)(?:\s+-\s+(.*))?$/);
    if (m && m[1]) locations.add(m[1].trim());
    if (m && m[2]) tod[m[2].trim()] = (tod[m[2].trim()] || 0) + 1;
  }
  return { chars, sceneOf, words: totalWords, ie, tod, locations: [...locations] };
}

function lint(p, lay, an) {
  const s = p.script;
  const out = [];
  const add = (i, sev, msg) => out.push({ i, id: s[i].id, sev, msg });
  const introduced = new Set();
  for (let i = 0; i < s.length; i++) {
    const b = s[i];
    const x = (b.x || '').trim();
    if (!x) continue;
    if (b.t === 'action') {
      const lines = wrapStyled(x, 60).length;
      if (lines > 4) add(i, 'warn', `Action paragraph runs ${lines} lines. Break it up; four lines or fewer reads faster.`);
      if (/\bwe (see|hear|watch|notice)\b/i.test(x)) add(i, 'tip', '"We see" / "we hear" can usually go: just describe what happens.');
      if (/\b(camera|pan(s)? (to|across)|zoom(s)? in|angle on|close on|dolly)\b/i.test(x)) add(i, 'tip', 'Camera directions are usually left out of a spec script.');
      for (const m of x.matchAll(/\b([A-Z][A-Z'.-]{1,}(?: [A-Z][A-Z'.-]{1,})*)\b/g)) introduced.add(m[1]);
    }
    if (b.t === 'scene') {
      const h = x.toUpperCase();
      if (!SCENE_RE.test(h) && !/^(MONTAGE|FLASHBACK|INTERCUT|SERIES OF SHOTS|BACK TO)/.test(h)) add(i, 'warn', 'Scene headings usually start with INT. or EXT.');
      else if (SCENE_RE.test(h) && !/ - /.test(h)) add(i, 'tip', 'Add the time of day: INT. KITCHEN - NIGHT.');
    }
    if (b.t === 'paren') {
      if (x.length > 30) add(i, 'tip', 'Long parenthetical. Keep them to a few words, or make it action.');
      if (/\b\w+ly\b/i.test(x)) add(i, 'tip', 'Adverb parenthetical. Can the line or an action show that instead?');
    }
    if (b.t === 'character') {
      const name = x.replace(/\s*\(.*?\)/g, '').trim().toUpperCase();
      const c = an.chars[name];
      if (c && c.first === i && ![...introduced].some((w) => w === name || w.startsWith(name + ' ') || w.split(' ')[0] === name)) {
        add(i, 'tip', `${name} speaks before being introduced. Introduce them in CAPS in the action with an age, e.g. "${name} (40s)".`);
      }
      let n = 0;
      for (let j = i + 1; j < s.length && (s[j].t === 'dialogue' || s[j].t === 'paren'); j++) if (s[j].t === 'dialogue') n += wrapStyled(s[j].x || '', 35).length;
      if (n > 10) add(i, 'warn', `${name} has a ${n}-line speech. Could it be cut, or broken up by action or another voice?`);
    }
    if (b.t === 'dialogue' && /^(hi|hello|hey|good (morning|evening|afternoon))\b[,.!]?/i.test(x) && x.length < 30) add(i, 'tip', 'Small talk. Can the scene start after the greeting?');
  }
  const target = p.mode === 'tv' ? TV_STRUCTURE[p.tvFormat].at(-1)[2] : p.targetPages;
  if (lay.pageCount > target * 1.15) out.push({ i: -1, sev: 'warn', msg: `The draft is ${lay.pageCount} pages; your target is about ${target}.` });
  return out;
}

function currentBeat(p, page) {
  if (p.mode === 'tv') {
    const acts = TV_STRUCTURE[p.tvFormat];
    const a = acts.find(([, x, y]) => page >= x && page < y) || acts.at(-1);
    return { name: a[0], range: `pp. ${a[1]}–${a[2]}`, desc: page >= a[2] - 2 ? 'Build to the act out: end on a question that carries us through the break.' : 'Keep the A story moving; weave in the B and C stories.', q: 'What question will the audience carry into the break?' };
  }
  const t = p.targetPages;
  // Point beats win when the page is on them; otherwise the span we're inside.
  const pts = BEATS.filter((b) => b[1] === b[2]);
  const onPoint = pts.find((b) => Math.abs(scale(b[1], t) - page) <= 1);
  const span = BEATS.filter((b) => b[1] !== b[2] && page >= scale(b[1], t) && page <= scale(b[2], t)).at(-1);
  const b = onPoint || span || BEATS.at(-1);
  return { name: b[0], range: pageRange(b[1], b[2], t), desc: b[3], q: b[4], idx: BEATS.indexOf(b) };
}
