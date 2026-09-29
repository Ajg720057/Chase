#!/usr/bin/env python3
"""Inline src/*.js into draftroom.html.

Writes two files:
  dist/draftroom-artifact.html  page body for publishing as a claude.ai artifact
  dist/Draftroom.html           standalone file to open in any browser, offline
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ORDER = ["core.js", "editor.js", "app.js", "main.js"]

shell = open(os.path.join(HERE, "draftroom.html"), encoding="utf-8").read()
js = "\n".join(open(os.path.join(HERE, "src", f), encoding="utf-8").read() for f in ORDER)
body = shell.replace("/*@@APP@@*/", "(() => {\n" + js + "\n})();")
os.makedirs(os.path.join(HERE, "dist"), exist_ok=True)
with open(os.path.join(HERE, "dist", "draftroom-artifact.html"), "w", encoding="utf-8") as f:
    f.write(body)
standalone = (
    '<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
    "<style>body{margin:0}[hidden]{display:none!important}</style>\n</head>\n<body>\n"
    + body + "\n</body>\n</html>\n"
)
with open(os.path.join(HERE, "dist", "Draftroom.html"), "w", encoding="utf-8") as f:
    f.write(standalone)
print("Built dist/draftroom-artifact.html and dist/Draftroom.html")
