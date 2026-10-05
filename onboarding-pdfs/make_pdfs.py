"""Generates two fillable PDF checklists (Manager, New Hire) mirroring
onboarding-checklist.html. Every task line is an editable text field, so
wording can be changed in place, and each section ends with blank
"duplicate" rows - empty copies of the same row - ready to fill in as
extra tasks. A bonus page of blank rows sits at the end for anything else.
"""

import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib.colors import Color
from reportlab.pdfgen import canvas
from pypdf import PdfReader, PdfWriter
from pypdf.generic import DictionaryObject, NameObject, TextStringObject, BooleanObject, ArrayObject

from pdf_data import (
    MANAGER_PHASES, MANAGER_ITEMS,
    NEWHIRE_PHASES, NEWHIRE_ITEMS,
    day_label,
)

PAGE_W, PAGE_H = letter
MARGIN_L = 50
MARGIN_R = 50
MARGIN_TOP = 46
MARGIN_BOTTOM = 46
CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R

COL_CHECK_W = 18
COL_GAP = 10
COL_DATE_W = 90
COL_TIMING_W = 108
COL_TASK_W = CONTENT_W - COL_CHECK_W - COL_TIMING_W - COL_DATE_W - 2 * COL_GAP

ROW_H = 28
ROW_PITCH = ROW_H + 9
PHASE_BAND_H = 20


def hx(h):
    h = h.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    return Color(r, g, b)


WHITE = Color(1, 1, 1)
INK = hx("16211c")
INK_MUTED = hx("68756c")
ACCENT = hx("1f5d5b")
ACCENT_SOFT = hx("dcece7")
BORDER = hx("c7d0c4")
BAND = hx("eef1ea")
FIELD_BORDER = hx("a9b3a4")

FIELD_KW = dict(fillColor=WHITE, borderColor=FIELD_BORDER, textColor=INK,
                 borderWidth=0.6, fontName="Helvetica", fontSize=9)


def draw_doc_header(c, title, subtitle):
    y = PAGE_H - MARGIN_TOP
    c.setFillColor(ACCENT)
    c.setFont("Helvetica-Bold", 17)
    c.drawString(MARGIN_L, y, title)
    c.setFillColor(INK_MUTED)
    c.setFont("Helvetica", 9.5)
    c.drawString(MARGIN_L, y - 15, subtitle)
    c.setFillColor(ACCENT)
    c.setLineWidth(1.4)
    c.line(MARGIN_L, y - 23, PAGE_W - MARGIN_R, y - 23)
    return y - 23


def draw_field_block(c, x, y, w, label, name, height=20):
    c.setFillColor(INK_MUTED)
    c.setFont("Helvetica-Bold", 7.6)
    c.drawString(x, y, label.upper())
    c.acroForm.textfield(
        name=name, value="", x=x, y=y - height - 3, width=w, height=height,
        tooltip=label, **FIELD_KW,
    )


def draw_identity_block(c, y, field_prefix):
    gap = 16
    col_w = (CONTENT_W - gap) / 2
    row1_y = y - 20
    draw_field_block(c, MARGIN_L, row1_y, col_w, "New hire name", field_prefix + "_name")
    draw_field_block(c, MARGIN_L + col_w + gap, row1_y, col_w, "Role / title", field_prefix + "_role")
    row2_y = row1_y - 44
    draw_field_block(c, MARGIN_L, row2_y, col_w, "Manager", field_prefix + "_manager")
    draw_field_block(c, MARGIN_L + col_w + gap, row2_y, col_w, "Start date (MM/DD/YYYY)", field_prefix + "_startdate")
    c.setFillColor(ACCENT)
    c.setFont("Helvetica-Oblique", 8)
    c.drawString(MARGIN_L + col_w + gap, row2_y - 23 - 11, "Fill this in — every Due Date below updates automatically.")
    bottom = row2_y - 23 - 11 - 14
    c.setStrokeColor(BORDER)
    c.setLineWidth(0.6)
    c.line(MARGIN_L, bottom, PAGE_W - MARGIN_R, bottom)
    return bottom - 16


def draw_column_labels(c, y):
    c.setFillColor(INK_MUTED)
    c.setFont("Helvetica-Bold", 7.6)
    x = MARGIN_L
    c.drawString(x, y, "DONE")
    x += COL_CHECK_W + COL_GAP
    c.drawString(x, y, "TASK  (edit the wording here as needed)")
    x += COL_TASK_W + COL_GAP
    c.drawString(x, y, "TIMING")
    x += COL_TIMING_W + COL_GAP
    c.drawString(x, y, "DUE DATE")
    return y - 12


def draw_continuation_header(c, title, page_num):
    y = PAGE_H - MARGIN_TOP
    c.setFillColor(ACCENT)
    c.setFont("Helvetica-Bold", 11)
    c.drawString(MARGIN_L, y, title + " (continued)")
    c.setFillColor(INK_MUTED)
    c.setFont("Helvetica", 8.5)
    c.drawRightString(PAGE_W - MARGIN_R, y, "Page " + str(page_num))
    c.setStrokeColor(ACCENT)
    c.setLineWidth(1.1)
    c.line(MARGIN_L, y - 8, PAGE_W - MARGIN_R, y - 8)
    y = draw_column_labels(c, y - 22)
    return y - 6


def draw_phase_band(c, y, label, window):
    c.setFillColor(BAND)
    c.rect(MARGIN_L, y - PHASE_BAND_H, CONTENT_W, PHASE_BAND_H, stroke=0, fill=1)
    c.setFillColor(ACCENT)
    c.setFont("Helvetica-Bold", 9.5)
    c.drawString(MARGIN_L + 8, y - PHASE_BAND_H + 6, label)
    if window:
        c.setFillColor(INK_MUTED)
        c.setFont("Helvetica", 8.5)
        c.drawRightString(PAGE_W - MARGIN_R - 8, y - PHASE_BAND_H + 6, window)
    return y - PHASE_BAND_H - 10


def strike_js(done_name, strike_name, task_name):
    # Runs when the checkbox is clicked (Acrobat/Reader only - see README note).
    # Re-derives state from the checkbox itself so it's correct either way,
    # rather than just toggling, in case the script ever reruns. Each effect
    # is in its own try/catch so one failing (e.g. a font resource issue)
    # can't silently block the other.
    return (
        'var on = this.getField("%s").valueAsString != "Off";'
        'try { var s = this.getField("%s"); if (s) s.display = on ? display.visible : display.hidden; } catch (e) {}'
        'try { var t = this.getField("%s"); if (t) t.textFont = on ? font.HelvI : font.Helv; } catch (e) {}'
    ) % (done_name, strike_name, task_name)


# One identical Calculate script, shared by every "due date" field in the
# document (Acrobat/Reader only - see README note). It reads its OWN name to
# find its sibling Timing field and the document's Start Date field, so it
# needs no per-row constants and keeps working even on a row duplicated by
# hand in Acrobat Pro: parse a day number out of the Timing field's text
# ("Day 30", "14d before Day 1", or a bare number), add it to the Start Date,
# and write the result - re-running automatically whenever ANY field on the
# form changes, via the document's calculation order.
DATE_CALC_JS = (
    'var tf = this.getField(event.target.name.replace(/_date$/, "_timing"));'
    'var sdf = this.getField(event.target.name.split("_")[0] + "_startdate");'
    'var txt = (tf ? tf.valueAsString : "") || "";'
    'var sd = (sdf ? sdf.valueAsString : "") || "";'
    'var offset = null;'
    'var mBefore = txt.match(/(\\d+)\\s*d.*before/i);'
    'var mDay = txt.match(/Day\\s*(-?\\d+)/i);'
    'if (mBefore) { offset = -parseInt(mBefore[1], 10); }'
    'else if (mDay) { offset = parseInt(mDay[1], 10) - 1; }'
    'else if (/^-?\\d+$/.test(txt.replace(/\\s/g, ""))) { offset = parseInt(txt, 10) - 1; }'
    'event.value = "";'
    'if (offset !== null && sd) {'
    '  var parts = sd.split(/[\\/\\-]/);'
    '  if (parts.length === 3) {'
    '    var mm = parseInt(parts[0], 10) - 1, dd = parseInt(parts[1], 10), yy = parseInt(parts[2], 10);'
    '    if (yy < 100) yy += 2000;'
    '    var base = new Date(yy, mm, dd);'
    '    if (!isNaN(base.getTime()) && base.getMonth() === mm) {'
    '      base.setDate(base.getDate() + offset);'
    '      event.value = util.printd("mm/dd/yyyy", base);'
    '    }'
    '  }'
    '}'
)


def draw_row(c, y, prefix, task_value, timing_value, actions):
    x = MARGIN_L
    top = y
    done_name = prefix + "_done"
    task_name = prefix + "_task"
    strike_name = prefix + "_strike"

    c.acroForm.checkbox(
        name=done_name, x=x, y=top - 16, size=13, checked=False,
        buttonStyle="check", fillColor=WHITE, borderColor=FIELD_BORDER,
        borderWidth=0.7, fieldFlags="", tooltip="Mark done",
    )
    x += COL_CHECK_W + COL_GAP
    c.acroForm.textfield(
        name=task_name, value=task_value, x=x, y=top - ROW_H, width=COL_TASK_W,
        height=ROW_H, fieldFlags="multiline", maxlen=400, **FIELD_KW,
    )
    # Thin bar overlaid on the task field, hidden until the checkbox is
    # ticked - a strikethrough that works without rich-text form fields.
    # Positioned to cross the first text line, not the middle of the whole
    # (taller, wrap-friendly) field - Acrobat top-anchors field text, so the
    # glyphs sit near the field's top edge, not its vertical center.
    c.acroForm.textfield(
        name=strike_name, value="", x=x + 6, y=top - 9.5, width=COL_TASK_W - 12,
        height=1.3, fillColor=INK, borderColor=INK, borderWidth=0, fieldFlags="readOnly",
        annotationFlags="hidden", fontSize=1,
    )
    x += COL_TASK_W + COL_GAP
    c.acroForm.textfield(
        name=prefix + "_timing", value=timing_value, x=x, y=top - 20, width=COL_TIMING_W,
        height=18, maxlen=60, tooltip='Day number, e.g. "Day 30" — the due date fills in automatically',
        **FIELD_KW,
    )
    x += COL_TIMING_W + COL_GAP
    c.acroForm.textfield(
        name=prefix + "_date", value="", x=x, y=top - 20, width=COL_DATE_W,
        height=18, maxlen=20, tooltip="Auto-fills from the start date above and this row's Timing",
        **FIELD_KW,
    )
    actions[done_name] = strike_js(done_name, strike_name, task_name)
    return y - ROW_PITCH


def attach_dynamic_behavior(path, checkbox_actions):
    """Post-process pass: reportlab's AcroForm API has no hook for a widget's
    /AA (additional-actions) dict or the form's /CO (calculation order), so
    both are patched in with pypdf after the fact. Adobe Acrobat/Reader runs
    all of this; other viewers (Preview, Chrome, Firefox, most phone apps)
    just ignore it and leave plain, still-fully-functional fields.

    Wires two things per document:
    - each checkbox's MouseUp action (checkbox_actions, by field name) -
      the strikethrough/italic toggle.
    - every "..._date" field's Calculate action (the same DATE_CALC_JS for
      all of them) plus /AcroForm/CO, so Acrobat recalculates every due date
      whenever the Start Date (or a Timing field) changes.
    """
    reader = PdfReader(path)
    writer = PdfWriter()
    writer.append(reader)

    acro_form = writer._root_object["/AcroForm"] if "/AcroForm" in writer._root_object else None
    if acro_form is not None:
        acro_form[NameObject("/NeedAppearances")] = BooleanObject(True)

    def js_action(script):
        d = DictionaryObject()
        d[NameObject("/S")] = NameObject("/JavaScript")
        d[NameObject("/JS")] = TextStringObject(script)
        return d

    patched_checkboxes = 0
    date_field_refs = []
    for page in writer.pages:
        annots = page.get("/Annots")
        if not annots:
            continue
        for annot_ref in annots:
            annot = annot_ref.get_object()
            name = annot.get("/T")
            if name is None:
                continue
            name = str(name)

            if name in checkbox_actions:
                aa = DictionaryObject()
                aa[NameObject("/U")] = js_action(checkbox_actions[name])
                annot[NameObject("/AA")] = aa
                patched_checkboxes += 1
            elif name.endswith("_date"):
                aa = DictionaryObject()
                aa[NameObject("/C")] = js_action(DATE_CALC_JS)
                annot[NameObject("/AA")] = aa
                date_field_refs.append(annot_ref)

    if acro_form is not None and date_field_refs:
        acro_form[NameObject("/CO")] = ArrayObject(date_field_refs)

    with open(path, "wb") as f:
        writer.write(f)
    assert patched_checkboxes == len(checkbox_actions), \
        "expected %d checkboxes patched, got %d" % (len(checkbox_actions), patched_checkboxes)
    assert date_field_refs, "expected at least one due-date field to wire up"


def ensure_room(c, y, needed, title, page_num):
    if y - needed < MARGIN_BOTTOM:
        c.showPage()
        page_num += 1
        y = draw_continuation_header(c, title, page_num)
    return y, page_num


def build_checklist_pdf(path, title, subtitle, phases, items, field_prefix,
                         extra_rows_per_phase, bonus_rows):
    c = canvas.Canvas(path, pagesize=letter)
    c.setTitle(title)
    actions = {}  # checkbox field name -> JavaScript to run when it's clicked

    page_num = 1
    y = draw_doc_header(c, title, subtitle)
    y = draw_identity_block(c, y, field_prefix)
    y = draw_column_labels(c, y)
    y -= 6

    by_phase = {}
    for item_id, phase_id, offset, text in items:
        by_phase.setdefault(phase_id, []).append((item_id, offset, text))

    for phase in phases:
        phase_items = by_phase.get(phase["id"], [])
        y, page_num = ensure_room(c, y, PHASE_BAND_H + ROW_PITCH, title, page_num)
        y = draw_phase_band(c, y, phase["label"], phase["window"])

        for item_id, offset, text in phase_items:
            y, page_num = ensure_room(c, y, ROW_PITCH, title, page_num)
            y = draw_row(c, y, field_prefix + "_" + item_id, text, day_label(offset), actions)

        for i in range(extra_rows_per_phase):
            y, page_num = ensure_room(c, y, ROW_PITCH, title, page_num)
            y = draw_row(c, y, field_prefix + "_" + phase["id"] + "_extra" + str(i + 1), "", "", actions)

        y -= 4

    # Additional Tasks always starts on its own fresh page.
    c.showPage()
    page_num += 1
    y = draw_continuation_header(c, title, page_num)
    y = draw_phase_band(c, y, "Additional Tasks", "add as many as you need")
    for i in range(bonus_rows):
        y, page_num = ensure_room(c, y, ROW_PITCH, title, page_num)
        y = draw_row(c, y, field_prefix + "_bonus" + str(i + 1), "", "", actions)

    c.save()
    attach_dynamic_behavior(path, actions)


def main():
    build_checklist_pdf(
        "Manager_Onboarding_Checklist.pdf",
        "Manager Onboarding Checklist",
        "The First 90 — everything to prep before day one, and how to check in through day 90.",
        MANAGER_PHASES, MANAGER_ITEMS, "m",
        extra_rows_per_phase=8, bonus_rows=20,
    )
    build_checklist_pdf(
        "NewHire_Onboarding_Checklist.pdf",
        "New Hire Onboarding Checklist",
        "The First 90 — your plan from day one through day 90.",
        NEWHIRE_PHASES, NEWHIRE_ITEMS, "n",
        extra_rows_per_phase=8, bonus_rows=20,
    )
    print("done")


if __name__ == "__main__":
    sys.exit(main())
