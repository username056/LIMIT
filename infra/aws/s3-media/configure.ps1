[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("dev", "prod")]
    [string]$Environment,

    [ValidateSet("Apply", "Verify", "Smoke")]
    [string]$Action = "Verify",

    [string]$Profile = "limit-bootstrap",
    [string]$Region = "ap-northeast-2",
    [string]$NameSuffix = "0b849303"
)

$ErrorActionPreference = "Stop"

$bucketName = "l1mit-$Environment-media-$NameSuffix"
$roleName = "l1mit-$Environment-backend"
$policyName = "l1mit-$Environment-media-access"
$aws = Get-Command aws -ErrorAction SilentlyContinue

if (-not $aws) {
    $userInstall = Join-Path $env:LOCALAPPDATA "Programs\Amazon\AWSCLIV2\aws.exe"
    if (Test-Path -LiteralPath $userInstall) {
        $aws = $userInstall
    } else {
        throw "AWS CLI v2 is required."
    }
}

function Invoke-Aws {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & $aws @Arguments --profile $Profile --region $Region --no-cli-pager
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI command failed: aws $($Arguments -join ' ')"
    }
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)][object]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $json = $Value | ConvertTo-Json -Depth 20
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Test-BucketExists {
    $existingBucket = & $aws s3api list-buckets `
        --profile $Profile `
        --query "Buckets[?Name=='$bucketName'].Name | [0]" `
        --output text `
        --no-cli-pager
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to list S3 buckets."
    }
    return $existingBucket -eq $bucketName
}

function Test-RoleExists {
    $existingRole = & $aws iam list-roles `
        --profile $Profile `
        --query "Roles[?RoleName=='$roleName'].RoleName | [0]" `
        --output text `
        --no-cli-pager
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to list IAM roles."
    }
    return $existingRole -eq $roleName
}

function Apply-Configuration {
    $tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) "limit-s3-media-$Environment"
    [System.IO.Directory]::CreateDirectory($tempDirectory) | Out-Null

    try {
        if (-not (Test-BucketExists)) {
            Invoke-Aws s3api create-bucket `
                --bucket $bucketName `
                --create-bucket-configuration "LocationConstraint=$Region"
        }

        Invoke-Aws s3api put-public-access-block `
            --bucket $bucketName `
            --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

        Invoke-Aws s3api put-bucket-encryption `
            --bucket $bucketName `
            --server-side-encryption-configuration "Rules=[{ApplyServerSideEncryptionByDefault={SSEAlgorithm=AES256},BucketKeyEnabled=false}]"

        Invoke-Aws s3api put-bucket-versioning `
            --bucket $bucketName `
            --versioning-configuration "Status=Enabled"

        $origins = if ($Environment -eq "prod") {
            @("https://l1mit.shop", "https://www.l1mit.shop")
        } else {
            @("http://localhost:5173")
        }

        $corsPath = Join-Path $tempDirectory "cors.json"
        Write-JsonFile -Path $corsPath -Value @{
            CORSRules = @(
                @{
                    AllowedOrigins = @($origins)
                    AllowedMethods = @("PUT", "GET", "HEAD")
                    AllowedHeaders = @("*")
                    ExposeHeaders = @("ETag")
                    MaxAgeSeconds = 3600
                }
            )
        }
        Invoke-Aws s3api put-bucket-cors --bucket $bucketName --cors-configuration "file://$corsPath"

        $lifecyclePath = Join-Path $tempDirectory "lifecycle.json"
        Write-JsonFile -Path $lifecyclePath -Value @{
            Rules = @(
                @{
                    ID = "abort-incomplete-multipart-uploads"
                    Status = "Enabled"
                    Filter = @{}
                    AbortIncompleteMultipartUpload = @{ DaysAfterInitiation = 1 }
                },
                @{
                    ID = "expire-unused-temporary-objects"
                    Status = "Enabled"
                    Filter = @{ Prefix = "tmp/" }
                    Expiration = @{ Days = 1 }
                    NoncurrentVersionExpiration = @{ NoncurrentDays = 1 }
                }
            )
        }
        Invoke-Aws s3api put-bucket-lifecycle-configuration `
            --bucket $bucketName `
            --lifecycle-configuration "file://$lifecyclePath"

        $trustPath = Join-Path $tempDirectory "trust.json"
        Write-JsonFile -Path $trustPath -Value @{
            Version = "2012-10-17"
            Statement = @(
                @{
                    Effect = "Allow"
                    Principal = @{ Service = "ec2.amazonaws.com" }
                    Action = "sts:AssumeRole"
                }
            )
        }

        if (-not (Test-RoleExists)) {
            Invoke-Aws iam create-role `
                --role-name $roleName `
                --assume-role-policy-document "file://$trustPath" `
                --description "Least-privilege S3 media access for the Limit $Environment backend"
        } else {
            Invoke-Aws iam update-assume-role-policy `
                --role-name $roleName `
                --policy-document "file://$trustPath"
        }

        $policyPath = Join-Path $tempDirectory "policy.json"
        Write-JsonFile -Path $policyPath -Value @{
            Version = "2012-10-17"
            Statement = @(
                @{
                    Sid = "ListMediaPrefixes"
                    Effect = "Allow"
                    Action = @("s3:ListBucket")
                    Resource = "arn:aws:s3:::$bucketName"
                    Condition = @{
                        StringLike = @{
                            "s3:prefix" = @("tmp/*", "listings/*", "evidence/*")
                        }
                    }
                },
                @{
                    Sid = "ManageMediaObjects"
                    Effect = "Allow"
                    Action = @(
                        "s3:GetObject",
                        "s3:PutObject",
                        "s3:DeleteObject",
                        "s3:AbortMultipartUpload",
                        "s3:ListMultipartUploadParts"
                    )
                    Resource = @(
                        "arn:aws:s3:::$bucketName/tmp/*",
                        "arn:aws:s3:::$bucketName/listings/*",
                        "arn:aws:s3:::$bucketName/evidence/*"
                    )
                }
            )
        }
        Invoke-Aws iam put-role-policy `
            --role-name $roleName `
            --policy-name $policyName `
            --policy-document "file://$policyPath"
    } finally {
        if (Test-Path -LiteralPath $tempDirectory) {
            Remove-Item -LiteralPath $tempDirectory -Recurse -Force
        }
    }
}

function Verify-Configuration {
    Invoke-Aws s3api get-bucket-location --bucket $bucketName
    Invoke-Aws s3api get-public-access-block --bucket $bucketName
    Invoke-Aws s3api get-bucket-encryption --bucket $bucketName
    Invoke-Aws s3api get-bucket-versioning --bucket $bucketName
    Invoke-Aws s3api get-bucket-cors --bucket $bucketName
    Invoke-Aws s3api get-bucket-lifecycle-configuration --bucket $bucketName
    Invoke-Aws iam get-role --role-name $roleName --query "Role.{Arn:Arn,CreateDate:CreateDate}"
    Invoke-Aws iam get-role-policy --role-name $roleName --policy-name $policyName
}

function Invoke-SmokeTest {
    $key = "tmp/smoke/aws-cli-$([guid]::NewGuid().ToString('N')).txt"
    $file = Join-Path ([System.IO.Path]::GetTempPath()) "limit-s3-smoke-$Environment.txt"
    [System.IO.File]::WriteAllText($file, "limit s3 smoke test", [System.Text.UTF8Encoding]::new($false))

    try {
        Invoke-Aws s3api put-object `
            --bucket $bucketName `
            --key $key `
            --body $file `
            --content-type "text/plain"
        Invoke-Aws s3api head-object --bucket $bucketName --key $key
        Invoke-Aws s3api get-object --bucket $bucketName --key $key "$file.downloaded"
        Invoke-Aws s3api delete-object --bucket $bucketName --key $key
        Write-Host "Smoke test passed for s3://$bucketName/$key"
    } finally {
        Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath "$file.downloaded" -Force -ErrorAction SilentlyContinue
    }
}

switch ($Action) {
    "Apply" {
        Apply-Configuration
        Verify-Configuration
    }
    "Verify" {
        Verify-Configuration
    }
    "Smoke" {
        Invoke-SmokeTest
    }
}
