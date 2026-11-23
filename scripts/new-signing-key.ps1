param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$signingDir = Join-Path $projectRoot 'signing'
$keyFile = Join-Path $signingDir 'zheyou.p12'
$propertiesFile = Join-Path $signingDir 'release.properties'
if ((Test-Path -LiteralPath $keyFile) -or (Test-Path -LiteralPath $propertiesFile)) {
    throw 'Signing files already exist. Keep them for future upgrades; do not regenerate.'
}
New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
$random = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($random) } finally { $rng.Dispose() }
$password = [Convert]::ToBase64String($random)
$env:ZHEYOU_SIGNING_PASSWORD = $password
try {
    & keytool -genkeypair -keystore $keyFile -storetype PKCS12 -alias zheyou -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Zheyou Novel, O=zgozh' -storepass:env ZHEYOU_SIGNING_PASSWORD -keypass:env ZHEYOU_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
    $properties = @(
        'RELEASE_STORE_FILE=../signing/zheyou.p12'
        "RELEASE_STORE_PASSWORD=$password"
        'RELEASE_KEY_ALIAS=zheyou'
        "RELEASE_KEY_PASSWORD=$password"
    ) -join "`n"
    [IO.File]::WriteAllText($propertiesFile, $properties, (New-Object Text.UTF8Encoding($false)))
} finally {
    Remove-Item Env:ZHEYOU_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}
Write-Output 'Created local release signing files. Back up the entire signing directory securely.'
