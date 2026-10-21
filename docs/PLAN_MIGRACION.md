# Plan de Migración — FuturoTech AI (Python → Java + React)

> Motor Jurídico Universal. Migración del stack Python (FastAPI + Streamlit + SQLite/ChromaDB) a Java (Spring Boot 4) + React, con PostgreSQL + pgvector. Todas las tecnologías son open source / gratuitas para uso comercial.

---

## 🧭 ESTADO DE LA MIGRACIÓN (continuar desde aquí)

Fuente de referencia (sistema Python original): `c:\proyecto\derecho_peticion` — especificación ejecutable, no se toca.
Proyecto destino: `C:\proyecto\futuroTech` · rama `feature/migracion` · base package `com.futurotech.legal`.

| Fase | Descripción | Estado |
|---|---|---|
| 0 | Scaffolding (Gradle, Docker Compose, Flyway base, React/Vite) | ✅ Completa |
| 1 | Dominio hexagonal + persistencia JPA + Flyway V1 | ✅ Completa |
| 2 | Auth JWT + RBAC (BCrypt, Spring Security stateless) | ✅ Completa |
| 3 | CRUD REST (users, templates, entities, company) con `@PreAuthorize` | ✅ Completa |
| 4 | Generación + RAG (Ollama+Resilience4j, pgvector, prompt estricto, DOCX/PDF, auditoría) | ✅ Completa |
| 5 | Frontend React (wizard, admin pages) | ✅ Completa |
| 6 | Infra completa + datos semilla + corte | ✅ Completa (código); verificación e2e requiere Docker |

**Commit de referencia:** `a70fff3` ("feat: migracion a Java 21 + Spring Boot 4 (Fases 0-4)"). Fases 0-5 commiteadas.

**Fase 5 — completa:**
- ✅ API client axios (`src/api/`: types, client con refresh JWT, endpoints)
- ✅ Store Zustand (`src/store/auth.ts`) + ProtectedRoute + AppLayout
- ✅ Páginas: Login, GenerateWizard (3 pasos), MyDocuments, Templates, Entities, Users, Company
- ✅ Routing en `App.tsx`
- ✅ `npm run build` verde (tsc -b + vite build). Fix: MUI 6.5 usa `Grid2` para el API con `size`; se migraron `CompanyPage` y `GenerateWizard`.

**Verificaciones pendientes (requieren Docker/Ollama, no disponibles en el entorno de desarrollo actual):**
- Test de persistencia Testcontainers (omitido sin Docker).
- Generación end-to-end, embeddings, búsqueda pgvector.
- Preservación byte-a-byte del DOCX con documentos modelo reales (el reemplazo del cuerpo usa manipulación de string del `document.xml`; validar con .docx complejos: tablas, saltos de sección).
- Flujo HTTP real con seguridad (solo hay tests unitarios de servicio, no MockMvc/contexto).

**Fase 6 — completa (código):**
- ✅ Datos semilla vía Flyway `V2__seed_data.sql`: tenant `estufuturo-legal` (branding camelCase), 3 plantillas (`PETICION-GENERAL-V1`, `PETICION-PENSIONAL-V1`, `ACCION-TUTELA-V1`) con system_prompt + legal_rules, y 20 entidades. Idempotente (`ON CONFLICT DO NOTHING`). `fields` sembrado como array de keys (el dominio `Template.fields` es `List<String>`; labels/required viven en el front).
- ✅ Docker Compose completo: servicio `ollama-init` que hace `ollama pull` de los modelos de generación y embeddings una sola vez; el backend espera a que termine (`service_completed_successfully`).
- ✅ Scripts de arranque de un comando: `start.ps1` (Windows) y `start.sh` (Unix) — crean `.env` con `JWT_SECRET` aleatorio y levantan el stack.
- ⏳ **Verificación e2e pendiente (requiere Docker, no disponible en este entorno):** `docker compose up`, ejecución real de Flyway V2, descarga de modelos Ollama, login con credenciales del primer arranque, generación + RAG + descarga DOCX/PDF.

**Las otras 2 plantillas del sistema Python** (`PETICION-SALUD-V1`, `RECURSO-REPOSICION-V1`) quedan disponibles en `derecho_peticion/templates.json` para ampliar el seed cuando se requieran.

---

## 1. Objetivo y principios

Migrar **la lógica de negocio que aporta valor**, descartar el andamiaje accidental, mejorar donde se pueda, y entregar un **frontend intuitivo pensado para abogados**.

No es un port 1:1. Es una reescritura que preserva el comportamiento jurídico y mejora la plataforma.

Principios rectores:
- **Preservar el comportamiento del dominio** (composición de prompt, modo RAG estricto, preservación del formato DOCX, RBAC). El sistema Python es la especificación ejecutable de referencia.
- **Eliminar deuda**: nada de SQLite dual, migración desde JSON, ni sesión HMAC en memoria.
- **Mejorar la plataforma**: API REST completa y tipada, JWT, validación formal, un solo motor de datos.
- **Frontend para abogados**: flujo guiado, lenguaje del dominio, mínima fricción.

---

## 2. Stack destino (todo gratuito / OSS)

### Backend — Java
| Componente | Tecnología | Licencia |
|---|---|---|
| Lenguaje | Java 21 (LTS, Temurin) | GPLv2+CE |
| Framework | Spring Boot 4.0 (Spring Framework 7, Jakarta EE 11, Jackson 3, JUnit 6) | Apache 2.0 |
| Build | Gradle 9 (Kotlin DSL) | Apache 2.0 |
| Persistencia | Spring Data JPA + Hibernate ORM 7 | Apache 2.0 / LGPL |
| BD | PostgreSQL 16 + extensión **pgvector** | PostgreSQL License |
| Migraciones | Flyway | Apache 2.0 |
| IA (generación) | **Ollama** vía `WebClient` (REST, local) | MIT — costo cero |
| IA (embeddings) | **Ollama** (`nomic-embed-text` / `mxbai-embed-large`) | MIT — costo cero |
| Resiliencia | Resilience4j (circuit breaker + retry) | Apache 2.0 |
| DOCX | Apache POI | Apache 2.0 |
| PDF | OpenPDF | LGPL/MPL |
| Auth | Spring Security + BCrypt + jjwt (JWT) | Apache 2.0 |
| Tests | JUnit 6 + Mockito + AssertJ + Testcontainers | OSS |

### Frontend — React
| Componente | Tecnología | Licencia |
|---|---|---|
| Framework | React 18 + Vite | MIT |
| Lenguaje | TypeScript | Apache 2.0 |
| Routing | React Router | MIT |
| Data fetching | TanStack Query | MIT |
| Estado | Zustand | MIT |
| UI | Material UI (MUI) | MIT |
| Editor | react-md-editor (o similar OSS) | MIT |

### Infraestructura
- Docker + Docker Compose (gratis)
- Nginx — reverse proxy + servir SPA (gratis)
- Despliegue objetivo: AWS EC2 Free Tier (como hoy)

> **IA con Ollama (costo cero total):** toda la IA corre localmente vía Ollama — sin costo por uso y con datos 100% on-premise (relevante para documentos jurídicos confidenciales; nada sale a un tercero). El adaptador de IA se diseña contra el puerto `AIServicePort`, de modo que Gemini u otro proveedor siguen siendo intercambiables sin tocar el dominio.
>
> **Modelos sugeridos:** generación → `llama3.1:8b` o `qwen2.5:7b` (buen español); embeddings → `nomic-embed-text` (768 dims) o `mxbai-embed-large` (1024 dims). La dimensión de la columna `vector` en pgvector se fija según el modelo de embeddings elegido.
>
> **Consideración de infra:** un modelo de generación decente necesita RAM/GPU. El AWS EC2 Free Tier **no es suficiente** para correr Ollama con calidad; el despliegue requerirá una instancia con más memoria o GPU, o un host dedicado. Es el trade-off de eliminar el costo de API.

---

## 3. Qué migra / qué se descarta / qué se mejora

### ✅ Migra (lógica que sirve — se preserva el comportamiento)
- Composición del prompt jurídico y **modo RAG estricto** (3 escenarios anti-alucinación).
- Generación con IA (Ollama) + resiliencia (circuit breaker + retry exponencial).
- RAG: ingesta PDF/TXT/DOCX, chunking con solapamiento, embeddings, búsqueda semántica.
- Preservación **byte-a-byte** del formato DOCX (reemplazo de `word/document.xml`) + export PDF con membrete.
- Multi-tenant + branding (encabezado, pie, color, NIT, contacto).
- RBAC: 4 roles (admin, abogado, pasante, consulta), 8 permisos, permisos custom por usuario.
- Plantillas con versionado inmutable, campos configurables y reglas jurídicas.
- Entidades destinatarias.
- Health check profundo (DB, vector store, estado del circuit breaker).

### ❌ Se descarta (accidental, no aporta valor)
- Streamlit completo y la capa `ui/` → reemplazado por React.
- SQLite y toda la lógica dual dev/prod → solo PostgreSQL.
- Migración desde JSON (`migrate_from_json`, `tenants.json`) → andamiaje legacy.
- Sesión HMAC server-side en memoria (`session.py`) → JWT stateless.
- Auto-migración con `ALTER TABLE` → Flyway desde el inicio.
- Metadata con claves `_system_prompt` / `_rag_strict` → campos tipados en la API.

### ⬆️ Se mejora
- **API REST completa y tipada**: hoy solo `/generate` es HTTP; el resto lo hacía Streamlit directo contra el repositorio. Todo pasa a REST con DTOs validados (Bean Validation).
- **Auth moderna**: JWT + refresh token, CORS correcto, sin token en query param.
- **Un solo motor de datos**: pgvector elimina la desincronización Chroma↔BD del diseño actual.
- **Seguridad**: `SECRET`/JWT key obligatorios en prod (falla en arranque si faltan), sin contraseñas en logs.
- **Frontend guiado** para el usuario final (abogado).

---

## 4. Mapeo de componentes

| Python (origen) | Java (destino) |
|---|---|
| FastAPI `routes.py` + `dtos.py` | `@RestController` + DTOs + Bean Validation |
| `generate_document.py` (use case) | `GenerateDocumentService` (`@Service`) |
| `in_ports.py` / `out_ports.py` | Interfaces (puertos) en capa de dominio |
| `db_postgres.py` (SQLAlchemy) | Spring Data JPA: entities + repositories |
| `db_sqlite.py` | *(descartado)* |
| Alembic | Flyway |
| `ai_gemini.py` (pybreaker + tenacity) | `OllamaClient` (WebClient) + Resilience4j (otros proveedores intercambiables vía puerto) |
| `knowledge_base.py` (ChromaDB) | `PgVectorKnowledgeBase` (pgvector + HNSW) |
| `doc_generator.py` (python-docx / fpdf2) | `DocxExporter` (Apache POI) + `PdfExporter` (OpenPDF) |
| `auth_service.py` (bcrypt) | Spring Security + `BCryptPasswordEncoder` |
| `session.py` (HMAC en memoria) | JWT stateless (jjwt) |
| Streamlit `pages/` | React: páginas + componentes + TanStack Query |

---

## 5. Esquema de datos (PostgreSQL + Flyway)

Tablas portadas del esquema actual (mejoradas y tipadas):

- `tenant` — firma/empresa + branding (JSONB), `active`. **Single-tenant en el MVP: una sola fila**, pero el modelo queda multi-tenant-ready para no cerrar la puerta a futuras firmas.
- `templates` — plantilla + `system_prompt`, `legal_rules` (JSONB), `rag_strict`, `format_doc_name`, `format_doc_bytes` (BYTEA)
- `template_versions` — historial inmutable por plantilla
- `template_documents` — documentos RAG indexados por plantilla
- `template_chunks` — **nueva**: chunks + columna `vector` (pgvector, índice HNSW, coseno). Dimensión según modelo de embeddings de Ollama.
- `entities` — entidades destinatarias
- `users` — RBAC con `role` y `permissions` (JSONB nullable)
- `generated_document` — **nueva (trazabilidad/auditoría)**: autor, plantilla+versión, entidad destinataria, campos del caso, timestamp, estado (`DRAFT`/`REVIEWED`/`APPROVED`), hash del archivo, radicado interno opcional. Append-only — registro de auditoría, no se borra.

---

## 6. API REST a construir

La mayor parte del backend: exponer como REST lo que Streamlit hacía contra el repositorio.

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/v1/auth/login` | Login → JWT + refresh |
| POST | `/api/v1/auth/refresh` | Renovar token |
| GET/POST/PUT/DELETE | `/api/v1/users` | CRUD usuarios + permisos |
| GET/POST/PUT/DELETE | `/api/v1/templates` | CRUD plantillas + versiones |
| POST/DELETE | `/api/v1/templates/{id}/documents` | Subir/borrar documentos RAG |
| GET/POST/PUT/DELETE | `/api/v1/entities` | CRUD entidades |
| GET/PUT | `/api/v1/company` | Tenant / branding |
| POST | `/api/v1/documents/generate` | Generación (ya existe hoy) |
| GET | `/api/v1/documents/{id}/download` | Descarga DOCX/PDF |
| GET | `/health` | Health check profundo |

---

## 7. Frontend para abogados (UX)

Objetivo: que un abogado genere un documento **sin entrenamiento técnico**.

Principios:
- **Flujo guiado (wizard)** para generación: elegir plantilla → datos del caso → revisar reglas/RAG → generar → previsualizar y editar → descargar. No un formulario denso.
- **Lenguaje del dominio**: "Entidad a la que se dirige", "Hechos del caso" — nunca "metadata" ni "template_id".
- **Previsualización en vivo** con editor simple (formato por botones, no sintaxis markdown cruda).
- **Mínimos clics** para la tarea principal. La administración (usuarios, plantillas) en segundo plano, solo para admin.
- **Feedback claro**: estados de carga, errores en español entendible, confirmaciones.
- **Responsive y accesible** con MUI.

Pantallas:
1. Login
2. Generar documento (wizard) — pantalla estrella
3. Mis documentos / historial
4. Plantillas (admin)
5. Entidades
6. Empresa / branding (admin)
7. Usuarios (admin)

---

## 8. Fases de ejecución

Cada fase termina compilando y con verificación. No se entrega todo de un golpe.

| Fase | Entregable | Verificación |
|---|---|---|
| **0. Scaffolding** | Estructura `backend/` + `frontend/`, Gradle, Docker Compose (postgres+pgvector), Flyway con esquema base | `gradle build` OK, contenedores levantan |
| **1. Dominio + persistencia** | Entities, repositories JPA, puertos, migraciones Flyway | Tests de repositorio con Testcontainers |
| **2. Auth + RBAC** | Login JWT, refresh, 4 roles, 8 permisos, BCrypt | Tests de seguridad, login end-to-end |
| **3. CRUD REST** | Users, templates, entities, company como REST tipado | Tests de integración por endpoint |
| **4. Generación + RAG** | GeminiClient + Resilience4j, prompt composition, pgvector, export DOCX/PDF | Paridad de salida vs sistema Python |
| **5. Frontend React** | Vite + MUI, auth, wizard de generación, pantallas admin | Flujo completo navegable |
| **6. Infra + corte** | Docker Compose completo, Nginx, script import one-shot de plantillas/entidades/RAG | Despliegue local + datos reales importados |

---

## 9. Estructura de carpetas propuesta

```
C:\proyecto\futuroTech\
├── .kiro/
│   └── steering/
│       └── project-conventions.md
├── docs/
│   └── PLAN_MIGRACION.md
├── backend/                  # Spring Boot 4 (Gradle Kotlin DSL)
│   └── src/main/java/...     # dominio / aplicación / infraestructura
├── frontend/                 # React + Vite + TS
│   └── src/...
├── docker-compose.yml
└── README.md
```

---

## 10. Decisiones tomadas (como senior)

| Tema | Decisión | Razón |
|---|---|---|
| Vector store | **pgvector** | Un solo motor, consistencia transaccional, menos operación. Escala suficiente para el caso. |
| Build | **Gradle (Kotlin DSL)** | Soporte de primera clase en SB4, builds incrementales, tipado en el script. |
| Datos | **BD limpia** + import one-shot al final | Esquema mejorado; migrar en vivo introduce bugs. Plantillas/RAG se importan en el corte. |
| Spring | **Spring Boot 4.0 / Java 21** | GA desde nov-2025. Jakarta EE 11, Jackson 3, JUnit 6. |
| Frontend UI | **Material UI** | Librería madura, accesible, MIT. |
| IA | **Ollama local** (`qwen2.5:7b`/`14b`) | Costo cero + datos on-premise (confidencialidad jurídica). Puerto abierto a otros modelos. |
| Alcance | **Single-tenant** (modelo multi-tenant-ready) | MVP para una firma; sin complejidad de gestión multi-firma, sin cerrar la puerta. |
| Jurisdicción | **Colombia / español** | Enfoque del MVP; simplifica plantillas y prompts. |
| Entrega | **MVP operando** (despliegue automatizado) | `docker compose up` levanta todo; scripts de backup/restore; apto para despacho sin IT. |
| Firma | **Manuscrita (imprimir/PDF)** | Firma electrónica queda como gancho futuro, no en el MVP. |
| Concurrencia | **Cola de generación simple (1 a la vez)** | Ollama local compite por GPU/CPU; evita degradación con uso concurrente. |

---

## 11. Arquitectura hexagonal (puertos y adaptadores)

El dominio no conoce Spring, JPA ni Jackson. Las dependencias apuntan hacia adentro.

```
backend/src/main/java/com/futurotech/legal/
├── domain/                      # Núcleo puro — sin framework
│   ├── model/                   # TenantConfig, Template, Entity, User, LegalContext,
│   │                            #   ClientData, GeneratedDocument, Permission, Role
│   ├── exception/               # DomainException, AuthenticationException, TenantNotFoundException...
│   └── port/
│       ├── in/                  # GenerateDocumentUseCase (driving)
│       └── out/                 # AIServicePort, KnowledgeBasePort, TenantRepositoryPort,
│                                #   TemplateRepositoryPort, EntityRepositoryPort, UserRepositoryPort,
│                                #   FileExporterPort, ClientDataProviderPort, GeneratedDocumentPort
│
├── application/                 # Casos de uso — @Service (orquestación)
│   └── service/                 # GenerateDocumentService, AuthService
│
└── infrastructure/              # Adaptadores concretos — aquí vive Spring
    ├── adapter/in/web/          # @RestController + DTOs + Bean Validation
    └── adapter/out/
        ├── ai/                  # OllamaClient (WebClient + Resilience4j) → AIServicePort
        ├── persistence/         # JPA entities + repositories → *RepositoryPort
        ├── rag/                 # PgVectorKnowledgeBase → KnowledgeBasePort
        ├── export/              # DocxExporter (POI), PdfExporter (OpenPDF) → FileExporterPort
        └── client/              # ManualClientDataProvider → ClientDataProviderPort (gancho integración futura)
```

Reglas: modelos de dominio independientes de las `@Entity` JPA (mapeo explícito); puertos como interfaces en el dominio, implementados en infraestructura; DTOs HTTP separados del dominio. Hexagonal pragmática: separación donde protege el dominio de cambios externos (IA, BD, web, sistema de la firma), sin capas de mapeo gratuitas.

---

## 12. Requisitos de producto y jurídicos (lo que lo hace usable por un despacho)

### Trazabilidad / auditoría
Todo documento generado se registra en `generated_document` (autor, plantilla+versión, destinatario, campos del caso, timestamp, estado, hash). Append-only. Habilita el historial "Mis documentos" y la defensa ante responsabilidad profesional.

### Revisión humana obligatoria (salvaguarda jurídica)
- Todo documento nace en estado **BORRADOR**. Nunca "listo" automáticamente.
- Transición a REVISADO/APROBADO solo por rol abogado o admin (un pasante no aprueba).
- **Disclaimer visible**: "Documento generado por IA. Requiere revisión y validación de un abogado antes de su uso. Las citas normativas deben verificarse contra fuente oficial."
- El modo RAG estricto es la defensa técnica contra jurisprudencia inventada (riesgo disciplinario real en Colombia).

### Formato de la firma (requisito central)
- La firma **adjunta su .docx modelo** (membrete, logos, encabezado/pie, márgenes, estructura de secciones).
- La IA recibe la **estructura extraída** del modelo como instrucción obligatoria de secciones y orden.
- Export con **preservación byte-a-byte**: se reemplaza solo `word/document.xml`, el resto del paquete OOXML queda intacto. Implementación Java con Apache POI + manipulación directa del ZIP. **Punto de verificación Fase 4**: validar con los .docx reales de la firma que abran en Word con formato intacto (tablas, campos, saltos de sección).

### Plantillas semilla con prompts de grado profesional
Prompts editables por la firma, pero con un **default robusto** en cada plantilla del MVP:
- Derecho de petición (Art. 23 CP, Ley 1755 de 2015)
- Acción de tutela (Art. 86 CP, Decreto 2591 de 1991)
- Requerimiento pensional (marco de seguridad social)

Anatomía de cada prompt: rol/competencia específica · marco normativo anclado · estructura formal obligatoria · reglas de estilo jurídico · salvaguardas anti-alucinación · manejo de datos faltantes con marcador `[PENDIENTE: ...]` · límite de rol (borrador que requiere revisión). *Nota: la validación jurídica de fondo la realiza un abogado de la firma; la herramienta no reemplaza el juicio del profesional que firma.*

### Habeas data (Ley 1581 de 2012)
Datos sensibles (cédulas, hechos de salud en tutelas). Retención configurable, función de borrado de datos de cliente, y **logs sin datos sensibles del cliente**.

### Corpus legal base del RAG
Para que el modo estricto funcione desde el día uno, se incluye un corpus mínimo (Constitución Política, Ley 1755 de 2015, Decreto 2591 de 1991). La firma amplía con sus propias fuentes.

### Integración futura con el sistema de la firma
Puerto `ClientDataProviderPort` con adaptador `ManualClientDataProvider` por defecto (hoy el abogado digita los datos). A futuro, un adaptador externo trae datos del cliente del sistema de la firma sin tocar el dominio. En la UI: campo de identificación con botón "Traer datos" (gancho preparado).

---

## 13. Experiencia de usuario (más fácil y ágil que ChatGPT o a mano)

Propuesta de valor: eliminar el prompt manual, el copiar/pegar a Word, el rearmado de membrete y el riesgo de citas inventadas.

Principios aplicados:
- **Wizard de 3 pasos** para generar: tipo de documento (tarjetas visuales) → datos del caso (lenguaje de abogado, con ayudas) → generar (botón grande + progreso honesto).
- **Autocompletado**: entidades desde el directorio con buscador; identificación con "Traer datos"; autosave del borrador.
- **Previsualización + edición** en el mismo lugar, con botones de formato (no markdown crudo); descarga Word/PDF con un clic.
- **Ayudas gráficas**: tooltips, estados visuales por color (borrador/revisado), indicador de "modo estricto / fuentes cargadas", errores en español llano, primer arranque guiado, empty states con guía.
- **Rapidez percibida**: navegación instantánea (React + TanStack Query con caché); lo único lento (generación) con progreso real.
- **Complejidad oculta**: el abogado ve simpleza; la auditoría, el modo estricto y el estado de revisión ocurren por debajo.

---

## 14. Robustez y operación (despacho sin IT)

- **Backups automáticos** diarios de PostgreSQL + volumen pgvector, con retención y restauración probada.
- **Auto-restart**: `docker compose` con `restart: unless-stopped`.
- **Health check profundo** (DB, pgvector, Ollama, circuit breaker) + alerta simple ante degradación.
- **Scripts de un comando** para arrancar, respaldar y restaurar.
- **Manejo de errores de cara al usuario**: mensajes claros en español, nunca stack traces.
- **Documentación de operación** orientada a usuarios no técnicos.

---

*Documento de planificación. El sistema Python actual (`c:\proyecto\derecho_peticion`) permanece intacto como especificación ejecutable de referencia hasta completar el corte.*
