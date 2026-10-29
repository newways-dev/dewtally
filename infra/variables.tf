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

variable "image_registry" {
  description = "Where CI publishes the images (public packages, no login needed)"
  type        = string
  default     = "ghcr.io/newways-dev"
}

variable "image_tag" {
  description = "Image version to run: latest, or an exact commit like sha-1a2b3c4"
  type        = string
  default     = "latest"
}
