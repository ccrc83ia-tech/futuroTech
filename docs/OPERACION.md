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
