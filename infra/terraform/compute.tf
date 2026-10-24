# AMI más reciente de Ubuntu 22.04 (Canonical) para la región activa.
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "backend" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = var.ssh_key_name
  iam_instance_profile   = aws_iam_instance_profile.backend.name
  vpc_security_group_ids = [aws_security_group.backend.id]

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    aws_region     = var.aws_region
    project_name   = var.project_name
    db_name        = var.db_name
    db_username    = var.db_username
    db_endpoint    = aws_db_instance.main.endpoint
    duckdns_domain = var.duckdns_domain
  })

  depends_on = [aws_db_instance.main]
  tags       = { Name = "${var.project_name}-backend" }
}

# IP fija para apuntar el DNS/DuckDNS de forma estable.
resource "aws_eip" "backend" {
  instance = aws_instance.backend.id
  domain   = "vpc"
  tags     = { Name = "${var.project_name}-backend-eip" }
}
