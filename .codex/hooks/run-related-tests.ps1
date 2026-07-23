[Console]::In.ReadToEnd() | Out-Null

$root = git rev-parse --show-toplevel 2>$null
if (-not $root) {
    $root = (Get-Location).Path
}

$changed = @()
git -C $root rev-parse --is-inside-work-tree 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    $base = 'HEAD'
    git -C $root rev-parse --verify origin/dev 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $base = git -C $root merge-base HEAD origin/dev
    }
    $changed += git -C $root diff --name-only $base 2>$null
    $changed += git -C $root ls-files --others --exclude-standard -- frontend backend 2>$null
    $changed = $changed | Sort-Object -Unique
} else {
    $changed = @('frontend', 'backend')
}

if ($changed -match '^frontend/') {
    $frontendDir = Join-Path $root 'frontend'
    if (Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules') -PathType Container) {
        Push-Location $frontendDir
        try {
            & npm run lint
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & npm run test
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & npm run build
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        } finally {
            Pop-Location
        }
    } else {
        Write-Output '프론트 검증 건너뜀: frontend/node_modules가 없습니다. npm install 후 실행하세요.'
    }
}

if ($changed -match '^backend/') {
    $backendDir = Join-Path $root 'backend'
    $wrapper = Join-Path $backendDir 'gradlew.bat'
    Push-Location $backendDir
    try {
        if (Test-Path -LiteralPath $wrapper -PathType Leaf) {
            & $wrapper test bootJar
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        } elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
            & gradle test bootJar
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        } else {
            Write-Output '백엔드 검증 건너뜀: Gradle Wrapper가 없고 전역 Gradle도 없습니다.'
        }
    } finally {
        Pop-Location
    }
}
