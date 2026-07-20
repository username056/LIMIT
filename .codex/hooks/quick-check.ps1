[Console]::In.ReadToEnd() | Out-Null

$root = git rev-parse --show-toplevel 2>$null
if (-not $root) {
    $root = (Get-Location).Path
}

$files = @()
$files += git -C $root diff --name-only 2>$null
$files += git -C $root diff --cached --name-only 2>$null
$files += git -C $root ls-files --others --exclude-standard 2>$null
$files = $files | Sort-Object -Unique

$secretPattern = '(?i)(api[_-]?key|secret|token|password)\s*[:=]\s*["''][^$<{][^"'']{7,}["'']'

foreach ($relativePath in $files) {
    $path = Join-Path $root $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        continue
    }
    if ($path -match '\.(png|jpe?g|gif|jar|class)$') {
        continue
    }

    $content = Get-Content -Raw -ErrorAction SilentlyContinue -LiteralPath $path
    if ($content -match $secretPattern) {
        [Console]::Error.WriteLine("Secret-like value found in $relativePath")
        exit 2
    }
}

exit 0

