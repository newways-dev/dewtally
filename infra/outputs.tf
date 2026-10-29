output "resource_group_name" {
  description = "Name of the resource group with the whole environment"
  value       = azurerm_resource_group.main.name
}

output "postgres_host" {
  description = "Host name of the PostgreSQL server"
  value       = azurerm_postgresql_flexible_server.main.fqdn
}
