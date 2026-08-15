output "b_instance_id" {
  description = "Instance ID of target server B"
  value       = aws_instance.b.id
}

output "b_private_ip" {
  description = "Private IP of target server B (used by Ansible over A)"
  value       = aws_instance.b.private_ip
}

output "b_eip" {
  description = "Elastic IP of target server B (used by browsers and GitHub Actions)"
  value       = aws_eip.b.public_ip
}

output "b_public_dns" {
  description = "Public DNS of target server B"
  value       = aws_instance.b.public_dns
}