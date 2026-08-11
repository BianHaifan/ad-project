variable "region" {
  description = "AWS region"
  default     = "us-east-1"
}

variable "key_name" {
  description = "EC2 key pair name shared by A and B (already created in AWS)"
  default     = "ad-control"
}

variable "instance_type" {
  description = "Instance type for target server B"
  default     = "t3.micro"
}

variable "control_sg_name" {
  description = "Name of the security group of control plane A; B allows SSH only from it"
  default     = "ad-control-sg"
}