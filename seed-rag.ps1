# ============================================================
# FuturoTech AI — Carga de minutas modelo y corpus RAG (Windows / PowerShell)
# ------------------------------------------------------------
# Para cada plantilla de la firma sube:
#   1) el .docx modelo (POST /templates/{id}/format-doc) -> preserva el membrete,
#      el pie de dos oficinas, la fuente Cambria 14 y el bloque de firma.
#   2) la(s) minuta(s) como corpus RAG (POST /templates/{id}/documents) -> el
#      texto juridico (boilerplate) queda disponible para generacion en modo
#      estricto, de modo que la IA reproduzca el lenguaje real de la firma.
#
# Requisitos:
#   - El stack debe estar arriba (.\start.ps1) con backend y Ollama listos
#     (el corpus RAG necesita Ollama para generar los embeddings).
#   - Un usuario con permiso manage_templates (rol admin o abogado).
#
# Uso:
#   .\seed-rag.ps1 -MinutasDir "C:\ruta\a\MINUTAS DENNIS" `
#                  -BaseUrl "http://localhost:8080" `
#                  -Username admin -Password "Admin12345*"
#
# Nota: solo se suben archivos .docx. Los .doc (formato binario antiguo) deben
# convertirse antes a .docx (Apache POI no los procesa con fidelidad).
# ============================================================
param(
    [Parameter(Mandatory = $true)] [string]$MinutasDir,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "Admin12345*"
)

$ErrorActionPreference = "Stop"

# --- Mapeo minuta (archivo .docx) -> plantilla (template_id) ---
# Cada plantilla recibe la(s) minuta(s) representativa(s) de su tipologia.
# La PRIMERA minuta de cada plantilla se usa tambien como .docx modelo (formato).
$map = @{
    "PETICION-DOCUMENTOS" = @(
        "DERECHO DE PETICION EMPLEADOS PUBLICOS.docx",
        "DERECHO DE PETICION DOCUMENTOS ANGELA MARIA DE LAS MERCEDES PEÑA HERNANDEZ.docx",
        "DERECHO DE PETICION INVESTIGACION ADMINSTRATIVA PENSION DE SOBREVIVIENTES.docx",
        "DERECHO DE PETICION ALLEGO CETIL A COLPENSIONES.docx"
    )
    "PETICION-CALCULO-ACTUARIAL" = @(
        "DERECHO DE PETICION COLPENSIONES NOVEDADES SOY ACTUARIO.docx",
        "DERECHO DE PETICION ACTUALIZACION CALCULOS ACTUARIALES.docx",
        "DERECHO DE PETICION ACTUALIZACION DE DATOS CALCULO ACTUARIAL.docx",
        "DERECHO DE PETICION NO VINCULADOS RAI CALCULO ACTUARIAL.docx",
        "DERECHO DE PETICION- APLICACIÓN DE CICLOS PERIODOS POSTERIORES.docx"
    )
    "RECLAMACION-REAJUSTE-PENSIONAL" = @(
        "RECLAMACION  REAJUSTE PENSIONAL.docx",
        "RECLAMACION MUJERES  PENSIONADAS EN 2026 REAJUSTE PENSIONAL.docx",
        "RECLAMACION REAJUSTE PENSION ANTICIPADA DE VEJEZ POR DEFICIENCIA.docx",
        "RECLAMACION RELIQUIDACION PRIMA DE VIDA CARA UGPP.docx",
        "CONTENCIOSO AD.RECLAMACION MUJERES  PENSIONADAS EN 2026 REAJUSTE PENSIONAL.docx"
    )
    "RECLAMACION-RETROACTIVO" = @(
        "RECLAMACION RETROACTIVO PENSIONAL.docx",
        "RECLAMACION  RETROACTIVO.docx",
        "RECLAMACION  RETROACTIVO DOCENTES.docx"
    )
    "PETICION-CULPA-PATRONAL" = @(
        "D.P ACCIDENTE LABORAL CULPA PATRONAL 2026.docx",
        "D.P DE ENFERMEDAD LABORAL 2026.docx",
        "DERECHO DE PETICION CULPA PATRONAL INDEMNIZACION POR ACCIDENTE.docx"
    )
    "RECLAMACION-INEFICACIA-TRASLADO" = @(
        "RECLAMACION PARA INEFICACIA  COLPENSIONES ANGELA MARIA DE LAS MERCEDES PEÑA HERNANDEZ.docx"
    )
    "RECLAMACION-CONTRATO-REALIDAD" = @(
        "RECLAMACION CONTRATO REALIDAD NELSON RICARDO VALENCIA.docx"
    )
}

if (-not (Test-Path $MinutasDir)) {
    throw "No existe el directorio de minutas: $MinutasDir"
}

# --- 1. Login -> JWT ---
Write-Host "Autenticando como '$Username' en $BaseUrl..." -ForegroundColor Cyan
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
try {
    $auth = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -ContentType "application/json" -Body $loginBody
} catch {
    throw "Fallo el login. Verifica que el stack este arriba y las credenciales. $_"
}
$token = $auth.accessToken
if (-not $token) { throw "El login no devolvio accessToken." }
$headers = @{ Authorization = "Bearer $token" }
Write-Host "Autenticado." -ForegroundColor Green

# --- 2. Por cada plantilla: subir .docx modelo + corpus RAG ---
$totalChunks = 0
foreach ($templateId in $map.Keys) {
    Write-Host ""
    Write-Host "== Plantilla: $templateId ==" -ForegroundColor Cyan
    $files = $map[$templateId]
    $first = $true
    foreach ($fileName in $files) {
        $path = Join-Path $MinutasDir $fileName
        if (-not (Test-Path $path)) {
            Write-Host "  [SALTA] no encontrada: $fileName" -ForegroundColor Yellow
            continue
        }

        # 2a. La primera minuta define el documento modelo (formato/membrete).
        if ($first) {
            try {
                $form = @{ file = Get-Item $path }
                Invoke-RestMethod -Uri "$BaseUrl/api/v1/templates/$templateId/format-doc" `
                    -Method Post -Headers $headers -Form $form | Out-Null
                Write-Host "  [modelo] $fileName" -ForegroundColor Green
            } catch {
                Write-Host "  [ERROR modelo] $fileName -> $_" -ForegroundColor Red
            }
            $first = $false
        }

        # 2b. Indexar la minuta como corpus RAG.
        try {
            $form = @{ file = Get-Item $path }
            $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/templates/$templateId/documents" `
                -Method Post -Headers $headers -Form $form
            Write-Host "  [RAG] $fileName -> $($resp.chunks) chunks" -ForegroundColor Green
            $totalChunks += [int]$resp.chunks
        } catch {
            Write-Host "  [ERROR RAG] $fileName -> $_" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "Listo. Total de chunks RAG indexados: $totalChunks" -ForegroundColor Green
Write-Host "Las plantillas quedaron con su .docx modelo (formato) y su corpus de minutas."
