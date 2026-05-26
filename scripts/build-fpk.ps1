$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "Building TypeScript..."
pnpm -s build

Write-Host "Fetching ktpwarp-web..."
& (Join-Path $root "scripts\fetch-ktpwarp-web.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$fpkDir = Join-Path $root "fpk"
$serverDir = Join-Path $fpkDir "app\server"

Write-Host "Staging app/server..."
$configApiSrc = Join-Path $fpkDir "app\server\config-api.js"
$configApiBackup = $null
if (Test-Path $configApiSrc) {
  $configApiBackup = Join-Path $env:TEMP "ktpwarp-config-api.js"
  Copy-Item $configApiSrc $configApiBackup -Force
}

if (Test-Path $serverDir) {
  Remove-Item $serverDir -Recurse -Force
}
New-Item -ItemType Directory -Path $serverDir -Force | Out-Null

Copy-Item (Join-Path $root "dist") (Join-Path $serverDir "dist") -Recurse -Force
Copy-Item (Join-Path $root "package.json") (Join-Path $serverDir "package.json") -Force
Copy-Item (Join-Path $root "pnpm-lock.yaml") (Join-Path $serverDir "pnpm-lock.yaml") -Force
Copy-Item (Join-Path $root "config.example.json") (Join-Path $serverDir "config.example.json") -Force
if ($configApiBackup) {
  Copy-Item $configApiBackup (Join-Path $serverDir "config-api.js") -Force
}

Write-Host "Installing production node_modules into fpk (offline-ready)..."
Push-Location $serverDir
npm install --omit=dev --no-fund --no-audit
if ($LASTEXITCODE -ne 0) {
  Pop-Location
  throw "npm install failed in fpk/app/server"
}
Pop-Location

$uiDir = Join-Path $fpkDir "app\ui"
$uiImages = Join-Path $uiDir "images"
New-Item -ItemType Directory -Path $uiImages -Force | Out-Null
Copy-Item (Join-Path $fpkDir "ICON_256.PNG") (Join-Path $uiImages "icon_256.png") -Force
Copy-Item (Join-Path $fpkDir "ICON.PNG") (Join-Path $uiImages "icon_128.png") -Force

$fnpack = Join-Path $root "fnpack.exe"
if (-not (Test-Path $fnpack)) {
  Write-Host "Downloading fnpack.exe..."
  Invoke-WebRequest -Uri "https://raw.githubusercontent.com/Brian099/fn_fpk_packages/main/fn-notepad/fnpack.exe" -OutFile $fnpack -UseBasicParsing
}

$outFpk = Join-Path $root "ktpwarp-server.fpk"
Remove-Item $outFpk -ErrorAction SilentlyContinue
Remove-Item (Join-Path $fpkDir "ktpwarp-server.fpk") -ErrorAction SilentlyContinue

Write-Host "Running fnpack build..."
& $fnpack build --directory $fpkDir
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$built = Join-Path $root "ktpwarp-server.fpk"
if (-not (Test-Path $built)) {
  throw "fnpack did not produce ktpwarp-server.fpk in $root"
}

if ($built -ne $outFpk) {
  if (Test-Path $outFpk) { Remove-Item $outFpk -Force }
  Move-Item -Force $built $outFpk
}

# Alias for third-party store naming convention
$alias = Join-Path $root "ktpwarp-server_all.fpk"
Copy-Item -Force $outFpk $alias

Write-Host "Done: $outFpk"
Write-Host "Also: $alias"
