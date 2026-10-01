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
    bottom = row2_y - 23 - 14
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
    c.drawString(x, y, "YOUR DATE")
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


def draw_row(c, y, prefix, task_value, timing_value):
    x = MARGIN_L
    top = y
    c.acroForm.checkbox(
        name=prefix + "_done", x=x, y=top - 16, size=13, checked=False,
        buttonStyle="check", fillColor=WHITE, borderColor=FIELD_BORDER,
        borderWidth=0.7, fieldFlags="", tooltip="Mark done",
    )
    x += COL_CHECK_W + COL_GAP
    c.acroForm.textfield(
        name=prefix + "_task", value=task_value, x=x, y=top - ROW_H, width=COL_TASK_W,
        height=ROW_H, fieldFlags="multiline", maxlen=400, **FIELD_KW,
    )
    x += COL_TASK_W + COL_GAP
    c.acroForm.textfield(
        name=prefix + "_timing", value=timing_value, x=x, y=top - 20, width=COL_TIMING_W,
        height=18, maxlen=60, **FIELD_KW,
    )
    x += COL_TIMING_W + COL_GAP
    c.acroForm.textfield(
        name=prefix + "_date", value="", x=x, y=top - 20, width=COL_DATE_W,
        height=18, maxlen=20, tooltip="Fill in once you know the start date", **FIELD_KW,
    )
    return y - ROW_PITCH


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
            y = draw_row(c, y, field_prefix + "_" + item_id, text, day_label(offset))

        for i in range(extra_rows_per_phase):
            y, page_num = ensure_room(c, y, ROW_PITCH, title, page_num)
            y = draw_row(c, y, field_prefix + "_" + phase["id"] + "_extra" + str(i + 1), "", "")

        y -= 4

    # Bonus page: a block of blank "duplicate" rows for anything else.
    y, page_num = ensure_room(c, y, PHASE_BAND_H + ROW_PITCH, title, page_num)
    y = draw_phase_band(c, y, "Additional Tasks", "add as many as you need")
    for i in range(bonus_rows):
        y, page_num = ensure_room(c, y, ROW_PITCH, title, page_num)
        y = draw_row(c, y, field_prefix + "_bonus" + str(i + 1), "", "")

    c.save()


def main():
    build_checklist_pdf(
        "Manager_Onboarding_Checklist.pdf",
        "Manager Onboarding Checklist",
        "The First 90 — everything to prep before day one, and how to check in through day 90.",
        MANAGER_PHASES, MANAGER_ITEMS, "m",
        extra_rows_per_phase=1, bonus_rows=10,
    )
    build_checklist_pdf(
        "NewHire_Onboarding_Checklist.pdf",
        "New Hire Onboarding Checklist",
        "The First 90 — your plan from day one through day 90.",
        NEWHIRE_PHASES, NEWHIRE_ITEMS, "n",
        extra_rows_per_phase=1, bonus_rows=10,
    )
    print("done")


if __name__ == "__main__":
    sys.exit(main())
