# Secretos en SSM Parameter Store (SecureString). El backend los lee en el
# arranque (user_data) y los vuelca al .env del contenedor. Nunca en el código.

locals {
  ssm_prefix = "/${var.project_name}"
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.ssm_prefix}/DB_PASSWORD"
  type  = "SecureString"
  value = var.db_password
}

resource "aws_ssm_parameter" "gemini_api_key" {
  name  = "${local.ssm_prefix}/GEMINI_API_KEY"
  type  = "SecureString"
  value = var.gemini_api_key != "" ? var.gemini_api_key : "PENDIENTE"
}

resource "aws_ssm_parameter" "groq_api_key" {
  name  = "${local.ssm_prefix}/GROQ_API_KEY"
  type  = "SecureString"
  value = var.groq_api_key != "" ? var.groq_api_key : "PENDIENTE"
}

resource "aws_ssm_parameter" "openrouter_api_key" {
  name  = "${local.ssm_prefix}/OPENROUTER_API_KEY"
  type  = "SecureString"
  value = var.openrouter_api_key != "" ? var.openrouter_api_key : "PENDIENTE"
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "${local.ssm_prefix}/JWT_SECRET"
  type  = "SecureString"
  value = var.jwt_secret != "" ? var.jwt_secret : "PENDIENTE"
}
