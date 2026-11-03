#!/usr/bin/env bash
# Plain TSV, not .gz: AGP unzips .gz assets at package time, and the APK deflates the file anyway.
# Builds the offline city asset from GeoNames cities5000 (CC BY 4.0).
set -euo pipefail

OUT="$(cd "$(dirname "$0")/.." && pwd)/core/common/src/main/assets/geonames/cities5000.tsv"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

curl -sSfL -o "$WORK/cities5000.zip" https://download.geonames.org/export/dump/cities5000.zip
unzip -p "$WORK/cities5000.zip" cities5000.txt |
  awk -F'\t' 'BEGIN { OFS = "\t" } { print $2, sprintf("%.3f", $5), sprintf("%.3f", $6), $9, $15 + 0 }' |
  sort -t$'\t' -k5,5nr -s >"$WORK/out.tsv"

mkdir -p "$(dirname "$OUT")"
mv "$WORK/out.tsv" "$OUT"
echo "wrote $OUT"
