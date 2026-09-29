#!/bin/sh
# Rebuild the ready-made PDFs in dist/.
set -e
cd "$(dirname "$0")"
python3 -m scriptwriter workbook --mode film -o dist/screenplay-workbook-film.pdf
python3 -m scriptwriter workbook --mode tv --tv-format hour -o dist/screenplay-workbook-tv-hour.pdf
python3 -m scriptwriter workbook --mode tv --tv-format half-hour -o dist/screenplay-workbook-tv-half-hour.pdf
python3 -m scriptwriter format examples/sample.fountain -o dist/sample-screenplay.pdf
