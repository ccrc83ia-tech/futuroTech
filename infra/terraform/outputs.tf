output "server_ip" {
  description = "IP pública fija del backend (apuntar aquí el DNS/DuckDNS)."
  value       = aws_eip.backend.public_ip
}

output "database_endpoint" {
  description = "Endpoint del RDS PostgreSQL."
  value       = aws_db_instance.main.endpoint
}

output "ssh_command" {
  description = "Comando para conectarse por SSH."
  value       = "ssh -i ${var.ssh_key_name}.pem ubuntu@${aws_eip.backend.public_ip}"
}

output "next_steps" {
  description = "Pasos tras 'terraform apply'."
  value       = <<-EOT
    1. Esperar ~3 min a que termine user_data (ver /var/log/user-data.log vía SSH).
    2. Subir el repo al servidor y ejecutar: docker compose -f docker-compose.prod.yml up -d
    3. DuckDNS: apuntar ${var.duckdns_domain} -> ${aws_eip.backend.public_ip}
    4. HTTPS: sudo certbot --nginx -d ${var.duckdns_domain}
    5. Reindexar corpus RAG: ./seed-rag.sh
  EOT
}
