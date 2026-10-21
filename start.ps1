# ============================================================
# FuturoTech AI — Arranque de un comando (Windows / PowerShell)
# Levanta todo el stack: PostgreSQL+pgvector, Ollama (+ descarga de
# modelos), backend Spring Boot y frontend React tras Nginx.
# ============================================================
$ErrorActionPreference = "Stop"

if (-not (Test-Path ".env")) {
    Write-Host "No existe .env. Creando desde .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    # Genera un JWT_SECRET aleatorio si está vacío.
    $secret = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
    (Get-Content ".env") -replace '^JWT_SECRET=.*$', "JWT_SECRET=$secret" | Set-Content ".env"
    Write-Host "Se generó un JWT_SECRET aleatorio en .env. Revisa DB_PASSWORD antes de producción." -ForegroundColor Yellow
}

Write-Host "Construyendo y levantando el stack..." -ForegroundColor Cyan
docker compose up -d --build

Write-Host ""
Write-Host "Listo. La primera vez, Ollama descarga los modelos (puede tardar varios minutos)." -ForegroundColor Green
Write-Host "  Frontend:  http://localhost"
Write-Host "  Backend:   http://localhost:8080"
Write-Host "  Health:    http://localhost:8080/health"
Write-Host ""
Write-Host "Las credenciales iniciales aparecen una sola vez en el log del backend:" -ForegroundColor Yellow
Write-Host "  docker compose logs backend | Select-String 'Credenciales'"
