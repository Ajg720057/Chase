import os
import tempfile
import unittest

from scriptwriter import fountain, screenplay, workbook
from scriptwriter.__main__ import main

HERE = os.path.dirname(__file__)
SAMPLE = os.path.join(HERE, "..", "examples", "sample.fountain")


def types(script):
    return [e.type for e in script.elements]


class ParserTests(unittest.TestCase):
    def test_title_page(self):
        s = fountain.parse("Title: **Big Film**\nAuthor: A. Writer\nContact:\n    a@b.c\n    555\n\nINT. ROOM - DAY\n")
        self.assertEqual(s.title, "Big Film")
        self.assertEqual(s.title_page["contact"], "a@b.c\n555")
        self.assertEqual(types(s), ["scene_heading"])

    def test_core_elements(self):
        s = fountain.parse(
            "EXT. PARK - DAY\n\nA dog runs.\nIt barks.\n\nBOB (V.O.)\n(softly)\nHello.\n\nCUT TO:\n\n"
            ".MONTAGE\n\n> THE END <\n"
        )
        self.assertEqual(
            types(s),
            ["scene_heading", "action", "character", "parenthetical", "dialogue", "transition",
             "scene_heading", "centered"],
        )
        self.assertEqual(s.elements[1].text, "A dog runs.\nIt barks.")
        self.assertEqual(s.elements[2].text, "BOB (V.O.)")

    def test_scene_heading_needs_blank_line_before(self):
        s = fountain.parse("She walks.\nINT. is short for interior.\n")
        self.assertEqual(types(s), ["action"])

    def test_forced_elements_and_scene_numbers(self):
        s = fountain.parse("INT. HOUSE - NIGHT #12A#\n\n@McCLANE\nYippee.\n\n!BOOM\n\n>SMASH CUT TO:\n")
        self.assertEqual(s.elements[0].scene_number, "12A")
        self.assertEqual(s.elements[0].text, "INT. HOUSE - NIGHT")
        self.assertEqual(types(s), ["scene_heading", "character", "dialogue", "action", "transition"])
        self.assertEqual(s.elements[1].text, "McCLANE")

    def test_all_caps_line_without_dialogue_is_action(self):
        s = fountain.parse("INT. ROOM - DAY\n\nBOOM!\n\nSilence.\n")
        self.assertEqual(types(s), ["scene_heading", "action", "action"])

    def test_notes_and_boneyard_removed(self):
        s = fountain.parse("INT. ROOM - DAY\n\nShe waits.[[fix this]]\n\n/* cut\nscene */\nDone.\n")
        texts = [e.text for e in s.elements]
        self.assertIn("She waits.", texts)
        self.assertFalse(any("fix" in t or "cut" in t for t in texts))

    def test_sections_synopses_dual(self):
        s = fountain.parse("# Act One\n\n= Setup.\n\nINT. A - DAY\n\nAMY ^\nHi.\n")
        self.assertEqual(types(s), ["section", "synopsis", "scene_heading", "character", "dialogue"])
        self.assertEqual(s.elements[0].level, 1)
        self.assertTrue(s.elements[3].dual)
        self.assertEqual(s.elements[3].text, "AMY")


class TextTests(unittest.TestCase):
    def test_emphasis(self):
        chars = screenplay.styled_chars("a *b* **c** _d_ \\*e")
        styles = {ch: (b, i, u) for ch, b, i, u in chars if ch.strip()}
        self.assertEqual(styles["a"], (False, False, False))
        self.assertEqual(styles["b"], (False, True, False))
        self.assertEqual(styles["c"], (True, False, False))
        self.assertEqual(styles["d"], (False, False, True))
        self.assertEqual("".join(c[0] for c in chars), "a b c d *e")

    def test_wrap_respects_width_and_markup(self):
        text = "The *quick brown fox* jumps over the lazy dog again and again"
        lines = screenplay.wrap(text, 20)
        self.assertTrue(all(screenplay.visible_len(ln) <= 20 for ln in lines))
        rejoined = " ".join("".join(c[0] for c in screenplay.styled_chars(ln)) for ln in lines)
        self.assertEqual(rejoined, "The quick brown fox jumps over the lazy dog again and again")
        italic = [c[0] for ln in lines for c in screenplay.styled_chars(ln) if c[2]]
        self.assertEqual("".join(italic).replace(" ", ""), "quickbrownfox")

    def test_eighths(self):
        self.assertEqual(screenplay.eighths(1), "1/8")
        self.assertEqual(screenplay.eighths(54), "1")
        self.assertEqual(screenplay.eighths(54 + 27), "1 4/8")


class LayoutTests(unittest.TestCase):
    def test_pages_never_overflow(self):
        body = "\n\n".join(f"INT. ROOM {i} - DAY\n\nAction line {i} " + "word " * 40 for i in range(60))
        lay = screenplay.layout(fountain.parse(body))
        for page in lay.pages:
            self.assertLessEqual(max(row for row, _ in page), screenplay.LINES_PER_PAGE - 1)

    def test_long_speech_splits_with_more_and_contd(self):
        speech = " ".join(f"word{i}" for i in range(900))
        lay = screenplay.layout(fountain.parse(f"INT. ROOM - DAY\n\nBOB\n{speech}\n"))
        self.assertGreater(len(lay.pages), 1)
        last_on_page1 = lay.pages[0][-1][1]
        first_on_page2 = lay.pages[1][0][1]
        self.assertEqual(last_on_page1.text, "(MORE)")
        self.assertEqual(first_on_page2.text, "BOB (CONT'D)")
        self.assertEqual(lay.dialogue["BOB"][0], 1)  # one speech, even though split

    def test_scene_heading_not_orphaned_at_page_bottom(self):
        filler = "\n\n".join("Line." for _ in range(26))  # 26 lines + 25 blanks = 51 lines
        lay = screenplay.layout(fountain.parse(f"{filler}\n\nINT. NEW PLACE - DAY\n\nShe enters.\n"))
        heading_pages = [n for n, page in enumerate(lay.pages)
                         for _, ln in page if ln.kind == "scene_heading"]
        self.assertEqual(heading_pages, [1])

    def test_stats_report(self):
        with open(SAMPLE) as f:
            report = screenplay.report(fountain.parse(f.read()))
        self.assertIn("Scenes: 6", report)
        self.assertIn("MAYA", report)
        # Synopses attach to the scene that follows them.
        self.assertIn("Catalyst: the ticket", report.split("EXT. MAYA'S APARTMENT BUILDING")[1].split("\n")[1])


class PdfTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.tmp.cleanup()

    def path(self, name):
        return os.path.join(self.tmp.name, name)

    def test_format_sample(self):
        out = self.path("sample.pdf")
        main(["format", SAMPLE, "-o", out, "--scene-numbers"])
        self.assertGreater(os.path.getsize(out), 1000)

    def test_new_then_format(self):
        src = self.path("film.fountain")
        main(["new", src, "--title", "Test Film"])
        with open(src) as f:
            script = fountain.parse(f.read())
        self.assertEqual(script.title, "Test Film")
        sections = [e.text for e in script.elements if e.type == "section"]
        self.assertIn("ACT ONE", sections)
        self.assertTrue(any(s.startswith("Midpoint") for s in sections))
        main(["format", src, "-o", self.path("film.pdf")])

    def test_workbook_all_modes(self):
        for mode in ("film", "tv", "both"):
            out = self.path(f"{mode}.pdf")
            pages = workbook.build(out, workbook.Options(mode=mode, script_pages=3, notes_pages=1,
                                                          scene_card_pages=2, characters=2))
            self.assertGreater(pages, 20)
            self._check_links(out, pages)

    def _check_links(self, path, pages):
        try:
            import pymupdf
        except ImportError:
            self.skipTest("pymupdf not installed; link targets not checked")
        doc = pymupdf.open(path)
        self.assertEqual(doc.page_count, pages)
        self.assertAlmostEqual(doc[0].rect.width / doc[0].rect.height, 1404 / 1872, places=3)
        for page in doc:
            links = page.get_links()
            self.assertGreaterEqual(len(links), len(workbook.TABS))
            for link in links:
                self.assertTrue(0 <= link["page"] < doc.page_count, link)


if __name__ == "__main__":
    unittest.main()
