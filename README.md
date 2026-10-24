# FuturoTech AI — Motor Jurídico Universal

Sistema de generación de documentos jurídicos con IA para despachos de abogados.
Genera derechos de petición, acciones de tutela y otros documentos a partir de los datos del caso,
controlando las alucinaciones mediante RAG estricto sobre fuentes normativas cargadas por la firma.

---

## ¿Qué hace?

- **Genera documentos jurídicos** (DOCX/PDF) con IA (pipeline multi-proveedor: Gemini genera, Groq y DeepSeek verifican).
- **Control anti-alucinación (RAG estricto + verificación multi-modelo):** la IA solo cita normas del corpus de la firma, y dos modelos independientes verifican las citas y los datos duros del borrador.
- **Flujo de revisión:** todo documento nace como BORRADOR con marca de agua; solo un abogado o administrador puede aprobarlo.
- **Control de plazos:** calcula y muestra el vencimiento legal de cada documento (petición=15 días hábiles, tutela=10).
- **Radicado:** permite registrar el número de radicado asignado por la entidad destinataria.
- **Dashboard:** métricas del despacho — documentos del mes, pendientes de revisión, próximos a vencer, vencidos.
- **RBAC:** 4 roles (admin, abogado, pasante, consulta) con 8 permisos configurables por usuario.

---

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 25 + Spring Boot 4.1 + Gradle 9.8.1 |
| Base de datos | PostgreSQL 16 + pgvector |
| IA generación | Gemini Flash (API OpenAI-compatible, configurable por rol) |
| IA verificación | Groq/Llama (citas) + DeepSeek vía OpenRouter (datos) |
| IA embeddings | Gemini `gemini-embedding-001` (768 dims) |
| Frontend | React 18 + Vite + TypeScript + MUI 6 |
| Infra local | Docker Compose + Nginx |
| Infra AWS | Terraform (EC2 + RDS + SSM + CloudWatch) — ver `infra/terraform/` |

---

## Arranque rápido

### Requisitos
- Docker Desktop (o Docker Engine + Compose)
- Una `GEMINI_API_KEY` de [Google AI Studio](https://aistudio.google.com) (gratis) — generación + embeddings
- Opcional: `GROQ_API_KEY` y `OPENROUTER_API_KEY` para activar los verificadores anti-alucinación

### Windows
```powershell
git clone https://github.com/ccrc83ia-tech/futuroTech
cd futuroTech

# Copia el ejemplo y agrega tus claves de IA
copy .env.example .env
# Editar .env y poner GEMINI_API_KEY=... (y opcionalmente GROQ/OPENROUTER)

.\start.ps1
```

### Linux / macOS
```bash
git clone https://github.com/ccrc83ia-tech/futuroTech
cd futuroTech
cp .env.example .env
# Editar .env y poner GEMINI_API_KEY=... (y opcionalmente GROQ/OPENROUTER)
./start.sh
```

El script crea un `JWT_SECRET` aleatorio si no existe, construye las imágenes y levanta los servicios.
La IA (generación, verificación y embeddings) usa APIs remotas — no se descargan modelos locales.

### Accesos
| Servicio | URL |
|---|---|
| Frontend | http://localhost |
| Backend API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |

### Credenciales de desarrollo
| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `Admin12345*` | Administrador |
| `abogado` | `Admin12345*` | Abogado |
| `pasante` | `Admin12345*` | Pasante |
| `consulta` | `Admin12345*` | Consulta (solo lectura) |

> **Producción:** cambiar estas contraseñas o eliminar los INSERT del seed (`V2__seed_data.sql`) antes de desplegar.

---

## Estructura del proyecto

```
futuroTech/
├── backend/                    # Spring Boot 4.1 (Gradle Kotlin DSL)
│   ├── src/main/java/...
│   │   ├── domain/             # Modelos y puertos (sin Spring)
│   │   ├── application/        # Casos de uso (@Service)
│   │   └── infrastructure/     # Adaptadores (REST, JPA, IA, RAG, exporters)
│   └── src/main/resources/
│       ├── db/migration/       # Flyway V1–V7
│       └── rag-corpus/         # Textos base del corpus normativo
├── infra/terraform/            # IaC AWS (EC2, RDS, SSM, CloudWatch)
├── docker-compose.prod.yml     # Compose de producción (sin db ni ollama)
├── frontend/                   # React 18 + Vite + TypeScript
│   └── src/
│       ├── pages/              # 9 páginas (Dashboard, Wizard, Documentos, etc.)
│       ├── components/         # AppLayout, NotificationProvider, DeadlineChip, states
│       ├── api/                # Axios client + endpoints + tipos
│       └── store/              # Zustand (auth)
├── docs/
│   ├── OPERACION.md            # Guía de operación para el despacho
│   └── PLAN_MIGRACION.md       # Historial de decisiones y estado
├── docker-compose.yml
├── start.ps1 / start.sh        # Arranque de un comando
├── backup.ps1 / backup.sh      # Backup de PostgreSQL con retención
└── restore.ps1 / restore.sh    # Restauración
```

---

## Páginas del frontend

| Ruta | Página | Visible para |
|---|---|---|
| `/` | **Dashboard** — métricas, control de plazos, borradores pendientes | Todos |
| `/generar` | **Generar documento** — wizard 3 pasos | Pasante, abogado, admin |
| `/documentos` | **Mis documentos** — búsqueda, filtros, radicado, vencimiento | Pasante, abogado, admin |
| `/todos` | **Todos los documentos** — vista global del equipo | Abogado, admin |
| `/plantillas` | **Plantillas** — CRUD + corpus RAG por plantilla | Admin (edit), todos (ver) |
| `/entidades` | **Entidades destinatarias** — directorio de juzgados, EPS, etc. | Todos |
| `/empresa` | **Empresa** — branding, membrete, color | Admin |
| `/usuarios` | **Usuarios** — CRUD, roles con chips de color, activar/desactivar | Admin |

---

## API REST resumida

```
POST   /api/v1/auth/login                   # Login → JWT
POST   /api/v1/auth/refresh                 # Renueva token

GET    /api/v1/documents/mine               # Mis documentos
GET    /api/v1/documents/all                # Todos (admin/abogado)
GET    /api/v1/documents/stats              # Métricas del despacho
POST   /api/v1/documents/generate           # Genera documento (multipart)
GET    /api/v1/documents/{id}/download      # Descarga DOCX o PDF (?format=pdf)
PUT    /api/v1/documents/{id}/review        # Aprobar/revisar/anular
PUT    /api/v1/documents/{id}/docket        # Registrar radicado

GET    /api/v1/templates                    # Lista plantillas
POST   /api/v1/templates/{id}/documents     # Indexa fuente RAG (PDF/DOCX/TXT)
GET    /api/v1/templates/{id}/documents     # Lista fuentes RAG

GET    /api/v1/entities                     # Directorio de entidades
GET    /api/v1/company                      # Configuración de la firma
GET    /api/v1/users                        # Gestión de usuarios
```

---

## Flujo de trabajo típico

```
1. Admin carga las plantillas y sus fuentes normativas (RAG)
2. Pasante abre el wizard → selecciona tipo de documento → ingresa datos del caso
3. El sistema genera el documento (IA + RAG estricto) → estado BORRADOR con marca de agua
4. Abogado revisa en "Todos los documentos" → aprueba → desaparece la marca de agua
5. Se descarga el documento aprobado (Word o PDF) y se radica ante la entidad
6. Se registra el número de radicado en el sistema para trazabilidad
```

---

## Marca de agua y salvaguarda jurídica

Todo documento generado nace en estado **BORRADOR** e incluye una marca de agua diagonal:
- **DOCX**: header VML (WordArt nativo que Word reconoce como marca de agua).
- **PDF**: capa diagonal translúcida generada al vuelo.

La marca se aplica en la descarga según el estado actual del documento — el archivo en disco no cambia. Al aprobar el documento, la siguiente descarga sale limpia sin necesidad de regenerar.

> ⚠️ Los documentos generados por IA requieren revisión y validación de un abogado antes de su uso. Las citas normativas deben verificarse contra la fuente oficial.

---

## Control de plazos

El sistema calcula automáticamente la fecha límite legal al generar cada documento:
- **Acción de Tutela:** 10 días hábiles
- **Derecho de Petición (general, pensional):** 15 días hábiles

El dashboard muestra los documentos próximos a vencer (≤7 días, en naranja) y vencidos (en rojo).

---

## Corpus RAG y fuentes normativas

El corpus base incluye:
- Constitución Política — Art. 23, 29, 48, 49, 86
- Ley 1755 de 2015 (derecho de petición)
- Decreto 2591 de 1991 (tutela)

La firma puede ampliar el corpus desde la UI: **Plantillas → Fuentes normativas → Subir documento** (PDF, DOCX o TXT). La ingesta fragmenta el texto (chunking consciente de estructura legal), genera embeddings con Gemini (768 dims) y los indexa en pgvector. En la búsqueda, un reranking léxico reordena los fragmentos y las fuentes se numeran `[Fuente N]` para citas trazables.

---

## Backups

```powershell
.\backup.ps1           # Crea dump en ./backups/ (retención 14 últimos)
.\restore.ps1 -File backups/legal_engine-20260101-030000.dump
```

Ver `docs/OPERACION.md` para configurar el backup automático diario.

---

## Variables de entorno (`.env`)

| Variable | Descripción | Requerida |
|---|---|---|
| `GEMINI_API_KEY` | Clave de Gemini — generador + embeddings | Sí |
| `JWT_SECRET` | Secreto para firmar los JWT (auto-generado por `start.ps1`) | Sí |
| `DB_PASSWORD` | Contraseña de PostgreSQL | Sí (default: `secret`) |
| `GROQ_API_KEY` | Clave de Groq — verificador de citas | No (degrada a "omitido") |
| `OPENROUTER_API_KEY` | Clave de OpenRouter — verificador de datos (DeepSeek) | No (degrada a "omitido") |
| `AI_GENERATOR_MODEL` | Modelo generador (default: `gemini-2.5-flash`) | No |
| `EMBEDDINGS_DIMENSIONS` | Dimensión de embeddings (default: `768`) | No |
| `VERIFICATION_ENABLED` | Activa/desactiva la verificación anti-alucinación | No (default `true`) |

Ver `.env.example` para la lista completa (modelos por rol, URLs, interruptores).

---

## Despliegue en AWS

Infraestructura como código en `infra/terraform/` (single-node, costo reducido):

- **EC2 `t3.small`** con backend + frontend en Docker (sin Ollama → instancia pequeña).
- **RDS PostgreSQL 16** con pgvector.
- **SSM Parameter Store** para secretos (keys de IA, JWT, DB).
- **CloudWatch** para logs, **Elastic IP** fija, **DuckDNS + Certbot** para HTTPS gratis.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # editar
terraform init && terraform apply
```

Usa `docker-compose.prod.yml` (sin `db` — es RDS — ni Ollama). Pasos completos y costos en `infra/terraform/README.md`.

> **Datos reales (Ley 1581):** el stack de IA gratuito puede usar los datos para entrenar. Para producción con datos de clientes, cambia generador y embeddings a tier de pago ajustando las variables `AI_GENERATOR_*`/`EMBEDDINGS_*` (sin tocar código).

---

## Licencia

Propietario — FuturoTech AI. Todas las dependencias son open source / gratuitas para uso comercial.
