#!/usr/bin/env bash
# ============================================================
# FuturoTech AI — Carga de minutas modelo y corpus RAG (Linux / macOS)
# ------------------------------------------------------------
# Para cada plantilla de la firma sube:
#   1) el .docx modelo (POST /templates/{id}/format-doc) -> preserva el membrete,
#      el pie de dos oficinas, la fuente Cambria 14 y el bloque de firma.
#   2) la(s) minuta(s) como corpus RAG (POST /templates/{id}/documents) -> el
#      texto juridico queda disponible para la generacion en modo estricto.
#
# Requisitos: stack arriba (./start.sh) con backend y Ollama listos; usuario con
# permiso manage_templates (admin/abogado); curl y jq instalados.
#
# Uso:
#   ./seed-rag.sh "/ruta/a/MINUTAS DENNIS" [BASE_URL] [USERNAME] [PASSWORD]
#
# Nota: solo se suben archivos .docx. Los .doc (binario antiguo) deben
# convertirse antes a .docx (Apache POI no los procesa con fidelidad).
# ============================================================
set -euo pipefail

MINUTAS_DIR="${1:?Debe indicar el directorio de minutas}"
BASE_URL="${2:-http://localhost:8080}"
USERNAME="${3:-admin}"
PASSWORD="${4:-Admin12345*}"

[ -d "$MINUTAS_DIR" ] || { echo "No existe el directorio: $MINUTAS_DIR"; exit 1; }

# --- Mapeo plantilla -> minutas (la primera es, ademas, el .docx modelo) ---
declare -A MAP
MAP["PETICION-DOCUMENTOS"]=$'DERECHO DE PETICION EMPLEADOS PUBLICOS.docx\nDERECHO DE PETICION DOCUMENTOS ANGELA MARIA DE LAS MERCEDES PEÑA HERNANDEZ.docx\nDERECHO DE PETICION INVESTIGACION ADMINSTRATIVA PENSION DE SOBREVIVIENTES.docx\nDERECHO DE PETICION ALLEGO CETIL A COLPENSIONES.docx'
MAP["PETICION-CALCULO-ACTUARIAL"]=$'DERECHO DE PETICION COLPENSIONES NOVEDADES SOY ACTUARIO.docx\nDERECHO DE PETICION ACTUALIZACION CALCULOS ACTUARIALES.docx\nDERECHO DE PETICION ACTUALIZACION DE DATOS CALCULO ACTUARIAL.docx\nDERECHO DE PETICION NO VINCULADOS RAI CALCULO ACTUARIAL.docx\nDERECHO DE PETICION- APLICACIÓN DE CICLOS PERIODOS POSTERIORES.docx'
MAP["RECLAMACION-REAJUSTE-PENSIONAL"]=$'RECLAMACION  REAJUSTE PENSIONAL.docx\nRECLAMACION MUJERES  PENSIONADAS EN 2026 REAJUSTE PENSIONAL.docx\nRECLAMACION REAJUSTE PENSION ANTICIPADA DE VEJEZ POR DEFICIENCIA.docx\nRECLAMACION RELIQUIDACION PRIMA DE VIDA CARA UGPP.docx\nCONTENCIOSO AD.RECLAMACION MUJERES  PENSIONADAS EN 2026 REAJUSTE PENSIONAL.docx'
MAP["RECLAMACION-RETROACTIVO"]=$'RECLAMACION RETROACTIVO PENSIONAL.docx\nRECLAMACION  RETROACTIVO.docx\nRECLAMACION  RETROACTIVO DOCENTES.docx'
MAP["PETICION-CULPA-PATRONAL"]=$'D.P ACCIDENTE LABORAL CULPA PATRONAL 2026.docx\nD.P DE ENFERMEDAD LABORAL 2026.docx\nDERECHO DE PETICION CULPA PATRONAL INDEMNIZACION POR ACCIDENTE.docx'
MAP["RECLAMACION-INEFICACIA-TRASLADO"]=$'RECLAMACION PARA INEFICACIA  COLPENSIONES ANGELA MARIA DE LAS MERCEDES PEÑA HERNANDEZ.docx'
MAP["RECLAMACION-CONTRATO-REALIDAD"]=$'RECLAMACION CONTRATO REALIDAD NELSON RICARDO VALENCIA.docx'

# --- 1. Login -> JWT ---
echo "Autenticando como '$USERNAME' en $BASE_URL..."
TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" | jq -r '.accessToken')
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "Login fallido o sin accessToken."; exit 1; }
echo "Autenticado."

# --- 2. Por cada plantilla: subir modelo + corpus RAG ---
total=0
for templateId in "${!MAP[@]}"; do
    echo ""
    echo "== Plantilla: $templateId =="
    first=1
    while IFS= read -r fileName; do
        [ -n "$fileName" ] || continue
        path="$MINUTAS_DIR/$fileName"
        if [ ! -f "$path" ]; then
            echo "  [SALTA] no encontrada: $fileName"
            continue
        fi
        if [ "$first" -eq 1 ]; then
            if curl -fsS -X POST "$BASE_URL/api/v1/templates/$templateId/format-doc" \
                -H "Authorization: Bearer $TOKEN" -F "file=@$path" >/dev/null; then
                echo "  [modelo] $fileName"
            else
                echo "  [ERROR modelo] $fileName"
            fi
            first=0
        fi
        chunks=$(curl -fsS -X POST "$BASE_URL/api/v1/templates/$templateId/documents" \
            -H "Authorization: Bearer $TOKEN" -F "file=@$path" | jq -r '.chunks // 0')
        echo "  [RAG] $fileName -> $chunks chunks"
        total=$((total + chunks))
    done <<< "${MAP[$templateId]}"
done

echo ""
echo "Listo. Total de chunks RAG indexados: $total"
