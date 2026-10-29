# Everything of the environment lives in one resource group:
# "terraform destroy" removes it completely, so nothing keeps costing money.
resource "azurerm_resource_group" "main" {
  name     = "rg-${var.prefix}"
  location = var.location
  tags     = var.tags
}

# Logs of all containers, searchable in the Azure portal (like "docker compose logs").
# Billed per GB: the daily cap keeps a runaway error loop from becoming expensive.
resource "azurerm_log_analytics_workspace" "main" {
  name                = "log-${var.prefix}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  daily_quota_gb      = 0.5
  tags                = var.tags
}

# Shared environment for all containers (api, worker, RabbitMQ, Keycloak).
# Inside it, containers reach each other by name, like services in Docker Compose.
resource "azurerm_container_app_environment" "main" {
  name                       = "cae-${var.prefix}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  tags                       = var.tags
}
