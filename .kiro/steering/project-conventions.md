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
| IA generación/verificación | Pipeline multi-proveedor OpenAI-compatible (`OpenAiCompatibleClient` instanciado por rol en `AiClientsConfig`). 3 beans `AIServicePort` con `@Qualifier`: **`aiGenerator`** (`@Primary`, Gemini Flash), **`aiVerifierCitations`** (Groq/Llama), **`aiVerifierData`** (DeepSeek vía OpenRouter). Cambiar de proveedor = cambiar `*_BASE_URL`/`*_MODEL`/key por env, sin tocar código. **Ollama eliminado.** |
| IA embeddings | Gemini (`GeminiEmbeddingClient implements EmbeddingPort`), `gemini-embedding-001`, `outputDimensionality=768` → `vector(768)` (sin migración de esquema). Caché LRU 256. |
| Anti-alucinación | `CitationVerificationService` (verifica normas citadas vs. chunks) + `DataVerificationService` (verifica cálculos/fechas/montos). Resultados en `caseFields._verification_*`. Reranking léxico + chunking consciente de estructura legal + citas inline `[Fuente N]`. Tolerantes a fallos. |
| Resiliencia | Resilience4j (circuit breaker + retry) — `configs.default` + instancias `ai-generator`, `ai-verifier-citations`, `ai-verifier-data` |
| DOCX export | Apache POI (`DocxExporter`, cuerpo Cambria 14 justificado) + `DocxWatermarkStamper` (VML "BORRADOR") + `DocxSignatureStamper` (firma al aprobar) |
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
- **Formato de las minutas de la firma (TUFUTURO — Abogados en Pensiones):** el cuerpo generado sale en **Cambria 14 pt justificado** (replica las minutas reales). El membrete (logo + pie de dos oficinas Medellín/Pereira) y la estructura se preservan cargando un **`.docx` modelo por plantilla** (`format-doc`, modo `buildFromFormat`). El tenant real es "TUFUTURO — Abogados en Pensiones" (migración V5), aunque la PK del tenant sigue siendo `estufuturo-legal`.
- **Firma manuscrita:** se estampa (vía `DocxSignatureStamper`) sobre la línea "Atentamente," **solo cuando el documento está APPROVED**, al vuelo en la descarga DOCX, excluyente con la marca de agua BORRADOR. Imagen configurable con `SIGNATURE_IMAGE_PATH`. Limitación: el PDF se reconstruye desde texto y no incluye la firma (usar DOCX). Los documentos revisados deben salir con el membrete de la empresa (depende de que la plantilla tenga `.docx` modelo cargado).
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
| GET | `/all` | `view_all_documents` | Todos los documentos del equipo (admin/abogado) |
| GET | `/stats` | `view_all_documents` | Estadísticas: totalMonth, pendingReview, upcomingDeadlines, overdue, byTemplate |
| GET | `/{id}/download?format=docx\|pdf` | `generate_document` | Descarga DOCX o PDF. DRAFT → marca de agua "BORRADOR"; APPROVED → firma estampada (DOCX). |
| GET | `/{id}/text` | `generate_document` | Texto del documento con marcado Markdown básico |
| PUT | `/{id}/content` | `generate_document` | Edita el cuerpo de un borrador (solo DRAFT); regenera el `.docx` y el hash |
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

### `DocxExporter`
Dos modos: `buildFromFormat` (con `.docx` modelo) preserva el paquete OOXML (membrete, pie, estilos, `sectPr`/márgenes) y solo reemplaza el interior de `<w:body>`; `buildBranded` (sin modelo) construye con POI y branding de texto. En **ambos** el cuerpo sale en **Cambria 14 pt justificado, spacing after 0** (constantes `BODY_FONT`/`BODY_FONT_SIZE` + helpers `applyBodyRunStyle`/`applyBodyParagraphStyle`). Esta ruta la comparten la generación y la edición de borrador (`PUT /{id}/content`). Nota POI: `XWPFHeaderFooterPolicy` está en `org.apache.poi.xwpf.model`; `getFontSize()` deprecado → usar `getFontSizeAsDouble()`.

### `DocxSignatureStamper`
Inserta la imagen de firma (config `signature.image-path` / `SIGNATURE_IMAGE_PATH`, ancho/alto en pt) en un párrafo nuevo tras la línea "Atentamente,". Solo se aplica en `download` cuando `status == APPROVED` (excluyente con la marca de agua). Tolerante a fallos: sin imagen configurada, ruta ilegible o sin ancla devuelve el `.docx` intacto.

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
| `V5__update_tenant_branding.sql` | Actualiza el tenant a la identidad real "TUFUTURO — Abogados en Pensiones" (oficinas Medellín/Pereira, correos, WhatsApp, web); branding JSONB camelCase. PK `estufuturo-legal` sin cambios. |
| `V6__templates_tufuturo.sql` | 7 plantillas por tipología real de la firma (ver sección Plantillas), `INSERT ... ON CONFLICT DO NOTHING`; conviven con las 3 genéricas previas. |

---

## Plantillas de la firma y corpus RAG (TUFUTURO)

Tipologías jurídicas reales derivadas del análisis de las minutas (migración V6), todas `rag_strict=true`:
`PETICION-DOCUMENTOS`, `PETICION-CALCULO-ACTUARIAL`, `RECLAMACION-REAJUSTE-PENSIONAL`, `RECLAMACION-RETROACTIVO`, `PETICION-CULPA-PATRONAL`, `RECLAMACION-INEFICACIA-TRASLADO`, `RECLAMACION-CONTRATO-REALIDAD`.

- Cada `system_prompt` calca la anatomía real: `Señores / DESTINATARIO / Ciudad|E.S.D. / ASUNTO|REFERENCIA / comparecencia (apoderado o nombre propio, campo `tipo_firmante`) / cuerpo por tipología / bloque de NOTIFICACIONES literal de la firma / Atentamente / FIRMANTE en mayúsculas / C.C / [T.P solo si apoderado]`. Sin títulos de sección en negrita (las minutas no los llevan).
- `legal_rules` acotado a lo que la firma **sí** cita: art. 23 CN, Ley 1755/2015, arts. 5/15/16 Ley 1437/2011, Ley 100/1993 (art. 141 intereses de mora), Ley 797/2003, art. 216 CST.
- **Datos duros** (nº de resolución, radicados, sentencias, valores, fechas, PCL) provienen solo de los campos del caso o adjuntos — nunca inventados por el modelo.
- Los `fields` son KEYS en snake_case; el frontend deriva la etiqueta con `humanize()` (no hay mapa de labels por plantilla).

### Carga de minutas modelo + corpus (`seed-rag.ps1` / `seed-rag.sh`)
Scripts en la raíz. Por cada plantilla suben su `.docx` modelo (`format-doc`) y sus minutas como corpus RAG (`/documents`). Requieren el stack arriba (`GEMINI_API_KEY` para embeddings) y usuario `manage_templates`. Usan **`curl.exe`** para el multipart (Windows PowerShell 5.1 no tiene `Invoke-RestMethod -Form`). Los `.ps1` con no-ASCII deben guardarse **con BOM UTF-8** (PS 5.1 lee sin BOM como ANSI y rompe tildes/ñ). Solo procesan `.docx`; los `.doc` antiguos deben convertirse antes. Documentado en `docs/OPERACION.md`.

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
| `PageHeader` | Encabezado de página estándar: título (h4) + subtítulo + slot de acción. Usar en todas las páginas en vez de armar el encabezado a mano |
| `RowActions` | Menú "⋮" que agrupa las acciones de fila de tabla (Ver/Editar, Word, PDF, Aprobar…). Evita tiras de botones; patrón único para acciones de fila |
| `DocumentEditor` | Editor Tiptap (hoja A4). Úsalo para **toda** visualización/edición de documentos (`readOnly` para ver). Mismo control en el wizard y en "Mis documentos" |

### Diseño / theme
- **Theme central** en `src/theme.ts` (`createTheme`): paleta (primary índigo + secondary dorado), tipografía jerárquica (h4 títulos de página, h5/h6 secciones, botones sin mayúsculas) y overrides globales (**tablas `size="small"` por defecto**, `TextField` small, botones sin elevación, cards con borde). Preferir tokens del theme a colores hardcodeados.
- `AppLayout` envuelve el contenido en `Container maxWidth="xl"` para un ancho consistente.
- Conversión texto↔HTML para el editor: `markdownToHtml` / `htmlToPlainText` en `src/utils/`.

---

## Notas de implementación (aprendizajes acumulados)

### Gradle / Java
- **`gradle.properties`** en `backend/` con `org.gradle.java.installations.paths=C:/Program Files/Java/jdk-25.0.4.1` (o la ruta del JDK 25 en la máquina) es necesario cuando `JAVA_HOME` no apunta a Java 25. Sin esta configuración, Gradle busca el toolchain y falla con "Cannot find a Java installation matching languageVersion=25".
- El wrapper (`.\gradlew.bat`) es el único punto de entrada para compilar. No usar Gradle global.
- Exit code 1 en `gradlew build` en PowerShell puede ser ruido de stderr (HotSpot warnings), no necesariamente un error. Verificar en `build/test-results/test/*.xml`.

### Docker / Despliegue
- **Modo PREBUILT**: si el contenedor no tiene acceso a internet (DNS falla para Maven Central), el build de la imagen falla. Solución: `.\gradlew.bat bootJar --no-daemon` en local y `docker compose build --build-arg PREBUILT=true backend`. El Dockerfile soporta ambos modos con `ARG PREBUILT=false`.
- **Volumen `generated_docs`**: los `.docx` generados viven en `/app/generated_docs` dentro del backend. Ya hay un **volumen Docker nombrado `generated_docs`** declarado en `docker-compose.yml` para que persistan entre recreaciones del contenedor. Los documentos creados **antes** de añadir el volumen se perdieron (sus registros en BD dan 404 en `download`/`text`); los nuevos persisten.
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
- Visualización de documentos unificada en `DocumentEditor` (Tiptap). Acciones de fila unificadas en `RowActions` (menú ⋮). Encabezados en `PageHeader`.

### Scripts PowerShell (Windows PowerShell 5.1)
- Multipart (`-Form` de `Invoke-RestMethod`) **no existe** en PS 5.1 → usar `curl.exe` (en System32) con `-F` para subir archivos.
- Scripts `.ps1` con caracteres no-ASCII (tildes, ñ) deben guardarse **con BOM UTF-8**; sin BOM, PS 5.1 los lee como ANSI y el parser falla.

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
- **Backend completo**: auth JWT, RBAC, CRUD REST, generación + RAG (pgvector + Gemini), exportación DOCX/PDF, auditoría, plazos jurídicos (`deadline`), radicado (`internalDocket`), estadísticas, marca de agua al vuelo, edición de borrador (`PUT /{id}/content`).
- **IA multi-proveedor costo-cero + anti-alucinación**: pipeline de 3 modelos (Gemini Flash genera, Groq/Llama verifica citas, DeepSeek verifica datos), embeddings Gemini (sin Ollama), reranking + chunking legal + citas `[Fuente N]`.
- **Despliegue AWS (Terraform)**: `infra/terraform/` (EC2 t3.small, RDS PG16+pgvector, SSM, CloudWatch, EIP) + `docker-compose.prod.yml` sin db/ollama + DuckDNS para HTTPS. Ver `infra/terraform/README.md`.
- **Fidelidad a las minutas de la firma**: cuerpo Cambria 14 justificado; 7 plantillas por tipología real con prompts que calcan la anatomía; identidad del tenant actualizada (V5); firma estampada al aprobar; scripts `seed-rag` para cargar `.docx` modelo + corpus RAG.
- **Frontend completo**: Dashboard, wizard de generación, mis documentos, todos los documentos (admin), plantillas con corpus RAG, entidades, empresa, usuarios. Pase de UI/UX: theme central, `PageHeader`, `RowActions` (menú ⋮), visualización unificada con `DocumentEditor`.
- **Infra**: Docker Compose con 5 servicios + volumen persistente `generated_docs`, scripts `start.ps1/sh`, `seed-rag.ps1/sh`, backups `backup.ps1/sh` + `restore.ps1/sh`, `OPERACION.md`.
- **Tests**: `GenerateDocumentServiceTest`, `DocxExporterTest` (Cambria 14 justificado + membrete), `DocxSignatureStamperTest`, `TemplateControllerSecurityTest`, `AuthServiceTest`, `JwtServiceTest`, `UserServiceTest`, `SplitTextTest`, `PersistenceIntegrationTest`. Suite: 36 tests verdes.

### ⏳ Pendiente operativo (depende de la firma o del servidor)
- `.docx` modelo por plantilla: cargar vía `POST /templates/{id}/format-doc`.
- Corpus legal propio: ampliar vía UI "Fuentes normativas" en Plantillas.
- Backup automático: agendar `backup.ps1`/`backup.sh` en cron/Task Scheduler.
- Credenciales de producción: rotar o eliminar los 4 usuarios del seed (`V2`).

### ⚠️ Deuda técnica conocida
- **Membrete universal**: solo salen con membrete los documentos cuya plantilla tiene `.docx` modelo cargado (`buildFromFormat`). El modo `buildBranded` (plantillas sin modelo, p. ej. las genéricas viejas) pone solo texto, no el logo. Para cumplir "todo documento revisado con membrete" de forma universal, falta insertar el logo real en `buildBranded`.
- **Firma en PDF**: `PdfExporter.buildFromDocx` reconstruye desde texto plano → la firma estampada (y el formato DOCX) no aparecen en el PDF. Usar DOCX para el documento firmado.
- `ClientDataProviderPort` sin adaptador (gancho a futuro).
- `JwtAuthFilter` usa `@NonNull` de `org.springframework.lang` (deprecado en Spring 7; migrar a JSpecify).
- Bundle del frontend ~680 kB — code-splitting con `React.lazy` mejoraría carga inicial.
- El campo `authorUserId` en documentos históricos contiene UUID en vez de username (`resolveUserName` lo maneja, pero lo ideal sería una migración que normalice el campo).
