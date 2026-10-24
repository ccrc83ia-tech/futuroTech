variable "aws_region" {
  description = "Región AWS del despliegue."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefijo de nombres de recursos."
  type        = string
  default     = "futurotech"
}

variable "instance_type" {
  description = "Tipo de EC2 para el backend. t3.small (2 GB) es el mínimo cómodo sin Ollama."
  type        = string
  default     = "t3.small"
}

variable "db_instance_class" {
  description = "Clase de RDS PostgreSQL."
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "Nombre de la base de datos."
  type        = string
  default     = "legal_engine"
}

variable "db_username" {
  description = "Usuario maestro de la base de datos."
  type        = string
  default     = "legal"
}

variable "db_password" {
  description = "Password de la base de datos (mínimo 12 caracteres). NO hardcodear: pasar por -var o TF_VAR_db_password."
  type        = string
  sensitive   = true
}

variable "allowed_ssh_cidr" {
  description = "CIDR permitido para SSH. Usa tu IP/32 por seguridad."
  type        = string
  default     = "0.0.0.0/0"
}

variable "ssh_key_name" {
  description = "Nombre del Key Pair EC2 existente para SSH."
  type        = string
}

# --- Secretos de IA (se guardan en SSM Parameter Store, no en el código) ---

variable "gemini_api_key" {
  description = "API key de Gemini (generador + embeddings)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "groq_api_key" {
  description = "API key de Groq (verificador de citas)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "openrouter_api_key" {
  description = "API key de OpenRouter (verificador de datos — DeepSeek)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "jwt_secret" {
  description = "Secreto JWT del backend. Si vacío, generar y colocar en SSM manualmente."
  type        = string
  sensitive   = true
  default     = ""
}

variable "duckdns_domain" {
  description = "Subdominio DuckDNS para HTTPS gratis (p. ej. futurotech.duckdns.org). Vacío = sin TLS automático."
  type        = string
  default     = ""
}
