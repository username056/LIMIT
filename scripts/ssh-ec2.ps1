[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9.-]+$')]
    [string]$HostName = 'i15c203.p.ssafy.io',

    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_-]*$')]
    [string]$UserName = 'ubuntu',

    [string]$KeyPath = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh/I15C203T.pem')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    throw 'OpenSSH 클라이언트가 없습니다. Windows 선택적 기능에서 OpenSSH Client를 설치하세요.'
}

$resolvedKeyPath = (Resolve-Path -LiteralPath $KeyPath -ErrorAction Stop).Path

if ($IsWindows) {
    $currentIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls $resolvedKeyPath '/inheritance:r' '/grant:r' "${currentIdentity}:(R)" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'PEM 키 파일 권한을 현재 사용자 전용으로 설정하지 못했습니다.'
    }
}

Write-Host "EC2에 접속합니다: $UserName@$HostName"

& ssh `
    -i $resolvedKeyPath `
    -o IdentitiesOnly=yes `
    -o StrictHostKeyChecking=accept-new `
    -o ConnectTimeout=10 `
    "$UserName@$HostName"

exit $LASTEXITCODE
