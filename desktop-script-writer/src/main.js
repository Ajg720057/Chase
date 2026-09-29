/* =====================================================================
   Assistant: AI notes when this page can ask Claude, prompts otherwise
   ===================================================================== */
const Assist = { history: [], busy: null };

function storyContext() {
  const i = P.idea;
  const lines = [
    `Project: "${P.title || 'Untitled'}" (${P.mode === 'film' ? `feature film, target ${P.targetPages} pages` : `${P.tvFormat} TV episode`}).`,
    chosenLogline() && `Logline: ${chosenLogline()}`,
    i.genre && `Genre: ${i.genre}. Tone: ${i.tone || 'n/a'}.`,
    i.theme && `Theme: ${i.theme}`,
    i.controlling && `Controlling idea: ${i.controlling}`,
    i.synopsis && `Synopsis: ${i.synopsis.slice(0, 1800)}`,
  ].filter(Boolean);
  if (P.mode === 'tv' && P.tv.engine) lines.push(`Series engine: ${P.tv.engine}`);
  if (P.characters.length) lines.push('Characters:\n' + P.characters.map((c) => `- ${c.name || 'Unnamed'} (${c.role || 'role n/a'}${c.age ? ', ' + c.age : ''}): wants ${c.want || '?'}; needs ${c.need || '?'}; flaw: ${c.flaw || '?'}`).join('\n'));
  const beats = BEATS.map((b, k) => (P.beats[k] ? `- ${b[0]}: ${P.beats[k]}` : '')).filter(Boolean);
  if (beats.length) lines.push('Planned beats:\n' + beats.join('\n'));
  return lines.join('\n');
}
function sceneFountain(k) {
  const r = sceneRanges()[k];
  if (!r) return '';
  const tmp = { ...P, title: '', author: '', contact: '', draftDate: '', script: P.script.slice(r.start, r.end + 1) };
  return toFountain(tmp).split('\n').slice(3).join('\n').trim().slice(0, 7000);
}
function currentSceneIndex() {
  const c = Editor.el && Editor.caret();
  if (c) return sceneIndexAt(c.i);
  return Math.max(0, sceneRanges().length - 1);
}
function sceneContext() {
  const k = currentSceneIndex();
  const r = sceneRanges()[k];
  if (!r) return 'The writer has not started a scene yet.';
  const lay = getLayout();
  const page = lay.srcPage[r.start] || 1;
  const beat = currentBeat(P, page);
  const card = P.cards[r.block.id] || {};
  return `Current scene (scene ${k + 1}, page ${page}, beat: ${beat.name}):\n${card.goal ? `Scene goal: ${card.goal}\n` : ''}${card.conflict ? `Scene conflict: ${card.conflict}\n` : ''}\`\`\`\n${sceneFountain(k)}\n\`\`\``;
}
function firstPages(n) {
  const lay = getLayout();
  const upto = lay.srcPage.findIndex((p) => p > n);
  const tmp = { ...P, script: P.script.slice(0, upto < 0 ? P.script.length : upto) };
  return toFountain(tmp).slice(0, 14000);
}

const PRESETS = {
  home: [['What should I work on next?', () => `${storyContext()}\n\nDraft length: ${getLayout().pageCount} pages. Based on what's planned and written, what are the two or three most useful things I could work on next, and why?`]],
  idea: [
    ['Five sharper loglines', () => `${storyContext()}\n\nLogline builder answers: ${JSON.stringify({ protagonist: P.idea.protagonist, incident: P.idea.incident, goal: P.idea.goal, antagonist: P.idea.antagonist, stakes: P.idea.stakes, hook: P.idea.hook })}\n\nWrite five distinct logline options (25–35 words each) in different flavors, then say in one line which is strongest and why.`],
    ['Stress-test my premise', () => `${storyContext()}\n\nPlay a tough development executive. What are the three biggest weaknesses or unanswered questions in this premise, and one concrete way to fix each?`],
  ],
  story: [
    ['Suggest beats from my idea', () => `${storyContext()}\n\nPropose a beat sheet for this story using the 15 Save the Cat beats, one or two sentences each. Keep what I've already planned and fill the gaps.`],
    ['Find the weak spot', () => `${storyContext()}\n\nWhere is this structure weakest (for example a passive hero, a soft midpoint, or an ending that isn't earned)? Give specific fixes.`],
  ],
  cast: [
    ['Deepen this character', () => { const c = P.characters[state.castSel] || {}; return `${storyContext()}\n\nFocus on ${c.name || 'this character'}: ${JSON.stringify(c)}\n\nSuggest a sharper want/need contradiction, a specific wound, and three behaviors that show the flaw on screen without dialogue.`; }],
    ['Make the voices distinct', () => `${storyContext()}\n\nSample dialogue by character:\n${Object.values(getAnalysis().chars).slice(0, 6).map((c) => `${c.name}: ${c.lines.slice(0, 5).join(' / ')}`).join('\n')}\n\nHow could each main character sound more distinct? Give each a speech pattern and one rewritten sample line.`],
  ],
  scenes: [
    ['Spot missing scenes', () => `${storyContext()}\n\nScene list:\n${sceneRanges().map((r, k) => `${k + 1}. ${r.block.x} (goal: ${(P.cards[r.block.id] || {}).goal || '?'})`).join('\n')}\n\nWhat scenes seem to be missing, redundant, or out of order? Be specific.`],
  ],
  script: [
    ['Notes on this scene', () => `${storyContext()}\n\n${sceneContext()}\n\nGive notes on this scene: is the goal clear, is there conflict, does it turn, does it enter late and leave early? Three to five specific notes, most important first.`],
    ['Sharpen the dialogue', () => `${storyContext()}\n\n${sceneContext()}\n\nPoint to the three lines of dialogue that are most on-the-nose or flat, and offer a sharper alternative for each with more subtext. Keep each character's voice.`],
    ['What could happen next?', () => `${storyContext()}\n\n${sceneContext()}\n\nOffer three different options for the next scene, each in two or three sentences, ranging from expected to surprising. Say which pushes the story hardest.`],
    ['Is this scene turning?', () => `${storyContext()}\n\n${sceneContext()}\n\nWhat value is at stake in this scene, where does it start (+ or –) and where does it end? If it doesn't turn, suggest how it could.`],
  ],
  rewrite: [['Coverage on my first pages', () => `${storyContext()}\n\nFirst pages of the draft:\n\`\`\`\n${firstPages(10)}\n\`\`\`\n\nWrite brief studio-style coverage for these opening pages: premise, character, dialogue, structure, each rated Excellent/Good/Fair/Poor with one line of why, then the three most important notes.`]],
  guide: [['Explain a formatting rule', () => 'Explain the most common screenplay formatting mistakes new writers make, with a short Fountain example of each fixed.']],
};
const SYSTEM = 'You are a script consultant working inside Draftroom, a screenwriting app. The writer owns the story: give specific, practical notes and options, never rewrite their whole script, and respect their voice. Keep answers under 350 words unless asked for more. Use short headings or bullets. When you show sample lines, format them as screenplay text in Fountain inside ``` fences.\n\n';

function mdLite(s) {
  const blocks = String(s).split(/```(?:\w+)?\n?/);
  return blocks.map((b, k) => {
    if (k % 2) return `<pre>${esc(b.replace(/\n$/, ''))}</pre>`;
    const lines = esc(b).split('\n');
    let html = '', list = null;
    const inline = (t) => t.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/(^|[^*])\*(?!\s)(.+?)\*/g, '$1<i>$2</i>');
    for (const l of lines) {
      const m = l.match(/^\s*(?:[-*•]|\d+[.)])\s+(.*)/);
      if (m) { if (!list) { list = /^\s*\d/.test(l) ? 'ol' : 'ul'; html += `<${list}>`; } html += `<li>${inline(m[1])}</li>`; continue; }
      if (list) { html += `</${list}>`; list = null; }
      const h = l.match(/^#{1,4}\s+(.*)/);
      if (h) html += `<h4>${inline(h[1])}</h4>`; else if (l.trim()) html += `<p>${inline(l)}</p>`;
    }
    if (list) html += `</${list}>`;
    return html;
  }).join('');
}

function refreshAssistant() {
  const d = $('#drawer');
  if (d.hidden) return;
  const out = $('#assist-out');
  const presets = PRESETS[state.view] || [];
  const ai = !!caps.sample;
  $('#assist-form').hidden = !ai;
  let html = '';
  if (ai) {
    html += `<div><div class="eyebrow" style="margin-bottom:6px">Ask about ${esc(VIEWS.find(([v]) => v === state.view)[1].toLowerCase())}</div><div class="presets">${presets.map(([l], k) => `<button data-preset="${k}">${esc(l)}</button>`).join('')}</div></div>`;
    html += Assist.history.map((h) => `<div class="answer"><div class="q">${esc(h.q)}</div><div class="a">${h.a ? mdLite(h.a) : '<span class="muted">Thinking…</span>'}</div>${h.a && !h.busy ? `<button class="btn small ghost" data-copy="${Assist.history.indexOf(h)}">Copy</button>` : ''}</div>`).reverse().join('');
    $('#assist-note').textContent = 'Notes use your project details.';
  } else {
    const k = (state.stuck ?? 0) % STUCK.length;
    html += `<div class="deck">${esc(STUCK[k])}</div><button class="btn small" data-act="stuck">Another prompt</button>
      <p class="sub">${inFrame ? 'AI notes aren\'t available in this view.' : 'AI notes (feedback on scenes, logline options, dialogue punch-ups) work when you open Draftroom from its claude.ai link.'} The prompts above, the beat guidance in the script companion and the automatic script notes work everywhere.</p>`;
  }
  out.innerHTML = html;
}

async function ask(q, input) {
  if (!caps.sample || Assist.busy) return;
  const entry = { q, a: '', busy: true };
  Assist.history.push(entry);
  const ctl = new AbortController();
  Assist.busy = ctl;
  $('#assist-send').textContent = 'Stop';
  refreshAssistant();
  try {
    const r = await caps.sample(SYSTEM + input, { cache: false, signal: ctl.signal, onText: ({ text }) => { entry.a = text; const el = $$('#assist-out .answer .a')[0]; if (el) el.innerHTML = mdLite(text); } });
    entry.a = r.text + (r.truncated ? '\n\n(Answer cut short.)' : '');
  } catch (e) {
    const code = e && e.code;
    if (code === 'not_granted') { caps.sample = null; entry.a = 'AI notes weren\'t allowed, so they\'re switched off for this visit.'; }
    else if (code === 'cancelled') entry.a = (entry.a || '') + '\n\n(Stopped.)';
    else if (code === 'rate_limited') entry.a = 'Too many requests just now. Wait a minute and try again.';
    else entry.a = (e && e.text) || 'Something went wrong getting notes. Try again.';
  } finally {
    entry.busy = false;
    Assist.busy = null;
    $('#assist-send').textContent = 'Ask';
    refreshAssistant();
  }
}

/* =====================================================================
   Events
   ===================================================================== */
function bindShell() {
  $('#main').addEventListener('input', (e) => {
    const t = e.target;
    if (t.dataset.slug) {
      const b = P.script.find((x) => x.id === t.dataset.slug);
      if (b) { b.x = t.value.toUpperCase(); touch(true); }
      return;
    }
    if (!t.dataset.k || (Editor.el && Editor.el.contains(t))) return;
    let v = t.type === 'checkbox' ? t.checked : t.type === 'radio' ? t.value : t.value;
    if (t.type === 'number') v = +t.value;
    setPath(P, t.dataset.k, v);
    touch();
    const view = Views[state.view];
    if (view.onInput) view.onInput(t);
  });
  $('#main').addEventListener('change', (e) => {
    const t = e.target;
    if (t.tagName === 'SELECT' && t.dataset.k) { setPath(P, t.dataset.k, t.value); touch(); const v = Views[state.view]; if (v.onInput) v.onInput(t); }
  });
  document.addEventListener('click', (e) => {
    const go = e.target.closest('[data-go]');
    if (go) { navigate(go.dataset.go); return; }
    const tab = e.target.closest('[data-tab]');
    if (tab) { const [v, s] = tab.dataset.tab.split(':'); state.sub[v] = s; renderView(); return; }
    const open = e.target.closest('[data-open]');
    if (open && !open.closest('#menu')) { openProject(open.dataset.open); return; }
    const jump = e.target.closest('[data-jump]');
    if (jump) { e.preventDefault(); jumpTo(+jump.dataset.jump); return; }
    const el = e.target.closest('[data-el]');
    if (el && Editor.el) { e.preventDefault(); const c = Editor.caret(); Editor.el.focus(); if (!c) Editor.setCaret(P.script.length - 1, 0); Editor.setType(el.dataset.el); return; }
    const preset = e.target.closest('[data-preset]');
    if (preset) { const [label, fn] = PRESETS[state.view][+preset.dataset.preset]; ask(label, fn()); return; }
    const copy = e.target.closest('[data-copy]');
    if (copy) { const text = Assist.history[+copy.dataset.copy].a; navigator.clipboard.writeText(text).then(() => toast('Copied.'), () => toast('Copy isn\'t available here. Select the text instead.')); return; }
    const act = e.target.closest('[data-act]');
    if (act) Actions[act.dataset.act]?.(act);
  });
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 's') { e.preventDefault(); Store.flush(); toast('Saved.'); }
    if (e.key === 'Escape') closeMenu();
    const card = e.target.closest && e.target.closest('.logline-card[data-go]');
    if (card && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); navigate('idea'); }
  });
  $('#main').addEventListener('mousedown', (e) => { if (e.target.closest('[data-el]')) e.preventDefault(); });
  $('#btn-projects').addEventListener('click', (e) => projectsMenu(e.currentTarget));
  $('#btn-export').addEventListener('click', (e) => exportMenu(e.currentTarget));
  $('#btn-theme').addEventListener('click', () => {
    const cur = document.documentElement.dataset.theme;
    const dark = cur ? cur === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    const next = dark ? 'light' : 'dark';
    applyTheme(next);
    try { localStorage.setItem('draftroom.theme', next); } catch (err) { /* ignore */ }
  });
  const toggleAssist = (open) => {
    const d = $('#drawer');
    d.hidden = open === undefined ? !d.hidden : !open;
    $('#btn-assist').setAttribute('aria-pressed', String(!d.hidden));
    refreshAssistant();
    if (state.view === 'script' && state.preview) setTimeout(fitPages, 0);
  };
  $('#btn-assist').addEventListener('click', () => toggleAssist());
  $('#btn-close-assist').addEventListener('click', () => toggleAssist(false));
  $('#assist-form').addEventListener('submit', (e) => {
    e.preventDefault();
    if (Assist.busy) { Assist.busy.abort(); return; }
    const q = $('#assist-q').value.trim();
    if (!q) return;
    $('#assist-q').value = '';
    const scene = state.view === 'script' ? `\n\n${sceneContext()}` : '';
    ask(q, `${storyContext()}${scene}\n\nThe writer asks: ${q}`);
  });
  $('#assist-q').addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('#assist-form').requestSubmit(); } });
  $('#assist-out').addEventListener('click', (e) => { const a = e.target.closest('[data-act="stuck"]'); if (a) { e.stopPropagation(); state.stuck = (state.stuck ?? 0) + 1; refreshAssistant(); } }, true);
  $('#file-in').addEventListener('change', (e) => { const f = e.target.files[0]; if (f) importFile(f); e.target.value = ''; });
  window.addEventListener('hashchange', () => { const v = location.hash.slice(1); if (v && v !== state.view) navigate(v); });
  window.addEventListener('resize', () => { if (state.preview) fitPages(); });
  window.addEventListener('beforeunload', () => { Store.writeLocal(); });
}

function jumpTo(i) {
  if (i < 0) return;
  if (state.view !== 'script' || state.preview) { state.preview = false; state.jump = i; navigate('script'); return; }
  Editor.el.focus();
  Editor.setCaret(i, 0);
  Editor.scrollTo(i, true);
}

const Actions = {
  'new-project': () => { const p = blankProject('Untitled'); addProject(p); navigate('home'); toast('New project created. Give it a title in Settings.'); },
  'keep-example': () => { delete P.example; touch(); renderView(); },
  import: () => $('#file-in').click(),
  'add-subplot': () => { P.subplots.push({ name: '' }); touch(); renderView(); },
  'del-subplot': (b) => { P.subplots.splice(+b.dataset.i, 1); touch(); renderView(); },
  'add-char': (b) => { P.characters.push({ id: uid(), name: b.dataset.name ? b.dataset.name[0] + b.dataset.name.slice(1).toLowerCase() : '', role: P.characters.length ? 'Supporting' : 'Protagonist' }); state.castSel = P.characters.length - 1; touch(); renderView(); if (!b.dataset.name) setTimeout(() => $(`#f-characters-${state.castSel}-name`)?.focus(), 0); },
  'sel-char': (b) => { state.castSel = +b.dataset.i; renderView(); refreshAssistant(); },
  'del-char': () => { $('#del-char').innerHTML = `<span class="confirm">Remove ${esc(P.characters[state.castSel].name || 'this character')}? Their lines in the script stay. <button class="btn small danger" data-act="del-char-yes">Remove</button><button class="btn small" data-act="del-char-no">Keep</button></span>`; },
  'del-char-yes': () => { P.characters.splice(state.castSel, 1); state.castSel = 0; touch(); renderView(); },
  'del-char-no': () => renderView(),
  turn: (b) => { const c = P.cards[b.dataset.id] || (P.cards[b.dataset.id] = {}); c.turn = c.turn === b.dataset.v ? '' : b.dataset.v; touch(); $$(`[data-act="turn"][data-id="${b.dataset.id}"]`).forEach((x) => x.setAttribute('aria-pressed', String(c.turn === x.dataset.v))); },
  move: (b) => { moveScene(+b.dataset.k, +b.dataset.k + +b.dataset.d); renderView(); },
  'write-scene': (b) => { const i = P.script.findIndex((x) => x.id === b.dataset.id); jumpTo(i); },
  'add-scene': () => { const b = { id: uid(), t: 'scene', x: '' }; P.script.push(b, { id: uid(), t: 'action', x: '' }); touch(true); jumpTo(P.script.length - 2); },
  'add-idea': () => { P.ideas.unshift({ id: uid(), text: '' }); touch(); renderView(); setTimeout(() => $(`#idea-${P.ideas[0].id}`)?.focus(), 0); },
  'del-idea': (b) => { P.ideas.splice(+b.dataset.i, 1); touch(); renderView(); },
  promote: (b) => { const it = P.ideas.splice(+b.dataset.i, 1)[0]; const s = { id: uid(), t: 'scene', x: '' }; P.script.push(s, { id: uid(), t: 'action', x: it.text || '' }); P.cards[s.id] = { goal: it.text || '' }; touch(true); renderView(); toast('Added to the end of the script. Give it a scene heading.'); },
  'add-fb': () => { P.feedback.push({ from: '', note: '', plan: '', done: false }); touch(); renderView(); },
  'del-fb': (b) => { P.feedback.splice(+b.dataset.i, 1); touch(); renderView(); },
  preview: () => { if (Editor.el) Editor.readDOM(); state.preview = !state.preview; renderView(); },
  focus: () => { state.focus = !state.focus; $('#sv').classList.toggle('focus', state.focus); const btn = $('[data-act="focus"]'); btn.textContent = state.focus ? 'Show panels' : 'Focus'; btn.setAttribute('aria-pressed', String(state.focus)); },
  stuck: () => { state.stuck = (state.stuck ?? Math.floor(Math.random() * STUCK.length)) + 1; if (state.view === 'script') { const c = Editor.el && Editor.caret(); renderCompanion(c ? c.i : 0, true); } },
};

/* =====================================================================
   Boot
   ===================================================================== */
function boot() {
  try { applyTheme(localStorage.getItem('draftroom.theme')); } catch (e) { /* ignore */ }
  const saved = Store.readLocal();
  if (saved && saved.projects && Object.keys(saved.projects).length) LIB = saved;
  else { const p = sampleProject(); LIB = { current: p.id, projects: { [p.id]: p } }; }
  for (const id in LIB.projects) normalize(LIB.projects[id]);
  bindShell();
  const v = location.hash.slice(1);
  state.view = VIEWS.some(([x]) => x === v) ? v : 'home';
  openProject(LIB.current, true);
  Store.status(Store.writeLocal() ? 'Saved in this browser' : 'Not saved: export a backup', false);
  // Light up runtime capabilities when this page runs as a claude.ai artifact.
  if (window.claude && typeof window.claude.use === 'function') {
    window.claude.use('sample').then((s) => { caps.sample = s; refreshAssistant(); }).catch(() => {});
    window.claude.use('downloads').then((d) => { caps.downloads = d; }).catch(() => {});
    Promise.all([window.claude.use('db'), window.claude.use('user')]).then(async ([db, user]) => {
      if (!db || !user) return;
      const id = await user.id();
      if (!id) return;
      caps.db = db; caps.userId = id;
      await Store.sync();
      if (state.view === 'home') renderView();
    }).catch(() => {});
  }
}
boot();
