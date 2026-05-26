#!/usr/bin/env bash
# Build fnOS .fpk (Linux/macOS and GitHub Actions).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Building TypeScript..."
pnpm -s build

echo "Fetching ktpwarp-web..."
bash "$ROOT/scripts/fetch-ktpwarp-web.sh"

FPK_DIR="$ROOT/fpk"
SERVER_DIR="$FPK_DIR/app/server"

echo "Staging app/server..."
rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR"

cp -r dist "$SERVER_DIR/"
cp package.json pnpm-lock.yaml config.example.json "$SERVER_DIR/"

echo "Installing production node_modules into fpk (offline-ready)..."
(
  cd "$SERVER_DIR"
  npm install --omit=dev --no-fund --no-audit
)

UI_IMAGES="$FPK_DIR/app/ui/images"
mkdir -p "$UI_IMAGES"
cp "$FPK_DIR/ICON_256.PNG" "$UI_IMAGES/icon_256.png"
cp "$FPK_DIR/ICON.PNG" "$UI_IMAGES/icon_128.png"

FNPACK="$ROOT/fnpack"
FNPACK_URL="${FNPACK_URL:-https://static2.fnnas.com/fnpack/fnpack-1.2.1-linux-amd64}"

if [[ ! -x "$FNPACK" ]]; then
  echo "Downloading fnpack..."
  curl -fsSL "$FNPACK_URL" -o "$FNPACK"
  chmod +x "$FNPACK"
fi

rm -f "$ROOT/ktpwarp-server.fpk" "$FPK_DIR/ktpwarp-server.fpk" "$ROOT/ktpwarp-server_all.fpk"

echo "Running fnpack build..."
"$FNPACK" build --directory "$FPK_DIR"

if [[ ! -f "$ROOT/ktpwarp-server.fpk" ]]; then
  # fnpack may write next to fpk dir depending on version
  if [[ -f "$FPK_DIR/ktpwarp-server.fpk" ]]; then
    mv -f "$FPK_DIR/ktpwarp-server.fpk" "$ROOT/ktpwarp-server.fpk"
  else
    echo "fnpack did not produce ktpwarp-server.fpk" >&2
    exit 1
  fi
fi

cp -f "$ROOT/ktpwarp-server.fpk" "$ROOT/ktpwarp-server_all.fpk"

echo "Done: $ROOT/ktpwarp-server.fpk"
echo "Also: $ROOT/ktpwarp-server_all.fpk"
