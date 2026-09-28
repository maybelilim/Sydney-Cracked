# Sydney 3.0.0 (loader MC 1.21.4) — keygen для .sydney\authorization
# Крипта лоадера, извлечённая реверсом:
#   key  = PBKDF2-HMAC-SHA256(pw="dW0wBAlrg62G0eaJpeDvpJjH2jenDyMKhW6tN5uaHhW3IHvWr5zYnkrwiRH5znjV",
#                             salt="JZ1NNWMFKN47jg9mCkAQlIwKDTcNy3Kb", iter=100000, len=32)
#   file = base64url( IV[16] || AES-256-CBC-PKCS5(plaintext, key, IV) )
# Проверено: лоадер расшифровывает и принимает ключ (проверка "Invalid Credentials" обходится).

param(
    [string]$Payload = "user:2099-12-31:HWIDHASH",  # содержимое лицензии
    [string]$Out = "$env:USERPROFILE\.sydney\authorization"
)

$pw   = "dW0wBAlrg62G0eaJpeDvpJjH2jenDyMKhW6tN5uaHhW3IHvWr5zYnkrwiRH5znjV"
$salt = [System.Text.Encoding]::UTF8.GetBytes("JZ1NNWMFKN47jg9mCkAQlIwKDTcNy3Kb")

$kdf = New-Object System.Security.Cryptography.Rfc2898DeriveBytes(
    [System.Text.Encoding]::UTF8.GetBytes($pw), $salt, 100000,
    [System.Security.Cryptography.HashAlgorithmName]::SHA256)
$key = $kdf.GetBytes(32)

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$iv  = New-Object byte[] 16
$rng.GetBytes($iv)

$aes = [System.Security.Cryptography.Aes]::Create()
$aes.Key = $key; $aes.Mode = 'CBC'; $aes.Padding = 'PKCS7'; $aes.IV = $iv
$enc = $aes.CreateEncryptor()
$pt  = [System.Text.Encoding]::UTF8.GetBytes($Payload)
$ct  = $enc.TransformFinalBlock($pt, 0, $pt.Length)

$blob = New-Object byte[] (16 + $ct.Length)
[Array]::Copy($iv, 0, $blob, 0, 16)
[Array]::Copy($ct, 0, $blob, 16, $ct.Length)

$f = [Convert]::ToBase64String($blob).Replace('+','-').Replace('/','_').TrimEnd('=')
New-Item -ItemType Directory -Force -Path (Split-Path $Out) | Out-Null
[IO.File]::WriteAllText($Out, $f)
Write-Host "authorization file: $Out"
Write-Host "payload: $Payload"
