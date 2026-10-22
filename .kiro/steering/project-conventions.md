# Convenciones del Proyecto — FuturoTech AI (Java + React)

Motor Jurídico Universal. Migración desde un sistema Python (FastAPI + Streamlit) a Java + React.
**Este proyecto NO tiene relación con Ficohsa/NOVA** — no aplicar steering, librerías ni convenciones corporativas de Ficohsa aquí.

---

## Stack (fijo — no cambiar sin decisión explícita)

| Capa | Tecnología |
|---|---|
| Backend | Java 25 (LTS) + Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11, Jackson 3, JUnit 6) |
| Build | Gradle 9.8.1 Kotlin DSL (`build.gradle.kts`). Toolchain Java 25. Wrapper ≥ 9.1. |
| Persistencia | Spring Data JPA + Hibernate ORM 7 sobre PostgreSQL 16 |
| Vector store | pgvector (extensión de PostgreSQL) — **NO** ChromaDB |
| Migraciones | Flyway con `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| IA generación | Groq por defecto (`GroqClient`, OpenAI-compatible). Modelo `openai/gpt-oss-120b`. Ollama on-premise conmutable con `ai.provider=ollama`. **Exactamente UN bean `AIServicePort` activo.** |
| IA embeddings | Ollama local (`OllamaEmbeddingClient`), `nomic-embed-text`, 768 dims → `vector(768)` |
| Resiliencia | Resilience4j (circuit breaker + retry) — instancias `groq` y `ollama` |
| DOCX export | Apache POI + `DocxWatermarkStamper` (VML header con marca de agua) |
| PDF export | OpenPDF (`com.github.librepdf:openpdf`, paquete `com.lowagie`) + `PdfExporter` con `WatermarkEvent` |
| PDF extracción | Apache PDFBox 3 (extracción texto PDF en RAG y adjuntos) |
| Auth | Spring Security + BCrypt + JWT (jjwt 0.12.6) — stateless, NO sesión |
| Frontend | React 18 + Vite + TypeScript + Material UI 6 + TanStack Query + Zustand |
| Tests | JUnit 6 + Mockito + AssertJ + Testcontainers |

Todas las tecnologías deben ser **open source / gratuitas para uso comercial**.

---

## Decisiones de alcance (MVP)

- **Single-tenant** operativo; modelo de datos multi-tenant-ready (tabla `tenant` con una fila).
- **Jurisdicción:** Colombia / español.
- **Trazabilidad obligatoria:** toda generación en `generated_document` (append-only).
- **Revisión humana obligatoria:** todo documento nace `DRAFT`; solo abogado/admin aprueba.
- **Marca de agua:** documentos DRAFT incluyen "BORRADOR" en DOCX (header VML) y PDF (capa diagonal), aplicada al vuelo en la descarga sin tocar el archivo en disco.
- **Plazos jurídicos:** `deadline` calculado al generar según el tipo de plantilla (petición = 15 días hábiles, tutela = 10 días hábiles). Se muestra en el Dashboard y en la tabla de documentos.
- **Radicado interno:** `internalDocket` registrable en cualquier estado (el abogado puede necesitarlo antes de aprobar el borrador, al presentarlo físicamente).
- **Integración futura:** `ClientDataProviderPort` (gancho para traer datos del cliente del sistema de la firma; sin implementación en el MVP).

---

## Arquitectura hexagonal

```
domain/          → sin dependencias de framework (records, interfaces de puertos)
application/     → @Service (orquestación, casos de uso)
infrastructure/  → adaptadores concretos (Spring, JPA, exporters, IA, RAG)
```

Reglas: los modelos de dominio son independientes de las `@Entity` JPA (mapeo explícito en adaptadores); puertos como interfaces en el dominio, implementados en infraestructura; DTOs HTTP separados del dominio.

---

## API REST completa (todos los endpoints implementados)

### Auth — `/api/v1/auth`
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/login` | público | Login → JWT + refresh |
| POST | `/refresh` | público | Renueva access token |

### Usuarios — `/api/v1/users` (`PERM_manage_users`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/` | Lista todos los usuarios |
| GET | `/{userId}` | Obtiene un usuario |
| POST | `/` | Crea usuario |
| PUT | `/{userId}` | Actualiza usuario (incluye rol y campo `active`) |
| DELETE | `/{userId}` | Elimina usuario |

### Plantillas — `/api/v1/templates`
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/` | `view_templates` | Lista plantillas |
| GET | `/{templateId}` | `view_templates` | Obtiene plantilla |
| POST | `/` | `manage_templates` | Crea plantilla |
| PUT | `/{templateId}` | `manage_templates` | Actualiza plantilla |
| DELETE | `/{templateId}` | `manage_templates` | Elimina plantilla + chunks RAG |
| POST | `/{id}/format-doc` | `manage_templates` | Sube .docx modelo (preservación byte-a-byte) |
| DELETE | `/{id}/format-doc` | `manage_templates` | Elimina .docx modelo |
| GET | `/{id}/documents` | `view_templates` | Lista fuentes RAG indexadas |
| POST | `/{id}/documents` | `manage_templates` | Indexa doc RAG (PDF/DOCX/TXT) |
| DELETE | `/{id}/documents?docName=` | `manage_templates` | Elimina fuente RAG |

### Entidades — `/api/v1/entities`
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/` | `view_entities` | Lista entidades destinatarias |
| GET | `/{entityId}` | `view_entities` | Obtiene entidad |
| POST | `/` | `manage_entities` | Crea entidad |
| PUT | `/{entityId}` | `manage_entities` | Actualiza entidad |
| DELETE | `/{entityId}` | `manage_entities` | Elimina entidad |

### Empresa — `/api/v1/company`
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/` | `view_company` | Obtiene configuración del tenant |
| PUT | `/` | `manage_company` | Actualiza nombre y branding |

### Documentos — `/api/v1/documents`
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/generate` (multipart) | `generate_document` | Genera documento. Parte `request` (JSON) + `attachments` (PDF/DOCX/TXT opcionales como hechos). |
| GET | `/mine` | `generate_document` | Documentos del usuario autenticado |
| GET | `/all` | `manage_users` | Todos los documentos (admin/abogado) |
| GET | `/stats` | `manage_users` | Estadísticas: totalMonth, pendingReview, upcomingDeadlines, overdue, byTemplate |
| GET | `/{id}/download?format=docx\|pdf` | `generate_document` | Descarga DOCX o PDF. DRAFT incluye marca de agua "BORRADOR". |
| GET | `/{id}/text` | `generate_document` | Texto plano del documento generado |
| PUT | `/{id}/review` | rol ABOGADO o ADMIN | Cambia estado: REVIEWED, APPROVED, VOID |
| PUT | `/{id}/docket` | `generate_document` | Registra número de radicado interno |

### Health
- `GET /actuator/health` — estado de BD, AI (circuit breaker), disco, liveness, readiness.

---

## Modelo de dominio — campos clave

### `GeneratedDocument`
```
id, templateId, templateVersion, authorUserId (username), entityId,
caseFields (Map), status (DRAFT|REVIEWED|APPROVED|VOID),
internalDocket, fileName, fileHash, createdAt, reviewedBy, reviewedAt, deadline
```
- `authorUserId` guarda el **username** (no UUID) en documentos nuevos. En documentos históricos puede contener el UUID — `DocumentController.resolveUserName()` maneja ambos casos.
- `deadline` calculado por `GenerateDocumentService.calculateDeadline(templateId)`: TUTELA=10 días hábiles, resto=15 días hábiles.

### `GeneratedDocResponse` (DTO enriquecido)
Además de los campos base devuelve: `clientName` (de `caseFields["cliente_nombre"]`), `entityName` (resuelto por ID), `authorUserId` (nombre completo resuelto), `reviewedBy` (nombre completo resuelto), `reviewedAt`, `deadline`, `internalDocket`.

---

## Infraestructura de exportación

### `DocxWatermarkStamper`
Opera sobre bytes de un `.docx` (ZIP/OOXML): inyecta `word/wmarkheader1.xml` (WordArt VML diagonal "BORRADOR"), la relación en `document.xml.rels`, el content-type en `[Content_Types].xml`, y `<w:headerReference>` en cada `<w:sectPr>` del `document.xml`. Tolerante a fallos: si algo va mal devuelve el DOCX original sin marca. Solo se aplica si `status == DRAFT`.

### `PdfExporter`
- `buildFromDocx(docxBytes, branding, watermark)` — extrae texto del DOCX vía POI y genera PDF con membrete + marca de agua opcional.
- `WatermarkEvent` (inner class, `PdfPageEventHelper`) — dibuja "BORRADOR" diagonal, gris 18% opacidad, en cada página al cerrar el PDF.

### `CaseDocumentExtractor`
Extrae texto de adjuntos del caso (PDF via PDFBox, DOCX via POI, TXT). El texto se inyecta en el prompt como hechos; **no se indexa en pgvector ni persiste**. Solo el nombre del archivo queda en `case_fields._adjuntos`.

---

## Migraciones Flyway

| Script | Contenido |
|---|---|
| `V1__initial_schema.sql` | Tablas: tenant, users, templates, template_versions, template_documents, template_chunks (vector 768, HNSW coseno), entities, generated_document |
| `V2__seed_data.sql` | Tenant, 3 plantillas, 20 entidades, 4 usuarios de desarrollo |
| `V3__fix_entity_nit.sql` | NIT de supersalud/superfinanciera → "POR VERIFICAR" |
| `V4__add_deadline.sql` | `ALTER TABLE generated_document ADD COLUMN deadline TIMESTAMPTZ` |

---

## Frontend — páginas y componentes

### Páginas (`src/pages/`)
| Ruta | Componente | Permiso | Descripción |
|---|---|---|---|
| `/` | `DashboardPage` | autenticado | Métricas, control de plazos, borradores pendientes |
| `/generar` | `GenerateWizard` | `generate_document` | Wizard 3 pasos: plantilla → datos → generar |
| `/documentos` | `MyDocumentsPage` | `generate_document` | Mis documentos con búsqueda, filtros, radicado, vence |
| `/todos` | `AllDocumentsPage` | `manage_users` | Todos los docs del equipo (admin/abogado) |
| `/plantillas` | `TemplatesPage` + `RagDocumentsDialog` | `view_templates` | Gestión de plantillas y corpus RAG |
| `/entidades` | `EntitiesPage` | `view_entities` | Directorio de entidades destinatarias |
| `/empresa` | `CompanyPage` | `view_company` | Branding con preview de color hex |
| `/usuarios` | `UsersPage` | `manage_users` | CRUD usuarios, chips de rol, campo activo |
| `/login` | `LoginPage` | público | Toggle de contraseña, fondo gradiente |

### Componentes reutilizables (`src/components/`)
| Componente | Uso |
|---|---|
| `AppLayout` | Shell responsive: Drawer permanent (desktop) / temporary+hamburguesa (móvil), Avatar con iniciales |
| `NotificationProvider` + `useNotify()` | Snackbars con cola (1 a la vez). `notify.success/error/info/warning` |
| `LoadingState` / `EmptyState` | Estados consistentes en tablas y listas |
| `DeadlineChip` | Chip de vencimiento con color: verde (>7d), warning (≤7d), error (≤3d o vencido) |
| `ProtectedRoute` | Redirige a `/login` si no autenticado; a `/` si sin permiso |

---

## Notas de implementación (aprendizajes acumulados)

### Gradle / Java
- **`gradle.properties`** en `backend/` con `org.gradle.java.installations.paths=C:/Program Files/Java/jdk-25.0.4.1` (o la ruta del JDK 25 en la máquina) es necesario cuando `JAVA_HOME` no apunta a Java 25. Sin esta configuración, Gradle busca el toolchain y falla con "Cannot find a Java installation matching languageVersion=25".
- El wrapper (`.\gradlew.bat`) es el único punto de entrada para compilar. No usar Gradle global.
- Exit code 1 en `gradlew build` en PowerShell puede ser ruido de stderr (HotSpot warnings), no necesariamente un error. Verificar en `build/test-results/test/*.xml`.

### Docker / Despliegue
- **Modo PREBUILT**: si el contenedor no tiene acceso a internet (DNS falla para Maven Central), el build de la imagen falla. Solución: `.\gradlew.bat bootJar --no-daemon` en local y `docker compose build --build-arg PREBUILT=true backend`. El Dockerfile soporta ambos modos con `ARG PREBUILT=false`.
- **Volumen `generated_docs`**: los `.docx` generados viven dentro del contenedor en `generated_docs/`. Si el contenedor se recrea sin un volumen persistente, los archivos se pierden (los registros en BD siguen pero el `download` da 404). Pendiente: declarar un `volume` en `docker-compose.yml` para este directorio.
- Postgres se publica en puerto **5433** en el host (evita conflicto con instalaciones locales).

### Spring Boot 4
- Flyway requiere `spring-boot-starter-flyway` (no `flyway-core` solo).
- Health API: `org.springframework.boot.health.contributor` (NO `actuate.health`).
- `@WebMvcTest` requiere starter `spring-boot-starter-webmvc-test`. No auto-configura seguridad → añadir `@EnableWebSecurity` a `SecurityConfig` e importar `SecurityConfig + JwtAuthFilter` en el test.
- `@MockBean` deprecado → usar `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).
- Sin `AuthenticationEntryPoint`, requests no autenticados → 403 (no 401).

### Frontend
- `Grid2 as Grid` de `@mui/material` para el API con `size={{ xs, sm, md }}`.
- Descargas autenticadas: usar Axios con `responseType: "blob"` (no `<a href>` directo → 403).
- `DocumentController.resolveUserName(usernameOrId)`: los documentos históricos tienen UUID en `authorUserId`; los nuevos tienen username. El método intenta `findByUsername` primero, luego `findById` para compatibilidad retroactiva.

---

## Logging y manejo de excepciones

- **Logger:** SLF4J `LoggerFactory.getLogger(Clase.class)`, campo `private static final`. NO `System.out`/`printStackTrace`. Excepción: banner de credenciales iniciales en `AuthService` (secretos nunca al logger).
- **traceId:** `CorrelationIdFilter` asigna UUID por petición al MDC, devuelve cabecera `X-Correlation-ID`.
- **Mapa de excepciones** (`GlobalExceptionHandler`): 401 AuthenticationException, 403 AccessDenied, 404 ResourceNotFound/TenantNotFound/NoHandlerFound, 405 MethodNotSupported, 422 InvalidSchema/@Valid, 400 IllegalArgument/HttpMessageNotReadable, 500 DocumentGenerationException/catch-all.
- **Nunca** registrar PII (nombres, cédulas, hechos del caso). Usar `transactionId`/ids.
- `build.gradle.kts` compila con `-Xlint:deprecation`.

---

## Idioma

- UI, mensajes y documentación al usuario: **español**.
- Código, variables, comentarios técnicos: **inglés**.

---

## Verificación (obligatoria antes de dar por terminado un cambio)

- Backend: `.\gradlew.bat compileJava --no-daemon` o `bootJar`. Si hay tests nuevos, `.\gradlew.bat test --no-daemon`.
- Frontend: `npm run build` (tsc + vite) — exit 0 sin errores de tipo.
- Despliegue: `docker compose build --build-arg PREBUILT=true backend && docker compose up -d backend`. Verificar `Started FuturoTechLegalApplication` en logs.

---

## Estado del proyecto (rama `feature/migracion`)

### ✅ Completado y en producción
- **Backend completo**: auth JWT, RBAC, CRUD REST, generación + RAG (pgvector + Groq), exportación DOCX/PDF, auditoría, plazos jurídicos (`deadline`), radicado (`internalDocket`), estadísticas, marca de agua al vuelo.
- **Frontend completo**: Dashboard con métricas y control de plazos, wizard de generación, mis documentos, todos los documentos (admin), plantillas con corpus RAG, entidades, empresa, usuarios con chips de rol y campo activo.
- **Infra**: Docker Compose con 5 servicios, scripts `start.ps1/sh`, backups `backup.ps1/sh` + `restore.ps1/sh`, `OPERACION.md`.
- **Tests**: `GenerateDocumentServiceTest` (prompt + RAG estricto), `TemplateControllerSecurityTest` (MockMvc + @PreAuthorize), `AuthServiceTest`, `JwtServiceTest`, `UserServiceTest`, `SplitTextTest`, `PersistenceIntegrationTest` (Testcontainers).

### ⏳ Pendiente operativo (depende de la firma o del servidor)
- `.docx` modelo por plantilla: cargar vía `POST /templates/{id}/format-doc`.
- Corpus legal propio: ampliar vía UI "Fuentes normativas" en Plantillas.
- Backup automático: agendar `backup.ps1`/`backup.sh` en cron/Task Scheduler.
- Credenciales de producción: rotar o eliminar los 4 usuarios del seed (`V2`).

### ⚠️ Deuda técnica conocida
- `generated_docs/` no tiene volumen Docker persistente → los `.docx` se pierden al recrear el contenedor.
- `ClientDataProviderPort` sin adaptador (gancho a futuro).
- `JwtAuthFilter` usa `@NonNull` de `org.springframework.lang` (deprecado en Spring 7; migrar a JSpecify).
- Bundle del frontend ~680 kB — code-splitting con `React.lazy` mejoraría carga inicial.
- El campo `authorUserId` en documentos históricos contiene UUID en vez de username (`resolveUserName` lo maneja, pero lo ideal sería una migración que normalice el campo).
