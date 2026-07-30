[CmdletBinding()]
param(
    [string]$Profile = 'limit-bootstrap',
    [string]$Region = 'ap-northeast-2',
    [string]$UserName = 'l1mit-prod-runtime',
    [string]$Bucket = 'l1mit-prod-media-0b849303',
    [string]$HostName = 'i15c203.p.ssafy.io',
    [string]$SshUser = 'ubuntu',
    [string]$KeyPath = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh/I15C203T.pem'),
    [string]$DeployPath = '/opt/limit'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$created = $false
$persisted = $false
$accessKeyId = ''
$secretAccessKey = ''
$smokeKey = "tmp/smoke/runtime-$([guid]::NewGuid().ToString('N')).txt"
$smokeObjectExists = $false
$uploadFile = New-TemporaryFile
$downloadFile = "$($uploadFile.FullName).download"

function Clear-RuntimeEnvironment {
    Remove-Item Env:AWS_ACCESS_KEY_ID -ErrorAction SilentlyContinue
    Remove-Item Env:AWS_SECRET_ACCESS_KEY -ErrorAction SilentlyContinue
    Remove-Item Env:AWS_SESSION_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:AWS_DEFAULT_REGION -ErrorAction SilentlyContinue
}

function Invoke-AwsQuiet {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & aws @Arguments 1>$null 2>$null
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

try {
    [System.IO.File]::WriteAllText(
        $uploadFile.FullName,
        'limit production S3 credential smoke test',
        [System.Text.UTF8Encoding]::new($false)
    )

    $existingKeyCount = & aws iam list-access-keys `
        --user-name $UserName `
        --profile $Profile `
        --query 'length(AccessKeyMetadata)' `
        --output text `
        --no-cli-pager 2>$null
    if ($LASTEXITCODE -ne 0 -or $existingKeyCount -ne '0') {
        throw 'The runtime IAM user must exist without an Access Key.'
    }

    $keyJson = & aws iam create-access-key `
        --user-name $UserName `
        --profile $Profile `
        --output json `
        --no-cli-pager 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'Access Key creation failed.'
    }

    $keyObject = $keyJson | ConvertFrom-Json
    $accessKeyId = [string]$keyObject.AccessKey.AccessKeyId
    $secretAccessKey = [string]$keyObject.AccessKey.SecretAccessKey
    if ([string]::IsNullOrWhiteSpace($accessKeyId) `
            -or [string]::IsNullOrWhiteSpace($secretAccessKey) `
            -or $accessKeyId -match '[\r\n]' `
            -or $secretAccessKey -match '[\r\n]') {
        throw 'AWS returned an invalid Access Key.'
    }
    $created = $true

    $env:AWS_ACCESS_KEY_ID = $accessKeyId
    $env:AWS_SECRET_ACCESS_KEY = $secretAccessKey
    $env:AWS_DEFAULT_REGION = $Region

    $uploaded = $false
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        $putExitCode = Invoke-AwsQuiet @(
            's3api', 'put-object',
            '--bucket', $Bucket,
            '--key', $smokeKey,
            '--body', $uploadFile.FullName,
            '--content-type', 'text/plain',
            '--output', 'json',
            '--no-cli-pager'
        )
        if ($putExitCode -eq 0) {
            $uploaded = $true
            $smokeObjectExists = $true
            break
        }
        Start-Sleep -Seconds 5
    }
    if (-not $uploaded) {
        throw 'The runtime credential could not upload to S3.'
    }

    $headExitCode = Invoke-AwsQuiet @(
        's3api', 'head-object',
        '--bucket', $Bucket,
        '--key', $smokeKey,
        '--output', 'json',
        '--no-cli-pager'
    )
    if ($headExitCode -ne 0) {
        throw 'S3 HEAD smoke test failed.'
    }

    $getExitCode = Invoke-AwsQuiet @(
        's3api', 'get-object',
        '--bucket', $Bucket,
        '--key', $smokeKey,
        $downloadFile,
        '--output', 'json',
        '--no-cli-pager'
    )
    if ($getExitCode -ne 0) {
        throw 'S3 GET smoke test failed.'
    }

    $deleteExitCode = Invoke-AwsQuiet @(
        's3api', 'delete-object',
        '--bucket', $Bucket,
        '--key', $smokeKey,
        '--output', 'json',
        '--no-cli-pager'
    )
    if ($deleteExitCode -ne 0) {
        throw 'S3 DELETE smoke test failed.'
    }
    $smokeObjectExists = $false
    Write-Host 'runtime-credential-s3-smoke:passed'

    $body = @(
        "AWS_ACCESS_KEY_ID=$accessKeyId"
        "AWS_SECRET_ACCESS_KEY=$secretAccessKey"
        'AWS_SESSION_TOKEN='
    ) -join "`n"
    $sshArguments = @(
        '-i', (Resolve-Path -LiteralPath $KeyPath).Path,
        '-o', 'BatchMode=yes',
        '-o', 'IdentitiesOnly=yes',
        '-o', 'StrictHostKeyChecking=yes',
        "$SshUser@$HostName",
        "sh '$DeployPath/scripts/apply-ec2-env-remote.sh' '$DeployPath'"
    )
    $remoteOutput = @($body | & ssh @sshArguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw 'EC2 credential injection failed.'
    }
    $persisted = $true

    $remoteOutput |
        Where-Object { $_ -match '^(applied|backup):' } |
        ForEach-Object { Write-Host $_ }

    $verifyCommand = "cd '$DeployPath' " `
        + "&& grep -Eq '^AWS_ACCESS_KEY_ID=.+$' infra/.env " `
        + "&& grep -Eq '^AWS_SECRET_ACCESS_KEY=.+$' infra/.env " `
        + "&& docker compose --env-file infra/.env -p limit-prod " `
        + "-f infra/compose.yml -f infra/compose.prod.yml config --quiet"
    $verifyArguments = @($sshArguments[0..8]) + $verifyCommand
    & ssh @verifyArguments 1>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'EC2 credential verification failed.'
    }
    Write-Host 'ec2-runtime-credential-config:passed'
} catch {
    Clear-RuntimeEnvironment
    if ($created -and -not $persisted -and -not [string]::IsNullOrWhiteSpace($accessKeyId)) {
        [void](Invoke-AwsQuiet @(
            'iam', 'delete-access-key',
            '--user-name', $UserName,
            '--access-key-id', $accessKeyId,
            '--profile', $Profile,
            '--no-cli-pager'
        ))
        Write-Host 'orphaned-access-key:revoked'
    }
    throw
} finally {
    if ($smokeObjectExists `
            -and -not [string]::IsNullOrWhiteSpace($accessKeyId) `
            -and -not [string]::IsNullOrWhiteSpace($secretAccessKey)) {
        $env:AWS_ACCESS_KEY_ID = $accessKeyId
        $env:AWS_SECRET_ACCESS_KEY = $secretAccessKey
        $env:AWS_DEFAULT_REGION = $Region
        [void](Invoke-AwsQuiet @(
            's3api', 'delete-object',
            '--bucket', $Bucket,
            '--key', $smokeKey,
            '--no-cli-pager'
        ))
    }
    Clear-RuntimeEnvironment
    Remove-Item -LiteralPath $uploadFile.FullName -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
    $secretAccessKey = ''
    $accessKeyId = ''
}
