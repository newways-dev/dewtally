output "resource_group_name" {
  description = "Name of the resource group with the whole environment"
  value       = azurerm_resource_group.main.name
}
