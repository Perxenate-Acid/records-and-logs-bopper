#!/usr/bin/env bash
# Build script for the Records & Logs Bopper Jingle plugin.
# Produces: out/records-and-logs-bopper-1.0.0.jar
#
# NOTE: every path passed to the native Windows JDK tools (javac/jar/java)
# must be converted with cygpath -w, because MSYS2 does not convert
# /c/... style paths for native executables (they would be misread as C:\c\...).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/out"
JDK_BIN="/c/Program Files/Java/jdk-21/bin"

w() { cygpath -w "$1"; }

# Clean previous build output (file-level delete, then empty dirs depth-first)
if [ -d "$OUT" ]; then
  find "$OUT" -type f -delete
  find "$OUT" -depth -type d -empty -delete
fi
mkdir -p "$OUT/classes" "$OUT/stub-classes" "$OUT/test-classes"

# Convert a find result list into a Windows-path @argfile for javac.
make_argfile() {
  local dir="$1" outfile="$2"
  find "$dir" -name '*.java' | while read -r f; do cygpath -w "$f"; done > "$outfile"
}

echo "[1/4] Compiling API stubs (compile-only, never packaged)..."
make_argfile "$ROOT/stubs" "$OUT/stub-sources.txt"
"$JDK_BIN/javac" -encoding UTF-8 --release 17 -d "$(w "$OUT/stub-classes")" @"$(w "$OUT/stub-sources.txt")"

echo "[2/4] Compiling plugin sources against stubs..."
make_argfile "$ROOT/src" "$OUT/main-sources.txt"
"$JDK_BIN/javac" -encoding UTF-8 --release 17 -cp "$(w "$OUT/stub-classes")" -d "$(w "$OUT/classes")" @"$(w "$OUT/main-sources.txt")"

echo "[3/4] Packaging jar (jingle.plugin.json + plugin classes only)..."
cp "$ROOT/jingle.plugin.json" "$OUT/classes/"
(cd "$OUT/classes" && "$JDK_BIN/jar" cf "$(w "$OUT/records-and-logs-bopper-1.0.0.jar")" .)

echo "[4/4] Running standalone logic tests..."
make_argfile "$ROOT/test" "$OUT/test-sources.txt"
"$JDK_BIN/javac" -encoding UTF-8 --release 17 -cp "$(w "$OUT/classes")" -d "$(w "$OUT/test-classes")" @"$(w "$OUT/test-sources.txt")"
"$JDK_BIN/java" -cp "$(w "$OUT/classes");$(w "$OUT/test-classes")" TestMain

echo ""
echo "Build OK: $OUT/records-and-logs-bopper-1.0.0.jar"
