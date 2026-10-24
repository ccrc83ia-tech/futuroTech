# Despliegue AWS — FuturoTech AI (Terraform)

Infraestructura single-node costo-reducido: **EC2** (backend + frontend en Docker) + **RDS PostgreSQL 16 con pgvector** + **SSM** (secretos) + **CloudWatch** (logs) + **Elastic IP**. Sin Ollama (embeddings y generación son APIs remotas gratuitas), por lo que la EC2 puede ser pequeña (`t3.small`).

## Arquitectura

```
Internet → DuckDNS (dominio gratis) → Elastic IP → EC2 t3.small
                                                     ├─ Nginx (frontend + proxy /api, HTTPS Certbot)
                                                     └─ backend :8080 (Docker)
                                                          │
                                                     RDS PostgreSQL 16 + pgvector
                                                          │
                                                     egress → Gemini / Groq / OpenRouter (IA gratis)
```

## Requisitos previos

1. Terraform ≥ 1.6 y credenciales AWS configuradas (`aws configure`).
2. Un **Key Pair EC2** creado en la región (EC2 → Key Pairs).
3. (Opcional) Un subdominio **DuckDNS** gratis en https://www.duckdns.org para HTTPS.
4. Claves de IA: Gemini (AI Studio), Groq (console.groq.com), OpenRouter (openrouter.ai).

## Despliegue

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # editar valores
terraform init
terraform plan
terraform apply
```

Las keys de IA y el password de BD se guardan en **SSM Parameter Store** (SecureString). Puedes dejarlas vacías en `terraform.tfvars` y cargarlas luego:

```bash
aws ssm put-parameter --name /futurotech/GEMINI_API_KEY --type SecureString --overwrite --value "TU_KEY"
```

## Tras `terraform apply`

El `user_data` instala Docker, lee los secretos de SSM, genera el `.env`, y habilita `CREATE EXTENSION vector` en RDS. Luego, en el servidor:

```bash
# 1. Conéctate (el output 'ssh_command' lo imprime)
ssh -i mi-keypair.pem ubuntu@<server_ip>

# 2. Sube el repo (desde tu máquina, p. ej. scp o git clone) a /opt/futurotech
# 3. Levanta la app
cd /opt/futurotech
docker compose -f docker-compose.prod.yml up -d --build

# 4. HTTPS gratis (si configuraste DuckDNS)
sudo certbot --nginx -d futurotech.duckdns.org

# 5. Reindexar el corpus RAG con los nuevos embeddings (Gemini 768)
./seed-rag.sh
```

## Costos (orden de magnitud)

| Recurso | Año 1 (Free Tier) | Después |
|---|---|---|
| EC2 t3.small | ~$0* | ~$15/mes |
| RDS db.t3.micro | ~$0* | ~$15/mes |
| EBS / EIP / CloudWatch | ~$3/mes | ~$7/mes |
| IA (Gemini/Groq/OpenRouter free) | $0 | $0 |

\*Sujeto a elegibilidad del AWS Free Tier. La IA es gratuita a ~20 docs/semana.

## Seguridad / notas

- **Secretos**: nunca en el código; viven en SSM. `terraform.tfvars` está en `.gitignore`.
- **SSH**: restringe `allowed_ssh_cidr` a tu IP/32.
- **Datos reales (Ley 1581)**: el stack gratuito puede usar datos para entrenar. Para producción con datos de clientes, cambia generador y embeddings a tier de pago ajustando `AI_GENERATOR_*` y las keys (sin tocar código).
- **RDS**: antes de producción, revisa `deletion_protection = true` y retención de backups.
- **pgvector**: la dimensión está fijada en 768 (coincide con `vector(768)` del esquema). Cambiarla exige migración + reindexar.

## Destruir

```bash
terraform destroy
```
