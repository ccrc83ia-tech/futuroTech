# Operación — FuturoTech AI

Guía breve para operar el sistema en un despacho sin equipo de IT.

---

## 1. Arranque

Requisito: Docker Desktop (o Docker Engine + Compose).

```powershell
# Windows
.\start.ps1
```
```bash
# Linux / macOS
./start.sh
```

El script crea `.env` (si no existe) con un `JWT_SECRET` aleatorio y levanta todo:
PostgreSQL+pgvector, Ollama (descarga los modelos la primera vez), backend y frontend.

- Frontend: http://localhost
- Backend / health: http://localhost:8080/health

La primera vez Ollama descarga los modelos (`qwen2.5:7b`, `nomic-embed-text`); puede
tardar varios minutos. El corpus legal base del RAG se siembra solo en el primer
arranque (requiere que Ollama ya responda).

---

## 2. Credenciales

**Desarrollo:** el seed crea `admin`, `abogado`, `pasante`, `consulta` con contraseña
`Admin12345*`. Cambiar tras el primer login.

**Producción:** eliminar los INSERT de usuarios del seed (`V2__seed_data.sql`). Con la
tabla `users` vacía, el primer arranque genera contraseñas aleatorias y las imprime
**una sola vez** en el log del backend:

```powershell
docker compose logs backend | Select-String "Credenciales" -Context 0,6
```

---

## 3. Backups

Respaldo manual (dump comprimido en `./backups/`, con retención):

```powershell
.\backup.ps1            # conserva los 14 más recientes
.\backup.ps1 -Keep 30   # conserva los 30 más recientes
```
```bash
./backup.sh 30
```

Restauración (sobrescribe los datos actuales, pide confirmación):

```powershell
.\restore.ps1 -File backups\legal_engine-20260101-030000.dump
```
```bash
./restore.sh backups/legal_engine-20260101-030000.dump
```

### Backup automático (diario)

**Windows — Programador de tareas:**
```powershell
$action  = New-ScheduledTaskAction -Execute "powershell.exe" `
  -Argument "-NoProfile -File C:\proyecto\futuroTech\backup.ps1" -WorkingDirectory "C:\proyecto\futuroTech"
$trigger = New-ScheduledTaskTrigger -Daily -At 3am
Register-ScheduledTask -TaskName "FuturoTech-Backup" -Action $action -Trigger $trigger
```

**Linux — cron** (`crontab -e`):
```
0 3 * * * cd /opt/futuroTech && ./backup.sh >> backups/backup.log 2>&1
```

Recomendación: copiar periódicamente la carpeta `backups/` a un destino externo
(otro disco o almacenamiento fuera del servidor) y probar una restauración de vez en cuando.

---

## 4. Habeas data (Ley 1581 de 2012) — datos sensibles en logs

Política verificada en el código: **los logs no registran PII del cliente**.
- La generación loguea solo `transactionId`, `tenantId`, `templateId` y longitudes
  de texto — nunca los campos del caso (`caseFields`: nombre, cédula, hechos).
- La contraseña inicial se imprime en consola una vez, nunca al logger.
- RAG/Ollama loguean conteos y tamaños, no contenido.

Al agregar nuevo logging, **no** incluir nombres, cédulas, diagnósticos ni hechos del
caso. Si se necesita trazar un documento, usar su `id` / `transactionId`.

---

## 5. Documentos modelo (.docx) por plantilla

Para preservar el membrete y la estructura de la firma, cargar el `.docx` modelo de
cada plantilla (como administrador):

```
POST /api/v1/templates/{templateId}/format-doc   (multipart: file=<modelo.docx>)
```

Mientras no se cargue, la generación produce el texto sin membrete. Verificar que el
`.docx` resultante abra correctamente en Word (tablas, saltos de sección).

---

## 6. Problemas comunes

- **El backend no arranca:** revisar que `db` esté `healthy` (`docker compose ps`) y que
  la migración Flyway no haya fallado (`docker compose logs backend`).
- **Generación falla con "servicio de IA no disponible":** el circuit breaker de Ollama
  está abierto; revisar `http://localhost:8080/health` y los logs de `ollama`.
- **El modo estricto responde `[texto no disponible en fuentes autorizadas]`:** el corpus
  RAG no se sembró (Ollama no respondía al arrancar). Reiniciar el backend una vez que
  Ollama esté arriba.

---

## 7. Checklist de verificación e2e (ejecutar en una máquina con Docker)

Esta migración se desarrolló en un entorno sin Docker/Postgres/Ollama. Antes de dar
el corte por bueno, ejecutar esta lista en un host con Docker. Marca cada punto.

### Infraestructura
- [ ] `docker compose config --quiet` no reporta errores de sintaxis.
- [ ] `.\start.ps1` (o `./start.sh`) levanta los 4 servicios + `ollama-init`.
- [ ] `docker compose ps`: `db` en estado `healthy`; `ollama-init` terminó con éxito (exit 0).
- [ ] `ollama-init` descargó los modelos: `docker compose exec ollama ollama list`
      muestra `qwen2.5:7b` y `nomic-embed-text`.

### Base de datos y migraciones
- [ ] Flyway aplicó V1, V2 y V3 sin error: `docker compose logs backend | Select-String "Flyway|Migrating|Successfully applied"`.
- [ ] Datos semilla presentes: 1 tenant, 3 plantillas, 20 entidades (2 con NIT `POR VERIFICAR`).
- [ ] La columna `template_chunks.embedding` es `vector(768)` y el índice HNSW existe.

### Corpus RAG
- [ ] Log de arranque muestra `Corpus RAG: ... chunks` para las 3 plantillas.
- [ ] `hasDocuments` verdadero: en `template_chunks` hay filas para
      `PETICION-GENERAL-V1`, `PETICION-PENSIONAL-V1`, `ACCION-TUTELA-V1`.
- [ ] Reiniciar el backend NO duplica chunks (idempotencia del seeder).

### Autenticación y RBAC
- [ ] Login `admin` / `Admin12345*` devuelve JWT + refresh.
- [ ] Refresh token renueva el acceso.
- [ ] Un usuario `consulta` NO puede generar ni administrar (403 esperado).
- [ ] Solo `abogado`/`admin` pueden `PUT /documents/{id}/review`.

### Generación + RAG + export
- [ ] `POST /api/v1/documents/generate` produce un documento en estado `DRAFT`.
- [ ] Con corpus cargado y modo estricto, el texto cita normas de las fuentes
      (NO aparece `[texto no disponible en fuentes autorizadas]`).
- [ ] `GET /api/v1/documents/{id}/download` descarga el archivo.
- [ ] Se registró la auditoría en `generated_document` (autor, plantilla, timestamp, hash).

### DOCX byte-a-byte (punto de riesgo)
- [ ] Cargar un `.docx` modelo real de la firma vía `POST /templates/{id}/format-doc`.
- [ ] Generar y abrir el `.docx` en Word: membrete, tablas, saltos de sección y
      campos se conservan intactos.
- [ ] Exportar a PDF conserva el membrete.

### Salud y resiliencia
- [ ] `GET /health` reporta `UP` con detalle de DB, pgvector y Ollama.
- [ ] Detener `ollama` y generar: el circuit breaker abre y el error al usuario es
      claro en español (no stack trace). `/health` refleja el estado degradado.

### Frontend
- [ ] Login, wizard de 3 pasos, "Mis documentos" y páginas admin navegan sin errores.
- [ ] El disclaimer de IA (BORRADOR) es visible en el resultado de la generación.
- [ ] El build de producción servido por Nginx carga en http://localhost.

### Backups
- [ ] `.\backup.ps1` genera un `.dump` en `backups/`.
- [ ] `.\restore.ps1` sobre una BD modificada restaura el estado del dump.

---

## Carga de minutas modelo y corpus RAG (formato y lenguaje de la firma)

Para que los documentos generados salgan idénticos a las minutas de la firma
(membrete, fuente Cambria 14 justificada, pie de dos oficinas y lenguaje jurídico
real), cada plantilla debe tener asociados:

1. Un **.docx modelo** (preserva el formato byte a byte: membrete, estilos, pie).
2. Un **corpus RAG** con las minutas de esa tipología (el texto jurídico queda
   disponible para la generación en modo estricto, de modo que la IA reproduzca
   el lenguaje de la firma en vez de parafrasearlo).

El script `seed-rag` hace ambas cargas por cada plantilla. Requiere el stack
arriba (backend + Ollama listos, porque el RAG genera embeddings con Ollama) y un
usuario con permiso `manage_templates` (rol admin o abogado).

```powershell
# Windows
.\seed-rag.ps1 -MinutasDir "C:\ruta\a\MINUTAS DENNIS" -Username admin -Password "Admin12345*"
```
```bash
# Linux / macOS (requiere curl y jq)
./seed-rag.sh "/ruta/a/MINUTAS DENNIS" http://localhost:8080 admin "Admin12345*"
```

El script sube, por cada una de las 7 plantillas de la firma, su minuta
representativa como `.docx` modelo y todas sus minutas como corpus RAG. Es
idempotente: volver a ejecutarlo reindexa sin duplicar.

> Nota: solo procesa archivos `.docx`. Las minutas en formato `.doc` (binario
> antiguo) deben convertirse antes a `.docx` (Apache POI no las lee con fidelidad).
> Verificación rápida tras la carga: en la UI de Plantillas, cada plantilla debe
> mostrar su documento modelo y sus fuentes RAG; genere un documento de prueba y
> compárelo contra la minuta real (membrete, fuente, pie y estructura).
