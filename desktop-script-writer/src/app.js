/* =====================================================================
   State and saving
   ===================================================================== */
let LIB = { current: null, projects: {} };
let P = null;
let layoutDirty = true;
let _layout = null, _analysis = null;
const state = { view: 'home', sub: {}, castSel: 0, preview: false, focus: false, episode: 0 };
const caps = { db: null, userId: null, sample: null, downloads: null };
const inFrame = (() => { try { return window.self !== window.top; } catch (e) { return true; } })();
const LS_KEY = 'draftroom.v1';

function getLayout() {
  if (layoutDirty || !_layout) { _layout = layoutScript(P.script); _analysis = null; layoutDirty = false; }
  return _layout;
}
function getAnalysis() { const lay = getLayout(); if (!_analysis) _analysis = analyze(P, lay); return _analysis; }

function blankProject(title) {
  return {
    id: uid(), title: title || 'Untitled', author: '', contact: '', draftDate: '', mode: 'film', targetPages: 110, tvFormat: 'hour', revision: 'White',
    idea: {}, beats: {}, acts: {}, sequences: {}, subplots: [{ name: 'A story' }, { name: 'B story' }, { name: 'C story' }],
    tv: { episodes: [], structure: {} }, characters: [], ideas: [], cards: {},
    script: [{ id: uid(), t: 'scene', x: '' }], checklist: {}, feedback: [], notes: '',
    created: Date.now(), updated: Date.now(),
  };
}
function normalize(p) {
  const b = blankProject();
  for (const k of Object.keys(b)) if (p[k] == null) p[k] = b[k];
  if (!Array.isArray(p.script) || !p.script.length) p.script = b.script;
  p.script.forEach((s) => { if (!s.id) s.id = uid(); if (!EL[s.t]) s.t = 'action'; if (typeof s.x !== 'string') s.x = ''; });
  return p;
}

const Store = {
  timer: null,
  queue: Promise.resolve(),
  written: {},
  status(text, err) { const el = $('#save-state'); if (el) { el.textContent = text; el.classList.toggle('err', !!err); } },
  readLocal() {
    try { const raw = localStorage.getItem(LS_KEY); if (raw) return JSON.parse(raw); } catch (e) { /* storage blocked */ }
    return null;
  },
  writeLocal() {
    try { localStorage.setItem(LS_KEY, JSON.stringify(LIB)); return true; } catch (e) { return false; }
  },
  schedule() {
    this.status('Saving…');
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.flush(), 700);
  },
  async flush() {
    clearTimeout(this.timer);
    const localOK = this.writeLocal();
    if (caps.db && caps.userId) {
      try { await this.pushRemote(P); this.status('Saved to your account'); }
      catch (e) { this.status(localOK ? 'Saved in this browser (account save failed)' : 'Not saved: export a backup', !localOK); }
    } else {
      this.status(localOK ? 'Saved in this browser' : 'Not saved: export a backup', !localOK);
    }
  },
  path(k) { return `data/users/${caps.userId}/${k}`; },
  async write(path, body) {
    const str = JSON.stringify(body);
    if (this.written[path] === str) return;
    await caps.db.doc(path).set(body);
    this.written[path] = str;
  },
  pushRemote(p) {
    const job = async () => {
      const { script, ...meta } = p;
      const chunks = [];
      let cur = [], size = 0;
      for (const b of script) {
        const n = JSON.stringify(b).length;
        if (size + n > 150000 && cur.length) { chunks.push(cur); cur = []; size = 0; }
        cur.push(b); size += n;
      }
      chunks.push(cur);
      for (let k = 0; k < chunks.length; k++) await this.write(this.path(`p_${p.id}_s${k}`), { blocks: JSON.stringify(chunks[k]) });
      await this.write(this.path(`p_${p.id}`), { meta: JSON.stringify(meta), chunks: chunks.length, updated: p.updated });
      await this.write(this.path('index'), { current: LIB.current, list: JSON.stringify(Object.values(LIB.projects).map((x) => ({ id: x.id, title: x.title, updated: x.updated }))) });
    };
    this.queue = this.queue.then(job, job);
    return this.queue;
  },
  async pullRemote(id) {
    const m = await caps.db.doc(this.path(`p_${id}`)).get();
    if (!m.exists) return null;
    const d = m.data();
    const meta = JSON.parse(d.meta);
    const script = [];
    for (let k = 0; k < d.chunks; k++) {
      const c = await caps.db.doc(this.path(`p_${id}_s${k}`)).get();
      if (c.exists) script.push(...JSON.parse(c.data().blocks));
    }
    return normalize({ ...meta, script });
  },
  async removeRemote(id) {
    if (!caps.db || !caps.userId) return;
    try {
      const m = await caps.db.doc(this.path(`p_${id}`)).get();
      const n = m.exists ? m.data().chunks : 0;
      for (let k = 0; k < n; k++) await caps.db.doc(this.path(`p_${id}_s${k}`)).delete();
      await caps.db.doc(this.path(`p_${id}`)).delete();
    } catch (e) { /* best effort */ }
  },
  async sync() {
    // Merge account copies with this browser's copies; newest wins per project.
    try {
      const idx = await caps.db.doc(this.path('index')).get();
      let changedCurrent = false;
      if (idx.exists) {
        const list = JSON.parse(idx.data().list || '[]');
        for (const r of list) {
          const local = LIB.projects[r.id];
          if (!local || (r.updated || 0) > (local.updated || 0)) {
            const p = await this.pullRemote(r.id);
            if (p) { LIB.projects[p.id] = p; if (p.id === LIB.current) changedCurrent = true; }
          }
        }
        if (!LIB.projects[LIB.current] && idx.data().current) LIB.current = idx.data().current;
      }
      for (const p of Object.values(LIB.projects)) await this.pushRemote(p);
      this.writeLocal();
      this.status('Saved to your account');
      if (changedCurrent || P !== LIB.projects[LIB.current]) { openProject(LIB.current, true); }
    } catch (e) {
      this.status('Saved in this browser');
    }
  },
};

let railTimer = null;
function touch(structural) {
  P.updated = Date.now();
  if (structural) layoutDirty = true;
  Store.schedule();
  clearTimeout(railTimer);
  railTimer = setTimeout(renderRail, 500);
}

function openProject(id, quiet) {
  if (!LIB.projects[id]) id = Object.keys(LIB.projects)[0];
  LIB.current = id;
  P = normalize(LIB.projects[id]);
  layoutDirty = true;
  Editor.undo = []; Editor.redo = [];
  state.castSel = 0; state.episode = 0;
  $('#proj-name').textContent = P.title || 'Untitled';
  renderRail();
  renderView();
  if (!quiet) Store.writeLocal();
}
function addProject(p, open = true) {
  p = normalize(p);
  LIB.projects[p.id] = p;
  if (open) openProject(p.id);
  touch();
  return p;
}

/* =====================================================================
   Example project
   ===================================================================== */
const SAMPLE_FOUNTAIN = `Title: The Last Train North
Credit: Written by
Author: Example Writer
Draft date: First Draft

# ACT ONE

FADE IN:

EXT. RAIL YARD - NIGHT

Sodium lights buzz over rows of dead freight cars. Snow drifts through the beams.

MAYA REYES (30s, grease-stained overalls, eyes that never stop scanning) walks the line with a flashlight, tapping each coupling with a wrench.

*Clang.* *Clang.* Then a hollow _thunk_.

She stops. Crouches. Something is taped under the car: an envelope.

MAYA
(to herself)
You've got to be kidding me.

She tears it open. Inside: a single ticket. NORTHERN LINE. ONE WAY. Tomorrow's date.

And her name, handwritten.

INT. YARD OFFICE - CONTINUOUS

A space heater rattles. OTIS (60s, cardigan over a union jacket) nurses a thermos of coffee and a crossword.

Maya slaps the ticket on his desk.

MAYA
Who's been under car nine?

OTIS
Nobody's been under car nine since the Carter administration.

MAYA
Then explain this.

Otis studies it. Something passes over his face, recognition or maybe fear, and is gone.

OTIS
(too casual)
Kids. Pranks. Toss it.

MAYA
Kids don't know my middle name, Otis.

He doesn't answer. He folds his crossword. Very carefully.

INT. MAYA'S APARTMENT - KITCHEN - NIGHT

Dishes. Unopened bills. A photo on the fridge: Maya, younger, beside a WOMAN in a conductor's cap.

Maya props the ticket against the photo. Stares at both.

MAYA
You don't get to do this. Not after twenty years.

She picks up the phone. Dials. Waits.

INT. OTIS'S TRUCK - MOVING - NIGHT

Otis drives through snow. His phone buzzes. He answers.

OTIS
It's two in the morning.

MAYA (V.O.)
You knew her handwriting. Didn't you.

A long silence. Wipers thud.

OTIS
Get on the train, Maya.

He hangs up.

CUT TO:

EXT. NORTHERN LINE PLATFORM - DAWN

Maya, duffel over one shoulder, steps onto a train that shouldn't exist: brass fittings, velvet curtains, steam curling from its wheels.
`;

function sampleProject() {
  const r = parseFountain(SAMPLE_FOUNTAIN);
  const p = blankProject(r.title);
  Object.assign(p, { author: r.author, draftDate: r.draftDate, script: r.blocks, example: true, revision: 'Blue' });
  p.idea = {
    protagonist: 'Maya Reyes, a rail-yard mechanic who never left her hometown',
    incident: 'a one-way ticket in her vanished mother\'s handwriting turns up under a freight car',
    goal: 'board a train that shouldn\'t exist and find out what happened to her mother',
    antagonist: 'a yard boss who knows more than he says, and a train that doesn\'t want to let her off',
    stakes: 'she loses her last chance at the truth, and the life she never let herself live',
    hook: 'a train that only comes for the people who were left behind',
    logline1: 'When a one-way ticket in her vanished mother\'s handwriting turns up under a freight car, a rail-yard mechanic who never left home must ride a train that shouldn\'t exist to learn the truth, before it carries her past the point of return.',
    chosen: '1',
    genre: 'Supernatural drama', tone: 'Melancholy, mysterious, warm', comps: 'Field of Dreams meets Snowpiercer',
    theme: 'Is staying put an act of loyalty, or of fear?', controlling: 'We find our way home only when we stop waiting to be rescued.',
    cdq: 'Will Maya find out what happened to her mother?',
  };
  p.beats = {
    0: 'Maya walks the dead rail yard alone at night, tapping couplings. Dutiful, stuck, alone.',
    1: 'Otis: "Every train goes somewhere. You just have to decide if it\'s somewhere you want to be."',
    3: 'The ticket under car nine, in her mother\'s handwriting.',
    4: 'Maya confronts Otis, who tells her to toss it. The phone call: "Get on the train."',
    5: 'Maya boards the Northern Line at dawn.',
  };
  p.characters = [
    { id: uid(), name: 'Maya', role: 'Protagonist', age: '30s', want: 'To find out why her mother left', need: 'To stop living as if someone else will decide for her', flaw: 'Mistakes duty for love; never asks for help', arc: 'Keeps the lights on for everyone → chooses her own life', voice: 'Dry, clipped, deflects with jokes' },
    { id: uid(), name: 'Otis', role: 'Mentor', age: '60s', want: 'To keep a promise he made twenty years ago', need: 'To tell the truth even if it costs him Maya', flaw: 'Protects people by lying to them', voice: 'Folksy, circles around the point' },
  ];
  const scenes = p.script.filter((b) => b.t === 'scene');
  const cards = [
    ['Finish the night shift unnoticed', 'Something is taped under car nine', '-+'],
    ['Get Otis to explain the ticket', 'Otis deflects; he\'s hiding something', '+-'],
    ['Decide whether to believe it', 'Twenty years of anger at her mother', ''],
    ['Make Otis admit what he knows', 'He won\'t say it outright', '-+'],
    ['Board the train', '', ''],
  ];
  scenes.forEach((s, k) => { if (cards[k]) p.cards[s.id] = { goal: cards[k][0], conflict: cards[k][1], turn: cards[k][2] }; });
  p.ideas = [
    { id: uid(), text: 'Maya finds her mother\'s conductor cap in the dining car.' },
    { id: uid(), text: 'Every passenger is someone who never left their hometown.' },
  ];
  return p;
}

/* =====================================================================
   Shell: rail, menus, toast, theme
   ===================================================================== */
const VIEWS = [
  ['home', 'Overview'], ['idea', 'Idea'], ['story', 'Structure'], ['cast', 'Cast'],
  ['scenes', 'Scene board'], ['script', 'Script'], ['rewrite', 'Rewrite'], ['guide', 'Guide'],
];

function renderRail() {
  if (!P) return;
  const lay = getLayout();
  const beatsDone = Object.values(P.beats || {}).filter((v) => String(v).trim()).length;
  const scenes = lay.scenes.length;
  const meters = {
    idea: P.idea.logline1 || P.idea.logline2 || P.idea.logline3 ? '✓' : '',
    story: P.mode === 'film' ? `${beatsDone}/15` : `${(P.tv.episodes || []).filter((e) => e && e.title).length} eps`,
    cast: P.characters.length || '',
    scenes: scenes || '',
    script: lay.pageCount ? `${lay.pageCount} pp` : '',
    rewrite: `${Object.values(P.checklist).filter(Boolean).length}/18`,
  };
  $('#rail').innerHTML = VIEWS.map(([id, label]) => `<a href="#${id}" data-view="${id}" ${state.view === id ? 'aria-current="page"' : ''}>${label}<span class="meter">${meters[id] ?? ''}</span></a>`).join('')
    + `<div class="sep"></div><div class="hint">${P.mode === 'tv' ? (P.tvFormat === 'hour' ? 'One-hour TV' : 'Half-hour TV') : 'Feature film'} · ${esc(P.revision)} draft</div>`;
}

function toast(msg) {
  let t = $('#toast');
  if (!t) { t = document.createElement('div'); t.id = 'toast'; t.className = 'toast'; t.setAttribute('role', 'status'); document.body.appendChild(t); }
  t.textContent = msg; t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.hidden = true; }, 2600);
}

function openMenu(anchor, html, bind) {
  closeMenu();
  const m = document.createElement('div');
  m.className = 'menu'; m.id = 'menu'; m.setAttribute('role', 'menu');
  m.innerHTML = html;
  document.body.appendChild(m);
  const r = anchor.getBoundingClientRect();
  m.style.top = r.bottom + 6 + 'px';
  m.style.left = Math.max(8, Math.min(r.right - m.offsetWidth, window.innerWidth - m.offsetWidth - 8)) + 'px';
  bind(m);
  setTimeout(() => document.addEventListener('mousedown', closeMenuOutside), 0);
}
function closeMenuOutside(e) { if (!e.target.closest('#menu')) closeMenu(); }
function closeMenu() { const m = $('#menu'); if (m) m.remove(); document.removeEventListener('mousedown', closeMenuOutside); }

function projectsMenu(anchor) {
  const list = Object.values(LIB.projects).sort((a, b) => b.updated - a.updated);
  openMenu(anchor, `<small>Projects</small>${list.map((p) => `<button data-open="${p.id}">${p.id === P.id ? '● ' : ''}${esc(p.title || 'Untitled')}</button>`).join('')}
    <hr><button data-m="new-film">New feature film</button><button data-m="new-tv">New TV episode</button><button data-m="dup">Duplicate this project</button>
    <hr><button data-m="del" class="danger">Delete this project…</button>`, (m) => {
    m.addEventListener('click', (e) => {
      const b = e.target.closest('button'); if (!b) return;
      if (b.dataset.open) { closeMenu(); openProject(b.dataset.open); return; }
      const a = b.dataset.m;
      if (a === 'new-film' || a === 'new-tv') { closeMenu(); const p = blankProject('Untitled'); if (a === 'new-tv') { p.mode = 'tv'; p.script = [{ id: uid(), t: 'section', x: 'TEASER' }, { id: uid(), t: 'scene', x: '' }]; } addProject(p); navigate('home'); toast('New project created. Give it a title in Settings.'); }
      if (a === 'dup') { closeMenu(); const p = JSON.parse(JSON.stringify(P)); p.id = uid(); p.title = (P.title || 'Untitled') + ' (copy)'; delete p.example; addProject(p); toast('Duplicated.'); }
      if (a === 'del') {
        b.outerHTML = `<div class="confirm" style="padding:6px 10px">Delete “${esc(P.title)}”? <button class="btn small danger" data-m="del-yes">Delete</button><button class="btn small" data-m="del-no">Keep</button></div>`;
      }
      if (a === 'del-no') closeMenu();
      if (a === 'del-yes') {
        closeMenu();
        const id = P.id;
        delete LIB.projects[id];
        Store.removeRemote(id);
        if (!Object.keys(LIB.projects).length) addProject(blankProject('Untitled'), false);
        openProject(Object.keys(LIB.projects)[0]);
        Store.flush();
        toast('Project deleted.');
      }
    });
  });
}

function exportMenu(anchor) {
  openMenu(anchor, `<small>Export “${esc(P.title)}”</small>
    <button data-m="pdf">Screenplay PDF</button>
    <button data-m="fountain">Fountain file (plain text)</button>
    <button data-m="json">Project backup (everything)</button>
    ${inFrame ? '' : '<button data-m="print">Print…</button>'}
    <hr><button data-m="import">Import Fountain file or backup…</button>`, (m) => {
    m.addEventListener('click', (e) => {
      const a = e.target.closest('button')?.dataset.m; if (!a) return;
      closeMenu();
      if (a === 'pdf') exportPDF();
      if (a === 'fountain') saveFile(`${fileBase()}.${caps.downloads ? 'txt' : 'fountain'}`, toFountain(P), 'text/plain');
      if (a === 'json') saveFile(`${fileBase()} backup.json`, JSON.stringify(P, null, 1), 'application/json');
      if (a === 'print') printScript();
      if (a === 'import') $('#file-in').click();
    });
  });
}
function fileBase() { return (P.title || 'Untitled').replace(/[\\/:*?"<>|]+/g, '').trim() || 'Untitled'; }

async function saveFile(name, data, mime) {
  if (caps.downloads) {
    try { await caps.downloads.save({ filename: name, data }); toast(`Saved ${name}`); }
    catch (e) { if (e && e.code === 'declined') return; toast(e && e.code === 'rate_limited' ? 'A save is already waiting for you to confirm.' : 'That file couldn\'t be saved here.'); }
    return;
  }
  if (!inFrame) {
    const url = URL.createObjectURL(new Blob([data], { type: mime }));
    const a = document.createElement('a');
    a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 4000);
    toast(`Saved ${name}`);
    return;
  }
  toast('Saving files isn\'t available in this view.');
}

function applyTheme(t) {
  if (t === 'light' || t === 'dark') document.documentElement.dataset.theme = t;
  else delete document.documentElement.dataset.theme;
}

/* =====================================================================
   Routing and rendering
   ===================================================================== */
function navigate(view) {
  if (!VIEWS.some(([v]) => v === view)) view = 'home';
  if (location.hash !== '#' + view) history.replaceState(null, '', '#' + view);
  state.view = view;
  renderRail();
  renderView();
  refreshAssistant();
}

function renderView() {
  if (Editor.el) Editor.unmount();
  const main = $('#main');
  main.style.overflow = '';
  const v = Views[state.view];
  main.innerHTML = v.render();
  main.scrollTop = 0;
  if (v.after) v.after(main);
}

function tabs(view, list) {
  const cur = state.sub[view] || list[0][0];
  return `<div class="tabs" role="tablist">${list.map(([id, label]) => `<button role="tab" data-tab="${view}:${id}" aria-selected="${id === cur}">${label}</button>`).join('')}</div>`;
}
const subOf = (view, def) => state.sub[view] || def;

function field(k, label, opts = {}) {
  const v = getPath(P, k) ?? '';
  const hint = opts.hint ? `<em>${esc(opts.hint)}</em>` : '';
  const id = 'f-' + k.replace(/\W/g, '-');
  const control = opts.input
    ? `<input type="text" id="${id}" data-k="${k}" value="${esc(v)}" placeholder="${esc(opts.ph || '')}">`
    : `<textarea id="${id}" data-k="${k}" class="${opts.rows === 1 ? 'short' : ''}" rows="${opts.rows || 2}" placeholder="${esc(opts.ph || '')}">${esc(v)}</textarea>`;
  return `<label class="f" for="${id}"><span>${esc(label)}${hint}</span>${control}</label>`;
}

/* =====================================================================
   Views
   ===================================================================== */
const Views = {};

Views.home = {
  render() {
    const lay = getLayout(), an = getAnalysis();
    const log = chosenLogline();
    const film = P.mode === 'film';
    const target = film ? P.targetPages : TV_STRUCTURE[P.tvFormat].at(-1)[2];
    const beatsDone = Object.values(P.beats).filter((v) => String(v).trim()).length;
    const n = nextStep(lay, beatsDone, target);
    const chars = Object.values(an.chars).sort((a, b) => b.words - a.words).slice(0, 8);
    const maxW = Math.max(1, ...chars.map((c) => c.words));
    const pct = clamp(lay.pageCount / target, 0, 1) * 100;
    const ticks = film
      ? [['Catalyst', 12], ['Act II', 25], ['Midpoint', 55], ['All is lost', 75], ['Act III', 85], ['End', 110]].map(([l, p]) => [l, scale(p, target)])
      : TV_STRUCTURE[P.tvFormat].map(([l, a]) => [l, a]).slice(1).concat([['End', target]]);
    const projects = Object.values(LIB.projects).sort((a, b) => b.updated - a.updated);
    return `<div class="view">
      ${P.example ? `<div class="banner"><span><b>This is an example project.</b> Look around, try the editor, then start your own.</span><span class="row"><button class="btn small primary" data-act="new-project">Start my own project</button><button class="btn small" data-act="keep-example">Keep it as mine</button></span></div>` : ''}
      <div class="hdr"><div><div class="eyebrow">${film ? 'Feature film' : P.tvFormat === 'hour' ? 'One-hour TV' : 'Half-hour TV'} · ${esc(P.revision)} draft</div>
        <h1 class="vtitle">${esc(P.title || 'Untitled')}</h1>
        <p class="lede">${P.author ? 'Written by ' + esc(P.author) : 'Add your name and contact details in Settings below. They go on the title page.'}</p></div>
        <button class="btn primary" data-go="script">Open the script</button></div>
      <div class="stack">
        ${log ? `<div class="logline-card" data-go="idea" role="link" tabindex="0">${esc(log)}</div>` : `<div class="logline-card empty">No logline yet. <button class="btn small" data-go="idea">Write your logline</button></div>`}
        <div class="panel next"><div><div class="eyebrow">Next step</div><h3>${esc(n.title)}</h3><p class="sub" style="margin:2px 0 0">${esc(n.why)}</p></div><button class="btn primary" data-go="${n.view}">${esc(n.cta)}</button></div>
        <div class="stats">
          <div class="stat"><b>${lay.pageCount}</b><span>pages of ~${target}</span></div>
          <div class="stat"><b>${lay.pageCount}</b><span>minutes, roughly</span></div>
          <div class="stat"><b>${lay.scenes.length}</b><span>scenes</span></div>
          <div class="stat"><b>${an.words.toLocaleString()}</b><span>words</span></div>
          <div class="stat"><b>${film ? beatsDone + '/15' : (P.tv.episodes || []).filter((e) => e && e.title).length}</b><span>${film ? 'beats planned' : 'episodes planned'}</span></div>
          <div class="stat"><b>${Object.keys(an.chars).length}</b><span>speaking roles</span></div>
        </div>
        <div class="panel"><div class="spread"><h2 class="sec">Where the draft is</h2><span class="muted num" style="font-size:13px">p. ${lay.pageCount} of ~${target}</span></div>
          <div class="timeline" aria-hidden="true"><div class="bar"></div><div class="fill" style="width:${pct}%"></div>
          ${ticks.map(([l, p]) => `<div class="tick ${lay.pageCount >= p ? 'done' : ''}" style="left:${clamp(p / target, 0, 1) * 100}%"><span>${esc(l)} · ${p}</span></div>`).join('')}</div></div>
        <div class="grid2">
          <div class="panel"><h2 class="sec">Who talks most</h2><p class="sub">Words of dialogue per character.</p>
            ${chars.length ? `<div class="bars">${chars.map((c) => `<div class="b"><span>${esc(c.name)}</span><i style="width:${(c.words / maxW) * 100}%"></i><span class="muted num">${c.words}</span></div>`).join('')}</div>` : '<p class="muted">Dialogue stats appear once characters start talking.</p>'}</div>
          <div class="panel"><h2 class="sec">Production snapshot</h2><p class="sub">What a line producer would ask first.</p>
            <div class="bars">${Object.entries(an.ie).filter(([, v]) => v).map(([k, v]) => `<div class="b"><span>${k}</span><i style="width:${(v / Math.max(1, lay.scenes.length)) * 100}%"></i><span class="muted num">${v}</span></div>`).join('') || '<p class="muted">No scenes yet.</p>'}</div>
            <p class="sub" style="margin-top:12px">${an.locations.length} locations${Object.keys(an.tod).length ? ' · ' + Object.entries(an.tod).sort((a, b) => b[1] - a[1]).slice(0, 4).map(([k, v]) => `${esc(k.toLowerCase())} ${v}`).join(', ') : ''}</p></div>
        </div>
        <div class="grid2">
          <div class="panel stack"><h2 class="sec">Settings</h2>
            ${field('title', 'Title', { input: true })}
            <div class="grid2">${field('author', 'Written by', { input: true })}${field('draftDate', 'Draft', { input: true, ph: 'First draft, June 2026' })}</div>
            ${field('contact', 'Contact', { rows: 2, ph: 'Email, phone or agent' })}
            <div class="grid2">
              <label class="f" for="f-mode"><span>Format</span><select id="f-mode" data-k="mode"><option value="film" ${film ? 'selected' : ''}>Feature film</option><option value="tv" ${!film ? 'selected' : ''}>TV episode</option></select></label>
              ${film ? `<label class="f" for="f-target"><span>Target pages</span><input type="number" id="f-target" data-k="targetPages" min="10" max="200" value="${P.targetPages}"></label>`
                : `<label class="f" for="f-tvf"><span>Episode length</span><select id="f-tvf" data-k="tvFormat"><option value="hour" ${P.tvFormat === 'hour' ? 'selected' : ''}>One hour</option><option value="half-hour" ${P.tvFormat !== 'hour' ? 'selected' : ''}>Half hour</option></select></label>`}
            </div>
            <label class="f" for="f-rev"><span>Revision color<em>Production drafts change page color with each revision</em></span><select id="f-rev" data-k="revision">${REVISIONS.map(([r]) => `<option ${P.revision === r ? 'selected' : ''}>${r}</option>`).join('')}</select></label>
          </div>
          <div class="panel stack"><h2 class="sec">Your projects</h2>
            <div class="projects">${projects.map((p) => `<div class="p ${p.id === P.id ? 'cur' : ''}"><span><b>${esc(p.title || 'Untitled')}</b><br><small class="muted">${p.mode === 'tv' ? 'TV' : 'Film'} · edited ${new Date(p.updated).toLocaleDateString()}</small></span>${p.id === P.id ? '<span class="pill acc">Open</span>' : `<button class="btn small" data-open="${p.id}">Open</button>`}</div>`).join('')}</div>
            <div class="row"><button class="btn" data-act="new-project">New project</button><button class="btn" data-act="import">Import…</button></div>
            <p class="sub" style="margin:0">${saveExplainer()}</p>
          </div>
        </div>
      </div></div>`;
  },
  onInput(t) {
    if (t.dataset.k === 'title') { $('#proj-name').textContent = P.title || 'Untitled'; }
    if (['mode', 'targetPages', 'tvFormat', 'revision'].includes(t.dataset.k)) { if (t.dataset.k === 'targetPages') P.targetPages = clamp(+t.value || 110, 10, 200); renderView(); }
  },
};

function saveExplainer() {
  if (caps.db && caps.userId) return 'Your projects save to your account as you type, and a copy stays in this browser. Export a backup now and then.';
  return 'Your projects save in this browser as you type. Export a backup (Export → Project backup) to keep a copy somewhere safe or move to another computer.';
}
function chosenLogline() {
  const i = P.idea;
  return (i['logline' + (i.chosen || '1')] || i.logline1 || i.logline2 || i.logline3 || '').trim();
}
function nextStep(lay, beatsDone, target) {
  const film = P.mode === 'film';
  if (!chosenLogline()) return { title: 'Write your logline', why: 'One sentence: who wants what, what stands in the way, and what happens if they fail.', view: 'idea', cta: 'Go to Idea' };
  if (film && beatsDone < 6) return { title: 'Map your structure', why: `You've planned ${beatsDone} of 15 beats. Knowing your midpoint and ending makes the draft much faster.`, view: 'story', cta: 'Open the beat sheet' };
  if (!film && !(P.tv.engine || '').trim()) return { title: 'Define your series engine', why: 'What generates a new story every episode? Nail that before the pilot.', view: 'story', cta: 'Open the bible' };
  if (P.characters.length < 2) return { title: 'Build your cast', why: 'Give your hero and antagonist a want, a need and a flaw.', view: 'cast', cta: 'Go to Cast' };
  if (lay.scenes.length < 5 && lay.pageCount < 5) return { title: 'Plan your scenes', why: 'Sketch scenes as cards, give each a goal and a conflict, then put them in order.', view: 'scenes', cta: 'Open the scene board' };
  if (lay.pageCount < target * 0.9) {
    const b = currentBeat(P, Math.max(1, lay.pageCount));
    return { title: `Keep drafting: you're on page ${lay.pageCount}`, why: `You're in ${b.name} (${b.range}). ${b.desc}`, view: 'script', cta: 'Write' };
  }
  return { title: 'Start your rewrite', why: 'The draft is in range. Work through one rewrite pass at a time.', view: 'rewrite', cta: 'Open the rewrite checklist' };
}

Views.idea = {
  render() {
    const sub = subOf('idea', 'logline');
    let body = '';
    if (sub === 'logline') {
      body = `<div class="grid2">
        <div class="stack">
          ${field('idea.protagonist', 'Protagonist', { rows: 1, hint: 'Who, plus the flaw that makes it hard', ph: 'a rail-yard mechanic who never left home' })}
          ${field('idea.incident', 'Inciting incident', { rows: 1, hint: 'What knocks their world off balance?' })}
          ${field('idea.goal', 'Goal', { rows: 1, hint: 'Specific and visible' })}
          ${field('idea.antagonist', 'Antagonist or obstacle', { rows: 1, hint: 'Who or what actively opposes them?' })}
          ${field('idea.stakes', 'Stakes', { rows: 1, hint: 'What happens if they fail?' })}
          ${field('idea.hook', 'The hook', { rows: 1, hint: 'What\'s fresh or ironic about this?' })}
        </div>
        <div class="stack">
          <div><div class="eyebrow" style="margin-bottom:6px">Assembled from your answers</div><div class="builder" id="builder">${builderHTML()}</div></div>
          ${[1, 2, 3].map((n) => `<div class="stack" style="gap:6px">${field('idea.logline' + n, `Logline, draft ${n}`, { rows: 3 })}
            <label class="row" style="font-size:13px;gap:6px"><input type="radio" name="chosen" data-k="idea.chosen" value="${n}" ${String(P.idea.chosen || '1') === String(n) ? 'checked' : ''}> Use this one on the Overview and in assistant notes</label></div>`).join('')}
          <p class="sub">A good logline is about 25–35 words, names a flawed hero and a clear goal, and hints at the ending's stakes. Read it to someone: do they ask “and then what?”</p>
        </div></div>`;
    } else if (sub === 'premise') {
      body = `<div class="stack">
        <div class="grid3">${field('idea.genre', 'Genre', { input: true })}${field('idea.tone', 'Tone', { input: true })}${field('idea.audience', 'Audience / rating', { input: true })}</div>
        ${field('idea.comps', 'Comparable titles', { rows: 1, hint: '"X meets Y". Recent and successful' })}
        ${field('idea.theme', 'Theme', { rows: 2, hint: 'What question does the story ask about how to live?' })}
        ${field('idea.controlling', 'Controlling idea', { rows: 1, hint: 'The story\'s answer: "___ happens when ___"' })}
        ${field('idea.cdq', 'Central dramatic question', { rows: 1, hint: 'Will [hero] [achieve goal]?' })}
        ${field('idea.why', 'Why this story, why you, why now?', { rows: 3 })}
        ${field('idea.titles', 'Title ideas', { rows: 3 })}
      </div>`;
    } else {
      const w = words(P.idea.synopsis);
      body = `<div class="stack">
        <p class="sub">Tell the whole story in present tense, ending included, in about a page (400–600 words). Readers and producers ask for this, and writing it exposes holes before you draft.</p>
        ${field('idea.synopsis', 'One-page synopsis', { rows: 18 })}
        <div class="muted num" id="syn-count" style="font-size:13px">${w} words</div></div>`;
    }
    return `<div class="view"><div class="hdr"><div><div class="eyebrow">Step 1</div><h1 class="vtitle">The idea</h1>
      <p class="lede">If you can't say it in one sentence, the story isn't clear yet. Start here.</p></div></div>
      ${tabs('idea', [['logline', 'Logline'], ['premise', 'Premise & theme'], ['synopsis', 'Synopsis']])}${body}</div>`;
  },
  onInput(t) {
    if ($('#builder')) $('#builder').innerHTML = builderHTML();
    if ($('#syn-count')) $('#syn-count').textContent = `${words(P.idea.synopsis)} words`;
  },
};
function builderHTML() {
  const i = P.idea;
  const g = (v, ph) => (v && v.trim() ? `<mark>${esc(v.trim())}</mark>` : `<span class="gap">${ph}</span>`);
  return `When ${g(i.incident, 'inciting incident')}, ${g(i.protagonist, 'a flawed protagonist')} must ${g(i.goal, 'reach a goal')} or else ${g(i.stakes, 'the stakes')}, despite ${g(i.antagonist, 'the opposition')}.`;
}

Views.story = {
  render() {
    const film = P.mode === 'film';
    const lay = getLayout();
    let body = '';
    const sub = subOf('story', film ? 'beats' : 'bible');
    if (film) {
      const t = P.targetPages;
      const cur = lay.pageCount ? currentBeat(P, lay.pageCount).idx : -1;
      if (sub === 'beats') {
        body = `<p class="sub">Blake Snyder's <i>Save the Cat!</i> beats, with page targets scaled to your ${t}-page length. Your draft is on page ${lay.pageCount || 0}.</p>
        <div class="panel">${BEATS.map(([name, a, b, desc, q], k) => `<div class="beat ${k === cur ? 'here' : ''}"><div class="n">${k + 1}</div><div>
          <h3>${name}<span class="pill">${pageRange(a, b, t)}</span>${k === cur ? '<span class="pill acc">Your draft is here</span>' : ''}</h3>
          <p>${desc} <span class="q">${q}</span></p>
          <textarea data-k="beats.${k}" id="beat-${k}" rows="2" aria-label="${name}">${esc(P.beats[k] || '')}</textarea></div></div>`).join('')}</div>`;
      } else if (sub === 'acts') {
        body = `<div class="panel" style="margin-bottom:14px">${tensionSVG(t)}</div>
          <div class="acts">${ACTS.map(([k, n, s, a, b, q]) => `<div class="panel stack" style="gap:6px"><div class="eyebrow">${n} · ${pageRange(a, b, t)}</div><h3 style="font-size:15px">${s}</h3><p class="sub" style="margin:0">${q}</p><textarea data-k="acts.${k}" id="act-${k}" rows="7" aria-label="${n}">${esc(P.acts[k] || '')}</textarea></div>`).join('')}</div>
          <div class="panel" style="margin-top:14px">${field('idea.cdq', 'Central dramatic question', { rows: 1, hint: 'The question the climax answers' })}</div>`;
      } else if (sub === 'seq') {
        body = `<p class="sub">Eight movements of roughly ${Math.round(t / 8)} pages, each with its own mini-goal and turn.</p><div class="grid2">${SEQUENCES.map(([n, d], k) => `<div class="panel stack" style="gap:6px"><div class="spread"><h3 style="font-size:15px">${k + 1}. ${n}</h3><span class="pill">pp. ${Math.round((k * t) / 8) + 1}–${Math.round(((k + 1) * t) / 8)}</span></div><p class="sub" style="margin:0">${d}</p><textarea data-k="sequences.${k}" id="seq-${k}" rows="3" aria-label="Sequence ${k + 1}">${esc(P.sequences[k] || '')}</textarea></div>`).join('')}</div>`;
      } else {
        body = subplotTable();
      }
      return shell(tabs('story', [['beats', 'Beat sheet'], ['acts', 'Three acts'], ['seq', 'Eight sequences'], ['plots', 'Subplots']]) + body);
    }
    // TV
    const acts = TV_STRUCTURE[P.tvFormat];
    if (sub === 'bible') {
      body = `<div class="stack">${field('tv.logline', 'Series logline', { rows: 2 })}
        <div class="grid3">${field('tv.format', 'Format', { input: true, ph: 'Single-cam, serialized' })}${field('tv.network', 'Where it could live', { input: true, ph: 'Streamer, cable, network' })}${field('tv.comps', 'Comparable shows', { input: true })}</div>
        ${field('tv.world', 'The world', { rows: 3, hint: 'Where and when, and what makes it specific' })}
        ${field('tv.engine', 'The engine', { rows: 3, hint: 'What generates a new story every episode?' })}
        ${field('tv.tone', 'Tone', { rows: 2 })}${field('tv.themes', 'Themes', { rows: 2 })}
        ${field('tv.pilot', 'The pilot', { rows: 3, hint: 'What happens, and how does it set up the series?' })}
        ${field('tv.future', 'Where could seasons 2–5 go?', { rows: 3 })}</div>`;
    } else if (sub === 'season') {
      body = `<div class="stack">${field('tv.seasonStart', 'Where the season starts', { rows: 2 })}${field('tv.midseason', 'Midseason turn', { rows: 2 })}${field('tv.seasonEnd', 'Where the season ends, and what\'s left open', { rows: 2 })}
        <div class="panel"><h2 class="sec">Episode grid</h2><p class="sub">One row per episode: the A, B and C stories and the season-arc beat.</p><div class="scroll-x"><table class="grid"><thead><tr><th style="width:36px">Ep</th><th>Title</th><th>A story</th><th>B / C story</th><th>Arc beat</th></tr></thead><tbody>
        ${Array.from({ length: 10 }, (_, k) => `<tr><td class="num" style="padding-top:12px">${k + 1}</td>${['title', 'a', 'b', 'arc'].map((f) => `<td><textarea data-k="tv.episodes.${k}.${f}" id="ep-${k}-${f}" rows="1" aria-label="Episode ${k + 1} ${f}">${esc(getPath(P, `tv.episodes.${k}.${f}`) || '')}</textarea></td>`).join('')}</tr>`).join('')}
        </tbody></table></div></div></div>`;
    } else {
      const ep = state.episode;
      body = `<div class="row" style="margin-bottom:12px"><label class="f" for="ep-pick" style="grid-template-columns:auto 200px;align-items:center;gap:10px"><span>Episode</span>
        <select id="ep-pick">${Array.from({ length: 10 }, (_, k) => `<option value="${k}" ${k === ep ? 'selected' : ''}>${k + 1}${getPath(P, `tv.episodes.${k}.title`) ? ' · ' + esc(getPath(P, `tv.episodes.${k}.title`)) : ''}</option>`).join('')}</select></label>
        <span class="muted" style="font-size:13px">${P.tvFormat === 'hour' ? 'One-hour' : 'Half-hour'} episode, about ${acts.at(-1)[2]} pages. End each act on a question the audience must stay through the break to answer.</span></div>
        <div class="grid2">${acts.map(([name, a, b]) => `<div class="panel stack" style="gap:8px"><div class="spread"><h3 style="font-size:15px">${name}</h3><span class="pill">pp. ${a}–${b}</span></div>
          ${['A', 'B', 'C'].map((s) => field(`tv.structure.${ep}.${name.replace(/\s/g, '')}.${s}`, `${s} story`, { rows: 1 })).join('')}
          ${field(`tv.structure.${ep}.${name.replace(/\s/g, '')}.out`, name === acts.at(-1)[0] ? 'Final button' : 'Act out', { rows: 1, hint: 'The cliffhanger' })}</div>`).join('')}</div>`;
    }
    return shell(tabs('story', [['bible', 'Series bible'], ['season', 'Season & episodes'], ['episode', 'Episode structure'], ['plots', 'Subplots']]) + (sub === 'plots' ? subplotTable() : body));

    function shell(inner) {
      return `<div class="view"><div class="hdr"><div><div class="eyebrow">Step 2</div><h1 class="vtitle">Structure</h1>
        <p class="lede">${film ? 'Map the shape of the story before you write pages. Fix structure here; it\'s much cheaper than fixing it in a draft.' : 'Pitch the whole show, then break each episode into acts.'}</p></div></div>${inner}</div>`;
    }
  },
  after(main) {
    const pick = $('#ep-pick', main);
    if (pick) pick.addEventListener('change', () => { state.episode = +pick.value; renderView(); });
  },
};
function subplotTable() {
  const cols = P.mode === 'film' ? ACTS.map(([k, n]) => [k, n]) : TV_STRUCTURE[P.tvFormat].map(([n]) => [n.replace(/\s/g, ''), n]);
  return `<p class="sub">Where does each storyline advance? One row per plot; note its beat in each ${P.mode === 'film' ? 'act' : 'act of the episode'}.</p>
    <div class="panel scroll-x"><table class="grid"><thead><tr><th style="width:150px">Plot</th>${cols.map(([, n]) => `<th>${n}</th>`).join('')}<th style="width:40px"></th></tr></thead><tbody>
    ${P.subplots.map((s, r) => `<tr><td><input type="text" id="sp-${r}" data-k="subplots.${r}.name" value="${esc(s.name || '')}" aria-label="Plot name"></td>${cols.map(([k, n]) => `<td><textarea id="sp-${r}-${k}" data-k="subplots.${r}.${k}" rows="2" aria-label="${esc(s.name)} ${n}">${esc(s[k] || '')}</textarea></td>`).join('')}<td><button class="btn ghost small" data-act="del-subplot" data-i="${r}" aria-label="Remove row">✕</button></td></tr>`).join('')}
    </tbody></table><button class="btn small" data-act="add-subplot" style="margin-top:10px">Add a storyline</button></div>`;
}
function tensionSVG(t) {
  const W = 900, H = 170, x0 = 20, x1 = W - 20, base = 120;
  const X = (p) => x0 + ((p - 1) / 109) * (x1 - x0);
  const pts = [[1, 10], [12, 18], [25, 40], [45, 46], [55, 64], [66, 54], [75, 28], [85, 48], [99, 92], [106, 78], [110, 44]];
  let d = `M ${X(pts[0][0])} ${base - pts[0][1]}`;
  for (let k = 1; k < pts.length; k++) { const [a, ya] = pts[k - 1], [b, yb] = pts[k]; const m = (X(a) + X(b)) / 2; d += ` C ${m} ${base - ya}, ${m} ${base - yb}, ${X(b)} ${base - yb}`; }
  const marks = [[1, '1'], [12, 'Catalyst'], [25, 'Act I break'], [55, 'Midpoint'], [75, 'All is lost'], [85, 'Act II break'], [99, 'Climax'], [110, String(t)]];
  return `<svg class="tension" viewBox="0 0 ${W} ${H}" role="img" aria-label="Story tension rising through the three acts">
    <path d="${d}" fill="none" stroke="var(--accent)" stroke-width="3"/>
    <line x1="${x0}" x2="${x1}" y1="${base}" y2="${base}" stroke="var(--ink)" stroke-width="1.5"/>
    ${marks.map(([p, l]) => `<g><line x1="${X(p)}" x2="${X(p)}" y1="${base - 5}" y2="${base + 5}" stroke="var(--ink)"/><text x="${X(p)}" y="${base + 22}" text-anchor="${p === 1 ? 'start' : p === 110 ? 'end' : 'middle'}" font-size="13" fill="var(--ink)" font-family="Instrument Sans, sans-serif">${l}</text>${p > 1 && p < 110 ? `<text x="${X(p)}" y="${base + 40}" text-anchor="middle" font-size="12" fill="var(--muted)" font-family="Instrument Sans, sans-serif">p. ${scale(p, t)}</text>` : ''}</g>`).join('')}
    <text x="${x0}" y="16" font-size="11" fill="var(--muted)" font-family="Instrument Sans, sans-serif" letter-spacing="1">TENSION</text></svg>`;
}

Views.cast = {
  render() {
    const an = getAnalysis();
    const list = P.characters;
    const sel = clamp(state.castSel, 0, Math.max(0, list.length - 1));
    const known = new Set(list.map((c) => (c.name || '').toUpperCase()));
    const missing = Object.keys(an.chars).filter((n) => !known.has(n));
    const c = list[sel];
    let detail = '<div class="panel"><p class="muted">Add your first character to start a profile.</p></div>';
    if (c) {
      const st = an.chars[(c.name || '').toUpperCase()];
      const lay = getLayout();
      detail = `<div class="panel stack">
        <div class="grid3">${field(`characters.${sel}.name`, 'Name', { input: true })}
          <label class="f" for="c-role"><span>Role</span><select id="c-role" data-k="characters.${sel}.role">${['Protagonist', 'Antagonist', 'Ally', 'Mentor', 'Love interest', 'Supporting', 'Other'].map((r) => `<option ${c.role === r ? 'selected' : ''}>${r}</option>`).join('')}</select></label>
          ${field(`characters.${sel}.age`, 'Age', { input: true })}</div>
        ${st ? `<div class="row"><span class="pill acc">${plural(st.speeches, 'speech')}</span><span class="pill">${st.words} words</span><span class="pill">${plural(st.scenes.size, 'scene')}</span><span class="pill">first speaks p. ${lay.srcPage[st.first] || 1}</span></div>` : '<p class="sub" style="margin:0">Not speaking in the script yet.</p>'}
        ${field(`characters.${sel}.look`, 'Look & first impression', { rows: 2, hint: 'How we meet them on the page' })}
        <div class="grid2">${field(`characters.${sel}.want`, 'Want', { rows: 2, hint: 'Conscious, external goal' })}${field(`characters.${sel}.need`, 'Need', { rows: 2, hint: 'What they must learn or accept' })}</div>
        <div class="grid2">${field(`characters.${sel}.wound`, 'Wound', { rows: 2, hint: 'The past event that shaped them' })}${field(`characters.${sel}.flaw`, 'Flaw & the lie they believe', { rows: 2 })}</div>
        ${field(`characters.${sel}.arc`, 'Arc', { rows: 2, hint: 'Who they are on page 1 → who they are at the end' })}
        ${field(`characters.${sel}.voice`, 'Voice', { rows: 2, hint: 'Vocabulary, rhythm, what they never say' })}
        <div class="grid2">${field(`characters.${sel}.secret`, 'Secret', { rows: 2 })}${field(`characters.${sel}.relationships`, 'Key relationships', { rows: 2 })}</div>
        ${st && st.lines.length ? `<div><div class="eyebrow" style="margin-bottom:6px">How they sound so far</div><div class="voice">${esc(st.lines.slice(0, 6).join('\n'))}</div></div>` : ''}
        <div class="row" id="del-char"><button class="btn small danger" data-act="del-char">Remove this character</button></div></div>`;
    }
    return `<div class="view"><div class="hdr"><div><div class="eyebrow">Step 3</div><h1 class="vtitle">Cast</h1><p class="lede">Give every major character a want, a need and a flaw that puts them in conflict with someone else.</p></div>
      <button class="btn primary" data-act="add-char">Add a character</button></div>
      ${missing.length ? `<div class="banner"><span>Speaking in your script without a profile:</span><span class="row">${missing.slice(0, 8).map((n) => `<button class="btn small" data-act="add-char" data-name="${esc(n)}">+ ${esc(n)}</button>`).join('')}</span></div>` : ''}
      <div class="cast"><div class="cast-list" role="tablist">${list.map((ch, k) => `<button role="tab" data-act="sel-char" data-i="${k}" aria-selected="${k === sel}"><b>${esc(ch.name || 'Unnamed')}</b><small>${esc(ch.role || '')}${ch.want ? ' · wants ' + esc(ch.want.slice(0, 40)) : ''}</small></button>`).join('')}</div>${detail}</div></div>`;
  },
  onInput(t) {
    if (/\.name$/.test(t.dataset.k) || /\.role$/.test(t.dataset.k)) {
      const b = $(`.cast-list [data-i="${state.castSel}"]`);
      const ch = P.characters[state.castSel];
      if (b && ch) b.innerHTML = `<b>${esc(ch.name || 'Unnamed')}</b><small>${esc(ch.role || '')}</small>`;
    }
  },
};

function sceneRanges() {
  const s = P.script;
  const starts = [];
  s.forEach((b, i) => { if (b.t === 'scene') starts.push(i); });
  return starts.map((st, k) => ({ start: st, end: (starts[k + 1] ?? s.length) - 1, block: s[st] }));
}
function moveScene(from, to) {
  const r = sceneRanges();
  if (to < 0 || to >= r.length || from === to) return;
  const chunk = P.script.slice(r[from].start, r[from].end + 1);
  P.script.splice(r[from].start, chunk.length);
  const r2 = sceneRanges();
  const at = to >= r2.length ? P.script.length : r2[to].start;
  P.script.splice(at, 0, ...chunk);
  layoutDirty = true;
  touch(true);
}

Views.scenes = {
  render() {
    const lay = getLayout(), an = getAnalysis();
    const ranges = sceneRanges();
    const cards = ranges.map((r, k) => {
      const b = r.block;
      const sc = lay.scenes.find((x) => x.id === b.id);
      const card = P.cards[b.id] || {};
      const who = [...new Set(P.script.slice(r.start, r.end + 1).filter((x) => x.t === 'character' && x.x.trim()).map((x) => x.x.replace(/\s*\(.*?\)/g, '').trim().toUpperCase()))];
      return `<article class="card" draggable="true" data-scene="${k}">
        <div class="top"><span class="no">${k + 1}</span><span class="muted num" style="font-size:12px">${sc ? `p. ${sc.page} · ${eighths(sc.lines)} pg` : 'empty'}</span></div>
        <input class="slug" type="text" id="slug-${b.id}" data-slug="${b.id}" value="${esc(b.x)}" placeholder="INT. LOCATION - DAY" aria-label="Scene heading">
        <div class="who">${who.length ? esc(who.join(', ')) : 'No dialogue yet'}</div>
        <div class="lab"><span>Goal</span><input class="line" type="text" id="cg-${b.id}" data-k="cards.${b.id}.goal" value="${esc(card.goal || '')}" placeholder="What does someone want?"></div>
        <div class="lab"><span>Conflict</span><input class="line" type="text" id="cc-${b.id}" data-k="cards.${b.id}.conflict" value="${esc(card.conflict || '')}" placeholder="What's in the way?"></div>
        <div class="lab"><span>Turn</span><span class="turn" role="group" aria-label="Value shift">${['+-', '-+'].map((v) => `<button data-act="turn" data-id="${b.id}" data-v="${v}" aria-pressed="${card.turn === v}">${v === '+-' ? '+ → –' : '– → +'}</button>`).join('')}</span></div>
        <div class="row" style="justify-content:space-between;margin-top:4px"><span class="row" style="gap:2px"><button class="btn ghost small" data-act="move" data-k="${k}" data-d="-1" aria-label="Move earlier" ${k === 0 ? 'disabled' : ''}>↑</button><button class="btn ghost small" data-act="move" data-k="${k}" data-d="1" aria-label="Move later" ${k === ranges.length - 1 ? 'disabled' : ''}>↓</button></span><button class="btn small" data-act="write-scene" data-id="${b.id}">Write</button></div>
      </article>`;
    }).join('');
    void an;
    return `<div class="view wide" style="max-width:1400px"><div class="hdr"><div><div class="eyebrow">Step 4</div><h1 class="vtitle">Scene board</h1>
      <p class="lede">Every card is a scene in your script. Drag or use the arrows to reorder: the script moves with it. Each scene needs a goal, a conflict and a turn.</p></div>
      <button class="btn primary" data-act="add-scene">Add a scene at the end</button></div>
      <div class="board"><div class="cards" id="cards">${cards || '<p class="muted">No scenes yet. Add one, or promote an idea from the pile.</p>'}</div>
      <aside class="pile"><div class="spread"><h2 class="sec">Idea pile</h2><button class="btn small" data-act="add-idea">New idea</button></div>
        <p class="sub" style="margin:0">Scenes you might use. Promote one to add it to the end of the script.</p>
        ${P.ideas.map((it, k) => `<div class="card"><textarea class="short" id="idea-${it.id}" data-k="ideas.${k}.text" rows="2" aria-label="Idea" style="border:0;background:transparent;padding:0">${esc(it.text || '')}</textarea>
          <div class="row" style="justify-content:space-between"><button class="btn small" data-act="promote" data-i="${k}">Add to script</button><button class="btn ghost small" data-act="del-idea" data-i="${k}" aria-label="Delete idea">✕</button></div></div>`).join('')}
      </aside></div></div>`;
  },
  after(main) {
    let dragFrom = null;
    main.addEventListener('dragstart', (e) => { const c = e.target.closest('.card[data-scene]'); if (!c) return; dragFrom = +c.dataset.scene; c.classList.add('dragging'); e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', String(dragFrom)); });
    main.addEventListener('dragend', () => $$('.card', main).forEach((c) => c.classList.remove('dragging', 'drop')));
    main.addEventListener('dragover', (e) => { const c = e.target.closest('.card[data-scene]'); if (!c || dragFrom == null) return; e.preventDefault(); $$('.card.drop', main).forEach((x) => x.classList.remove('drop')); c.classList.add('drop'); });
    main.addEventListener('drop', (e) => { const c = e.target.closest('.card[data-scene]'); if (!c || dragFrom == null) return; e.preventDefault(); moveScene(dragFrom, +c.dataset.scene); dragFrom = null; renderView(); });
  },
};

/* ---------- Script view ---------- */
Views.script = {
  render() {
    return `<div class="script-view ${state.focus ? 'focus' : ''}" id="sv">
      <nav class="nav" id="scene-nav" aria-label="Scenes"></nav>
      <div class="desk">
        <div class="toolbar"><div class="els" id="els" ${state.preview ? 'hidden' : ''}>${ELEMENTS.map(([k, label], n) => `<button data-el="${k}" aria-pressed="false" title="${label} (Alt+${n + 1})">${label}<kbd>⌥${n + 1}</kbd></button>`).join('')}</div>
          <span style="flex:1">${state.preview ? '<span class="muted" style="font-size:13px">Pages as they\'ll print: US Letter, Courier 12pt.</span>' : ''}</span>
          <button class="btn small ${state.preview ? 'primary' : ''}" data-act="preview" aria-pressed="${state.preview}">${state.preview ? 'Back to writing' : 'See pages'}</button>
          <button class="btn small" data-act="focus" aria-pressed="${state.focus}">${state.focus ? 'Show panels' : 'Focus'}</button></div>
        <div class="sheet" id="sheet"></div>
        <div class="status" id="status"></div>
      </div>
      <aside class="companion" id="companion" aria-label="Scene companion"></aside></div>`;
  },
  after() {
    $('#main').style.overflow = 'hidden';
    const sheet = $('#sheet');
    if (state.preview) {
      sheet.innerHTML = `<div class="pages" id="pages">${pagesHTML(true)}</div>`;
      fitPages();
    } else {
      Editor.onChange = (structural) => { touch(structural); scheduleScriptRefresh(); };
      Editor.onCaret = (i) => scheduleCompanion(i);
      Editor.mount(sheet);
      const rev = REVISIONS.find(([r]) => r === P.revision);
      Editor.el.style.setProperty('--rev', rev ? rev[1] : 'transparent');
      const target = state.jump != null ? state.jump : 0;
      state.jump = null;
      setTimeout(() => { if (!Editor.el) return; Editor.el.focus(); Editor.setCaret(target, target ? 0 : (P.script[0].x || '').length); if (target) Editor.scrollTo(target, true); }, 30);
    }
    refreshScriptChrome();
    renderCompanion(0, true);
  },
};

let scriptTimer = null, compTimer = null, lastComp = { scene: -2, page: -1 };
function scheduleScriptRefresh() { clearTimeout(scriptTimer); scriptTimer = setTimeout(refreshScriptChrome, 350); }
function scheduleCompanion(i) { clearTimeout(compTimer); compTimer = setTimeout(() => { renderCompanion(i); updateToolbar(i); highlightNav(i); }, 120); }

function refreshScriptChrome() {
  if (state.view !== 'script') return;
  const lay = getLayout(), an = getAnalysis();
  const c = Editor.el ? Editor.caret() : null;
  const i = c ? c.i : 0;
  const page = lay.srcPage[i] || 1;
  const target = P.mode === 'film' ? P.targetPages : TV_STRUCTURE[P.tvFormat].at(-1)[2];
  $('#status').innerHTML = `<span>Page <b>${lay.pageCount ? page : 0}</b> of ${lay.pageCount} (~${target})</span><span><b>${lay.scenes.length}</b> scenes</span><span><b>${an.words.toLocaleString()}</b> words</span><span>~<b>${lay.pageCount}</b> min</span><span>${esc(P.revision)} draft</span><span class="muted">Tab: next element · Enter: new line · ⌥1–7: set element</span>`;
  // Scene navigator
  let html = '', n = 0;
  P.script.forEach((b, k) => {
    if (b.t === 'section' && b.x.trim()) html += `<div class="sec">${esc(b.x)}</div>`;
    if (b.t === 'scene') { n++; html += `<a href="#script" data-jump="${k}"><span class="sn">${n}</span><span class="sh">${esc(b.x || 'Untitled scene')}</span></a>`; }
  });
  $('#scene-nav').innerHTML = `<div class="sec">Scenes</div>${html || '<p class="muted" style="font-size:12.5px;padding:4px 8px">Scene headings you write appear here.</p>'}`;
  highlightNav(i);
  // Lint underline
  if (Editor.el) {
    const flagged = new Set(lint(P, lay, an).filter((l) => l.sev === 'warn').map((l) => l.id));
    Array.from(Editor.el.children).forEach((d) => d.classList.toggle('lint', flagged.has(d.dataset.id)));
  }
  renderCompanion(i, true);
  updateToolbar(i);
}
function updateToolbar(i) {
  const b = P.script[i];
  $$('#els button').forEach((btn) => btn.setAttribute('aria-pressed', String(!!b && btn.dataset.el === b.t)));
}
function highlightNav(i) {
  const ranges = sceneRanges();
  const k = ranges.findIndex((r) => i >= r.start && i <= r.end);
  $$('#scene-nav a').forEach((a, n) => a.classList.toggle('cur', n === k));
}
function sceneIndexAt(i) { return sceneRanges().findIndex((r) => i >= r.start && i <= r.end); }

function renderCompanion(i, force) {
  const box = $('#companion');
  if (!box) return;
  const lay = getLayout(), an = getAnalysis();
  const page = Math.max(1, lay.srcPage[i] || 1);
  const k = sceneIndexAt(i);
  if (!force && lastComp.scene === k && lastComp.page === page) return;
  if (box.contains(document.activeElement) && document.activeElement.matches('input,textarea') && lastComp.scene === k) return;
  lastComp = { scene: k, page };
  const beat = currentBeat(P, page);
  const r = sceneRanges()[k];
  const card = r ? (P.cards[r.block.id] || {}) : null;
  const issues = lint(P, lay, an).filter((l) => r && l.i >= r.start && l.i <= r.end);
  const stuck = STUCK[(state.stuck ?? Math.floor(Math.random() * STUCK.length)) % STUCK.length];
  const target = P.mode === 'film' ? P.targetPages : TV_STRUCTURE[P.tvFormat].at(-1)[2];
  box.innerHTML = `
    <section class="where"><h3>You are here</h3><b class="num">p. ${page}</b><span class="muted"> of ~${target}</span>
      <div class="beatname">${esc(beat.name)} <span class="pill">${esc(beat.range)}</span></div><p>${esc(beat.desc)}</p></section>
    <div class="prompt">${esc(beat.q)}</div>
    ${r ? `<section class="stack" style="gap:8px"><h3>Scene ${k + 1} card</h3>
      <label class="f" for="cmp-goal"><span>Goal</span><input type="text" id="cmp-goal" data-k="cards.${r.block.id}.goal" value="${esc(card.goal || '')}" placeholder="What does someone want here?"></label>
      <label class="f" for="cmp-conf"><span>Conflict</span><input type="text" id="cmp-conf" data-k="cards.${r.block.id}.conflict" value="${esc(card.conflict || '')}" placeholder="What stands in the way?"></label>
      <div class="row"><span class="eyebrow">Turn</span><span class="turn" role="group" aria-label="Value shift">${['+-', '-+'].map((v) => `<button data-act="turn" data-id="${r.block.id}" data-v="${v}" aria-pressed="${card.turn === v}">${v === '+-' ? '+ → –' : '– → +'}</button>`).join('')}</span></div></section>` : ''}
    <section><h3>Script notes ${issues.length ? `<span class="pill warn">${issues.length}</span>` : ''}</h3>
      ${issues.length ? `<div class="lints">${issues.slice(0, 6).map((l) => `<button data-jump="${l.i}"><span class="sev ${l.sev}"></span><span>${esc(l.msg)}</span></button>`).join('')}</div>` : '<p class="muted" style="font-size:13px;margin:0">Nothing flagged in this scene.</p>'}</section>
    <section><h3>Stuck?</h3><div class="prompt" style="background:var(--surface-2)">${esc(stuck)}</div><button class="btn small" data-act="stuck" style="margin-top:8px">Another prompt</button></section>
    <section><h3>Keys</h3><div class="kbd" style="grid-template-columns:auto 1fr;font-size:12.5px"><kbd class="k">Tab</kbd><span>Next element type</span><kbd class="k">Enter</kbd><span>New element (smart)</span><kbd class="k">⇧ Enter</kbd><span>Line break</span><kbd class="k">⌥1–7</kbd><span>Set element</span><kbd class="k">Ctrl/⌘ B I U</kbd><span>Bold, italic, underline</span></div></section>`;
}

function pagesHTML(withTitle) {
  const lay = getLayout();
  const runHTML = (c) => {
    let html = '', k = 0;
    while (k < c.length) {
      let j = k; const st = c[k].slice(1).join();
      while (j < c.length && c[j].slice(1).join() === st) j++;
      let t = esc(c.slice(k, j).map((x) => x[0]).join(''));
      const [, b, it, u] = c[k];
      if (b) t = `<b>${t}</b>`; if (it) t = `<i>${t}</i>`; if (u) t = `<u>${t}</u>`;
      html += t; k = j;
    }
    return html;
  };
  let out = '';
  if (withTitle && (P.title || P.author)) {
    out += `<div class="pg title"><div class="ln" style="top:3.5in;text-align:center">${esc((P.title || 'Untitled').toUpperCase())}</div>
      <div class="ln" style="top:calc(3.5in + 36pt);text-align:center">Written by</div><div class="ln" style="top:calc(3.5in + 60pt);text-align:center">${esc(P.author || '')}</div>
      <div class="ln" style="left:1.5in;width:auto;top:calc(11in - 1.9in)">${esc([P.draftDate, P.contact].filter(Boolean).join('\n'))}</div></div>`;
  }
  lay.pages.forEach((page, n) => {
    out += `<div class="pg">${n ? `<div class="pn">${n + 1}.</div>` : ''}${page.map((ln) => {
      const top = `calc(1in + ${ln.row * 12}pt)`;
      const pos = ln.align === 'right' ? `right:${8.5 - RIGHT}in` : `left:${ln.x}in`;
      return `<div class="ln" style="top:${top};${pos}">${runHTML(ln.c)}</div>`;
    }).join('')}</div>`;
  });
  return out || '<div class="pg"></div>';
}
function fitPages() {
  const pages = $('#pages');
  if (!pages) return;
  const w = $('#sheet').clientWidth - 32;
  pages.style.zoom = String(clamp(w / 816, 0.35, 1));
}

/* ---------- Rewrite ---------- */
Views.rewrite = {
  render() {
    const sub = subOf('rewrite', 'check');
    let body = '';
    if (sub === 'check') {
      let n = 0;
      body = `<div class="grid2">${REWRITE_PASSES.map(([name, items]) => `<div class="panel"><h2 class="sec">${name} pass</h2>${items.map((it) => { const key = `${name}-${n++}`; const on = !!P.checklist[key]; return `<label class="check ${on ? 'done' : ''}"><input type="checkbox" data-k="checklist.${key}" ${on ? 'checked' : ''}><span>${esc(it)}</span></label>`; }).join('')}</div>`).join('')}</div>`;
    } else if (sub === 'lint') {
      const lay = getLayout(), an = getAnalysis();
      const all = lint(P, lay, an);
      const warn = all.filter((l) => l.sev === 'warn'), tips = all.filter((l) => l.sev === 'tip');
      const row = (l) => `<button data-jump="${l.i}"><span class="sev ${l.sev}"></span><span>${l.i >= 0 ? `<b class="num">p. ${lay.srcPage[l.i] || 1}</b> · ` : ''}${esc(l.msg)}${l.i >= 0 ? `<br><span class="muted" style="font-family:var(--f-script);font-size:12px">${esc((P.script[l.i].x || '').slice(0, 90))}</span>` : ''}</span></button>`;
      body = `<p class="sub">Automatic checks for common spec-script problems. They're suggestions, not rules: tap one to jump to it in the script.</p>
        <div class="grid2"><div class="panel"><h2 class="sec">Worth fixing <span class="pill warn">${warn.length}</span></h2><div class="lints">${warn.map(row).join('') || '<p class="muted">Nothing here. Nice.</p>'}</div></div>
        <div class="panel"><h2 class="sec">Worth a look <span class="pill acc">${tips.length}</span></h2><div class="lints">${tips.map(row).join('') || '<p class="muted">Nothing here.</p>'}</div></div></div>`;
    } else if (sub === 'feedback') {
      body = `<p class="sub">Log notes from readers. When two people flag the same thing, it's real, even if their suggested fix is wrong.</p>
        <div class="panel scroll-x"><table class="grid"><thead><tr><th style="width:140px">From</th><th>Note</th><th>What I'll do</th><th style="width:50px">Done</th><th style="width:40px"></th></tr></thead><tbody>
        ${P.feedback.map((f, k) => `<tr><td><input type="text" id="fb-${k}-from" data-k="feedback.${k}.from" value="${esc(f.from || '')}" aria-label="From"></td><td><textarea id="fb-${k}-note" data-k="feedback.${k}.note" rows="2" aria-label="Note">${esc(f.note || '')}</textarea></td><td><textarea id="fb-${k}-plan" data-k="feedback.${k}.plan" rows="2" aria-label="Plan">${esc(f.plan || '')}</textarea></td><td style="text-align:center;padding-top:12px"><input type="checkbox" data-k="feedback.${k}.done" ${f.done ? 'checked' : ''} aria-label="Done"></td><td><button class="btn ghost small" data-act="del-fb" data-i="${k}" aria-label="Remove">✕</button></td></tr>`).join('')}
        </tbody></table><button class="btn small" data-act="add-fb" style="margin-top:10px">Add a note</button></div>`;
    } else {
      body = field('notes', 'Notes', { rows: 18 });
    }
    return `<div class="view"><div class="hdr"><div><div class="eyebrow">Step 6</div><h1 class="vtitle">Rewrite</h1><p class="lede">Writing is rewriting. Do one pass per focus, and log what readers tell you.</p></div></div>
      ${tabs('rewrite', [['check', 'Rewrite checklist'], ['lint', 'Script notes'], ['feedback', 'Feedback log'], ['notes', 'Notes']])}${body}</div>`;
  },
  onInput(t) { if (t.type === 'checkbox' && t.closest('.check')) t.closest('.check').classList.toggle('done', t.checked); },
};

/* ---------- Guide ---------- */
Views.guide = {
  render() {
    const sub = subOf('guide', 'keys');
    let body = '';
    if (sub === 'keys') {
      body = `<div class="grid2"><div class="panel"><h2 class="sec">Writing in the editor</h2><p class="sub">Type normally. The editor formats each element to industry margins as you go.</p><div class="kbd">
        <kbd class="k">Enter</kbd><span>New element, picking the likely next one: after a scene heading comes action; after a character, dialogue; after dialogue, action.</span>
        <kbd class="k">Tab</kbd><span>Change the current element: action → character → dialogue → parenthetical → transition → scene heading.</span>
        <kbd class="k">Shift + Tab</kbd><span>Change it the other way.</span>
        <kbd class="k">Enter on empty line</kbd><span>Turns an empty character or dialogue line back into action.</span>
        <kbd class="k">Shift + Enter</kbd><span>Line break inside action or dialogue.</span>
        <kbd class="k">Alt/⌥ + 1–7</kbd><span>Scene heading, action, character, parenthetical, dialogue, transition, act/section.</span>
        <kbd class="k">Ctrl/⌘ + B, I, U</kbd><span>Bold, italic, underline the selection.</span>
        <kbd class="k">Ctrl/⌘ + Z</kbd><span>Undo. Ctrl/⌘ + Shift + Z to redo.</span></div></div>
        <div class="panel"><h2 class="sec">Shortcuts that type for you</h2><div class="kbd">
        <span>INT. / EXT.</span><span>Start an action line with INT. or EXT. and it becomes a scene heading.</span>
        <span>Locations</span><span>In a scene heading, locations you've used before are suggested. Tab accepts.</span>
        <span>Time of day</span><span>Type “ - ” after the location for DAY, NIGHT, CONTINUOUS…</span>
        <span>Character names</span><span>Names you've used, and your cast list, are suggested as you type.</span>
        <span>Extensions</span><span>Type “(” after a name for V.O., O.S. or CONT'D.</span>
        <span>(</span><span>Starting a dialogue line with “(” makes it a parenthetical.</span>
        <span>Paste</span><span>Paste a scene from any Fountain or plain-text screenplay and it's formatted.</span></div></div></div>`;
    } else if (sub === 'format') {
      body = `<div class="grid2"><div class="fmt">INT. DINER - NIGHT

Rain streaks the windows. MAYA (30s,
tired eyes) slides into a booth.

                      MAYA
                (quietly)
          You said you'd come alone.

                      OTIS
          I did. They followed me.

                                    CUT TO:</div>
        <div class="panel scroll-x"><table class="grid"><thead><tr><th>Element</th><th>From left</th><th>Rules</th></tr></thead><tbody>
          <tr><td><b>Scene heading</b></td><td>1.5"</td><td>INT./EXT. LOCATION - DAY/NIGHT, in caps.</td></tr>
          <tr><td><b>Action</b></td><td>1.5"</td><td>Present tense, what we see and hear. Four lines or fewer.</td></tr>
          <tr><td><b>Character</b></td><td>3.7"</td><td>Caps. Add (V.O.), (O.S.) or (CONT'D) as needed.</td></tr>
          <tr><td><b>Parenthetical</b></td><td>3.1"</td><td>Brief and lowercase. Use sparingly.</td></tr>
          <tr><td><b>Dialogue</b></td><td>2.5"</td><td>Up to 3.5" wide. No quotation marks.</td></tr>
          <tr><td><b>Transition</b></td><td>right</td><td>CUT TO: and similar, ending at 7.5". Rarely needed.</td></tr></tbody></table>
          <p class="sub" style="margin-top:12px">US Letter, Courier 12pt, 1" top and bottom margins, 1.5" left, 1" right, page numbers top right. One page is about one minute of screen time. Draftroom's PDF export follows all of this, including (MORE) and (CONT'D) when a speech crosses a page break.</p></div></div>`;
    } else if (sub === 'fountain') {
      body = `<div class="panel"><p class="sub">Fountain is the plain-text screenplay format that Highland, WriterSolo, Final Draft (via import), Slugline and many others read. Draftroom exports and imports it, and pasting Fountain into the editor formats it.</p>
        <div class="scroll-x"><table class="grid"><thead><tr><th>Element</th><th>Type this</th><th>Notes</th></tr></thead><tbody>
        ${[['Scene heading', 'INT. DINER - NIGHT', 'Starts with INT, EXT, EST or I/E, or force it with a leading period'], ['Character', 'MAYA', 'A line in caps, followed by dialogue'], ['Parenthetical', '(quietly)', 'On its own line under the character'], ['Transition', 'CUT TO:', 'Caps ending in TO:, or force with >'], ['Emphasis', '*italic* **bold** _underline_', ''], ['Section', '# ACT ONE', 'Not printed; shows in the scene list'], ['Note', '[[check this]]', 'Not printed'], ['Title page', 'Title: My Script', 'Key: value lines at the very top']]
          .map(([a, b, c]) => `<tr><td><b>${a}</b></td><td style="font-family:var(--f-script)">${esc(b)}</td><td class="muted">${c}</td></tr>`).join('')}</tbody></table></div></div>`;
    } else {
      body = `<div class="grid2"><div class="panel scroll-x"><h2 class="sec">Typical lengths</h2><table class="grid"><thead><tr><th>Type</th><th>Pages</th><th>Notes</th></tr></thead><tbody>
        ${[['Feature: comedy / horror', '90–105', 'Shorter and faster.'], ['Feature: drama / thriller', '100–120', ''], ['Half-hour, single-camera', '25–35', 'Film-style format.'], ['Half-hour, multi-camera', '45–55', 'Double-spaced dialogue, caps action.'], ['One-hour drama', '50–65', 'Teaser plus 4–5 acts; streaming often drops act breaks.'], ['Short film', '5–15', 'Festivals favor under 15 minutes.']]
          .map(([a, b, c]) => `<tr><td><b>${a}</b></td><td class="num">${b}</td><td class="muted">${c}</td></tr>`).join('')}</tbody></table></div>
        <div class="panel"><h2 class="sec">Words to know</h2><dl class="gloss">
        ${[['Spec script', 'Written on your own to sell or show. No camera directions or scene numbers.'], ['Slugline', 'Another name for the scene heading.'], ['Beat', 'A single story moment, or a pause in dialogue.'], ['Button', 'A strong last line or image that ends a scene.'], ['Cold open', 'The pre-credits scene of a TV episode.'], ['Act out', 'The cliffhanger at the end of a TV act.'], ['Bible', 'The document describing a series: world, characters, arcs, future seasons.'], ['Eighths', 'Scene length in eighths of a page, used to schedule a shoot.'], ['Revision colors', 'Changed pages in production drafts are printed on a new color: white, blue, pink, yellow, green, goldenrod…']]
          .map(([a, b]) => `<dt>${a}</dt><dd>${b}</dd>`).join('')}</dl></div></div>`;
    }
    return `<div class="view"><div class="hdr"><div><div class="eyebrow">Reference</div><h1 class="vtitle">Guide</h1><p class="lede">How the editor works, and the format rules readers expect.</p></div></div>
      ${tabs('guide', [['keys', 'Editor & shortcuts'], ['format', 'Screenplay format'], ['fountain', 'Fountain'], ['lengths', 'Lengths & terms']])}${body}</div>`;
  },
};

/* =====================================================================
   Export: PDF, print, import
   ===================================================================== */
function exportPDF() {
  const J = window.jspdf && window.jspdf.jsPDF;
  if (!J) { if (!inFrame) { printScript(); return; } toast('The PDF maker didn\'t load. Check your connection and try again.'); return; }
  const lay = getLayout();
  const doc = new J({ unit: 'pt', format: 'letter' });
  doc.setProperties({ title: P.title || 'Screenplay', author: P.author || '', creator: 'Draftroom' });
  const W = 612;
  const style = (b, i) => (b && i ? 'bolditalic' : b ? 'bold' : i ? 'italic' : 'normal');
  const draw = (c, x, y, align) => {
    const width = c.length * 7.2;
    if (align === 'right') x -= width;
    if (align === 'center') x -= width / 2;
    let k = 0;
    while (k < c.length) {
      let j = k; const st = c[k].slice(1).join();
      while (j < c.length && c[j].slice(1).join() === st) j++;
      const [, b, i, u] = c[k];
      const run = c.slice(k, j).map((q) => q[0]).join('');
      doc.setFont('courier', style(b, i));
      doc.text(run, x + k * 7.2, y);
      if (u) doc.line(x + k * 7.2, y + 1.5, x + j * 7.2, y + 1.5);
      k = j;
    }
  };
  doc.setFontSize(12);
  let first = true;
  if (P.title || P.author) {
    const center = (t, y) => draw(styled(t), W / 2, y, 'center');
    center((P.title || 'Untitled').toUpperCase(), 252);
    center('Written by', 288);
    center(P.author || '', 312);
    [P.draftDate, ...(P.contact || '').split('\n')].filter(Boolean).forEach((t, k) => draw(styled(t), 108, 648 + k * 12));
    doc.outline.add(null, 'Title page', { pageNumber: 1 });
    first = false;
  }
  const offset = first ? 0 : 1;
  lay.pages.forEach((page, n) => {
    if (!first || n) doc.addPage();
    if (n) { doc.setFont('courier', 'normal'); doc.text(`${n + 1}.`, RIGHT * 72, 36, { align: 'right' }); }
    for (const ln of page) draw(ln.c, ln.x * 72, 72 + ln.row * 12 + 9.6, ln.align);
  });
  for (const o of lay.outline) doc.outline.add(null, o.title, { pageNumber: o.page + offset });
  saveFile(`${fileBase()}.pdf`, doc.output('arraybuffer'), 'application/pdf');
}

function printScript() {
  $('#print-root').innerHTML = pagesHTML(true);
  window.print();
}

function importFile(file) {
  const r = new FileReader();
  r.onload = () => {
    const text = String(r.result || '');
    try {
      if (/\.json$/i.test(file.name)) {
        const p = JSON.parse(text);
        if (!p || !Array.isArray(p.script)) throw new Error('not a project');
        p.id = uid(); delete p.example;
        addProject(p); navigate('home'); toast(`Imported “${p.title || 'Untitled'}”.`);
      } else {
        const f = parseFountain(text);
        const p = blankProject(f.title || file.name.replace(/\.[^.]+$/, ''));
        Object.assign(p, { author: f.author, contact: f.contact, draftDate: f.draftDate, script: f.blocks.length ? f.blocks : p.script });
        f.blocks.forEach((b) => { if (b.t === 'scene' && b.syn) p.cards[b.id] = { goal: b.syn }; });
        addProject(p); navigate('script'); toast(`Imported ${plural(f.blocks.filter((b) => b.t === 'scene').length, 'scene')}.`);
      }
    } catch (e) { toast('That file couldn\'t be read. Import a .fountain, .txt or Draftroom backup (.json).'); }
  };
  r.readAsText(file);
}
