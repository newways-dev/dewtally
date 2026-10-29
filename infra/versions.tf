# Which Terraform and which provider versions this code works with.
terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

# The subscription comes from the environment (ARM_SUBSCRIPTION_ID) or "az login", never from the code.
provider "azurerm" {
  features {
    key_vault {
      # "terraform destroy" removes the vault for good instead of leaving it in the recycle bin
      purge_soft_delete_on_destroy = true
    }
  }
}
