# Convenciones del Proyecto — FuturoTech AI (Java + React)

Motor Jurídico Universal. Migración desde un sistema Python (FastAPI + Streamlit) a Java + React. Este proyecto NO tiene relación con Ficohsa/NOVA — no aplicar steering, librerías ni convenciones corporativas de Ficohsa aquí.

## Stack (fijo)

- **Backend:** Java 25 (LTS) + Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11, Jackson 3, JUnit 6)
- **Build:** Gradle 9.8.1 con Kotlin DSL (`build.gradle.kts`). El toolchain Java es 25; el wrapper debe ser ≥ 9.1 (soporte Java 25).
- **Persistencia:** Spring Data JPA + Hibernate ORM 7 sobre PostgreSQL 16
- **Vector store:** pgvector (extensión de PostgreSQL) — NO ChromaDB
- **Migraciones:** Flyway — NO Alembic, NO auto-ALTER. En Spring Boot 4 **requiere el starter `spring-boot-starter-flyway`** (no basta `flyway-core`: el diseño modular ya no autoconfigura Flyway solo con la librería). Para pg16/pgvector, añadir `flyway-database-postgresql`.
- **IA — generación:** **Groq por defecto** (`GroqClient`, API compatible con OpenAI: `POST /chat/completions`) vía `WebClient`. Modelo `openai/gpt-oss-120b`. Seleccionable con `ai.provider` (`groq` | `ollama`): `GroqClient` es `@Primary` + `@ConditionalOnProperty(ai.provider=groq, matchIfMissing=true)`; `OllamaClient` queda como alternativa on-premise con `@ConditionalOnProperty(ai.provider=ollama)`. El `base-url` es configurable → migrar a una API paga compatible con OpenAI solo exige cambiar URL/model/api-key, sin tocar código. **Debe haber exactamente UN bean de `AIServicePort` activo.**
- **IA — embeddings:** **Ollama local** (`OllamaEmbeddingClient`), modelo `nomic-embed-text` (768 dims → `vector(768)`). Groq no ofrece embeddings, así que el RAG sigue dependiendo de Ollama. `ollama-init` en compose solo descarga el modelo de embeddings salvo `AI_PROVIDER=ollama`.
- **Resiliencia:** Resilience4j (circuit breaker + retry) — instancias `groq` y `ollama`.
- **Documentos del caso (adjuntos):** `CaseDocumentExtractor` (infra) extrae texto de PDF (Apache PDFBox), DOCX (POI `XWPFWordExtractor`) y TXT. El texto se inyecta en el prompt como "DOCUMENTOS APORTADOS DEL CASO (HECHOS)" — efímero, NO se indexa en pgvector ni se persiste como fuente normativa; solo el nombre queda en la auditoría (`case_fields._adjuntos`).
- **DOCX:** Apache POI · **PDF (export):** OpenPDF (`com.github.librepdf:openpdf`, paquete `com.lowagie`) · **PDF (extracción):** Apache PDFBox 3.
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

## Notas de implementación (aprendizajes Spring Boot 4 / Java 25)

- Gradle NO está global; usar el **wrapper** (`.\gradlew.bat`). El wrapper está fijado a 9.8.1 (soporta Java 25; 9.0 NO).
- **Flyway en Spring Boot 4**: si las migraciones no corren (síntoma: `Schema validation: missing table [...]` al arrancar y la BD vacía sin `flyway_schema_history`), es porque falta el starter. Usar `spring-boot-starter-flyway`, no `flyway-core` suelto.
- En Spring Boot 4 el API de health se movió a `org.springframework.boot.health.contributor` (`Health`, `HealthIndicator`) — NO `org.springframework.boot.actuate.health`.
- **Un solo bean de `AIServicePort`**: `GroqClient` y `OllamaClient` son mutuamente excluyentes vía `@ConditionalOnProperty(ai.provider=...)`. Si ninguno queda activo, el arranque falla con `No qualifying bean of type AIServicePort`. `ai.provider` por defecto es `groq` (`matchIfMissing=true`).
- Testcontainers requiere su BOM (`testcontainers-bom`) en `dependencyManagement`; sus módulos no traen versión del BOM de Spring Boot.
- Al ejecutar `gradlew build` en PowerShell, el exit code puede ser 1 por warnings en stderr (deprecation / HotSpot) aunque el build sea exitoso. Verificar el resultado real en `build/test-results/test/*.xml`, no solo el exit code.
- Los tests de integración con Testcontainers usan `@EnabledIf("dockerAvailable")` y se omiten si no hay Docker; corren en CI o máquina con Docker.
- **MUI 6.5**: el `Grid` importado de `@mui/material` es el legacy (sin prop `size`). Para el API nuevo con `size={{ xs: ... }}` usar `Grid2 as Grid` desde `@mui/material`.
- **Tests de web en Spring Boot 4 (slicing modular)**: el slice `@WebMvcTest` y `@AutoConfigureMockMvc` ya NO viven en `spring-boot-test-autoconfigure`; están en el módulo dedicado `spring-boot-webmvc-test`, que se trae con el starter **`spring-boot-starter-webmvc-test`** (añadido a `testImplementation`). El import correcto es `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- **`@MockBean` está deprecado** en Spring Boot 4: usar `@MockitoBean` de `org.springframework.test.context.bean.override.mockito`.
- **`@WebMvcTest` ya no auto-configura Spring Security** cuando la seguridad se define con un bean `SecurityFilterChain` (sin `WebSecurityConfigurerAdapter`): falta el bean `HttpSecurity` y el contexto no arranca. Solución aplicada: anotar `SecurityConfig` con `@EnableWebSecurity` (además de `@EnableMethodSecurity`) e importar en el test tanto `SecurityConfig` como `JwtAuthFilter` (`@Import({ SecurityConfig.class, JwtAuthFilter.class })`), mockeando `JwtService` para satisfacer la dependencia del filtro.
- **Autenticación ausente → 403, no 401**: con JWT stateless y sin `AuthenticationEntryPoint` personalizado, Spring responde 403 a las peticiones sin autenticar. Los tests de seguridad lo asumen así.
- **Probar `@PreAuthorize` en el slice web**: un POST con cuerpo JSON puede fallar con 400 (deserialización) antes de evaluar la seguridad de método; para aislar la regla de autorización conviene usar endpoints sin cuerpo (DELETE) o multipart. El `Authentication` se inyecta con `spring-security-test` (`user("x").authorities(...)`), no con tokens reales.
- **Descargas autenticadas en el frontend**: NO usar `<a href>` directo a endpoints protegidos (el navegador no adjunta el JWT → 403). Descargar vía Axios con `responseType: "blob"` y disparar un object URL (ver `documentsApi.download`).
- **Dockerfile + Windows**: el `gradlew` puede tener finales CRLF que rompen su ejecución en la imagen Linux (`./gradlew: not found`). El Dockerfile normaliza con `sed -i 's/\r$//' gradlew` antes de usarlo.
- **Puertos en host**: Postgres se publica en **5433** (`DB_HOST_PORT`) para no chocar con otros Postgres locales; dentro de la red Docker el backend sigue usando 5432.
- **Arranque del backend**: necesita Postgres+pgvector (Flyway migra al arrancar y JPA valida con `ddl-auto: validate`), `GROQ_API_KEY` para generar, y Ollama en `11434` para embeddings del RAG.
- **Seed Flyway `V2__seed_data.sql`**: tenant, 3 plantillas, 20 entidades y 4 usuarios de desarrollo con contraseña conocida. `branding` (JSONB) va en **camelCase** (el record `Branding` se serializa sin estrategia snake_case). `templates.fields` se siembra como array de keys string (el dominio `Template.fields` es `List<String>`).

## Arquitectura

- Arquitectura hexagonal (puertos y adaptadores): dominio sin dependencias de framework, aplicación con casos de uso, infraestructura con adaptadores.
- Spring MVC bloqueante (NO WebFlux reactivo) — el sistema es I/O-bound esperando al LLM; no se justifica la complejidad reactiva.
- DTOs de transporte separados de los modelos de dominio.

## Logging y manejo de excepciones

- **Logger:** SLF4J vía `LoggerFactory.getLogger(Clase.class)` como `private static final Logger log`. NO usar `System.out`/`printStackTrace`. Única excepción documentada: el banner de credenciales iniciales en `AuthService` va a `System.out` a propósito (los secretos NUNCA al logger, que puede tener appenders a fichero/agregador).
- **Correlación (traceId):** `CorrelationIdFilter` (infra/config, `@Order(HIGHEST_PRECEDENCE)`) asigna un `traceId` por petición al MDC de SLF4J y lo devuelve en la cabecera `X-Correlation-ID` (respeta la que envíe el cliente). El patrón de consola incluye `[%X{traceId}]`, así que toda línea de una misma petición es rastreable. El MDC se limpia en `finally` (pool de hilos).
- **Excepciones de dominio:** heredan de `DomainException`. Mapa estándar en `GlobalExceptionHandler` (`@RestControllerAdvice`):
  - `AuthenticationException` → 401
  - `AccessDeniedException` (fallos de `@PreAuthorize`) → 403
  - `TenantNotFoundException` / `ResourceNotFoundException` / `NoHandlerFoundException` → 404
  - `HttpRequestMethodNotSupportedException` → 405
  - `InvalidSchemaException` / `MethodArgumentNotValidException` → 422 (`UNPROCESSABLE_CONTENT`, NO el deprecado `UNPROCESSABLE_ENTITY` en Spring 7)
  - `HttpMessageNotReadableException` / `IllegalArgumentException` → 400
  - `DocumentGenerationException` → 500
  - `Exception` (red de seguridad) → 500 sin filtrar stacktrace al cliente
- **"No encontrado" = 404:** usar `ResourceNotFoundException`, NO `IllegalArgumentException` (400) ni `DocumentGenerationException` (500).
- **Cuerpo de error:** record `ApiError` (`timestamp`, `status`, `error`, `message`, `path`, `traceId`). Mensajes de cara al usuario en español; el `traceId` permite cruzar con los logs.
- **Política de niveles:** 4xx del cliente → `WARN` sin traza (flujo normal); 5xx y lo no previsto → `ERROR` con traza completa. Nunca registrar secretos ni PII sensible.
- **Deprecaciones:** `build.gradle.kts` compila con `-Xlint:deprecation`. Nuevo código sin deprecaciones; `JwtAuthFilter` aún usa `@NonNull` de `org.springframework.lang` (deprecado en Spring 7, pendiente migrar a JSpecify).

## Reglas de migración

- El sistema Python en `c:\proyecto\derecho_peticion` es la **especificación ejecutable de referencia**. Replicar el comportamiento del dominio (composición de prompt, modo RAG estricto, preservación byte-a-byte del DOCX, reglas RBAC) con fidelidad.
- Migrar solo la lógica que aporta valor. Descartar andamiaje: SQLite, migración desde JSON, sesión HMAC.
- Mejorar donde se pueda: API REST completa y tipada, validación formal, seguridad.

## Frontend

- Pensado para abogados sin perfil técnico: flujo guiado (wizard), lenguaje del dominio (no jerga técnica como "metadata"/"template_id"), previsualización en vivo, mínima fricción.
- Accesible y responsive.

### Patrones UI/UX (obligatorios en pantallas nuevas)

- **Notificaciones (feedback):** toda acción con efecto (mutación: guardar, eliminar, aprobar, subir) DEBE dar feedback al usuario vía el proveedor global de notificaciones. Usar el hook `useNotify()` de `components/NotificationProvider.tsx` (`notify.success` / `notify.error`) en los callbacks `onSuccess` / `onError` de cada `useMutation`. Nunca dejar una mutación silenciosa ni tragarse el error. Para el texto de error usar `errorMessage(e, fallback)` de `api/client.ts`. El `NotificationProvider` envuelve la app en `main.tsx` (dentro de `ThemeProvider`, fuera del `BrowserRouter`); muestra un snackbar a la vez y encola el resto.
- **Estados de carga y vacío:** usar los componentes reutilizables de `components/states.tsx` — `LoadingState` mientras la query resuelve (`isLoading`) y `EmptyState` (con icono, título, descripción y CTA opcional) cuando la lista viene vacía. No mostrar tablas vacías sin explicación ni `CircularProgress` suelto.
- **Botones de envío:** deshabilitar durante la mutación con `mutation.isPending` y mostrar texto de progreso ("Guardando…"). Validar los campos obligatorios antes de permitir el submit (`disabled={!valid || mutation.isPending}`) para evitar envíos dobles o inválidos.
- **Accesibilidad:** todo `IconButton` lleva `aria-label` descriptivo y, cuando aporta, un `Tooltip`. Los inputs de contraseña ofrecen toggle de visibilidad. Los campos de formulario usan `autoComplete` apropiado (`username`, `current-password`, etc.).
- **Responsive / layout:** el `AppLayout` usa `Drawer` `temporary` con botón hamburguesa en móvil (`useMediaQuery(theme.breakpoints.down("md"))`) y `permanent` en escritorio. Padding del `main` adaptativo (`p: { xs: 2, sm: 3 }`). Para grids responsivos usar `Grid2 as Grid` con `size={{ xs, sm, md }}`.
- **Legibilidad del dominio:** no exponer identificadores técnicos crudos en la UI (p. ej. en "Mis documentos" se resuelve `templateId` → nombre de plantilla reutilizando `templatesApi.list`). Etiquetas de formulario en español legible, no el nombre del campo del backend.
- **React Query:** el `QueryClient` global (`main.tsx`) usa `retry: 1` y `refetchOnWindowFocus: false`. Invalidar las queries afectadas en `onSuccess` de las mutaciones (`qc.invalidateQueries({ queryKey: [...] })`).

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
- **Pulido UI/UX ✅**: `NotificationProvider` global (snackbars con cola, hook `useNotify`) y componentes `LoadingState`/`EmptyState` reutilizables. Todas las mutaciones dan feedback (`onSuccess`/`onError`) con botones `isPending` + validación. `AppLayout` responsive (drawer colapsable móvil + hamburguesa + avatar). Login con toggle de contraseña. "Mis documentos" resuelve nombre de plantilla. `npm run build` verde.
- **Cierre de gaps backend ✅** (tras auditoría): 
  - **Ingesta RAG por plantilla**: endpoints `GET/POST/DELETE /api/v1/templates/{id}/documents` (`TemplateService.addRagDocument/listRagDocuments/deleteRagDocument`, reutiliza `CaseDocumentExtractor` para PDF/DOCX/TXT; ingesta idempotente). UI en `TemplatesPage` → diálogo `RagDocumentsDialog` (botón "Fuentes normativas"). Antes el corpus solo lo sembraba `RagCorpusSeeder` al arranque; ahora la firma amplía sus fuentes desde la UI.
  - **Export PDF cableado**: `PdfExporter.buildFromDocx(docxBytes, branding)` + `GET /documents/{id}/download?format=pdf` (default `docx`), convierte al vuelo con el branding del tenant. UI ofrece descargar Word o PDF.
  - **Cola de generación**: `Semaphore` (`generation.max-concurrent`, default 1; `generation.acquire-timeout-seconds`, default 180) serializa las generaciones; si no hay cupo tras el timeout, error claro al usuario. Protege a Ollama on-premise de la contención por GPU/CPU.
  - **Tests nuevos**: `GenerateDocumentServiceTest` (composición de prompt + modo RAG estricto con/sin fuentes) y `TemplateControllerSecurityTest` (MockMvc, reglas `@PreAuthorize`). `gradle test` verde (Testcontainers skipped sin Docker).
- Fase 6 (infra + semilla) ✅ completa en código y commiteada: seed Flyway `V2`, servicio `ollama-init` en compose, scripts `start.ps1`/`start.sh`.
- **Migración de plataforma ✅**: Java 21 → **Java 25**, Spring Boot 4.0.0 → **4.1.0**, Gradle wrapper → **9.8.1**, Dockerfile → `eclipse-temurin:25`.
- **IA Ollama → Groq ✅**: generación por Groq (`GroqClient`, `openai/gpt-oss-120b`), Ollama conmutable para on-premise, embeddings siguen en Ollama. `Flyway` arreglado (starter de Spring Boot 4).
- **Adjuntos del caso ✅**: `/documents/generate` acepta multipart con documentos (PDF/DOCX/TXT); su texto se inyecta como hechos en el prompt. Selector de archivos en el wizard. Ejemplo: adjuntar el derecho de petición negado al crear una tutela.
- **Verificación e2e ✅** (hecha con Docker): `docker compose up` con los 5 servicios sanos, Flyway aplica el esquema, `/actuator/health` UP (ai+db), Groq responde con la API key del `.env`. Descarga DOCX corregida (403 por falta de token en `<a href>`).
- **Credenciales de desarrollo** (sembradas en `V2`): usuarios `admin` / `abogado` / `pasante` / `consulta`, todos con contraseña `Admin12345*`. En producción eliminar esos INSERT o cambiar la contraseña tras el primer login.
- ⚠️ **Seguridad**: `GROQ_API_KEY` vive solo en `.env` (ignorado por git). Rotar cualquier clave que se haya expuesto. Validar el tratamiento de datos del proveedor antes de producción (documentos legales salen a la nube).
- ⏳ **Pendiente**: corpus legal base del RAG y `.docx` modelo por plantilla (sin sembrar).
