#!/usr/bin/env bash
# Fetch ktpwarp-web (gh-pages) into fpk/app/web and patch for fnOS auto-connect.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT/fpk/app/web"
REPO="https://github.com/celesWuff/ktpwarp-web.git"
BRANCH="gh-pages"

mkdir -p "$WEB_DIR"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Fetching ktpwarp-web ($BRANCH)..."
git clone --depth 1 --branch "$BRANCH" "$REPO" "$TMP"

for item in "$TMP"/*; do
  name="$(basename "$item")"
  [[ "$name" == .git ]] && continue
  rm -rf "$WEB_DIR/$name"
  cp -a "$item" "$WEB_DIR/$name"
done

INDEX="$WEB_DIR/index.html"
export INDEX
python3 <<'PY'
import os
import re
from pathlib import Path

index = Path(os.environ["INDEX"])
html = index.read_text(encoding="utf-8")

boot = '<script src="ktpwarp-fnOS-boot.js"></script>'
if boot not in html:
    html = html.replace("</body>", boot + "\n</body>")

new_init = """ async init() {
 if (this.serverAddress === "") {
 try {
 const info = await fetch(new URL("/api/public-info", location.origin)).then((r) => r.json());
 if (info.suggestedWsUrl) this.serverAddress = info.suggestedWsUrl;
 } catch (e) {}
 }
 if (this.serverAddress !== "") {
 this.connect();
 }"""

if "async init()" in html:
    print("index.html already patched for fnOS")
elif re.search(r"init\(\) \{\s+if \(this\.serverAddress !==", html):
    html = re.sub(
        r"init\(\) \{\s+if \(this\.serverAddress !== \"\"\) \{\s+this\.connect\(\);\s+\}",
        new_init,
        html,
        count=1,
    )
    print("Patched index.html init() for auto WebSocket URL")
else:
    print("WARNING: Could not patch index.html init(); manual connect may be required")

index.write_text(html, encoding="utf-8")
PY

echo "ktpwarp-web staged in $WEB_DIR"
