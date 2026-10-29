# Who runs Terraform right now (your Azure login); used to give Terraform the right to write secrets.
data "azurerm_client_config" "current" {}

# Safe for secrets. Access is managed with Azure roles (RBAC), not with vault-specific access policies.
resource "azurerm_key_vault" "main" {
  name                       = "kv-${var.prefix}-${random_string.suffix.result}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  soft_delete_retention_days = 7
  # Off on purpose: with it, a deleted vault could not be removed for good, and "destroy" would leave it behind.
  purge_protection_enabled = false
  tags                     = var.tags
}

# Terraform may write secrets. The containers will only get the right to read them (next step).
resource "azurerm_role_assignment" "terraform_writes_secrets" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_key_vault_secret" "db_password" {
  name         = "db-password"
  value        = random_password.db.result
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.terraform_writes_secrets]
}

resource "azurerm_key_vault_secret" "openai_api_key" {
  name         = "openai-api-key"
  value        = var.openai_api_key
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.terraform_writes_secrets]
}
