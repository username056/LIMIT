$inputText = [Console]::In.ReadToEnd()
$sanitized = $inputText -replace [regex]::Escape('.env.example'), ''

$secretFilePattern = '(^|[/\\])\.env([.][a-z0-9_-]+)?(["\s]|$)|[.](pem|key|p12|jks)(["\s]|$)'
$dangerousCommandPattern = 'git\s+reset\s+--hard|git\s+push[^\r\n]*(--force|-f(\s|$))|rm\s+-rf|drop\s+(database|schema)|kubectl[^\r\n]*\sdelete'

if ($sanitized -match $secretFilePattern) {
    [Console]::Error.WriteLine('Blocked: Secret or real environment files cannot be read or modified.')
    exit 2
}

if ($inputText -match $dangerousCommandPattern) {
    [Console]::Error.WriteLine('Blocked: destructive Git, file, database, or infrastructure command.')
    exit 2
}

exit 0

