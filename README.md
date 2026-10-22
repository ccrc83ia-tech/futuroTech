# FuturoTech AI — Motor Jurídico Universal

Sistema de generación de documentos jurídicos con IA para despachos de abogados.
Genera derechos de petición, acciones de tutela y otros documentos a partir de los datos del caso,
controlando las alucinaciones mediante RAG estricto sobre fuentes normativas cargadas por la firma.

---

## ¿Qué hace?

- **Genera documentos jurídicos** (DOCX/PDF) con IA (Groq por defecto, Ollama on-premise opcional).
- **Control anti-alucinación (RAG estricto):** la IA solo cita normas presentes en el corpus cargado por la firma.
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
| IA generación | Groq (default) / Ollama on-premise |
| IA embeddings | Ollama (`nomic-embed-text`) |
| Frontend | React 18 + Vite + TypeScript + MUI 6 |
| Infra | Docker Compose + Nginx |

---

## Arranque rápido

### Requisitos
- Docker Desktop (o Docker Engine + Compose)
- Una `GROQ_API_KEY` de [console.groq.com](https://console.groq.com) (gratis)

### Windows
```powershell
git clone https://github.com/ccrc83ia-tech/futuroTech
cd futuroTech

# Copia el ejemplo y agrega tu clave de Groq
copy .env.example .env
# Editar .env y poner GROQ_API_KEY=gsk_...

.\start.ps1
```

### Linux / macOS
```bash
git clone https://github.com/ccrc83ia-tech/futuroTech
cd futuroTech
cp .env.example .env
# Editar .env y poner GROQ_API_KEY=gsk_...
./start.sh
```

El script crea un `JWT_SECRET` aleatorio si no existe, construye las imágenes y levanta los 5 servicios.
La primera vez Ollama descarga el modelo de embeddings (~270 MB) — puede tardar varios minutos.

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
│       ├── db/migration/       # Flyway V1–V4
│       └── rag-corpus/         # Textos base del corpus normativo
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

La firma puede ampliar el corpus desde la UI: **Plantillas → Fuentes normativas → Subir documento** (PDF, DOCX o TXT). La ingesta fragmenta el texto, genera embeddings con Ollama y los indexa en pgvector.

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
| `GROQ_API_KEY` | Clave API de Groq para generación | Sí |
| `JWT_SECRET` | Secreto para firmar los JWT (auto-generado por `start.ps1`) | Sí |
| `DB_PASSWORD` | Contraseña de PostgreSQL | Sí (default: `secret`) |
| `AI_PROVIDER` | `groq` (default) o `ollama` | No |
| `GROQ_MODEL` | Modelo de Groq (default: `openai/gpt-oss-120b`) | No |
| `OLLAMA_EMBED_MODEL` | Modelo de embeddings (default: `nomic-embed-text`) | No |

Ver `.env.example` para la lista completa.

---

## Licencia

Propietario — FuturoTech AI. Todas las dependencias son open source / gratuitas para uso comercial.
