# Uploaded original documents: api writes them, worker reads them. Both mount the same file share at /app/data,
# like the shared "documents" volume in compose.yaml. Azure Blob Storage in the code would be the next step.
resource "azurerm_storage_account" "documents" {
  name                     = "st${var.prefix}${random_string.suffix.result}"
  location                 = azurerm_resource_group.main.location
  resource_group_name      = azurerm_resource_group.main.name
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"
  tags                     = var.tags
}

resource "azurerm_storage_share" "documents" {
  name               = "documents"
  storage_account_id = azurerm_storage_account.documents.id
  quota              = 5 # GB: plenty for invoices
}

# Makes the file share available to the apps of the environment under the name "documents"
resource "azurerm_container_app_environment_storage" "documents" {
  name                         = "documents"
  container_app_environment_id = azurerm_container_app_environment.main.id
  account_name                 = azurerm_storage_account.documents.name
  share_name                   = azurerm_storage_share.documents.name
  access_key                   = azurerm_storage_account.documents.primary_access_key
  access_mode                  = "ReadWrite"
}
