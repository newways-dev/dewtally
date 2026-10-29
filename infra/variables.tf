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