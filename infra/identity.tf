# Service account of our containers in Azure. Its only right: reading secrets from Key Vault.
# So no password is ever written into a container's settings in plain text.
resource "azurerm_user_assigned_identity" "apps" {
  name                = "id-${var.prefix}-apps"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = var.tags
}

resource "azurerm_role_assignment" "apps_read_secrets" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.apps.principal_id
}
