variable "prefix" {
  description = "Short name used in all resource names"
  type        = string
  default     = "dewtally"
}

variable "location" {
  description = "Azure region; Frankfurt keeps invoice data in Germany"
  type        = string
  default     = "germanywestcentral"
}

variable "tags" {
  description = "Tags on every resource, so costs can be traced to this project"
  type        = map(string)
  default = {
    project    = "dewtally"
    managed_by = "terraform"
  }
}

# No default and never in a file: set it in the shell before running Terraform,
# PowerShell: $env:TF_VAR_openai_api_key = "..."
variable "openai_api_key" {
  description = "OpenAI API key for reading plain PDF invoices"
  type        = string
  sensitive   = true
}
