# 랜덤 키는 최초 한 번만 생성하고, 기존 키가 있으면 그대로 유지한다.
$ErrorActionPreference = 'Stop'
$backRoot = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $backRoot 'config'
$keyPath = Join-Path $keyDirectory 'jwt-secret.properties'

if (Test-Path -LiteralPath $keyPath) {
    Write-Output '기존 JWT 키 파일을 유지합니다.'
    return
}

[IO.Directory]::CreateDirectory($keyDirectory) | Out-Null
$keyBytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($keyBytes)
} finally {
    $random.Dispose()
}

# Base64는 바이너리 키를 파일에 저장하기 위한 표현이며 암호화가 아니다.
$content = 'JWT_SECRET=' + [Convert]::ToBase64String($keyBytes) + [Environment]::NewLine
# 동시에 실행되더라도 이미 생성된 키를 덮어쓰지 않는다.
$stream = [IO.File]::Open($keyPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $bytes = [Text.Encoding]::UTF8.GetBytes($content)
    $stream.Write($bytes, 0, $bytes.Length)
} finally {
    $stream.Dispose()
}
Write-Output 'JWT 랜덤 키 파일을 생성했습니다. 키 값은 출력하지 않습니다.'