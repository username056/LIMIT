[CmdletBinding()]
param(
    [ValidateSet('local', 'prod')]
    [string]$Environment = 'local',
    [switch]$NoStart,
    [switch]$NoOpen,
    [string]$EnvFile = 'infra/.env.local',
    [ValidateRange(10, 600)]
    [int]$TimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = if ([IO.Path]::IsPathRooted($EnvFile)) {
    [IO.Path]::GetFullPath($EnvFile)
} else {
    [IO.Path]::GetFullPath((Join-Path $repositoryRoot $EnvFile))
}
$composeBase = Join-Path $repositoryRoot 'infra/compose.yml'
$composeLocal = Join-Path $repositoryRoot 'infra/compose.local.yml'

function New-RandomValue {
    $bytes = [byte[]]::new(32)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }

    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', 'A').Replace('/', 'B')
}

function Initialize-LocalEnvironment {
    if (Test-Path -LiteralPath $environmentFile) {
        return
    }

    $mysqlPassword = New-RandomValue
    $mysqlRootPassword = New-RandomValue
    $mongodbPassword = New-RandomValue
    $redisPassword = New-RandomValue
    $lines = @(
        'MYSQL_DATABASE=limit'
        'MYSQL_USER=limit'
        "MYSQL_PASSWORD=$mysqlPassword"
        "MYSQL_ROOT_PASSWORD=$mysqlRootPassword"
        'FLYWAY_BASELINE_ON_MIGRATE=true'
        'FLYWAY_BASELINE_VERSION=1'
        'MONGODB_ROOT_USER=root'
        "MONGODB_ROOT_PASSWORD=$mongodbPassword"
        "MONGODB_URI=mongodb://root:${mongodbPassword}@mongodb:27017/limit?authSource=admin"
        "REDIS_PASSWORD=$redisPassword"
        "JWT_SECRET=$(New-RandomValue)$(New-RandomValue)"
        'REFRESH_TOKEN_STORE=redis'
        'EMAIL_VERIFICATION_STORE=redis'
        'EMAIL_VERIFICATION_DELIVERY_ENABLED=false'
        'FRONTEND_EMAIL_VERIFICATION_URL=http://localhost:5173/verify-email'
        'MAIL_HOST='
        'MAIL_PORT=587'
        'MAIL_USERNAME='
        'MAIL_PASSWORD='
        'MAIL_FROM='
        'INITIAL_ADMIN_ENABLED=false'
        'GOOGLE_OAUTH_ENABLED=false'
        'GOOGLE_OAUTH_CLIENT_ID='
        'GOOGLE_OAUTH_CLIENT_SECRET='
        'GOOGLE_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/google'
        'KAKAO_OAUTH_ENABLED=false'
        'KAKAO_OAUTH_CLIENT_ID='
        'KAKAO_OAUTH_CLIENT_SECRET='
        'KAKAO_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/kakao'
        'NAVER_OAUTH_ENABLED=false'
        'NAVER_OAUTH_CLIENT_ID='
        'NAVER_OAUTH_CLIENT_SECRET='
        'NAVER_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/naver'
    )

    [IO.File]::WriteAllLines($environmentFile, $lines, [Text.UTF8Encoding]::new($false))
    Write-Host "로컬 전용 환경 파일을 생성했습니다: $environmentFile"
}

function Wait-Docker {
    & docker info *> $null
    if ($LASTEXITCODE -eq 0) {
        return
    }

    if ($env:OS -eq 'Windows_NT') {
        $dockerDesktop = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
        if (Test-Path -LiteralPath $dockerDesktop) {
            Write-Host 'Docker Desktop을 시작하고 있습니다.'
            Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
        }
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Seconds 2
        & docker info *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
    } while ((Get-Date) -lt $deadline)

    throw "Docker 엔진이 $TimeoutSeconds초 안에 준비되지 않았습니다. Docker Desktop 상태를 확인하세요."
}

if ($Environment -eq 'local' -and -not $NoStart) {
    Initialize-LocalEnvironment
    Wait-Docker

    & docker compose `
        --env-file $environmentFile `
        -p limit-local `
        -f $composeBase `
        -f $composeLocal `
        up -d --build

    if ($LASTEXITCODE -ne 0) {
        throw "로컬 Docker Compose 기동에 실패했습니다."
    }
}

$tools = if ($Environment -eq 'local') {
    @(
        [pscustomobject]@{
            Name = 'Swagger'
            HealthUrl = 'http://localhost:18080/v3/api-docs'
            OpenUrl = 'http://localhost:18080/swagger-ui.html'
        },
        [pscustomobject]@{
            Name = 'Grafana'
            HealthUrl = 'http://localhost:3000/api/health'
            OpenUrl = 'http://localhost:3000/'
        }
    )
} else {
    @(
        [pscustomobject]@{
            Name = 'Swagger'
            HealthUrl = 'https://docs.l1mit.shop/v3/api-docs'
            OpenUrl = 'https://docs.l1mit.shop/'
        },
        [pscustomobject]@{
            Name = 'Grafana'
            HealthUrl = 'https://grafana.l1mit.shop/api/health'
            OpenUrl = 'https://grafana.l1mit.shop/'
        }
    )
}

foreach ($tool in $tools) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $ready = $false

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $tool.HealthUrl -Method Get -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    if (-not $ready) {
        throw "$($tool.Name)이(가) $TimeoutSeconds초 안에 준비되지 않았습니다: $($tool.HealthUrl)"
    }

    Write-Host "$($tool.Name) 준비 완료: $($tool.OpenUrl)"
    if (-not $NoOpen) {
        Start-Process $tool.OpenUrl
    }
}
