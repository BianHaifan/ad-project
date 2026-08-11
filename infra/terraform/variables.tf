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
  default     = "t3.small"
}