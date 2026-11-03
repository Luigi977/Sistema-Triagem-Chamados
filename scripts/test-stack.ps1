$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$settings = @{}
Get-Content -LiteralPath (Join-Path $projectRoot '.env.docker') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $settings[$matches[1]] = $matches[2] }
}
$baseUrl = 'http://127.0.0.1:' + $settings['TRIAGEM_HTTP_PORT']
$health = Invoke-RestMethod -Uri "$baseUrl/health"
if ($health -ne 'OK') { throw 'Backend sem saude.' }
$credentials = @{ login=$settings['DOCKER_ADMIN_LOGIN']; senha=$settings['DOCKER_ADMIN_PASSWORD'] } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType 'application/json' -Body $credentials
$headers = @{ Authorization='Bearer ' + $session.token }
try {
    $connection = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/whatsapp/conectar" -Headers $headers
    $status = Invoke-RestMethod -Uri "$baseUrl/api/whatsapp/status" -Headers $headers
    if ($status.instancia -ne $settings['DOCKER_EVOLUTION_INSTANCE']) { throw 'Instancia inesperada.' }
    if ($status.estado -notin @('open','close','connecting')) { throw 'Estado invalido.' }
    $calls = Invoke-RestMethod -Uri "$baseUrl/api/chamados?size=1" -Headers $headers
    Write-Host "Backend OK. Instancia: $($status.instancia). Estado real: $($status.estado)."
    Write-Host "Chamados no banco: $($calls.totalElements). Nenhum chamado ou mensagem foi criado pelo teste."
    if ($connection.qrCode) { Write-Host 'QR Code real disponivel na tela /whatsapp.html.' }
} finally {
    Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/logout" -Headers $headers | Out-Null
}
