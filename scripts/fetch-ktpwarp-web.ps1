$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$webDir = Join-Path $root "fpk\app\web"
$repo = "https://github.com/celesWuff/ktpwarp-web.git"
$branch = "gh-pages"

New-Item -ItemType Directory -Path $webDir -Force | Out-Null

$tmp = Join-Path $env:TEMP "ktpwarp-web-fetch-$(Get-Random)"
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }

Write-Host "Fetching ktpwarp-web ($branch)..."
git clone --depth 1 --branch $branch $repo $tmp
if ($LASTEXITCODE -ne 0) { throw "git clone failed" }

Get-ChildItem $tmp -Force | Where-Object { $_.Name -ne ".git" } | ForEach-Object {
  $dest = Join-Path $webDir $_.Name
  if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
  Copy-Item $_.FullName $dest -Recurse -Force
}

Remove-Item $tmp -Recurse -Force

$indexPath = Join-Path $webDir "index.html"
$html = Get-Content $indexPath -Raw -Encoding UTF8

$bootTag = '<script src="ktpwarp-fnOS-boot.js"></script>'
if ($html -notmatch [regex]::Escape($bootTag)) {
  $html = $html -replace "</body>", "$bootTag`n</body>"
}

$newInit = @'
 async init() {
 if (this.serverAddress === "") {
 try {
 const info = await fetch(new URL("/api/public-info", location.origin)).then((r) => r.json());
 if (info.suggestedWsUrl) this.serverAddress = info.suggestedWsUrl;
 } catch (e) {}
 }
 if (this.serverAddress !== "") {
 this.connect();
 }
'@

if ($html -match 'async init\(\)') {
  Write-Host "index.html already patched for fnOS"
} elseif ($html -match 'init\(\) \{\s+if \(this\.serverAddress !==') {
  $pattern = 'init\(\) \{\s+if \(this\.serverAddress !== ""\) \{\s+this\.connect\(\);\s+\}'
  $html = $html -replace $pattern, $newInit
  Write-Host "Patched index.html init() for auto WebSocket URL"
} else {
  Write-Warning "Could not patch index.html init(); manual connect may be required"
}

Set-Content -Path $indexPath -Value $html -Encoding UTF8 -NoNewline

Write-Host "ktpwarp-web staged in $webDir"
