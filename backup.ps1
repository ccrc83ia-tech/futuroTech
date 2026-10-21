# ============================================================
# FuturoTech AI — Backup de PostgreSQL (incluye datos pgvector)
# Genera un dump comprimido en ./backups/ con marca de tiempo y
# aplica retención (conserva los N más recientes).
# Requiere el stack levantado (docker compose up).
# Uso:  .\backup.ps1  [-Keep 14]
# ============================================================
param([int]$Keep = 14)
$ErrorActionPreference = "Stop"

# Carga DB_NAME / DB_USER desde .env si existe (si no, usa los defaults del compose).
$dbName = "legal_engine"; $dbUser = "legal"
if (Test-Path ".env") {
    foreach ($line in Get-Content ".env") {
        if ($line -match '^\s*DB_NAME\s*=\s*(.+)$') { $dbName = $Matches[1].Trim() }
        if ($line -match '^\s*DB_USER\s*=\s*(.+)$') { $dbUser = $Matches[1].Trim() }
    }
}

New-Item -ItemType Directory -Force -Path "backups" | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$file  = "backups/${dbName}-${stamp}.dump"

Write-Host "Respaldando base '$dbName' (usuario '$dbUser')..." -ForegroundColor Cyan
# -Fc = formato custom comprimido, restaurable con pg_restore.
docker compose exec -T db pg_dump -U $dbUser -d $dbName -Fc | Set-Content -Path $file -Encoding Byte
if ($LASTEXITCODE -ne 0) { throw "pg_dump falló (¿está levantado el stack?)" }

Write-Host "Backup creado: $file" -ForegroundColor Green

# Retención: conservar solo los $Keep más recientes.
$dumps = Get-ChildItem "backups" -Filter "${dbName}-*.dump" | Sort-Object LastWriteTime -Descending
if ($dumps.Count -gt $Keep) {
    $dumps | Select-Object -Skip $Keep | ForEach-Object {
        Remove-Item $_.FullName -Force
        Write-Host "Backup antiguo eliminado: $($_.Name)" -ForegroundColor DarkGray
    }
}
