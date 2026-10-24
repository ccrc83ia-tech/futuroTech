---
name: run-and-verify-stack
description: Levanta y verifica de punta a punta el stack de FuturoTech (PostgreSQL+pgvector, backend Spring Boot, frontend) con Docker Compose. El stack ya NO usa Ollama; la IA (generación, verificación y embeddings) son APIs remotas (Gemini/Groq/OpenRouter). Úsala cuando pidan ejecutar, arrancar, probar o verificar el proyecto localmente, o al diagnosticar fallos de arranque (Flyway, puertos, keys de IA).
---

# Ejecutar y verificar el stack de FuturoTech

Procedimiento probado para arrancar el proyecto con `docker compose` y confirmar que funciona de verdad (no solo que los contenedores están "up").

## Requisitos previos

- Docker Desktop corriendo. Verificar: `docker info --format '{{.ServerVersion}}'`.
- Archivo `.env` en la raíz (lo crea `start.ps1` desde `.env.example` si falta). Claves de IA: `GEMINI_API_KEY` (generación + embeddings, obligatoria), `GROQ_API_KEY` y `OPENROUTER_API_KEY` (verificadores, opcionales — degradan a "omitido" si faltan). El `.env` está en `.gitignore`: nunca commitearlo.

## Arranque

Desde la raíz del repo (desarrollo local, incluye `db`):

```powershell
docker compose up -d --build
```

Primera vez: construye backend (Gradle dentro del contenedor) y frontend (Vite) y descarga la imagen de Postgres+pgvector. **Ya no hay servicios Ollama**; los embeddings y la generación salen por API remota. Para producción en AWS se usa `docker-compose.prod.yml` (sin `db` — RDS — y sin Ollama).

## Gotchas conocidos (y su causa)

1. **`./gradlew: not found` al construir el backend** → el `gradlew` tiene finales CRLF (viene de Windows). El Dockerfile ya lo normaliza con `sed -i 's/\r$//' gradlew`. Si reaparece, revisar esa línea.
2. **Puerto 5432 ya en uso** → otro Postgres local ocupa el puerto. El `db` se publica en **5433** en el host (`DB_HOST_PORT`); dentro de Docker sigue siendo 5432. No cambiar el puerto interno.
3. **Backend en bucle de reinicio con `Schema validation: missing table [...]`** → Flyway no corrió. Causa: en Spring Boot 4 hace falta `spring-boot-starter-flyway` (no `flyway-core` solo). Confirmar en `build.gradle.kts`.
4. **`expected single matching bean but found 3` / error de inyección de `AIServicePort`** → hay 3 beans por rol (`aiGenerator`, `aiVerifierCitations`, `aiVerifierData`); todo consumidor debe inyectar con `@Qualifier`. El generador es `@Primary`.
5. **Generación falla con "falta la API key"** → `GEMINI_API_KEY` vacía. Los verificadores sin key degradan a "omitido" sin romper; el generador sí la exige.
6. **Conflicto de nombre de contenedor al recrear** → `docker compose stop backend; docker compose rm -f backend` y volver a levantar.

## Verificación end-to-end (obligatoria)

1. **Contenedores sanos:**
   ```powershell
   docker compose ps --format "table {{.Name}}\t{{.State}}\t{{.Status}}"
   ```
   `db` debe estar `healthy`; `backend` y `frontend` `running` sin reiniciar.

2. **Backend arrancó (Flyway + Spring):**
   ```powershell
   docker compose logs backend | Select-String -Pattern "Started FuturoTech|Flyway|APPLICATION FAILED|ERROR" -CaseSensitive:$false
   ```
   Buscar `Started FuturoTechLegalApplication` y que Flyway haya validado/aplicado migraciones.

3. **Health UP (ai + db):**
   ```powershell
   (Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing).Content
   ```
   Esperar `"status":"UP"`, componente `ai` con `circuit: CLOSED` (refleja el generador) y `db` UP.

4. **IA responde (prueba real de generación):** generar un documento desde el wizard o `POST /api/v1/documents/generate`. El log del backend debe mostrar `IA completó` y las líneas `Verificación de citas/datos OK` (o hallazgos). Si una key de verificador falta, esa verificación sale "omitida" — es esperado, no un fallo.

5. **Accesos:** Frontend `http://localhost`, Backend `http://localhost:8080`, Health `http://localhost:8080/actuator/health`.

## Credenciales de desarrollo

Usuarios sembrados en Flyway `V2`: `admin` / `abogado` / `pasante` / `consulta`, contraseña `Admin12345*`. Para probar generación y descarga usar un rol con `PERM_generate_document` (admin, abogado o pasante).

## Detener

```powershell
docker compose down          # conserva los volúmenes (db_data, generated_docs)
docker compose down -v       # BORRA los volúmenes (reset total de datos)
```
