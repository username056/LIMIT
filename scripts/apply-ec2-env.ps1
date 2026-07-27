[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [Alias('KeyValue')]
    [string[]]$Entry = @(),

    [ValidatePattern('^[A-Za-z0-9.-]+$')]
    [string]$HostName = 'i15c203.p.ssafy.io',

    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_-]*$')]
    [string]$UserName = 'ubuntu',

    [string]$KeyPath = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh/I15C203T.pem'),

    [ValidatePattern('^/[A-Za-z0-9._/-]+$')]
    [string]$DeployPath = '/opt/limit',

    [switch]$AllowUnknownKey,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$contractFiles = @(
    (Join-Path $repositoryRoot 'infra/.env.example'),
    (Join-Path $repositoryRoot 'infra/compose.yml'),
    (Join-Path $repositoryRoot 'infra/compose.prod.yml')
)
$allowedKeys = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal
)

foreach ($contractFile in $contractFiles) {
    if (-not (Test-Path -LiteralPath $contractFile -PathType Leaf)) {
        throw "환경변수 계약 파일을 찾을 수 없습니다: $contractFile"
    }

    foreach ($contractLine in Get-Content -LiteralPath $contractFile) {
        foreach ($match in [regex]::Matches($contractLine, '(?:^|\$\{)([A-Z][A-Z0-9_]*)')) {
            [void]$allowedKeys.Add($match.Groups[1].Value)
        }
    }
}

$entries = [ordered]@{}
function Add-EnvironmentEntry {
    param([Parameter(Mandatory = $true)][string]$Line)

    if ($Line.Contains("`r") -or $Line.Contains("`n") -or $Line.Contains([char]0)) {
        throw '환경변수 값에는 개행 또는 NUL 문자를 사용할 수 없습니다.'
    }

    $separatorIndex = $Line.IndexOf('=')
    if ($separatorIndex -le 0) {
        throw '입력 형식이 KEY=VALUE가 아닙니다.'
    }

    $key = $Line.Substring(0, $separatorIndex).Trim()
    $value = $Line.Substring($separatorIndex + 1)
    if ($key -notmatch '^[A-Z][A-Z0-9_]*$') {
        throw "환경변수 키 형식이 올바르지 않습니다: $key"
    }
    if (-not $AllowUnknownKey -and -not $allowedKeys.Contains($key)) {
        throw "프로젝트 환경변수 계약에 없는 키입니다: $key (-AllowUnknownKey로 명시적 허용 가능)"
    }

    $entries[$key] = $value
}

if ($Entry.Count -gt 0) {
    foreach ($line in $Entry) {
        Add-EnvironmentEntry -Line $line
    }
} else {
    Write-Host 'KEY=VALUE 형식으로 입력하세요. 빈 줄을 입력하면 적용을 시작합니다.'
    while ($true) {
        $line = Read-Host
        if ([string]::IsNullOrEmpty($line)) {
            break
        }
        Add-EnvironmentEntry -Line $line
    }
}

if ($entries.Count -eq 0) {
    throw '적용할 KEY=VALUE가 없습니다.'
}

$keyNames = @($entries.Keys)
Write-Host "적용 대상: $UserName@$HostName ($DeployPath/infra/.env)"
Write-Host "적용할 키: $($keyNames -join ', ')"

if ($DryRun) {
    Write-Host 'DryRun 완료: EC2에는 접속하지 않았습니다.'
    exit 0
}

$sshCommand = Get-Command ssh -ErrorAction SilentlyContinue
if (-not $sshCommand) {
    throw 'OpenSSH 클라이언트를 찾을 수 없습니다.'
}

$resolvedKeyPath = (Resolve-Path -LiteralPath $KeyPath -ErrorAction Stop).Path
$remoteScriptPath = Join-Path $PSScriptRoot 'apply-ec2-env-remote.sh'
$remoteScript = Get-Content -Raw -LiteralPath $remoteScriptPath -ErrorAction Stop
$remoteScriptBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($remoteScript))
$remoteCommand = 'bash -c "$(printf %s ''{0}'' | base64 -d)" -- ''{1}''' -f `
    $remoteScriptBase64, $DeployPath

$bodyLines = foreach ($key in $entries.Keys) {
    "$key=$($entries[$key])"
}
$body = ($bodyLines -join "`n") + "`n"
$sshArguments = @(
    '-i', $resolvedKeyPath,
    '-o', 'BatchMode=yes',
    '-o', 'IdentitiesOnly=yes',
    '-o', 'StrictHostKeyChecking=yes',
    '-o', 'ConnectTimeout=10',
    "$UserName@$HostName",
    $remoteCommand
)

$previousOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    $remoteOutput = @($body | & $sshCommand.Source @sshArguments 2>&1)
    $sshExitCode = $LASTEXITCODE
} finally {
    $OutputEncoding = $previousOutputEncoding
}

if ($sshExitCode -ne 0) {
    $safeDiagnostic = ($remoteOutput -join "`n")
    $valuesToRedact = @(
        $entries.Values |
            ForEach-Object { [string]$_ } |
            Where-Object { -not [string]::IsNullOrEmpty($_) } |
            Sort-Object -Property Length -Descending
    )
    foreach ($value in $valuesToRedact) {
        $safeDiagnostic = $safeDiagnostic.Replace($value, '<redacted>')
    }
    throw "EC2 환경변수 적용에 실패했습니다.`n$safeDiagnostic"
}

foreach ($outputLine in $remoteOutput) {
    if ($outputLine -match '^(applied|backup):') {
        Write-Host $outputLine
    }
}
Write-Host '환경 파일 갱신과 Compose 설정 검증이 완료됐습니다.'
Write-Host '실행 중인 컨테이너에는 다음 Blue-Green 배포부터 적용됩니다.'
