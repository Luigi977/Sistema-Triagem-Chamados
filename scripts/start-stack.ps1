param([switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.env.docker'
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
if (-not (Test-Path -LiteralPath $configPath)) {
    $settings = @(
        '# Segredos locais gerados automaticamente. Nunca versionar.',
        "DOCKER_POSTGRES_PASSWORD=$(New-LocalSecret)",
        "DOCKER_EVOLUTION_DB_PASSWORD=$(New-LocalSecret)",
        "DOCKER_EVOLUTION_API_KEY=$(New-LocalSecret)",
        "DOCKER_EVOLUTION_WEBHOOK_TOKEN=$(New-LocalSecret)",
        'DOCKER_EVOLUTION_INSTANCE=suporte',
        'DOCKER_ADMIN_LOGIN=admin',
        "DOCKER_ADMIN_PASSWORD=$(New-LocalSecret)",
        'TRIAGEM_BIND_ADDRESS=127.0.0.1',
        'TRIAGEM_HTTP_PORT=8080'
    )
    [IO.File]::WriteAllLines($configPath, $settings, (New-Object Text.UTF8Encoding $false))
    Write-Host 'Configuracao privada criada em .env.docker. Credenciais nao sao exibidas nos logs.'
}
if ($PrepareOnly) { return }
Push-Location $projectRoot
try {
    docker compose --env-file $configPath config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Configuracao Docker invalida.' }
    docker compose --env-file $configPath up -d --build --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Docker nao concluiu a inicializacao. Consulte docker compose --env-file .env.docker logs.' }
    $portSetting = Get-Content -LiteralPath $configPath | Where-Object { $_ -match '^TRIAGEM_HTTP_PORT=' } | Select-Object -First 1
    $httpPort = if ($portSetting) { $portSetting.Split('=',2)[1] } else { '8080' }
    Write-Host "Aplicacao iniciada. Abra http://localhost:$httpPort/whatsapp.html."
    Write-Host 'Entre com DOCKER_ADMIN_LOGIN e DOCKER_ADMIN_PASSWORD de .env.docker e conecte o celular pelo QR Code.'
} finally { Pop-Location }
