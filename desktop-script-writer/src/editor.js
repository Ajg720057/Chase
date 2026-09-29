/* =====================================================================
   Script editor: one contenteditable page whose children are elements.
   The model (P.script) is the source of truth; the DOM is re-read after
   native typing and re-rendered after structural edits.
   ===================================================================== */
const Editor = {
  el: null,
  undo: [],
  redo: [],
  lastPush: 0,
  ac: null, // autocomplete state
  onChange: null,
  onCaret: null,

  mount(container) {
    const el = document.createElement('div');
    el.className = 'paper';
    el.contentEditable = 'true';
    el.spellcheck = true;
    el.setAttribute('role', 'textbox');
    el.setAttribute('aria-multiline', 'true');
    el.setAttribute('aria-label', 'Screenplay');
    container.appendChild(el);
    this.el = el;
    this.render();
    el.addEventListener('keydown', (e) => this.keydown(e));
    el.addEventListener('beforeinput', (e) => this.beforeinput(e));
    el.addEventListener('input', () => this.input());
    el.addEventListener('paste', (e) => this.paste(e));
    el.addEventListener('drop', (e) => e.preventDefault());
    el.addEventListener('blur', () => setTimeout(() => this.closeAC(), 150));
    this._sel = () => { if (this.el && this.el.contains(document.getSelection().anchorNode)) this.caretMoved(); };
    document.addEventListener('selectionchange', this._sel);
  },
  unmount() {
    document.removeEventListener('selectionchange', this._sel);
    this.closeAC();
    this.el = null;
  },

  blockHTML(b) {
    const x = b.x || '';
    return `<div class="el t-${b.t}${x ? '' : ' empty'}" data-t="${b.t}" data-id="${b.id}" data-label="${EL[b.t].label}" data-ph="${esc(EL[b.t].ph)}">${x ? esc(x) : '<br>'}</div>`;
  },
  render() {
    if (!this.el) return;
    if (!P.script.length) P.script.push({ id: uid(), t: 'scene', x: '' });
    this.el.innerHTML = P.script.map((b) => this.blockHTML(b)).join('');
  },
  renderBlock(i) {
    const div = this.el.children[i];
    const tmp = document.createElement('div');
    tmp.innerHTML = this.blockHTML(P.script[i]);
    div.replaceWith(tmp.firstChild);
  },

  /* ---------- caret helpers ---------- */
  caret() {
    const sel = document.getSelection();
    if (!sel.rangeCount || !this.el.contains(sel.anchorNode)) return null;
    let node = sel.anchorNode;
    while (node && node.parentNode !== this.el) node = node.parentNode;
    if (!node) return null;
    const i = Array.prototype.indexOf.call(this.el.children, node);
    const r = document.createRange();
    r.selectNodeContents(node);
    r.setEnd(sel.anchorNode, sel.anchorOffset);
    return { i, off: r.toString().length, collapsed: sel.isCollapsed };
  },
  setCaret(i, off) {
    const div = this.el.children[clamp(i, 0, this.el.children.length - 1)];
    if (!div) return;
    const sel = document.getSelection();
    const r = document.createRange();
    let remaining = off;
    const walker = document.createTreeWalker(div, NodeFilter.SHOW_TEXT);
    let node, placed = false;
    while ((node = walker.nextNode())) {
      if (remaining <= node.length) { r.setStart(node, remaining); placed = true; break; }
      remaining -= node.length;
    }
    if (!placed) { r.selectNodeContents(div); r.collapse(false); if (!div.textContent) r.setStart(div, 0); }
    r.collapse(true);
    sel.removeAllRanges();
    sel.addRange(r);
    this.caretMoved();
  },
  scrollTo(i, flash) {
    const div = this.el && this.el.children[i];
    if (!div) return;
    div.scrollIntoView({ block: 'center', behavior: 'smooth' });
    if (flash) { div.classList.add('flash'); setTimeout(() => div.classList.remove('flash'), 900); }
  },

  /* ---------- model sync ---------- */
  readDOM() {
    // Normalize stray nodes the browser may create, then rebuild the model.
    const known = new Map(P.script.map((b) => [b.id, b]));
    const seen = new Set();
    const next = [];
    let dirty = false;
    for (const node of Array.from(this.el.childNodes)) {
      if (node.nodeType === 3) {
        if (!node.textContent.trim()) { node.remove(); continue; }
        const div = document.createElement('div');
        node.replaceWith(div); div.appendChild(node);
        div.className = 'el t-action'; div.dataset.t = 'action'; dirty = true;
        next.push(this.fromDiv(div, known, seen)); continue;
      }
      if (node.nodeType !== 1) { node.remove(); continue; }
      if (!node.classList.contains('el') || !node.dataset.t) { node.className = 'el t-action'; node.dataset.t = 'action'; dirty = true; }
      if (Array.from(node.childNodes).some((c) => c.nodeType === 1 && c.nodeName !== 'BR')) { dirty = true; node.textContent = node.innerText.replace(/\n$/, ''); }
      next.push(this.fromDiv(node, known, seen));
    }
    P.script = next.length ? next : [{ id: uid(), t: 'action', x: '' }];
    return dirty;
  },
  fromDiv(div, known, seen) {
    let id = div.dataset.id;
    if (!id || seen.has(id)) { id = uid(); div.dataset.id = id; }
    seen.add(id);
    const t = div.dataset.t;
    const x = div.textContent.replace(/ /g, ' ');
    const old = known.get(id);
    const b = { id, t, x };
    if (old && old.syn) b.syn = old.syn;
    div.classList.toggle('empty', !x);
    return b;
  },
  changed(structural) {
    layoutDirty = true;
    if (this.onChange) this.onChange(structural);
  },

  /* ---------- history ---------- */
  snapshot() { return { script: JSON.stringify(P.script), caret: this.caret() }; },
  push(force) {
    const now = Date.now();
    if (!force && now - this.lastPush < 900) return;
    this.lastPush = now;
    this.undo.push(this.snapshot());
    if (this.undo.length > 300) this.undo.shift();
    this.redo = [];
  },
  restore(from, to) {
    const snap = from.pop();
    if (!snap) return;
    to.push(this.snapshot());
    P.script = JSON.parse(snap.script);
    this.render();
    if (snap.caret) this.setCaret(snap.caret.i, snap.caret.off);
    this.lastPush = 0;
    this.changed(true);
  },

  /* ---------- editing operations ---------- */
  setType(t, i) {
    const c = this.caret();
    if (i == null) { if (!c) return; i = c.i; }
    if (P.script[i].t === t) return;
    this.push(true);
    this.readDOM();
    P.script[i].t = t;
    if (t === 'paren') {
      const x = P.script[i].x.trim();
      if (x && !x.startsWith('(')) P.script[i].x = `(${x})`;
    }
    this.renderBlock(i);
    this.setCaret(i, c && c.i === i ? c.off : (P.script[i].x || '').length);
    this.changed(true);
  },
  insertAfter(i, block) {
    P.script.splice(i + 1, 0, block);
    const tmp = document.createElement('div');
    tmp.innerHTML = this.blockHTML(block);
    if (i < 0) this.el.prepend(tmp.firstChild);
    else this.el.children[i].after(tmp.firstChild);
  },
  enter(shift) {
    const sel = document.getSelection();
    if (!sel.isCollapsed) { document.execCommand('delete'); this.readDOM(); }
    const c = this.caret();
    if (!c) return;
    this.push(true);
    this.readDOM();
    const b = P.script[c.i];
    const x = b.x || '';
    if (shift && (b.t === 'action' || b.t === 'dialogue')) {
      b.x = x.slice(0, c.off) + '\n' + x.slice(c.off);
      this.renderBlock(c.i);
      this.setCaret(c.i, c.off + 1);
      this.changed(false);
      return;
    }
    // Enter on an empty non-action element turns it into action.
    if (!x.trim() && b.t !== 'action' && b.t !== 'scene') {
      b.t = 'action';
      b.x = '';
      this.renderBlock(c.i);
      this.setCaret(c.i, 0);
      this.changed(true);
      return;
    }
    // Recognize typed scene headings and transitions.
    if (b.t === 'action' && SCENE_RE.test(x.trim())) b.t = 'scene';
    if (b.t === 'action' && x.trim() === x.trim().toUpperCase() && /TO:$/.test(x.trim())) b.t = 'transition';
    let before = x.slice(0, c.off);
    const after = x.slice(c.off);
    if (b.t === 'paren' && !after && before.trim().startsWith('(') && !before.trim().endsWith(')')) before = before.trimEnd() + ')';
    let t = NEXT[b.t] || 'action';
    if (c.off === 0 && x) {
      // Caret at the start of a filled element: open an empty line above.
      this.insertAfter(c.i - 1, { id: uid(), t: b.t === 'scene' ? 'action' : b.t, x: '' });
      this.setCaret(c.i + 1, 0);
      this.changed(true);
      return;
    }
    if (after) t = b.t;
    b.x = before;
    this.renderBlock(c.i);
    this.insertAfter(c.i, { id: uid(), t, x: after });
    this.setCaret(c.i + 1, 0);
    this.changed(true);
  },
  backspace(e) {
    const c = this.caret();
    if (!c || !c.collapsed || c.off !== 0) return;
    e.preventDefault();
    this.readDOM();
    const b = P.script[c.i];
    if (!b.x && b.t !== 'action' && c.i > 0 && P.script[c.i - 1].t !== 'character') {
      // First backspace on an empty element resets it to action.
      this.push(true); b.t = 'action'; this.renderBlock(c.i); this.setCaret(c.i, 0); this.changed(true); return;
    }
    if (c.i === 0) { if (b.t !== 'action' && !b.x) this.setType('action', 0); return; }
    this.push(true);
    const prev = P.script[c.i - 1];
    const off = (prev.x || '').length;
    prev.x = (prev.x || '') + (b.x || '');
    P.script.splice(c.i, 1);
    this.el.children[c.i].remove();
    this.renderBlock(c.i - 1);
    this.setCaret(c.i - 1, off);
    this.changed(true);
  },
  del(e) {
    const c = this.caret();
    if (!c || !c.collapsed) return;
    this.readDOM();
    const b = P.script[c.i];
    if (c.off !== (b.x || '').length || c.i >= P.script.length - 1) return;
    e.preventDefault();
    this.push(true);
    const next = P.script[c.i + 1];
    b.x = (b.x || '') + (next.x || '');
    P.script.splice(c.i + 1, 1);
    this.el.children[c.i + 1].remove();
    this.renderBlock(c.i);
    this.setCaret(c.i, c.off);
    this.changed(true);
  },
  cycle(dir) {
    const c = this.caret();
    if (!c) return;
    const b = P.script[c.i];
    const k = CYCLE.indexOf(b.t);
    const t = CYCLE[(k + dir + CYCLE.length) % CYCLE.length];
    this.setType(t);
  },

  /* ---------- events ---------- */
  keydown(e) {
    const mod = e.metaKey || e.ctrlKey;
    if (this.ac) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); this.moveAC(e.key === 'ArrowDown' ? 1 : -1); return; }
      if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); this.acceptAC(); return; }
      if (e.key === 'Escape') { e.preventDefault(); this.closeAC(); return; }
    }
    if (mod && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? this.restore(this.redo, this.undo) : this.restore(this.undo, this.redo); return; }
    if (mod && e.key.toLowerCase() === 'y') { e.preventDefault(); this.restore(this.redo, this.undo); return; }
    if (mod && (e.key.toLowerCase() === 'b' || e.key.toLowerCase() === 'i' || e.key.toLowerCase() === 'u')) { e.preventDefault(); this.wrapSelection({ b: '**', i: '*', u: '_' }[e.key.toLowerCase()]); return; }
    if (e.altKey && /^Digit[1-7]$/.test(e.code)) { e.preventDefault(); this.setType(ELEMENTS[+e.code.slice(5) - 1][0]); return; }
    if (e.key === 'Enter') { e.preventDefault(); this.enter(e.shiftKey); return; }
    if (e.key === 'Tab') { e.preventDefault(); this.cycle(e.shiftKey ? -1 : 1); return; }
    if (e.key === 'Backspace') { this.backspace(e); return; }
    if (e.key === 'Delete') { this.del(e); return; }
  },
  beforeinput(e) {
    if (e.inputType === 'historyUndo') { e.preventDefault(); this.restore(this.undo, this.redo); return; }
    if (e.inputType === 'historyRedo') { e.preventDefault(); this.restore(this.redo, this.undo); return; }
    if (e.inputType === 'insertParagraph' || e.inputType === 'insertLineBreak') { e.preventDefault(); return; }
    const sel = document.getSelection();
    this.push(!sel.isCollapsed);
  },
  input() {
    const c = this.caret();
    const dirty = this.readDOM();
    if (dirty && c) this.setCaret(c.i, c.off);
    if (c) this.autoType(c.i);
    this.changed(false);
    this.suggest();
  },
  autoType(i) {
    const b = P.script[i];
    const x = b.x || '';
    let t = null;
    if (b.t === 'action' && /^(int|ext|est|int\.?\/ext|i\/e)[.\s]/i.test(x) && !x.includes('\n')) t = 'scene';
    if (b.t === 'dialogue' && x.startsWith('(')) t = 'paren';
    if (t) {
      const c = this.caret();
      b.t = t;
      this.renderBlock(i);
      this.setCaret(i, c ? c.off : x.length);
    }
  },
  paste(e) {
    e.preventDefault();
    const text = (e.clipboardData.getData('text/plain') || '').replace(/\r\n?/g, '\n');
    if (!text) return;
    this.push(true);
    if (!text.includes('\n')) { document.execCommand('insertText', false, text); return; }
    const sel = document.getSelection();
    if (!sel.isCollapsed) document.execCommand('delete');
    this.readDOM();
    const c = this.caret() || { i: P.script.length - 1, off: (P.script.at(-1).x || '').length };
    const b = P.script[c.i];
    const before = (b.x || '').slice(0, c.off), after = (b.x || '').slice(c.off);
    let blocks = parseFountain(text).blocks;
    if (!blocks.length) return;
    b.x = before;
    const ins = blocks;
    const keepB = before.trim() !== '';
    const start = keepB ? c.i + 1 : c.i;
    P.script.splice(start, keepB ? 0 : 1, ...ins);
    let last = start + ins.length - 1;
    if (after.trim()) { P.script.splice(last + 1, 0, { id: uid(), t: b.t, x: after }); }
    this.render();
    this.setCaret(last, (P.script[last].x || '').length);
    this.scrollTo(last);
    this.changed(true);
  },
  wrapSelection(mark) {
    const sel = document.getSelection();
    if (sel.isCollapsed) return;
    const text = sel.toString();
    if (text.includes('\n')) return;
    this.push(true);
    document.execCommand('insertText', false, mark + text + mark);
  },

  /* ---------- caret moved: update toolbar/companion ---------- */
  caretMoved() {
    const c = this.caret();
    if (!c) return;
    if (this._cur !== c.i || !this.el.children[c.i]?.classList.contains('cur')) {
      $$('.el.cur', this.el).forEach((d) => d.classList.remove('cur'));
      this.el.children[c.i]?.classList.add('cur');
      this._cur = c.i;
    }
    if (this.onCaret) this.onCaret(c.i);
  },

  /* ---------- autocomplete ---------- */
  suggest() {
    const c = this.caret();
    if (!c) return this.closeAC();
    const b = P.script[c.i];
    const x = (b.x || '');
    const X = x.toUpperCase();
    let items = [], mode = null, note = '';
    if (b.t === 'character' && c.off === x.length) {
      const names = new Set();
      for (const s of P.script) if (s.t === 'character' && s !== b && s.x.trim()) names.add(s.x.replace(/\s*\(.*?\)/g, '').trim().toUpperCase());
      for (const ch of P.characters) if (ch.name) names.add(ch.name.toUpperCase());
      if (/\($/.test(X) || /\s\($/.test(X)) {
        items = ['V.O.)', 'O.S.)', "CONT'D)", 'O.C.)'].map((s) => X + s); note = 'Extensions';
      } else if (X.trim()) {
        items = [...names].filter((n) => n.startsWith(X.trim()) && n !== X.trim()).slice(0, 6);
        note = 'Characters';
      }
      mode = 'replace';
    } else if (b.t === 'scene' && c.off === x.length) {
      const m = X.match(/^((?:INT\.?\/EXT|INT\/EXT|I\/E|INT|EXT|EST)\.?\s+)(.*)$/);
      if (!X.trim()) { items = ['INT. ', 'EXT. ', 'INT./EXT. ']; note = 'Scene heading'; }
      else if (m && !/ - /.test(m[2])) {
        const locs = analyze(P, getLayout()).locations;
        items = locs.filter((l) => l.startsWith(m[2].trim()) && l !== m[2].trim()).slice(0, 6).map((l) => m[1] + l + ' - ');
        note = 'Locations';
      } else if (/ - $/.test(X) || / - [A-Z]*$/.test(X)) {
        const part = X.split(' - ').at(-1);
        items = ['DAY', 'NIGHT', 'CONTINUOUS', 'LATER', 'MOMENTS LATER', 'MORNING', 'EVENING', 'DAWN', 'DUSK']
          .filter((t) => t.startsWith(part) && t !== part).map((t) => X.slice(0, X.length - part.length) + t);
        note = 'Time of day';
      }
      mode = 'replace';
    } else if (b.t === 'transition' && c.off === x.length) {
      items = ['CUT TO:', 'SMASH CUT TO:', 'MATCH CUT TO:', 'DISSOLVE TO:', 'FADE OUT.', 'FADE IN:'].filter((t) => t.startsWith(X.trim()) && t !== X.trim());
      note = 'Transitions';
      mode = 'replace';
    }
    if (!items.length) return this.closeAC();
    this.ac = { i: c.i, items, sel: 0, mode, note };
    this.drawAC();
  },
  drawAC() {
    let box = $('#ac');
    if (!box) { box = document.createElement('div'); box.id = 'ac'; box.className = 'ac'; box.setAttribute('role', 'listbox'); document.body.appendChild(box); }
    const div = this.el.children[this.ac.i];
    const r = div.getBoundingClientRect();
    const indent = parseFloat(getComputedStyle(div).marginLeft) || 0;
    box.style.left = Math.min(r.left, window.innerWidth - 240) + 'px';
    box.style.top = Math.min(r.bottom + 4, window.innerHeight - 220) + 'px';
    box.innerHTML = `<small>${esc(this.ac.note)} · Tab to accept</small>` + this.ac.items.map((s, k) => `<div role="option" data-k="${k}" aria-selected="${k === this.ac.sel}">${esc(s)}</div>`).join('');
    box.onmousedown = (ev) => { const d = ev.target.closest('[data-k]'); if (d) { ev.preventDefault(); this.ac.sel = +d.dataset.k; this.acceptAC(); } };
    void indent;
  },
  moveAC(d) { this.ac.sel = (this.ac.sel + d + this.ac.items.length) % this.ac.items.length; this.drawAC(); },
  acceptAC() {
    const { i, items, sel } = this.ac;
    this.push(true);
    P.script[i].x = items[sel];
    this.renderBlock(i);
    this.setCaret(i, items[sel].length);
    this.closeAC();
    this.changed(false);
    this.suggest();
  },
  closeAC() { this.ac = null; const box = $('#ac'); if (box) box.remove(); },
};
