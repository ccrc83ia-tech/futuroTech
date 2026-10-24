data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

resource "aws_iam_role" "backend" {
  name = "${var.project_name}-ec2-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Permite leer solo los parámetros SSM de este proyecto y enviar logs/métricas.
resource "aws_iam_role_policy" "backend" {
  name = "${var.project_name}-ec2-policy"
  role = aws_iam_role.backend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadProjectSsm"
        Effect = "Allow"
        Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        Resource = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}/*"
      },
      {
        Sid    = "DecryptSsm"
        Effect = "Allow"
        Action = ["kms:Decrypt"]
        Resource = "*"
        Condition = {
          StringEquals = { "kms:ViaService" = "ssm.${data.aws_region.current.name}.amazonaws.com" }
        }
      },
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams", "logs:CreateLogGroup"]
        Resource = "${aws_cloudwatch_log_group.backend.arn}:*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "backend" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.backend.name
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/${var.project_name}/backend"
  retention_in_days = 14
  tags              = { Name = "${var.project_name}-backend-logs" }
}
