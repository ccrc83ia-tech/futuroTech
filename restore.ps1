# ============================================================
# FuturoTech AI — Restauración de PostgreSQL desde un dump
# ADVERTENCIA: sobrescribe los datos actuales de la base.
# Requiere el stack levantado (docker compose up).
# Uso:  .\restore.ps1 -File backups\legal_engine-YYYYMMDD-HHmmss.dump
# ============================================================
param([Parameter(Mandatory = $true)][string]$File)
$ErrorActionPreference = "Stop"

if (-not (Test-Path $File)) { throw "No existe el archivo de backup: $File" }

$dbName = "legal_engine"; $dbUser = "legal"
if (Test-Path ".env") {
    foreach ($line in Get-Content ".env") {
        if ($line -match '^\s*DB_NAME\s*=\s*(.+)$') { $dbName = $Matches[1].Trim() }
        if ($line -match '^\s*DB_USER\s*=\s*(.+)$') { $dbUser = $Matches[1].Trim() }
    }
}

Write-Host "ADVERTENCIA: se sobrescribirán los datos de '$dbName'." -ForegroundColor Yellow
$confirm = Read-Host "Escribe 'si' para continuar"
if ($confirm -ne "si") { Write-Host "Cancelado." -ForegroundColor DarkGray; exit 0 }

Write-Host "Restaurando '$dbName' desde $File..." -ForegroundColor Cyan
# --clean --if-exists limpia objetos previos; -Fc coincide con el dump custom.
Get-Content -Path $File -Encoding Byte | docker compose exec -T db pg_restore -U $dbUser -d $dbName --clean --if-exists --no-owner
if ($LASTEXITCODE -ne 0) { Write-Host "pg_restore terminó con avisos (revisar arriba)." -ForegroundColor Yellow }
else { Write-Host "Restauración completada." -ForegroundColor Green }
