# Convenciones del Proyecto — FuturoTech AI (Java + React)

Motor Jurídico Universal. Migración desde un sistema Python (FastAPI + Streamlit) a Java + React. Este proyecto NO tiene relación con Ficohsa/NOVA — no aplicar steering, librerías ni convenciones corporativas de Ficohsa aquí.

## Stack (fijo)

- **Backend:** Java 21 + Spring Boot 4.0 (Spring Framework 7, Jakarta EE 11, Jackson 3, JUnit 6)
- **Build:** Gradle 9 con Kotlin DSL (`build.gradle.kts`)
- **Persistencia:** Spring Data JPA + Hibernate ORM 7 sobre PostgreSQL 16
- **Vector store:** pgvector (extensión de PostgreSQL) — NO ChromaDB
- **Migraciones:** Flyway — NO Alembic, NO auto-ALTER
- **IA:** **Ollama local** vía `WebClient` (REST). Generación `qwen2.5:7b`, embeddings `nomic-embed-text` (768 dims → columna `vector(768)`). Costo cero, datos on-premise. El puerto `AIServicePort` deja a Gemini u otro proveedor intercambiables sin tocar el dominio.
- **Resiliencia:** Resilience4j (circuit breaker + retry) — instancia `ollama`.
- **DOCX:** Apache POI · **PDF:** OpenPDF (`com.github.librepdf:openpdf`, paquete `com.lowagie`).
- **Auth:** Spring Security + BCrypt + JWT (jjwt 0.12.6) — stateless, NO sesión en memoria.
- **Frontend:** React 18 + Vite + TypeScript + Material UI + TanStack Query + Zustand.
- **Tests:** JUnit 6 + Mockito + AssertJ + Testcontainers.

Todas las tecnologías deben ser open source / gratuitas para uso comercial. No introducir dependencias con licencia de pago.

## Decisiones de alcance (MVP)

- **Single-tenant** operativo, pero el modelo de datos es multi-tenant-ready (tabla `tenant` con una fila). No construir gestión de firmas.
- **Jurisdicción:** Colombia / español.
- **Trazabilidad obligatoria:** toda generación se registra en `generated_document` (append-only, auditoría).
- **Revisión humana obligatoria:** todo documento nace `DRAFT`; solo rol abogado/admin aprueba; disclaimer visible de IA.
- **Integración futura:** puerto `ClientDataProviderPort` (adaptador manual por defecto) para traer datos del cliente del sistema de la firma más adelante.
- **Entrega:** MVP operando vía `docker compose` (postgres+pgvector, ollama, backend, frontend).

## Notas de implementación (aprendizajes Spring Boot 4)

- Gradle NO está global; usar el **wrapper** (`.\gradlew.bat`). El wrapper ya está generado en `backend/`.
- En Spring Boot 4 el API de health se movió a `org.springframework.boot.health.contributor` (`Health`, `HealthIndicator`) — NO `org.springframework.boot.actuate.health`.
- Testcontainers requiere su BOM (`testcontainers-bom`) en `dependencyManagement`; sus módulos no traen versión del BOM de Spring Boot.
- Al ejecutar `gradlew build` en PowerShell, el exit code puede ser 1 por warnings en stderr (deprecation / HotSpot) aunque el build sea exitoso. Verificar el resultado real en `build/test-results/test/*.xml`, no solo el exit code.
- Los tests de integración con Testcontainers usan `@EnabledIf("dockerAvailable")` y se omiten si no hay Docker; corren en CI o máquina con Docker.
- **MUI 6.5**: el `Grid` importado de `@mui/material` es el legacy (sin prop `size`). Para el API nuevo con `size={{ xs: ... }}` usar `Grid2 as Grid` desde `@mui/material`.
- **Arranque del backend**: necesita Postgres+pgvector en `localhost:5432` (Flyway migra al arrancar y JPA valida con `ddl-auto: validate`) y Ollama en `11434`. Sin ellos NO arranca. El entorno de desarrollo actual solo tiene Java 21 + Node/npm (sin Docker, Postgres ni Ollama).
- **Seed Flyway `V2__seed_data.sql`**: tenant, 3 plantillas, 20 entidades y 4 usuarios de desarrollo con contraseña conocida. `branding` (JSONB) va en **camelCase** (el record `Branding` se serializa sin estrategia snake_case). `templates.fields` se siembra como array de keys string (el dominio `Template.fields` es `List<String>`).

## Arquitectura

- Arquitectura hexagonal (puertos y adaptadores): dominio sin dependencias de framework, aplicación con casos de uso, infraestructura con adaptadores.
- Spring MVC bloqueante (NO WebFlux reactivo) — el sistema es I/O-bound esperando al LLM; no se justifica la complejidad reactiva.
- DTOs de transporte separados de los modelos de dominio.

## Reglas de migración

- El sistema Python en `c:\proyecto\derecho_peticion` es la **especificación ejecutable de referencia**. Replicar el comportamiento del dominio (composición de prompt, modo RAG estricto, preservación byte-a-byte del DOCX, reglas RBAC) con fidelidad.
- Migrar solo la lógica que aporta valor. Descartar andamiaje: SQLite, migración desde JSON, sesión HMAC.
- Mejorar donde se pueda: API REST completa y tipada, validación formal, seguridad.

## Frontend

- Pensado para abogados sin perfil técnico: flujo guiado (wizard), lenguaje del dominio (no jerga técnica como "metadata"/"template_id"), previsualización en vivo, mínima fricción.
- Accesible y responsive.

## Idioma

- UI, mensajes de error y documentación de cara al usuario: español.
- Código, nombres de variables y comentarios técnicos: inglés.

## Verificación

- Cada cambio de backend debe compilar (`gradle build`) antes de darse por terminado.
- Escribir tests para lógica de negocio nueva (JUnit + Mockito; Testcontainers para repositorios).
- Frontend: verificar con `npm run build` (tsc + vite) antes de dar por terminada una pantalla.

## Estado de avance (ver detalle en `docs/PLAN_MIGRACION.md`)

- Fases 0-4 ✅ completas y commiteadas (commit `a70fff3`, rama `feature/migracion`).
- Fase 5 (frontend React) ✅ completa y commiteada: `npm run build` verde (fix MUI Grid2). Login, wizard, mis documentos y páginas admin.
- Fase 6 (infra + semilla) ✅ completa en código y commiteada: seed Flyway `V2`, servicio `ollama-init` en compose, scripts `start.ps1`/`start.sh`.
- **Credenciales de desarrollo** (sembradas en `V2`): usuarios `admin` / `abogado` / `pasante` / `consulta`, todos con contraseña `Admin12345*`. En producción eliminar esos INSERT o cambiar la contraseña tras el primer login.
- ⏳ **Pendiente — verificación e2e (requiere Docker/Postgres/Ollama, no disponibles en el entorno actual):** `docker compose up`, ejecución real de Flyway, descarga de modelos, login y generación RAG + descarga DOCX/PDF. Falta además: corpus legal base del RAG y .docx modelo por plantilla (ambos quedan sin sembrar).
